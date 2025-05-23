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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yc.video.bridge.ControlWrapper;
import com.yc.video.config.ConstantKeys;
import com.yc.video.tool.PlayerUtils;
import com.yc.video.ui.view.InterControlView;

import cn.mahua.av.R;
import cn.mahua.av.play.ControllerClickListener;


/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2017/11/9
 *     desc  : 出错提示界面
 *     revise:
 * </pre>
 */
public class AvErrorView extends LinearLayout implements InterControlView, View.OnClickListener {

    private Context mContext;
    private float mDownX;
    private float mDownY;
    private TextView mTvMessage;
    private ImageView mIvStopFullscreen;

    private ControlWrapper mControlWrapper;

    private LinearLayout mLinearLayout;

    public AvErrorView(Context context) {
        super(context);
        init(context);
    }

    public AvErrorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvErrorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context){
        this.mContext = context;
        setVisibility(GONE);
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.av_video_player_error, this, true);
        initFindViewById(view);
        initListener();
        setClickable(true);
    }

    private void initFindViewById(View view) {
        mTvMessage = view.findViewById(R.id.tv_message);
        mIvStopFullscreen = view.findViewById(R.id.iv_stop_fullscreen);
        mLinearLayout = view.findViewById(R.id.linearLayout);
    }

    private void initListener() {
        mIvStopFullscreen.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
       if (v == mIvStopFullscreen){
            //点击返回键
            if (mControlWrapper.isFullScreen()) {
                Activity activity = PlayerUtils.scanForActivity(mContext);
                if (activity != null && !activity.isFinishing()) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    mControlWrapper.stopFullScreen();
                }
            }
        }
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
    public void onPlayStateChanged(int playState,String msg) {
        if (playState == ConstantKeys.CurrentState.STATE_ERROR||playState == ConstantKeys.CurrentState.STATE_NETWORK_ERROR||playState == ConstantKeys.CurrentState.STATE_PARSE_ERROR) {
            bringToFront();
            setVisibility(VISIBLE);
            mIvStopFullscreen.setVisibility(mControlWrapper.isFullScreen() ? VISIBLE : GONE);
            mTvMessage.setText(getContext().getString(R.string.error_message));
        }  else if (playState == ConstantKeys.CurrentState.STATE_IDLE) {
            setVisibility(GONE);
        } else if (playState == ConstantKeys.CurrentState.STATE_ONCE_LIVE) {
            setVisibility(GONE);
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {

    }

    @Override
    public void setProgress(int duration, int position) {

    }

    @Override
    public void onLockStateChanged(boolean isLock) {

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float absDeltaX = Math.abs(ev.getX() - mDownX);
                float absDeltaY = Math.abs(ev.getY() - mDownY);
                if (absDeltaX > ViewConfiguration.get(getContext()).getScaledTouchSlop() ||
                        absDeltaY > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
            case MotionEvent.ACTION_UP:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void addTools(String text,OnClickListener clickListener) {
        if (mLinearLayout != null && text != null) {
            View view=  LayoutInflater.from(getContext()).inflate(R.layout.av_error_item,null);
            TextView textView=view.findViewById(R.id.text);
            textView.setText(text);
            view.setOnClickListener(clickListener);
            mLinearLayout.addView(view);
        }
    }
}
