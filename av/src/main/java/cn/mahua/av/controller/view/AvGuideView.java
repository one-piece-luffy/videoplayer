package cn.mahua.av.controller.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yc.video.bridge.ControlWrapper;
import com.yc.video.config.ConstantKeys;
import com.yc.video.ui.view.InterControlView;

import cn.mahua.av.R;
import cn.mahua.av.utils.AvSharePreference;

/**
 * <pre>
 *     time  : 2022/06/16
 *     desc  : 手势引导图
 *     revise: 用于普通播放器
 * </pre>
 */
public class AvGuideView extends FrameLayout implements InterControlView,
        View.OnClickListener {

    private Context mContext;
    protected ControlWrapper mControlWrapper;
    private View mRootView;


    public AvGuideView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvGuideView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvGuideView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        setVisibility(GONE);
        View view = LayoutInflater.from(getContext()).inflate(getLayoutId(), this, true);
        initFindViewById(view);
        initListener();
    }

    private void initFindViewById(View view) {
        mRootView = view.findViewById(R.id.av_guide);
    }

    private void initListener() {
        mRootView.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        if (v == mRootView) {
            setVisibility(View.GONE);
        }
    }

    protected int getLayoutId() {
        return R.layout.av_guide;
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

    }


    @Override
    public void onPlayerStateChanged(int playerState) {
        switch (playerState) {
            case ConstantKeys.PlayMode.MODE_NORMAL:
                setVisibility(View.GONE);
                break;
            case ConstantKeys.PlayMode.MODE_FULL_SCREEN:
                if(AvSharePreference.getShowAvGuide(mContext)){
                    setVisibility(View.VISIBLE);
                    AvSharePreference.saveShowAvGuide(mContext,false);
                }
                break;
        }
    }

    @Override
    public void setProgress(int duration, int position) {

    }


    @Override
    public void onLockStateChanged(boolean isLocked) {
        onVisibilityChanged(!isLocked, null);
    }



}
