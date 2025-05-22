package com.jeffmony.videocache.task;

import android.text.TextUtils;
import android.util.Log;

import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.m3u8.M3U8Seg;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.AES128Utils;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.OkHttpUtil;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;

public class M3U8CacheTask extends VideoCacheTask {

    private static final String TAG = "M3U8CacheTask";

    private static final String TEMP_POSTFIX = ".download";

    private static final int THREAD_POOL_COUNT = 6;
    private static final int CONTINUOUS_SUCCESS_TS_THRESHOLD = 6;
    private volatile int mM3U8DownloadPoolCount;
    private volatile int mContinuousSuccessSegCount;   //连续请求分片成功的个数

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
        //todo 这里的逻辑有问题，假如第一次播放视频，seek进度条，没完成缓存就退出；那么下次再进入播放时，计算的下载起点是不靠谱的；必须要获取到当前播放请求index
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
        LogUtils.i(TAG, "seekToCacheTaskFromServer segIndex="+segIndex);
        pauseCacheTask();
        startRequestVideoRange(segIndex);
    }

    @Override
    public void seekToCacheTaskFromServer(int segIndex, long time) {

    }

    private void startRequestVideoRange(int curTs) {
        if (mCacheInfo.isCompleted()) {
            notifyOnTaskCompleted();
            return;
        }
        if (isTaskRunning()) {
            //已经存在的任务不需要重新创建了
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
        for (int index = curTs; index < mTotalSegCount; index++) {
            final M3U8Seg seg = mSegList.get(index);
            mTaskExecutor.execute(() -> {
                try {
                    startDownloadSegTask(seg);
                } catch (Exception e) {
                    LogUtils.w(TAG, "M3U8 ts video download failed, exception=" + e);
                    notifyOnTaskFailed(e);
                }
            });
        }
    }

    private void startDownloadSegTask(M3U8Seg seg) throws Exception {
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
        if (exist && length > 0 &&length==contentLength) {
            //只有这样的情况下才能保证当前的ts文件真正被下载下来了
            seg.setFileSize(segFile.length());
            //更新进度
            notifyCacheProgress();
            Log.e(TAG,"notifyCacheProgress:"+segFile.getName()+" length:"+segFile.length());
        }else {
            Log.e(TAG,"文件大小不一致:"+segFile.getName()+" length:"+length+" contentlength:"+contentLength);
        }
    }

    public void downloadFile(M3U8Seg ts, File file, String videoUrl) {
//        Log.e(TAG,"队列开始下载ts");
        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response=null;
        File tmpFile = new File(file.getParentFile(), file.getName() + TEMP_POSTFIX);

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
                            fileOutputStream = new FileOutputStream(file);//todo oom
                            fileOutputStream.write(result);
                            //解密后文件的大小和content-length不一致，所以直接赋值为文件大小
                            contentLength = file.length();
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
                    fos = new FileOutputStream(tmpFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                    /**
                     *  todo
                     *  这里需要引入临时文件，下载完成后再重命名为原来的名字，不然 M3U8SegResponse的sendBody()的判断会出问题
                     *  sendBody 监听文件是否存在，只要文件存在就将数据发给播放器，这时候的文件可能是不完整的。所以这里等全部下载完成再重命名。
                     *  这时候sendBody监听到的就是完整的文件。
                     */
                    FileUtils.rename(tmpFile,file);
                    if (contentLength <= 0) {
                        contentLength = file.length();
                    }
                }


                ts.setContentLength(contentLength);
                Log.d(TAG,"队列ts下载完成:"+ts.getSegName());
                if (ts.getSegIndex() == 0) {
                    Log.e(TAG, "首个片段已经下载 " + file.getName()+ ", url=" + ts.getUrl());
                }
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
            Log.e(TAG, "InterruptedIOException" );

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "ts下载出错了",e );
            ts.setRetryCount(ts.getRetryCount() + 1);
//            if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
////                Log.e(TAG, "====retry, exception=" + e.getMessage());
//                downloadFile(ts, file, videoUrl);//todo oom
////                Log.e("asdf","重试");
//            }else {
////                Log.e("asdf","失败");
//            }
        } finally {
            ProxyCacheUtils.close(inputStream);
            ProxyCacheUtils.close(fos);
            ProxyCacheUtils.close(response);
            ProxyCacheUtils.close(rbc);
            ProxyCacheUtils.close(foutc);
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
