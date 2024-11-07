package com.example.javaapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackingActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "TrackingActivity";
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private CameraBridgeViewBase mCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera); // Use the same layout as CameraActivity
        mOpenCvCameraView = findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mHandlerThread = new HandlerThread("CameraProcessingThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV initialization succeeded");
            mOpenCvCameraView.enableView();
        } else {
            Log.d(TAG, "OpenCV initialization failed");
            // Handle the error, maybe show a message to the user
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show();
            finish(); // Close the activity if OpenCV fails to initialize
        }
        if (mCameraView != null) {
            mCameraView.enableView();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraView != null) {
            mCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraView != null) {
            mCameraView.disableView();
        }
        // Stop the handler thread
        mHandlerThread.quitSafely();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        // Convert to HSV color space
        Mat hsv = new Mat();
        Imgproc.cvtColor(mRgba, hsv, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // Define color range for detection (e.g., red)
        Scalar lowerBound = new Scalar(0, 70, 50); // Lower bound for red
        Scalar upperBound = new Scalar(10, 255, 255); // Upper bound for red

        // Create a mask for the defined color
        Mat mask = new Mat();
        Core.inRange(hsv, lowerBound, upperBound, mask);

        // Find contours in the mask
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Draw contours on the original frame
        for (MatOfPoint contour : contours) {
            Imgproc.drawContours(mRgba, contours, -1, new Scalar(255, 0, 0), 2); // Draw in blue
        }

        return mRgba; // Return the processed frame with contours
    }


}
