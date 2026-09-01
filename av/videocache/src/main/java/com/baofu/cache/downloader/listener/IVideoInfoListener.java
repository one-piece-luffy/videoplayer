package com.baofu.cache.downloader.listener;

import com.baofu.cache.downloader.m3u8.M3U8;
import com.baofu.cache.downloader.model.CacheTaskItem;

public interface IVideoInfoListener {

    void onFinalUrl(String finalUrl);

    void onBaseVideoInfoSuccess(CacheTaskItem info);

    void onBaseVideoInfoFailed(Exception error);

    void onM3U8InfoSuccess(CacheTaskItem info, M3U8 m3u8);

    void onLiveM3U8Callback(CacheTaskItem info);

    void onM3U8InfoFailed(Exception error);
}
