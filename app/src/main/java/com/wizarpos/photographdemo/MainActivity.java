package com.wizarpos.photographdemo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "photograph";
    private SurfaceHolder surfaceHolder;
    private LinearLayout ll_pre;
    private LinearLayout ll_post;
    private Camera camera;
    private Bundle bundle = null;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = MainActivity.this;
        initView();
    }

    private void initView() {
        ll_pre = (LinearLayout) findViewById(R.id.ll_pre);
        ll_post = (LinearLayout) findViewById(R.id.ll_post);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        Button btn_take = (Button) findViewById(R.id.btn_take);
        Button btn_scan = (Button) findViewById(R.id.btn_scan);
        Button switch_camera = (Button) findViewById(R.id.btn_switch);
        Button btn_store = (Button) findViewById(R.id.btn_store);
        Button btn_cancel = (Button) findViewById(R.id.btn_cancel);

        btn_take.setOnClickListener(this);
        btn_scan.setOnClickListener(this);
        btn_store.setOnClickListener(this);
        btn_cancel.setOnClickListener(this);
        switch_camera.setOnClickListener(this);
        surfaceHolder = surfaceView.getHolder();

        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.addCallback(new SurfaceCallback());
        surfaceHolder.setKeepScreenOn(true);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take:  // Take photo
                if (camera != null) {
                    initCameraRotation(camera);
                    Camera.Parameters param = camera.getParameters();
                    List<Camera.Size> previewSizes = param.getSupportedPictureSizes();
                    Camera.Size size = previewSizes.get(0);
                    for (Camera.Size ss : previewSizes) {
                        Log.d("SupportedPictureSizes", "x=" + ss.width + ",y=" + ss.height);
                    }
                    param.setPictureSize(size.width, size.height);//如果不设置会按照系统默认配置最低160x120分辨率
                    camera.setParameters(param);
                    camera.takePicture(null, null, new MyPictureCallback());
                }
                break;
            case R.id.btn_scan: // Check photo

                Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getIntent.setType("image/*");
                Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickIntent.setType("image/*");
                Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {pickIntent});
                startActivityForResult(chooserIntent, 1);
                break;
            case R.id.btn_switch: // SwitchCamera
                int cameraCount = Camera.getNumberOfCameras();//获取摄像头数量
                Log.d(TAG, "cameraCount   cameraCount   =   " + cameraCount);
                showDialog();
                break;
            case R.id.btn_cancel: // Cancel
                ll_pre.setVisibility(View.VISIBLE);
                ll_post.setVisibility(View.GONE);
                camera.startPreview(); // restart preview
                break;
            case R.id.btn_store: // Save picture
                Log.d(TAG, "bundle = " + bundle);
                byte[] data = bundle.getByteArray("bytes");
                try {
                    saveToSDCard(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ll_pre.setVisibility(View.VISIBLE);
                ll_post.setVisibility(View.GONE);
                camera.startPreview();
                break;
        }
    }


    private void changeCamera(int cameraId, SurfaceHolder holder) {
        camera.stopPreview();//停掉原来摄像头的预览
        camera.release();//释放资源
        camera = null;//取消原来摄像头
        camera = Camera.open(cameraId);//打开当前选中的摄像头
        try {
            camera.setPreviewDisplay(holder);//通过surfaceview显示取景画面
        } catch (IOException e) {
            e.printStackTrace();
        }
        //initCameraRotation(camera);
        camera.setDisplayOrientation(90);
        camera.startPreview();//开始预览
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    void initCameraRotation(Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Log.d(TAG, info.orientation + "    orientation");
        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int displayRotation = display.getRotation();
        int cwRotationFromNaturalToDisplay;
        switch (displayRotation) {
            case Surface.ROTATION_0:
                cwRotationFromNaturalToDisplay = 90;
                break;
            case Surface.ROTATION_90:
                cwRotationFromNaturalToDisplay = 180;
                break;
            case Surface.ROTATION_180:
                cwRotationFromNaturalToDisplay = 270;
                break;
            case Surface.ROTATION_270:
                cwRotationFromNaturalToDisplay = 0;
                break;
            default:
                // Have seen this return incorrect values like -90
                if (displayRotation % 90 == 0) {
                    cwRotationFromNaturalToDisplay = (360 + displayRotation) % 360;
                } else {
                    throw new IllegalArgumentException("Bad rotation: " + displayRotation);
                }
        }
        camera.setDisplayOrientation(cwRotationFromNaturalToDisplay);
        Log.i(TAG, "Display at: " + cwRotationFromNaturalToDisplay);
    }


    private void showDialog() {

        View view = View.inflate(mContext, R.layout.half_dialog_view, null);
        final EditText editText = (EditText) view.findViewById(R.id.dialog_edit);
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle("switch open camera index")//设置对话框的标题
                .setView(view)
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String inputId = editText.getText().toString();
                        Log.e(TAG, "inputId = " + inputId);
                        changeCamera(Integer.parseInt(inputId), surfaceHolder);
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e(TAG, "request =" + requestCode + "result = " + resultCode);
        if (requestCode == 1 && data != null) {
            Intent intent = new Intent(mContext, ShowPicture.class);
            Uri uri = data.getData();
            intent.setData(uri);
            startActivity(intent);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private final class MyPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            Log.d(TAG, "PictureCallback = " + data.length);
            bundle = new Bundle();
            bundle.putByteArray("bytes", data);
            ll_pre.setVisibility(View.GONE);
            ll_post.setVisibility(View.VISIBLE);
        }
    }


    private boolean isPreview = false;

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open(0);
            initParam();
            try {
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //initCameraRotation(camera);
            camera.setDisplayOrientation(90);
            camera.startPreview();
            isPreview = true;

            //another way to focus, but have some bugs.

//            timer = new Timer();
//            timer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    Log.e(TAG, "prepare auto focus" + isPreview);
//                    if (isPreview) {
//                        camera.autoFocus(new Camera.AutoFocusCallback() {
//                            @Override
//                            public void onAutoFocus(boolean success, Camera camera) {
//                                camera.setOneShotPreviewCallback(null);
//                                Toast.makeText(mContext, "Focus success", Toast.LENGTH_SHORT).show();
//                                camera.cancelAutoFocus();
//                            }
//                        });
//
//                    }
//                }
//            }, 1000, 6000);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.e(TAG, "destroy + " + isPreview);
            if (camera != null && isPreview) {
                isPreview = false;
                camera.stopPreview();
                camera.cancelAutoFocus();
                camera.release();
                camera = null;
            }
        }
    }

    private void initParam() {
        Camera.Parameters parameters = camera.getParameters();
        int PreviewWidth = 0;
        int PreviewHeight = 0;
        // 选择合适的预览尺寸
        List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
//        You can check the properties your camera support.
        for (Camera.Size size : sizeList) {
            Log.e(TAG, size.width + ":" +
                    size.height + "\n");
        }
        // 如果sizeList只有一个我们也没有必要做什么了，因为就他一个别无选择
        if (sizeList.size() > 1) {
            Iterator<Camera.Size> itor = sizeList.iterator();
            while (itor.hasNext()) {
                Camera.Size cur = itor.next();
                if (cur.width >= PreviewWidth && cur.height >= PreviewHeight) {
                    PreviewWidth = cur.width;
                    PreviewHeight = cur.height;
                    break;
                }
            }
//        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
//        for (String focus : supportedFocusModes) {
//            Log.e(TAG, focus + "\n");
//        }
//
//        List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
//        for (Camera.Size size : supportedPictureSizes) {
//            Log.e(TAG, size.width + ":" + size.height + "\n");
//        }

            parameters.setPreviewSize(PreviewWidth, PreviewHeight);  // Preview Size.
//        parameters.setPictureSize(1024, 768);   // The picture size. You real get.
//        parameters.setJpegQuality(100);
//        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE); // auto focus
            camera.setParameters(parameters);
        }
    }

    /**
     * Save photo to SDCard
     *
     * @param data
     * @throws IOException
     */

    public void saveToSDCard(byte[] data) throws IOException {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String filename = format.format(date) + ".jpg";
        File fileFolder = new File(Environment.getExternalStorageDirectory()
                + "/Pictures/");
        Log.e(TAG, "fileName = " + filename + "\npath = " + fileFolder);
        if (!fileFolder.exists()) {
            Log.e(TAG, "Directory not exist.");
            fileFolder.mkdir();
        }
        File jpgFile = new File(fileFolder, filename);
        FileOutputStream outputStream = new FileOutputStream(jpgFile);
        outputStream.write(data);
        outputStream.close();

        //Send broadcast to update photo album. If not, you won't find the picture you saved in photo album.
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(jpgFile);
        intent.setData(uri);
        mContext.sendBroadcast(intent);
    }
}