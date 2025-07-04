package com.jeffmony.videocache;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;


import androidx.annotation.NonNull;

import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.StorageUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StorageManager {

    private static final String TAG = "StorageManager";

    private static final int MSG_INIT_CACHE = 1;
    private static final int MSG_CHECK_CACHE = 2;

    private static volatile StorageManager sInstance = null;

    private HandlerThread mCacheCleanThread;
    private final VideoCacheCleanHandler mCacheCleanHandler;

    private String mRootFilePath;
    private long mMaxCacheSize;
    private long mMaxRemainingSize;    //限定一个保持的时间,不然频繁的删除会导致性能问题
    private long mExpiredTime;
    private long mCurrentSize;
    private LinkedHashMap<String, CacheFileInfo> mLruCache;

    private StorageManager() {
        mCacheCleanThread = new HandlerThread("VideoCacheClean:Handler", Process.THREAD_PRIORITY_BACKGROUND);
        mCacheCleanThread.start();
        mCacheCleanHandler = new VideoCacheCleanHandler(mCacheCleanThread.getLooper());

        mCurrentSize = 0;
        mLruCache = new LinkedHashMap<>();
    }

    private class VideoCacheCleanHandler extends Handler {
        public VideoCacheCleanHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_INIT_CACHE:
                    initCacheInfoInternal();
                    break;
                case MSG_CHECK_CACHE:
                    String filePath = (String) msg.obj;
                    checkCacheInternal(filePath);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 缓存文件的基本信息
     */
    private static class CacheFileInfo implements Comparable<CacheFileInfo>{
        public String mFilePath;
        public long mLastModified;
        public long mSize;

        public CacheFileInfo(String filePath, long lastModified, long size) {
            mFilePath = filePath;
            mLastModified = lastModified;
            mSize = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheFileInfo that = (CacheFileInfo) o;
            return Objects.equals(mFilePath, that.mFilePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mFilePath);
        }

        @Override
        public String toString() {
            return "CacheFileInfo{" +
                    "mFilePath='" + mFilePath + '\'' +
                    ", mLastModified=" + mLastModified +
                    ", mSize=" + mSize +
                    '}';
        }

        @Override
        public int compareTo(CacheFileInfo o) {
            return Long.compare(this.mLastModified, o.mLastModified);
        }
    }

    public static StorageManager getInstance() {
        if (sInstance == null) {
            synchronized (StorageManager.class) {
                if (sInstance == null) {
                    sInstance = new StorageManager();
                }
            }
        }
        return sInstance;
    }

    public void initCacheConfig(String rootFilePath, long maxCacheSize, long expiredTime) {
        mRootFilePath = rootFilePath;
        mMaxCacheSize = maxCacheSize;
        mExpiredTime = expiredTime;

        mMaxRemainingSize = (long) (0.8f * mMaxCacheSize);
    }

    public void initCacheInfo() {
        mCacheCleanHandler.obtainMessage(MSG_INIT_CACHE).sendToTarget();
    }

    private void initCacheInfoInternal() {
        mCurrentSize = 0;
        if (TextUtils.isEmpty(mRootFilePath)) return;
        File rootFile = new File(mRootFilePath);
        if (!rootFile.exists()) return;

        try {
            StorageUtils.cleanExpiredCacheData(rootFile, mExpiredTime);
        } catch (Exception e) {
            LogUtils.w(TAG, "cleanExpiredCacheData failed, exception = " + e.getMessage());
        }

        File[] files = rootFile.listFiles();
        if (files == null) return;
        List<File> result = Arrays.asList(files);
        Collections.sort(result, (o1, o2) -> {
            return Long.compare(o1.lastModified(), o2.lastModified());
        });
        for (File itemFile : result) {
            CacheFileInfo cacheFileInfo = new CacheFileInfo(itemFile.getAbsolutePath(), itemFile.lastModified(), StorageUtils.getTotalSize(itemFile));
            addCache(cacheFileInfo.mFilePath, cacheFileInfo);
        }
        trimCacheData();
    }

    private void addCache(String key, CacheFileInfo cacheFileInfo) {
        mCurrentSize += cacheFileInfo.mSize;
        mLruCache.put(key, cacheFileInfo);
    }

    private void removeCache(String key) {
        CacheFileInfo cacheFileInfo = mLruCache.remove(key);
        if (cacheFileInfo != null) {
            mCurrentSize -= cacheFileInfo.mSize;
        }
    }

    /**
     * 清理存储超限的缓存
     */
    private void trimCacheData() {
        Log.i(TAG, "=========mCurrentSize:" + (mCurrentSize / 1024 / 1024) + "MB");
        if (mCurrentSize > mMaxCacheSize) {
            Iterator<Map.Entry<String, CacheFileInfo>> iterator = mLruCache.entrySet().iterator();
            if (!iterator.hasNext()) return;

            int count=0;
            while (mCurrentSize > mMaxRemainingSize) {
                Map.Entry<String, CacheFileInfo> item = iterator.next();

                //最多保留一个,不能删除正在播放的视频
                String filePath = item.getKey();
                if (!TextUtils.isEmpty(VideoProxyCacheManager.getInstance().getPlayingUrlMd5())
                        && filePath.contains(VideoProxyCacheManager.getInstance().getPlayingUrlMd5())) {
                    LogUtils.i(TAG, "trimCacheData ignore playing video");
                    PlayerProgressListenerManager.getInstance().log("trimCacheData ignore playing video");
                } else {
                    CacheFileInfo cacheFileInfo = item.getValue();
                    File file = new File(filePath);
                    boolean deleted = StorageUtils.deleteFile(file);
                    if (deleted) {
                        mCurrentSize -= cacheFileInfo.mSize;
                        count++;
                        //不会存在多线程的操作情况
                        iterator.remove();
                    }
                }
                PlayerProgressListenerManager.getInstance().log("存储超限清理:"+count);
                if (!iterator.hasNext()) break;
            }
        }
    }

    /**
     * 播放过程中校验缓存数据,如果发现缓存数据超过特定的大小,还是要清理掉的
     * @param filePath
     */
    public void checkCache(String filePath) {
        Message message = mCacheCleanHandler.obtainMessage();
        message.obj = filePath;
        message.what = MSG_CHECK_CACHE;
        mCacheCleanHandler.sendMessage(message);
    }

    private void checkCacheInternal(String filePath) {
        if (TextUtils.isEmpty(filePath)) return;
        File file = new File(filePath);
        if (!file.exists()) return;

        try {
            StorageUtils.setLastModifiedTimeStamp(file);
        } catch (Exception e) {
            LogUtils.w(TAG, "setLastModifiedTimeStamp failed, exception="+e.getMessage());
        }

        removeCache(filePath);

        CacheFileInfo cacheFileInfo = new CacheFileInfo(filePath, file.lastModified(), StorageUtils.getTotalSize(file));
        addCache(filePath, cacheFileInfo);
        trimCacheData();
    }
}
