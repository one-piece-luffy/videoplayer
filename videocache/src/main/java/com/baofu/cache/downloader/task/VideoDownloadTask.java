package com.baofu.cache.downloader.task;

import android.util.Log;

import com.baofu.cache.downloader.listener.ICacheDownloadTaskListener;
import com.baofu.cache.downloader.model.CacheTaskItem;
import com.baofu.cache.downloader.utils.OkHttpUtil;
import com.baofu.cache.downloader.utils.VideoDownloadUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ThreadPoolExecutor;

import okhttp3.Response;

public abstract class VideoDownloadTask {
    public final String TAG=getClass().getSimpleName();
    //参数初始化
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //线程池最大容纳线程数
    public static final int maximumPoolSize = CPU_COUNT * 2 + 1;
    protected static final int THREAD_COUNT = maximumPoolSize;
    protected static final int BUFFER_SIZE = VideoDownloadUtils.DEFAULT_BUFFER_SIZE;
    protected final CacheTaskItem mTaskItem;
    protected final String mFinalUrl;
    protected File mSaveDir;
    protected String mSaveName;
    protected ThreadPoolExecutor mDownloadExecutor;
    protected ICacheDownloadTaskListener mDownloadTaskListener;
    protected long mLastCachedSize = 0L;
    protected long mCurrentCachedSize = 0L;
    protected long mLastInvokeTime = 0L;
    protected float mSpeed = 0.0f;
    protected float mPercent = 0.01f;
    protected float maxSpeed = 0;
    protected float minSpeed = 0;

    protected VideoDownloadTask(CacheTaskItem taskItem) {
        mTaskItem = taskItem;
        mFinalUrl = taskItem.getFinalUrl();
        initSaveDir();
    }

    public void setDownloadTaskListener(ICacheDownloadTaskListener listener) {
        mDownloadTaskListener = listener;
    }

    public abstract void startDownload();

    public abstract void resumeDownload();

    public abstract void pauseDownload();

    public abstract void cancle();

    public abstract void delete();

    protected void notifyOnTaskPaused() {
        if (mDownloadTaskListener != null) {
            mDownloadTaskListener.onTaskPaused();
        }
    }

    protected void notifyOnTaskFailed(Exception e) {

        if(mDownloadTaskListener!=null){
            mDownloadTaskListener.onTaskFailed(e);
        }
    }

    public abstract void initSaveDir();

    /**
     * 保存封面
     */
    public void downloadCover(File file, String url) {

        InputStream inputStream = null;
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        try {
            String method = OkHttpUtil.METHOD.GET;
            if (OkHttpUtil.METHOD.POST.equalsIgnoreCase(mTaskItem.method)) {
                method = OkHttpUtil.METHOD.POST;
            }
            response = OkHttpUtil.getInstance().requestSync(url, method, VideoDownloadUtils.getTaskHeader(mTaskItem));

            if (response != null && response.isSuccessful()) {
                inputStream = response.body().byteStream();

                rbc = Channels.newChannel(inputStream);
                fos = new FileOutputStream(file);
                foutc = fos.getChannel();
                foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                mTaskItem.setCoverPath(file.getAbsolutePath());

            }


        } catch (Exception e) {
            Log.e(TAG, "发生异常: ", e);
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
}
