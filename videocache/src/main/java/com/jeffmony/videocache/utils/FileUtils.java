package com.jeffmony.videocache.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {
    private static final String TAG="FileUtils";
    /**
     * 文件重命名
     * @param old
     * @param newFile
     */
    public static void rename(File old, File newFile){
        boolean result=old.renameTo(newFile);
        if(!result){
            Log.e(TAG,"rename失败");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Path source = Paths.get(old.getAbsolutePath());
                Path dest = Paths.get(newFile.getAbsolutePath());

                try {
                    Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // 处理异常情况
                    Log.e(TAG,"文件移动失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        }else {
//            Log.e(TAG,"rename suc");
        }
    }

    /**
     * 处理临时文件重命名
     * 因为播放器和缓存下载 两个可能同时下载，同时处理一个ts，所以要先判断文件是否存在再决定是否执行重命名
     * @param tmpFile 临时文件
     * @param file 最终文件
     */
    public static void handleRename(File tmpFile, File file) {
        //
        if (file.exists() && VideoCacheUtils.sizeSimilar(file.length(),tmpFile.length())) {
            deleteFile(tmpFile);
            Log.e(TAG,"文件已存在，删除临时文件:"+tmpFile.getName());
            return;
        }
        FileUtils.rename(tmpFile, file);
    }


    /**
     * 删除文件
     * @param file
     */
    public static void deleteFile( File file) {

        try {
            if (file.exists()) {
                Path path = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    path = Paths.get(file.getAbsolutePath());
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        Log.e(TAG,"出错了",e);
                    }
                } else {
                    boolean result = file.delete();
                    if (!result) {
                        file.deleteOnExit();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG,"出错了",e);
        }

    }
}
