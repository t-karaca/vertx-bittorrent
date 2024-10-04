package vertx.bittorrent;

import io.vertx.core.buffer.Buffer;

public class BufferReader {
    private final Buffer buffer;

    private int position;

    public BufferReader(Buffer buffer) {
        this.buffer = buffer;
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return buffer.length() - position;
    }

    public int length() {
        return buffer.length();
    }

    public byte get() {
        return buffer.getByte(position++);
    }

    public void get(byte[] dst) {
        get(dst, 0, dst.length);
    }

    public void get(byte[] dst, int offset, int length) {
        buffer.getBytes(position, position + length, dst, offset);
        position += length;
    }
}
