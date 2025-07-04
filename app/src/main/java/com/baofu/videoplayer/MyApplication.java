package com.baofu.videoplayer;

import com.baofu.base.BaseApplication;
import com.baofu.base.utils.CrashHandler;
import com.baofu.cache.downloader.rules.CacheDownloadManager;
import com.baofu.cache.downloader.utils.VideoDownloadConfig;
import com.jeffmony.videocache.VideoProxyCacheManager;
import com.yc.kernel.utils.PlayerConstant;
import com.yc.kernel.utils.PlayerFactoryUtils;
import com.yc.video.config.VideoPlayerConfig;

import java.io.File;

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
        File saveFile =getExternalCacheDir();
        if (!saveFile.exists()) {
            saveFile.mkdir();
        }
        //边下边播配置
        VideoProxyCacheManager.Builder builder = new VideoProxyCacheManager.Builder(this).
                setFilePath(saveFile.getAbsolutePath()).    //缓存存储位置
                setConnTimeOut(60 * 1000).                  //网络连接超时
                setReadTimeOut(60 * 1000).                  //网络读超时
                setExpireTime(2 * 24 * 60 * 60 * 1000).     //2天的过期时间
                setMaxCacheSize(2 * 1024 * 1024 * 1024L);    //2G的存储上限
        VideoProxyCacheManager.getInstance().initProxyConfig(builder.build());


        //播放器预加载初始化
//        VideoDownloadManager.getInstance().downloadDir = AppConfig.DOWNLOAD_DIR;

        try {
            VideoDownloadConfig config = new VideoDownloadConfig.Builder(this)
//                    .publicPath(file.getAbsolutePath())
                    .privatePath(saveFile.getAbsolutePath())
                    .connTimeOut(20)
                    .readTimeOut(20)
                    .writeTimeOut(20)
                    .retryCount(2)
                    .concurrentCount(2) //并发数
                    .context(this)
                    .threadSchedule(true)
                    .build();
            CacheDownloadManager.getInstance().initConfig(config);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
