package com.example.javaapp;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import org.checkerframework.checker.units.qual.A;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
//import org.tensorflow.lite.gpu.GpuDelegate;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ObjectDetector {
    private Map<Integer, Rect> trackedObjects = new HashMap<>();
    private int nextObjectId = 1;
    private Interpreter interpreter;
    private List<String> labelList;
    private int INPUT_SIZE;
    private int PIXEL_SIZE=3;
    private int IMAGE_MEAN=0;
    private  float IMAGE_STD=255.0f;
    //private GpuDelegate gpuDelegate;
    private int height=0;
    private  int width=0;




    ObjectDetector(AssetManager assetManager,String modelPath, String labelPath,int inputSize) throws IOException{
        INPUT_SIZE=inputSize;
        Interpreter.Options options=new Interpreter.Options();
        //gpuDelegate=new GpuDelegate();
        //options.addDelegate(gpuDelegate);
        options.setNumThreads(4);
        interpreter=new Interpreter(loadModelFile(assetManager,modelPath),options);
        labelList=loadLabelList(assetManager,labelPath);
    }




    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList=new ArrayList<>();
        BufferedReader reader=new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line=reader.readLine())!=null ){

            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor=assetManager.openFd(modelPath);
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset =fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }
    public Mat recognizeImage(Mat mat_image) {
        Mat rotated_mat_image = new Mat();
        Core.flip(mat_image.t(), rotated_mat_image, 1);
        Bitmap bitmap = Bitmap.createBitmap(rotated_mat_image.cols(), rotated_mat_image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rotated_mat_image, bitmap);
        height = bitmap.getHeight();
        width = bitmap.getWidth();

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);
        Object[] input = new Object[1];
        input[0] = byteBuffer;
        Map<Integer, Object> output_map = new TreeMap<>();
        float[][][] boxes = new float[1][10][4];
        float[][] scores = new float[1][10];
        float[][] classes = new float[1][10];
        output_map.put(0, boxes);
        output_map.put(1, classes);
        output_map.put(2, scores);
        interpreter.runForMultipleInputsOutputs(input, output_map);

        for (int i = 0; i < 10; i++) {
            float class_value = (float) Array.get(Array.get(classes, 0), i);
            float score_value = (float) Array.get(Array.get(scores, 0), i);
            if (score_value > 0.5) {
                Object box = Array.get(Array.get(boxes, 0), i);
                float top = (float) Array.get(box, 0) * height;
                float left = (float) Array.get(box, 1) * width;
                float bottom = (float) Array.get(box, 2) * height;
                float right = (float) Array.get(box, 3) * width;




                if (class_value == 2 || class_value == 3 || class_value == 5 || class_value == 7) {
                    // Draw the bounding box
                    Imgproc.rectangle(rotated_mat_image, new Point(left, top), new Point(right, bottom), new Scalar(0, 255, 0, 255), 2);

                    // Get the object ID for this bounding box
                    int objectId = getObjectID(new Rect((int) left, (int) top, (int) (right - left), (int) (bottom - top)));

                    // Draw the label text on the Bitmap using Canvas
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(40);
                    paint.setStyle(Paint.Style.FILL);

                    // Draw the class label
                    String labelText = labelList.get((int) class_value);
                    canvas.drawText(labelText, left, top - 10, paint);
                    // Draw the object ID
                    canvas.drawText("ID: " + objectId, left, top - 40, paint);

                    Log.d("ObjectDetector", "Detected: " + labelText +
                            " (Class: " + class_value + ") ID: " + objectId + " | BoundingBox: [" + left + ", " + top + "] to [" + right + ", " + bottom + "]");
                }
            }
        }

        Utils.bitmapToMat(bitmap, rotated_mat_image); // Convert back to Mat
        Core.flip(rotated_mat_image.t(), mat_image, 0);
        return mat_image;
    }

    private int getObjectID(Rect rect) {
        for (Map.Entry<Integer, Rect> entry : trackedObjects.entrySet()) {
            if (isSameObject(entry.getValue(), rect)) {
                return entry.getKey();
            }
        }
        int id = nextObjectId++;
        trackedObjects.put(id, rect);
        return id;
    }




    // Helper function to determine if two rectangles represent the same object
    private boolean isSameObject(Rect previous, Rect current) {
        return Math.abs(previous.x - current.x) < 30 && Math.abs(previous.y - current.y) < 30;
    }
    public List<Recognition> detect(Bitmap bitmap) {
        // Your object detection logic goes here
        // For example, after detecting objects, you can create Recognition objects:
        List<Recognition> recognitions = new ArrayList<>();

        // Example of adding a recognition (replace with your actual detection logic)
        recognitions.add(new Recognition("car", new RectF(100, 100, 200, 200))); // Example bounding box

        return recognitions;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;
        int quant=0;
        int size_images=INPUT_SIZE;
        if(quant==0){
            byteBuffer=ByteBuffer.allocateDirect(1*size_images*size_images*3);
        }
        else {
            byteBuffer=ByteBuffer.allocateDirect(4*1*size_images*size_images*3);
        }
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_images*size_images];
        bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        int pixel=0;
        for (int i=0;i<size_images;++i){
            for (int j=0;j<size_images;++j){
                final  int val=intValues[pixel++];
                if(quant==0){
                    byteBuffer.put((byte) ((val>>16)&0xFF));
                    byteBuffer.put((byte) ((val>>8)&0xFF));
                    byteBuffer.put((byte) (val&0xFF));
                }
                else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val >> 8) & 0xFF))/255.0f);
                    byteBuffer.putFloat((((val) & 0xFF))/255.0f);
                }
            }
        }
        return byteBuffer;
    }

}