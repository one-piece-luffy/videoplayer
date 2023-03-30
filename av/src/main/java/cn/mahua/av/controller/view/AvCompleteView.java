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
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

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
 *     desc  : 自动播放完成界面
 *     revise:
 * </pre>
 */
public class AvCompleteView extends FrameLayout implements InterControlView, View.OnClickListener {

    private Context mContext;
    private ControlWrapper mControlWrapper;
    private FrameLayout mCompleteContainer;
    private ImageView mIvStopFullscreen;
    private LinearLayout mLlReplay;
    private ImageView mIvReplay;
    private LinearLayout mLlShare;
    private ImageView mIvShare;
    private ImageView iv_play;
    private View shareLayout;
    protected ControllerClickListener controllerClickListener;
    public AvCompleteView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvCompleteView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvCompleteView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context){
        this.mContext = context;
        setVisibility(GONE);
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.av_video_player_completed, this, true);
        initFindViewById(view);
        initListener();
        setClickable(true);
    }

    private void initFindViewById(View view) {
        mCompleteContainer = view.findViewById(R.id.complete_container);
        mIvStopFullscreen = view.findViewById(R.id.iv_stop_fullscreen);
        mLlReplay = view.findViewById(R.id.ll_replay);
        mIvReplay = view.findViewById(R.id.iv_replay);
        mLlShare = view.findViewById(R.id.ll_share);
        mIvShare = view.findViewById(R.id.iv_share);
        iv_play = view.findViewById(R.id.iv_play);
        shareLayout = view.findViewById(R.id.shareLayout);
    }

    private void initListener() {
        mLlReplay.setOnClickListener(this);
        mLlShare.setOnClickListener(this);
        mIvStopFullscreen.setOnClickListener(this);
        iv_play.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mLlReplay||v==iv_play){
            //点击重播
            mControlWrapper.replay(true);
        } else if (v == mLlShare){
            //点击分享
            if(controllerClickListener!=null){
                controllerClickListener.share();
            }
        } else if (v == mIvStopFullscreen){
            //点击返回键
            if (mControlWrapper.isFullScreen()) {
                Activity activity = PlayerUtils.scanForActivity(mContext);
                if (activity != null && !activity.isFinishing()) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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
        if (playState == ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING) {
            setVisibility(VISIBLE);
            mIvStopFullscreen.setVisibility(mControlWrapper.isFullScreen() ? VISIBLE : GONE);
            bringToFront();
        } else {
            setVisibility(GONE);
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {
        if (playerState == ConstantKeys.PlayMode.MODE_FULL_SCREEN) {
            mIvStopFullscreen.setVisibility(VISIBLE);
        } else if (playerState == ConstantKeys.PlayMode.MODE_NORMAL) {
            mIvStopFullscreen.setVisibility(GONE);
        }

        Activity activity = PlayerUtils.scanForActivity(mContext);
        if (activity != null && mControlWrapper.hasCutout()) {
            int orientation = activity.getRequestedOrientation();
            int cutoutHeight = mControlWrapper.getCutoutHeight();
            LinearLayout.LayoutParams sflp = (LinearLayout.LayoutParams) mIvStopFullscreen.getLayoutParams();
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                sflp.setMargins(0, 0, 0, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                sflp.setMargins(cutoutHeight, 0, 0, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                sflp.setMargins(0, 0, 0, 0);
            }
        }
    }

    @Override
    public void setProgress(int duration, int position) {

    }

    @Override
    public void onLockStateChanged(boolean isLock) {

    }
    public void setControllerClickListener(ControllerClickListener controllerClickListener) {
        this.controllerClickListener = controllerClickListener;
    }
    public void showShare(boolean show){
        if (show) {
            shareLayout.setVisibility(View.VISIBLE);
            iv_play.setVisibility(View.GONE);
        } else {
            shareLayout.setVisibility(View.GONE);
            iv_play.setVisibility(View.VISIBLE);
        }
    }
}
