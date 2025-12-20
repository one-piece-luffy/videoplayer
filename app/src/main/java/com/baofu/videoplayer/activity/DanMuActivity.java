package com.baofu.videoplayer.activity;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.allfootball.news.imageloader.ImageLoader;
import com.baofu.base.utils.CommonUtils;
import com.baofu.videoplayer.danmu.FixedDanmakuView;
import com.baofu.videoplayer.R;
import com.baofu.videoplayer.utils.Appconstants;
import com.jeffmony.videocache.CacheConstants;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.control.LocalProxyVideoControl;
import com.jeffmony.videocache.listener.IPlayerProgressListener;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.yc.video.config.ConstantKeys;
import com.yc.video.player.OnVideoStateListener;
import com.yc.video.player.VideoPlayer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import cn.mahua.av.SpeedInterface;
import cn.mahua.av.controller.AvNormalPlayController;
import cn.mahua.av.listener.OnSetProgressListener;
import cn.mahua.av.play.ControllerClickListener;

public class DanMuActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_dan_mu);

        initPlayer();
        init();


        handler = new Handler(Looper.getMainLooper());
        initViews();
        initData();
        setupListeners();
        startAutoDanmaku();
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

        controller.setOnSetProgressListener(new OnSetProgressListener() {
            @Override
            public void setProgress(int duration, int position) {
                Log.e("aaaa","duration:"+duration+" position:"+position);
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
            header.put(CacheConstants.HEADER_KEY_NAME, ProxyCacheUtils.encodeUriWithBase64(name));

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
                            Toast.makeText(DanMuActivity.this,"1.5倍速播放",Toast.LENGTH_SHORT).show();
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

        findViewById(R.id.fr).setOnClickListener(v -> {
            Log.e("asdf","========凡人=========");
            mUrl= Appconstants.fanren;
            name="凡人";
            play(name);
        });
        findViewById(R.id.jsc).setOnClickListener(v -> {
            Log.e("asdf","========镜双城=========");
            mUrl=Appconstants.jsc;
            name="镜双城";
            play(name);
        });
        findViewById(R.id.hzw).setOnClickListener(v -> {
            Log.e("asdf","========海贼王=========");
            mUrl=Appconstants.hzw;
            name="海贼王";
            play(name);
        });
        findViewById(R.id.hjh).setOnClickListener(v -> {
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
        findViewById(R.id.langKeXing).setOnClickListener(v -> {
            Log.e("asdf","========浪客行==========");
            mUrl=Appconstants.LangKeXing;
            name="浪客行";
            play(name);
        });

        danmakuView=findViewById(R.id.danmaku_view);
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
        if (danmakuView != null) {
            danmakuView.resume();
            // 当Surface重新创建后恢复数据（可通过回调实现）
//            danmakuView.post(() -> danmakuView.restoreDanmakusState());
        }
        if (isStatsVisible) {
            startStatsUpdate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.pause();
        }
        if (danmakuView != null) {
            danmakuView.pause(); // 仅暂停渲染，不清除数据
        }
        stopStatsUpdate();
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
        handler.removeCallbacksAndMessages(null);
    }

    private FixedDanmakuView danmakuView;
    private EditText etDanmakuInput;
    private Button btnSend;
    private TextView tvStats;
    private Button btnClear;
    private Button btnPerformance;
    private Button btnResetStats;

    // 开关
    private SwitchCompat switchClick;
    private SwitchCompat switchBackground;
    private SwitchCompat switchClickThrough;
    private SwitchCompat switchUnlimitedLines;
    private SwitchCompat switchSpeed;
    private SwitchCompat switchOverlap;

    // 滑动条
    private SeekBar seekBarSpeed;
    private SeekBar seekBarLines;
    private SeekBar seekBarTextSize;
    private SeekBar seekBarMaxCount;

    // 文本显示
    private TextView tvSpeed;
    private TextView tvSpeedValue;
    private TextView tvLines;
    private TextView tvTextSize;
    private TextView tvMaxCount;

    // 行高按钮
    private Button btnLineHeightSmall;
    private Button btnLineHeightMedium;
    private Button btnLineHeightLarge;


    // 控制面板
    private View svControls;
    private Button btnToggleControls;

    // 其他
    private Random random;
    private boolean isStatsVisible = false;
    private boolean controlsVisible = true;

    // 速度控制相关
    private float currentSpeed = 1.0f;
    private float minSpeed = 0.2f;
    private float maxSpeed = 5.0f;


    private void initViews() {
        // 主要视图
        danmakuView = findViewById(R.id.danmaku_view);
        etDanmakuInput = findViewById(R.id.et_danmaku_input);
        btnSend = findViewById(R.id.btn_send);
        tvStats = findViewById(R.id.tv_stats);
        btnClear = findViewById(R.id.btn_clear);
        btnPerformance = findViewById(R.id.btn_performance);
        btnResetStats = findViewById(R.id.btn_reset_stats);

        // 开关
        switchClick = findViewById(R.id.switch_click);
        switchBackground = findViewById(R.id.switch_background);
        switchClickThrough = findViewById(R.id.switch_clickthrough);
        switchUnlimitedLines = findViewById(R.id.switch_unlimited_lines);
        switchSpeed = findViewById(R.id.switch_speed);
        switchOverlap = findViewById(R.id.switch_overlap);

        // 滑动条
        seekBarSpeed = findViewById(R.id.seekbar_speed);
        seekBarLines = findViewById(R.id.seekbar_lines);
        seekBarTextSize = findViewById(R.id.seekbar_textsize);
        seekBarMaxCount = findViewById(R.id.seekbar_maxcount);

        // 文本显示
        tvSpeed = findViewById(R.id.tv_speed);
        tvSpeedValue = findViewById(R.id.tv_speed_value);
        tvLines = findViewById(R.id.tv_lines);
        tvTextSize = findViewById(R.id.tv_textsize);
        tvMaxCount = findViewById(R.id.tv_maxcount);

        // 行高按钮
        btnLineHeightSmall = findViewById(R.id.btn_line_height_small);
        btnLineHeightMedium = findViewById(R.id.btn_line_height_medium);
        btnLineHeightLarge = findViewById(R.id.btn_line_height_large);


        // 控制面板
        svControls = findViewById(R.id.sv_controls);
        btnToggleControls = findViewById(R.id.btn_toggle_controls);

        // 测试按钮
        findViewById(R.id.btn_test).setOnClickListener(v -> addTestDanmakus(5));
        findViewById(R.id.btn_special).setOnClickListener(v -> addSpecialDanmaku());
        findViewById(R.id.btn_click_test).setOnClickListener(v -> addClickTestDanmakus());
    }

    private void initData() {
        handler = new Handler(Looper.getMainLooper());
        random = new Random();

        // 设置初始值
        switchClick.setChecked(true);
        switchBackground.setChecked(false);
        switchClickThrough.setChecked(false);
        switchUnlimitedLines.setChecked(false);
        switchSpeed.setChecked(false);
        switchOverlap.setChecked(false);

        // 计算初始进度条位置
        int speedProgress = calculateProgressFromSpeed(1.0f);
        seekBarSpeed.setProgress(speedProgress);
        seekBarLines.setProgress(14);
        seekBarTextSize.setProgress(40);
        seekBarMaxCount.setProgress(100);

        updateSpeedDisplay();
        updateLinesText();
        updateTextSizeText();
        updateMaxCountText();
    }

    private void setupListeners() {
        // 发送弹幕
        btnSend.setOnClickListener(v -> sendDanmaku());
        etDanmakuInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendDanmaku();
                return true;
            }
            return false;
        });

        // 清空弹幕
        btnClear.setOnClickListener(v -> {
            danmakuView.clearAll();
            Toast.makeText(this, "弹幕已清空", Toast.LENGTH_SHORT).show();
        });

        // 性能显示
        btnPerformance.setOnClickListener(v -> toggleStats());

        // 重置统计
        btnResetStats.setOnClickListener(v -> {
            danmakuView.resetStats();
            Toast.makeText(this, "统计已重置", Toast.LENGTH_SHORT).show();
        });

        // 开关监听
        switchClick.setOnCheckedChangeListener((v, isChecked) ->
                danmakuView.setEnableClick(isChecked));

        switchBackground.setOnCheckedChangeListener((v, isChecked) ->
                danmakuView.setShowBackground(isChecked));

        switchClickThrough.setOnCheckedChangeListener((v, isChecked) ->
                danmakuView.setClickThroughEnabled(isChecked));

        switchUnlimitedLines.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                danmakuView.setMaxLines(-1);
                tvLines.setText("行数: 无限制");
                seekBarLines.setEnabled(false);
                btnLineHeightSmall.setEnabled(false);
                btnLineHeightMedium.setEnabled(false);
                btnLineHeightLarge.setEnabled(false);
            } else {
                int lines = seekBarLines.getProgress() + 1;
                danmakuView.setMaxLines(lines);
                updateLinesText();
                seekBarLines.setEnabled(true);
                btnLineHeightSmall.setEnabled(true);
                btnLineHeightMedium.setEnabled(true);
                btnLineHeightLarge.setEnabled(true);
            }
        });
        switchSpeed.setOnCheckedChangeListener((v, isChecked) -> {
            danmakuView.setUniformSpeed(isChecked);
        });
        switchOverlap.setOnCheckedChangeListener((v, isChecked) -> {
            danmakuView.setAllowOverlap(isChecked);
        });

        // 速度控制
        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentSpeed = calculateSpeedFromProgress(progress);
                    danmakuView.setGlobalSpeed(currentSpeed);
                    updateSpeedDisplay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 添加动画效果
                tvSpeedValue.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                tvSpeedValue.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                Toast.makeText(DanMuActivity.this,
                        String.format("速度设置为: %.1fx", currentSpeed),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 行数控制
        seekBarLines.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !switchUnlimitedLines.isChecked()) {
                    int lines = progress + 1;
                    danmakuView.setMaxLines(lines);
                    updateLinesText();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 文字大小控制
        seekBarTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int size =  progress * 20 / 100;
                    danmakuView.setTextSize(size);
                    updateTextSizeText();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 最大数量控制
        seekBarMaxCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateMaxCountText();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 行高控制
        btnLineHeightSmall.setOnClickListener(v -> setLineHeight(40, "小"));
        btnLineHeightMedium.setOnClickListener(v -> setLineHeight(50, "中"));
        btnLineHeightLarge.setOnClickListener(v -> setLineHeight(60, "大"));

        // 控制面板显示/隐藏
        btnToggleControls.setOnClickListener(v -> toggleControls());

        // 设置弹幕点击监听
        danmakuView.setDanmakuClickListener(danmaku -> {
            Toast.makeText(this,
                    String.format("点击弹幕: %s\n用户: %s",
                            danmaku.getText(), danmaku.getUserName()),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private float calculateSpeedFromProgress(int progress) {
        return danmakuView.getMaxSpeed()* progress / 100f;
//        if (normalized < 0.5f) {
//            return minSpeed + normalized * 2 * (1.0f - minSpeed);
//        } else {
//            return 1.0f + (normalized - 0.5f) * 2 * (maxSpeed - 1.0f);
//        }
    }

    private int calculateProgressFromSpeed(float speed) {
        if (speed <= 1.0f) {
            return (int) ((speed - minSpeed) * 50 / (1.0f - minSpeed));
        } else {
            return 50 + (int) ((speed - 1.0f) * 50 / (maxSpeed - 1.0f));
        }
    }

    private void updateSpeedDisplay() {
        String speedText = String.format(Locale.getDefault(), "%.1f", currentSpeed);
        tvSpeedValue.setText(speedText);

        // 根据速度改变颜色
        int color;
        if (currentSpeed < 0.8f) {
            color = Color.GREEN;
        } else if (currentSpeed > 2.0f) {
            color = Color.RED;
        } else {
            color = Color.YELLOW;
        }
        tvSpeedValue.setTextColor(color);
    }


    private void updateLinesText() {
        int lines = seekBarLines.getProgress() + 1;
        tvLines.setText(String.format(Locale.getDefault(), "行数: %d", lines));
    }

    private void updateTextSizeText() {
        int progress = seekBarTextSize.getProgress();
        float size =  progress * 20 / 100f;
        tvTextSize.setText(String.format(Locale.getDefault(), "文字大小: %.0fdp", size));
    }

    private void updateMaxCountText() {
        int progress = seekBarMaxCount.getProgress();
        int maxCount = 10 + progress * 490 / 100;
        tvMaxCount.setText(String.format(Locale.getDefault(), "最大数量: %d", maxCount));
    }

    private void setLineHeight(int height, String text) {
        danmakuView.setLineHeight(height);
        Toast.makeText(this, "行高设置为: " + text, Toast.LENGTH_SHORT).show();
    }

    private void sendDanmaku() {
        String text = etDanmakuInput.getText().toString().trim();
        if (!text.isEmpty()) {
            boolean clickable = random.nextBoolean();
            int color = Color.rgb(
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256)
            );

            danmakuView.addDanmaku(text, color, clickable);
            etDanmakuInput.setText("");
        }
    }

    private void addTestDanmakus(int count) {
        String[] testTexts = {
                "测试弹幕1", "Android弹幕系统", "高性能渲染",
                "可点击弹幕", "穿透点击测试", "行数控制测试",
                "速度调节测试", "文字大小测试", "对象池优化"
        };
        Log.e("asdff","count:"+count);

        for (int i = 0; i < count; i++) {
            String text = testTexts[random.nextInt(testTexts.length)];
            int color = Color.rgb(
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256)
            );

            boolean clickable = !text.contains("穿透");
            danmakuView.addDanmaku(text, color, clickable);
        }
    }


    private void addClickTestDanmakus() {
        danmakuView.addDanmaku("✅ 可点击弹幕 - 点击我试试！", Color.GREEN, true);
        danmakuView.addDanmaku("🚫 不可点击弹幕 - 我会穿透", Color.argb(100, 255, 0, 0), false);
        danmakuView.addDanmaku("🔍 半透明弹幕 - 可能穿透", Color.argb(150, 0, 150, 255), true);

        Toast.makeText(this, "添加了点击测试弹幕", Toast.LENGTH_SHORT).show();
    }

    private void addSpecialDanmaku() {
        String[] specialTexts = {"✨ 特殊弹幕 ✨", "🎯 高级弹幕 🎯", "🚀 性能优化 🚀"};
        String text = specialTexts[random.nextInt(specialTexts.length)];
        danmakuView.addDanmaku(text, Color.YELLOW, true);

        Toast.makeText(this, "添加了特殊弹幕", Toast.LENGTH_SHORT).show();
    }

    private void toggleStats() {
        isStatsVisible = !isStatsVisible;

        if (isStatsVisible) {
            startStatsUpdate();
        } else {
            stopStatsUpdate();
            tvStats.setText("点击查看性能统计");
        }
    }

    private void startStatsUpdate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isStatsVisible && danmakuView != null && !isFinishing()) {
                    String stats = danmakuView.getPerformanceStats();
                    tvStats.setText(stats);
                    handler.postDelayed(this, 500);
                }
            }
        });
    }

    private void stopStatsUpdate() {
        handler.removeCallbacksAndMessages(null);
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        svControls.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        btnToggleControls.setText(controlsVisible ? "隐藏控制" : "显示控制");
    }

    private void startAutoDanmaku() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    addTestDanmakus(1);
                    handler.postDelayed(this, 2000);
                }
            }
        }, 1000);
    }


}