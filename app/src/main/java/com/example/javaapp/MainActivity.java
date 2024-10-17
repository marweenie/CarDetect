package com.example.javaapp;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.IOException;

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
    private Button image_button;
    private ImageView imageView;

    private ObjectDetector objectDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        camera_button = findViewById(R.id.camera_button);
        video_button = findViewById(R.id.video_button); // Initialize video button
        videoView = findViewById(R.id.video_view); // Make sure to add a VideoView in XML
        imageView = findViewById(R.id.image_view);
        image_button = findViewById(R.id.image_button);

        try{
            objectDetector=new ObjectDetector(getAssets(),"model.tflite","labelmap.txt",300);
            Log.d("MainActivity","Model is successfully loaded");
        }
        catch (IOException e){
            Log.d("MainActivity","Getting some error");
            e.printStackTrace();
        }

        image_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w("WE R IN IMGBUTTON", "ddd");
                playIMGVideo();

            }
        });


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
        image_button.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
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
                image_button.setVisibility(View.VISIBLE);

                // Hide the video view
                videoView.setVisibility(View.GONE);
            }
        });
    }

    private MediaMetadataRetriever retriever;
    private Handler handler;
    private int frameRate = 30; // Frame rate in frames per second
    private int currentFrame = 0; // Current frame index

    private void playIMGVideo() {
        Log.w("WE R IN PLAYIMGVIDEO FUNCTION", "WE IN ");

        // Hide other UI elements
        camera_button.setVisibility(View.GONE);
        video_button.setVisibility(View.GONE);
        image_button.setVisibility(View.GONE);

        // Show the ImageView
        imageView.setVisibility(View.VISIBLE);

        // Initialize MediaMetadataRetriever
        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.car));

        // Start looping through frames
        handler = new Handler();
        currentFrame = 0;
        loopThroughFrames();
    }

    private void loopThroughFrames() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int totalFrames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT) != null ?
                        Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)) : 1;

                long frameDuration = (long) ((1.0 / frameRate) * 1000000);
                long currentTime = currentFrame * frameDuration;
                Bitmap frame = retriever.getFrameAtTime(currentTime, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) {
                    Mat matFrame = new Mat();
                    Utils.bitmapToMat(frame, matFrame);

                    // Apply object detection using the instance of ObjectDetector
                    Mat detectedMat = objectDetector.recognizeImage(matFrame);

                    // Convert the processed Mat back to a Bitmap
                    Bitmap detectedBitmap = Bitmap.createBitmap(detectedMat.cols(), detectedMat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(detectedMat, detectedBitmap);

                    // Display the processed frame with object detection in the ImageView
                    imageView.setImageBitmap(detectedBitmap);
                }

                currentFrame++;
                if (currentTime < totalFrames * frameDuration) {
                    loopThroughFrames();
                } else {
                    resetUI();
                }
            }

        }, 1000 / frameRate); // Delay based on frame rate
    }

    private void resetUI() {
        // Show buttons again and hide the ImageView
        camera_button.setVisibility(View.VISIBLE);
        video_button.setVisibility(View.VISIBLE);
        image_button.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

    }
}