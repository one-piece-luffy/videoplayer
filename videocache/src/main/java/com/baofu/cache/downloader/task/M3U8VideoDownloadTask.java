package com.baofu.cache.downloader.task;

import static com.baofu.cache.downloader.common.VideoDownloadConstants.MAX_RETRY_COUNT_503;

import android.text.TextUtils;
import android.util.Log;

import com.baofu.cache.downloader.VideoDownloadException;
import com.baofu.cache.downloader.m3u8.M3U8;
import com.baofu.cache.downloader.m3u8.M3U8Constants;
import com.baofu.cache.downloader.m3u8.M3U8Seg;
import com.baofu.cache.downloader.model.VideoTaskItem;
import com.baofu.cache.downloader.rules.CacheDownloadManager;
import com.baofu.cache.downloader.utils.DownloadExceptionUtils;
import com.baofu.cache.downloader.utils.HttpUtils;
import com.baofu.cache.downloader.utils.OkHttpUtil;
import com.baofu.cache.downloader.utils.VideoDownloadUtils;
import com.baofu.cache.downloader.utils.VideoStorageUtils;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.utils.AES128Utils;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Response;

public class M3U8VideoDownloadTask extends VideoDownloadTask {

    private static final String TAG = "M3U8VideoDownloadTask";
    private final Object mFileLock = new Object();
    private final Object mCreateFileLock = new Object();

    private final M3U8 mM3U8;
    private final List<M3U8Seg> mTsList;
    private final AtomicInteger mCurTs = new AtomicInteger(0);
    private final int mTotalTs;
    private long mTotalSize;
    //ts下载失败的个数
    private final AtomicInteger mErrorTsCont = new AtomicInteger(0);
    private Timer netSpeedTimer;//定时任务
    private final AtomicLong mCurrentDownloaddSize = new AtomicLong(0);//当前的下载大小
    AtomicBoolean isRunning = new AtomicBoolean(false);//任务是否正在运行中
    String fileName;
    //存储下载失败的错误信息
    Map<String,String> errMsgMap=new ConcurrentHashMap<>();
    final int MAX_ERR_MAP_COUNT = 3;

    final int MAX_THREAD_COUNT = 4;
    final int MIN_THREAD_COUNT = 2;

    public M3U8VideoDownloadTask(VideoTaskItem taskItem, M3U8 m3u8) {
        super(taskItem);
        mM3U8 = m3u8;
        mTsList = m3u8.getTsList();
        mTotalTs = mTsList.size();
        mPercent = taskItem.getPercent();
        Map<String,String> header=VideoDownloadUtils.getTaskHeader(taskItem);
        if(header!=null){
            header.put("Connection", "close");
            mTaskItem.header=VideoDownloadUtils.mapToJsonString(header);
        }
        mTaskItem.setTotalTs(mTotalTs);
        mTaskItem.setCurTs(mCurTs.get());

        if (mTaskItem.estimateSize > 0) {
            //暂时把预估大小设置为文件的总大小，等下载完成后再更新准确的总大小
            mTaskItem.setTotalSize(taskItem.estimateSize);
        }
    }

    private void initM3U8Ts() {
        if (mCurTs.get() == mTotalTs) {
            mTaskItem.setIsCompleted(true);
        }
        mTaskItem.suffix = ".m3u8";
        mCurrentDownloaddSize.set(0);
        mCurTs.set(0);
        fileName = VideoDownloadUtils.getFileNameWithSuffix(mTaskItem);
    }


    @Override
    public void startDownload() {
        if(mDownloadTaskListener!=null){
            mDownloadTaskListener.onTaskStart(mTaskItem.getUrl());
        }

        initM3U8Ts();
        begin();
    }

    private void begin() {
        if (mTaskItem.isCompleted()) {
            Log.i(TAG, "M3U8VideoDownloadTask local file.");
            notifyDownloadFinish();
            return;
        }
        if (isRunning.get())
            return;
        netSpeedTimer = new Timer();
        netSpeedTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                notifyProgress();
            }
        }, 0, 1000);

        if (mDownloadExecutor != null) {
            mDownloadExecutor.shutdownNow();
        }
        mDownloadExecutor = null;
        mDownloadExecutor = new ThreadPoolExecutor(MIN_THREAD_COUNT, MIN_THREAD_COUNT, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        //任务过多后，存储任务的一个阻塞队列
//        mDownloadExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
        isRunning.set(true);
        new Thread() {
            @Override
            public void run() {
                float length = 0;
                for (int index = 0; index < mTotalTs; index++) {
                    final M3U8Seg ts = mTsList.get(index);
                    File tempTsFile = new File(mSaveDir, ts.getSegName());
                    if (tempTsFile.exists()) {
                        if (tempTsFile.length() > 0) {
                            ts.setTsSize(tempTsFile.length());
                            ts.setContentLength(tempTsFile.length());
                            ts.success = true;
                            mCurrentDownloaddSize.getAndAdd(ts.getTsSize());

                        } else {
                            VideoStorageUtils.deleteFile2(tempTsFile);
                        }

                    }
                    length += ts.getDuration();
                }
                mTaskItem.videoLength = (long) length;
                Log.e(TAG, "已下载的大小:" + mCurrentDownloaddSize.get());




                for (int index = 0; index < mTotalTs; index++) {
                    final M3U8Seg ts = mTsList.get(index);
                    if(ts.success){
                        File tempTsFile = new File(mSaveDir, ts.getSegName());
                        if (tempTsFile.exists()&&tempTsFile.length() > 0) {
                            mCurTs.incrementAndGet();
                            continue;
                        }
                    }


                    mDownloadExecutor.execute(() -> {
                        if (ts.hasInitSegment()) {
                            String tsInitSegmentName = ts.getInitSegmentName();
                            File tsInitSegmentFile = new File(mSaveDir, tsInitSegmentName);
                            if (!tsInitSegmentFile.exists() || tsInitSegmentFile.length() == 0) {
                                Log.e(TAG, "===================出大事了===============");
                                Log.e(TAG, "===================出大事了===============");
                                Log.e(TAG, "===================出大事了===============");
                                try {
                                    downloadFile(ts, tsInitSegmentFile, ts.getInitSegmentUri());
                                } catch (Exception e) {
                                    Log.e(TAG, "出错了", e);
                                }

                            }
                        }
                        File tsFile = new File(mSaveDir, ts.getSegName());
                        if (!tsFile.exists() || tsFile.length() == 0) {
                            // ts is network resource, download ts file then rename it to local file.
                            try {
                                downloadFile(ts, tsFile, ts.getUrl());
                            } catch (Exception e) {
                                Log.e(TAG, "出错了", e);
                            }
                        }

                        //下载失败的比例超过30%则不再下载，直接提示下载失败
                        if (mErrorTsCont.get() * 100 / mTotalTs > 25) {
                            StringBuilder err = new StringBuilder();

                            Set<String> keySet = errMsgMap.keySet();
                            int i = 0;
                            for (String key : keySet) {
                                i++;
                                err.append("errNum ").append(i).append(":").append(key).append("  ");
                            }
                            Log.e(TAG, "错误的ts超过30%: " + err);
                            if (isRunning.get()) {
                                notifyDownloadError(new VideoDownloadException("m3u8:" + err));
                            }

                        }
                    });


                }
                if (mDownloadExecutor != null) {
                    mDownloadExecutor.shutdown();//下载完成之后要关闭线程池
                }
                while (mDownloadExecutor != null && !mDownloadExecutor.isTerminated()) {

                    try {
                        //等待中
                        Thread.sleep(1500);
                    } catch (Exception e) {
                        Log.e(TAG, "发生异常: ", e);
                    }

                    try {
                        ThreadPoolExecutor tpe = ((ThreadPoolExecutor) mDownloadExecutor);
                        int queueSize = tpe.getQueue().size();
                        int activeCount = tpe.getActiveCount();
                        long completedTaskCount = tpe.getCompletedTaskCount();
                        long taskCount = tpe.getTaskCount();
                        Log.e(TAG, mTaskItem.mName+" 当前排队线程数：" + queueSize + " 当前活动线程数：" + activeCount + " 执行完成线程数：" + completedTaskCount + " 总线程数：" + taskCount);
                    } catch (Exception e) {
                        Log.e(TAG, "发生异常: ", e);
                    }

                }


                Log.e(TAG, "开始创建本地文件==============");
                synchronized (mCreateFileLock) {
                    if (isRunning.get()) {
                        isRunning.set(false);
                        try {
                            createLocalM3U8File();
                        } catch (Exception e) {
                            Log.e(TAG, "创建本地文件失败");
                            notifyDownloadError(new VideoDownloadException("m3u8:创建本地文件失败"));
                            return;
                        }
                        stopTimer();

                        mTotalSize = mCurrentDownloaddSize.get();
                        Log.i(TAG, "下载完成:" + mTotalSize);
                        if (mDownloadTaskListener != null) {
                            mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mTotalSize, mCurTs.get(), mTotalTs, mSpeed);
                        }
                        notifyDownloadFinish();


                        mCurrentCachedSize = VideoStorageUtils.countTotalSize(mSaveDir);
                        Log.i(TAG, "文件目录大小:" + VideoDownloadUtils.getSizeStr(mCurrentCachedSize));



                    }
                }

            }
        }.start();
    }



    @Override
    public void resumeDownload() {
        startDownload();
    }

    @Override
    public void pauseDownload() {
        Log.e(TAG, "==========暂停下载===========:"+mTaskItem.mName);

        new Thread() {
            @Override
            public void run() {
                stopTimer();
                isRunning.set(false);
                notifyOnTaskPaused();
                if (mDownloadExecutor != null) {
                    Log.i(TAG, "mDownloadExecutor shutdownNow");
                    try {
                        mDownloadExecutor.shutdownNow();
                    } catch (Exception e) {
                        Log.e(TAG, "发生异常: ", e); 
                    }

                }

            }
        }.start();

    }

    private void stopTimer() {
        if (netSpeedTimer != null) {
            netSpeedTimer.cancel();
            netSpeedTimer = null;
        }
    }

    @Override
    public void cancle() {
        new Thread() {
            @Override
            public void run() {
                Log.i(TAG, "cancle");
                isRunning.set(false);
                stopTimer();
                try {
                    if (mDownloadExecutor != null) {
                        mDownloadExecutor.shutdownNow();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "发生异常: ", e); 
                }

            }
        }.start();

    }

    @Override
    public void delete() {

    }

    @Override
    public void initSaveDir() {

        if (TextUtils.isEmpty(mTaskItem.getSaveDir())) {
            mSaveName = VideoDownloadUtils.getFileName(mTaskItem, null, false);
            mSaveDir = new File(CacheDownloadManager.getInstance().mConfig.privatePath, mSaveName);

            if (mSaveDir.exists()) {
                if (!mTaskItem.overwrite) {
                    mSaveName = VideoDownloadUtils.getFileName(mTaskItem, System.currentTimeMillis() + "", false);
                    mSaveDir = new File(CacheDownloadManager.getInstance().mConfig.privatePath, mSaveName);
                }
            }
            if (!mSaveDir.exists()) {
                mSaveDir.mkdir();
            }
        } else {
            mSaveDir = new File(mTaskItem.getSaveDir());
            mSaveName = mTaskItem.getFileHash();
        }

        mTaskItem.setFileHash(mSaveName);
        mTaskItem.setSaveDir(mSaveDir.getAbsolutePath());
    }

    private void notifyProgress() {
        //未获得下载大小前不更新进度
        if (mCurrentDownloaddSize.get() == 0) {
            return;
        }
        if (mTaskItem.isCompleted()) {
            mCurTs.set(mTotalTs);
            if(mDownloadTaskListener!=null){

                mDownloadTaskListener.onTaskProgressForM3U8(100.0f, mCurrentDownloaddSize.get(), mCurTs.get(), mTotalTs, mSpeed);
            }
            mPercent = 100.0f;
            mTotalSize = mCurrentDownloaddSize.get();
            notifyDownloadFinish();
            return;
        }
        if (mCurTs.get() >= mTotalTs) {
            mCurTs.set(mTotalTs);
        }
        float percent = mCurTs.get() * 1.0f * 100 / mTotalTs;
        if (!VideoDownloadUtils.isFloatEqual(percent, mPercent) && mCurrentDownloaddSize.get() > mLastCachedSize) {
            long nowTime = System.currentTimeMillis();
            mSpeed = (mCurrentDownloaddSize.get() - mLastCachedSize)   / ((nowTime - mLastInvokeTime)/1000f);
            if(mDownloadTaskListener!=null){
                mDownloadTaskListener.onTaskProgressForM3U8(percent, mCurrentDownloaddSize.get(), mCurTs.get(), mTotalTs, mSpeed);
            }
            mPercent = percent;

            mLastCachedSize = mCurrentDownloaddSize.get();
            mLastInvokeTime = nowTime;
            Log.i(TAG, mTaskItem.mName+" m3u8  cur:" + mCurTs + " error count:" + mErrorTsCont + " mTotalTs:" + mTotalTs);

        }
    }


    private void notifyDownloadFinish() {
        stopTimer();
        mTaskItem.setFilePath(mTaskItem.getSaveDir() + File.separator + fileName);
        if(mDownloadTaskListener!=null){
            mDownloadTaskListener.onTaskFinished(mTotalSize);
        }
    }

    private void notifyDownloadError(Exception e) {
        Log.e(TAG, "notifyDownloadError:" + e.getMessage());
        stopTimer();
        cancle();
        notifyOnTaskFailed(e);
    }

    public void downloadFile(M3U8Seg ts, File file, String videoUrl) {
        Log.e(TAG,mTaskItem.mName+" 队列开始下载ts:"+file.getName());
        if (CacheDownloadManager.getInstance().mDownloadReplace != null) {
            for (Map.Entry<String, String> entry : (Iterable<Map.Entry<String, String>>) CacheDownloadManager.getInstance().mDownloadReplace.entrySet()) {
                if (videoUrl.contains(entry.getKey())) {
                    videoUrl = videoUrl.replaceAll(entry.getKey(), entry.getValue());
                    break;
                }
            }
        }
        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        int responseCode = -1;
        try {
            String method = OkHttpUtil.METHOD.GET;
            if (OkHttpUtil.METHOD.POST.equalsIgnoreCase(mTaskItem.method)) {
                method = OkHttpUtil.METHOD.POST;
            }
            response = OkHttpUtil.getInstance().requestSync(videoUrl,method, VideoDownloadUtils.getTaskHeader(mTaskItem));

            if (response != null) {
                responseCode = response.code();
            }
            if (response!=null&& response.isSuccessful()) {
                ts.setRetryCount(0);
                inputStream = response.body().byteStream();
                long contentLength = response.body().contentLength();

                byte[] encryptionKey = ts.encryptionKey == null ? mM3U8.encryptionKey : ts.encryptionKey;
                String iv = ts.encryptionKey == null ? mM3U8.encryptionIV : ts.getKeyIV();
                if (encryptionKey != null) {
                    String tsInitSegmentName = ts.getInitSegmentName() + ".temp";
                    File tsInitSegmentFile = new File(mSaveDir, tsInitSegmentName);

                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tsInitSegmentFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);

                    FileOutputStream fileOutputStream = null;
                    try {
                        byte[] result = AES128Utils.dencryption(AES128Utils.readFile(tsInitSegmentFile), encryptionKey, iv);
                        if (result == null) {
                            // aes解密失败,这里的失败不用重试，重试也是失败
                            ts.failed = true;
                            ts.setRetryCount(ts.getRetryCount()+1);
                            mErrorTsCont.incrementAndGet();
                            String err = "aes dencryption  fail";
                            if (errMsgMap.size() < MAX_ERR_MAP_COUNT) {
                                errMsgMap.put(err, err);
                            }
                        } else {
                            fileOutputStream = new FileOutputStream(file);
                            fileOutputStream.write(result);
                            //解密后文件的大小和content-length不一致，所以直接赋值为文件大小
                            contentLength = file.length();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "发生异常: ", e); 
                        ts.setRetryCount(ts.getRetryCount() + 1);
                        if (ts.getRetryCount() <= CacheDownloadManager.getInstance().mConfig.retryCount) {
                            Log.e(TAG, "====retry, exception=" + e.getMessage());
                            downloadFile(ts, file, videoUrl);
                        } else {
                            ts.failed = true;
                            mErrorTsCont.incrementAndGet();
                            if (errMsgMap.size() < MAX_ERR_MAP_COUNT) {
                                errMsgMap.put(e.getMessage(), e.getMessage());
                            }
                        }
                        return;
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                            VideoStorageUtils.delete(tsInitSegmentFile);
                        }
                    }
                } else {
                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(file);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                    if (contentLength <= 0) {
                        contentLength = file.length();
                    }
                }


                if (contentLength == 0) {
                    onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception("file length = 0 or code=" + responseCode));
                } else {
                    ts.setContentLength(contentLength);
                    ts.setTsSize(contentLength);
//                    Log.e("asdf","content length:"+contentLength+"  str:"+VideoDownloadUtils.getSizeStr((contentLength)));
                    mCurrentDownloaddSize.getAndAdd(contentLength);
                    mCurTs.incrementAndGet();
                    ts.success = true;

                    if (ts.mSegIndex == 0 && mDownloadTaskListener != null) {
                        mDownloadTaskListener.onTaskFirstTsDownload(mTaskItem);
//                    Log.e(TAG, "首个片段已经下载 " + fileName+ ", url=" + ts.getUrl());
                    }
                }

            } else {
                onDownloadFileErr(ts, file, videoUrl, responseCode, new Exception("response is null or code=" + responseCode));
            }
//            if (mDownloadExecutor != null) {
//                if (VideoDownloadManager.getInstance().mConfig.threadSchedule) {
//                    //线程调度
//                    if (mTaskItem.mUrl.equals(VideoDownloadManager.getInstance().curPlayUrl)) {
//                        mDownloadExecutor.setCorePoolSize(MAX_THREAD_COUNT);
//                        mDownloadExecutor.setMaximumPoolSize(MAX_THREAD_COUNT);
//
//                    } else {
//                        mDownloadExecutor.setCorePoolSize(MIN_THREAD_COUNT);
//                        mDownloadExecutor.setMaximumPoolSize(MIN_THREAD_COUNT);
//                    }
//                }
//            }

        } catch (InterruptedIOException e) {
            //被中断了，使用stop时会抛出这个，不需要处理
            Log.e(TAG, "InterruptedIOException");
        } catch (Exception e) {
            onDownloadFileErr(ts,file,videoUrl,responseCode,e);
        } finally {
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(fos);
            if (response != null) {
                VideoDownloadUtils.close(response.body());
            }
            if (rbc != null) {
                try {
                    rbc.close();
                } catch (IOException e) {
                    Log.e(TAG, "发生异常: ", e); 

                }
            }
            if (foutc != null) {
                try {
                    foutc.close();
                } catch (IOException e) {
                    Log.e(TAG, "发生异常: ", e); 
                }
            }
        }

    }

    private void onDownloadFileErr(M3U8Seg ts, File file, String videoUrl,int responseCode,Exception exception){
        ts.setRetryCount(ts.getRetryCount() + 1);
        if (responseCode == HttpUtils.RESPONSE_503 || responseCode == HttpUtils.RESPONSE_429) {
            if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
                //遇到503，延迟[4,24]秒后再重试，区间间隔不能太小
                int ran = 4000 + (int) (Math.random() * 20000);
                try {
                    Thread.sleep(ran);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                downloadFile(ts, file, videoUrl);
            }
        } else if (ts.getRetryCount() <= CacheDownloadManager.getInstance().mConfig.retryCount) {
            Log.e(TAG, "====retry1   responseCode=" + responseCode + " msg:"+ exception.getMessage()+ "  ts:" + ts.getUrl()+" "+file.getName()+" count:"+ts.getRetryCount());

            downloadFile(ts, file, videoUrl);
        } else {
            Log.e(TAG, "====error   responseCode=" + responseCode+ " msg:"+ exception.getMessage() + "  ts:" + ts.getUrl()+" "+file.getName()+" count:"+ts.getRetryCount());
            ts.failed = true;
            mErrorTsCont.incrementAndGet();
            String err;
            if (exception == null) {
                err = "code:" + responseCode;
            } else {
                err = exception.getMessage();
            }
            if (errMsgMap.size() < MAX_ERR_MAP_COUNT) {
                errMsgMap.put(err,err);
            }
        }
    }

    private void saveFile(InputStream inputStream, File file, long contentLength, M3U8Seg ts, String videoUrl) {
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        long totalLength = 0;
        int bufferSize = 1024 * 1024 * 2;
        int position = 0;
        int mCurrentLength = 0;
        try {

            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            int len;
            byte[] data = new byte[1024 << 3];
            byte[] buffer = new byte[bufferSize << 1];

            while ((len = inputStream.read(data)) != -1) {
                totalLength +=  len;
                mCurrentLength += len;
                System.arraycopy(data, 0, buffer, position, data.length);
                position += len;
                if (mCurrentLength >= bufferSize) {
                    bos.write(buffer, 0, mCurrentLength);
                    position = 0;
                    mCurrentLength = 0;
                    buffer = new byte[bufferSize << 1];
                }
            }
            if (mCurrentLength > 0) {
                bos.write(buffer, 0, mCurrentLength);
//                bos.flush();
            }
            ts.setContentLength(totalLength);
            ts.setTsSize(totalLength);


        } catch (InterruptedIOException e) {
            //被中断了，使用stop时会抛出这个，不需要处理
        } catch (IOException e) {
            if (file.exists() && ((contentLength > 0 && contentLength == file.length()) || (contentLength == -1 && totalLength == file.length()))) {
                //这时候也能说明ts已经下载好了
            } else {
                if ((e instanceof ProtocolException &&
                        !TextUtils.isEmpty(e.getMessage()) &&
                        e.getMessage().contains(DownloadExceptionUtils.PROTOCOL_UNEXPECTED_END_OF_STREAM)) &&
                        (contentLength > totalLength && totalLength == file.length())) {
                    if (file.length() == 0) {
                        ts.setRetryCount(ts.getRetryCount() + 1);
                        if (ts.getRetryCount() < HttpUtils.MAX_RETRY_COUNT) {
                            downloadFile(ts, file, videoUrl);
                        } else {
                            Log.e(TAG, file.getAbsolutePath() + ", length=" + file.length() + " contentLength=" + contentLength + ", saveFile failed1, exception=" + e);
                            if (file.exists()) {
                                VideoStorageUtils.deleteFile2(file);
                            }
                            ts.failed = true;
                            mErrorTsCont.incrementAndGet();
                        }
                    } else {
                        ts.setContentLength(totalLength);
                    }
                } else {
                    Log.e(TAG, file.getAbsolutePath() + ", length=" + file.length() + " contentLength=" + contentLength + ", saveFile failed2, exception=" + e);
                    if (file.exists()) {
                        VideoStorageUtils.deleteFile2(file);
                    }
                    ts.failed = true;
                    mErrorTsCont.incrementAndGet();
                }
            }
        } finally {
            VideoDownloadUtils.close(inputStream);
            VideoDownloadUtils.close(fos);
            VideoDownloadUtils.close(bos);
        }
    }

    /**
     * 创建本地m3u8文件，可用于离线播放
     */
    private void createLocalM3U8File() throws IOException {
        synchronized (mFileLock) {
            File tempM3U8File = new File(mSaveDir, "temp.m3u8");
            if (tempM3U8File.exists()) {
                VideoStorageUtils.deleteFile2(tempM3U8File);
            }
            Log.i(TAG, "createLocalM3U8File");

            BufferedWriter bfw = new BufferedWriter(new FileWriter(tempM3U8File, false));
            bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
            bfw.write(M3U8Constants.TAG_VERSION + ":" + mM3U8.getVersion() + "\n");
            bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + mM3U8.getInitSequence() + "\n");

            bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + mM3U8.getTargetDuration() + "\n");

            for (M3U8Seg m3u8Ts : mTsList) {
                if (m3u8Ts.failed || m3u8Ts.getTsSize() == 0) {
                    continue;
                }
                if (m3u8Ts.hasInitSegment()) {
                    String initSegmentInfo;
                    String initSegmentFilePath = mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getInitSegmentName();
                    if (m3u8Ts.getSegmentByteRange() != null) {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath + "\"" + ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() + "\"";
                    } else {
                        initSegmentInfo = "URI=\"" + initSegmentFilePath + "\"";
                    }
                    bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
                }
                if (m3u8Ts.hasKey() ) {
                    if (m3u8Ts.getMethod() != null) {
                        String key = "METHOD=" + m3u8Ts.getMethod();
                        if (m3u8Ts.getKeyUri() != null) {
                            File keyFile = new File(mSaveDir, m3u8Ts.getLocalKeyUri());
                            if (!m3u8Ts.isMessyKey() && keyFile.exists() && keyFile.length() > 0) {
                                key += ",URI=\"" + keyFile.getAbsolutePath() + "\"";
                            } else {
                                key += ",URI=\"" + m3u8Ts.getKeyUri() + "\"";
                            }
                        }
                        if (m3u8Ts.getKeyIV() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIV();
                        }
                        bfw.write(M3U8Constants.TAG_KEY + ":" + key + "\n");
                    }
                }
                if (m3u8Ts.hasDiscontinuity()) {
                    bfw.write(M3U8Constants.TAG_DISCONTINUITY + "\n");
                }
                bfw.write(M3U8Constants.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() + ",\n");
                bfw.write(mSaveDir.getAbsolutePath() + File.separator + m3u8Ts.getSegName());
                bfw.newLine();
            }
            bfw.write(M3U8Constants.TAG_ENDLIST);
            bfw.flush();
            bfw.close();

//            File localM3U8File = new File(mSaveDir, mSaveName + "_" + VideoDownloadUtils.LOCAL_M3U8);
            File localM3U8File = new File(mSaveDir, fileName);
            if (localM3U8File.exists()) {
                VideoStorageUtils.deleteFile2(localM3U8File);

            }
            tempM3U8File.renameTo(localM3U8File);
        }
    }


}

