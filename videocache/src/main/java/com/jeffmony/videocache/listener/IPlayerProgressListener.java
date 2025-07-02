package com.jeffmony.videocache.listener;

/**
 * 播放器解析进度监听
 */
public interface IPlayerProgressListener {

    /**
     * 回调在子线程
     * 缓存任务第一个ts下载成功
     */
    void onTaskFirstTsDownload(String filename);
    /**
     * 回调在子线程
     * 播放器的第一个ts下载成功
     */
    void onPlayerFirstTsDownload(String filename);
    /**
     * 回调在主线程
     * 播放器的第一个ts下载成功
     */
    void onM3U8ParsedFailed(String error);

    /**
     * 播放器解析进度log
     * @param log
     */
    void playerCacheLog(String log);

}
