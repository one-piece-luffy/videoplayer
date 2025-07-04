package com.baofu.cache.downloader.factory;

public interface IDownloadFactory {
    public void download();
    public void cancel();
    public void pause();
    public void resetStutus();
    public void delete();
}
