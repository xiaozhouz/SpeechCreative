//.wav录音服务
package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.util.EventHandler;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tech.oom.idealrecorder.IdealRecorder;
import tech.oom.idealrecorder.StatusListener;

public class RecordingService extends Service {
    private String mFileName;
    private String mFilePath;
    //我的语音API密钥

    private MediaRecorder mRecorder;
    private IdealRecorder idealRecorder;
    private IdealRecorder.RecordConfig recordConfig;
    private Boolean isRunning=false;//是否处于录音状态

    private long mStartingTimeMillis;
    private long mElapsedMillis;

    private static final String LOG_TAG = "SoundRecorder";

    @Override
    public void onCreate() {
        super.onCreate();
        //.wav录音组件初始化
        isRunning=true;
        IdealRecorder.getInstance().init(this);
        idealRecorder = IdealRecorder.getInstance();
        recordConfig = new IdealRecorder.RecordConfig(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        //开启前台服务
        if(Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
    }

    private static final String CHANNEL_ID="1";

    @RequiresApi(api=Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        //Intent intent=new Intent(this,MainActivity.class);
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(this,MainActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pi=PendingIntent.getActivity(this,0,intent,0);
        String channelName="channelName";

        int importance= NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,channelName,importance);
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL_ID);
        builder.setContentTitle("正在录音")
                .setContentText("Continue Recording...")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setChannelId(CHANNEL_ID)
                .setContentIntent(pi);
        Notification notification=builder.build();
        startForeground(1,notification);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecording();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        /*if (mRecorder != null) {
            stopRecording();
        }*/
        //停止录音
        isRunning=false;
        idealRecorder.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    //.wav录音组件使用的监听器
    private StatusListener statusListener = new StatusListener() {
        @Override
        public void onStartRecording() {
            Toast.makeText(getApplicationContext(), "录音开始", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRecordData(short[] data, int length) {

            tech.oom.idealrecorder.utils.Log.d("MainActivity", "current buffer size is " + length);
        }

        @Override
        public void onVoiceVolume(int volume) {
            tech.oom.idealrecorder.utils.Log.d("MainActivity", "current volume is " + volume);
        }

        @Override
        public void onRecordError(int code, String errorMsg) {
        }

        @Override
        public void onFileSaveFailed(String error) {
            Toast.makeText(getApplicationContext(), "文件保存失败", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFileSaveSuccess(String fileUri) {
            Toast.makeText(getApplicationContext(), "文件保存成功,路径是" + fileUri, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopRecording() {
            //Toast.makeText(getApplicationContext(), "录音结束!!!!!!!!!!!!!!!!!!", Toast.LENGTH_SHORT).show();
            Log.e("Me","结束啦！！！！");
            if(isRunning) {
                idealRecorder.start();
            }
        }
    };

    // 开始录音
    public void startRecording() {
        setFileNameAndPath();
        idealRecorder.setRecordFilePath(mFilePath);
        idealRecorder.setRecordConfig(recordConfig);
        idealRecorder.setMaxRecordTime(10000);//录音最长录10秒
        idealRecorder.setStatusListener(statusListener);
        idealRecorder.start();

        //下面是用MediaRecorder录.amr音频的代码，但是Azure不能识别.amr文件
        /*mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT); //录音文件保存的格式，这里保存为 mp4
        mRecorder.setOutputFile(mFilePath); // 设置录音文件的保存路径
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mRecorder.setAudioChannels(1);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setAudioEncodingBitRate(192000);
        mRecorder.setMaxDuration(10000);
        mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
                    mRecorder.stop();
                }
            }
        });
        try {
            mRecorder.prepare();
            mRecorder.start();
            mStartingTimeMillis = System.currentTimeMillis();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }*/
        //idealRecorder = IdealRecorder.getInstance();

    }

    // 设置录音文件的名字和保存路径
    public void setFileNameAndPath() {
        /*File f;

        do {
            mFileName = "record"
                    + "_" + (System.currentTimeMillis()) + ".mp3";
            mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
            mFilePath += "/SoundRecorder/" + mFileName;
            f = new File(mFilePath);
        } while (f.exists() && !f.isDirectory());*/

        //录音文件路径与名字，现在设定的是新的录音文件会覆盖原有录音文件
        mFilePath=Environment.getExternalStorageDirectory()+"/ideal.wav";
    }

    //这个函数暂时没啥用
    public void stopRecording() {
        mRecorder.stop();
        mElapsedMillis = (System.currentTimeMillis() - mStartingTimeMillis);
        mRecorder.release();

        getSharedPreferences("sp_name_audio", MODE_PRIVATE)
                .edit()
                .putString("audio_path", mFilePath)
                .putLong("elpased", mElapsedMillis)
                .apply();

        mRecorder = null;
    }
}
