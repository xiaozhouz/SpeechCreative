//悬浮窗所需类
package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
//import android.util.Log;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
//import android.view.ViewConfiguration;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;


import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Control {
    //绘图

    private final int REFRESH = 0x003;
    private static Control instance;
    private Context context;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams wParamsTop;
    private int lastX, lastY;
    private int downX, downY;
    private int lastX_, lastY_;
    //down 时的时间戳
    private int downTime;
    //布局
    private LinearLayout layoutTop;
    //屏幕宽高
    private int screenWidth, screenHeight;
    //top xy坐标, bottom y坐标
    private int topY, bottomY, topX;
    //top 的宽高
    private int topWidth, topHeight;
    //进度条
    public ProgressBar prog;
    //状态栏的高度
    private int statusBarHeight;
    //最小滑动距离
    private int mTouchSlop;


    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    mWindowManager.updateViewLayout(layoutTop, wParamsTop);
                    break;
                default:
                    super.handleMessage(msg);
            }

        }
    };


    //构造函数
    private Control(Context context) {
        this.context = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        //获取status_bar_height资源的ID
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        //需要减去状态栏高度
        screenHeight = mWindowManager.getDefaultDisplay().getHeight() - statusBarHeight;
        topWidth = WindowHelper.dip2px(context, 40);
        topHeight = WindowHelper.dip2px(context, 40);

        //WindowHelper.setCoordinateX(context, 30);
        WindowHelper.setCoordinateY(context, 400);
        //mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void init(){
        Log.d("yzy", "!");
        topX = WindowHelper.getCoordinateX(context);
        topY = WindowHelper.getCoordinateY(context);

        initbar();
        String s = String.valueOf(wParamsTop.x);
        Log.e("yzy", "h"+s);
        mWindowManager.addView(layoutTop, wParamsTop);

    }

    /**
     * 初始化进度条视图
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initbar() {
        layoutTop = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.top_ac, null);
        prog = (ProgressBar) layoutTop.findViewById(R.id.progressBar);
        prog.setMax(100);
        prog.setProgress(0);

        prog.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d("yzy", "?");
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        //Log.e("yzy", "ke1");
                        downTime = (int) System.currentTimeMillis();

                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();

                        lastX_ = (int) event.getRawX();
                        lastY_ = (int) event.getRawY();

                        //保留相对距离，后面可以通过绝对坐标算出真实坐标
                        downX = (int) event.getX();
                        downY = (int) event.getY();

                        break;
                    case MotionEvent.ACTION_MOVE:
                        //Log.e("yzy", "ke2");
                        topX = (int) (event.getRawX() - downX);
                        //需要减去状态栏高度
                        topY = (int) (event.getRawY() - statusBarHeight - downY);

                        //top左右不能越界
                        if (topX < 0) {
                            topX = 0;
                        } else if ((topX + topWidth) > screenWidth) {
                            topX = screenWidth - topWidth;
                        }
                        wParamsTop.x = topX;

                        //top上下不能越界
                        if (topY < 0) {
                            topY = 0;
                        } else if ((topY + topHeight) > screenHeight) {
                            topY = screenHeight - topHeight;
                        }
                        wParamsTop.y = topY;
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        String s = String.valueOf(wParamsTop.x);
                        //Log.e("yzy", s);
                        handler.sendEmptyMessage(REFRESH);
                        break;
                    case MotionEvent.ACTION_UP:
                        //Log.e("yzy", "ke3");
                        int currentTime = (int) System.currentTimeMillis();
                        if (currentTime - downTime < 200 && Math.abs(event.getRawX() - lastX) < mTouchSlop && Math.abs(event.getRawY() - lastY) < mTouchSlop) {
                            //handler.sendEmptyMessage(CLICK);
                        }
                        WindowHelper.setCoordinateX(context, topX);
                        WindowHelper.setCoordinateY(context, topY);

                        double deltaX=Math.sqrt((event.getRawX()-lastX_)*(event.getRawX()-lastX_)+(event.getRawY()-lastY_)*(event.getRawY()-lastY_));
                        if(deltaX<10) {
                            Intent intent=new Intent();
                            intent.setAction("com.example.myapplication.Control");
                            context.sendBroadcast(intent);
                        }
                        break;
                }
                return true;
            }
        });
        //Log.e("yzy", "ke4");
        wParamsTop = new WindowManager.LayoutParams();
        wParamsTop.width = WindowManager.LayoutParams.WRAP_CONTENT;
        wParamsTop.height = WindowManager.LayoutParams.WRAP_CONTENT;
        //初始化坐标
        wParamsTop.x = topX;
        wParamsTop.y = topY;
        //弹窗类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wParamsTop.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            wParamsTop.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        //以左上角为基准
        wParamsTop.gravity = Gravity.START | Gravity.TOP;
        wParamsTop.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        //如果不加,背景会是一片黑色。
        wParamsTop.format = PixelFormat.RGBA_8888;
    }

    public static Control getInstance(Context context) {
        if (instance == null) {
            synchronized (Control.class) {
                if (instance == null) {
                    instance = new Control(context);
                }
            }
        }
        return instance;
    }

    public void onDestroy() {
        if (mWindowManager != null) {
            try {
                mWindowManager.removeView(layoutTop);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle=intent.getExtras();
            int count=bundle.getInt("speechPoint");
            prog.setProgress(count);
        }
    }*/
}
