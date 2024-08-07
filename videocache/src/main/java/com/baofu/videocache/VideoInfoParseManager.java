package com.baofu.videocache;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.baofu.videocache.common.VideoCacheConstants;
import com.baofu.videocache.common.VideoCacheException;
import com.baofu.videocache.common.VideoType;
import com.baofu.videocache.common.VideoParams;
import com.baofu.videocache.listener.IVideoInfoParsedListener;
import com.baofu.videocache.listener.VideoInfoParsedListener;
import com.baofu.videocache.m3u8.M3U8;
import com.baofu.videocache.m3u8.M3U8Utils;
import com.baofu.videocache.model.VideoCacheInfo;
import com.baofu.videocache.okhttp.OkHttpManager;
import com.baofu.videocache.utils.HttpUtils;
import com.baofu.videocache.utils.ProxyCacheUtils;
import com.baofu.videocache.utils.StorageUtils;
import com.baofu.videocache.utils.VideoCacheUtils;
import com.baofu.videocache.utils.VideoParamsUtils;
import com.baofu.videocache.utils.VideoProxyThreadUtils;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.Map;

/**
 * @author jeffmony
 * 解析视频类型信息的工具类
 */
public class VideoInfoParseManager {

    private static final String TAG = "VideoInfoParseManager";

    private static volatile VideoInfoParseManager sInstance = null;

    private IVideoInfoParsedListener mListener;
    private Map<String, String> mHeaders;
    private String mContentType;
    private long mContentLength;
    public M3U8 m3u8;

    public static VideoInfoParseManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoInfoParseManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoInfoParseManager();
                }
            }
        }
        return sInstance;
    }

    //解析视频类型
    public void parseVideoInfo(VideoCacheInfo cacheInfo, Map<String, String> headers,
                               Map<String, Object> extraParams, IVideoInfoParsedListener listener) {

        mListener = listener;
        mHeaders = headers;
        //这两个值开发者可以设置
        mContentType = VideoParamsUtils.getStringValue(extraParams, VideoParams.CONTENT_TYPE);
        mContentLength = VideoParamsUtils.getLongValue(extraParams, VideoParams.CONTENT_LENGTH);

        VideoProxyThreadUtils.submitRunnableTask(() -> {
            if (ProxyCacheUtils.getConfig().useOkHttp()) {
                parseVideoInfoByOkHttp(cacheInfo);
            } else {
                parseVideoInfo(cacheInfo);
            }
        });
    }

    //使用okhttp网路框架去请求数据
    private void parseVideoInfoByOkHttp(VideoCacheInfo cacheInfo) {
        Log.e(TAG,"parseVideoInfoByOkHttp mContentType:"+mContentType);
        if (TextUtils.equals(VideoParams.UNKNOWN, mContentType)) {
            String videoUrl = cacheInfo.getVideoUrl();
            Log.e(TAG,"parseVideoInfoByOkHttp videoUrl:"+videoUrl);
            if (videoUrl.contains("m3u8")) {
                //这种情况下也基本可以认为video是M3U8类型，虽然判断不太严谨，但是mediaplayer也是这么做的
                parseNetworkM3U8Info(cacheInfo);
            } else {
                Uri videoUri = Uri.parse(videoUrl);
                String fileName = videoUri.getLastPathSegment();
                if (!TextUtils.isEmpty(fileName)) {
                    fileName = fileName.toLowerCase();
                    if (fileName.endsWith(".m3u8")) {
                        //当前是M3U8类型
                        parseNetworkM3U8Info(cacheInfo);
                    } else {
                        //不是M3U8类型，说明是整视频
                        parseNonM3U8VideoInfoByOkHttp(cacheInfo);
                    }
                } else {
                    //需要发起请求判定当前视频的类型
                    String contentType;
                    try {
                        contentType = OkHttpManager.getInstance().getContentType(videoUrl, mHeaders);
                    } catch (VideoCacheException e) {
                        mListener.onNonM3U8ParsedFailed(e, cacheInfo);
                        return;
                    }

                    if (TextUtils.isEmpty(contentType)) {
                        mListener.onNonM3U8ParsedFailed(new VideoCacheException("ContentType is null"), cacheInfo);
                    } else {
                        contentType = contentType.toLowerCase();
                        if (ProxyCacheUtils.isM3U8Mimetype(contentType)) {
                            //当前是M3U8类型
                            parseNetworkM3U8Info(cacheInfo);
                        } else {
                            parseNonM3U8VideoInfoByOkHttp(cacheInfo);
                        }
                    }
                }
            }
        } else {
            if (ProxyCacheUtils.isM3U8Mimetype(mContentType)) {
                //当前是M3U8类型
                parseNetworkM3U8Info(cacheInfo);
            } else {
                //不是M3U8类型，说明是整视频
                parseNonM3U8VideoInfoByOkHttp(cacheInfo);
            }
        }
    }

    //使用android原生的HttpURLConnection发起请求
    private void parseVideoInfo(VideoCacheInfo cacheInfo) {
        if (TextUtils.equals(VideoParams.UNKNOWN, mContentType)) {
            String videoUrl = cacheInfo.getVideoUrl();
            if (videoUrl.contains("m3u8")) {
                //这种情况下也基本可以认为video是M3U8类型，虽然判断不太严谨，但是mediaplayer也是这么做的
                parseNetworkM3U8Info(cacheInfo);
            } else {
                Uri videoUri = Uri.parse(videoUrl);
                String fileName = videoUri.getLastPathSegment();
                if (!TextUtils.isEmpty(fileName)) {
                    fileName = fileName.toLowerCase();
                    if (fileName.endsWith(".m3u8")) {
                        //当前是M3U8类型
                        parseNetworkM3U8Info(cacheInfo);
                    } else {
                        //不是M3U8类型，说明是整视频
                        parseNonM3U8VideoInfo(cacheInfo);
                    }
                } else {
                    //需要发起请求判定当前视频的类型
                    HttpURLConnection connection = null;
                    try {
                        connection = HttpUtils.getConnection(cacheInfo.getVideoUrl(), mHeaders);
                        String contentType = connection.getContentType();
                        if (ProxyCacheUtils.isM3U8Mimetype(contentType)) {
                            //当前是M3U8类型
                            parseNetworkM3U8Info(cacheInfo);
                        } else {
                            //不是M3U8类型，说明是整视频
                            parseNonM3U8VideoInfo(cacheInfo, connection);
                        }
                    } catch (Exception e) {
                        mListener.onNonM3U8ParsedFailed(new VideoCacheException(e.getMessage()), cacheInfo);
                    } finally {
                        HttpUtils.closeConnection(connection);
                    }
                }
            }
        } else {
            if (ProxyCacheUtils.isM3U8Mimetype(mContentType)) {
                //当前是M3U8类型
                parseNetworkM3U8Info(cacheInfo);
            } else {
                //不是M3U8类型，说明是整视频
                parseNonM3U8VideoInfo(cacheInfo);
            }
        }
    }

    /**
     * 解析M3U8视频信息
     *
     * M3U8类型的请求还是建议使用HttpURLConnection
     * @param cacheInfo
     */
    private void parseNetworkM3U8Info(VideoCacheInfo cacheInfo) {
        Log.e(TAG,"==parseNetworkM3U8Info ");
        try {
            if(mHeaders!=null&&mHeaders.containsKey(VideoCacheConstants.NAME)){
                mHeaders.remove(VideoCacheConstants.NAME);
            }
            m3u8 = M3U8Utils.parseNetworkM3U8Info(cacheInfo.getVideoUrl(), cacheInfo.getVideoUrl(), mHeaders, 0);
            Log.e(TAG,"开始创建m3u8文件 ");
//            if (m3u8.isIsLive()) {
//                //说明M3U8是直播
//                mListener.onM3U8LiveCallback(cacheInfo);
//            } else {
                cacheInfo.setVideoType(VideoType.M3U8_TYPE);
                cacheInfo.setTotalTs(m3u8.getSegCount());
                // 1.将M3U8结构保存到本地
                VideoProxyThreadUtils.submitRunnableTask(() -> {
                    File localM3U8File = new File(cacheInfo.getSavePath(), VideoCacheUtils.getFileName(cacheInfo) + StorageUtils.LOCAL_M3U8_SUFFIX);
                    try {
                        M3U8Utils.createLocalM3U8File(localM3U8File, m3u8);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                File proxyM3U8File = new File(cacheInfo.getSavePath(), VideoCacheUtils.getFileName(cacheInfo) + StorageUtils.PROXY_M3U8_SUFFIX);
                if (proxyM3U8File.exists() && cacheInfo.getLocalPort() == ProxyCacheUtils.getLocalPort()) {
                    //说明本地代理文件存在，连端口号都一样的，不用做任何改变

                    //Do nothing.
                } else {
                    cacheInfo.setLocalPort(ProxyCacheUtils.getLocalPort());
                    M3U8Utils.createProxyM3U8File(proxyM3U8File, m3u8, cacheInfo.getMd5(), mHeaders);
                }

                // 2.构建一个本地代理的m3u8结构
                mListener.onM3U8ParsedFinished(m3u8, cacheInfo);
//            }
        } catch (Exception e) {
            e.printStackTrace();
            mListener.onM3U8ParsedFailed(new VideoCacheException("parseM3U8Info failed, " + e.getMessage()), cacheInfo);
        }
    }

    public void parseProxyM3U8Info(VideoCacheInfo cacheInfo, Map<String, String> headers, VideoInfoParsedListener listener) {
        Log.e(TAG,"===parseProxyM3U8Info");
        mHeaders = headers;
        mListener = listener;
        File proxyM3U8File = new File(cacheInfo.getSavePath(), VideoCacheUtils.getFileName(cacheInfo) + StorageUtils.PROXY_M3U8_SUFFIX);
        if (!proxyM3U8File.exists()) {
            Log.e(TAG,"==m3u8  exit false");
            parseNetworkM3U8Info(cacheInfo);
        } else {
            Log.e(TAG,"==m3u8 exit true");
            VideoProxyThreadUtils.submitRunnableTask(() -> {
                boolean result = M3U8Utils.updateM3U8TsPortInfo(proxyM3U8File, ProxyCacheUtils.getLocalPort());
                if (result) {
                    File localM3U8File = new File(cacheInfo.getSavePath(), VideoCacheUtils.getFileName(cacheInfo) + StorageUtils.LOCAL_M3U8_SUFFIX);
                    try {
                        M3U8 m3u8 = M3U8Utils.parseLocalM3U8Info(localM3U8File, cacheInfo.getVideoUrl());
                        VideoInfoParseManager.getInstance().m3u8=m3u8;
                        cacheInfo.setTotalTs(m3u8.getSegCount());
                        mListener.onM3U8ParsedFinished(m3u8, cacheInfo);
                    } catch (Exception e) {
                        mListener.onM3U8ParsedFailed(new VideoCacheException("parseLocalM3U8Info failed"), cacheInfo);
                    }
                } else {
                    mListener.onM3U8ParsedFailed(new VideoCacheException("updateM3U8TsPortInfo failed"), cacheInfo);
                }
            });
        }
    }

    /**
     * 通过okhttp来请求非M3U8的视频
     *
     * 对于一些短视频,还是建议使用okhttp网络请求框架
     * @param cacheInfo
     */
    private void parseNonM3U8VideoInfoByOkHttp(VideoCacheInfo cacheInfo) {
        Log.e(TAG,"parseNonM3U8VideoInfoByOkHttp ");
        cacheInfo.setVideoType(VideoType.OTHER_TYPE);
        long contentLength;

        try {
            contentLength = OkHttpManager.getInstance().getContentLength(cacheInfo.getVideoUrl(), mHeaders);
            if (contentLength > 0) {
                cacheInfo.setTotalSize(contentLength);
                mListener.onNonM3U8ParsedFinished(cacheInfo);
            } else {
                mListener.onNonM3U8ParsedFailed(new VideoCacheException(""), cacheInfo);
            }
        } catch (VideoCacheException e) {
            mListener.onNonM3U8ParsedFailed(e, cacheInfo);
        }
    }

    /**
     * 解析非M3U8视频类型信息
     * @param cacheInfo
     */
    private void parseNonM3U8VideoInfo(VideoCacheInfo cacheInfo) {
        HttpURLConnection connection = null;
        try {
            connection = HttpUtils.getConnection(cacheInfo.getVideoUrl(), mHeaders);
            parseNonM3U8VideoInfo(cacheInfo, connection);
        } catch (Exception e) {
            mListener.onNonM3U8ParsedFailed(new VideoCacheException(e.getMessage()), cacheInfo);
        } finally {
            HttpUtils.closeConnection(connection);
        }
    }

    private void parseNonM3U8VideoInfo(VideoCacheInfo cacheInfo, HttpURLConnection connection) {
        cacheInfo.setVideoType(VideoType.OTHER_TYPE);
        String length = connection.getHeaderField("content-length");
        try {
            long totalLength = Long.parseLong(length);
            if (totalLength > 0) {
                cacheInfo.setTotalSize(totalLength);
                mListener.onNonM3U8ParsedFinished(cacheInfo);
            } else {
                mListener.onNonM3U8ParsedFailed(new VideoCacheException("Total length is illegal"), cacheInfo);
            }
        } catch (Exception e) {
            mListener.onNonM3U8ParsedFailed(new VideoCacheException(e.getMessage()), cacheInfo);
        }
    }
    public void release(){
        m3u8=null;
    }

}
