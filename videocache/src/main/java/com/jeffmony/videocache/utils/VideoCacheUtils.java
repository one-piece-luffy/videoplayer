package com.jeffmony.videocache.utils;


import android.text.TextUtils;

import com.jeffmony.videocache.model.VideoCacheInfo;

import java.io.Closeable;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoCacheUtils {

    private static final String TAG = "VideoDownloadUtils";
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    public static final String VIDEO_SUFFIX = ".video";
    public static final String LOCAL_M3U8 = "local.m3u8";
    public static final String REMOTE_M3U8 = "remote.m3u8";
    public static final String MERGED_MP4 = "merged.mp4";
    public static final String SEGMENT_PREFIX = "video_";
    public static final String M3U8_SUFFIX = ".m3u8";
    public static final String INIT_SEGMENT_PREFIX = "init_video_";


    public static String computeMD5(String string) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digestBytes = messageDigest.digest(string.getBytes());
            return bytesToHexString(digestBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                ub == Character.UnicodeBlock.GENERAL_PUNCTUATION ||
                ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    public static boolean isMessyCode(String strName) {
        Pattern p = Pattern.compile("\\s*|t*|r*|n*");
        Matcher m = p.matcher(strName);
        String after = m.replaceAll("");
        String temp = after.replaceAll("\\p{P}", "");
        char[] ch = temp.trim().toCharArray();
        float chLength = ch.length;
        float count = 0;
        for (char c : ch) {
            if (!Character.isLetterOrDigit(c)) {
                if (!isChinese(c)) {
                    count = count + 1;
                }
            }
        }
        if (chLength <= 0)
            return false;
        float result = count / chLength;
        if (result > 0.4) {
            return true;
        } else {
            return false;
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LogUtils.w(TAG, "VideoProxyCacheUtils close " + closeable + " failed, exception = " + e);
            }
        }
    }

    public static String getPercent(float percent) {
        DecimalFormat format = new DecimalFormat("###.00");
        return format.format(percent) + "%";
    }

    public static boolean isFloatEqual(float f1, float f2) {
        if (Math.abs(f1 - f2) < 0.01f) {
            return true;
        }
        return false;
    }

    public static String getSuffixName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < name.length()) ? name.substring(dotIndex) : "";
    }

    /**
     * 关闭资源
     *
     * @param closeables
     */
    public static void close(Closeable... closeables) {
        int length = closeables.length;
        try {
            for (int i = 0; i < length; i++) {
                Closeable closeable = closeables[i];
                if (null != closeable)
                    closeables[i].close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < length; i++) {
                closeables[i] = null;
            }
        }
    }


    /**
     * 是否是非法连接
     */
    public static boolean isIllegalUrl(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("http")) {
            return true;
        }
        return false;

    }


    public static String getTimeFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss");//设置日期格式
        Date now = new Date();
        return sdf.format(now);
    }

    public static String getFileName(VideoCacheInfo cacheInfo) {

        String fileName;
        if (!TextUtils.isEmpty(cacheInfo.name)) {
            fileName = cacheInfo.name;

        } else if (!TextUtils.isEmpty(cacheInfo.getMd5())) {
            fileName = cacheInfo.getMd5();
        } else {
            fileName = ProxyCacheUtils.computeMD5(cacheInfo.getVideoUrl());
        }
        if (TextUtils.isEmpty(fileName) || "0".equals(fileName)) {
            UUID uuid = UUID.randomUUID();
            fileName = uuid.toString();
        }
        if (!TextUtils.isEmpty(fileName) && fileName.length() > 35) {
            fileName = fileName.substring(0, 35);
        }
        if (TextUtils.isEmpty(fileName) || "0".equals(fileName)) {
            String arr = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(arr.charAt(random.nextInt(arr.length())));
            }
            fileName = sb.toString() + System.currentTimeMillis();


        }

        return fileName;
    }
    public static String getFileName(String name,String url) {

        String fileName;
        if (!TextUtils.isEmpty(name)) {
            fileName = filterFileName(name);

        }  else {
            fileName = ProxyCacheUtils.computeMD5(url);
        }
        if (TextUtils.isEmpty(fileName) || "0".equals(fileName)) {
            UUID uuid = UUID.randomUUID();
            fileName = uuid.toString();
        }
        if (!TextUtils.isEmpty(fileName) && fileName.length() > 35) {
            fileName = fileName.substring(0, 35);
        }
        if (TextUtils.isEmpty(fileName) || "0".equals(fileName)) {
            String arr = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(arr.charAt(random.nextInt(arr.length())));
            }
            fileName = sb.toString() + System.currentTimeMillis();


        }

        return fileName;
    }

    /**
     * 过滤一些不合法的文件名
     */
    public static String filterFileName(String fileName){
        if(!TextUtils.isEmpty(fileName)){
            String specialChars = "/:<>?|'\"*\\";

            // 遍历文件名中的每个字符，检查是否为特殊字符
            for (int i = 0; i < fileName.length(); i++) {
                char c = fileName.charAt(i);
                if (specialChars.indexOf(c) != -1) {
                    // 如果找到特殊字符，则将其替换为下划线 "_"
                    fileName = fileName.replace(String.valueOf(c), "_");
                }
            }
            fileName = fileName.trim();
        }

        return fileName;

    }
}
