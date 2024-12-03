package com.example.javaapp;

import android.util.Pair;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class SpeedEstimator {

    // Constant for the speed limit in km/h
    public static final double SPEED_LIMIT = 30.0;

    // Average length of a car in meters, used to calculate the meter per pixel (mpp)
    public static final double AVERAGE_CAR_LENGTH_METERS = 4.9;

    // Frame rate in pixels to space points A, B, and C
    private double frameRate;

    // Map to store object histories: key = objectID, value = list of timestamp and bounding box pairs
    private Map<Integer, List<Pair<Long, RectF>>> objectHistories = new HashMap<>();

    // Constructor to initialize the frame rate
    public SpeedEstimator(double frameRate) {
        this.frameRate = frameRate;
    }

    // Method to add a new bounding box and timestamp to the object's history
    public void addHistory(int objectID, RectF boundingBox) {
        long currentTime = System.currentTimeMillis(); // Current timestamp

        // Initialize the history list if the objectID is new
        if (!objectHistories.containsKey(objectID)) {
            objectHistories.put(objectID, new ArrayList<>());
        }

        // Retrieve the existing history and add the new point
        List<Pair<Long, RectF>> history = objectHistories.get(objectID);
        history.add(Pair.create(currentTime, boundingBox));

        // Limit the size of the history to a maximum of 10 entries to manage memory
        if (history.size() > 10) {
            history.remove(0);
        }
    }

    // Method to estimate the speed of an object based on its movement history
    public double estimateSpeed(int objectID) {
        // Retrieve the object's movement history
        List<Pair<Long, RectF>> history = objectHistories.get(objectID);

        // Check if there are enough points to calculate speed (at least 3 points are needed)
        if (history == null || history.size() < 3) {
            return 0; // Return 0 if not enough data
        }

        // Get the bounding boxes and timestamps for points A, B, and C
        Pair<Long, RectF> pointA = history.get(history.size() - 3);
        Pair<Long, RectF> pointB = history.get(history.size() - 2);
        Pair<Long, RectF> pointC = history.get(history.size() - 1);

        // Calculate meters per pixel (mpp) using the width of the bounding box at point A
        double mpp = AVERAGE_CAR_LENGTH_METERS / pointA.second.width();

        // Calculate distances between points AB and BC in pixels
        double distanceAB = pointB.second.centerX() - pointA.second.centerX();
        double distanceBC = pointC.second.centerX() - pointB.second.centerX();

        // Convert distances to meters using mpp
        double distanceABMeters = Math.abs(distanceAB * mpp);
        double distanceBCMeters = Math.abs(distanceBC * mpp);

        // Calculate time intervals between points A to B and B to C in seconds
        double timeAB = (pointB.first - pointA.first) / 1000.0; // Convert milliseconds to seconds
        double timeBC = (pointC.first - pointB.first) / 1000.0; // Convert milliseconds to seconds

        // Calculate speeds in meters per second for regions AB and BC
        double speedAB = distanceABMeters / timeAB;
        double speedBC = distanceBCMeters / timeBC;

        // Convert speeds to km/h (1 m/s = 3.6 km/h)
        double speedABKMPH = speedAB * 3.6;
        double speedBCKMPH = speedBC * 3.6;

        // Calculate the final average speed over the two regions
        return (speedABKMPH + speedBCKMPH) / 2.0;
    }

    // Method to check if the speed exceeds the speed limit
    public boolean isSpeedLimitExceeded(double speed) {
        return speed > SPEED_LIMIT;
    }

    // Helper method to determine the direction of movement
    private boolean isMovingForward(RectF pointA, RectF pointC) {
        // Check if the object is moving from left to right (forward direction)
        return pointA.centerX() < pointC.centerX();
    }

    // Method to estimate speed and account for movement in both directions
    public double estimateSpeedBothWays(int objectID) {
        // Retrieve the object's movement history
        List<Pair<Long, RectF>> history = objectHistories.get(objectID);

        // Check if there are enough points to calculate speed
        if (history == null || history.size() < 3) {
            return 0; // Return 0 if not enough data
        }

        // Get points A and C for direction check
        Pair<Long, RectF> pointA = history.get(history.size() - 3);
        Pair<Long, RectF> pointC = history.get(history.size() - 1);

        // Determine if the movement is forward or backward
        boolean isForward = isMovingForward(pointA.second, pointC.second);

        // Return the estimated speed with the correct sign for direction
        return isForward ? estimateSpeed(objectID) : -estimateSpeed(objectID);
    }
}