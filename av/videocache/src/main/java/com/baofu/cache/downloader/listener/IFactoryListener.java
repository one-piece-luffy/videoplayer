package com.baofu.cache.downloader.listener;

public interface IFactoryListener {
    void onError(Exception e);

    void onFinish();

    void onProgress(long progress,long total,boolean hasFileLength);

    void onReset();

}
