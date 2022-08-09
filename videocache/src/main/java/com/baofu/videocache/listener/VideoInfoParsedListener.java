package com.baofu.videocache.listener;

import com.baofu.videocache.common.VideoCacheException;
import com.baofu.videocache.m3u8.M3U8;
import com.baofu.videocache.model.VideoCacheInfo;

public abstract class VideoInfoParsedListener implements IVideoInfoParsedListener {


    @Override
    public void onM3U8ParsedFinished(M3U8 m3u8, VideoCacheInfo cacheInfo) {

    }

    @Override
    public void onM3U8ParsedFailed(VideoCacheException e, VideoCacheInfo cacheInfo) {

    }

    @Override
    public void onM3U8LiveCallback(VideoCacheInfo cacheInfo) {

    }

    @Override
    public void onNonM3U8ParsedFinished(VideoCacheInfo cacheInfo) {

    }

    @Override
    public void onNonM3U8ParsedFailed(VideoCacheException e, VideoCacheInfo cacheInfo) {

    }
}
