package cn.mahua.av.controller;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.yc.video.config.ConstantKeys;
import com.yc.video.controller.GestureVideoController;
import com.yc.video.tool.BaseToast;
import com.yc.video.tool.PlayerUtils;
import com.yc.video.ui.view.CustomLiveControlView;
import com.yc.video.ui.view.CustomOncePlayView;

import cn.mahua.av.R;
import cn.mahua.av.controller.view.AvCompleteView;
import cn.mahua.av.controller.view.AvErrorView;
import cn.mahua.av.controller.view.AvGestureView;
import cn.mahua.av.controller.view.AvGuideView;
import cn.mahua.av.controller.view.AvNormalPlayBottomView;
import cn.mahua.av.controller.view.AvPrepareView;
import cn.mahua.av.controller.view.AvTitleView;
import cn.mahua.av.listener.OnLongPressListener;
import cn.mahua.av.listener.OnSpeedChangeListener;
import cn.mahua.av.play.ControllerClickListener;
import cn.mahua.av.utils.AvSharePreference;

public class AvNormalPlayController extends GestureVideoController implements View.OnClickListener {
    public AvNormalPlayController(Context context) {
        super(context);
    }

    private Context mContext;
    private ImageView mLockButton;
    private ImageView thumb;
    //顶部title
    private AvTitleView titleView;
    //手势引导图
    private AvGuideView mGuideView;
    //底部视图
    private AvNormalPlayBottomView mAvBottomView;
    private CustomLiveControlView liveControlView;
    private CustomOncePlayView customOncePlayView;
    private TextView tvLiveWaitMessage;
    protected ControllerClickListener controllerClickListener;
    //播放完成时的视图
    AvCompleteView mAvCompleteView;
    //错误视图
    AvErrorView mErrorView;
    //准备视图
    AvPrepareView mPrepareView;
    //滑动控制视图
    AvGestureView gestureControlView;
    /**
     * 是否是直播，默认不是
     */
    public static boolean IS_LIVE = false;
    //是否长按状态
    boolean isLongPress;
    //上一次选择的播放速度
    String lastSpeed;
    //记住播放速度
    boolean remindSpeed = true;


    @Override
    protected int getLayoutId() {
        return R.layout.av_normal_player_standard;
    }

    @Override
    protected void initView(Context context) {
        super.initView(context);
        this.mContext = context;
        initFindViewById();
        initListener();
        initConfig();
        lastSpeed = AvSharePreference.getLastPlaySpeed(context);
    }

    private void initFindViewById() {
        mLockButton = findViewById(R.id.lock);
    }

    private void initListener() {
        mLockButton.setOnClickListener(this);
    }

    private void initConfig() {
        //根据屏幕方向自动进入/退出全屏
        setEnableOrientation(true);
        //设置可以滑动调节进度
        setCanChangePosition(true);
        //竖屏也开启手势操作，默认关闭
        setEnableInNormal(true);
        //滑动调节亮度，音量，进度，默认开启
        setGestureEnabled(true);
        //先移除多有的视图view
        removeAllControlComponent();
        //添加视图到界面
        addDefaultControlComponent();
    }


    /**
     * 快速添加各个组件
     * 需要注意各个层级
     */
    public void addDefaultControlComponent() {
        //添加自动完成播放界面view
        mAvCompleteView = new AvCompleteView(mContext);
        mAvCompleteView.setVisibility(GONE);
        this.addControlComponent(mAvCompleteView);

        //添加错误界面view
        mErrorView = new AvErrorView(mContext);
        mErrorView.setVisibility(GONE);
        this.addControlComponent(mErrorView);

        //添加与加载视图界面view，准备播放界面
        mPrepareView = new AvPrepareView(mContext);
        thumb = mPrepareView.getThumb();
        mPrepareView.setClickStart();
        this.addControlComponent(mPrepareView);

        //添加标题栏
        titleView = new AvTitleView(mContext);
        titleView.setVisibility(VISIBLE);
        this.addControlComponent(titleView);

        //添加直播/回放视频底部控制视图
        changePlayType();

        //添加引导图
        mGuideView = new AvGuideView(mContext);
        mGuideView.setVisibility(GONE);
        this.addControlComponent(mGuideView);

        gestureControlView = new AvGestureView(mContext);
        gestureControlView.setOnSpeedChangeListener(new OnSpeedChangeListener() {
            @Override
            public void onChange(String speed) {
                setSpeed(speed);
            }
        });
        gestureControlView.setOnLongPressListener(new OnLongPressListener() {
            @Override
            public void onLongPress(boolean longpress) {
                isLongPress = longpress;
            }
        });

        this.addControlComponent(gestureControlView);
    }


    /**
     * 切换直播/回放类型
     */
    public void changePlayType() {
        if (IS_LIVE) {
            //添加底部播放控制条
            if (liveControlView == null) {
                liveControlView = new CustomLiveControlView(mContext);
            }
            this.removeControlComponent(liveControlView);
            this.addControlComponent(liveControlView);

            //添加直播还未开始视图
            if (customOncePlayView == null) {
                customOncePlayView = new CustomOncePlayView(mContext);
                tvLiveWaitMessage = customOncePlayView.getTvMessage();
            }
            this.removeControlComponent(customOncePlayView);
            this.addControlComponent(customOncePlayView);

            //直播视频，移除回放视图
            if (mAvBottomView != null) {
                this.removeControlComponent(mAvBottomView);
            }
        } else {
            //添加底部播放控制条
            if (mAvBottomView == null) {
                mAvBottomView = new AvNormalPlayBottomView(mContext);
                //是否显示底部进度条。默认显示
                mAvBottomView.showBottomProgress(true);
            }

            this.removeControlComponent(mAvBottomView);
            this.addControlComponent(mAvBottomView);

            //正常视频，移除直播视图
            if (liveControlView != null) {
                this.removeControlComponent(liveControlView);
            }
            if (customOncePlayView != null) {
                this.removeControlComponent(customOncePlayView);
            }
        }
        setCanChangePosition(!IS_LIVE);
    }


    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.lock) {
            mControlWrapper.toggleLockState();
        }
    }

    @Override
    protected void onLockStateChanged(boolean isLocked) {
        if (isLocked) {
            mLockButton.setSelected(true);
            String string = mContext.getResources().getString(R.string.locked);
            BaseToast.showRoundRectToast(string);
        } else {
            mLockButton.setSelected(false);
            String string = mContext.getResources().getString(R.string.unlocked);
            BaseToast.showRoundRectToast(string);
        }
    }

    @Override
    protected void onVisibilityChanged(boolean isVisible, Animation anim) {
        if (mControlWrapper.isFullScreen()) {
            if (isVisible) {
                if (mLockButton.getVisibility() == GONE) {
                    mLockButton.setVisibility(VISIBLE);
                    if (anim != null) {
                        mLockButton.startAnimation(anim);
                    }
                }
            } else {
                mLockButton.setVisibility(GONE);
                if (anim != null) {
                    mLockButton.startAnimation(anim);
                }
            }
        }
    }

    /**
     * 播放模式
     * 普通模式，小窗口模式，正常模式三种其中一种
     * MODE_NORMAL              普通模式
     * MODE_FULL_SCREEN         全屏模式
     * MODE_TINY_WINDOW         小屏模式
     *
     * @param playerState 播放模式
     */
    @Override
    protected void onPlayerStateChanged(int playerState) {
        super.onPlayerStateChanged(playerState);
        switch (playerState) {
            case ConstantKeys.PlayMode.MODE_NORMAL:
                setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mLockButton.setVisibility(GONE);
                break;
            case ConstantKeys.PlayMode.MODE_FULL_SCREEN:
                if (isShowing()) {
                    mLockButton.setVisibility(VISIBLE);
                } else {
                    mLockButton.setVisibility(GONE);
                }
                break;
        }

        if (mActivity != null && hasCutout()) {
            int orientation = mActivity.getRequestedOrientation();
            int dp24 = PlayerUtils.dp2px(getContext(), 24);
            int cutoutHeight = getCutoutHeight();
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                LayoutParams lblp = (LayoutParams) mLockButton.getLayoutParams();
                lblp.setMargins(dp24, 0, dp24, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                LayoutParams layoutParams = (LayoutParams) mLockButton.getLayoutParams();
                layoutParams.setMargins(dp24 + cutoutHeight, 0, dp24 + cutoutHeight, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                LayoutParams layoutParams = (LayoutParams) mLockButton.getLayoutParams();
                layoutParams.setMargins(dp24, 0, dp24, 0);
            }
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
     *
     * @param playState 播放状态，主要是指播放器的各种状态
     */
    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        switch (playState) {
            //调用release方法会回到此状态
            case ConstantKeys.CurrentState.STATE_IDLE:
                mLockButton.setSelected(false);
                break;
            case ConstantKeys.CurrentState.STATE_PLAYING:
            case ConstantKeys.CurrentState.STATE_PAUSED:
            case ConstantKeys.CurrentState.STATE_PREPARED:
            case ConstantKeys.CurrentState.STATE_ERROR:
            case ConstantKeys.CurrentState.STATE_COMPLETED:

                break;
            case ConstantKeys.CurrentState.STATE_PREPARING:
            case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
                mLockButton.setVisibility(GONE);
                mLockButton.setSelected(false);
                break;
        }
    }

    @Override
    public boolean onBackPressed() {
        if (isLocked()) {
            show();
            String string = mContext.getResources().getString(R.string.lock_tip);
            BaseToast.showRoundRectToast(string);
            return true;
        }
        if (mControlWrapper.isFullScreen()) {
            return stopFullScreen();
        }
        Activity activity = PlayerUtils.scanForActivity(getContext());
        //如果不是全屏模式，则直接关闭页面activity
        if (PlayerUtils.isActivityLiving(activity)) {
            activity.finish();
        }
        return super.onBackPressed();
    }

    /**
     * 刷新进度回调，子类可在此方法监听进度刷新，然后更新ui
     *
     * @param duration 视频总时长
     * @param position 视频当前时长
     */
    @Override
    protected void setProgress(int duration, int position) {
        super.setProgress(duration, position);
        if (remindSpeed && !isLongPress && !TextUtils.isEmpty(lastSpeed) && !lastSpeed.equals(String.valueOf(mControlWrapper.getSpeed()))) {
            setSpeed(lastSpeed);
        }
    }

    @Override
    public void destroy() {

    }

    public ImageView getThumb() {
        return thumb;
    }

    public void setTitle(String title) {
        if (titleView != null) {
            titleView.setTitle(title);
        }
    }


    public TextView getTvLiveWaitMessage() {
        return tvLiveWaitMessage;
    }


    public void setSpeed(String speed) {
        if (mAvBottomView != null) {
            mAvBottomView.setSpeed(speed);
        }
    }

    public void toggleFullScreen() {
        if (mControlWrapper == null)
            return;
        Activity activity = PlayerUtils.scanForActivity(getContext());
        mControlWrapper.toggleFullScreen(activity);
    }

    public void setControllerClickListener(ControllerClickListener controllerClickListener) {
        this.controllerClickListener = controllerClickListener;

        if (mAvBottomView != null) {
            mAvBottomView.setControllerClickListener(controllerClickListener);
        }
        if (mAvCompleteView != null) {
            mAvCompleteView.setControllerClickListener(controllerClickListener);
        }
        if (mErrorView != null) {
            mErrorView.setControllerClickListener(controllerClickListener);
        }
        if (gestureControlView != null) {
            gestureControlView.setControllerClickListener(controllerClickListener);
        }
    }

    public void showTcpSpeed(boolean show) {
        if (mPrepareView != null) {
            mPrepareView.showTcpSpeed(show);
        }
    }

    /**
     * 添加自定义view
     *
     * @param view
     */
    public void addTools(View view) {
        if (mAvBottomView != null) {
            mAvBottomView.addTools(view);
        }
    }

    /**
     * 隐藏下一集
     */
    public void hideNextBtn() {
        if (mAvBottomView != null) {
            mAvBottomView.hideNextBtn();
        }
    }

    /**
     * 是否显示底部进度条，默认显示
     */
    public void showBottomProgress(boolean isShow) {
        if (mAvBottomView != null) {
            mAvBottomView.showBottomProgress(isShow);
        }
    }

    public void setLoadingMessage(String message) {
        if (mPrepareView != null) {
            mPrepareView.setLoadingMessage(message);
        }
    }

    /**
     * 直接显示加载框
     */
    public void showPreviewLoading() {
        if (mPrepareView != null) {
            mPrepareView.showLoading();
        }
    }

    /**
     * 播放完成是否显示分享布局
     *
     * @param show
     */
    public void showShare(boolean show) {
        if (mAvBottomView != null) {
            mAvCompleteView.showShare(show);
        }
    }

    public void setRemindSpeed(boolean remindSpeed) {
        this.remindSpeed = remindSpeed;
    }
}
