package com.baofu.videoplayer.activity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.allfootball.news.imageloader.ImageLoader;
import com.baofu.base.utils.CommonUtils;
import com.baofu.videoplayer.R;
import com.baofu.videoplayer.databinding.ActivityMainBinding;
import com.baofu.videoplayer.utils.Appconstants;
import com.baofu.videoplayer.utils.DefaultExecutor;
import com.baofu.videoplayer.utils.LocalLink;
import com.baofu.videoplayer.utils.TsFileFinderSmart;
import com.jeffmony.videocache.CacheConstants;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.StorageManager;
import com.jeffmony.videocache.VideoProxyCacheManager;
import com.jeffmony.videocache.control.LocalProxyVideoControl;
import com.jeffmony.videocache.listener.IPlayerProgressListener;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.TsFileValidator;
import com.jeffmony.videocache.utils.VideoCacheUtils;
import com.yc.video.config.ConstantKeys;
import com.yc.video.player.OnVideoStateListener;
import com.yc.video.player.VideoPlayer;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.mahua.av.SpeedInterface;
import cn.mahua.av.controller.AvNormalPlayController;
import cn.mahua.av.listener.OnSetProgressListener;
import cn.mahua.av.play.ControllerClickListener;

public class MainActivity extends AppCompatActivity {
    VideoPlayer videoView;
    AvNormalPlayController controller;
    LocalProxyVideoControl mLocalProxyVideoControl;
    String mUrl;
    //倍速播放速度
    String speed;
    String name;
    int  mGeneratedId;
    boolean toolShow=true;
    Handler handler =new Handler(Looper.getMainLooper());
    ActivityMainBinding binding;
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
        public void onM3U8ParsedFailed(String error) {
            CommonUtils.showToast("m3u8解析失败:"+error);
            Log.e("MainActivity","m3u8解析失败:"+error);
        }

        @Override
        public void playerCacheLog(String log) {
            Log.e("===asdf",log);
        }

        @Override
        public void onSeek(int segIndex) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(isDestroyed()||isFinishing()){
                        return;
                    }
                    CommonUtils.showToast("当前ts:"+segIndex);
                }
            });
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
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
        //隐藏下一集按钮
        controller.hideNextBtn();
        //设置缓存提示信息
        controller.setLoadingMessage("正在缓冲，哈哈");
        View view= LayoutInflater.from(this).inflate(R.layout.av_tools_item,null);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.showToast("工具1");
                toolShow=!toolShow;
                controller.showToolsViewById(mGeneratedId,toolShow);
            }
        });
        //添加自定义工具
        controller.addTools(view);

        TextView toolView2= (TextView) LayoutInflater.from(this).inflate(R.layout.av_tools_item,null);
        toolView2.setText("工具2");
        toolView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommonUtils.showToast("工具2");
            }
        });
        mGeneratedId= View.generateViewId();
        toolView2.setId(mGeneratedId);
        controller.addTools(toolView2);

        controller.addErrorViewItem("retry", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DefaultExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        //删除不合标准的ts文件后重播
                        long start=System.currentTimeMillis();
                        File cacheDirectory= VideoCacheUtils.getCacheFilePath(mUrl);
                        if(cacheDirectory.exists()){
                            List<String>  list= TsFileFinderSmart.getTsFiles(cacheDirectory.getAbsolutePath());
                            for(String path:list){
                                File file=new File(cacheDirectory+File.separator+path);
                                if (!TsFileValidator.isValidTsFile(file)) {
                                    FileUtils.deleteFile(file);
                                }
                            }
                            long end=System.currentTimeMillis()-start;
                            Log.e("asdfg","ts 检验时间:"+end+" 文件长度:"+list.size());
                        }
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                videoView.replay(false);
                            }
                        });
                    }
                });


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

        controller.setOnSetProgressListener(new OnSetProgressListener() {
            @Override
            public void setProgress(int duration, int position) {
//                Log.e("aaaa","duration:"+duration+" position:"+position);
            }
        });


        //直接显示加载框
//        controller.showPreviewLoading();
    }
    private void play(String name){

        try {
            String temp=mUrl;
            mUrl = encodeUrl(mUrl);
            Log.i("MainActivity",mUrl);
        } catch (URISyntaxException e) {
           e.printStackTrace();
        }
//        mUrl="/storage/emulated/0/Download/图片/图片.m3u8";
//        mUrl="https://cdn.wlcdn88.com:777/bf06cb13/index.m3u8";

        videoView.release();
        if(mLocalProxyVideoControl!=null){
            mLocalProxyVideoControl.releaseAll();
        }
        Map<String, String> header = new HashMap();
        header.put(
                "User-Agent",
                "Mozilla/5.0 (Linux; U; Android 10; zh-cn; M2006C3LC Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/79.0.3945.147 Mobile Safari/537.36 XiaoMi/MiuiBrowser/14.7.10"
        );



        String link=mUrl;
        if(mUrl.contains("m3u8")){
            header.put("type","m3u8");
            header.put(CacheConstants.HEADER_KEY_NAME,ProxyCacheUtils.encodeUriWithBase64(name));

            //开启视频缓存
            link = ProxyCacheUtils.getProxyUrl(Uri.parse(mUrl).toString(), header, null);
            if (link.startsWith(ProxyCacheUtils.LOCAL_PROXY_URL)) {
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        //开始缓存
                        mLocalProxyVideoControl = new LocalProxyVideoControl();
                        mLocalProxyVideoControl.startRequestVideoInfo(mUrl, name, header, null);
                    }
                }.start();
            }

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
                    case ConstantKeys.CurrentState.STATE_IDLE:{
                        break;
                    }
                    case ConstantKeys.CurrentState.STATE_PREPARED:{
//                        videoView.seekTo(500*1000);
//                        Log.e("asdf","position:"+videoView.getCurrentPosition()+" total:"+videoView.getDuration());
//                        mLocalProxyVideoControl.seekToCachePosition(500*1000,videoView.getDuration());
                        break;

                    }

                    case ConstantKeys.CurrentState.STATE_ERROR:{
                        Log.e("", "==error:"+msg);
                        break;
                    }

                    case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:{
                        //设置倍速播放为为上一次的速度
                        if (!TextUtils.isEmpty(speed)) {
                            Toast.makeText(MainActivity.this,"1.5倍速播放",Toast.LENGTH_SHORT).show();
                            controller.setSpeed(SpeedInterface.sp1_50);
                            speed = null;

                        }
                        break;
                    }

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
        binding.link1.setText(LocalLink.link1.NAME);
        binding.link2.setText(LocalLink.link2.NAME);
        binding.link3.setText(LocalLink.link3.NAME);
        binding.link4.setText(LocalLink.link4.NAME);
        binding.link1.setOnClickListener(v -> {
            Log.e("asdf","========"+LocalLink.link1.NAME+"=========");
            mUrl = LocalLink.link1.LINK;
            name = LocalLink.link1.NAME;
            play(name);
        });
        binding.link2.setOnClickListener(v -> {
            Log.e("asdf","========"+LocalLink.link2.NAME+"=========");
            mUrl = LocalLink.link2.LINK;
            name = LocalLink.link2.NAME;
            play(name);
        });
        binding.link3.setOnClickListener(v -> {
            Log.e("asdf","========"+LocalLink.link3.NAME+"=========");
            mUrl = LocalLink.link3.LINK;
            name = LocalLink.link3.NAME;
            play(name);
        });
        binding.link4.setOnClickListener(v -> {
            Log.e("asdf","========"+LocalLink.link4.NAME+"=========");
            mUrl = LocalLink.link4.LINK;
            name = LocalLink.link4.NAME;
            play(name);
        });
        binding.shut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VideoProxyCacheManager.getInstance().shutdown();
            }
        });
        binding.create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VideoProxyCacheManager.getInstance().initProxyConfig(ProxyCacheUtils.getConfig());
            }
        });
    }

    public String encodeUrl(String url) throws URISyntaxException {
        try {
            URI uri = new URI(
                    url.split("://")[0],                 // 协议部分
                    url.split("://")[1].split("/")[0],   // 主机部分
                    "/" + String.join("/", java.util.Arrays.copyOfRange(url.split("://")[1].split("/"), 1, url.split("://")[1].split("/").length)), // 路径部分
                    null                                 // 查询参数（如果有需要单独处理）
            );
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return url;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.resume();

        }
        if(VideoProxyCacheManager.getInstance().mLocalProxyServer!=null){
            Log.e("asdf","thread oom:"+VideoProxyCacheManager.getInstance().mLocalProxyServer.isThreadOOM());
            Log.e("asdf","isServerObjAlive:"+VideoProxyCacheManager.getInstance().mLocalProxyServer.isServerObjAlive());
            Log.e("asdf","ping:"+VideoProxyCacheManager.getInstance().mLocalProxyServer.pingProxyHealth());
            Log.e("asdf","ping:"+ProxyCacheUtils.isServerAlive());
//        VideoProxyCacheManager.getInstance().mLocalProxyServer.shutdown();
        }
        Log.e("asdf","ping:"+ StorageManager.getInstance().getCacheSize(getExternalCacheDir().getAbsolutePath()));
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
            mLocalProxyVideoControl.releaseAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PlayerProgressListenerManager.getInstance().setListener(null);

    }
}