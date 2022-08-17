package com.yc.kernel.impl.exo;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.TrafficStats;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer3.DefaultLoadControl;
import com.google.android.exoplayer3.DefaultRenderersFactory;
import com.google.android.exoplayer3.ExoPlaybackException;
import com.google.android.exoplayer3.LoadControl;
import com.google.android.exoplayer3.PlaybackParameters;
import com.google.android.exoplayer3.Player;
import com.google.android.exoplayer3.RenderersFactory;
import com.google.android.exoplayer3.SimpleExoPlayer;
import com.google.android.exoplayer3.analytics.AnalyticsCollector;
import com.google.android.exoplayer3.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer3.source.MediaSource;
import com.google.android.exoplayer3.source.MediaSourceEventListener;
import com.google.android.exoplayer3.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer3.trackselection.MappingTrackSelector;
import com.google.android.exoplayer3.trackselection.TrackSelector;
import com.google.android.exoplayer3.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer3.util.Clock;
import com.google.android.exoplayer3.util.EventLogger;
import com.google.android.exoplayer3.util.Log;
import com.google.android.exoplayer3.video.VideoListener;
import com.yc.kernel.inter.AbstractVideoPlayer;
import com.yc.kernel.inter.VideoPlayerListener;
import com.yc.kernel.utils.PlayerConstant;
import com.yc.kernel.utils.VideoLogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static com.google.android.exoplayer3.ExoPlaybackException.TYPE_SOURCE;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2018/11/9
 *     desc  : exo视频播放器实现类
 *     revise:
 * </pre>
 */
public class ExoMediaPlayer extends AbstractVideoPlayer implements VideoListener, Player.EventListener {

    protected Context mAppContext;
    protected SimpleExoPlayer mInternalPlayer;
    protected MediaSource mMediaSource;
    protected ExoMediaSourceHelper mMediaSourceHelper;
    private PlaybackParameters mSpeedPlaybackParameters;
    private int mLastReportedPlaybackState = Player.STATE_IDLE;
    private boolean mLastReportedPlayWhenReady = false;
    private boolean mIsPreparing;
    private boolean mIsBuffering;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    private TrackSelector mTrackSelector;

    private long mLastTcpSpeedTime;//最后一次获取网速的时间
    private long mLastTcpSpeed;//最后一次获取的网速

    public ExoMediaPlayer(Context context) {
        if (context instanceof Application){
            mAppContext = context;
        } else {
            mAppContext = context.getApplicationContext();
        }
        mMediaSourceHelper = ExoMediaSourceHelper.getInstance(context);
    }

    @Override
    public void initPlayer() {
        mInternalPlayer = new SimpleExoPlayer.Builder(
                mAppContext,
                mRenderersFactory == null ? mRenderersFactory = new DefaultRenderersFactory(mAppContext) : mRenderersFactory,
                mTrackSelector == null ? mTrackSelector = new DefaultTrackSelector(mAppContext) : mTrackSelector,
                new DefaultMediaSourceFactory(mAppContext),
                mLoadControl == null ? mLoadControl = new DefaultLoadControl() : mLoadControl,
                DefaultBandwidthMeter.getSingletonInstance(mAppContext),
                new AnalyticsCollector(Clock.DEFAULT))
                .build();
        setOptions();

        //播放器日志
        if (VideoLogUtils.isIsLog() && mTrackSelector instanceof MappingTrackSelector) {
            mInternalPlayer.addAnalyticsListener(new EventLogger((MappingTrackSelector) mTrackSelector, "ExoPlayer"));
        }
        initListener();
    }

    /**
     * exo视频播放器监听listener
     */
    private void initListener() {
        mInternalPlayer.addListener(this);
        mInternalPlayer.addVideoListener(this);
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        mTrackSelector = trackSelector;
    }

    public void setRenderersFactory(RenderersFactory renderersFactory) {
        mRenderersFactory = renderersFactory;
    }

    public void setLoadControl(LoadControl loadControl) {
        mLoadControl = loadControl;
    }

    /**
     * 设置播放地址
     *
     * @param path    播放地址
     * @param headers 播放地址请求头
     */
    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        // 设置dataSource
        if(path==null || path.length()==0){
            if (mPlayerEventListener!=null){
                mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_URL_NULL, 0);
            }
            return;
        }
        mMediaSource = mMediaSourceHelper.getMediaSource(path, headers);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        //no support
    }

    /**
     * 准备开始播放（异步）
     */
    @Override
    public void prepareAsync() {
        if (mInternalPlayer == null){
            return;
        }
        if (mMediaSource == null){
            return;
        }
        if (mSpeedPlaybackParameters != null) {
            mInternalPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        mIsPreparing = true;
        mIsPreparing = true;
        mInternalPlayer.setMediaSource(mMediaSource);
        mInternalPlayer.prepare();
//        mMediaSource.addEventListener(new Handler(), mMediaSourceEventListener);
//        //准备播放
//        mInternalPlayer.prepare(mMediaSource);
    }

    /**
     * 播放
     */
    @Override
    public void start() {
        if (mInternalPlayer == null){
            return;
        }
        mInternalPlayer.setPlayWhenReady(true);
    }

    /**
     * 暂停
     */
    @Override
    public void pause() {
        if (mInternalPlayer == null){
            return;
        }
        mInternalPlayer.setPlayWhenReady(false);
    }

    /**
     * 停止
     */
    @Override
    public void stop() {
        if (mInternalPlayer == null){
            return;
        }
        mInternalPlayer.stop();
    }

    private MediaSourceEventListener mMediaSourceEventListener = new MediaSourceEventListener() {
//        @Override
//        public void onReadingStarted(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {
//            if (mPlayerEventListener != null && mIsPreparing) {
//                mPlayerEventListener.onPrepared();
//            }
//        }
    };

    /**
     * 重置播放器
     */
    @Override
    public void reset() {
        if (mInternalPlayer != null) {
            mInternalPlayer.stop(true);
            mInternalPlayer.setVideoSurface(null);
            mIsPreparing = false;
            mIsBuffering = false;
            mLastReportedPlaybackState = Player.STATE_IDLE;
            mLastReportedPlayWhenReady = false;
        }
    }

    /**
     * 是否正在播放
     */
    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null){
            return false;
        }
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    /**
     * 调整进度
     */
    @Override
    public void seekTo(long time) {
        if (mInternalPlayer == null){
            return;
        }
        mInternalPlayer.seekTo(time);
    }

    /**
     * 释放播放器
     */
    @Override
    public void release() {

        if (mInternalPlayer != null) {
            mInternalPlayer.removeListener(this);
            mInternalPlayer.removeVideoListener(this);
            mInternalPlayer.release();
            mInternalPlayer = null;
        }

        mIsPreparing = false;
        mIsBuffering = false;
        mLastReportedPlaybackState = Player.STATE_IDLE;
        mLastReportedPlayWhenReady = false;
        mSpeedPlaybackParameters = null;
    }

    /**
     * 获取当前播放的位置
     */
    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null){
            return 0;
        }
        return mInternalPlayer.getCurrentPosition();
    }

    /**
     * 获取视频总时长
     */
    @Override
    public long getDuration() {
        if (mInternalPlayer == null){
            return 0;
        }
        return mInternalPlayer.getDuration();
    }

    /**
     * 获取缓冲百分比
     */
    @Override
    public int getBufferedPercentage() {
        return mInternalPlayer == null ? 0 : mInternalPlayer.getBufferedPercentage();
    }

    /**
     * 设置渲染视频的View,主要用于SurfaceView
     */
    @Override
    public void setSurface(Surface surface) {
        if (surface!=null){
            try {
                if (mInternalPlayer != null) {
                    mInternalPlayer.setVideoSurface(surface);
                }
            } catch (Exception e) {
                mPlayerEventListener.onError(PlayerConstant.ErrorType.TYPE_UNEXPECTED,e.getMessage());
            }
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null){
            setSurface(null);
        } else{
            setSurface(holder.getSurface());
        }
    }

    /**
     * 设置音量
     */
    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mInternalPlayer != null){
            mInternalPlayer.setVolume((leftVolume + rightVolume) / 2);
        }
    }

    /**
     * 设置是否循环播放
     */
    @Override
    public void setLooping(boolean isLooping) {
        if (mInternalPlayer != null){
            mInternalPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        }
    }

    @Override
    public void setOptions() {
        //准备好就开始播放
        mInternalPlayer.setPlayWhenReady(true);
    }

    /**
     * 设置播放速度
     */
    @Override
    public void setSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = playbackParameters;
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    /**
     * 获取播放速度
     */
    @Override
    public float getSpeed() {
        if (mSpeedPlaybackParameters != null) {
            return mSpeedPlaybackParameters.speed;
        }
        return 1f;
    }

    /**
     * 获取当前缓冲的网速
     */
    @Override
    public long getTcpSpeed() {
        // no support
        if (mAppContext == null) {
            return 0;
        }
        long speed = 0;
        try {
            //先使用getUidRxBytes方法获取该进程总接收量，如果没获取到就把当前接收数据总量设置为0，否则就获取接收的总流量并转为kb
            int uid = mAppContext.getApplicationInfo().uid;
            long nowTotalRxBytes = TrafficStats.getUidRxBytes(uid) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() );
            Log.i("ExoMediaPlayer","==nowTotalRxBytes1:"+nowTotalRxBytes);
            if (nowTotalRxBytes <= 0) {
                nowTotalRxBytes = getTotalBytesManual(uid);
                Log.i("ExoMediaPlayer","==nowTotalRxBytes2:"+nowTotalRxBytes);
            }

            //记录当前的时间
            long nowTimeStamp = System.currentTimeMillis();
            //上一次记录的时间-当前记录时间算出两次记录的时间差
            long calculationTime = (nowTimeStamp - mLastTcpSpeedTime);
            //如果时间差不变，直接返回0
            if (calculationTime == 0) {
                return calculationTime;
            }

            //两次的数据接收量的差除以两次数据接收的时间，就计算网速了。这边的时间差是毫秒，咱们需要转换成秒。
            speed = ((nowTotalRxBytes - mLastTcpSpeed) * 1000 / calculationTime);
            //当前时间存到上次时间这个变量，供下次计算用
            mLastTcpSpeedTime = nowTimeStamp;
            //当前总接收量存到上次接收总量这个变量，供下次计算用
            mLastTcpSpeed = nowTotalRxBytes;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return speed;

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (mPlayerEventListener == null){
            return;
        }
        if (mIsPreparing){
            return;
        }
        if (mLastReportedPlayWhenReady != playWhenReady || mLastReportedPlaybackState != playbackState) {
            switch (playbackState) {
                //最开始调用的状态
                case Player.STATE_IDLE:
                    break;
                //开始缓充
                case Player.STATE_BUFFERING:
                    mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                    mIsBuffering = true;
                    break;
                //开始播放
                case Player.STATE_READY:
                    if (mIsBuffering) {
                        mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                        mIsBuffering = false;
                    }
                    break;
                //播放器已经播放完了媒体
                case Player.STATE_ENDED:
                    mPlayerEventListener.onCompletion();
                    break;
                default:
                    break;
            }
            mLastReportedPlaybackState = playbackState;
            mLastReportedPlayWhenReady = playWhenReady;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (mPlayerEventListener != null) {
            int type = error.type;
            if (type == TYPE_SOURCE){
                //错误的链接
                mPlayerEventListener.onError(PlayerConstant.ErrorType.TYPE_SOURCE,error.getMessage());
            } else if (type == ExoPlaybackException.TYPE_RENDERER
                    || type == ExoPlaybackException.TYPE_UNEXPECTED
                    || type == ExoPlaybackException.TYPE_REMOTE){
                mPlayerEventListener.onError(PlayerConstant.ErrorType.TYPE_UNEXPECTED,error.getMessage());
            }
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(width, height);
            if (unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_VIDEO_ROTATION_CHANGED, unappliedRotationDegrees);
            }
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        if (mPlayerEventListener != null && mIsPreparing) {
            mPlayerEventListener.onPrepared();
            mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_VIDEO_RENDERING_START, 0);
            mIsPreparing = false;
        }

    }

    @Override
    public void setPlayerEventListener(VideoPlayerListener playerEventListener) {
        super.setPlayerEventListener(playerEventListener);
    }

    /**
     * 通过uid查询文件夹中的数据
     * @param localUid
     * @return
     */
    private Long getTotalBytesManual(int localUid) {
//        Log.e("BytesManual*****", "localUid:" + localUid);
        File dir = new File("/proc/uid_stat/");
        String[] children = dir.list();
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < children.length; i++) {
            stringBuffer.append(children[i]);
            stringBuffer.append("   ");
        }
//        Log.e("children*****", children.length + "");
//        Log.e("children22*****", stringBuffer.toString());
        if (!Arrays.asList(children).contains(String.valueOf(localUid))) {
            return 0L;
        }
        File uidFileDir = new File("/proc/uid_stat/" + String.valueOf(localUid));
        File uidActualFileReceived = new File(uidFileDir, "tcp_rcv");
        File uidActualFileSent = new File(uidFileDir, "tcp_snd");
        String textReceived = "0";
        String textSent = "0";
        try {
            BufferedReader brReceived = new BufferedReader(new FileReader(uidActualFileReceived));
            BufferedReader brSent = new BufferedReader(new FileReader(uidActualFileSent));
            String receivedLine;
            String sentLine;

            if ((receivedLine = brReceived.readLine()) != null) {
                textReceived = receivedLine;
//                Log.e("receivedLine*****", "receivedLine:" + receivedLine);
            }
            if ((sentLine = brSent.readLine()) != null) {
                textSent = sentLine;
//                Log.e("sentLine*****", "sentLine:" + sentLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
//            Log.e("IOException*****", e.toString());
        }
//        Log.e("BytesManualEnd*****", "localUid:" + localUid);
        return Long.valueOf(textReceived).longValue() + Long.valueOf(textSent).longValue();
    }

}
