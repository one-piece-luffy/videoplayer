package com.yc.kernel.impl.exo;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;


import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.database.ExoDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2018/11/9
 *     desc  : exo视频播放器帮助类
 *     revise:
 * </pre>
 */
@UnstableApi public final class ExoMediaSourceHelper {

    private static ExoMediaSourceHelper sInstance;
    private final String mUserAgent;
    private Context mAppContext;
    private HttpDataSource.Factory mHttpDataSourceFactory;
//    private Cache mCache;

    @OptIn(markerClass = UnstableApi.class) private ExoMediaSourceHelper(Context context) {
        if (context instanceof Application){
            mAppContext = context;
        } else {
            mAppContext = context.getApplicationContext();
        }
        mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
    }

    public static ExoMediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ExoMediaSourceHelper.class) {
                if (sInstance == null) {
                    sInstance = new ExoMediaSourceHelper(context);
                }
            }
        }
        return sInstance;
    }

    public MediaSource getMediaSource(String uri) {
        return getMediaSource(uri, null, false);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers) {
        return getMediaSource(uri, headers, false);
    }

    public MediaSource getMediaSource(String uri, boolean isCache) {
        return getMediaSource(uri, null, isCache);
    }

    @OptIn(markerClass = UnstableApi.class) public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache) {
        Uri contentUri = Uri.parse(uri);
        if ("rtmp".equals(contentUri.getScheme())) {
//            RtmpDataSourceFactory rtmpDataSourceFactory = new RtmpDataSourceFactory(null);
//            return new ProgressiveMediaSource.Factory(rtmpDataSourceFactory).createMediaSource(contentUri);
        }

        MediaItem mediaItem = MediaItem.fromUri(uri);

        int contentType = inferContentType(uri);
        DataSource.Factory factory;
//        if (isCache) {
//            factory = getCacheDataSourceFactory();
//        } else {
            factory = getDataSourceFactory();
//        }
        if (mHttpDataSourceFactory != null) {
            setHeaders(headers);
        }

        if(headers!=null&&"m3u8".equals(headers.get("type"))){
            return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
        }
        switch (contentType) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(factory).createMediaSource(mediaItem);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(factory).createMediaSource(mediaItem);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(factory).createMediaSource(mediaItem);
            default:
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem);
        }
    }

    @OptIn(markerClass = UnstableApi.class) private int inferContentType(String fileName) {
        fileName =fileName.toLowerCase();
        if (fileName.contains(".mpd")) {
            return C.TYPE_DASH;
        } else if (fileName.contains(".m3u8")||fileName.contains("127.0.0.1")) {
            return C.TYPE_HLS;
        } else if (fileName.matches(".*\\.ism(l)?(/manifest(\\(.+\\))?)?")) {
            return C.TYPE_SS;
        } else {
            return C.TYPE_OTHER;
        }
    }

//    private DataSource.Factory getCacheDataSourceFactory() {
//        if (mCache == null) {
//            mCache = newCache();
//        }
//        return new CacheDataSourceFactory(
//                mCache,
//                getDataSourceFactory(),
//                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
//    }

    @OptIn(markerClass = UnstableApi.class) private Cache newCache() {
        return new SimpleCache(
                //缓存目录
                new File(mAppContext.getExternalCacheDir(), "exo-video-cache"),
                //缓存大小，默认512M，使用LRU算法实现
                new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
                new ExoDatabaseProvider(mAppContext));
    }

    /**
     * Returns a new DataSource factory.
     *
     * @return A new DataSource factory.
     */
    @OptIn(markerClass = UnstableApi.class) private DataSource.Factory getDataSourceFactory() {
        return new DefaultDataSourceFactory(mAppContext, getHttpDataSourceFactory());
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @return A new HttpDataSource factory.
     */
    private DataSource.Factory getHttpDataSourceFactory() {
        if (mHttpDataSourceFactory == null) {
            mHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(60000) // 连接超时时间（毫秒）
                    .setReadTimeoutMs(60000) // 读取超时时间（毫秒）
                    .setAllowCrossProtocolRedirects(true); // 允许跨协议重定向;

        }
        return mHttpDataSourceFactory;
    }

    private void setHeaders(Map<String, String> headers) {
        if (headers != null && headers.size() > 0) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String key = header.getKey();
                String value = header.getValue();
                //如果发现用户通过header传递了UA，则强行将HttpDataSourceFactory里面的userAgent字段替换成用户的
                if (TextUtils.equals(key, "User-Agent")) {
                    if (!TextUtils.isEmpty(value)) {
                        try {
                            Field userAgentField = mHttpDataSourceFactory.getClass().getDeclaredField("userAgent");
                            userAgentField.setAccessible(true);
                            userAgentField.set(mHttpDataSourceFactory, value);
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                } else {
                    mHttpDataSourceFactory.setDefaultRequestProperties(headers);
                }
            }
        }
    }

//    public void setCache(Cache cache) {
//        this.mCache = cache;
//    }
}
