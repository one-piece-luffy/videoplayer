package com.jeffmony.videocache.task;

import android.os.Looper;
import android.util.Log;

import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.m3u8.M3U8Seg;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.AES128Utils;
import com.jeffmony.videocache.utils.DefaultExecutor;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.TsDownloadManager;
import com.jeffmony.videocache.utils.TsMetaDataManager;
import com.jeffmony.videocache.utils.VideoCacheUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Response;

/**
 * M3U8缓存任务 - 边下边播
 * ========== 优化点说明 ==========
 * 1. 【线程池管理 + 非阻塞等待】 - 解决ANR和内存泄漏
 *    - shutdownExecutorSafely() 使用 awaitTermination() 非阻塞等待
 *    - 循环检查取消状态，不阻塞主线程
 *    - 位置: shutdownExecutorSafely() + startRequestVideoRange()
 * 2. 【循环重试替代递归】 - 解决栈溢出风险
 *    - downloadFile() 中使用 while 循环重试
 *    - 不再递归调用 downloadFile()
 *    - 位置: downloadFile() 方法中的 while 循环
 * 3. 【支持中断请求】 - 解决资源浪费
 *    - 使用 TsDownloadManager 管理 OkHttp Call
 *    - 每下载 1MB 检查一次取消状态
 *    - 位置: TsDownloadManager + downloadToFile() 中的检查
 * 4. 【文件验证效率优化】 - 解决性能下降
 *    - 使用 ConcurrentHashMap 缓存已下载文件信息
 *    - 进度更新增量计算，不遍历全部文件
 *    - 位置: mSegSizeCache + notifyCacheProgress()
 * 5. 【磁盘空间检查】 - 解决下载失败
 *    - 下载前检查可用空间，低于阈值拒绝下载
 *    - 位置: checkDiskSpace()
 * 6. 【代码结构优化】 - 提高可维护性
 *    - 抽取常量配置
 *    - 清晰的方法职责划分
 *    - 位置: 顶部常量定义
 */
public class M3U8CacheTask extends VideoCacheTask {

    private static final String TAG = "M3U8CacheTask";

    // ==================== 常量配置 ====================

    /** 临时文件后缀 */
    private static final String TEMP_POSTFIX = ".task_downloading";

    /** 线程池核心线程数 */
    private static final int THREAD_POOL_COUNT = 5;

    /** 最大重试次数（非503） */
    private static final int MAX_RETRY_COUNT = 2;

    /** 503错误最大重试次数 */
    private static final int MAX_503_RETRY_COUNT = 2;

    /** 线程池关闭等待超时（秒） */
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 2;

    /** 磁盘空间预留大小（MB） */
    private static final long DISK_SPACE_RESERVE_MB = 300;

    /** 下载中断检查间隔（字节） - 每1MB检查一次 */
    private static final long CHECK_INTERVAL_BYTES = 1024 * 1024;

    // ==================== 状态控制变量 ====================

    /** 任务是否正在运行 */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** 任务是否正在暂停中 */
    private final AtomicBoolean isPausing = new AtomicBoolean(false);

    // ==================== 业务变量 ====================

    /** 已缓存的TS分片数量（子类特有，父类没有） */
    private int mCachedSegCount;

    /** 总TS分片数量 */
    private int mTotalSegCount;

    /** TS分片列表 */
    private List<M3U8Seg> mSegList;

    /** M3U8对象 */
    private M3U8 mM3U8;

    /** TS元数据管理器（用于快速校验文件完整性） */
    private TsMetaDataManager mMetaDataManager;

    /** TS下载管理器（支持中断） */
    private TsDownloadManager mDownloadManager;

    // ==================== 缓存优化 ====================
    boolean printLog=true;
    /**
     * TS文件大小缓存
     * Key: 文件名, Value: 文件大小
     * 用于快速判断文件是否已下载，避免重复IO
     */
    private final ConcurrentHashMap<String, Long> mSegSizeCache = new ConcurrentHashMap<>();

    // ==================== 构造函数 ====================

    public M3U8CacheTask(VideoCacheInfo cacheInfo, Map<String, String> headers, M3U8 m3u8) {
        super(cacheInfo, headers);
        mSegList = m3u8.getSegList();
        this.mM3U8 = m3u8;
        mTotalSegCount = mSegList == null ? 0 : mSegList.size();
        mCachedSegCount = cacheInfo.getCachedTs();
        mHeaders.put("Connection", "close");
        mMetaDataManager = TsMetaDataManager.getInstance(mSaveDir);
        mDownloadManager = new TsDownloadManager();
    }

    // ==================== 父类方法实现 ====================

    /**
     * 开始缓存任务
     */
    @Override
    public void startCacheTask() {
        // 防止重复启动
        if (isTaskRunning()) {
            return;
        }
        if (isRunning.get()) {
            return;
        }

        // 重置状态
        isRunning.set(true);
        isPausing.set(false);
        mDownloadManager.resetGlobalCancel();
        isStart = true;

        // 【优化5】检查磁盘空间
        if (!checkDiskSpace()) {
            notifyOnTaskFailed(new IOException("Insufficient disk space"));
            return;
        }

        notifyOnTaskStart();
        initM3U8TsInfo();

        int seekIndex = calculateStartIndex();
        startRequestVideoRange(seekIndex);
    }

    /**
     * 初始化M3U8 TS文件信息
     * 扫描本地已下载文件，更新缓存状态
     */
    private void initM3U8TsInfo() {
        long tempCachedSize = 0;
        int tempCachedTs = 0;
        mSegSizeCache.clear();

        // ✅ 一次性从元数据管理器加载所有大小信息
        for (int index = 0; index < mSegList.size(); index++) {
            M3U8Seg ts = mSegList.get(index);
            File tempTsFile = new File(mSaveDir, ts.getSegName());

            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                long expectedSize = mMetaDataManager.getTsMetaData(ts.getSegName());

                if (expectedSize > 0) {
                    if (VideoCacheUtils.sizeSimilar(tempTsFile.length(), expectedSize)) {
                        ts.setFileSize(tempTsFile.length());
                        tempCachedSize += tempTsFile.length();
                        tempCachedTs++;
                        // 【优化4】缓存已下载文件信息
                        mSegSizeCache.put(ts.getSegName(), tempTsFile.length());
                    } else {
                        // 文件不完整，删除
                        LogUtils.w(TAG, "Found incomplete file, deleting: " + tempTsFile.getName());
                        FileUtils.deleteFile(tempTsFile);
                        mMetaDataManager.removeTsMetaData(ts.getSegName());
                    }
                } else {
                    LogUtils.w(TAG, "Invalid TS file, deleting: " + tempTsFile.getName());
                    FileUtils.deleteFile(tempTsFile);
                }
            }
        }

        mCachedSegCount = tempCachedTs;
        mCachedSize = tempCachedSize;

        if (mCachedSegCount == mTotalSegCount) {
            mCacheInfo.setIsCompleted(true);
        }
    }


    /**
     * 暂停缓存任务
     * 【优化3】支持中断：取消所有正在下载的请求
     */
    @Override
    public void pauseCacheTask() {
        Log.e(TAG, "====pauseCacheTask:" + isMainThread());
        isRunning.set(false);
        isPausing.set(true);

        // 取消所有正在下载的请求（OkHttp Call.cancel()）
        cancelAllDownloads("Task paused");
        PlayerProgressListenerManager.getInstance().log("pauseCacheTask");
        // 【优化1】安全关闭线程池（非阻塞）
        // 2. 【关键优化】等待线程池完全关闭，再继续
        try {
            Future<?> shutdownFuture = shutdownExecutorSafely();
            if (shutdownFuture != null) {
                // 阻塞等待最多 3 秒，确保旧线程池被完全销毁
                shutdownFuture.get(3, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Wait for shutdown failed", e);
            PlayerProgressListenerManager.getInstance().log("Wait for shutdown failed:"+Log.getStackTraceString(e));
        }
    }

    /**
     * 停止缓存任务
     * 【优化3】支持中断：取消所有正在下载的请求
     */
    @Override
    public void stopCacheTask() {
        Log.e(TAG, "=====stopCacheTask:" + isMainThread());
        isRunning.set(false);
        isPausing.set(true);
        isStart = false;

        // 取消所有下载
        cancelAllDownloads("Task stopped");

        DefaultExecutor.execute(() -> {
            try {
                if (mMetaDataManager != null) {
                    mMetaDataManager.flushOnExit();
                }
            } catch (Exception e) {
                Log.e(TAG, "", e);
            }
        });
        try {
            Future<?> shutdownFuture = shutdownExecutorSafely();
            if (shutdownFuture != null) {
                // 阻塞等待最多 3 秒，确保旧线程池被完全销毁
                shutdownFuture.get(3, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Wait for shutdown failed", e);
            PlayerProgressListenerManager.getInstance().log("Wait for shutdown failed:"+Log.getStackTraceString(e));
        }
    }

    /**
     * 恢复缓存任务
     */
    @Override
    public void resumeCacheTask() {
        LogUtils.i(TAG, "resumeCacheTask");
        if (isTaskShutdown()) {
            isPausing.set(false);
            mDownloadManager.resetGlobalCancel();
            isRunning.set(true);
            isStart = true;

            initM3U8TsInfo();
            int seekIndex = calculateStartIndex();
            startRequestVideoRange(seekIndex);
        }
    }

    /**
     * 客户端Seek操作
     */
    @Override
    public void seekToCacheTaskFromClient(float percent) {
        int segIndex = (int) (mSegList.size() * percent);
        LogUtils.e(TAG, "====seekToCacheTaskFromClient=" + segIndex + " percent:" + percent);
        PlayerProgressListenerManager.getInstance().log("seekToCacheTaskFromClient:" + segIndex);
        PlayerProgressListenerManager.getInstance().onSeek(segIndex);

        // 暂停当前下载（会取消所有网络请求）
        pauseCacheTask();
        waitForCancellation();

        // 重置状态，从新位置开始
        isPausing.set(false);
        mDownloadManager.resetGlobalCancel();
        isRunning.set(true);
        isStart = true;
        startRequestVideoRange(segIndex);
    }

    /**
     * 服务端Seek（非M3U8视频专用）
     */
    @Override
    public void seekToCacheTaskFromServer(long startPosition) {
        // M3U8不支持此方法
    }

    /**
     * 服务端Seek
     */
    @Override
    public void seekToCacheTaskFromServerByM3u8(int segIndex) {
        LogUtils.e(TAG, "====seekToCacheTaskFromServerByM3u8 " + segIndex);
        PlayerProgressListenerManager.getInstance().log("seekToCacheTaskFromServerByM3u8:" + segIndex);
        PlayerProgressListenerManager.getInstance().onSeek(segIndex);

        pauseCacheTask();
        waitForCancellation();

        isPausing.set(false);
        mDownloadManager.resetGlobalCancel();
        isRunning.set(true);
        isStart = true;
        startRequestVideoRange(segIndex);
    }

    /**
     * 服务端Seek（M3U8视频）
     */
    @Override
    public void seekToCacheTaskFromServer(int segIndex, long time) {
        LogUtils.e(TAG, "====seekToCacheTaskFromServer " + segIndex);
        PlayerProgressListenerManager.getInstance().log("seekToCacheTaskFromServer:" + segIndex);
        PlayerProgressListenerManager.getInstance().onSeek(segIndex);

        pauseCacheTask();
        waitForCancellation();

        isPausing.set(false);
        mDownloadManager.resetGlobalCancel();
        isRunning.set(true);
        isStart = true;
        startRequestVideoRange(segIndex);
    }

    /**
     * 开始下载视频范围
     *
     * 【优化1】线程池管理：使用 awaitTermination 非阻塞等待
     * 【优化3】支持中断：提交任务前检查取消状态
     */
    private void startRequestVideoRange(int curTs) {
        PlayerProgressListenerManager.getInstance().log("startRequestVideoRange:" + curTs);
        isRunning.set(true);
        // 主动清空 DefaultExecutor 中已积压的旧启动任务
        DefaultExecutor.clear();
        try {
            Future<?> shutdownFuture = shutdownExecutorSafely();
            if (shutdownFuture != null) {
                // 阻塞等待最多 3 秒，确保旧线程池被完全销毁
                shutdownFuture.get(3, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Wait for shutdown failed", e);
        }
        DefaultExecutor.execute(() -> {
            try {
                PlayerProgressListenerManager.getInstance().log("saveVideoInfo");
                saveVideoInfo();

                if (mCacheInfo.isCompleted()) {
                    notifyOnTaskCompleted();
                    PlayerProgressListenerManager.getInstance().log("缓存已下载完成");
                    return;
                }

                // 创建新线程池
                mTaskExecutor = new ThreadPoolExecutor(
                        THREAD_POOL_COUNT, THREAD_POOL_COUNT, 0L,
                        TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                        r -> new Thread(r, "M3U8Downloader"),
                        new ThreadPoolExecutor.DiscardOldestPolicy()
                );

                printLog=true;
                // 提交下载任务
                for (int index = curTs; index < mSegList.size(); index++) {
                    final M3U8Seg seg = mSegList.get(index);

                    // 【优化4】使用缓存快速判断是否已下载
                    if (isSegAlreadyDownloaded(seg)) {
                        continue;
                    }

                    // 【优化3】检查取消状态
                    if (!isRunning.get() || isPausing.get()) {
                        Log.i(TAG, "Task stopped, break submitting");
                        PlayerProgressListenerManager.getInstance().log("=Task stopped, break submitting");
                        break;
                    }

                    mTaskExecutor.execute(() -> {
                        if (isRunning.get() && !isPausing.get()) {
                            if(printLog){
                                //只打印一次log
                                PlayerProgressListenerManager.getInstance().log("Task 开始下载");
                                printLog=false;
                            }
                            startDownloadSegTask(seg);
                        }
                    });
                }

                // 【优化1】关闭线程池并等待完成（非阻塞）
                if (mTaskExecutor != null) {
                    mTaskExecutor.shutdown();
                    try {
                        // 循环检查，每2秒检查一次取消状态
                        while (!mTaskExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                            // 【优化3】如果被取消，立即强制关闭
                            if (!isRunning.get() || isPausing.get()) {
                                mTaskExecutor.shutdownNow();
                                Log.i(TAG, "Task interrupted, shutdownNow called");
                                PlayerProgressListenerManager.getInstance().log("=Task interrupted, shutdownNow called");
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        mTaskExecutor.shutdownNow();
                        PlayerProgressListenerManager.getInstance().log("task shutdownNow InterruptedException");
                    }
                }

                // 检查是否全部完成
                if (mCachedSegCount >= mTotalSegCount) {
                    mCacheInfo.setIsCompleted(true);
                    mCacheInfo.setTotalSize(mCachedSize);
                    mTotalSize = mCachedSize;
                    notifyOnTaskCompleted();
                    saveVideoInfo();
                }
                PlayerProgressListenerManager.getInstance().log("task结束");
                isRunning.set(false);
            }catch (Exception e){
                e.printStackTrace();
                PlayerProgressListenerManager.getInstance().log("startRequestVideoRange exception:"+e.getMessage());
            }

        });
    }
    // ==================== 核心业务方法 ====================

    /**
     * 计算起始下载索引
     */
    private int calculateStartIndex() {
        if (mCachedSegCount > 1 && mCachedSegCount <= mTotalSegCount) {
            return mCachedSegCount - 1;
        }
        return mCachedSegCount;
    }


    /**
     * 【优化5】检查磁盘空间
     * 父类 mSaveDir 已经是 File 类型，直接使用
     */
    private boolean checkDiskSpace() {
        if (mSaveDir == null) {
            return false;
        }
        if (!mSaveDir.exists()) {
            mSaveDir.mkdirs();
        }
        long availableSpace = mSaveDir.getUsableSpace();
        long requiredSpace = DISK_SPACE_RESERVE_MB * 1024 * 1024;
        if (availableSpace < requiredSpace) {
            Log.e(TAG, "Insufficient disk space: available=" + availableSpace + ", required=" + requiredSpace);
            return false;
        }
        return true;
    }

    /**
     * 【优化1】安全关闭线程池（非阻塞）
     * 使用 awaitTermination 避免 while 循环阻塞
     */
    // M3U8CacheTask.java
    private Future<?> shutdownExecutorSafely() {
        if (mTaskExecutor == null) {
            return CompletableFuture.completedFuture(null);
        }

        // 提交关闭任务到 DefaultExecutor
        return DefaultExecutor.submit(() -> {
            try {
                PlayerProgressListenerManager.getInstance().log("shutdownExecutorSafely executing");
                // 立即中断所有执行中的任务
                mTaskExecutor.shutdownNow();
                // 非阻塞等待，最多等待 2 秒
                if (!mTaskExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor did not terminate in " + EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS + "s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 等待取消完成
     * 等待所有活跃下载被取消
     */
    private void waitForCancellation() {
        int waitCount = 0;
        while (mDownloadManager.hasActiveDownloads() && waitCount < 30) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waitCount++;
        }
        Log.i(TAG, "Wait for cancellation complete, active: " +
                mDownloadManager.getActiveDownloadCount());
        PlayerProgressListenerManager.getInstance().log("Wait for cancellation complete, active: " +
                mDownloadManager.getActiveDownloadCount());
    }

    /**
     * 【优化3】取消所有下载
     * 调用 TsDownloadManager.cancelAllDownloads() 取消所有 OkHttp Call
     */
    private void cancelAllDownloads(String reason) {
        Log.i(TAG, "Cancelling all downloads, reason: " + reason);
        mDownloadManager.cancelAllDownloads();
    }



    /**
     * 【优化4】检查TS是否已下载（使用缓存）
     */
    private boolean isSegAlreadyDownloaded(M3U8Seg seg) {
        String segName = seg.getSegName();

        // 使用缓存快速判断
        if (mSegSizeCache.containsKey(segName)) {
            return true;
        }

        // 缓存中没有，检查文件系统
        File segFile = new File(mSaveDir, segName);
        if (segFile.exists() && segFile.length() > 0) {
            long expectedSize = mMetaDataManager.getTsMetaData(segName);
            if (expectedSize > 0 && VideoCacheUtils.sizeSimilar(segFile.length(), expectedSize)) {
                // 【优化4】加入缓存
                mSegSizeCache.put(segName, segFile.length());
                mCachedSegCount++;
                mCachedSize += segFile.length();
                return true;
            }
        }
        return false;
    }

    /**
     * 开始下载单个TS分片
     */
    private void startDownloadSegTask(M3U8Seg seg) {
        if (!isRunning.get() || isPausing.get()) {
            return;
        }

        // 下载初始化分片（如果有）
        if (seg.hasInitSegment()) {
            String initSegmentName = seg.getInitSegmentName();
            File initSegmentFile = new File(mSaveDir, initSegmentName);
            if (!initSegmentFile.exists()) {
                if (!downloadFile(seg, initSegmentFile, seg.getInitSegmentUri())) {
                    return;
                }
            }
        }

        // 下载TS分片
        String segName = seg.getSegName();
        File segFile = new File(mSaveDir, segName);

        if (!segFile.exists() || segFile.length() == 0) {
            if (!downloadFile(seg, segFile, seg.getUrl())) {
                return;
            }
        }

        // 【优化4】验证并更新进度（增量更新，不遍历全部文件）
        if (segFile.exists() && segFile.length() > 0) {
            long expectedSize = mMetaDataManager.getTsMetaData(segName);
            if (expectedSize > 0 && VideoCacheUtils.sizeSimilar(segFile.length(), expectedSize)) {
                seg.setFileSize(segFile.length());
                if (!mSegSizeCache.containsKey(segName)) {
                    mSegSizeCache.put(segName, segFile.length());
                    mCachedSegCount++;
                    mCachedSize += segFile.length();
                    // 增量更新进度
                    notifyCacheProgress();
                }
            }
        }
    }

    /**
     * 【优化2】下载文件（使用循环重试，避免递归）
     * 【优化3】支持中断：通过 TsDownloadManager 取消 OkHttp Call
     */
    private boolean downloadFile(M3U8Seg ts, File file, String videoUrl) {
        if (!isRunning.get() || isPausing.get()) {
            return false;
        }

        String fileName = file.getName();
        File tmpFile = new File(file.getParentFile(), fileName + TEMP_POSTFIX);
        FileUtils.deleteFile(tmpFile);

        // 【优化2】循环重试，非递归
        int retryCount = 0;
        int maxRetry = MAX_RETRY_COUNT;

        while (retryCount <= maxRetry && isRunning.get() && !isPausing.get()) {
            // 重试延迟（指数退避）
            if (retryCount > 0) {
                int delay = calculateBackoffDelay(retryCount);
                try {
                    Log.i(TAG, "Retry " + retryCount + " for " + fileName + ", delay: " + delay + "ms");
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            try {
                boolean success = doDownload(ts, file, videoUrl, tmpFile);
                if (success) {
                    return true;
                }
                retryCount++;
            } catch (IOException e) {
                // 【优化3】检查是否是取消导致的异常
                if (e.getMessage() != null && e.getMessage().contains("cancel")) {
                    Log.i(TAG, "Download cancelled: " + fileName+" " + e.getMessage());
                    return false;
                }
                Log.e(TAG, "Download error: " + fileName + ", retry " + retryCount, e);
                retryCount++;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error: " + fileName + ", retry " + retryCount, e);
                PlayerProgressListenerManager.getInstance().log("Unexpected error: " + fileName + ", retry " + retryCount+" " + e.getMessage());
                retryCount++;
            }
        }

        Log.e(TAG, "Download failed after " + retryCount + " retries: " + fileName);
        return false;
    }

    /**
     * 实际下载逻辑
     * 【优化3】使用 TsDownloadManager 管理下载，支持中断
     */
    private boolean doDownload(M3U8Seg ts, File file, String videoUrl, File tmpFile) throws IOException {
        if (!isRunning.get() || isPausing.get()) {
            throw new IOException("Download cancelled");
        }

        String fileName = file.getName();
        TsDownloadManager.DownloadTask downloadTask = null;
        InputStream inputStream = null;

        try {
            // 通过 DownloadManager 执行下载（支持取消）
            downloadTask = mDownloadManager.startDownload(fileName, videoUrl, mHeaders);

            if (downloadTask == null || downloadTask.isCancelled()) {
                throw new IOException("Download cancelled");
            }

            if (downloadTask.error != null) {
                throw downloadTask.error;
            }

            Response response = downloadTask.response;
            if (response == null || !response.isSuccessful()) {
                throw new IOException("HTTP error: " + (response != null ? response.code() : -1));
            }

            inputStream = response.body().byteStream();
            long contentLength = response.body().contentLength();

            // 【优化3】下载到临时文件，每1MB检查一次取消状态
            long actualSize = downloadToFile(inputStream, tmpFile, contentLength);

            // 再次检查取消状态
            if (!isRunning.get() || isPausing.get()) {
                FileUtils.deleteFile(tmpFile);
                throw new IOException("Download cancelled");
            }

            // 验证下载完整性
            if (!isDownloadComplete(contentLength, actualSize, tmpFile)) {
                String error = String.format("File incomplete: expected=%d, actual=%d, file=%s",
                        contentLength, actualSize, tmpFile.getName());
                Log.e(TAG, error);
                PlayerProgressListenerManager.getInstance().log("=task 下载不完整:" + fileName);
                FileUtils.deleteFile(tmpFile);
                // ✅ 触发外部重试
                throw new IOException("File incomplete");
            }

            // 处理加密
            byte[] encryptionKey = ts.encryptionKey == null ? mM3U8.encryptionKey : ts.encryptionKey;
            String iv = ts.encryptionKey == null ? mM3U8.encryptionIV : ts.getKeyIv();

            if (encryptionKey != null) {
                File tempDecryptedFile = new File(tmpFile.getParent(), "decrypted_" + tmpFile.getName());
                if (AES128Utils.decryptFile(tmpFile, tempDecryptedFile, encryptionKey, iv)) {
                    if (tempDecryptedFile.exists() && tempDecryptedFile.length() > 0) {
                        FileUtils.handleRename(tempDecryptedFile, file);
                        FileUtils.deleteFile(tmpFile);
                        contentLength = file.length();
                    } else {
                        PlayerProgressListenerManager.getInstance().log("task 解密后文件为空:" + ts.getSegName());
                        FileUtils.deleteFile(tmpFile);
                        FileUtils.deleteFile(tempDecryptedFile);
                        PlayerProgressListenerManager.getInstance().log(ts.getSegName()+" 下载错误:"+"Decrypted file is empty");
                        return false;
                    }
                } else {
                    FileUtils.deleteFile(tmpFile);
                    PlayerProgressListenerManager.getInstance().log(ts.getSegName()+" 下载错误:"+"AES decrypt failed");
                    return false;
                }
            } else {
                FileUtils.handleRename(tmpFile, file);
                if (contentLength <= 0) {
                    contentLength = file.length();
                }
            }
            // 处理加密
//            if (!processEncryption(ts, tmpFile, file)) {
//                FileUtils.deleteFile(tmpFile);
//                PlayerProgressListenerManager.getInstance().log(ts.getSegName()+" 下载错误:"+"AES decrypt failed");
//
//                return false;
//            }
            if (file.exists() && file.length() > 0) {
                ts.setContentLength(contentLength);
                mMetaDataManager.saveTsMetaData(file.getName(), contentLength);
                Log.i(TAG, "队列ts下载完成:" + ts.getSegName());
                PlayerProgressListenerManager.getInstance().log("=task ts下载完成:" + ts.getSegName());
                if (ts.getSegIndex() == 0) {
                    if (PlayerProgressListenerManager.getInstance().getListener() != null) {
                        PlayerProgressListenerManager.getInstance().getListener().onTaskFirstTsDownload(fileName);
                    }
                }
                return true;
            }

            return false;

        } finally {
            ProxyCacheUtils.close(inputStream);
            if (downloadTask != null && !downloadTask.isCompleted) {
                mDownloadManager.cancelDownload(fileName);
            }
        }
    }

    /**
     * 【优化3】下载到文件，每1MB检查一次取消状态
     * 确保能够及时响应中断请求
     */
    private long downloadToFile(InputStream inputStream, File tmpFile, long expectedLength)
            throws IOException {
        try (ReadableByteChannel rbc = Channels.newChannel(inputStream);
             FileOutputStream fos = new FileOutputStream(tmpFile);
             FileChannel foutc = fos.getChannel()) {

            long totalRead = 0;
            long startTime = System.currentTimeMillis();
            final long TIMEOUT_MS = 30000; // 30秒超时
            while (expectedLength <= 0 || totalRead < expectedLength) {
                // 【优化3】每1MB检查一次取消状态
                // ✅ 检查取消
                if (!isRunning.get() || isPausing.get()) {
                    throw new IOException("Download cancelled");
                }

                // ✅ 检查超时
                if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                    throw new IOException("Download timeout");
                }

                long remaining = expectedLength > 0 ? expectedLength - totalRead : Long.MAX_VALUE;
                long toTransfer = Math.min(remaining, CHECK_INTERVAL_BYTES);

                long transferred = foutc.transferFrom(rbc, totalRead, toTransfer);
                // ⚠️ 如果 transferFrom 返回 0，说明流已结束或网络问题
                // 直接跳出循环，不重试
                if (transferred == 0) {
                    break;
                }
                totalRead += transferred;
            }

            // 确保数据写入磁盘
            foutc.force(true);
            return totalRead;
        }
    }
    /**
     * 处理AES加密
     */
    private boolean processEncryption(M3U8Seg ts, File tmpFile, File targetFile) {
        byte[] encryptionKey = ts.encryptionKey == null ? mM3U8.encryptionKey : ts.encryptionKey;
        String iv = ts.encryptionKey == null ? mM3U8.encryptionIV : ts.getKeyIv();

        if (encryptionKey != null) {
            File tempDecryptedFile = new File(targetFile.getParentFile(), "decrypted_" + tmpFile.getName());
            try {
                if (AES128Utils.decryptFile(tmpFile, tempDecryptedFile, encryptionKey, iv)) {
                    if (tempDecryptedFile.exists() && tempDecryptedFile.length() > 0) {
                        FileUtils.handleRename(tempDecryptedFile, targetFile);
                        FileUtils.deleteFile(tmpFile);
                        return true;
                    }
                }
                return false;
            } finally {
                FileUtils.deleteFile(tmpFile);
            }
        } else {
            FileUtils.handleRename(tmpFile, targetFile);
            return true;
        }
    }

    /**
     * 检查下载是否完整
     */
    private boolean isDownloadComplete(long expectedLength, long actualLength, File file) {
        if (!file.exists() || file.length() == 0) {
            return false;
        }
        if (expectedLength > 0) {
            return VideoCacheUtils.sizeSimilar(actualLength, expectedLength);
        }
        return actualLength > 0;
    }

    /**
     * 【优化2】计算退避延迟（指数退避）
     */
    private int calculateBackoffDelay(int retryCount) {
        //todo 503处理：延迟时间得加大
        int delay = Math.min(30000, 1000 * (1 << Math.min(retryCount, 5)));
        return delay + (int) (Math.random() * 1000);
    }

    /**
     * 【优化4】增量更新进度，不再遍历全部文件
     * 只有下载完成一个新TS时才调用
     */
    private void notifyCacheProgress() {
        if (mCachedSegCount > mTotalSegCount) {
            mCachedSegCount = mTotalSegCount;
        }

        mCacheInfo.setCachedTs(mCachedSegCount);
        mCacheInfo.setCachedSize(mCachedSize);

        float percent = mCachedSegCount * 100.0f / mTotalSegCount;

        if (!ProxyCacheUtils.isFloatEqual(percent, mPercent)) {
            long nowTime = System.currentTimeMillis();
            if (mCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                mSpeed = (mCachedSize - mLastCachedSize) * 1000.0f / (nowTime - mLastInvokeTime);
            }

            if (mListener != null) {
                mListener.onM3U8TaskProgress(percent, mCachedSize, mSpeed);
            }
            mPercent = percent;
            mCacheInfo.setPercent(percent);
            mCacheInfo.setSpeed(mSpeed);
            mLastInvokeTime = nowTime;
            mLastCachedSize = mCachedSize;
            saveVideoInfo();
        }

        if (mCachedSegCount >= mTotalSegCount) {
            mCacheInfo.setIsCompleted(true);
            mCacheInfo.setTotalSize(mCachedSize);
            mTotalSize = mCachedSize;
            notifyOnTaskCompleted();
            saveVideoInfo();
        }
    }

    public boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }


    // 公开方法：检查某个分片是否已下载（基于缓存 Map）
    public boolean isSegDownloaded(int segIndex) {
        if (segIndex < 0 || segIndex >= mSegList.size()) return false;
        String segName = mSegList.get(segIndex).getSegName();
        return mSegSizeCache.containsKey(segName);
    }

}