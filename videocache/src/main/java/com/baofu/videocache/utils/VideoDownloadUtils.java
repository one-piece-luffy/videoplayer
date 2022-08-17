package com.baofu.videocache.utils;


import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.DIRECTORY_MOVIES;
import static android.os.Environment.DIRECTORY_MUSIC;
import static android.os.Environment.DIRECTORY_PICTURES;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoDownloadUtils {

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
}
