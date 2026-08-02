package com.jeffmony.videocache.task;

import android.util.Log;

import com.jeffmony.videocache.CacheConstants;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.m3u8.M3U8Seg;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.AES128Utils;
import com.jeffmony.videocache.utils.DefaultExecutor;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.OkHttpUtil;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.TsFileValidator;
import com.jeffmony.videocache.utils.TsMetaDataManager;
import com.jeffmony.videocache.utils.VideoCacheUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Response;

/**
 * 播放器缓存，边下边播
 */
public class M3U8CacheTask extends VideoCacheTask {

    private static final String TAG = "M3U8CacheTask";

    private static final String TEMP_POSTFIX = ".task_downloading";

    //太多会导致OOM
    private static final int THREAD_POOL_COUNT = 5;

    private int mCachedSegCount;
    private int mTotalSegCount;
    private List<M3U8Seg> mSegList;
    M3U8 mM3U8;
    final static int MAX_RETRY_COUNT=2;
    private final static int MAX_RETRY_COUNT_503 = 3;//遇到503的重试次数
    private String mVideoName;
    AtomicBoolean isRunning = new AtomicBoolean(false);//任务是否正在运行中
    private TsMetaDataManager metaDataManager;

    public M3U8CacheTask(VideoCacheInfo cacheInfo, Map<String, String> headers, M3U8 m3u8) {
        super(cacheInfo, headers);
        mSegList = m3u8.getSegList();
        this.mM3U8=m3u8;
        mTotalSegCount = mSegList == null ? 0 : mSegList.size();
        mCachedSegCount = cacheInfo.getCachedTs();
        mHeaders.put("Connection", "close");
        mVideoName=ProxyCacheUtils.decodeUriWithBase64(mHeaders.get(CacheConstants.HEADER_KEY_NAME));
        // 初始化元数据管理器
        metaDataManager = TsMetaDataManager.getInstance(mSaveDir);
    }

    @Override
    public void startCacheTask() {
        if (isTaskRunning()) {
            return;
        }
        if (isRunning.get()){
            return;
        }

        notifyOnTaskStart();
        initM3U8TsInfo();
        int seekIndex = mCachedSegCount > 1 && mCachedSegCount <= mTotalSegCount ? mCachedSegCount - 1 : mCachedSegCount;
        //todo 这里的逻辑有问题，假如第一次播放视频，seek进度条，没完成缓存就退出；那么下次再进入播放时，计算的下载起点是不靠谱的；必须要获取到当前播放请求index
        startRequestVideoRange(seekIndex);
    }

    private void initM3U8TsInfo() {
        long tempCachedSize = 0;
        int tempCachedTs = 0;

        // ✅ 一次性从元数据管理器加载所有大小信息
        for (int index = 0; index < mSegList.size(); index++) {
            M3U8Seg ts = mSegList.get(index);
            File tempTsFile = new File(mSaveDir, ts.getSegName());

            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                // ✅ 从内存缓存中获取预期大小
                long expectedSize = metaDataManager.getTsMetaData(ts.getSegName());

                if (expectedSize > 0) {
                    // 有元数据，验证文件大小
                    if (VideoCacheUtils.sizeSimilar(tempTsFile.length(), expectedSize)) {
                        ts.setFileSize(tempTsFile.length());
                        tempCachedSize += tempTsFile.length();
                        tempCachedTs++;
                    } else {
                        // 文件不完整，删除
                        LogUtils.w(TAG, "Found incomplete file, deleting: " + tempTsFile.getName());
                        FileUtils.deleteFile(tempTsFile);
                        metaDataManager.removeTsMetaData(ts.getSegName());
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

    @Override
    public void pauseCacheTask() {
        Log.e(TAG, "====pauseCacheTask");
        isRunning.set(false);
        DefaultExecutor.execute(() -> {
            try {
                if (mTaskExecutor != null) {

                    mTaskExecutor.shutdownNow();
                }
            } catch (Exception e) {
                Log.e("", "", e);
            }
        });

    }

    @Override
    public void stopCacheTask() {
        Log.e(TAG, "=====stopCacheTask");
        isRunning.set(false);
        DefaultExecutor.execute(() -> {
            try {
                // ✅ 应用退出时保存元数据
                if (metaDataManager != null) {
                    metaDataManager.flushOnExit();
                }
                if (mTaskExecutor != null) {
                    mTaskExecutor.shutdownNow();
                }
            } catch (Exception e) {
                Log.e(TAG,"",e);
            }
        });


    }

    @Override
    public void resumeCacheTask() {
        LogUtils.i(TAG, "resumeCacheTask");
        if (isTaskShutdown()) {
            initM3U8TsInfo();
            int seekIndex = mCachedSegCount > 1 && mCachedSegCount <= mTotalSegCount ? mCachedSegCount - 1 : mCachedSegCount;
            startRequestVideoRange(seekIndex);
        }
    }

    @Override
    public void seekToCacheTaskFromClient(float percent) {
        int segIndex= (int) (mSegList.size()*percent);
        LogUtils.e(TAG, "====seekToCacheTaskFromClient=" + segIndex+" percent:"+percent);
        PlayerProgressListenerManager.getInstance().onSeek(segIndex);
        pauseCacheTask();
        startRequestVideoRange(segIndex);


    }

    @Override
    public void seekToCacheTaskFromServer(long startPosition) {
    }

    @Override
    public void seekToCacheTaskFromServerByM3u8(int segIndex) {
        LogUtils.e(TAG, "====seekToCacheTaskFromServerByM3u8 " + segIndex);
        PlayerProgressListenerManager.getInstance().onSeek(segIndex);
        pauseCacheTask();
        startRequestVideoRange(segIndex);
    }

    @Override
    public void seekToCacheTaskFromServer(int segIndex, long time) {

    }

    private void startRequestVideoRange(int curTs) {
        isRunning.set(true);
        DefaultExecutor.execute(() -> {
            saveVideoInfo();
            PlayerProgressListenerManager.getInstance().log("saveVideoInfo");
            if (mCacheInfo.isCompleted()) {
                notifyOnTaskCompleted();
                return;
            }
            try {
                if (mTaskExecutor != null) {
                    mTaskExecutor.shutdownNow();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mTaskExecutor = null;
            mTaskExecutor = new ThreadPoolExecutor(THREAD_POOL_COUNT, THREAD_POOL_COUNT, 0L,
                    TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
                    new ThreadPoolExecutor.DiscardOldestPolicy());
            for (int index = curTs; index < mSegList.size(); index++) {
                final M3U8Seg seg = mSegList.get(index);
                try {
                    mTaskExecutor.execute(() -> {
//                            try {
                        startDownloadSegTask(seg);
//                            } catch (Exception e) {
//                                LogUtils.w(TAG, "M3U8 ts video download failed, exception=" + e);
//                                notifyOnTaskFailed(e);
//                            }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "发生异常: ", e);
                }

            }
            if (mTaskExecutor != null) {
                mTaskExecutor.shutdown();//下载完成之后要关闭线程池
            }
            while (mTaskExecutor != null && !mTaskExecutor.isTerminated()) {

                try {
                    //等待中
                    Thread.sleep(2000);
                } catch (Exception e) {
                    Log.e(TAG, "发生异常: ", e);
                }

//            try {
//                ThreadPoolExecutor tpe = ((ThreadPoolExecutor) mTaskExecutor);
//                int queueSize = tpe.getQueue().size();
//                int activeCount = tpe.getActiveCount();
//                long completedTaskCount = tpe.getCompletedTaskCount();
//                long taskCount = tpe.getTaskCount();
//                Log.e(TAG, mVideoName+" 当前排队线程数：" + queueSize + " 当前活动线程数：" + activeCount + " 执行完成线程数：" + completedTaskCount + " 总线程数：" + taskCount);
//            } catch (Exception e) {
//                Log.e(TAG, "发生异常: ", e);
//            }
                //        isRunning.set(true);
            }
        });



    }

    private void startDownloadSegTask(M3U8Seg seg)  {
        LogUtils.i(TAG, "startDownloadSegTask index="+seg.getSegIndex()+", url="+seg.getUrl());
        if (seg.hasInitSegment()) {
            String initSegmentName = seg.getInitSegmentName();
            File initSegmentFile = new File(mSaveDir, initSegmentName);
            if (!initSegmentFile.exists()) {
                downloadFile(seg, initSegmentFile, seg.getInitSegmentUri());
            }
        }
        String segName = seg.getSegName();
        File segFile = new File(mSaveDir, segName);
        if (!segFile.exists()) {
            // ts is network resource, download ts file then rename it to local file.
//            downloadSegFile(seg, segFile, seg.getUrl());
            downloadFile(seg, segFile, seg.getUrl());
        }

        boolean exist=segFile.exists();
        long length=segFile.length();
        long contentLength=seg.getContentLength();
        //确保当前文件下载完整
        if (exist && length > 0 &&VideoCacheUtils.sizeSimilar(length,contentLength)) {
            //只有这样的情况下才能保证当前的ts文件真正被下载下来了
            seg.setFileSize(segFile.length());
            //更新进度
            notifyCacheProgress();
//            Log.e(TAG,"notifyCacheProgress:"+segFile.getName()+" length:"+segFile.length());
        }else {
//            Log.e(TAG,"文件大小不一致:"+segFile.getName()+" length:"+length+" contentlength:"+contentLength);
        }
    }

    public void downloadFile(M3U8Seg ts, File file, String videoUrl) {
        if(!isRunning.get()){
            return;
        }
        String fileName = file.getName();
        InputStream inputStream = null;
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        File tmpFile = new File(file.getParentFile(), fileName + TEMP_POSTFIX);

        try {
            response = OkHttpUtil.getInstance().requestSync(videoUrl, mHeaders);
            int responseCode = response.code();
            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                ts.setRetryCount(0);
                inputStream = response.body().byteStream();
                long contentLength = response.body().contentLength();

                // ✅ 下载到临时文件，获取实际下载大小（内部不重试，一次性读取）
                long actualDownloadSize = downloadToTempFile(inputStream, tmpFile, contentLength);

                // ✅ 验证文件完整性
                if (!isDownloadComplete(contentLength, actualDownloadSize, tmpFile)) {
                    String error = String.format("File incomplete: expected=%d, actual=%d, file=%s",
                            contentLength, actualDownloadSize, tmpFile.getName());
                    Log.e(TAG, error);
                    PlayerProgressListenerManager.getInstance().log("task 下载不完整:" + fileName);
                    FileUtils.deleteFile(tmpFile);
                    // ✅ 触发外部重试
                    onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception(error));
                    return;
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
                            onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception("Decrypted file is empty"));
                            return;
                        }
                    } else {
                        PlayerProgressListenerManager.getInstance().log("task aes decrypt fail:" + ts.getSegName());
                        FileUtils.deleteFile(tmpFile);
                        onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception("AES decrypt failed"));
                        return;
                    }
                } else {
                    FileUtils.handleRename(tmpFile, file);
                    if (contentLength <= 0) {
                        contentLength = file.length();
                    }
                }
                // ✅ 验证TS文件格式（使用FileChannel，只检查开头和结尾）
//                if (!TsFileValidator.isValidTsFile(file)) {
//                    String error = "Invalid TS file: " + tmpFile.getName();
//                    Log.e(TAG, error);
//                    PlayerProgressListenerManager.getInstance().log("task TS文件无效,没有0x47开头:" + tmpFile.getName());
//                    FileUtils.deleteFile(tmpFile);
//                    FileUtils.deleteFile(file);
//                    return;
//                }
                ts.setContentLength(contentLength);
//                Log.i(TAG, "队列ts下载完成:" + ts.getSegName());
                PlayerProgressListenerManager.getInstance().log("=task ts下载完成:" + ts.getSegName());

                if (ts.getSegIndex() == 0) {
                    if (PlayerProgressListenerManager.getInstance().getListener() != null) {
                        PlayerProgressListenerManager.getInstance().getListener().onTaskFirstTsDownload(fileName);
                    }
                }
                // ✅ 下载完成后保存元数据
                if (file.exists() && file.length() > 0) {
                    ts.setContentLength(contentLength);
                    metaDataManager.saveTsMetaData(file.getName(), contentLength);
                }
            } else {
                Log.e(TAG, "HTTP error: " + responseCode + " for " + fileName);
                PlayerProgressListenerManager.getInstance().log("=task " + fileName + " HTTP错误:" + responseCode);
                // ✅ 触发外部重试
                onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception("HTTP " + responseCode));
            }
        } catch (InterruptedIOException | ClosedByInterruptException e) {
            Log.i(TAG, "Download interrupted: " + fileName);
            FileUtils.deleteFile(tmpFile);
            // 中断不重试，直接返回
        } catch (Exception e) {
            Log.e(TAG, "Download error for " + fileName, e);
            PlayerProgressListenerManager.getInstance().log("=task " + fileName + "下载出错:" + e.getMessage());
            FileUtils.deleteFile(tmpFile);
            // ✅ 触发外部重试
            onDownloadFileErr(ts, file, videoUrl, 0, e);
        } finally {
            ProxyCacheUtils.close(inputStream);
            ProxyCacheUtils.close(fos);
            ProxyCacheUtils.close(response);
            ProxyCacheUtils.close(rbc);
            ProxyCacheUtils.close(foutc);
        }
    }

    /**
     * ✅ 下载到临时文件（不带重试，一次性读取）
     * 如果网络中断或读取失败，直接抛出异常
     */
    private long downloadToTempFile(InputStream inputStream, File tmpFile, long expectedLength)
            throws IOException {
        try (ReadableByteChannel rbc = Channels.newChannel(inputStream);
             FileOutputStream fos = new FileOutputStream(tmpFile);
             FileChannel foutc = fos.getChannel()) {

            long totalRead = 0;

            // ✅ 没有重试逻辑，直接尝试一次性读取所有数据
            while (expectedLength <= 0 || totalRead < expectedLength) {
                long remaining = expectedLength > 0 ? expectedLength - totalRead : Long.MAX_VALUE;
                long transferred = foutc.transferFrom(rbc, totalRead, remaining);

                // ⚠️ 如果 transferFrom 返回 0，说明流已结束或网络问题
                // 直接跳出循环，不重试
                if (transferred == 0) {
                    Log.w(TAG, "transferFrom returned 0, stream may be ended");
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
     * expectedLength 精确大小
     * actualLength 实际下载大小
     * ✅ 检查下载是否完整
     */
    private boolean isDownloadComplete(long expectedLength, long actualLength, File file) {
        if (!file.exists() || file.length() == 0) {
            return false;
        }

        if (expectedLength > 0) {
            return VideoCacheUtils.sizeSimilar(actualLength, expectedLength);
        }

        // 如果不知道预期大小，至少确保下载了数据
        return actualLength > 0;
    }

    /**
     * ✅ 保留外部重试逻辑
     */
    private void onDownloadFileErr(M3U8Seg ts, File file, String videoUrl, int responseCode, Exception exception) {
        ts.setRetryCount(ts.getRetryCount() + 1);

        if (responseCode == com.baofu.cache.downloader.utils.HttpUtils.RESPONSE_503 ||
                responseCode == com.baofu.cache.downloader.utils.HttpUtils.RESPONSE_429) {
            if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
                // 遇到503，延迟[4,24]秒后再重试
                int ran = 4000 + (int) (Math.random() * 20000);
                try {
                    Thread.sleep(ran);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                downloadFile(ts, file, videoUrl);
            }
        } else if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
            downloadFile(ts, file, videoUrl);
        } else {
            Log.e(TAG, "Max retry count reached for " + file.getName());
            PlayerProgressListenerManager.getInstance().log("task " + file.getName() + "重试"+ts.getRetryCount()+"次 次数已用完");
        }
    }

    private void notifyCacheProgress() {
        updateM3U8TsInfo();
        if (mCachedSegCount > mTotalSegCount) {
            mCachedSegCount = mTotalSegCount;
        }
        mCacheInfo.setCachedTs(mCachedSegCount);
        mCacheInfo.setCachedSize(mCachedSize);
        float percent = mCachedSegCount * 1.0f * 100 / mTotalSegCount;

        if (!ProxyCacheUtils.isFloatEqual(percent, mPercent)) {
            long nowTime = System.currentTimeMillis();
            if (mCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                mSpeed = (mCachedSize - mLastCachedSize) * 1000 * 1.0f / (nowTime - mLastInvokeTime); //byte/s
            }
            mListener.onM3U8TaskProgress(percent, mCachedSize, mSpeed);
            mPercent = percent;
            mCacheInfo.setPercent(percent);
            mCacheInfo.setSpeed(mSpeed);
            mLastInvokeTime = nowTime;
            mLastCachedSize = mCachedSize;
            saveVideoInfo();
        }

        boolean isCompleted = true;
        for (M3U8Seg ts : mSegList) {
            File tsFile = new File(mSaveDir, ts.getSegName());
            if (!tsFile.exists()) {
                isCompleted = false;
                break;
            }
        }
        mCacheInfo.setIsCompleted(isCompleted);
        if (isCompleted) {
            mCacheInfo.setTotalSize(mCachedSize);
            mTotalSize = mCachedSize;
            notifyOnTaskCompleted();
            saveVideoInfo();
        }
    }

    private void updateM3U8TsInfo() {
        long tempCachedSize = 0;
        int tempCachedTs = 0;
        for (int index = 0; index < mSegList.size(); index++) {
            M3U8Seg ts = mSegList.get(index);
            File tempTsFile = new File(mSaveDir, ts.getSegName());
            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                ts.setFileSize(tempTsFile.length());
                tempCachedSize += tempTsFile.length();
                tempCachedTs++;
            }
        }
        mCachedSegCount = tempCachedTs;
        mCachedSize = tempCachedSize;
    }
}
