/*
Copyright 2017 yangchong211（github.com/yangchong211）

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package cn.mahua.av.controller.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yc.kernel.utils.VideoLogUtils;
import com.yc.video.bridge.ControlWrapper;
import com.yc.video.config.ConstantKeys;
import com.yc.video.old.controller.AbsVideoPlayerController;
import com.yc.video.player.VideoViewManager;
import com.yc.video.ui.view.InterControlView;

import java.util.Timer;
import java.util.TimerTask;

import cn.mahua.av.R;
import cn.mahua.av.utils.AvUtils;


/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2017/11/9
 *     desc  : 预加载准备播放页面视图
 *     revise:
 * </pre>
 */
public class AvPrepareView extends FrameLayout implements InterControlView {

    String TAG="AvPrepareView";
    private Context mContext;
    private ControlWrapper mControlWrapper;
    private ImageView mIvThumb;
    private ImageView mIvStartPlay;
//    private ProgressBar mPbLoading;
    private FrameLayout mFlNetWarning;
    private TextView mTvMessage;
    private TextView mTvStart;
    LinearLayout mLoadingLayout;
    TextView mTvTcpSpeed;
    private Timer mUpdateNetSpeedTimer;
    private TimerTask mUpdateNetSpeedTask;

    public AvPrepareView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvPrepareView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvPrepareView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context){
        this.mContext = context;
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.av_video_player_prepare, this, true);
        initFindViewById(view);
        initListener();
    }

    private void initFindViewById(View view) {
        mIvThumb = view.findViewById(R.id.iv_thumb);
        mIvStartPlay = view.findViewById(R.id.iv_start_play);
//        mPbLoading = view.findViewById(R.id.pb_loading);
        mFlNetWarning = view.findViewById(R.id.fl_net_warning);
        mTvMessage = view.findViewById(R.id.tv_message);
        mTvStart = view.findViewById(R.id.tv_start);
        mLoadingLayout = view.findViewById(R.id.loading_layout);
        mTvTcpSpeed = view.findViewById(R.id.tcp_speed);
    }

    private void initListener() {
        mTvStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFlNetWarning.setVisibility(GONE);
                VideoViewManager.instance().setPlayOnMobileNetwork(true);
                mControlWrapper.start();
            }
        });
    }

    /**
     * 设置点击此界面开始播放
     */
    public void setClickStart() {
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mControlWrapper.start();
            }
        });
    }

    @Override
    public void attach(@NonNull ControlWrapper controlWrapper) {
        mControlWrapper = controlWrapper;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onVisibilityChanged(boolean isVisible, Animation anim) {

    }

    @Override
    public void onPlayStateChanged(int playState) {
        switch (playState) {
            case ConstantKeys.CurrentState.STATE_PREPARING:

                bringToFront();
                setVisibility(VISIBLE);
                mIvStartPlay.setVisibility(View.GONE);
                mFlNetWarning.setVisibility(GONE);
                mLoadingLayout.setVisibility(View.VISIBLE);
                //开启缓冲时更新网络加载速度
                startUpdateNetSpeedTimer();
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
                setVisibility(VISIBLE);
                mIvStartPlay.setVisibility(View.GONE);
                mFlNetWarning.setVisibility(GONE);
                mLoadingLayout.setVisibility(View.VISIBLE);
                mIvThumb.setVisibility(GONE);
                //开启缓冲时更新网络加载速度
                startUpdateNetSpeedTimer();
                break;
            case ConstantKeys.CurrentState.STATE_PLAYING:
            case ConstantKeys.CurrentState.STATE_PAUSED:
            case ConstantKeys.CurrentState.STATE_ERROR:
            case ConstantKeys.CurrentState.STATE_COMPLETED:
            case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
            case ConstantKeys.CurrentState.STATE_ONCE_LIVE:
            case ConstantKeys.CurrentState.STATE_PREPARED:
                setVisibility(GONE);
                cancelUpdateNetSpeedTimer();
                break;
            case ConstantKeys.CurrentState.STATE_IDLE:
                setVisibility(VISIBLE);
                bringToFront();
                cancelUpdateNetSpeedTimer();
                mLoadingLayout.setVisibility(View.GONE);
                mFlNetWarning.setVisibility(GONE);
                mIvStartPlay.setVisibility(View.VISIBLE);
                mIvThumb.setVisibility(View.VISIBLE);
                break;
            case ConstantKeys.CurrentState.STATE_START_ABORT:
                setVisibility(VISIBLE);
                mFlNetWarning.setVisibility(VISIBLE);
                mFlNetWarning.bringToFront();
                break;
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {

    }

    @Override
    public void setProgress(int duration, int position) {

    }

    @Override
    public void onLockStateChanged(boolean isLocked) {

    }

    public ImageView getThumb() {
        return mIvThumb;
    }



    /**
     * 当正在缓冲或者播放准备中状态时，开启缓冲时更新网络加载速度
     */
    protected void startUpdateNetSpeedTimer() {
        cancelUpdateNetSpeedTimer();
        Log.i(TAG,"start timer");
        if (mUpdateNetSpeedTimer == null) {
            mUpdateNetSpeedTimer = new Timer();
        }
        if (mUpdateNetSpeedTask == null) {
            mUpdateNetSpeedTask = new TimerTask() {
                @Override
                public void run() {
                    if(mTvTcpSpeed==null)
                        return;
                    //在主线程中更新进度，包括更新网络加载速度+
                    mTvTcpSpeed.post(new Runnable() {
                        @Override
                        public void run() {
                            long tcpSpeed = mControlWrapper.getTcpSpeed();
                            VideoLogUtils.i("获取网络加载速度++++++++" + tcpSpeed);
                            if (tcpSpeed > 0) {
                                //显示网速
                                mTvTcpSpeed.setVisibility(View.VISIBLE);
                                mTvTcpSpeed.setText(AvUtils.getSizeStr(tcpSpeed));
                            }
                            Log.i(TAG,"speed:"+tcpSpeed);
                        }
                    });


                }
            };
        }
        mUpdateNetSpeedTimer.schedule(mUpdateNetSpeedTask, 0, 1000);
    }

    /**
     * 取消缓冲时更新网络加载速度
     */
    protected void cancelUpdateNetSpeedTimer() {
        Log.i(TAG,"cancle timer");
        if (mUpdateNetSpeedTimer != null) {
            mUpdateNetSpeedTimer.cancel();
            mUpdateNetSpeedTimer = null;
        }
        if (mUpdateNetSpeedTask != null) {
            mUpdateNetSpeedTask.cancel();
            mUpdateNetSpeedTask = null;
        }
    }

}
