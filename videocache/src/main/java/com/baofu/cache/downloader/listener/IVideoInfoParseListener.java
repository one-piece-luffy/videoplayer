package com.baofu.cache.downloader.listener;

import com.baofu.cache.downloader.m3u8.M3U8;
import com.baofu.cache.downloader.model.CacheTaskItem;

public interface IVideoInfoParseListener {

    void onM3U8FileParseSuccess(CacheTaskItem info, M3U8 m3u8);

    void onM3U8FileParseFailed(CacheTaskItem info, Throwable error);
}
