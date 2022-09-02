package com.baofu.videocache.task;

import android.util.Log;

import com.baofu.videocache.m3u8.M3U8;
import com.baofu.videocache.m3u8.M3U8Seg;
import com.baofu.videocache.model.VideoCacheInfo;
import com.baofu.videocache.utils.AES128Utils;
import com.baofu.videocache.utils.HttpUtils;
import com.baofu.videocache.utils.LogUtils;
import com.baofu.videocache.utils.OkHttpUtil;
import com.baofu.videocache.utils.ProxyCacheUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import okhttp3.Response;

public class M3U8CacheTask extends VideoCacheTask {

    private static final String TAG = "M3U8CacheTask";

    private static final int THREAD_POOL_COUNT = 5;

    private int mCachedSegCount;
    private int mTotalSegCount;
    private Map<Integer, Long> mSegLengthMap;
    private List<M3U8Seg> mSegList;
    M3U8 mM3U8;
    final static int MAX_RETRY_COUNT=1;
    private final static int MAX_RETRY_COUNT_503 = 3;//遇到503的重试次数

    public M3U8CacheTask(VideoCacheInfo cacheInfo, Map<String, String> headers, M3U8 m3u8) {
        super(cacheInfo, headers);
        mSegList = m3u8.getSegList();
        this.mM3U8=m3u8;
        mTotalSegCount = cacheInfo.getTotalTs();
        mCachedSegCount = cacheInfo.getCachedTs();
        mSegLengthMap = cacheInfo.getTsLengthMap();
        if (mSegLengthMap == null) {
            mSegLengthMap = new HashMap<>();
        }
        mHeaders.put("Connection", "close");
    }

    @Override
    public void startCacheTask() {
        if (isTaskRunning()) {
            return;
        }
        notifyOnTaskStart();
        initM3U8TsInfo();
        int seekIndex = mCachedSegCount > 1 && mCachedSegCount <= mTotalSegCount ? mCachedSegCount - 1 : mCachedSegCount;
        startRequestVideoRange(seekIndex);
    }

    private void initM3U8TsInfo() {
        long tempCachedSize = 0;
        int tempCachedTs = 0;
        for (int index = 0; index < mSegList.size(); index++) {
            M3U8Seg ts = mSegList.get(index);
            File tempTsFile = new File(mSaveDir, ts.getSegName());
            if (tempTsFile.exists() && tempTsFile.length() > 0) {
                ts.setFileSize(tempTsFile.length());
                mSegLengthMap.put(index, tempTsFile.length());
                tempCachedSize += tempTsFile.length();
                tempCachedTs++;
            } else {
                break;
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
        Log.e(TAG, "pauseCacheTask");
        if (isTaskRunning()) {
            try {
                if (mTaskExecutor != null) {

                    mTaskExecutor.shutdownNow();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stopCacheTask() {
        LogUtils.i(TAG, "stopCacheTask");
        if (isTaskRunning()) {
            try {
                if (mTaskExecutor != null) {
                    mTaskExecutor.shutdownNow();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
    }

    @Override
    public void seekToCacheTaskFromServer(long startPosition) {
    }

    @Override
    public void seekToCacheTaskFromServer(int segIndex) {
//        Log.e(TAG, "================seek segIndex="+segIndex);
        pauseCacheTask();
        startRequestVideoRange(segIndex);
    }

    private void startRequestVideoRange(int curTs) {
//        Log.e(TAG,"startRequest m3u8");
        if (mCacheInfo.isCompleted()) {
            notifyOnTaskCompleted();
            Log.e(TAG,"m3u8 isCompleted");
            return;
        }
        if (isTaskRunning()) {
            //已经存在的任务不需要重新创建了
            Log.e(TAG,"task m3u8 is running");
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
        mTaskExecutor = Executors.newFixedThreadPool(THREAD_POOL_COUNT);
        for (int index = curTs; index < mSegList.size(); index++) {
           final int temp=index;
            mTaskExecutor.execute(() -> {
                try {
                    final M3U8Seg seg = mSegList.get(temp);
                    startDownloadSegTask(seg);
                } catch (Exception e) {
                    e.printStackTrace();
//                    Log.e(TAG, "M3U8 ts video download failed, exception=" + e);
                    notifyOnTaskFailed(e);
                }
            });
        }
    }

    private void startDownloadSegTask(M3U8Seg seg)  {
//       Log.e(TAG, "startDownloadSegTask index="+seg.getSegIndex()+", url="+seg.getUrl());
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

        //确保当前文件下载完整
        if (segFile.exists() && segFile.length() == seg.getContentLength()) {
            //只有这样的情况下才能保证当前的ts文件真正被下载下来了
            mSegLengthMap.put(seg.getSegIndex(), segFile.length());
            seg.setName(segName);
            seg.setFileSize(segFile.length());
            //更新进度
            notifyCacheProgress();
        }
    }

    public void downloadFile(M3U8Seg ts, File file, String videoUrl) {
//        Log.e(TAG,"队列开始下载ts");
        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response=null;
        try {

            response = OkHttpUtil.getInstance().requestSync(videoUrl,mHeaders);
            int responseCode = response.code();
            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                ts.setRetryCount(0);
                inputStream = response.body().byteStream();
                long contentLength =  response.body().contentLength();

                byte[] encryptionKey = ts.encryptionKey == null ? mM3U8.encryptionKey : ts.encryptionKey;
                String iv = ts.encryptionKey == null ? mM3U8.encryptionIV : ts.getKeyIv();
                if ( encryptionKey != null) {
                    String tsInitSegmentName = ts.getInitSegmentName() + ".temp";
                    File tsInitSegmentFile = new File(mSaveDir, tsInitSegmentName);

                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tsInitSegmentFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);

                    FileOutputStream fileOutputStream = null;
                    try {
                        byte[] result = AES128Utils.dencryption(AES128Utils.readFile(tsInitSegmentFile), encryptionKey, iv);
                        if (result != null) {
                            fileOutputStream = new FileOutputStream(file);
                            fileOutputStream.write(result);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                            tsInitSegmentFile.delete();
                        }
                    }
                } else {
                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(file);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                }

                if (contentLength <= 0) {
                    contentLength = file.length();
                }
                ts.setContentLength(contentLength);
//                Log.e(TAG,"队列ts下载完成");
            } else {
                ts.setRetryCount(ts.getRetryCount() + 1);
                if (responseCode == HttpUtils.RESPONSE_503||responseCode == HttpUtils.RESPONSE_429) {
                    if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
                        //遇到503，延迟[4,24]秒后再重试，区间间隔不能太小
                        int ran= 4000+(int) (Math.random()*20000);
                        Thread.sleep(ran);
                        downloadFile(ts, file, videoUrl);
                    }
                } else if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
//                    Log.e(TAG, "====retry1   responseCode=" + responseCode + "  ts:" + ts.getUrl());

                    downloadFile(ts, file, videoUrl);
                }
            }


        } catch (InterruptedIOException e) {
            //被中断了，使用stop时会抛出这个，不需要处理
//            Log.e(TAG, "InterruptedIOException" );

        } catch (Exception e) {
            e.printStackTrace();

            ts.setRetryCount(ts.getRetryCount() + 1);
            if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
//                Log.e(TAG, "====retry, exception=" + e.getMessage());
                downloadFile(ts, file, videoUrl);
//                Log.e("asdf","重试");
            }else {
//                Log.e("asdf","失败");
            }
        } finally {
            ProxyCacheUtils.close(inputStream);
            ProxyCacheUtils.close(fos);
            ProxyCacheUtils.close(response);
            ProxyCacheUtils.close(rbc);
            ProxyCacheUtils.close(foutc);
        }


    }


    private void notifyCacheProgress() {
        Log.i(TAG,"notifyCacheProgress");
        updateM3U8TsInfo();
        if (mCachedSegCount > mTotalSegCount) {
            mCachedSegCount = mTotalSegCount;
        }
        mCacheInfo.setCachedTs(mCachedSegCount);
        mCacheInfo.setTsLengthMap(mSegLengthMap);
        mCacheInfo.setCachedSize(mCachedSize);
        float percent = mCachedSegCount * 1.0f * 100 / mTotalSegCount;

        if (!ProxyCacheUtils.isFloatEqual(percent, mPercent)) {
            long nowTime = System.currentTimeMillis();
            if (mCachedSize > mLastCachedSize && nowTime > mLastInvokeTime) {
                mSpeed = (mCachedSize - mLastCachedSize) * 1000 * 1.0f / (nowTime - mLastInvokeTime);
            }
            mListener.onM3U8TaskProgress(percent, mCachedSize, mSpeed, mSegLengthMap);
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
                mSegLengthMap.put(index, tempTsFile.length());
                tempCachedSize += tempTsFile.length();
                tempCachedTs++;
            }
        }
        mCachedSegCount = tempCachedTs;
        mCachedSize = tempCachedSize;
    }
}
