package com.baofu.videoplayer;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.allfootball.news.imageloader.ImageLoader;
import com.baofu.base.utils.CommonUtils;
import com.baofu.videocache.VideoInfoParseManager;
import com.baofu.videocache.VideoProxyCacheManager;
import com.baofu.videocache.control.LocalProxyVideoControl;
import com.baofu.videocache.listener.ISocketListener;
import com.baofu.videocache.utils.ProxyCacheUtils;
import com.yc.video.config.ConstantKeys;
import com.yc.video.player.OnVideoStateListener;
import com.yc.video.player.VideoPlayer;

import java.util.HashMap;
import java.util.Map;

import cn.mahua.av.SpeedInterface;
import cn.mahua.av.controller.AvNormalPlayController;
import cn.mahua.av.play.ControllerClickListener;

public class MainActivity extends AppCompatActivity {
    VideoPlayer videoView;
    AvNormalPlayController controller;
    LocalProxyVideoControl mLocalProxyVideoControl;
    String mUrl;
    //倍速播放速度
    String speed;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPlayer();
        init();
    }

    void initPlayer(){

        videoView = findViewById(R.id.videoView);
        controller = new AvNormalPlayController(this);
        //设置标题
        controller.setTitle("海贼王");
        controller.showTcpSpeed(true);
        //设置缓存提示信息
        controller.setLoadingMessage("正在缓冲，哈哈");
        View view= LayoutInflater.from(this).inflate(R.layout.av_tools_item,null);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.showToast("工具1");
            }
        });

        //隐藏下一集按钮
        controller.hideNextBtn();
        //添加自定义工具
        controller.addTools(view);
        controller.setControllerClickListener(new ControllerClickListener() {
            @Override
            public void onClick(View view) {

            }

            @Override
            public void share() {
                CommonUtils.showToast("share");
            }

            @Override
            public void next() {
                CommonUtils.showToast("next");
            }

            @Override
            public void tryFix() {

            }

            @Override
            public void onSpeedClick(String speed) {

            }

            @Override
            public void onUserSeek(long position) {
                if(mLocalProxyVideoControl!=null){
                    mLocalProxyVideoControl.seekToCachePosition(position,videoView.getDuration());
                }
            }

        });
        //设置播放器缩略图
        ImageLoader.getInstance().placeholder(R.drawable.a)
                .url("https://img0.baidu.com/it/u=1519898345,2471979106&fm=26&fmt=auto")
                .imageView(controller.getThumb())
                .loadImage(this);
        //是否展示底部进度条
        controller.showBottomProgress(true);
        controller.showShare(true);
        //设置控制器
        videoView.setController(controller);

        setVideoListener();




        //直接显示加载框
//        controller.showPreviewLoading();
    }
    private void play(){

        videoView.release();
        if(mLocalProxyVideoControl!=null){
            mLocalProxyVideoControl.releaseLocalProxyResources();
        }
        Map<String, String> header = new HashMap();
        header.put(
                "User-Agent",
                "Mozilla/5.0 (Linux; U; Android 10; zh-cn; M2006C3LC Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/79.0.3945.147 Mobile Safari/537.36 XiaoMi/MiuiBrowser/14.7.10"
        );


//
        String link=mUrl;
        if(mUrl.contains("m3u8")){
            header.put("type","m3u8");
            //开启视频缓存
            ProxyCacheUtils.getConfig().setUseOkHttp(true);
            link = ProxyCacheUtils.getProxyUrl(Uri.parse(mUrl).toString(), null, null);
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    //开始缓存
                    mLocalProxyVideoControl = new LocalProxyVideoControl();
                    mLocalProxyVideoControl.startRequestVideoInfo(mUrl, null, null);
                }
            }.start();
            VideoProxyCacheManager.getInstance().addSocketListener(mUrl, new ISocketListener() {
                @Override
                public void timeout() {
                    Log.e("tag","socket red timeout");
                    if(isFinishing()){
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            videoView.seekTo(videoView.getCurrentPosition()+2000);
                        }
                    });

                }
            });
        }


        videoView.setUrl(link, header);
        //开始播放
        videoView.start();
    }


    private void setVideoListener() {
        speed = SpeedInterface.sp1_50;
        videoView.setOnStateChangeListener(new OnVideoStateListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
                switch (playerState) {
                    case ConstantKeys.PlayMode.MODE_NORMAL:
                        break;
                    case ConstantKeys.PlayMode.MODE_FULL_SCREEN:
                        break;
                    case ConstantKeys.PlayMode.MODE_TINY_WINDOW:
                        break;
                }
            }

            /**
             * 播放状态
             * -1               播放错误
             * 0                播放未开始
             * 1                播放准备中
             * 2                播放准备就绪
             * 3                正在播放
             * 4                暂停播放
             * 5                正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
             * 6                暂停缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，此时暂停播放器，继续缓冲，缓冲区数据足够后恢复暂停
             * 7                播放完成
             * 8                开始播放中止
             * @param playState                         播放状态，主要是指播放器的各种状态
             */
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case ConstantKeys.CurrentState.STATE_IDLE:
                        break;
                    case ConstantKeys.CurrentState.STATE_PREPARED:
                        break;
                    case ConstantKeys.CurrentState.STATE_ERROR:
                        Log.e("", "error");
                        break;
                    case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
                        //设置倍速播放为为上一次的速度
                        if (!TextUtils.isEmpty(speed)) {
                            Toast.makeText(MainActivity.this,"1.5倍速播放",Toast.LENGTH_SHORT).show();
                            controller.setSpeed(SpeedInterface.sp1_50);
                            speed = null;

                        }
                        break;
                    case ConstantKeys.CurrentState.STATE_PLAYING:
                        break;
                    case ConstantKeys.CurrentState.STATE_PAUSED:
                        break;
                    case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
                        break;
                    case ConstantKeys.CurrentState.STATE_COMPLETED:
                        break;
                }
            }
        });
    }

    private void init(){
        String mr="http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4";
        String jsc="https://sod11.btycsw.com/20220302/Zc8fl2aW/index.m3u8";//镜双城//
      String hzw="https://sod12.btycsw.com/20220718/cjOe7ZMf/index.m3u8";//海贼王
        String zx="https://v4.dious.cc/20220428/mPYHg8Sl/index.m3u8";//赘婿
        findViewById(R.id.mr).setOnClickListener(v -> {
            Log.e("asdf","========默认=========");
            Log.e("asdf","========默认=========");
            mUrl=mr;
            play();
        });
        findViewById(R.id.jsc).setOnClickListener(v -> {
            Log.e("asdf","========镜双城=========");
            Log.e("asdf","========镜双城=========");
            mUrl=jsc;
            play();
        });
        findViewById(R.id.hzw).setOnClickListener(v -> {
            Log.e("asdf","========海贼王=========");
            Log.e("asdf","========海贼王=========");
            mUrl=hzw;
            play();
        });
        findViewById(R.id.zx).setOnClickListener(v -> {
            Log.e("asdf","========赘婿==========");
            Log.e("asdf","========赘婿==========");
            mUrl=zx;
            play();
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.resume();

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.pause();

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.release();
        }
        if(mLocalProxyVideoControl!=null){
            mLocalProxyVideoControl.releaseLocalProxyResources();
        }
        VideoInfoParseManager.getInstance().release();
    }
}