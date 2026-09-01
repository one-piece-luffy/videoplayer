package com.baofu.cache.downloader.listener;

import com.baofu.cache.downloader.model.CacheTaskItem;

public interface ICacheDownloadListener {

    void onDownloadDefault(CacheTaskItem item);

    void onDownloadPending(CacheTaskItem item);

    void onDownloadPrepare(CacheTaskItem item);

    void onDownloadStart(CacheTaskItem item);

    void onDownloadProgress(CacheTaskItem item);

    void onDownloadSpeed(CacheTaskItem item);

    void onDownloadPause(CacheTaskItem item);

    void onDownloadError(CacheTaskItem item);

    void onDownloadSuccess(CacheTaskItem item);

    void onDownloadMerge(CacheTaskItem item);

    void onTaskFirstTsDownload(CacheTaskItem item);
}
