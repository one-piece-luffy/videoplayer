package com.baofu.cache.downloader.process;

public interface IM3U8MergeListener {

    void onMergedFinished();

    void onMergeFailed(Exception e);
}
