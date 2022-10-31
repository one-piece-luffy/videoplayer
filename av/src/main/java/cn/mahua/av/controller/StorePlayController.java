package cn.mahua.av.controller;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.yc.video.config.ConstantKeys;
import com.yc.video.controller.GestureVideoController;
import com.yc.video.tool.BaseToast;
import com.yc.video.tool.PlayerUtils;
import com.yc.video.ui.view.CustomErrorView;
import com.yc.video.ui.view.CustomLiveControlView;
import com.yc.video.ui.view.CustomOncePlayView;
import com.yc.video.ui.view.CustomPrepareView;

import cn.mahua.av.R;
import cn.mahua.av.SpeedInterface;
import cn.mahua.av.controller.view.AvCompleteView;
import cn.mahua.av.controller.view.AvGestureView;
import cn.mahua.av.controller.view.AvStoreBottomView;
import cn.mahua.av.controller.view.AvTitleView;
import cn.mahua.av.play.ControllerClickListener;

public class StorePlayController extends GestureVideoController implements View.OnClickListener{
    public StorePlayController(Context context) {
        super(context);
    }

    private Context mContext;
    private ImageView mLockButton;
    private ProgressBar mLoadingProgress;
    private ImageView thumb;
    private AvTitleView titleView;
    private AvStoreBottomView vodControlView;
    private CustomLiveControlView liveControlView;
    private CustomOncePlayView customOncePlayView;
    private TextView tvLiveWaitMessage;
    protected ControllerClickListener controllerClickListener;
    AvCompleteView mAvCompleteView;
    /**
     * 是否是直播，默认不是
     */
    public static boolean IS_LIVE = false;



    @Override
    protected int getLayoutId() {
        return R.layout.av_video_player_standard;
    }

    @Override
    protected void initView(Context context) {
        super.initView(context);
        this.mContext = context;
        initFindViewById();
        initListener();
        initConfig();
    }

    private void initFindViewById() {
        mLockButton = findViewById(R.id.lock);
        mLoadingProgress = findViewById(R.id.loading);
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
        CustomErrorView errorView = new CustomErrorView(mContext);
        errorView.setVisibility(GONE);
        this.addControlComponent(errorView);

        //添加与加载视图界面view，准备播放界面
        CustomPrepareView prepareView = new CustomPrepareView(mContext);
        thumb = prepareView.getThumb();
        prepareView.setClickStart();
        this.addControlComponent(prepareView);

        //添加标题栏
        titleView = new AvTitleView(mContext);
        titleView.setVisibility(VISIBLE);
        this.addControlComponent(titleView);

        //添加直播/回放视频底部控制视图
        changePlayType();

        //添加滑动控制视图
        AvGestureView gestureControlView = new AvGestureView(mContext);
        this.addControlComponent(gestureControlView);
    }


    /**
     * 切换直播/回放类型
     */
    public void changePlayType(){
        if (IS_LIVE) {
            //添加底部播放控制条
            if (liveControlView==null){
                liveControlView = new CustomLiveControlView(mContext);
            }
            this.removeControlComponent(liveControlView);
            this.addControlComponent(liveControlView);

            //添加直播还未开始视图
            if (customOncePlayView==null){
                customOncePlayView = new CustomOncePlayView(mContext);
                tvLiveWaitMessage = customOncePlayView.getTvMessage();
            }
            this.removeControlComponent(customOncePlayView);
            this.addControlComponent(customOncePlayView);

            //直播视频，移除回放视图
            if (vodControlView!=null){
                this.removeControlComponent(vodControlView);
            }
        } else {
            //添加底部播放控制条
            if (vodControlView==null){
                vodControlView = new AvStoreBottomView(mContext);
                //是否显示底部进度条。默认显示
                vodControlView.showBottomProgress(true);
            }

            this.removeControlComponent(vodControlView);
            this.addControlComponent(vodControlView);

            //正常视频，移除直播视图
            if (liveControlView!=null){
                this.removeControlComponent(liveControlView);
            }
            if (customOncePlayView!=null){
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
     * @param playerState                   播放模式
     */
    @Override
    protected void onPlayerStateChanged(int playerState) {
        super.onPlayerStateChanged(playerState);
        switch (playerState) {
            case ConstantKeys.PlayMode.MODE_NORMAL:
                setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                FrameLayout.LayoutParams lblp = (FrameLayout.LayoutParams) mLockButton.getLayoutParams();
                lblp.setMargins(dp24, 0, dp24, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mLockButton.getLayoutParams();
                layoutParams.setMargins(dp24 + cutoutHeight, 0, dp24 + cutoutHeight, 0);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mLockButton.getLayoutParams();
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
     * @param playState                     播放状态，主要是指播放器的各种状态
     */
    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        switch (playState) {
            //调用release方法会回到此状态
            case ConstantKeys.CurrentState.STATE_IDLE:
                mLockButton.setSelected(false);
                mLoadingProgress.setVisibility(GONE);
                break;
            case ConstantKeys.CurrentState.STATE_PLAYING:
            case ConstantKeys.CurrentState.STATE_PAUSED:
            case ConstantKeys.CurrentState.STATE_PREPARED:
            case ConstantKeys.CurrentState.STATE_ERROR:
            case ConstantKeys.CurrentState.STATE_COMPLETED:
                mLoadingProgress.setVisibility(GONE);
                break;
            case ConstantKeys.CurrentState.STATE_PREPARING:
            case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
                mLoadingProgress.setVisibility(VISIBLE);
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
                mLoadingProgress.setVisibility(GONE);
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
        if (PlayerUtils.isActivityLiving(activity)){
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
    }

    @Override
    public void destroy() {

    }

    public ImageView getThumb() {
        return thumb;
    }

    public void setTitle(String title) {
        if (titleView!=null){
            titleView.setTitle(title);
        }
    }

    public AvStoreBottomView getBottomView() {
        return vodControlView;
    }


    public TextView getTvLiveWaitMessage() {
        return tvLiveWaitMessage;
    }


    public void setSpeed(String speed){
        if(vodControlView!=null){
            // 转为小写处理
            switch (speed.toLowerCase()) {

                case SpeedInterface.sp0_75:
                    mControlWrapper.setSpeed(0.75f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_0_75));
                    break;
                case SpeedInterface.sp1_0:
                    mControlWrapper.setSpeed(1f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_1_0));
                    break;
                case SpeedInterface.sp1_25:
                    mControlWrapper.setSpeed(1.25f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_1_25));
                    break;
                case SpeedInterface.sp1_50:
                    mControlWrapper.setSpeed(1.5f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_1_5));
                    break;
                case SpeedInterface.sp1_75:
                    mControlWrapper.setSpeed(1.75f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_1_75));
                    break;
                case SpeedInterface.sp2_0:
                    mControlWrapper.setSpeed(2f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_2_0));
                    break;
                case SpeedInterface.sp3_0:
                    mControlWrapper.setSpeed(3f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_3_0));
                    break;
                case SpeedInterface.sp4_0:
                    mControlWrapper.setSpeed(4f);
                    vodControlView.setSpeed(getResources().getString(R.string.av_speed_4_0));
                    break;
                default:
                    break;
            }
        }
    }
    public void setControllerClickListener(ControllerClickListener controllerClickListener) {
        this.controllerClickListener = controllerClickListener;
        if(vodControlView!=null){
            vodControlView.setControllerClickListener(controllerClickListener);
        }
        if(mAvCompleteView!=null){
            mAvCompleteView.setControllerClickListener(controllerClickListener);
        }
    }

}
