//语音识别服务
package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.util.EventHandler;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpeechRecogService extends Service {

    private static String speechSubscriptionKey = "dc09711878bd4e1b9e9340ebe09276b3";
    private static String serviceRegion = "westus";
    private static final String CHANNEL_ID="3";
    private SpeechRecognizer reco;

    public SpeechRecogService() {
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();
        //开启前台服务
        if(Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    @RequiresApi(api=Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        //Intent intent=new Intent(this,MainActivity.class);
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(this,MainActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pi=PendingIntent.getActivity(this,0,intent,0);
        String channelName="channelName__";

        int importance= NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,channelName,importance);
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL_ID);
        builder.setContentTitle("正在语音识别")
                .setContentText("Continue Recognizing...")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setChannelId(CHANNEL_ID)
                .setContentIntent(pi);
        Notification notification=builder.build();
        startForeground(3,notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startSpeechRecog();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        reco.stopContinuousRecognitionAsync();
        //reco.close();
        super.onDestroy();
    }

    //开始语音识别
    private void startSpeechRecog() {
        try {
            SpeechConfig config = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion);
            config.setSpeechRecognitionLanguage("zh-CN");
            reco = new SpeechRecognizer(config);
            reco.startContinuousRecognitionAsync();
            reco.recognizing.addEventListener(new EventHandler<SpeechRecognitionEventArgs>() {
                @Override
                public void onEvent(Object o, SpeechRecognitionEventArgs speechRecognitionEventArgs) {
                    SpeechRecognitionResult result=speechRecognitionEventArgs.getResult();
                    if (result.getReason() == ResultReason.RecognizingSpeech) {
                        String resultText=result.getText();
                        Log.e("Me",resultText);
                        //识别出的文本非空，就传到后台去做文本分析
                        if(!resultText.equals("")) {
                            //resultText是语音识别出来的文本，可以在这里把resultText传到后台
                            postRequest(resultText);
                        }
                    }
                }
            });
        } catch (Exception ex) {
            Log.e("SpeechSDKDemo", "unexpected " + ex.getMessage());
            assert(false);
        }
    }

    //用OkHttp传文本到后台，当然我不知道传什么到后台去，还没写完......
    private void postRequest(String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client=new OkHttpClient();
                    RequestBody requestBody=new FormBody.Builder()
                            .add("resultText",text)
                            .build();
                    //用自己电脑建的后端服务器做测试，外部无法访问
                    Request request=new Request.Builder()
                            .url("http://192.168.2.7:80/text_process")
                            .post(requestBody)
                            .build();
                    Response response=client.newCall(request).execute();
                    if(response.isSuccessful()) {
                        String responseData=response.body().string();
                        Log.e("Me","传值成功！");
                        Log.e("Me",responseData);
                        Intent intent=new Intent();
                        float speechPoint=Float.parseFloat(responseData);
                        //int count=Math.round(speechPoint*100);
                        intent.putExtra("speechPoint", speechPoint);
                        intent.setAction("com.example.myapplication.SpeechRecogService");
                        sendBroadcast(intent);
                    }
                    else {
                        Log.e("Me","传值失败！");
                        Log.e("Me",response.body().string());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
