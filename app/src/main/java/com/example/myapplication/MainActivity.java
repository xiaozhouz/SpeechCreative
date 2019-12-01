package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.internal.AudioStreamContainerFormat;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.util.EventHandler;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Future;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private static final int REQUEST_FLOAT_WINDOW = 2000;
    //我的语音API密钥
    private static String speechSubscriptionKey = "dc09711878bd4e1b9e9340ebe09276b3";
    private static String serviceRegion = "westus";
    //程序是否开启
    private boolean isStarted=false;
    private boolean isMicroOpen=false;
    private MyReceiver receiver=null;
    private IntentFilter filter;
    Button btn_control;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        receiver=new MyReceiver();
        filter=new IntentFilter();
        filter.addAction("com.example.myapplication.Control");
        this.registerReceiver(receiver,filter);
        //要求权限
        RxPermissions rxPermissions=new RxPermissions(this);
        rxPermissions.request(Manifest.permission.RECORD_AUDIO,
                              Manifest.permission.WRITE_EXTERNAL_STORAGE,
                              Manifest.permission.INTERNET)
                .subscribe(granted -> {
                    if(granted) {

                    } else {
                        Toast.makeText(MainActivity.this,"权限未授予，无法运行！",Toast.LENGTH_SHORT).show();
                    }
                });
        btn_control=(Button)findViewById(R.id.btn_controlService);
        btn_control.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //整个程序开始，开启后台麦克风、屏幕截图、悬浮窗
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startCapture() {
        openMicrophone();
        //要求悬浮窗权限，如果没有，先请求
        if(!Settings.canDrawOverlays(this)) {
            requestAlertWindowPermission();
        }
        else {
            //开启屏幕截图
            startScreenCapture();
            //开启悬浮窗
            WindowHelper.instance.setHasPermission(true);
            WindowHelper.instance.setForeground(true);
            WindowHelper.instance.startWindowService(getApplicationContext());
            //startSpeechRecog();
        }
    }

    //整个程序结束，关闭后台麦克风、屏幕截图、悬浮窗
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopCapture() {
        stopMicrophone();
        stopScreenCapture();
        WindowHelper.instance.stopWindowService(getApplicationContext());
    }

    //调用Azure做语音识别
    private void startSpeechRecog() {
        try {
            SpeechConfig config = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion);
            config.setSpeechRecognitionLanguage("zh-CN");
            assert(config != null);
            SpeechRecognizer reco = new SpeechRecognizer(config);
            reco.startContinuousRecognitionAsync();
            reco.recognized.addEventListener(new EventHandler<SpeechRecognitionEventArgs>() {
                @Override
                public void onEvent(Object o, SpeechRecognitionEventArgs speechRecognitionEventArgs) {
                    SpeechRecognitionResult result=speechRecognitionEventArgs.getResult();
                    if (result.getReason() == ResultReason.RecognizedSpeech) {
                        //Activity的TextView会显示识别出的文本
                        //txt.setText(result.getText());
                        reco.recognized.fireEvent(o, speechRecognitionEventArgs);
                    }
                }
            });
        } catch (Exception ex) {
            Log.e("SpeechSDKDemo", "unexpected " + ex.getMessage());
            assert(false);
        }
    }

    @SuppressLint("CheckResult")
    private void openMicrophone() {
        //上面这行被注释掉的intent是开启.wav录音服务，下面没有被注释掉的intent是开启语音识别服务
        //.wav录音服务和语音识别服务不能同时开启，如果是要传.wav文件到后台的话就在RecordingService里面传
        //Intent startIntent=new Intent(this,RecordingService.class);
        Intent startIntent=new Intent(this,SpeechRecogService.class);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            startForegroundService(startIntent);
        }
    }

    private void stopMicrophone() {
        //上面这行被注释掉的intent是停止.wav录音服务，下面没有被注释掉的intent是停止语音识别服务
        //Intent stopIntent=new Intent(this,RecordingService.class);
        Intent stopIntent=new Intent(this,SpeechRecogService.class);
        stopService(stopIntent);
    }

    //请求录屏权限
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent it=mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(it, REQUEST_MEDIA_PROJECTION);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopScreenCapture() {
        Intent stopIntent=new Intent(this,ScreenCaptureService.class);
        stopService(stopIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestAlertWindowPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_FLOAT_WINDOW);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_MEDIA_PROJECTION:
                //开启录屏
                if(resultCode==RESULT_OK&&data!=null) {
                    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                        Intent startIntent_=new Intent(MainActivity.this,ScreenCaptureService.class);
                        ScreenCaptureService.setResultData(data);
                        startForegroundService(startIntent_);
                    }
                }
                break;
            case REQUEST_FLOAT_WINDOW:
                //开启悬浮窗
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(MainActivity.this,"悬浮窗权限已授予！",Toast.LENGTH_SHORT).show();
                    WindowHelper.instance.setHasPermission(true);
                    WindowHelper.instance.setForeground(true);
                    WindowHelper.instance.startWindowService(getApplicationContext());
                }
                break;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btn_controlService:
                if(!isStarted) {
                    startCapture();
                    isStarted=true;
                    isMicroOpen=true;
                    btn_control.setText("Stop");
                    break;
                }
                else {
                    stopCapture();
                    isStarted=false;
                    isMicroOpen=false;
                    btn_control.setText("Start");
                    break;
                }
        }
    }

    public class MyReceiver extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isMicroOpen) {
                stopMicrophone();
                isMicroOpen=false;
                Toast.makeText(getApplicationContext(),"已关闭录音",Toast.LENGTH_SHORT).show();
            }
            else {
                openMicrophone();
                isMicroOpen=true;
                Toast.makeText(getApplicationContext(),"已开启录音",Toast.LENGTH_SHORT).show();
            }
        }
    }

}
