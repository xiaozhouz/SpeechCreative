//获取屏幕截图服务
package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Emotion;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ScreenCaptureService extends Service {
    public MediaProjectionManager mediaProjectionManager;
    public MediaProjection mediaProjection;
    public ImageReader mImageReader;
    private String mImagePath;
    public WindowManager mWindowManager;
    public int mWindowWidth;
    public int mWindowHeight;
    public int mScreenDensity;
    private String mImageName;
    private static final String TAG = "ScreenCaptureService";
    private Bitmap mBitmap;
    private static Intent mResultData=null;
    private int mResultCode;
    public VirtualDisplay mVirtualDisplay;

    //我的人脸识别API密钥
    private final String apiEndpoint = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0";
    private final String subscriptionKey = "581b5f7e3c964b4dbd210425d979c787";

    public ScreenCaptureService() {
    }

    public static void setResultData(Intent data) {
        mResultData=data;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();
        //初始化录屏
        init();
        //开启前台服务
        if(Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    private static final String CHANNEL_ID="2";

    @RequiresApi(api=Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        //Intent intent=new Intent(this,MainActivity.class);
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(this,MainActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pi=PendingIntent.getActivity(this,0,intent,0);
        String channelName="channelName_";

        int importance= NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,channelName,importance);
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL_ID);
        builder.setContentTitle("正在录屏")
                .setContentText("Continue Screening...")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setChannelId(CHANNEL_ID)
                .setContentIntent(pi);
        Notification notification=builder.build();
        startForeground(2,notification);

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //监听器，要等截屏图片可以取到以后，才可以进行截屏
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try {
                    startCapture(reader);//截屏
                    //Log.e("Me","线程已休眠！");
                    Thread.sleep(1000);//大概每10s截一次屏
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            },getBackgroundHandler());

        //下面是用定时器写的，但是好像时间间隔设成10s还是会出现取不到截屏图片的情况，所以就算了
        /*Timer timer = new Timer(true);
        TimerTask task=new TimerTask() {
            @Override
            public void run() {
                startCapture(mImageReader);
            }
        };
        timer.schedule(task,2000,10000);*/
        return START_STICKY;
    }
    Handler backgroundHandler;

    private Handler getBackgroundHandler() {
        if (backgroundHandler == null) {
            HandlerThread backgroundThread =
                    new HandlerThread("catwindow", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                }
            };
        }
        return backgroundHandler;
    }

    //初始化录屏
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void init() {
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        assert mWindowManager != null;
        mWindowWidth = mWindowManager.getDefaultDisplay().getWidth();
        mWindowHeight = mWindowManager.getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);
        mScreenDensity = displayMetrics.densityDpi;

        mImageReader = ImageReader.newInstance(mWindowWidth, mWindowHeight, PixelFormat.RGBA_8888, 2);

        mediaProjectionManager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        assert mediaProjectionManager != null;
        mediaProjection=mediaProjectionManager.getMediaProjection(Activity.RESULT_OK,mResultData);
        mVirtualDisplay = mediaProjection.createVirtualDisplay("screen-mirror",
                mWindowWidth, mWindowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);

        mImagePath = Environment.getExternalStorageDirectory().getPath() + "/jietu/";
    }

    //截屏
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void startCapture(ImageReader mImageReader_) {
        mImageName = "截图" + System.currentTimeMillis() + ".png";
        //Log.i(TAG, "image name is : " + mImageName);
        Log.e("Me","开始截图，");
        Image image = mImageReader_.acquireLatestImage();
        if (image == null) {
            Log.e(TAG, "image is null.");
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        mBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        mBitmap.copyPixelsFromBuffer(buffer);
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height);
        image.close();
        Log.e("Me","截图完成，");
        saveToFile();
        //人脸表情识别API调用的函数，直接将截好的Bitmap（也就是变量mBitmap）传过来识别，就不用先存到文件里面去了
        //当然如果要把图片传到后台的话再改
        emotionDetect();

    }

    //保存截屏文件
    public void saveToFile() {
        try {
            File fileFolder = new File(mImagePath);
            if (!fileFolder.exists())
                fileFolder.mkdirs();
            File file = new File(mImagePath, mImageName);
            if (!file.exists()) {
                //Log.d(TAG, "file create success ");
                file.createNewFile();
            }
            FileOutputStream out = new FileOutputStream(file);
            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            //Log.d(TAG, "file save success ");
        } catch (IOException e) {
            //Log.e(TAG, e.toString());
            Toast.makeText(getApplicationContext(), "截图失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    //人脸表情识别API调用，直接将截好的Bitmap传过来识别，就不用先存到文件里面去了
    public void emotionDetect() {
        final FaceServiceClient faceServiceClient =
            new FaceServiceRestClient(apiEndpoint, subscriptionKey);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());
        @SuppressLint("StaticFieldLeak")
        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    String exceptionMessage = "";

                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                    // returnFaceAttributes:
                                    new FaceServiceClient.FaceAttributeType[] {
                                            FaceServiceClient.FaceAttributeType.Age,
                                            FaceServiceClient.FaceAttributeType.Gender,
                                            FaceServiceClient.FaceAttributeType.Emotion}
                            );
                            if (result == null){
                                publishProgress(
                                        "Detection Finished. Nothing detected");
                                return null;
                            }
                            Face resultFace=result[0];
                            if(result.length>1) {
                                for(Face face : result) {
                                    FaceRectangle faceRectangle = face.faceRectangle;
                                    if(faceRectangle.height*faceRectangle.width>
                                            resultFace.faceRectangle.height*resultFace.faceRectangle.width) {
                                        resultFace=face;
                                    }
                                }
                            }
                            //调用API后结果会给出该人脸表情的happiness、neutral、anger、contempt等八个方面的具体数字
                            //可以把这8个数字传到后台去
                            Log.e("Me","人脸：");
                            /*Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.happiness));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.neutral));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.anger));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.contempt));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.disgust));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.surprise));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.fear));
                            Log.e("Me",String.valueOf(resultFace.faceAttributes.emotion.sadness));*/
                            Emotion emotion=resultFace.faceAttributes.emotion;
                            float imageEmotion=(float)(emotion.happiness+0.5*emotion.neutral-emotion.anger-emotion.contempt-emotion.disgust-emotion.fear-emotion.sadness);
                            //均一化
                            float imageEmotion_number=(imageEmotion+1)/2;
                            Intent intent=new Intent();
                            //int imageEmotion_number=Math.round(imageEmotion_);
                            intent.putExtra("imagePoint", imageEmotion_number);
                            intent.setAction("com.example.myapplication.ScreenCaptureService");
                            sendBroadcast(intent);
                            return result;
                        } catch (Exception e) {
                            exceptionMessage = String.format(
                                    "Detection failed: %s", e.getMessage());
                            return null;
                        }
                    }
                };
        detectTask.execute(inputStream);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
