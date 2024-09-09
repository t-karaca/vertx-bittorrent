package vertx.bittorrent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public class LearningModel {
    private static final int NUM_CLASSES = 10;
    private static final int IMAGE_SIZE = 28 * 28;

    private static final Random RANDOM = new Random();

    private final int id;
    private final MultiLayerNetwork model;
    private final List<DataSet> localData;
    private final NormalizerMinMaxScaler normalizer;

    private INDArray weights;

    public LearningModel(int id, String imagesPath, String labelsPath) throws IOException {
        this.id = id;
        this.localData = loadDataSet(imagesPath, labelsPath);
        this.model = createModel();
        this.model.init();
        this.model.setListeners(new ScoreIterationListener(10));
        this.normalizer = new NormalizerMinMaxScaler(0, 1); // new NormalizerStandardize();
        this.weights = trainAndReturnWeights();
        createTorrent();
    }

    public void createTorrent() {
        try {
            String torrentFilePath = "/torrents/" + "node-" + id + "-weights.torrent";
            File weightsFile = new File("/data/" + "node-" + id + "-weights.dat");
            Files.write(weightsFile.toPath(), encodeWeights(weights).getBytes());

            com.turn.ttorrent.common.Torrent torrent = com.turn.ttorrent.common.Torrent.create(
                    weightsFile, new URL("http://opentracker:6969/announce").toURI(), "node-" + id);
            torrent.save(new FileOutputStream(torrentFilePath));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<DataSet> loadDataSet(String imagesPath, String labelsPath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagesPath));
        byte[] labelBytes = Files.readAllBytes(Paths.get(labelsPath));

        int numImages = toInt(imageBytes, 4);
        int numRows = toInt(imageBytes, 8);
        int numColumns = toInt(imageBytes, 12);

        List<DataSet> dataSets = new ArrayList<>(numImages);

        int imageOffset = 16;
        int labelOffset = 8;
        for (int i = 0; i < numImages; i++) {
            float[] imageData = new float[numRows * numColumns];
            for (int j = 0; j < numRows * numColumns; j++) {
                imageData[j] = (imageBytes[imageOffset + j] & 0xFF) / 255.0f;
            }
            imageOffset += numRows * numColumns;

            float[] labelData = new float[NUM_CLASSES];
            int label = labelBytes[labelOffset++] & 0xFF;
            labelData[label] = 1.0f;

            INDArray features = Nd4j.create(imageData, new int[] {1, numRows * numColumns});
            INDArray labels = Nd4j.create(labelData, new int[] {1, NUM_CLASSES});
            dataSets.add(new DataSet(features, labels));
        }

        return dataSets;
    }

    private MultiLayerNetwork createModel() {
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(123)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(
                        0,
                        new DenseLayer.Builder()
                                .nIn(784)
                                .nOut(1000)
                                .activation(Activation.RELU)
                                .weightInit(WeightInit.XAVIER)
                                .build())
                .layer(
                        1,
                        new DenseLayer.Builder()
                                .nIn(1000)
                                .nOut(500)
                                .activation(Activation.RELU)
                                .weightInit(WeightInit.XAVIER)
                                .build())
                .layer(
                        2,
                        new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                .activation(Activation.SOFTMAX)
                                .nIn(500)
                                .nOut(10)
                                .build())
                .build();
        MultiLayerNetwork model = new MultiLayerNetwork(configuration);
        // model.init();
        return model;
    }

    public INDArray trainAndReturnWeights() {
        DataSetIterator trainData = new ListDataSetIterator<>(localData, 64);
        // normalizer(trainData, normalizer);
        // trainData.setPreProcessor(normalizer);
        model.fit(trainData);
        normalizer(trainData, normalizer);
        // trainData.setPreProcessor(normalizer);
        return model.params().dup();
    }

    private static void normalizer(DataSetIterator data, NormalizerMinMaxScaler normalizer) {
        data.setPreProcessor(normalizer);
    }

    private String encodeWeights(INDArray weights) {
        byte[] byteArray = weights.data().asBytes();
        return Base64.getEncoder().encodeToString(byteArray);
    }

    private static int toInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }
}
