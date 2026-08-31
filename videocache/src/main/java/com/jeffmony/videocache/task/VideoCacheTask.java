package com.jeffmony.videocache.task;


import androidx.annotation.NonNull;

import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.StorageManager;
import com.jeffmony.videocache.listener.IVideoCacheTaskListener;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.DefaultExecutor;
import com.jeffmony.videocache.utils.StorageUtils;
import com.jeffmony.videocache.utils.VideoProxyThreadUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class VideoCacheTask {

    protected VideoCacheInfo mCacheInfo;
    protected Map<String, String> mHeaders;
    protected IVideoCacheTaskListener mListener;
    protected ThreadPoolExecutor mTaskExecutor;

    protected volatile long mCachedSize;      //当前缓存大小
    protected volatile long mLastCachedSize;  //上一次缓存大小
    protected long mTotalSize;
    protected long mLastInvokeTime;
    protected float mPercent = 0.0f;
    protected float mSpeed = 0.0f;
    protected File mSaveDir;

    protected volatile boolean isStart = false;

    protected boolean hasFinish = false;

    public VideoCacheTask(VideoCacheInfo cacheInfo, Map<String, String> headers) {
        mCacheInfo = cacheInfo;
        mHeaders = headers;
        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mCachedSize = cacheInfo.getCachedSize();
        mTotalSize = cacheInfo.getTotalSize();
        mSaveDir = new File(cacheInfo.getSavePath());
        if (!mSaveDir.exists()) {
            mSaveDir.mkdir();
        }
    }

    public boolean isStart() {
        return isStart;
    }

    public void setTaskListener(@NonNull IVideoCacheTaskListener listener) {
        mListener = listener;
    }

    public abstract void startCacheTask();

    public abstract void pauseCacheTask();

    public abstract void stopCacheTask();

    public abstract void seekToCacheTaskFromClient(float percent);        //来自客户端的seek操作

    public abstract void seekToCacheTaskFromServer(long startPosition);   //来自服务端的seek操作,针对非M3U8视频

    @Deprecated
    public abstract void seekToCacheTaskFromServerByM3u8(int segIndex);         //来自服务端的seek操作,针对M3U8视频

    public abstract void seekToCacheTaskFromServer(int segIndex, long time);  //来自服务端的seek操作,针对M3U8视频

    public abstract void resumeCacheTask();

    protected void notifyOnTaskStart() {
        mListener.onTaskStart();
    }

    protected void notifyOnTaskFailed(Exception e) {
        StorageManager.getInstance().checkCache(mSaveDir.getAbsolutePath());
        mListener.onTaskFailed(e);
    }

    protected void notifyOnTaskCompleted() {
        if (hasFinish) {
            return;
        }
        hasFinish = true;
        StorageManager.getInstance().checkCache(mSaveDir.getAbsolutePath());
        mListener.onTaskCompleted(mTotalSize);
        PlayerProgressListenerManager.getInstance().log("缓存下载完成");
    }

    protected boolean isTaskRunning() {
        return mTaskExecutor != null && !mTaskExecutor.isShutdown();
    }

    protected boolean isTaskShutdown() {
        return mTaskExecutor != null && mTaskExecutor.isShutdown();
    }

    /**
     * 非M3U8视频专用的接口
     * 核心逻辑在NonM3U8CacheTask.java
     * @param startPosition
     * @return
     */
    public boolean isMp4PositionSegExisted(long startPosition) { return false; }

    public boolean isMp4Completed() { return false; }

    /**
     * position之后的数据是否缓存完全
     * @param position
     * @return
     */
    public boolean isMp4CompletedFromPosition(long position) { return false; }

    /**
     * 获取mp4视频最近的缓存点
     * @param position
     * @return
     */
    public long getMp4CachedPosition(long position) { return -1L; }

    protected void setThreadPoolArgument(int corePoolSize, int maxPoolSize) {
        if (isTaskRunning()) {
            mTaskExecutor.setCorePoolSize(corePoolSize);
            mTaskExecutor.setMaximumPoolSize(maxPoolSize);
        }
    }

    /**
     * 获取当前 mTaskExecutor 的任务执行状态
     * @return 状态描述字符串
     */
    public String getExecutorStatus() {
        if (mTaskExecutor == null) {
            return "Executor is NULL (未初始化或已回收)";
        }

        // 正在运行的线程数（近似值）
        int activeCount = mTaskExecutor.getActiveCount();
        // 队列中等待执行的任务数
        int queueSize = mTaskExecutor.getQueue().size();
        // 已完成的任务总数
        long completedCount = mTaskExecutor.getCompletedTaskCount();
        // 总任务数（近似）
        long taskCount = mTaskExecutor.getTaskCount();
        // 是否已关闭
        boolean isShutdown = mTaskExecutor.isShutdown();
        // 是否已完全终止
        boolean isTerminated = mTaskExecutor.isTerminated();

        return String.format(
                "Status [shutdown=%b, terminated=%b, 正在运行的线程数=%d, 队列中等待执行的任务数=%d, 已完成的任务总数=%d,  总任务数≈%d]",
                isShutdown, isTerminated, activeCount, queueSize, completedCount, taskCount
        );
    }

    /**
     * 判断当前缓存任务是否处于空闲状态（无正在执行/排队的下载任务）
     */
    public boolean isIdle() {
        // 线程池未创建或已完全终止
        if (mTaskExecutor == null || mTaskExecutor.isTerminated()) {
            return true;
        }
        // 检查活跃线程和队列
        int active = mTaskExecutor.getActiveCount();
        int queueSize = mTaskExecutor.getQueue().size();
        return (active == 0 && queueSize == 0);
    }

    protected void saveVideoInfo() {
        StorageUtils.saveVideoCacheInfo(mCacheInfo, mSaveDir);
    }
}
