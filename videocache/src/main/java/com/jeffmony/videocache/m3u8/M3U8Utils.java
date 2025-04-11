package com.jeffmony.videocache.m3u8;

import android.text.TextUtils;
import android.util.Log;

import com.jeffmony.videocache.common.VideoCacheConstants;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.OkHttpUtil;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.UrlUtils;
import com.jeffmony.videocache.utils.VideoCacheUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

/**
 * @author jeffmony
 *
 * M3U8的通用处理类
 */
public class M3U8Utils {

    private static final String TAG = "M3U8Utils";

    private static int sOldPort = 0;

    /**
     * 根据url将M3U8信息解析出来
     * @param videoUrl
     * @param headers
     * @return
     * @throws IOException
     */
    public static M3U8 parseNetworkM3U8Info(String parentUrl, String videoUrl, Map<String, String> headers, int retryCount) throws IOException {
        if(headers!=null&&headers.containsKey(VideoCacheConstants.NAME)){
            headers.remove(VideoCacheConstants.NAME);
        }
        BufferedReader bufferedReader = null;
        Response response=null;
        try {
//            HttpURLConnection connection = HttpUtils.getConnection(videoUrl, headers, VideoDownloadUtils.getDownloadConfig().ignoreAllCertErrors);
//            int responseCode = connection.getResponseCode();

            response = OkHttpUtil.getInstance().requestSync(videoUrl,headers);
            int responseCode =response.code();
            Log.e(TAG, "==parseNetworkM3U8Info responseCode=" + responseCode);
            if (responseCode == HttpUtils.RESPONSE_503 && retryCount < HttpUtils.MAX_RETRY_COUNT) {
                Log.e(TAG, "==parseNetworkM3U8Info responseCode=" + responseCode);
                return parseNetworkM3U8Info(parentUrl, videoUrl, headers, retryCount + 1);
            }
            bufferedReader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            M3U8 m3u8 = new M3U8(videoUrl);
            float tsDuration = 0;
            int targetDuration = 0;
            int tsIndex = 0;
            int version = 0;
            int sequence = 0;
            boolean hasDiscontinuity = false;
            boolean hasEndList = false;
            boolean hasStreamInfo = false;
            boolean hasKey = false;
            boolean hasInitSegment = false;
            String method = null;
            String encryptionIV = null;
            String encryptionKeyUri = null;
            String initSegmentUri = null;
            String segmentByteRange = null;
            String line;
            int errorCount = 0;
            //从网络读取出来的key
            byte[] encryptionKey=null;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (TextUtils.isEmpty(line)) {
                    continue;
                }
                LogUtils.i(TAG, "line = " + line);
                if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
                    if (line.startsWith(M3U8Constants.TAG_MEDIA_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            tsDuration = Float.parseFloat(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_TARGET_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_TARGET_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            targetDuration = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_VERSION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_VERSION);
                        if (!TextUtils.isEmpty(ret)) {
                            version = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_MEDIA_SEQUENCE)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_SEQUENCE);
                        if (!TextUtils.isEmpty(ret)) {
                            sequence = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_STREAM_INF)) {
                        hasStreamInfo = true;
                    } else if (line.startsWith(M3U8Constants.TAG_DISCONTINUITY)) {
                        hasDiscontinuity = true;
                    } else if (line.startsWith(M3U8Constants.TAG_ENDLIST)) {
                        hasEndList = true;
                    } else if (line.startsWith(M3U8Constants.TAG_KEY)) {
                        hasKey = true;
                        method = parseOptionalStringAttr(line, M3U8Constants.REGEX_METHOD);
                        String keyFormat = parseOptionalStringAttr(line, M3U8Constants.REGEX_KEYFORMAT);
                        if (!M3U8Constants.METHOD_NONE.equals(method)) {
                            encryptionIV = parseOptionalStringAttr(line, M3U8Constants.REGEX_IV);
                            if (M3U8Constants.KEYFORMAT_IDENTITY.equals(keyFormat) || keyFormat == null) {
                                if (M3U8Constants.METHOD_AES_128.equals(method)) {
                                    // The segment is fully encrypted using an identity key.
                                    String tempKeyUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                                    if (tempKeyUri != null) {
                                        encryptionKeyUri = getM3U8AbsoluteUrl(videoUrl, tempKeyUri);
                                    }
                                } else {
                                    // Do nothing. Samples are encrypted using an identity key,
                                    // but this is not supported. Hopefully, a traditional DRM
                                    // alternative is also provided.
                                }
                            } else {
                                // Do nothing.
                            }
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_INIT_SEGMENT)) {
                        String tempInitSegmentUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                        if (!TextUtils.isEmpty(tempInitSegmentUri)) {
                            hasInitSegment = true;
                            initSegmentUri = getM3U8AbsoluteUrl(videoUrl, tempInitSegmentUri);
                            segmentByteRange = parseOptionalStringAttr(line, M3U8Constants.REGEX_ATTR_BYTERANGE);
                        }
                    }
                    continue;
                }
                // It has '#EXT-X-STREAM-INF' tag;
                if (hasStreamInfo) {
                    String tempUrl = UrlUtils.getM3U8MasterUrl(videoUrl, line);
                    Log.e(TAG, "==parseNetworkM3U8Info hasStreamInfo："+tempUrl );
                    return parseNetworkM3U8Info(parentUrl,getM3U8AbsoluteUrl(videoUrl, line), headers, retryCount+1);
                }
                if (Math.abs(tsDuration) < 0.001f) {
                    continue;
                }
                M3U8Seg ts = new M3U8Seg();
                ts.setParentUrl(parentUrl);
                String tsUrl = getM3U8AbsoluteUrl(videoUrl, line);
                ts.setUrl(tsUrl);
                ts.setDuration(tsDuration);
                ts.setSegIndex(tsIndex);
                ts.setHasDiscontinuity(hasDiscontinuity);

                if (hasKey) {
                    if (encryptionKey == null) {
                        encryptionKey = parseKey(encryptionKeyUri);
                        ts.encryptionKey = encryptionKey;
                    }
                    ts.setHasKey(true);
                    ts.encryptionKey = encryptionKey;
                    ts.setMethod(method);
                    ts.setKeyUrl(encryptionKeyUri);
                    ts.setKeyIv(encryptionIV);
                    m3u8.encryptionKey=encryptionKey;
                    m3u8.encryptionIV=encryptionIV;
                }
                if (hasInitSegment) {
                    ts.setInitSegmentInfo(initSegmentUri, segmentByteRange);
                }
                m3u8.addSeg(ts);
                tsIndex++;
                tsDuration = 0;
                hasStreamInfo = false;
                hasDiscontinuity = false;
                hasKey = false;
                hasInitSegment = false;
                method = null;
                encryptionKeyUri = null;
                encryptionIV = null;
                initSegmentUri = null;
                segmentByteRange = null;
            }


            m3u8.setTargetDuration(targetDuration);
            m3u8.setVersion(version);
            m3u8.setSequence(sequence);
            //去掉这个，不然有的m3u8无法下载  https://v5.szjal.cn/20210627/Nmb0o6pZ/index.m3u8
//            m3u8.setIsLive(!hasEndList);
            Log.e(TAG, "m3u8解析完毕");
            return m3u8;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            ProxyCacheUtils.close(response.body());
            ProxyCacheUtils.close(bufferedReader);
        }
    }

    public static M3U8 parseLocalM3U8Info(File localM3U8File, String videoUrl) throws Exception {
        if (!localM3U8File.exists()) {
            throw new VideoCacheException("Local M3U8 File not found");
        }
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        try {
            inputStreamReader = new InputStreamReader(new FileInputStream(localM3U8File));
            bufferedReader = new BufferedReader(inputStreamReader);
            M3U8 m3u8 = new M3U8(videoUrl);
            float tsDuration = 0;
            int targetDuration = 0;
            int tsIndex = 0;
            int version = 0;
            int sequence = 0;
            boolean hasDiscontinuity = false;
            boolean hasKey = false;
            boolean hasInitSegment = false;
            String method = null;
            String encryptionIV = null;
            String encryptionKeyUri = null;
            String initSegmentUri = null;
            String segmentByteRange = null;
            String line;
            //从网络读取出来的key
            byte[] encryptionKey=null;
            while ((line = bufferedReader.readLine()) != null) {
                LogUtils.i(TAG, "line = " + line);
                if (line.startsWith(M3U8Constants.TAG_PREFIX)) {
                    if (line.startsWith(M3U8Constants.TAG_MEDIA_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            tsDuration = Float.parseFloat(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_TARGET_DURATION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_TARGET_DURATION);
                        if (!TextUtils.isEmpty(ret)) {
                            targetDuration = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_VERSION)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_VERSION);
                        if (!TextUtils.isEmpty(ret)) {
                            version = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_MEDIA_SEQUENCE)) {
                        String ret = parseStringAttr(line, M3U8Constants.REGEX_MEDIA_SEQUENCE);
                        if (!TextUtils.isEmpty(ret)) {
                            sequence = Integer.parseInt(ret);
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_DISCONTINUITY)) {
                        hasDiscontinuity = true;
                    } else if (line.startsWith(M3U8Constants.TAG_KEY)) {
                        hasKey = true;
                        method = parseOptionalStringAttr(line, M3U8Constants.REGEX_METHOD);
                        String keyFormat = parseOptionalStringAttr(line, M3U8Constants.REGEX_KEYFORMAT);
                        if (!M3U8Constants.METHOD_NONE.equals(method)) {
                            encryptionIV = parseOptionalStringAttr(line, M3U8Constants.REGEX_IV);
                            if (M3U8Constants.KEYFORMAT_IDENTITY.equals(keyFormat) || keyFormat == null) {
                                if (M3U8Constants.METHOD_AES_128.equals(method)) {
                                    // The segment is fully encrypted using an identity key.
                                    encryptionKeyUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                                } else {
                                    // Do nothing. Samples are encrypted using an identity key,
                                    // but this is not supported. Hopefully, a traditional DRM
                                    // alternative is also provided.
                                }
                            } else {
                                // Do nothing.
                            }
                        }
                    } else if (line.startsWith(M3U8Constants.TAG_INIT_SEGMENT)) {
                        initSegmentUri = parseStringAttr(line, M3U8Constants.REGEX_URI);
                        if (!TextUtils.isEmpty(initSegmentUri)) {
                            hasInitSegment = true;
                            segmentByteRange = parseOptionalStringAttr(line, M3U8Constants.REGEX_ATTR_BYTERANGE);
                        }
                    }
                    continue;
                }
                M3U8Seg ts = new M3U8Seg();
                ts.setUrl(line);
                ts.setDuration(tsDuration);
                ts.setSegIndex(tsIndex);
                ts.setHasDiscontinuity(hasDiscontinuity);

                if (hasKey) {
                    if (encryptionKey == null) {
                        encryptionKey = parseKey(encryptionKeyUri);
                        ts.encryptionKey = encryptionKey;
                    }
                    ts.setHasKey(true);
                    ts.encryptionKey = encryptionKey;
                    ts.setMethod(method);
                    ts.setKeyUrl(encryptionKeyUri);
                    ts.setKeyIv(encryptionIV);
                    m3u8.encryptionKey=encryptionKey;
                    m3u8.encryptionIV=encryptionIV;
                }
                if (hasInitSegment) {
                    ts.setInitSegmentInfo(initSegmentUri, segmentByteRange);
                }
                m3u8.addSeg(ts);
                tsIndex++;
                tsDuration = 0;
                hasDiscontinuity = false;
                hasKey = false;
                hasInitSegment = false;
                method = null;
                encryptionKeyUri = null;
                encryptionIV = null;
                initSegmentUri = null;
                segmentByteRange = null;
            }
            m3u8.setTargetDuration(targetDuration);
            m3u8.setVersion(version);
            m3u8.setSequence(sequence);
            Log.e(TAG, "local m3u8解析完毕");
            return m3u8;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            ProxyCacheUtils.close(inputStreamReader);
            ProxyCacheUtils.close(bufferedReader);
        }
    }

    public static String parseStringAttr(String line, Pattern pattern) {
        if (pattern == null)
            return null;
        Matcher matcher = pattern.matcher(line);
        if (matcher.find() && matcher.groupCount() == 1) {
            return matcher.group(1);
        }
        return null;
    }
    public static byte[] parseKey(String url){
        if (TextUtils.isEmpty(url))
            return null;
        url = url.trim();
        if (!url.startsWith("http")) {
            return null;
        }
        try {
            Response response= OkHttpUtil.getInstance().requestSync(url,null);
            InputStream inStream = response.body().byteStream();
            //key只能是16位
            byte[] buffer = new byte[16];
            if ((inStream.read(buffer)) != -1) {
                return buffer;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private static String parseOptionalStringAttr(String line, Pattern pattern) {
        if (pattern == null)
            return null;
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String getM3U8AbsoluteUrl(String videoUrl, String line) {
        if (TextUtils.isEmpty(videoUrl) || TextUtils.isEmpty(line)) {
            return "";
        }
        if (videoUrl.startsWith("file://") || videoUrl.startsWith("/")) {
            return videoUrl;
        }
        String baseUriPath = getBaseUrl(videoUrl);
        String hostUrl = getHostUrl(videoUrl);
        if (line.startsWith("//")) {
            String tempUrl = getSchema(videoUrl) + ":" + line;
            return tempUrl.trim();
        }
        if (line.startsWith("/")) {
            String pathStr = getPathStr(videoUrl);
            String longestCommonPrefixStr = getLongestCommonPrefixStr(pathStr, line);
            if (hostUrl.endsWith("/")) {
                hostUrl = hostUrl.substring(0, hostUrl.length() - 1);
            }
            String tempUrl = hostUrl + longestCommonPrefixStr + line.substring(longestCommonPrefixStr.length());
            return tempUrl.trim();
        }
        String result=null;
        if (line.startsWith("http")) {
            result= line;
        }else {
            result= baseUriPath + line;
        }
        return result.trim();
    }
    private static String getSchema(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        int index = url.indexOf("://");
        if (index != -1) {
            String result = url.substring(0, index);
            return result;
        }
        return "";
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到https://xvideo.d666111.com/xvideo/taohuadao56152307/
     *
     * @param url
     * @return
     */
    public static String getBaseUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        int slashIndex = url.lastIndexOf("/");
        if (slashIndex != -1) {
            return url.substring(0, slashIndex + 1);
        }
        return url;
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到https://xvideo.d666111.com/
     *
     * @param url
     * @return
     */
    public static String getHostUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            URL formatURL = new URL(url);
            String host = formatURL.getHost();
            if (host == null) {
                return url;
            }
            int hostIndex = url.indexOf(host);
            if (hostIndex != -1) {
                int port = formatURL.getPort();
                String resultUrl;
                if (port != -1) {
                    resultUrl = url.substring(0, hostIndex + host.length()) + ":" + port + "/";
                } else {
                    resultUrl = url.substring(0, hostIndex + host.length()) + "/";
                }
                return resultUrl;
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 例如https://xvideo.d666111.com/xvideo/taohuadao56152307/index.m3u8
     * 我们希望得到   /xvideo/taohuadao56152307/index.m3u8
     *
     * @param url
     * @return
     */
    public static String getPathStr(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String hostUrl = getHostUrl(url);
        if (TextUtils.isEmpty(hostUrl)) {
            return url;
        }
        return url.substring(hostUrl.length() - 1);
    }
    /**
     * 获取两个字符串的最长公共前缀
     * /xvideo/taohuadao56152307/500kb/hls/index.m3u8   与     /xvideo/taohuadao56152307/index.m3u8
     * <p>
     * /xvideo/taohuadao56152307/500kb/hls/jNd4fapZ.ts  与     /xvideo/taohuadao56152307/500kb/hls/index.m3u8
     *
     * @param str1
     * @param str2
     * @return
     */
    public static String getLongestCommonPrefixStr(String str1, String str2) {
        if (TextUtils.isEmpty(str1) || TextUtils.isEmpty(str2)) {
            return "";
        }
        if (TextUtils.equals(str1, str2)) {
            return str1;
        }
        char[] arr1 = str1.toCharArray();
        char[] arr2 = str2.toCharArray();
        int j = 0;
        while (j < arr1.length && j < arr2.length) {
            if (arr1[j] != arr2[j]) {
                break;
            }
            j++;
        }
        return str1.substring(0, j);
    }

    /**
     * 将远程的m3u8结构保存到本地
     * @param m3u8File
     * @param m3u8
     * @throws Exception
     */
    public static void createLocalM3U8File(File m3u8File, M3U8 m3u8) throws Exception{
//        if (m3u8File.exists()) {
//            m3u8File.delete();
//            //todo 重新创建目录
//            return;
//            Log.e(TAG,"M3U8 文件已经存在");
//        }
        BufferedWriter bfw = new BufferedWriter(new FileWriter(m3u8File, false));
        bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
        bfw.write(M3U8Constants.TAG_VERSION + ":" + m3u8.getVersion() + "\n");
        bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + m3u8.getSequence() + "\n");
        bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + m3u8.getTargetDuration() + "\n");
        for (M3U8Seg m3u8Ts : m3u8.getSegList()) {
            if (m3u8Ts.hasInitSegment()) {
                String initSegmentInfo;
                if (m3u8Ts.getSegmentByteRange() != null) {
                    initSegmentInfo = "URI=\"" + m3u8Ts.getInitSegmentUri() + "\"" + ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() + "\"";
                } else {
                    initSegmentInfo = "URI=\"" + m3u8Ts.getInitSegmentUri() + "\"";
                }
                bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
            }
            if (m3u8Ts.isHasKey()) {
                if (m3u8Ts.getMethod() != null) {
                    String key = "METHOD=" + m3u8Ts.getMethod();
                    if (m3u8Ts.getKeyUrl() != null) {
                        String keyUri = m3u8Ts.getKeyUrl();
                        key += ",URI=\"" + keyUri + "\"";
                        URL keyURL = new URL(keyUri);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(keyURL.openStream()));
                        StringBuilder textBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            textBuilder.append(line);
                        }
                        boolean isMessyStr = VideoCacheUtils.isMessyCode(textBuilder.toString());
                        m3u8Ts.mIsMessyKey=isMessyStr;
                        File keyFile = new File(m3u8File.getParentFile().getAbsolutePath(), m3u8Ts.getLocalKeyUri());
                        FileOutputStream outputStream = new FileOutputStream(keyFile);
                        outputStream.write(textBuilder.toString().getBytes());
                        bufferedReader.close();
                        outputStream.close();
                        if (m3u8Ts.getKeyIv() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIv();
                        }
                    }
                    bfw.write(M3U8Constants.TAG_KEY + ":" + key + "\n");
                }
            }
            if (m3u8Ts.isHasDiscontinuity()) {
                bfw.write(M3U8Constants.TAG_DISCONTINUITY + "\n");
            }
            bfw.write(M3U8Constants.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() + ",\n");
            bfw.write(m3u8Ts.getUrl());
            bfw.newLine();
        }
        bfw.write(M3U8Constants.TAG_ENDLIST);
        bfw.flush();
        bfw.close();
    }

    /**
     * 创建本地代理的M3U8索引文件
     * @param m3u8File
     * @param m3u8
     * @param md5  这是videourl的MD5值
     * @param headers
     * @throws Exception
     */
    public static void createProxyM3U8File(File m3u8File, M3U8 m3u8, String md5, Map<String, String> headers) throws Exception {
        BufferedWriter bfw = new BufferedWriter(new FileWriter(m3u8File, false));
        bfw.write(M3U8Constants.PLAYLIST_HEADER + "\n");
        bfw.write(M3U8Constants.TAG_VERSION + ":" + m3u8.getVersion() + "\n");
        bfw.write(M3U8Constants.TAG_MEDIA_SEQUENCE + ":" + m3u8.getSequence() + "\n");
        bfw.write(M3U8Constants.TAG_TARGET_DURATION + ":" + m3u8.getTargetDuration() + "\n");

        for (M3U8Seg m3u8Ts : m3u8.getSegList()) {
            if (m3u8Ts.hasInitSegment()) {
                String initSegmentInfo = "URI=\"" + m3u8Ts.getInitSegProxyUrl(md5, headers) + "\"";
                if (m3u8Ts.getSegmentByteRange() != null) {
                    initSegmentInfo += ",BYTERANGE=\"" + m3u8Ts.getSegmentByteRange() +"\"";
                }
                bfw.write(M3U8Constants.TAG_INIT_SEGMENT + ":" + initSegmentInfo + "\n");
            }
            if (m3u8Ts.isHasKey()) {
                if (m3u8Ts.getMethod() != null) {
                    String key = "METHOD=" + m3u8Ts.getMethod();
                    if (m3u8Ts.getKeyUrl() != null) {
                        String keyUri = m3u8Ts.getKeyUrl();

                        URL keyURL = new URL(keyUri);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(keyURL.openStream()));
                        StringBuilder textBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            textBuilder.append(line);
                        }
                        m3u8Ts.mIsMessyKey= VideoCacheUtils.isMessyCode(textBuilder.toString());
                        File keyFile = new File(m3u8File.getParentFile().getAbsolutePath(), m3u8Ts.getLocalKeyUri());
                        FileOutputStream outputStream = new FileOutputStream(keyFile);
                        outputStream.write(textBuilder.toString().getBytes());
                        bufferedReader.close();
                        outputStream.close();
                        key += ",URI=\"" + keyUri + "\"";
                        if (m3u8Ts.getKeyIv() != null) {
                            key += ",IV=" + m3u8Ts.getKeyIv();
                        }
                    }
                    //不写入key
//                    bfw.write(M3U8Constants.TAG_KEY + ":" + key + "\n");
                }
            }
            if (m3u8Ts.isHasDiscontinuity()) {
                bfw.write(M3U8Constants.TAG_DISCONTINUITY + "\n");
            }
            bfw.write(M3U8Constants.TAG_MEDIA_DURATION + ":" + m3u8Ts.getDuration() + ",\n");
            bfw.write(m3u8Ts.getSegProxyUrl(md5, headers) + "\n");
            bfw.newLine();
        }
        bfw.write(M3U8Constants.TAG_ENDLIST);
        bfw.flush();
        bfw.close();
    }

    /**
     * 更新M3U8 索引文件中的端口号
     * @param proxyM3U8File
     * @param proxyPort
     * @return
     */
    public static boolean updateM3U8TsPortInfo(File proxyM3U8File, int proxyPort) {
        File tempM3U8File = null;
        if (proxyM3U8File.exists()) {
            File parentFile = proxyM3U8File.getParentFile();
            tempM3U8File = new File(parentFile, "temp_video.m3u8");
        }
        if (tempM3U8File != null) {
            BufferedWriter bfw = null;
            try {
                bfw = new BufferedWriter(new FileWriter(tempM3U8File, false));
            } catch (Exception e) {
                LogUtils.w(TAG, "Create buffered writer file failed, exception="+e);
                ProxyCacheUtils.close(bfw);
                return false;
            }

            InputStreamReader inputStreamReader = null;
            try {
                inputStreamReader = new InputStreamReader(new FileInputStream(proxyM3U8File));
            } catch (Exception e) {
                LogUtils.w(TAG, "Create stream reader failed, exception="+e);
                ProxyCacheUtils.close(inputStreamReader);
                return false;
            }

            BufferedReader bufferedReader;

            if (inputStreamReader != null && bfw != null) {
                bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                try {
                    while((line = bufferedReader.readLine()) != null) {
                        if (line.startsWith(ProxyCacheUtils.LOCAL_PROXY_URL)) {
                            if (sOldPort == 0) {
                                sOldPort = ProxyCacheUtils.getPortFromProxyUrl(line);
                                if (sOldPort == 0) {
                                    tempM3U8File.delete();
                                    return false;
                                } else if (sOldPort == proxyPort) {
                                    tempM3U8File.delete();
                                    return true;
                                }
                            }
                            line = line.replace(":" + sOldPort, ":" + proxyPort);
                            bfw.write(line + "\n");
                        } else {
                            bfw.write(line + "\n");
                        }
                    }
                } catch (Exception e) {
                    LogUtils.w(TAG, "Read proxy m3u8 file failed, exception="+e);
                    return false;
                } finally {
                    ProxyCacheUtils.close(bfw);
                    ProxyCacheUtils.close(inputStreamReader);
                    ProxyCacheUtils.close(bufferedReader);
                }
            } else {
                ProxyCacheUtils.close(bfw);
                ProxyCacheUtils.close(inputStreamReader);
                return false;
            }

            if (proxyM3U8File.exists() && tempM3U8File.exists()) {
                proxyM3U8File.delete();
                tempM3U8File.renameTo(proxyM3U8File);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

}
