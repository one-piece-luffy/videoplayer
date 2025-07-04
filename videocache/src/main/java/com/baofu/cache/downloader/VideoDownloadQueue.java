package com.baofu.cache.downloader;

import com.baofu.cache.downloader.model.CacheTaskItem;
import com.baofu.cache.downloader.model.VideoTaskState;
import com.baofu.cache.downloader.utils.LogUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom Download Queue.
 */
public class VideoDownloadQueue {

    private static final String TAG = "VideoDownloadQueue";

    private CopyOnWriteArrayList<CacheTaskItem> mQueue;

    public VideoDownloadQueue() {
        mQueue = new CopyOnWriteArrayList<>();
    }

    public List<CacheTaskItem> getDownloadList() {
        return mQueue;
    }

    //put it into queue
    public void offer(CacheTaskItem taskItem) {
        mQueue.add(taskItem);
    }

    //Remove Queue head item,
    //Return Next Queue head.
    public CacheTaskItem poll() {
        try {
            if (mQueue.size() >= 1) {
                CacheTaskItem item=mQueue.get(0);
                mQueue.remove(0);
                return item;
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue remove failed.");
        }
        return null;
    }

    public CacheTaskItem peek() {
        try {
            if (mQueue.size() >= 1) {
                return mQueue.get(0);
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue get failed.");
        }
        return null;
    }

    public boolean remove(CacheTaskItem taskItem) {
        if (contains(taskItem)) {
            return mQueue.remove(taskItem);
        }
        return false;
    }

    public boolean contains(CacheTaskItem taskItem) {
        return mQueue.contains(taskItem);
    }

    public CacheTaskItem getTaskItem(String url) {
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                CacheTaskItem taskItem = mQueue.get(index);
                if (taskItem != null && taskItem.getUrl() != null &&
                        taskItem.getUrl().equals(url)) {
                    return taskItem;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getTaskItem failed.");
        }
        return null;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return mQueue.size();
    }

    public boolean isHead(CacheTaskItem taskItem) {
        if (taskItem == null)
            return false;
        return taskItem.equals(peek());
    }

    public int getRunningCount() {
        int count = 0;
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (isTaskRunnig(mQueue.get(index))) {
                    count++;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getDownloadingCount failed.");
        }
        return count;
    }
    public int getDownloadingCount() {
        int count = 0;
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (isTaskDownloading(mQueue.get(index))) {
                    count++;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getDownloadingCount failed.");
        }
        return count;
    }


    public int getPendingCount() {
        int count = 0;
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (isTaskPending(mQueue.get(index))) {
                    count++;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getDownloadingCount failed.");
        }
        return count;
    }

    public int getStartCount() {
        int count = 0;
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (isTaskStart(mQueue.get(index))) {
                    count++;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getDownloadingCount failed.");
        }
        return count;
    }

    public CacheTaskItem peekPendingTask() {
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                CacheTaskItem taskItem = mQueue.get(index);
                if (isTaskPending(taskItem)) {
                    return taskItem;
                }
            }
        } catch (Exception e) {
            LogUtils.w(TAG, "DownloadQueue getDownloadingCount failed.");
        }
        return null;
    }

    public boolean isTaskPending(CacheTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return taskState == VideoTaskState.PENDING ||
                taskState == VideoTaskState.PREPARE;
    }
    public boolean isTaskStart(CacheTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return taskState == VideoTaskState.START;
    }

    public boolean isTaskRunnig(CacheTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return taskState == VideoTaskState.START ||
                taskState == VideoTaskState.DOWNLOADING;
    }
    public boolean isTaskDownloading(CacheTaskItem taskItem) {
        if (taskItem == null)
            return false;
        int taskState = taskItem.getTaskState();
        return   taskState == VideoTaskState.DOWNLOADING;
    }
}
