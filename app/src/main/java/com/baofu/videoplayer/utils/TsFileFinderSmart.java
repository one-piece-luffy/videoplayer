package com.baofu.videoplayer.utils;

import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TsFileFinderSmart {

    /**
     * 智能选择最优方法：根据 Android 版本自动适配
     */
    public static List<String> getTsFiles(String directoryPath) {
        // Android 8.0+ (API 26+) 使用 NIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getTsFilesNIO(directoryPath);
        } else {
            // Android 8.0 以下使用传统方式（但优化过）
            return getTsFilesOptimized(directoryPath);
        }
    }

    /**
     * NIO 方式 (API 26+)
     */
    private static List<String> getTsFilesNIO(String directoryPath) {
        List<String> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


            try {
                Path dir = Paths.get(directoryPath);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir,
                        path -> Files.isRegularFile(path) &&
                                path.toString().endsWith(".ts"))) {

                    for (Path path : stream) {
                        result.add(path.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                // 失败时回退
                return getTsFilesOptimized(directoryPath);
            }
        }
        return result;
    }

    /**
     * 优化后的传统方式 (所有版本)
     */
    private static List<String> getTsFilesOptimized(String directoryPath) {
        List<String> result = new ArrayList<>();
        File dir = new File(directoryPath);

        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }

        // 预估容量，减少扩容
        result = new ArrayList<>(files.length);

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName();
                if (name.endsWith(".ts") || name.endsWith(".TS")) {
                    result.add(name);
                }
            }
        }

        return result;
    }
}