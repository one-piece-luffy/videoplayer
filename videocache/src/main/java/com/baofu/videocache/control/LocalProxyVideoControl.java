package com.baofu.videocache.control;

import android.util.Log;

import com.baofu.videocache.VideoProxyCacheManager;
import com.baofu.videocache.common.VideoParams;
import com.baofu.videocache.listener.IVideoCacheListener;
import com.baofu.videocache.model.VideoCacheInfo;
import com.baofu.videocache.utils.LogUtils;
import com.baofu.videocache.utils.ProxyCacheUtils;

import java.util.HashMap;
import java.util.Map;

public class LocalProxyVideoControl {

    private static final String TAG = "LocalProxyVideoControl";

    private String mVideoUrl;

    private IVideoCacheListener mListener = new IVideoCacheListener() {
        @Override
        public void onCacheStart(VideoCacheInfo cacheInfo) { }

        @Override
        public void onCacheProgress(VideoCacheInfo cacheInfo) {
            Map<String, Object> params = new HashMap<>();
            params.put(VideoParams.PERCENT, cacheInfo.getPercent());
            params.put(VideoParams.CACHE_SIZE, cacheInfo.getCachedSize());
        }

        @Override
        public void onCacheError(VideoCacheInfo cacheInfo, int errorCode) {

        }

        @Override
        public void onCacheForbidden(VideoCacheInfo cacheInfo) { }

        @Override
        public void onCacheFinished(VideoCacheInfo cacheInfo) {
            Map<String, Object> params = new HashMap<>();
            params.put(VideoParams.PERCENT, 100f);
            params.put(VideoParams.TOTAL_SIZE, cacheInfo.getTotalSize());
        }
    };

    public LocalProxyVideoControl() {
    }

    public void startRequestVideoInfo(String videoUrl, String videoName,Map<String, String> headers, Map<String, Object> extraParams) {
        if(videoUrl==null){
            return;
        }
        mVideoUrl = videoUrl;
        VideoProxyCacheManager.getInstance().addCacheListener(videoUrl, mListener);
        VideoProxyCacheManager.getInstance().setPlayingUrlMd5(ProxyCacheUtils.computeMD5(videoUrl));
        VideoProxyCacheManager.getInstance().startRequestVideoInfo(videoUrl, videoName,headers, extraParams);
    }

    public void pauseLocalProxyTask() {
        LogUtils.i(TAG, "pauseLocalProxyTask");
        if(mVideoUrl==null){
            return;
        }
        VideoProxyCacheManager.getInstance().pauseCacheTask(mVideoUrl);
    }

    public void resumeLocalProxyTask() {
        LogUtils.i(TAG, "resumeLocalProxyTask");
        if(mVideoUrl==null){
            return;
        }
        VideoProxyCacheManager.getInstance().resumeCacheTask(mVideoUrl);
    }

    public void seekToCachePosition(long position,long totalDuration) {
        if(mVideoUrl==null){
            return;
        }
        if (totalDuration > 0) {
            float percent = position * 1.0f / totalDuration;
            VideoProxyCacheManager.getInstance().seekToCacheTaskFromClient(mVideoUrl, percent);
        }
    }

    public void releaseLocalProxyResources() {
        if(mVideoUrl==null){
            return;
        }
        VideoProxyCacheManager.getInstance().stopCacheTask(mVideoUrl);   //停止视频缓存任务
        VideoProxyCacheManager.getInstance().releaseProxyReleases(mVideoUrl);
    }
}
