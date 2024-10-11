package com.example.javaapp;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {
    static {
        if (OpenCVLoader.initDebug()) {
            Log.d("MainActivity: ", "Opencv is loaded");
        } else {
            Log.d("MainActivity: ", "Opencv failed to load");
        }
    }


    private Button camera_button;
    private Button video_button; // New variable for video button
    private VideoView videoView; // VideoView to play the video

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        camera_button = findViewById(R.id.camera_button);
        video_button = findViewById(R.id.video_button); // Initialize video button
        videoView = findViewById(R.id.video_view); // Make sure to add a VideoView in XML
        video_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });
        camera_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CameraActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });

    }


    private void playVideo() {
        camera_button.setVisibility(View.GONE);
        video_button.setVisibility(View.GONE);

        videoView.setVisibility(View.VISIBLE);

        // Specify the path to your video
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.car);

        // Set the video URI to the VideoView
        videoView.setVideoURI(videoUri);

        // Set the Media Controller
        videoView.setMediaController(new android.widget.MediaController(this));

        // Request focus and start playback
        videoView.requestFocus();
        videoView.start();
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // Show the buttons again when the video finishes
                camera_button.setVisibility(View.VISIBLE);
                video_button.setVisibility(View.VISIBLE);
                // Hide the video view
                videoView.setVisibility(View.GONE);
            }
        });
    }
}

/*

 videoView.setVisibility(View.VISIBLE);
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.car);
        videoView.setMediaController(new android.widget.MediaController(this));
        videoView.setVideoURI(videoUri);
        videoView.start();


        ..............
    private void playVideo() {
        Log.d("MainActivity", "playVideo() called");

        videoView.setVisibility(View.VISIBLE);

        // Specify the path to your video
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.car);

        // Set the video URI to the VideoView
        videoView.setVideoURI(videoUri);

        // Start the video playback
        videoView.setMediaController(new android.widget.MediaController(this));
        videoView.requestFocus();
        videoView.start();
    }*/