package com.baofu.videoplayer;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.allfootball.news.imageloader.ImageLoader;
import com.baofu.base.utils.CommonUtils;
import com.baofu.cache.downloader.utils.VideoDownloadUtils;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.VideoInfoParseManager;
import com.jeffmony.videocache.control.LocalProxyVideoControl;
import com.jeffmony.videocache.listener.IPlayerProgressListener;
import com.jeffmony.videocache.listener.IVideoCacheListener;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.baofu.videoplayer.utils.Appconstants;
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
    String name;
    Handler handler =new Handler(Looper.getMainLooper());
    IPlayerProgressListener iPlayerProgressListener=new IPlayerProgressListener() {
        @Override
        public void onTaskFirstTsDownload(String filename) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(isDestroyed()||isFinishing()){
                        return;
                    }
                    CommonUtils.showToast("task 第一个ts下载完成:"+filename);
                    Log.e("MainActivity","task 第一个ts下载完成:"+filename);
                }
            });
        }

        @Override
        public void onPlayerFirstTsDownload(String filename) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(isDestroyed()||isFinishing()){
                        return;
                    }
                    CommonUtils.showToast("播放器 第一个ts下载完成:"+filename);
                    Log.e("MainActivity","player 第一个ts下载完成:"+filename);
                }
            });

        }

        @Override
        public void onM3U8ParsedFailed(String error) {
            CommonUtils.showToast("m3u8解析失败:"+error);
            Log.e("MainActivity","m3u8解析失败:"+error);
        }

        @Override
        public void playerCacheLog(String log) {
            Log.e("===asdf",log);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPlayer();
        init();
    }

    void initPlayer(){
        //设置播放器缓存加载进度监听
        PlayerProgressListenerManager.getInstance().setListener(iPlayerProgressListener);

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

        controller.addErrorViewItem("retry", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                videoView.replay(false);
            }
        });
        controller.addErrorViewItem("fix", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                videoView.replay(false);
            }
        });
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
        //固定为竖屏模式
        controller.setOrientationPortrait(false);
        //滑动调节音量
        controller.enableChangeVolume(true);
        //滑动调节亮度
        controller.enableChangeBrightness(true);
        controller.setOnVisibilityChangedListener(null);
        //设置控制器
        videoView.setController(controller);

        setVideoListener();




        //直接显示加载框
//        controller.showPreviewLoading();
    }
    private void play(String name){

        videoView.release();
        if(mLocalProxyVideoControl!=null){
            mLocalProxyVideoControl.releaseLocalProxyResources();
        }
        Map<String, String> header = new HashMap();
//        header.put(
//                "User-Agent",
//                "Mozilla/5.0 (Linux; U; Android 10; zh-cn; M2006C3LC Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/79.0.3945.147 Mobile Safari/537.36 XiaoMi/MiuiBrowser/14.7.10"
//        );


//
        String link=mUrl;
        if(mUrl.contains("m3u8")){
            header.put("type","m3u8");
            header.put("vodName",ProxyCacheUtils.encodeUriWithBase64(name));

            //开启视频缓存
            link = ProxyCacheUtils.getProxyUrl(Uri.parse(mUrl).toString(), header, null);
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    //开始缓存
                    mLocalProxyVideoControl = new LocalProxyVideoControl();
                    mLocalProxyVideoControl.startRequestVideoInfo(mUrl, name,header, null);
                }
            }.start();
//            VideoProxyCacheManager.getInstance().addSocketListener(mUrl, new ISocketListener() {
//                @Override
//                public void timeout() {
//                    Log.e("tag","socket red timeout");
//                    if(isFinishing()){
//                        return;
//                    }
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            videoView.seekTo(videoView.getCurrentPosition()+2000);
//                        }
//                    });
//
//                }
//            });
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
            public void onPlayStateChanged(int playState,String msg) {
                switch (playState) {
                    case ConstantKeys.CurrentState.STATE_IDLE:
                        break;
                    case ConstantKeys.CurrentState.STATE_PREPARED:
                        break;
                    case ConstantKeys.CurrentState.STATE_ERROR:
                        Log.e("", "==error:"+msg);
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

        findViewById(R.id.fr).setOnClickListener(v -> {
            Log.e("asdf","========凡人=========");
            mUrl= Appconstants.fanren;
            name="凡人";
            play(name);
        });
        findViewById(R.id.jsc).setOnClickListener(v -> {
            Log.e("asdf","========镜双城=========");
            Log.e("asdf","========镜双城=========");
            mUrl=Appconstants.jsc;
            name="镜双城";
            play(name);
        });
        findViewById(R.id.hzw).setOnClickListener(v -> {
            Log.e("asdf","========海贼王=========");
            Log.e("asdf","========海贼王=========");
            mUrl=Appconstants.hzw;
            name="海贼王";
            play(name);
        });
        findViewById(R.id.hjh).setOnClickListener(v -> {
            Log.e("asdf","========画江湖==========");
            Log.e("asdf","========画江湖==========");
            mUrl=Appconstants.huajianghu;
            name="画江湖";
            play(name);
        });
        findViewById(R.id.shixiong).setOnClickListener(v -> {
            Log.e("asdf","========师兄啊师兄==========");
            mUrl=Appconstants.shixiong;
            name="师兄啊师兄";
            play(name);
        });
        findViewById(R.id.canghaizhuan).setOnClickListener(v -> {
            Log.e("asdf","========藏海传==========");
            mUrl=Appconstants.canghaizhuan;
            name="藏海传";
            play(name);
        });
        findViewById(R.id.shaohua).setOnClickListener(v -> {
            Log.e("asdf","========韶华若锦==========");
            mUrl=Appconstants.ShaoHuaRuoJIn;
            name="韶华若锦";
            play(name);
        });
        findViewById(R.id.luohua).setOnClickListener(v -> {
            Log.e("asdf","========落花时节又逢君==========");
            mUrl=Appconstants.LuoHua;
            name="落花时节又逢君";
            play(name);
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
    public void finish() {
        super.finish();
        if (videoView != null) {
            videoView.release();
        }
        if (mLocalProxyVideoControl != null) {
            mLocalProxyVideoControl.releaseLocalProxyResources();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PlayerProgressListenerManager.getInstance().setListener(null);

    }
}