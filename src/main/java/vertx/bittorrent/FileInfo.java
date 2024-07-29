package vertx.bittorrent;

import be.adaxisoft.bencode.BEncodedValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class FileInfo {
    private final String path;
    private final long length;

    public static FileInfo fromDict(String basePath, BEncodedDict dict) {
        try {
            List<BEncodedValue> elements = dict.requireList("path");
            long length = dict.requireLong("length");

            Path path = Path.of(basePath);

            for (var element : elements) {
                if (path == null) {
                    path = Path.of(element.getString());
                } else {
                    path = path.resolve(element.getString());
                }
            }

            return new FileInfo(path.toString(), length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
