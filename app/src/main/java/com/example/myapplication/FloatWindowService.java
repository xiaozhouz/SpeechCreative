//悬浮窗服务
package com.example.myapplication;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.Objects;

public class FloatWindowService extends Service {
    private Context context;
    private Control controller;
    private MyReceiver receiver=null;
    private IntentFilter filter;
    public float speechEmotion_number=0;
    public float imageEmotion_number=1;

    public static void startService(Context context){
        context.startService(new Intent(context,FloatWindowService.class));
    }

    public static void stopService(Context context){
        context.stopService(new Intent(context,FloatWindowService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        receiver=new MyReceiver();
        filter=new IntentFilter();
        filter.addAction("com.example.myapplication.SpeechRecogService");
        filter.addAction("com.example.myapplication.ScreenCaptureService");
        this.registerReceiver(receiver,filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent!=null) {
            if(controller != null) {
                controller.onDestroy();
                controller = null;
            }
            controller = Control.getInstance(context);
            controller.init();
        }
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if(controller != null){
            this.unregisterReceiver(receiver);
            controller.onDestroy();
        }
    }

    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(Objects.requireNonNull(intent.getAction())) {
                case "com.example.myapplication.SpeechRecogService": {
                    Bundle bundle=intent.getExtras();
                    speechEmotion_number=bundle.getFloat("speechPoint");
                    setProgress(speechEmotion_number,imageEmotion_number);
                    Log.e("FloatWindow",String.valueOf(speechEmotion_number));
                    //controller.prog.setProgress(count);
                    break;
                }
                case "com.example.myapplication.ScreenCaptureService": {
                    Bundle bundle=intent.getExtras();
                    imageEmotion_number=bundle.getFloat("imagePoint");
                    setProgress(speechEmotion_number,imageEmotion_number);
                    Log.e("FloatWindowService",String.valueOf(imageEmotion_number));
                    //controller.prog.setProgress(count);
                    break;
                }
            }
        }
        public void setProgress(float speechEmotionNumber,float imageEmotionNumber) {
            //具体算法...
            float emotionNumber=(float)(speechEmotionNumber*0.6+imageEmotionNumber*0.4);
            int emotionNumber_int=Math.round(emotionNumber*100);
            Log.e("FloatWindowService_____",String.valueOf(emotionNumber_int));
            controller.prog.setProgress(emotionNumber_int);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
