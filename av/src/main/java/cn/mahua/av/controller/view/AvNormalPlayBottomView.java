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

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yc.video.bridge.ControlWrapper;
import com.yc.video.config.ConstantKeys;
import com.yc.video.config.VideoPlayerConfig;
import com.yc.video.tool.PlayerUtils;
import com.yc.video.ui.view.InterControlView;

import cn.mahua.av.R;
import cn.mahua.av.SpeedInterface;
import cn.mahua.av.play.ControllerClickListener;
import cn.mahua.av.widget.view.SpeedDialog;

import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_16_9;
import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_4_3;
import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_CENTER_CROP;
import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_DEFAULT;
import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_MATCH_PARENT;
import static com.yc.video.config.ConstantKeys.PlayerScreenScaleType.SCREEN_SCALE_ORIGINAL;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2017/11/9
 *     desc  : 底部控制栏视图
 *     revise: 用于普通播放器
 * </pre>
 */
public class AvNormalPlayBottomView extends FrameLayout implements InterControlView,
        View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private Context mContext;
    protected ControlWrapper mControlWrapper;
    private LinearLayout mLlBottomContainer;
    private ImageView mIvPlay;
    private TextView mTvCurrTime;
    private SeekBar mSeekBar;
    private TextView mTvTotalTime;
    private ProgressBar mPbBottomProgress;
    private boolean mIsDragging;
    private boolean mIsShowBottomProgress = true;
    ViewGroup mBottomToolsLayout;
    TextView tv_speed;
    TextView tv_av_scale;
    ImageView iv_av_next;
    ImageView iv_fullscreen;
    protected ControllerClickListener controllerClickListener;
    int mCurrentScaleType = SCREEN_SCALE_DEFAULT;
    float mLastSpeed;
    float mCurrentSpeed;

    int[] mScaleTypeArray = new int[]{
            SCREEN_SCALE_DEFAULT,
            //16：9比例类型，最为常见
            SCREEN_SCALE_16_9,
            //4：3比例类型，也比较常见
            SCREEN_SCALE_4_3,
            //充满整个控件视图
            SCREEN_SCALE_MATCH_PARENT,
            //原始类型，指视频的原始类型
            SCREEN_SCALE_ORIGINAL,
            //剧中裁剪类型
            SCREEN_SCALE_CENTER_CROP
    };


    public AvNormalPlayBottomView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvNormalPlayBottomView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvNormalPlayBottomView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        setVisibility(GONE);
        View view = LayoutInflater.from(getContext()).inflate(getLayoutId(), this, true);
        initFindViewById(view);
        initListener();
        //5.1以下系统SeekBar高度需要设置成WRAP_CONTENT
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mPbBottomProgress.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    private void initFindViewById(View view) {
        mLlBottomContainer = view.findViewById(R.id.ll_bottom_container);
        mIvPlay = view.findViewById(R.id.iv_play);
        mTvCurrTime = view.findViewById(R.id.tv_curr_time);
        mSeekBar = view.findViewById(R.id.seekBar);
        mTvTotalTime = view.findViewById(R.id.tv_total_time);
        mPbBottomProgress = view.findViewById(R.id.pb_bottom_progress);
        mBottomToolsLayout = view.findViewById(R.id.bottom_tools_layout);
        tv_speed = view.findViewById(R.id.tv_speed);
        tv_av_scale = view.findViewById(R.id.tv_av_scale);
        iv_av_next = view.findViewById(R.id.iv_av_next);
        iv_fullscreen = view.findViewById(R.id.iv_fullscreen);
    }

    private void initListener() {
        mSeekBar.setOnSeekBarChangeListener(this);
        mIvPlay.setOnClickListener(this);
        tv_speed.setOnClickListener(this);
        tv_av_scale.setOnClickListener(this);
        iv_av_next.setOnClickListener(this);
        iv_fullscreen.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        if (v == mIvPlay) {
            mControlWrapper.togglePlay();
        } else if (v == iv_fullscreen) {
            toggleFullScreen();
        } else if (v == tv_av_scale) {
            mCurrentScaleType = (mCurrentScaleType + 1) % mScaleTypeArray.length;
            mControlWrapper.setScreenScaleType(mScaleTypeArray[mCurrentScaleType]);

            switch (mScaleTypeArray[mCurrentScaleType]) {
                case SCREEN_SCALE_DEFAULT:
                    tv_av_scale.setText(R.string.video_default);
                    break;

                case SCREEN_SCALE_16_9:
                    //16：9比例类型，最为常见
                    tv_av_scale.setText("16:9");
                    break;
                case SCREEN_SCALE_4_3:
                    //4：3比例类型，也比较常见
                    tv_av_scale.setText("4:3");
                    break;
                case SCREEN_SCALE_MATCH_PARENT:
                    //充满整个控件视图
                    tv_av_scale.setText(R.string.fill);
                    break;

                case SCREEN_SCALE_ORIGINAL:
                    //原始类型，指视频的原始类型
                    tv_av_scale.setText(R.string.original_size);
                    break;

                case SCREEN_SCALE_CENTER_CROP:
                    //剧中裁剪类型
                    tv_av_scale.setText(R.string.center_cut);
                    break;
            }
        } else if (v == tv_speed) {
            new SpeedDialog(mContext, mControlWrapper.getSpeed() + "", new SpeedDialog.OnSpeedItemClickListener() {
                @Override
                public void onSpeedItemClick(String speed) {
                    setSpeed(speed);
                    if(controllerClickListener!=null){
                        controllerClickListener.onSpeedClick(speed);

                    }
                }
            }).show();

        } else if (v == iv_av_next) {
            if (controllerClickListener != null) {
                controllerClickListener.next();
            }
        }
    }

    protected int getLayoutId() {
        return R.layout.av_video_player_normal_bottom;
    }

    /**
     * 是否显示底部进度条，默认显示
     */
    public void showBottomProgress(boolean isShow) {
        mIsShowBottomProgress = isShow;
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
        if (isVisible) {
            mLlBottomContainer.setVisibility(VISIBLE);
            if (anim != null) {
                mLlBottomContainer.startAnimation(anim);
            }
            if (mIsShowBottomProgress) {
                mPbBottomProgress.setVisibility(GONE);
            }
        } else {
            mLlBottomContainer.setVisibility(GONE);
            if (anim != null) {
                mLlBottomContainer.startAnimation(anim);
            }
            if (mIsShowBottomProgress) {
                mPbBottomProgress.setVisibility(VISIBLE);
                AlphaAnimation animation = new AlphaAnimation(0f, 1f);
                animation.setDuration(300);
                mPbBottomProgress.startAnimation(animation);
            }
        }
        if (getVisibility() == VISIBLE) {
            boolean isLand = ((Activity) getContext()).getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            if (mControlWrapper.isFullScreen() || isLand) {
                mBottomToolsLayout.setVisibility(VISIBLE);
            } else {
                mBottomToolsLayout.setVisibility(GONE);
            }
        }
    }

    @Override
    public void onPlayStateChanged(int playState) {
        switch (playState) {
            case ConstantKeys.CurrentState.STATE_IDLE:
            case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
                setVisibility(GONE);
                mPbBottomProgress.setProgress(0);
                mPbBottomProgress.setSecondaryProgress(0);
                mSeekBar.setProgress(0);
                mSeekBar.setSecondaryProgress(0);
                break;
            case ConstantKeys.CurrentState.STATE_START_ABORT:
            case ConstantKeys.CurrentState.STATE_PREPARING:
            case ConstantKeys.CurrentState.STATE_PREPARED: {

            }
            case ConstantKeys.CurrentState.STATE_ERROR:
            case ConstantKeys.CurrentState.STATE_ONCE_LIVE:
                setVisibility(GONE);
                break;
            case ConstantKeys.CurrentState.STATE_PLAYING:
                mIvPlay.setSelected(true);
                if (mIsShowBottomProgress) {
                    if (mControlWrapper.isShowing()) {
                        mPbBottomProgress.setVisibility(GONE);
                        mLlBottomContainer.setVisibility(VISIBLE);
                    } else {
                        mLlBottomContainer.setVisibility(GONE);
                        mPbBottomProgress.setVisibility(VISIBLE);
                    }
                } else {
                    mLlBottomContainer.setVisibility(GONE);
                }
                setVisibility(VISIBLE);
                //开始刷新进度
                mControlWrapper.startProgress();
                break;
            case ConstantKeys.CurrentState.STATE_PAUSED:
                mIvPlay.setSelected(false);
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
            case ConstantKeys.CurrentState.STATE_COMPLETED:
                mIvPlay.setSelected(mControlWrapper.isPlaying());
                break;
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {
        switch (playerState) {
            case ConstantKeys.PlayMode.MODE_NORMAL:
                iv_fullscreen.setSelected(false);
                mBottomToolsLayout.setVisibility(GONE);
                break;
            case ConstantKeys.PlayMode.MODE_FULL_SCREEN:
                iv_fullscreen.setSelected(true);
                mBottomToolsLayout.setVisibility(VISIBLE);
                break;
        }
        Activity activity = PlayerUtils.scanForActivity(mContext);
        if (activity != null && mControlWrapper.hasCutout()) {
            int orientation = activity.getRequestedOrientation();
            int cutoutHeight = mControlWrapper.getCutoutHeight();
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                mLlBottomContainer.setPadding(0, 0, 0, 0);
                mPbBottomProgress.setPadding(0, 0, 0, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                mLlBottomContainer.setPadding(cutoutHeight, 0, 0, 0);
                mPbBottomProgress.setPadding(cutoutHeight, 0, 0, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                mLlBottomContainer.setPadding(0, 0, cutoutHeight, 0);
                mPbBottomProgress.setPadding(0, 0, cutoutHeight, 0);
            }
        }
    }

    /**
     * 刷新进度回调，子类可在此方法监听进度刷新，然后更新ui
     *
     * @param duration 视频总时长
     * @param position 视频当前时长
     */
    @Override
    public void setProgress(int duration, int position) {
        if (mIsDragging) {
            return;
        }

        if (mSeekBar != null) {
            if (duration > 0) {
                mSeekBar.setEnabled(true);
                int pos = (int) (position * 1.0 / duration * mSeekBar.getMax());
                mSeekBar.setProgress(pos);
                mPbBottomProgress.setProgress(pos);
            } else {
                mSeekBar.setEnabled(false);
            }
            int percent = mControlWrapper.getBufferedPercentage();
            if (percent >= 95) {
                //解决缓冲进度不能100%问题
                mSeekBar.setSecondaryProgress(mSeekBar.getMax());
                mPbBottomProgress.setSecondaryProgress(mPbBottomProgress.getMax());
            } else {
                mSeekBar.setSecondaryProgress(percent);
                mPbBottomProgress.setSecondaryProgress(percent);
            }
        }

        if (mTvTotalTime != null) {
            mTvTotalTime.setText(PlayerUtils.formatTime(duration));
        }
        if (mTvCurrTime != null) {
            mTvCurrTime.setText(PlayerUtils.formatTime(position));
        }


        if (VideoPlayerConfig.newBuilder().build().mIsShowToast) {
            long time = VideoPlayerConfig.newBuilder().build().mShowToastTime;
            if (time <= 0) {
                time = 5;
            }
            long currentPosition = mControlWrapper.getCurrentPosition();
            Log.d("progress---", "duration---" + duration + "--currentPosition--" + currentPosition);
            if (duration - currentPosition < 2 * time * 1000) {
                //当前视频播放到最后3s时，弹出toast提示：即将自动为您播放下一个视频。
                if ((duration - currentPosition) / 1000 % 60 == time) {
                    Log.d("progress---", "即将自动为您播放下一个视频");
                    if (listener != null) {
                        listener.showToastOrDialog();
                    }
                }
            }
        }

        //更新底部的倍速显示
        mCurrentSpeed = mControlWrapper.getSpeed();
        if (mCurrentSpeed != mLastSpeed) {
            setSpeed(mCurrentSpeed + "");
            mLastSpeed = mCurrentSpeed;
        }


    }

    @Override
    public void onLockStateChanged(boolean isLocked) {
        onVisibilityChanged(!isLocked, null);
    }


    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mIsDragging = true;
        mControlWrapper.stopProgress();
        mControlWrapper.stopFadeOut();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        long duration = mControlWrapper.getDuration();
        long newPosition = (duration * seekBar.getProgress()) / mPbBottomProgress.getMax();
        mControlWrapper.seekTo((int) newPosition);
        mIsDragging = false;
        mControlWrapper.startProgress();
        mControlWrapper.startFadeOut();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) {
            return;
        }
        long duration = mControlWrapper.getDuration();
        long newPosition = (duration * progress) / mPbBottomProgress.getMax();
        if (mTvCurrTime != null) {
            mTvCurrTime.setText(PlayerUtils.formatTime(newPosition));
        }
    }

    public void setSpeed(String speed) {
        // 转为小写处理
        switch (speed) {
            case SpeedInterface.sp0_50:
                mControlWrapper.setSpeed(0.50f);
                setTvSpeed(getResources().getString(R.string.av_speed_1));
                break;
            case SpeedInterface.sp0_75:
                mControlWrapper.setSpeed(0.75f);
                setTvSpeed(getResources().getString(R.string.av_speed_2));
                break;
//            case SpeedInterface.sp1_0:
//                mControlWrapper.setSpeed(1f);
//                setTvSpeed(getResources().getString(R.string.av_speed_3));
//                break;
            case SpeedInterface.sp1_25:
                mControlWrapper.setSpeed(1.25f);
                setTvSpeed(getResources().getString(R.string.av_speed_4));
                break;
            case SpeedInterface.sp1_50:
                mControlWrapper.setSpeed(1.5f);
                setTvSpeed(getResources().getString(R.string.av_speed_5));
                break;
            case SpeedInterface.sp2_0:
                mControlWrapper.setSpeed(2f);
                setTvSpeed(getResources().getString(R.string.av_speed_6));
                break;
            case SpeedInterface.sp3_0:
                mControlWrapper.setSpeed(3f);
                setTvSpeed(getResources().getString(R.string.av_speed_7));
                break;
            default:
                mControlWrapper.setSpeed(1f);
                setTvSpeed(getResources().getString(R.string.av_speed_3));
                break;
        }
    }

    public void setTvSpeed(String speed) {
        if (tv_speed != null) {
            tv_speed.setText(speed);
        }
    }

    /**
     * 横竖屏切换
     */
    private void toggleFullScreen() {
        Activity activity = PlayerUtils.scanForActivity(getContext());
        mControlWrapper.toggleFullScreen(activity);
    }

    private OnToastListener listener;

    public void setListener(OnToastListener listener) {
        this.listener = listener;
    }

    public interface OnToastListener {
        void showToastOrDialog();
    }

    public void setControllerClickListener(ControllerClickListener controllerClickListener) {
        this.controllerClickListener = controllerClickListener;
    }

    /**
     * 隐藏下一集
     */
    public void hideNextBtn() {
        if (iv_av_next != null) {
            iv_av_next.setVisibility(View.GONE);
        }
    }

    /**
     * 添加自定义view
     *
     */
    public void addTools(View view) {
        if (mBottomToolsLayout != null && view != null) {
            mBottomToolsLayout.addView(view, 0);
        }
    }


}
