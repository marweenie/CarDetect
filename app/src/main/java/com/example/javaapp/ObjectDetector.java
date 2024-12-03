package com.example.javaapp;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ObjectDetector {

    private Interpreter interpreter;
    private List<String> labelList;
    private int INPUT_SIZE;
    private int height = 0;
    private int width = 0;
    private Point vehicleCentroid; // Stores the centroid of the detected vehicle

    // Constructor for initializing the object detector with model and labels
    public ObjectDetector(AssetManager assetManager, String modelPath, String labelPath, int inputSize) throws IOException {
        INPUT_SIZE = inputSize;
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Set number of threads for the interpreter
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
        labelList = loadLabelList(assetManager, labelPath); // Load labels
    }

    // Method to load the model file from assets
    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Method to load the label list from a file in assets
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

    // Inner class to represent a tracked vehicle with an ID, centroid, and history for speed estimation
    class Vehicle {
        int id;
        Point centroid;
        Point lastCentroid;
        int framesNotSeen;
        List<Pair<Long, RectF>> history = new ArrayList<>();

        Vehicle(int id, Point centroid) {
            this.id = id;
            this.centroid = centroid;
            this.lastCentroid = centroid;
            this.framesNotSeen = 0;
        }

        // Update the centroid and add the bounding box to the history
        void updateCentroid(Point newCentroid, RectF boundingBox) {
            this.lastCentroid = this.centroid;
            this.centroid = newCentroid;
            this.framesNotSeen = 0;
            long currentTime = System.currentTimeMillis();
            history.add(Pair.create(currentTime, boundingBox));
            if (history.size() > 10) { // Keep the history size manageable
                history.remove(0);
            }
        }

        // Estimate the speed based on equidistant points in the history
        double estimateSpeed() {
            if (history.size() < 4) {
                return 0; // Not enough data points for speed calculation
            }

            // Get equidistant points A, B, and C
            Pair<Long, RectF> pointA = history.get(history.size() - 4);
            Pair<Long, RectF> pointB = history.get(history.size() - 3);
            Pair<Long, RectF> pointC = history.get(history.size() - 2);
            Pair<Long, RectF> pointD = history.get(history.size() - 1);


            // Calculate meters per pixel (mpp) using the width of the bounding box at point A
            double mpp = AVERAGE_CAR_LENGTH_METERS / pointA.second.width();

            // Calculate distances in pixels between points AB and BC
            double distanceAB = pointB.second.centerX() - pointA.second.centerX();
            double distanceBC = pointC.second.centerX() - pointB.second.centerX();
            double distanceCD = pointD.second.centerX() - pointC.second.centerX();


            // Convert distances to meters
            double distanceABMeters = Math.abs(distanceAB * mpp);
            double distanceBCMeters = Math.abs(distanceBC * mpp);
            double distanceCDMeters = Math.abs(distanceCD * mpp);


            // Calculate time intervals between points A to B and B to C in seconds
            double timeAB = (pointB.first - pointA.first) / 1000.0; // Convert milliseconds to seconds
            double timeBC = (pointC.first - pointB.first) / 1000.0; // Convert milliseconds to seconds
            double timeCD = (pointD.first - pointC.first) / 1000.0; // Convert milliseconds to seconds


            // Calculate speeds in m/s for regions AB and BC
            double speedAB = distanceABMeters / timeAB;
            double speedBC = distanceBCMeters / timeBC;
            double speedCD = distanceBCMeters / timeCD;


            // Convert speeds to km/h (1 m/s = 3.6 km/h)
            double speedABKMPH = speedAB * 3.6;
            double speedBCKMPH = speedBC * 3.6;
            double speedCDKMPH = speedCD * 3.6;


            // Calculate the final average speed
            return (speedABKMPH + speedBCKMPH + speedCDKMPH) / 3.0;
        }
    }

    // List to keep track of active vehicles and their centroids
    private List<Vehicle> trackedVehicles = new ArrayList<>();
    private int nextVehicleId = 1; // ID counter for new vehicles
    private static final double AVERAGE_CAR_LENGTH_METERS = 4.9; // Average car length in meters

    // Method to update tracked vehicles with detected centroids and bounding boxes in the current frame
    private void updateTrackedVehicles(List<Point> detectedCentroids, List<RectF> boundingBoxes) {
        int MAX_DISTANCE = 300; // Maximum distance to consider the same vehicle
        int MAX_FRAMES_NOT_SEEN = 15; // Max frames before removing a vehicle
        Map<Integer, Point> updatedCentroids = new HashMap<>();

        // Loop over each detected centroid
        for (int i = 0; i < detectedCentroids.size(); i++) {
            Point newCentroid = detectedCentroids.get(i);
            RectF boundingBox = boundingBoxes.get(i);
            int closestVehicleId = -1;
            double minDistance = MAX_DISTANCE;

            // Find the closest tracked vehicle within the threshold distance
            for (Vehicle vehicle : trackedVehicles) {
                double distance = calculateDistance(vehicle.centroid, newCentroid);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestVehicleId = vehicle.id;
                }
            }

            // Update the centroid for the matched vehicle or create a new vehicle if none are close enough
            if (closestVehicleId != -1) {
                for (Vehicle vehicle : trackedVehicles) {
                    if (vehicle.id == closestVehicleId) {
                        vehicle.updateCentroid(newCentroid, boundingBox);
                        updatedCentroids.put(vehicle.id, vehicle.centroid);

                        // Estimate the speed and log it
                        double estimatedSpeed = vehicle.estimateSpeed();
                        Log.d("SpeedEstimator", "Vehicle ID: " + vehicle.id + " Speed: " + estimatedSpeed + " km/h");
                    }
                }
            } else {
                int newId = nextVehicleId++;
                Vehicle newVehicle = new Vehicle(newId, newCentroid);
                newVehicle.updateCentroid(newCentroid, boundingBox);
                trackedVehicles.add(newVehicle);
                updatedCentroids.put(newId, newCentroid);
            }
        }

        // Handle vehicles not seen in this frame by incrementing framesNotSeen counter
        for (Iterator<Vehicle> iterator = trackedVehicles.iterator(); iterator.hasNext(); ) {
            Vehicle vehicle = iterator.next();
            if (!updatedCentroids.containsKey(vehicle.id)) {
                vehicle.framesNotSeen++;
                // Remove vehicle if not seen for the maximum allowed frames
                if (vehicle.framesNotSeen > MAX_FRAMES_NOT_SEEN) {
                    iterator.remove();
                }
            }
        }
    }

    // Utility method to calculate Euclidean distance between two points
    private double calculateDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    public Mat recognizeImage(Mat mat_image) {
        Mat rotated_mat_image = new Mat();
        Core.flip(mat_image.t(), rotated_mat_image, 1); // Rotate and flip the image
        Bitmap bitmap = Bitmap.createBitmap(rotated_mat_image.cols(), rotated_mat_image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rotated_mat_image, bitmap);

        height = bitmap.getHeight();
        width = bitmap.getWidth();

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);

        Object[] input = new Object[1];
        input[0] = byteBuffer;

        Map<Integer, Object> output_map = new TreeMap<>();
        float[][][] boxes = new float[1][10][4]; // Bounding box coordinates
        float[][] scores = new float[1][10]; // Confidence scores
        float[][] classes = new float[1][10]; // Class labels

        output_map.put(0, boxes);
        output_map.put(1, classes);
        output_map.put(2, scores);

        interpreter.runForMultipleInputsOutputs(input, output_map);

        List<Point> detectedCentroids = new ArrayList<>();
        List<RectF> boundingBoxes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            float class_value = (float) Array.get(Array.get(output_map.get(1), 0), i);
            float score_value = (float) Array.get(Array.get(output_map.get(2), 0), i);

            if (score_value > 0.5) {
                Object box1 = Array.get(Array.get(output_map.get(0), 0), i);
                float top = (float) Array.get(box1, 0) * height;
                float left = (float) Array.get(box1, 1) * width;
                float bottom = (float) Array.get(box1, 2) * height;
                float right = (float) Array.get(box1, 3) * width;

                if (class_value == 2 || class_value == 3 || class_value == 5 || class_value == 7) { // Filter classes if needed
                    vehicleCentroid = new Point((left + right) / 2, (top + bottom) / 2);
                    RectF boundingBox = new RectF(left, top, right, bottom);
                    detectedCentroids.add(vehicleCentroid);
                    boundingBoxes.add(boundingBox);

                    Imgproc.rectangle(rotated_mat_image, new Point(left, top), new Point(right, bottom), new Scalar(0, 255, 0, 255), 2);
                    Imgproc.circle(rotated_mat_image, vehicleCentroid, 10, new Scalar(0, 0, 0), -1);
                }
            }
        }

        // Update tracked vehicles with new centroids and bounding boxes
        updateTrackedVehicles(detectedCentroids, boundingBoxes);

        // Draw each tracked vehicle's ID and speed on the bounding box
        for (Vehicle vehicle : trackedVehicles) {
            double estimatedSpeed = vehicle.estimateSpeed();

            // Draw vehicle ID and speed on the frame
            Imgproc.putText(rotated_mat_image, "ID: " + vehicle.id + " Speed: " + String.format("%.2f km/h", estimatedSpeed),
                    new Point(vehicle.centroid.x - 10, vehicle.centroid.y - 20),
                    0, 0.5, new Scalar(255, 0, 0, 255), 2);

        }

        Core.flip(rotated_mat_image.t(), mat_image, 0);
        return mat_image;
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