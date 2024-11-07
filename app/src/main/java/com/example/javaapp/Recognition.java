package com.example.javaapp;

import android.graphics.RectF;

public class Recognition {
    private String id;
    private RectF location;

    public Recognition(String id, RectF location) {
        this.id = id;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public RectF getLocation() {
        return location;
    }

    // Method to update the location
    public void updateLocation(RectF newLocation) {
        this.location = newLocation;
    }
}
