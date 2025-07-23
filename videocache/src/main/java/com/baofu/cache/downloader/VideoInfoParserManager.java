package com.baofu.cache.downloader;

import android.util.Log;

import com.baofu.cache.downloader.listener.IVideoInfoListener;
import com.baofu.cache.downloader.listener.IVideoInfoParseListener;
import com.baofu.cache.downloader.m3u8.M3U8;
import com.baofu.cache.downloader.m3u8.M3U8Utils;
import com.baofu.cache.downloader.model.Video;
import com.baofu.cache.downloader.model.CacheTaskItem;
import com.baofu.cache.downloader.rules.CacheDownloadManager;
import com.baofu.cache.downloader.utils.DownloadExceptionUtils;
import com.baofu.cache.downloader.utils.OkHttpUtil;
import com.baofu.cache.downloader.utils.VideoDownloadUtils;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.common.VideoType;
import com.jeffmony.videocache.model.VideoCacheInfo;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.StorageUtils;
import com.jeffmony.videocache.utils.VideoCacheUtils;

import java.io.File;
import java.util.Map;

public class VideoInfoParserManager {

    private static final String TAG = "VideoInfoParserManager";

    private static volatile VideoInfoParserManager sInstance;

    public static VideoInfoParserManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoInfoParserManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoInfoParserManager();
                }
            }
        }
        return sInstance;

    }



    /**
     * 解析网络m3u8文件
     * @param taskItem
     * @param headers
     * @param listener
     */
    public void parseNetworkM3U8Info(CacheTaskItem taskItem, Map<String, String> headers, IVideoInfoListener listener) {
        try {
            String method=OkHttpUtil.METHOD.GET;
            if(OkHttpUtil.METHOD.POST.equalsIgnoreCase(taskItem.method)){
                method=OkHttpUtil.METHOD.POST;
            }
            M3U8 m3u8 = M3U8Utils.parseNetworkM3U8Info(taskItem.getUrl(), headers, 0,false,method);
            if(m3u8==null){
                listener.onM3U8InfoFailed(new VideoDownloadException("m3u8 is null"));
                return;
            }
            // HLS LIVE video cannot be proxy cached.
            //todo m3u8里面的mp4链接
            if (m3u8.hasEndList()) {
                taskItem.suffix = VideoDownloadUtils.M3U8_SUFFIX;

                String saveName = VideoDownloadUtils.getFileName(taskItem, null, false);
                //todo  如果先下载到公有目录，再下载到私有目录 会导致保存下载进度的cache file 有问题，因为都是一个名字
                //需要用私有目录，不然android10以上没有权限
                File dir = new File(CacheDownloadManager.getInstance().mConfig.privatePath, saveName);
                //同名文件处理
                if (dir.exists()) {
                    if (!taskItem.overwrite) {
                        saveName = VideoDownloadUtils.getFileName(taskItem, System.currentTimeMillis() + "", false);
                        dir = new File(CacheDownloadManager.getInstance().mConfig.privatePath, saveName);
                    }
                }
                if (!dir.exists()) {
                    dir.mkdir();
                }

                //todo
                PlayerProgressListenerManager.getInstance().log("开始创建" + taskItem.mName + "m3u8文件");
                // 1.将M3U8结构保存到本地
                File localM3U8File = new File(dir, saveName + StorageUtils.LOCAL_M3U8_SUFFIX);
                M3U8Utils.createLocalM3U8File(localM3U8File, m3u8,headers);
                PlayerProgressListenerManager.getInstance().log("m3u8创建完毕");
                File proxyM3U8File = new File(dir, saveName + StorageUtils.PROXY_M3U8_SUFFIX);
//                cacheInfo.setLocalPort(ProxyCacheUtils.getLocalPort());
                M3U8Utils.createProxyM3U8File(proxyM3U8File, m3u8, saveName, null);
                PlayerProgressListenerManager.getInstance().log("代理文件创建完毕");

                //todo 创建cacheinfo?
                String md5 = ProxyCacheUtils.computeMD5(taskItem.mUrl);
                File saveDir = new File(ProxyCacheUtils.getConfig().getFilePath(), md5);
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }
                VideoCacheInfo videoCacheInfo = StorageUtils.readVideoCacheInfo(saveDir);
                if (videoCacheInfo == null) {
                    videoCacheInfo = new VideoCacheInfo(taskItem.mUrl);
                    videoCacheInfo.setMd5(md5);
                    videoCacheInfo.setSavePath(saveDir.getAbsolutePath());
                    videoCacheInfo.setVideoType(VideoType.M3U8_TYPE);
//                    videoCacheInfo.setTotalTs(m3u8.getTsList().size());
                    StorageUtils.saveVideoCacheInfo(videoCacheInfo, saveDir);
                }


                taskItem.setSaveDir(dir.getAbsolutePath());
                taskItem.setFileHash(saveName);
                taskItem.setVideoType(Video.Type.HLS_TYPE);
                listener.onM3U8InfoSuccess(taskItem, m3u8);
            } else {
                taskItem.setVideoType(Video.Type.HLS_LIVE_TYPE);
                listener.onLiveM3U8Callback(taskItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
            listener.onM3U8InfoFailed(e);
        }
    }


    /**
     * 解析本地m3u8文件
     * @param taskItem
     * @param callback
     */
    public void parseLocalM3U8File(CacheTaskItem taskItem, File m3u8File, IVideoInfoParseListener callback) {
        if (!m3u8File.exists()) {
            Log.e("asdf", "代理文件不存在");
            callback.onM3U8FileParseFailed(taskItem, new VideoDownloadException(DownloadExceptionUtils.REMOTE_M3U8_EMPTY));
            return;
        }
        try {
            M3U8 m3u8 = M3U8Utils.parseLocalM3U8File(m3u8File, VideoDownloadUtils.getTaskHeader(taskItem));
            callback.onM3U8FileParseSuccess(taskItem, m3u8);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onM3U8FileParseFailed(taskItem, e);
        }
    }





}
