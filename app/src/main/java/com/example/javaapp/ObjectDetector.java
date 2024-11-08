package com.example.javaapp;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ObjectDetector {

    private Interpreter interpreter;
    private List<String> labelList;
    private int INPUT_SIZE;
    private int height = 0;
    private int width = 0;
    private Point vehicleCentroid;  // Stores the centroid of the detected vehicle

    // Constructor for initializing the object detector with model and labels
    ObjectDetector(AssetManager assetManager, String modelPath, String labelPath, int inputSize) throws IOException {
        INPUT_SIZE = inputSize;
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Set number of threads for the interpreter
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
        labelList = loadLabelList(assetManager, labelPath); // Load labels
    }

    // Loads label list from a file in assets
    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    // Loads model file from assets and maps it to a ByteBuffer
    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Recognizes objects in an image, draws bounding boxes, and returns the processed image
    public Mat recognizeImage(Mat mat_image) {
        Mat rotated_mat_image = new Mat();
        Core.flip(mat_image.t(), rotated_mat_image, 1); // Rotate and flip the image
        Bitmap bitmap = Bitmap.createBitmap(rotated_mat_image.cols(), rotated_mat_image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rotated_mat_image, bitmap);

        height = bitmap.getHeight();
        width = bitmap.getWidth();

        // Scale the bitmap to the input size of the model
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);

        // Prepare input and output for the TensorFlow Lite interpreter
        Object[] input = new Object[1];
        input[0] = byteBuffer;

        Map<Integer, Object> output_map = new TreeMap<>();
        float[][][] boxes = new float[1][10][4]; // Bounding box coordinates
        float[][] scores = new float[1][10];     // Confidence scores
        float[][] classes = new float[1][10];    // Class labels

        output_map.put(0, boxes);
        output_map.put(1, classes);
        output_map.put(2, scores);

        // Run the model and get the output
        interpreter.runForMultipleInputsOutputs(input, output_map);

        // Process each detection
        for (int i = 0; i < 10; i++) {
            float class_value = (float) Array.get(Array.get(output_map.get(1), 0), i);
            float score_value = (float) Array.get(Array.get(output_map.get(2), 0), i);

            // If confidence score is above threshold
            if (score_value > 0.5) {
                // Get bounding box coordinates, scaled to the image size
                Object box1 = Array.get(Array.get(output_map.get(0), 0), i);
                float top = (float) Array.get(box1, 0) * height;
                float left = (float) Array.get(box1, 1) * width;
                float bottom = (float) Array.get(box1, 2) * height;
                float right = (float) Array.get(box1, 3) * width;

                // Check if the detected class corresponds to a vehicle
                if (class_value == 2 || class_value == 3 || class_value == 5 || class_value == 7) {
                    // Calculate and store the centroid of the vehicle
                    vehicleCentroid = new Point((left + right) / 2, (top + bottom) / 2);

                    // Draw bounding box and centroid
                    Imgproc.rectangle(rotated_mat_image, new Point(left, top), new Point(right, bottom), new Scalar(0, 255, 0, 255), 2);
                    Imgproc.circle(rotated_mat_image, vehicleCentroid, 10, new Scalar(0, 0, 0), -1);
                    Imgproc.putText(rotated_mat_image, labelList.get((int) class_value), new Point(left, top), 3, 1, new Scalar(255, 0, 0, 255), 2);

                    Log.d("ObjectDetector", "Detected: " + labelList.get((int) class_value) +
                            " (Class: " + class_value + ") | BoundingBox: [" + left + ", " + top + "] to [" + right + ", " + bottom + "] | Centroid: " + vehicleCentroid);
                }
            }
        }

        // Rotate the image back and return the result
        Core.flip(rotated_mat_image.t(), mat_image, 0);
        return mat_image;
    }

    // Provide a method to access the vehicle centroid
    public Point getVehicleCentroid() {
        return vehicleCentroid;
    }

    // Converts bitmap to ByteBuffer format for model input
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        int size_images = INPUT_SIZE;

        byteBuffer = ByteBuffer.allocateDirect(1 * size_images * size_images * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[size_images * size_images];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;

        for (int i = 0; i < size_images; ++i) {
            for (int j = 0; j < size_images; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.put((byte) ((val >> 16) & 0xFF));
                byteBuffer.put((byte) ((val >> 8) & 0xFF));
                byteBuffer.put((byte) (val & 0xFF));
            }
        }
        return byteBuffer;
    }
}
