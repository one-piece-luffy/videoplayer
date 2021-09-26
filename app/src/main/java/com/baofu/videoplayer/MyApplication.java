package com.baofu.videoplayer;

import android.app.Application;

import com.baofu.base.BaseApplication;
import com.baofu.base.utils.CrashHandler;
import com.yc.kernel.utils.PlayerConstant;
import com.yc.kernel.utils.PlayerFactoryUtils;
import com.yc.video.config.VideoPlayerConfig;

import cn.mahua.av.BuriedPointEventImpl;

public class MyApplication extends BaseApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());

        //yc播放器配置，注意：此为全局配置，按需开启
        com.yc.video.player.VideoViewManager.setConfig(VideoPlayerConfig.newBuilder()
                //设置上下文
                .setContext(this)
                //设置视频全局埋点事件
                .setBuriedPointEvent(new BuriedPointEventImpl())
                //调试的时候请打开日志，方便排错
                .setLogEnabled(true)
                //设置exo
                .setPlayerFactory(PlayerFactoryUtils.getPlayer(PlayerConstant.PlayerType.TYPE_EXO))
                .setPlayOnMobileNetwork(true)
                .setEnableOrientation(false)
                //创建SurfaceView
                //.setRenderViewFactory(SurfaceViewFactory.create())
                .build());


    }
}
