package com.jeffmony.videocache;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jeffmony.videocache.common.ProxyMessage;
import com.jeffmony.videocache.common.SourceCreator;
import com.jeffmony.videocache.common.VideoCacheConfig;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.common.VideoRequest;
import com.jeffmony.videocache.common.VideoType;
import com.jeffmony.videocache.listener.IVideoCacheListener;
import com.jeffmony.videocache.listener.IVideoCacheTaskListener;
import com.jeffmony.videocache.listener.IVideoInfoParsedListener;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.okhttp.IHttpPipelineListener;
import com.jeffmony.videocache.okhttp.NetworkConfig;
import com.jeffmony.videocache.okhttp.OkHttpManager;
import com.jeffmony.videocache.proxy.LocalProxyVideoServer;
import com.jeffmony.videocache.task.VideoCacheTask;
import com.jeffmony.videocache.utils.DefaultExecutor;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.StorageUtils;
import com.jeffmony.videocache.utils.VideoProxyThreadUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author jeffmony
 *
 * 本地代理的综合管理类
 */

public class VideoProxyCacheManager {

    private static final String TAG = "VideoProxyCacheManager";

    private static volatile VideoProxyCacheManager sInstance = null;
    private final Handler mthreadHandler; //用于内部通信
    private final Handler mMainHandler; //用于回调用户监听接口

    private final Map<String, VideoCacheTask> mCacheTaskMap = new ConcurrentHashMap<>();
    private final Map<String, VideoCacheInfo> mCacheInfoMap = new ConcurrentHashMap<>();
    private final Map<String, IVideoCacheListener> mCacheListenerMap = new ConcurrentHashMap<>();
    private final Map<String, Long> mVideoSeekMd5PositionMap = new ConcurrentHashMap<>();      //发生seek的时候加入set, 如果可以播放了, remove掉
    private final Object mSeekPositionLock = new Object();

    private final Set<String> mM3U8LocalProxyMd5Set = new ConcurrentSkipListSet<>();
    private final Set<String> mM3U8LiveMd5Set = new ConcurrentSkipListSet<>();

    private String mPlayingUrlMd5;   //设置当前正在播放的视频url的MD5值

    private final IVideoInfoParsedListener mListener;


    private String mCurl;

    public static VideoProxyCacheManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProxyCacheManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProxyCacheManager();
                }
            }
        }
        return sInstance;
    }

    private VideoProxyCacheManager() {
        HandlerThread handlerThread = new HandlerThread("proxy_cache_thread");
        handlerThread.start();

        mthreadHandler = new Handler(handlerThread.getLooper());
        mMainHandler = new ProxyMessageHandler(Looper.getMainLooper());
        mListener = new IVideoInfoParsedListener() {
            @Override
            public void onM3U8ParsedFinished(VideoRequest videoRequest, M3U8 m3u8, VideoCacheInfo cacheInfo) {
                try {
                    //                Log.e(TAG,"onM3U8ParsedFinished:开始下载");
                    //下载任务准备好才通知可以回复
                    String md5 = cacheInfo.getMd5();
                    mM3U8LocalProxyMd5Set.add(md5);
                    //开始发起请求M3U8视频中的ts数据
                    startM3U8Task(m3u8, cacheInfo, videoRequest.getHeaders());

                    notifyLocalProxyLock(VideoLockManager.getInstance().getLock(md5));
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }


            }

            @Override
            public void onM3U8ParsedFailed(VideoCacheException e, VideoCacheInfo cacheInfo) {
                PlayerProgressListenerManager.getInstance().log(e.getMessage());
                notifyLocalProxyLock(VideoLockManager.getInstance().getLock(cacheInfo.getMd5()));
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_ERROR, new VideoResult(cacheInfo, e.getMessage())).sendToTarget();
            }

            @Override
            public void onM3U8LiveCallback(VideoCacheInfo cacheInfo) {
                mM3U8LiveMd5Set.add(cacheInfo.getMd5());
                notifyLocalProxyLock(VideoLockManager.getInstance().getLock(cacheInfo.getMd5()));
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_FORBIDDEN, new VideoResult(cacheInfo)).sendToTarget();
            }

            @Override
            public void onNonM3U8ParsedFinished(VideoRequest videoRequest, VideoCacheInfo cacheInfo) {
                notifyLocalProxyLock(VideoLockManager.getInstance().getLock(cacheInfo.getMd5()));
                //开始发起请求视频数据
                startNonM3U8Task(cacheInfo, videoRequest.getHeaders());
            }

            @Override
            public void onNonM3U8ParsedFailed(VideoCacheException e, VideoCacheInfo cacheInfo) {
                notifyLocalProxyLock(VideoLockManager.getInstance().getLock(cacheInfo.getMd5()));
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_ERROR, new VideoResult(cacheInfo, e.getMessage())).sendToTarget();
            }
        };
    }

    private class ProxyMessageHandler extends Handler {

        public ProxyMessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            VideoResult videoResult = (VideoResult) msg.obj;
            VideoCacheInfo cacheInfo = videoResult.videoCacheInfo;
            IVideoCacheListener cacheListener = mCacheListenerMap.get(cacheInfo.getVideoUrl());
            if (cacheListener != null) {
                switch (msg.what) {
                    case ProxyMessage.MSG_VIDEO_PROXY_ERROR:
                        cacheListener.onCacheError(cacheInfo, videoResult.msg, 0);
                        PlayerProgressListenerManager.getInstance().parseM3u8Fail(videoResult.msg);
                        break;
                    case ProxyMessage.MSG_VIDEO_PROXY_FORBIDDEN:
                        cacheListener.onCacheForbidden(cacheInfo);
                        break;
                    case ProxyMessage.MSG_VIDEO_PROXY_START:
                        cacheListener.onCacheStart(cacheInfo);
                        break;
                    case ProxyMessage.MSG_VIDEO_PROXY_PROGRESS:
                        cacheListener.onCacheProgress(cacheInfo);
                        break;
                    case ProxyMessage.MSG_VIDEO_PROXY_COMPLETED:
                        cacheListener.onCacheFinished(cacheInfo);
                        break;

                    default:
                        break;
                }
            }
        }

    }

    /**
     * 构建代理缓存的属性
     */
    public static class Builder {

        private long mExpireTime = 7 * 24 * 60 * 60 * 1000;
        private long mMaxCacheSize = 2L * 1024 * 1024 * 1024;
        private String mFilePath;
        private int mReadTimeOut = 30 * 1000;
        private int mConnTimeOut = 30 * 1000;
        private boolean mIgnoreCert;
        private int mPort;
        private boolean mUseOkHttp;
        private SourceCreator mSourceCreator;

        private final Context mContext;

        public Builder(Context context) {
            this.mContext = context instanceof Application ? context : context.getApplicationContext();
        }

        public Builder setExpireTime(long expireTime) {
            mExpireTime = expireTime;
            return this;
        }

        public Builder setMaxCacheSize(long maxCacheSize) {
            mMaxCacheSize = maxCacheSize;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }

        public Builder setReadTimeOut(int readTimeOut) {
            mReadTimeOut = readTimeOut;
            return this;
        }

        public Builder setConnTimeOut(int connTimeOut) {
            mConnTimeOut = connTimeOut;
            return this;
        }

        public Builder setIgnoreCert(boolean ignoreCert) {
            mIgnoreCert = ignoreCert;
            return this;
        }

        //需要自定义端口号的可以调用这个函数
        public Builder setPort(int port) {
            mPort = port;
            return this;
        }

        public Builder setUseOkHttp(boolean useOkHttp) {
            mUseOkHttp = useOkHttp;
            return this;
        }

        public Builder setSourceCreator(SourceCreator mSourceCreator) {
            this.mSourceCreator = mSourceCreator;
            return this;
        }

        public VideoCacheConfig build() {
            return new VideoCacheConfig(mContext, mExpireTime, mMaxCacheSize, mFilePath, mReadTimeOut, mConnTimeOut, mIgnoreCert, mPort, mUseOkHttp, mSourceCreator);
        }
    }

    //网络性能数据回调
    private final IHttpPipelineListener mHttpPipelineListener = new IHttpPipelineListener() {
        @Override
        public void onRequestStart(String url, String rangeHeader) {

        }

        @Override
        public void onDnsStart(String url, long timeDuration) {

        }

        @Override
        public void onDnsEnd(String url, long timeDuration) {

        }

        @Override
        public void onConnectStart(String url, long timeDuration) {

        }

        @Override
        public void onConnectEnd(String url, long timeDuration) {

        }

        @Override
        public void onConnectFailed(String url, long timeDuration, Exception e) {

        }

        @Override
        public void onConnectAcquired(String url, long timeDuration) {

        }

        @Override
        public void onConnectRelease(String url, long timeDuration) {

        }

        @Override
        public void onRequestHeaderStart(String url, long timeDuration) {

        }

        @Override
        public void onRequestHeaderEnd(String url, long timeDuration) {

        }

        @Override
        public void onRequestBodyStart(String url, long timeDuration) {

        }

        @Override
        public void onRequestBodyEnd(String url, long timeDuration) {

        }

        @Override
        public void onResponseHeaderStart(String url, long timeDuration) {

        }

        @Override
        public void onResponseHeaderEnd(String url, long timeDuration) {

        }

        @Override
        public void onResponseBodyStart(String url, long timeDuration) {

        }

        @Override
        public void onResponseBodyEnd(String url, long timeDuration) {

        }

        @Override
        public void onResponseEnd(String url, long timeDuration) {

        }

        @Override
        public void onFailed(String url, long timeDuration, Exception e) {

        }
    };

    public void initProxyConfig(@NonNull VideoCacheConfig config) {
        ProxyCacheUtils.setVideoCacheConfig(config);
        new LocalProxyVideoServer();  //初始化本地代理服务

        NetworkConfig networkConfig = new NetworkConfig(config.getReadTimeOut(), config.getConnTimeOut(), config.ignoreCert());
        OkHttpManager.getInstance().initConfig(networkConfig, mHttpPipelineListener);

        //设置缓存清理规则
        StorageManager.getInstance().initCacheConfig(config.getFilePath(), config.getMaxCacheSize(), config.getExpireTime());
    }

    public void addCacheListener(String videoUrl, @NonNull IVideoCacheListener listener) {
        if (TextUtils.isEmpty(videoUrl)) {
            return;
        }
        mCacheListenerMap.put(videoUrl, listener);
    }

    public void removeCacheListener(String videoUrl) {
        mCacheListenerMap.remove(videoUrl);
    }

    public void releaseProxyReleases(String videoUrl) {
        removeCacheListener(videoUrl);
        String md5 = ProxyCacheUtils.computeMD5(videoUrl);
        releaseProxyCacheSet(md5);
        removeVideoSeekInfo(md5);
    }

    public void releaseAll() {
        for (String key : mCacheTaskMap.keySet()) {
            VideoCacheTask cacheTask = mCacheTaskMap.get(key);
            if (cacheTask != null) {
                cacheTask.stopCacheTask();

            }
        }
        mCacheTaskMap.clear();
        mCacheListenerMap.clear();
        mM3U8LiveMd5Set.clear();
        mM3U8LocalProxyMd5Set.clear();
        mVideoSeekMd5PositionMap.clear();
    }

    /**
     *
     * @param videoUrl  视频url
     */
    public void startRequestVideoInfo(String videoUrl) {
        startRequestVideoInfo(videoUrl, new HashMap<>());
    }

    /**
     *
     * @param videoUrl 视频url
     * @param headers  请求的头部信息
     */
    public void startRequestVideoInfo(String videoUrl, Map<String, String> headers) {
        startRequestVideoInfo(videoUrl, headers, new HashMap<>());
    }

    /**
     *
     * @param videoUrl    视频url
     * @param headers     请求的头部信息
     * @param extraParams 额外参数，这个map很有用，例如我已经知道当前请求视频的类型和长度，都可以在extraParams中设置,
     *                    详情见VideoParams
     */
    public void startRequestVideoInfo(final String videoUrl, Map<String, String> headers, Map<String, Object> extraParams) {
        mCurl = videoUrl;
        VideoRequest videoRequest = new VideoRequest.Builder(videoUrl).
                headers(headers).
                extraParams(extraParams).
                videoInfoParsedListener(mListener).build();
        try {
            DefaultExecutor.execute(() -> {

                StorageManager.getInstance().initCacheInfo();
                String md5 = ProxyCacheUtils.computeMD5(videoRequest.getVideoUrl());
                File saveDir = new File(ProxyCacheUtils.getConfig().getFilePath(), md5);
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }
                VideoCacheInfo videoCacheInfo = StorageUtils.readVideoCacheInfo(saveDir);
                PlayerProgressListenerManager.getInstance().log("startRequestVideoInfo: " + videoCacheInfo);
                // 处理videoinfo 还是判断缓存文件存不存在
                if (videoCacheInfo == null) {
                    //之前没有缓存信息
                    videoCacheInfo = new VideoCacheInfo(videoUrl);
                    videoCacheInfo.setMd5(md5);
                    videoCacheInfo.setSavePath(saveDir.getAbsolutePath());
                    VideoInfoParseManager.getInstance().parseVideoInfoByOkHttp(videoRequest, videoCacheInfo);
                } else {
                    if (videoCacheInfo.getVideoType() == VideoType.M3U8_TYPE) {
                        //说明视频类型是M3U8类型
                        VideoInfoParseManager.getInstance().parseProxyM3U8Info(videoRequest, videoCacheInfo);
                    } else if (videoCacheInfo.getVideoType() == VideoType.M3U8_LIVE_TYPE) {
                        //说明是直播
                        mM3U8LiveMd5Set.add(md5);
                        mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_FORBIDDEN, new VideoResult(videoCacheInfo)).sendToTarget();
                        PlayerProgressListenerManager.getInstance().log("这是直播类型");
                    } else {
                        startNonM3U8Task(videoCacheInfo, headers);
                    }
                }
            });
        }catch (Exception e){
            Log.e("asdf","异常",e);
        }

    }

    /**
     * 开始缓存M3U8任务
     * @param m3u8
     * @param cacheInfo
     * @param headers
     */
    private void startM3U8Task(M3U8 m3u8, VideoCacheInfo cacheInfo, Map<String, String> headers) {
        //todo 只保留一个请求
        if (!cacheInfo.getVideoUrl().equals(mCurl)) {
            Log.i(TAG,"不是当前请求:"+ProxyCacheUtils.decodeUriWithBase64(headers.get(CacheConstants.HEADER_KEY_NAME))+" "+cacheInfo.getVideoUrl());
            return;
        }
        VideoCacheTask cacheTask = mCacheTaskMap.get(cacheInfo.getVideoUrl());
        if (cacheTask != null) {
            cacheTask.stopCacheTask();
        }
        cacheTask = ProxyCacheUtils.getConfig().getSourceCreator().createM3U8CacheTask(cacheInfo, headers, m3u8, mthreadHandler);
        mCacheTaskMap.put(cacheInfo.getVideoUrl(), cacheTask);
        startVideoCacheTask(cacheTask, cacheInfo);
    }

    /**
     * 开始缓存非M3U8任务
     */
    private void startNonM3U8Task(VideoCacheInfo cacheInfo, Map<String, String> headers) {

        VideoCacheTask cacheTask = mCacheTaskMap.get(cacheInfo.getVideoUrl());
        if (cacheTask != null) {
            cacheTask.stopCacheTask();
        }
        cacheTask = ProxyCacheUtils.getConfig().getSourceCreator().createMp4CacheTask(cacheInfo, headers);
        mCacheTaskMap.put(cacheInfo.getVideoUrl(), cacheTask);
        startVideoCacheTask(cacheTask, cacheInfo);
    }

    private void startVideoCacheTask(VideoCacheTask cacheTask, VideoCacheInfo cacheInfo) {
        final Object lock = VideoLockManager.getInstance().getLock(cacheInfo.getMd5());
        cacheTask.setTaskListener(new IVideoCacheTaskListener() {
            @Override
            public void onTaskStart() {
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_START, new VideoResult(cacheInfo)).sendToTarget();
            }

            @Override
            public void onTaskProgress(float percent, long cachedSize, float speed) {
                if (shouldNotifyLock(cacheInfo.getVideoType(), cacheInfo.getVideoUrl(), cacheInfo.getMd5())) {
                    notifyLocalProxyLock(lock);
                }
                cacheInfo.setPercent(percent);
                cacheInfo.setCachedSize(cachedSize);
                cacheInfo.setSpeed(speed);
                mCacheInfoMap.put(cacheInfo.getVideoUrl(), cacheInfo);
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_PROGRESS, new VideoResult(cacheInfo)).sendToTarget();
            }

            @Override
            public void onM3U8TaskProgress(float percent, long cachedSize, float speed) {
                notifyLocalProxyLock(lock);
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_PROGRESS, new VideoResult(cacheInfo)).sendToTarget();
            }

            @Override
            public void onTaskFailed(Exception e) {
                notifyLocalProxyLock(lock);
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_ERROR, new VideoResult(cacheInfo, e.getMessage())).sendToTarget();
            }

            @Override
            public void onVideoSeekComplete() {
                notifyLocalProxyLock(lock);
            }

            @Override
            public void onTaskCompleted(long totalSize) {
                if (shouldNotifyLock(cacheInfo.getVideoType(), cacheInfo.getVideoUrl(), cacheInfo.getMd5())) {
                    LogUtils.i(TAG, "onTaskCompleted ----, totalSize="+totalSize);
                    notifyLocalProxyLock(lock);
                }
                cacheInfo.setTotalSize(totalSize);
                mCacheInfoMap.put(cacheInfo.getVideoUrl(), cacheInfo);
                mMainHandler.obtainMessage(ProxyMessage.MSG_VIDEO_PROXY_COMPLETED, new VideoResult(cacheInfo)).sendToTarget();
            }
        });

        cacheTask.startCacheTask();
    }

    /**
     * 暂停缓存任务, 一般是主线程操作
     * @param url
     */
    public void pauseCacheTask(String url) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            cacheTask.pauseCacheTask();
        }
    }

    public void stopCacheTask(String url) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            cacheTask.stopCacheTask();
            mCacheTaskMap.remove(url);
        }
    }

    /**
     * 恢复缓存任务,一般是主线程操作
     * @param url
     */
    public void resumeCacheTask(String url) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            cacheTask.resumeCacheTask();
        }
    }

    /**
     * 拖动播放进度条之后的操作
     * 纯粹客户端的操作, 一般是主线程操作
     * @param url
     * @param percent
     */
    public void seekToCacheTaskFromClient(String url, float percent) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            //当前seek到什么position在客户端不知道
            addVideoSeekInfo(url);
            cacheTask.seekToCacheTaskFromClient(percent);
        }
    }

    private void addVideoSeekInfo(String url) {
        String md5 = ProxyCacheUtils.computeMD5(url);
        synchronized (mSeekPositionLock) {
            LogUtils.i(TAG, "addVideoSeekInfo md5=" + md5 + ", url=" + url);
            mVideoSeekMd5PositionMap.put(md5, -1L);
        }
    }

    private boolean shouldNotifyLock(int videoType, String url, String md5) {
        synchronized (mSeekPositionLock) {
            //只有非M3U8视频才能进入这个逻辑
            if (videoType == VideoType.OTHER_TYPE && mVideoSeekMd5PositionMap.containsKey(md5)) {
                long position = mVideoSeekMd5PositionMap.get(md5).longValue();
                LogUtils.i(TAG, "shouldNotifyLock position=" + position + ", url=" + url);
                if (position > 0) {
                    boolean isMp4PositionSegExisted = isMp4PositionSegExisted(url, position);
                    LogUtils.i(TAG, "shouldNotifyLock position=" + position + ", isMp4PositionSegExisted=" + isMp4PositionSegExisted);
                    if (isMp4PositionSegExisted) {
                        mVideoSeekMd5PositionMap.remove(md5);
                        return true;
                    } else {
                        //说明发生了seek, 但是seek请求并没有结束
                        return false;
                    }
                } else {
                    if (isMp4Completed(url)) {
                        mVideoSeekMd5PositionMap.remove(md5);
                        return true;
                    }
                    //说明次数有seek操作,但是seek操作还没有从local server端发送过来
                    return false;
                }
            }
            return true;
        }
    }

    private void removeVideoSeekInfo(String md5) {
        synchronized (mSeekPositionLock) {
            if (mVideoSeekMd5PositionMap.containsKey(md5)) {
                LogUtils.i(TAG, "removeVideoSeekSet = " + md5);
                mVideoSeekMd5PositionMap.remove(md5);
            }
        }
    }

    /**
     * 服务端调用到客户端的通知, 这儿可以精确确定客户端应该从什么地方开始seek
     *
     * 从服务端调用过来, 肯定不是主线程, 所以要切换到主线程
     *
     * 这是针对非M3U8视频的
     * @param url
     * @param startPosition
     */
    public void seekToCacheTaskFromServer(String url, long startPosition) {
        String md5 = ProxyCacheUtils.computeMD5(url);
        boolean shouldSeek = false;
        synchronized (mSeekPositionLock) {
            long oldPosition = mVideoSeekMd5PositionMap.containsKey(md5) ? mVideoSeekMd5PositionMap.get(md5) : 0L;
            //说明这是一个新的seek操作, oldPosition =0L, 说明此时没有发生seek操作
            if (oldPosition == -1L) {
                LogUtils.i(TAG, "setVideoRangeRequest startPosition=" + startPosition);
                mVideoSeekMd5PositionMap.put(md5, startPosition);
                shouldSeek = true;
            }
        }

        final boolean seekByServer = shouldSeek;
        VideoProxyThreadUtils.runOnUiThread(() -> {
            VideoCacheTask cacheTask = mCacheTaskMap.get(url);
            if (cacheTask != null && seekByServer) {
                cacheTask.seekToCacheTaskFromServer(startPosition);
            }
        });
    }

    /**
     * 针对M3U8视频,从服务端传入分片索引到客户端来
     * @param url
     * @param segIndex
     */
    @Deprecated
    public void seekToCacheTaskFromServer(String url, int segIndex) {
        String md5 = ProxyCacheUtils.computeMD5(url);
        boolean shouldSeek = false;
        synchronized (mSeekPositionLock) {
            if (mVideoSeekMd5PositionMap.containsKey(md5)) {
                mVideoSeekMd5PositionMap.remove(md5);
                shouldSeek = true;
            }
        }
        final boolean seekByServer = shouldSeek;
        VideoProxyThreadUtils.runOnUiThread(() -> {
            VideoCacheTask cacheTask = mCacheTaskMap.get(url);
            if (cacheTask != null && seekByServer) {
                cacheTask.seekToCacheTaskFromServer(segIndex);
            }
        });
    }

    /**
     * 针对M3U8视频,从服务端传入分片索引到客户端来
     * 确保知道目前最新的ts视频请求，以便调整下载优先级
     * @param m3u8Url
     * @param segIndex
     * @param time
     */
    public void notifyCurSegIndex(String m3u8Url, int segIndex, long time) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(m3u8Url);
        if (cacheTask != null) {
            cacheTask.seekToCacheTaskFromServer(segIndex, time);
        }
    }

    /**
     * 当前MP4视频是否已经缓存到了startPosition位置
     * @param url
     * @param startPosition
     * @return
     */
    public boolean isMp4PositionSegExisted(String url, long startPosition) {
        if (startPosition == -1L) {
            //说明也没有seek 操作
            return true;
        }
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            return cacheTask.isMp4PositionSegExisted(startPosition);
        }
        return true;
    }

    /**
     * 当前MP4文件是否下载完全
     * @param url
     * @return
     */
    public boolean isMp4Completed(String url) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            return cacheTask.isMp4Completed();
        }
        return false;
    }

    /**
     * 从position开始,之后的数据都缓存完全了.
     * @param url
     * @param position
     * @return
     */
    public boolean isMp4CompletedFromPosition(String url, long position) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            return cacheTask.isMp4CompletedFromPosition(position);
        }
        return false;
    }

    /**
     * 当前position数据是否可以write到socket中
     * @param url
     * @param position
     * @return
     */
    public boolean shouldWriteResponseData(String url, long position) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            return cacheTask.isMp4PositionSegExisted(position);
        }
        return false;
    }

    public long getMp4CachedPosition(String url, long position) {
        VideoCacheTask cacheTask = mCacheTaskMap.get(url);
        if (cacheTask != null) {
            return cacheTask.getMp4CachedPosition(position);
        }
        return 0L;
    }

    private void notifyLocalProxyLock(Object lock) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /**
     * 当前proxy m3u8是否生成
     * @param md5
     * @return
     */
    public boolean isM3U8LocalProxyReady(String md5) {
        return mM3U8LocalProxyMd5Set.contains(md5);
    }

    /**
     * 是否是直播类型
     * @param md5
     * @return
     */
    public boolean isM3U8LiveType(String md5) {
        return mM3U8LiveMd5Set.contains(md5);
    }

    public void releaseProxyCacheSet(String md5) {
        mM3U8LiveMd5Set.remove(md5);
        mM3U8LocalProxyMd5Set.remove(md5);
    }

    public void setPlayingUrlMd5(String md5) {
        mPlayingUrlMd5 = md5;
    }

    public String getPlayingUrlMd5() {
        return mPlayingUrlMd5;
    }

    public boolean isM3U8SegCompleted(String m3u8Md5, int tsIndex, String filePath) {
        if (TextUtils.isEmpty(m3u8Md5) || TextUtils.isEmpty(filePath)) {
            return false;
        }
        File segFile = new File(filePath);
        if (!segFile.exists() || segFile.length() == 0) {
            return false;
        }
        for(Map.Entry<String, VideoCacheInfo> entry : mCacheInfoMap.entrySet()) {
            String url = String.valueOf(entry.getKey());
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            VideoCacheInfo cacheInfo = mCacheInfoMap.get(url);
            if (cacheInfo != null && TextUtils.equals(cacheInfo.getMd5(), m3u8Md5)) {
                Map<Integer, Long> tsLengthMap = cacheInfo.getTsLengthMap();
                if (tsLengthMap != null) {
                    long tsLength = tsLengthMap.get(tsIndex) != null ? tsLengthMap.get(tsIndex) : 0;
                    return segFile.length() == tsLength;
                }
            }
        }
        return false;
    }

    public long getTotalSize(String md5) {
        if (TextUtils.isEmpty(md5)) {
            return -1L;
        }
        for(Map.Entry entry : mCacheInfoMap.entrySet()) {
            String url = String.valueOf(entry.getKey());
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            VideoCacheInfo cacheInfo = mCacheInfoMap.get(url);
            if (cacheInfo != null && TextUtils.equals(cacheInfo.getMd5(), md5)) {
                return cacheInfo.getTotalSize();
            }
        }
        return -1L;
    }

    private static class VideoResult {
        public VideoCacheInfo videoCacheInfo;
        public String msg = "";

        public VideoResult(VideoCacheInfo videoCacheInfo) {
            this.videoCacheInfo = videoCacheInfo;
        }

        public VideoResult(VideoCacheInfo videoCacheInfo, String msg) {
            this.videoCacheInfo = videoCacheInfo;
            this.msg = msg;
        }
    }
}
