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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yc.video.bridge.ControlWrapper;
import com.yc.video.config.ConstantKeys;
import com.yc.video.controller.IGestureComponent;
import com.yc.video.tool.PlayerUtils;

import cn.mahua.av.R;
import cn.mahua.av.SpeedInterface;
import cn.mahua.av.listener.OnLongPressListener;
import cn.mahua.av.listener.OnSpeedChangeListener;
import cn.mahua.av.play.ControllerClickListener;

/**
 * 手势控制
 */

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2017/11/9
 *     desc  : 手势控制
 *     revise: 用于滑动改变亮度和音量的功能
 * </pre>
 */
public class AvGestureView extends FrameLayout implements IGestureComponent {

    private Context mContext;
    private ControlWrapper mControlWrapper;
    private LinearLayout mLlCenterContainer;
    private ImageView mIvIcon;
    private TextView mTvPercent;
    private ProgressBar mProPercent;
    ViewGroup mLongSpeed;
    float mLastSpeed;
    boolean isLongPress;
    //是否滑动改变了进度
    long mSlidePosition=-1;
    ImageView mIvQuick;
    final  String TAG="AvGestureView";
    AnimationDrawable mAnimationDrawable;
    OnSpeedChangeListener onSpeedChangeListener;
    OnLongPressListener onLongPressListener;
    ControllerClickListener controllerClickListener;
    boolean mIsLock;
    public AvGestureView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvGestureView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvGestureView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context){
        this.mContext = context;
        setVisibility(GONE);
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.av_video_player_gesture, this, true);
        initFindViewById(view);
        initListener();
    }

    private void initFindViewById(View view) {
        mLlCenterContainer = view.findViewById(R.id.ll_center_container);
        mIvIcon = view.findViewById(R.id.iv_icon);
        mTvPercent = view.findViewById(R.id.tv_percent);
        mProPercent = view.findViewById(R.id.pro_percent);
        mLongSpeed = view.findViewById(R.id.long_speed);
        mIvQuick = view.findViewById(R.id.iv_quick);

    }

    private void initListener() {

    }

    public void setOnSpeedChangeListener(OnSpeedChangeListener onSpeedChangeListener) {
        this.onSpeedChangeListener = onSpeedChangeListener;
    }
    public void setOnLongPressListener(OnLongPressListener onLongPressListener) {
        this.onLongPressListener = onLongPressListener;
    }
    public void setControllerClickListener(ControllerClickListener controllerClickListener) {
        this.controllerClickListener = controllerClickListener;
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
    public void onPlayerStateChanged(int playerState) {

    }

    /**
     * 开始滑动
     */
    @Override
    public void onStartSlide() {
        mControlWrapper.hide();
        mLlCenterContainer.setVisibility(VISIBLE);
        mLlCenterContainer.setAlpha(1f);
    }

    /**
     * 结束滑动
     * 这个是指，手指抬起或者意外结束事件的时候，调用这个方法
     */
    @Override
    public void onStopSlide() {
        mLlCenterContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mLlCenterContainer.setVisibility(GONE);
                    }
                })
                .start();
        mLongSpeed.setVisibility(View.GONE);
//        Log.i(TAG, "onstopSlide speed:" + mLastSpeed + " curSpeed:" + mControlWrapper.getSpeed());
        if (mLastSpeed != 0 && isLongPress) {
            isLongPress = false;
            mControlWrapper.setSpeed(mLastSpeed);
            if (onSpeedChangeListener != null) {
                onSpeedChangeListener.onChange(mLastSpeed+"");
            }
            if(mAnimationDrawable!=null){
                mAnimationDrawable.stop();
            }
        }
        if(mSlidePosition>-1){
            if(controllerClickListener!=null){
                controllerClickListener.onUserSeek(mSlidePosition);
                mSlidePosition=-1;
            }
        }
        if(onLongPressListener!=null){
            onLongPressListener.onLongPress(false);
        }
    }

    /**
     * 滑动调整进度
     * @param slidePosition 滑动进度
     * @param currentPosition 当前播放进度
     * @param duration 视频总长度
     */
    @Override
    public void onPositionChange(int slidePosition, int currentPosition, int duration) {
        mProPercent.setVisibility(GONE);
        if (slidePosition > currentPosition) {
            mIvIcon.setImageResource(R.drawable.ic_player_fast_forward);
        } else {
            mIvIcon.setImageResource(R.drawable.ic_player_fast_rewind);
        }
        mTvPercent.setText(String.format("%s/%s", PlayerUtils.formatTime(slidePosition), PlayerUtils.formatTime(duration)));
        mSlidePosition=slidePosition;
    }

    /**
     * 滑动调整亮度
     * @param percent 亮度百分比
     */
    @Override
    public void onBrightnessChange(int percent) {
        mProPercent.setVisibility(VISIBLE);
        mIvIcon.setImageResource(R.drawable.ic_palyer_brightness);
        mTvPercent.setText(percent + "%");
        mProPercent.setProgress(percent);
    }

    /**
     * 滑动调整音量
     * @param percent 音量百分比
     */
    @Override
    public void onVolumeChange(int percent) {
        mProPercent.setVisibility(VISIBLE);
        if (percent <= 0) {
            mIvIcon.setImageResource(R.drawable.ic_player_volume_off);
        } else {
            mIvIcon.setImageResource(R.drawable.ic_player_volume_up);
        }
        mTvPercent.setText(percent + "%");
        mProPercent.setProgress(percent);
    }

    @Override
    public void onLongPress() {
        if(mIsLock){
            return;
        }
        mLastSpeed = mControlWrapper.getSpeed();
        if(onLongPressListener!=null){
            onLongPressListener.onLongPress(true);
        }
        isLongPress=true;
        mControlWrapper.setSpeed(3);
        mLongSpeed.setVisibility(View.VISIBLE);
        mAnimationDrawable = (AnimationDrawable) mIvQuick.getDrawable();
        mAnimationDrawable.start();
        if (onSpeedChangeListener != null) {
            onSpeedChangeListener.onChange(SpeedInterface.sp3_0);
        }
    }

    @Override
    public void onPlayStateChanged(int playState) {
        if (playState == ConstantKeys.CurrentState.STATE_IDLE
                || playState == ConstantKeys.CurrentState.STATE_START_ABORT
                || playState == ConstantKeys.CurrentState.STATE_PREPARING
                || playState == ConstantKeys.CurrentState.STATE_PREPARED
                || playState == ConstantKeys.CurrentState.STATE_ERROR
                || playState == ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING
                || playState == ConstantKeys.CurrentState.STATE_ONCE_LIVE) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    @Override
    public void setProgress(int duration, int position) {

    }

    @Override
    public void onLockStateChanged(boolean isLock) {
        mIsLock=isLock;
    }


}
