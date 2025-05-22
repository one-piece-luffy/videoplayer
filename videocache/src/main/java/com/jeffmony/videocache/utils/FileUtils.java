package com.jeffmony.videocache.utils;

import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {
    /**
     * 文件重命名
     * @param old
     * @param newFile
     */
    public static void rename(File old, File newFile){
        boolean result=old.renameTo(newFile);
        if(!result){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Path source = Paths.get(old.getAbsolutePath());
                Path dest = Paths.get(newFile.getAbsolutePath());

                try {
                    Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // 处理异常情况
                    System.err.println("文件移动失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        }
    }
}
