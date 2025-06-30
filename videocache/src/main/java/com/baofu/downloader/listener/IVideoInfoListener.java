package com.baofu.downloader.listener;

import com.baofu.downloader.m3u8.M3U8;
import com.baofu.downloader.model.VideoTaskItem;

public interface IVideoInfoListener {

    void onFinalUrl(String finalUrl);

    void onBaseVideoInfoSuccess(VideoTaskItem info);

    void onBaseVideoInfoFailed(Exception error);

    void onM3U8InfoSuccess(VideoTaskItem info, M3U8 m3u8);

    void onLiveM3U8Callback(VideoTaskItem info);

    void onM3U8InfoFailed(Exception error);
}
