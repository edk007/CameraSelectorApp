package com.edtest.cameraselectorapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String APPID = "CSA";
    private static final String TAG = "MAIN_ACTIVITY: ";

    //CAMERA
    private TextureView textureView;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private static final String CAMERA_BACKGROUND_THREAD = "CAMERA_BACKGROUND_THREAD";
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private static Size imageDimension;
    private ImageReader imageReader;
    private static Handler mBackgroundHandler;
    private static HandlerThread mBackgroundThread;

    LinearLayout ll_camera_selector2;
    ListView camera_details_listView;
    ArrayAdapter arrayAdapter;
    ArrayList<String> cameraDetails = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Hide UI Elements - Full Screen Mode
        Utils.hideSystemUI(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //SCREEN ON AT ALL TIMES
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //SCREEN BRIGHTNESS SETTING
        WindowManager.LayoutParams MainActivity = getWindow().getAttributes();
        MainActivity.screenBrightness = 1F;
        getWindow().setAttributes(MainActivity);

        camera_details_listView =findViewById(R.id.camera_details_listView);
        ll_camera_selector2 = findViewById(R.id.ll_camera_selector2);
        arrayAdapter = new ArrayAdapter(this, R.layout.row_layout, R.id.label, cameraDetails);
        camera_details_listView.setAdapter(arrayAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //stop_camera_thread();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            //textureView = (TextureView) findViewById(R.id.texture);
            textureView = findViewById(R.id.texture);
            assert textureView != null;
            textureView.setSurfaceTextureListener(textureListener);
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Must grant camera permission", Toast.LENGTH_LONG).show();
            } else {
                startCamera();
            }
        }
    }//onRequestPermissionsResult

    private void startCamera() {
        createCameraSelector();
        start_camera_thread();
        if (textureView.isAvailable()) {
            openCamera(0);
            setCameraDetails(0);
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(0);
            setCameraDetails(0);
            Log.w(APPID, TAG + "CAMERA_TEXTURE_LISTENER: ON_SURFACE_TEXTURE_AVAILABLE");
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.w(APPID, TAG + "CAMERA_STATE_CALL_BACK: ON_OPENED");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            try {
                cameraDevice.close();
                cameraDevice = null;
                Log.w(APPID, TAG + "CAMERA_STATE_CALL_BACK: ERROR");
            } catch (Exception e) {
                Log.w(APPID, TAG + "CAMERA_STATE_CALL_BACK_EXCEPTION: " + e.toString());
                e.printStackTrace();
            }
        }
    };

    protected static void start_camera_thread() {
        Log.w(APPID, TAG + "START_CAMERA_THREAD");
        mBackgroundThread = new HandlerThread(CAMERA_BACKGROUND_THREAD);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected static void stop_camera_thread() {
        Log.w(APPID, TAG + "STOP_CAMERA_THREAD");
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.w(APPID, TAG + "STOP_CAMERA_THREAD_EXCEPTION: " + e.toString());
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        Log.w(APPID, TAG + "CREATE_CAMERA_PREVIEW");
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.w(APPID, TAG + "CREATE_CAMERA_PREVIEW: CONFIGURE_FAILED");
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.w(APPID, TAG + "CREATE_CAMERA_PREVIEW_EXCEPTION: " + e.toString());
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    public void openCamera(int camera) {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        Log.w(APPID, TAG + "OPEN_CAMERA: START");
        try {
            String cameraId = manager.getCameraIdList()[camera];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[camera];
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Log.w(APPID, TAG + "OPEN_CAMERA_EXCEPTION: " + e.toString());
            e.printStackTrace();
        }
        Log.w(APPID, TAG + "OPEN_CAMERA: END");
    }

    protected void updatePreview() {
        Log.w(APPID, TAG + "CREATE_CAMERA_PREVIEW: UPDATE_PREVIEW");
        if(null == cameraDevice) {
            Log.w(APPID, TAG + "CREATE_CAMERA_PREVIEW: UPDATE_PREVIEW_ERROR");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.w(APPID, TAG + "UPDATE_PREVIEW_EXCEPTION: " + e.toString());
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void createCameraSelector() {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        Log.w(APPID, TAG + "CREATE_CAMERA_SELECTOR");

        try {
            int numCams = manager.getCameraIdList().length;
            final RadioButton[] rb = new RadioButton[numCams];
            RadioGroup rg = new RadioGroup(this);
            rg.setOrientation(RadioGroup.VERTICAL);
            //TODO - enable selecting a different camera
            //TODO - when selecting a different camera - write out all camera characteristics in the UI below the selector somewhere - use a simple list view for this

            int i=0;

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                rb[i]  = new RadioButton(this);
                rb[i].setText("Camera ID: " + cameraId);
                rb[i].setId(i + 100);
                rb[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        changeCamera(Integer.parseInt(cameraId));
                    }
                });
                if (cameraId.equals("0")) {
                    rb[i].setChecked(true);
                }
                rg.addView(rb[i]);
                i++;
            }
            ll_camera_selector2.addView(rg);
        } catch (Exception e) {
            Log.w(APPID, TAG + "CREATE_CAMERA_SELECTOR_EXCEPTION: " + e.toString());
        }

    }

    private void setCameraDetails(int camera) {
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        Log.w(APPID, TAG + "SET_CAMERA_DETAILS");
        cameraDetails.clear();
        try {
            String cameraId = manager.getCameraIdList()[camera];
            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
            cameraDetails.add("Camera: " + cameraId);
            cameraDetails.add("Facing: " + chars.get(CameraCharacteristics.LENS_FACING));
            cameraDetails.add("Sensor Physical Size: " + chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE).toString());
            cameraDetails.add("Max Digital Zoom: " + chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).toString());
            cameraDetails.add("Control Zoom Ratio: " + chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE).toString());
            float[] aperatures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
            for (float a : aperatures) {
                cameraDetails.add("Lens Aperatures: " + a);
            }
            int[] ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
            for (int o : ois) {
                cameraDetails.add("Lens OIS: " + o);
            }
            float[] lengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            for (float l : lengths) {
                cameraDetails.add("Lens Focal Lengths: " + l);
            }
            cameraDetails.add("Sensor Pixel Array Size: " + chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).toString());
            cameraDetails.add("Supported Hardware Level: " + chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL).toString());
            //limited=0, full=1, legacy=2
            //CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[camera];
            for (Size size : map.getOutputSizes(SurfaceTexture.class)) {
                cameraDetails.add("Image Dimension " + size);
            }
        } catch (Exception e) {
            Log.w(APPID, TAG + "SET_CAMERA_DETAILS_EXCEPTION: " + e.toString());
        }
        arrayAdapter.notifyDataSetChanged();

    }

    private void changeCamera(int camera) {
        //openCamera(camera);
        setCameraDetails(camera);
    }

}