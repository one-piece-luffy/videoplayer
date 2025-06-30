package com.baofu.cache.downloader.m3u8;

import android.net.Uri;
import android.text.TextUtils;

import com.baofu.cache.downloader.utils.VideoDownloadUtils;
import com.jeffmony.videocache.utils.ProxyCacheUtils;

import java.io.File;
import java.util.Map;

public class M3U8Seg implements Comparable<M3U8Seg> {
    private float mDuration;                     //分片时长
    private int mSegIndex;                          //分片索引值,第一个为0
    private int mSequence;                       //分片的sequence, 根据initSequence自增得到的
    private String mUrl;                         //分片url
    private String mName;                        //分片名,可以自己定义
    private long mTsSize;                        //分片大小
    private boolean mHasDiscontinuity;           //分片前是否有#EXT-X-DISCONTINUITY标识
    private boolean mHasKey;                     //分片是否有#EXT-X-KEY
    private String mMethod;                      //加密的方式
    private String mKeyUri;                      //加密的url
    private String mKeyIV;                       //加密的IV
    private boolean mIsMessyKey;                 //当前加密key是否是乱码
    private long mContentLength;                 //分片的Content-Length
    private int mRetryCount;                     //分片的请求重试次数
    private boolean mHasInitSegment;             //分片前是否有#EXT-X-MAP
    private String mInitSegmentUri;              //MAP的url
    private String mSegmentByteRange;            //MAP的range
    public boolean is403;
    public boolean failed;
    public boolean success;
    public byte[] encryptionKey;
    public String mParentUrl;             //分片的上级M3U8的url

    public M3U8Seg() { }

    public void initTsAttributes(String url, float duration, int index,
                                 int sequence, boolean hasDiscontinuity) {
        mUrl = url;
        mDuration = duration;
        mSegIndex = index;
        mSequence = sequence;
        mHasDiscontinuity = hasDiscontinuity;
        mTsSize = 0L;
    }

    public void setKeyConfig(String method, String keyUri, String keyIV) {
        mHasKey = true;
        mMethod = method;
        mKeyUri = keyUri;
        mKeyIV = keyIV;
    }

    public void setInitSegmentInfo(String initSegmentUri, String segmentByteRange) {
        mHasInitSegment = true;
        mInitSegmentUri = initSegmentUri;
        mSegmentByteRange = segmentByteRange;
    }

    public int getSequence() { return mSequence; }

    public boolean hasKey() {
        return mHasKey;
    }

    public String getMethod() {
        return mMethod;
    }

    public String getKeyUri() {
        return mKeyUri;
    }

    public String getKeyIV() {
        return mKeyIV;
    }

    public float getDuration() {
        return mDuration;
    }

    public String getUrl() {
        return mUrl;
    }



    public String getIndexName() {
        String suffixName = "";
        if (!TextUtils.isEmpty(mUrl)) {
            Uri uri = Uri.parse(mUrl);
            String fileName = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(fileName)) {
                fileName = fileName.toLowerCase();
                suffixName = VideoDownloadUtils.getSuffixName(fileName);
            }
        }
        return VideoDownloadUtils.SEGMENT_PREFIX + mSegIndex + suffixName;
    }



    public void setTsSize(long tsSize) {
        mTsSize = tsSize;
    }

    public long getTsSize() {
        return mTsSize;
    }

    public boolean hasDiscontinuity() {
        return mHasDiscontinuity;
    }

    public void setIsMessyKey(boolean isMessyKey) {
        mIsMessyKey = isMessyKey;
    }

    public boolean isMessyKey() {
        return mIsMessyKey;
    }

    public void setContentLength(long contentLength) {
        mContentLength = contentLength;
    }

    public long getContentLength() {
        return mContentLength;
    }

    public void setRetryCount(int retryCount) {
        mRetryCount = retryCount;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    public boolean hasInitSegment() { return mHasInitSegment; }

    public String getInitSegmentUri() { return mInitSegmentUri; }

    public String getSegmentByteRange() { return mSegmentByteRange; }

    public String getInitSegmentName() {
        String suffixName = "";
        if (!TextUtils.isEmpty(mInitSegmentUri)) {
            Uri uri = Uri.parse(mInitSegmentUri);
            String fileName = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(fileName)) {
                fileName = fileName.toLowerCase();
                suffixName = VideoDownloadUtils.getSuffixName(fileName);
            }
        }
        return VideoDownloadUtils.INIT_SEGMENT_PREFIX + mSegIndex + suffixName;
    }

    public String toString() {
        return "duration=" + mDuration + ", index=" + mSegIndex + ", name=" + mName;
    }

    @Override
    public int compareTo(M3U8Seg object) {
        return mName.compareTo(object.mName);
    }


    public String getInitSegProxyUrl(String md5, Map<String, String> headers) {
        //三个字符串
        //1.parent url
        //2.init Seg的url
        //3.init Seg存储的位置
        //4.init Seg url对应的请求headers
        String proxyExtraInfo = mParentUrl + ProxyCacheUtils.SEG_PROXY_SPLIT_STR + mInitSegmentUri + ProxyCacheUtils.SEG_PROXY_SPLIT_STR +
                File.separator + md5 + File.separator + getInitSegmentName() + ProxyCacheUtils.SEG_PROXY_SPLIT_STR + ProxyCacheUtils.map2Str(headers);
        //return String.format(Locale.US, "http://%s:%d/%s", ProxyCacheUtils.LOCAL_PROXY_HOST, ProxyCacheUtils.getLocalPort(), ProxyCacheUtils.encodeUriWithBase64(proxyExtraInfo));
        return ProxyCacheUtils.encodeUriWithBase64(proxyExtraInfo);
    }

    public String getSegProxyUrl(String md5, Map<String, String> headers) {
        //三个字符串
        //1.parent url
        //2.Seg的url
        //3.Seg存储的位置
        //4.Seg url对应的请求headers
        String proxyExtraInfo = mParentUrl + ProxyCacheUtils.SEG_PROXY_SPLIT_STR + mUrl + ProxyCacheUtils.SEG_PROXY_SPLIT_STR +
                File.separator + md5 + File.separator + getSegName() + ProxyCacheUtils.SEG_PROXY_SPLIT_STR + ProxyCacheUtils.map2Str(headers);
        //return String.format(Locale.US, "http://%s:%d/%s", ProxyCacheUtils.LOCAL_PROXY_HOST, ProxyCacheUtils.getLocalPort(), ProxyCacheUtils.encodeUriWithBase64(proxyExtraInfo));
        return ProxyCacheUtils.encodeUriWithBase64(proxyExtraInfo);
    }

    public String getLocalKeyUri() {
        return  "local" + ".key";
    }

    public String getSegName() {
        if (!TextUtils.isEmpty(mName)) {
            return mName;
        }
        String suffixName = "";
        if (!TextUtils.isEmpty(mUrl)) {
            Uri uri = Uri.parse(mUrl);
            String fileName = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(fileName)) {
                suffixName = ProxyCacheUtils.getSuffixName(fileName.toLowerCase());
            }
        }
        //fix:https://github.com/JeffMony/JeffVideoCache/issues/21
        suffixName = !TextUtils.isEmpty(suffixName) ? suffixName : ".ts";
        mName = mSegIndex + suffixName;
        return mName;
    }
}

