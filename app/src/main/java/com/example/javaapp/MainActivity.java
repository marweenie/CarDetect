package com.example.javaapp;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    static {
        if (OpenCVLoader.initDebug()) {
            Log.d("MainActivity: ", "Opencv is loaded");
        } else {
            Log.d("MainActivity: ", "Opencv failed to load");
        }
    }
    private ApiService apiService;
    private SpeedEstimator speedEstimator;
    private Handler handler;
    private Runnable sendDataRunnable;
    private static final int INTERVAL = 5000; // Interval in milliseconds (e.g., 5000 ms = 5 seconds)

    private Button camera_button;
    private Button video_button; // New variable for video button
    private VideoView videoView; // VideoView to play the video
    private Button image_button;
    private ImageView imageView;

    private ObjectDetector objectDetector;
    public static int frameRate = 30; // Frame rate in frames per second

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

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://141.215.203.136/projects/SpeedEye/") // Replace with your correct IP and path
                .addConverterFactory(GsonConverterFactory.create())
                .build();


        apiService = retrofit.create(ApiService.class);

        // Initialize SpeedEstimator with a frame rate, e.g., 30 frames per second
        //change the 30 to be an int variable equal to 30.
        speedEstimator = new SpeedEstimator(frameRate);

        handler = new Handler();
        sendDataRunnable = new Runnable() {
            @Override
            public void run() {
                sendVehicleSpeedData();

                // Schedule the next execution
                handler.postDelayed(this, INTERVAL);
            }
        };

        // Start the periodic data sending
        handler.post(sendDataRunnable);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove any pending posts of sendDataRunnable when the activity is destroyed
        handler.removeCallbacks(sendDataRunnable);
    }

    private void sendVehicleSpeedData() {
        int objectID = 1; // Example object ID
        // Simulate detection of vehicles and their speed estimation
        speedEstimator.addHistory(objectID, new RectF(100, 100, 150, 150));
        speedEstimator.addHistory(objectID, new RectF(200, 200, 250, 250));
        speedEstimator.addHistory(objectID, new RectF(300, 300, 350, 350));

        double estimatedSpeed = speedEstimator.estimateSpeed(objectID);

        Map<String, Object> vehicleData = new HashMap<>();
        vehicleData.put("license_plate", "ABC123");
        vehicleData.put("speed", estimatedSpeed);
        vehicleData.put("latitude", 37.7749); // Optional: GPS latitude
        vehicleData.put("longitude", -122.4194); // Optional: GPS longitude
        vehicleData.put("image_path", "path/to/image.jpg"); // Optional: Image path

        Call<Void> call = apiService.insertVehicleSpeed(vehicleData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("MainActivity", "Vehicle speed inserted successfully");
                } else {
                    Log.e("MainActivity", "Error inserting vehicle speed: " + response.errorBody().toString());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("MainActivity", "Error inserting vehicle speed: " + t.getMessage());
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
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.sidecar);

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
        retriever.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.sidecar));

        // Start looping through frames
        handler = new Handler();
        currentFrame = 0;
        loopThroughFrames();
    }

    private void loopThroughFrames() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Retrieve the total frame count using the correct metadata key
                int totalFrames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null
                        ? Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) : 1;

                long frameDuration = (long) ((1.0 / frameRate) * 1000000); // Convert to microseconds
                long currentTime = currentFrame * frameDuration;
                Bitmap frame = retriever.getFrameAtTime(currentTime, MediaMetadataRetriever.OPTION_CLOSEST);

                if (frame != null) {
                    Mat matFrame = new Mat();
                    Utils.bitmapToMat(frame, matFrame);

                    // Apply object detection using the instance of ObjectDetector
                    Mat detectedMat = objectDetector.recognizeImage(matFrame);

                    speedEstimator = new SpeedEstimator(frameRate);

                    // Convert the processed Mat back to a Bitmap
                    Bitmap detectedBitmap = Bitmap.createBitmap(detectedMat.cols(), detectedMat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(detectedMat, detectedBitmap);

                    // Display the processed frame with object detection in the ImageView
                    runOnUiThread(() -> imageView.setImageBitmap(detectedBitmap));

                    //was   imageView.setImageBitmap(detectedBitmap);
                }

                currentFrame++;
                if (currentTime < totalFrames * 1000) { // Compare with duration in microseconds
                    loopThroughFrames(); // Continue to the next frame
                } else {
                    resetUI(); // Reset the UI when the video ends
                }
            }
        }, 1000 / frameRate); // Delay based on frame rate (milliseconds)
    }


    private void resetUI() {
        // Show buttons again and hide the ImageView
        camera_button.setVisibility(View.VISIBLE);
        video_button.setVisibility(View.VISIBLE);
        image_button.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

    }
}