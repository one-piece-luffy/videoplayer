package com.baofu.videoplayer.fragment;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.baofu.base.utils.CommonUtils;
import com.baofu.videocache.control.LocalProxyVideoControl;
import com.baofu.videocache.utils.ProxyCacheUtils;
import com.baofu.videoplayer.R;
import com.baofu.videoplayer.databinding.FragmentPlayerBinding;
import com.baofu.videoplayer.utils.LocalProxyVideoInstance;
import com.yc.video.config.ConstantKeys;
import com.yc.video.old.other.VideoPlayerManager;
import com.yc.video.player.OnVideoStateListener;
import com.yc.video.player.VideoPlayer;

import java.util.HashMap;
import java.util.Map;

import cn.mahua.av.controller.AvNormalPlayController;
import cn.mahua.av.play.ControllerClickListener;

public class PlayerFragement extends Fragment {
    FragmentPlayerBinding binding;

    public String mUrl;
    public int mPosition;
    boolean isFirst=true;

    public static PlayerFragement newInstance(String url,int position) {
        Bundle args = new Bundle();
        //设置当前位置
        args.putString("url", url);
        args.putInt("position", position);
        PlayerFragement fragment = new PlayerFragement();
        fragment.setArguments(args);
        return fragment;
    }


    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_player, container, false);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            mUrl = getArguments().getString("url");
            mPosition = getArguments().getInt("position");
        }
        initPlayer();
    }



    public void initPlayer( ) {
        //创建基础视频播放器，一般播放器的功能
        AvNormalPlayController controller = new AvNormalPlayController(getContext());

        controller.setLoadingMessage("loading");
        //显示网速
        controller.showTcpSpeed(true);
        //设置播放器缩略图

        //竖屏模式
        controller.setOrientationPortrait(true);
        controller.enableChangeBrightness(false);
        controller.enableChangeVolume(false);
        //设置控制器
        binding.videoView.setController(controller);
        controller.addErrorViewItem("重试", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.videoView.replay(false);
//                String url = getCurrenUrl();
//                if (TextUtils.isEmpty(url)) {
//                    return;
//                }
//                play(url);
            }
        });
        controller.setControllerClickListener(new ControllerClickListener() {
            @Override
            public void onClick(View view) {

            }

            @Override
            public void share() {
            }

            @Override
            public void next() {
//                int position=urlIndex+1;
//                changeSelect(position);
            }


            @Override
            public void onUserSeek(long position) {
                LocalProxyVideoInstance.getInstance().seekto(position,binding.videoView.getDuration());
            }
        });
        binding.videoView.addOnStateChangeListener(new OnVideoStateListener() {
            @Override
            public void onPlayerStateChanged(int playerState) {
                if (playerState == ConstantKeys.PlayMode.MODE_NORMAL) {

                }
            }

            @Override
            public void onPlayStateChanged(int playState, String msg) {
                switch (playState) {
                    case ConstantKeys.CurrentState.STATE_PREPARED: {

//                        mDuration = dataBinding.videoView.getDuration();
//                        if (mCurPlayPosition > 0) {
//                            dataBinding.videoView.seekTo(mCurPlayPosition);
//                        }
//                        videoPrepared = true;
//                        if (AppUtils.isVip() || AppConstants.AD_OFF) {
//                            dismissLoadingDialog();
//                        }
                        break;
                    }
                    case ConstantKeys.CurrentState.STATE_PAUSED: {
                        break;
                    }
                    case ConstantKeys.CurrentState.STATE_PLAYING: {
//                        videoPrepared = true;
                        break;
                    }
                    case ConstantKeys.CurrentState.STATE_ERROR: {
//                        videoPrepared = true;
//                        dismissLoadingDialog();
//                        handler.removeCallbacks(runnable);
                        break;
                    }
                    case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED: {
                        //缓冲中
                    }
                    case ConstantKeys.CurrentState.STATE_COMPLETED: {
                        //缓冲结束
                    }
                    case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING: {
                        //缓冲结束
//                        int position = urlIndex + 1;
//                        changeSelect(position);
                        break;
                    }
                }
            }
        });
    }

    private void play() {
        if (TextUtils.isEmpty(mUrl)) {
            CommonUtils.showToast("链接不存在");
            return;
        }


        LocalProxyVideoInstance.getInstance().release();
        try {
            binding.videoView.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, String> header = new HashMap<>();
        header.put(
                "User-Agent",
                "Mozilla/5.0 (Linux; U; Android 10; zh-cn; M2006C3LC Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/79.0.3945.147 Mobile Safari/537.36 XiaoMi/MiuiBrowser/14.7.10"
        );

        String link = mUrl;
        if (mUrl.contains("m3u8") &&ProxyCacheUtils.getConfig() != null && ProxyCacheUtils.getConfig().getFilePath() != null) {
            //开启视频缓存
            ProxyCacheUtils.getConfig().setUseOkHttp(true);
            link = ProxyCacheUtils.getProxyUrl(Uri.parse(mUrl).toString(), header, null);
            header.put("type", "m3u8");
            new Thread(){
                @Override
                public void run() {
                    super.run();
                    //开始缓存
                    LocalProxyVideoInstance.getInstance().start(mUrl,header,null);
                }
            }.start();
        }
        binding.videoView.setUrl(link);
        Log.e("asdff","start play");
        VideoPlayerManager.instance().setCurrentVideoPlayer(binding.videoView);
        //开始播放
        binding.videoView.start();
//            dataBinding.videoView.postDelayed(() -> dataBinding.videoView.start(), 300);

    }

    @Override
    public void onResume() {
        super.onResume();
        if(isFirst){
            isFirst=false;
            play();
        }
        binding.videoView.resume();
        LocalProxyVideoInstance.getInstance().resume();
        Log.e("asdff","onresume:"+mPosition);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        Log.e("asdff","hidden:"+hidden);
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.videoView.pause();
        LocalProxyVideoInstance.getInstance().pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding.videoView.release();
        Log.e("asdff","onDestroy");
        LocalProxyVideoInstance.getInstance().release();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        binding.videoView.release();
        Log.e("asdff","onDetach");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.videoView.release();
        Log.e("asdff","onDestroyView");
    }
}
