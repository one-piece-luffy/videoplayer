package com.jeffmony.videocache.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;


import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TS文件元数据管理器
 * 用于保存和恢复TS文件的大小信息，支持断点续传
 */
public class TsMetaDataManager {
    private static final String TAG = "TsMetaDataManager";
    private static final String META_FILE_NAME = "ts_metadata.json";
    private static final long FLUSH_DELAY_MS = 3000; // 3秒后批量写入磁盘

    // 单例实例缓存，key为缓存目录的绝对路径
    private static final Map<String, TsMetaDataManager> instances = new ConcurrentHashMap<>();

    // 内存缓存：文件名 -> 文件大小
    private final Map<String, Long> tsMetaDataMap = new ConcurrentHashMap<>();

    // 元数据文件
    private final File metaFile;

    // 是否有未保存的修改
    private volatile boolean isDirty = false;

    // 延迟写入Handler
    private final Handler flushHandler;

    // ✅ 对象锁，用于保护复合操作
    private final Object lock = new Object();

    // 延迟写入任务
    private final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            flushMetaData();
        }
    };

    /**
     * 私有构造函数，使用单例模式
     */
    private TsMetaDataManager(File cacheDir) {
        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                LogUtils.e(TAG, "Failed to create cache directory: " + cacheDir.getAbsolutePath());
            }
        }

        // 元数据文件放在缓存目录下
        this.metaFile = new File(cacheDir, META_FILE_NAME);
        LogUtils.d(TAG, "Meta file path: " + metaFile.getAbsolutePath());

        // 加载已有的元数据
        loadMetaData();

        // 初始化Handler，用于延迟写入
        flushHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取TsMetaDataManager单例实例
     * @param cacheDir 缓存目录
     * @return TsMetaDataManager实例
     */
    public static TsMetaDataManager getInstance(File cacheDir) {
        if (cacheDir == null) {
            LogUtils.e(TAG, "CacheDir is null");
            return null;
        }

        // 规范化路径，确保同一个目录只创建一个实例
        String normalizedPath = normalizePath(cacheDir);

        TsMetaDataManager manager = instances.get(normalizedPath);
        if (manager == null) {
            synchronized (TsMetaDataManager.class) {
                manager = instances.get(normalizedPath);
                if (manager == null) {
                    // 确保传入的是目录
                    File normalizedDir = new File(normalizedPath);
                    if (!normalizedDir.isDirectory()) {
                        // 如果是文件，使用其父目录
                        File parentDir = normalizedDir.getParentFile();
                        if (parentDir != null) {
                            normalizedDir = parentDir;
                            normalizedPath = normalizedDir.getAbsolutePath();
                        } else {
                            LogUtils.e(TAG, "Invalid cache directory: " + cacheDir.getAbsolutePath());
                            return null;
                        }
                    }

                    manager = new TsMetaDataManager(normalizedDir);
                    instances.put(normalizedPath, manager);
                    LogUtils.d(TAG, "Created new TsMetaDataManager instance for: " + normalizedPath);
                }
            }
        }
        return manager;
    }

    /**
     * 规范化路径，去掉末尾的文件分隔符
     */
    private static String normalizePath(File dir) {
        String path = dir.getAbsolutePath();
        // 去掉尾随的文件分隔符
        while (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 从磁盘加载元数据
     */
    private void loadMetaData() {
        long start=System.currentTimeMillis();
        if (!metaFile.exists()) {
            LogUtils.d(TAG, "Meta file not exists, will create new");
            return;
        }

        try (InputStream is = new FileInputStream(metaFile);
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            if (sb.length() == 0) {
                LogUtils.d(TAG, "Meta file is empty");
                return;
            }

            JSONObject jsonObject = new JSONObject(sb.toString());
            Iterator<String> keys = jsonObject.keys();
            int count = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                long value = jsonObject.getLong(key);
                tsMetaDataMap.put(key, value);
                count++;
            }


        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to load meta data, will create new", e);
            // 如果加载失败，可能是文件损坏，删除重建
            if (metaFile.exists()) {
                FileUtils.deleteFile(metaFile);
            }
        }
        long end=System.currentTimeMillis();
        LogUtils.e(TAG, "size: " + tsMetaDataMap.size() + " time:"+(end-start));
    }

    /**
     * ✅ 保存TS文件的元数据到内存（延迟写入磁盘）
     * 使用 synchronized 保证复合操作的原子性
     * @param fileName TS文件名
     * @param fileSize 文件大小
     */
    public void saveTsMetaData(String fileName, long fileSize) {
        if (TextUtils.isEmpty(fileName) || fileSize <= 0) {
            LogUtils.w(TAG, "Invalid meta data: fileName=" + fileName + ", fileSize=" + fileSize);
            return;
        }

        synchronized (lock) {
            // 先保存到内存
            tsMetaDataMap.put(fileName, fileSize);
            isDirty = true;

            // 取消之前的延迟任务，重新开始计时
            flushHandler.removeCallbacks(flushRunnable);
            flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);

        }
    }

    /**
     * ✅ 批量保存多个TS文件的元数据
     * 使用 synchronized 保证复合操作的原子性
     * @param metaDataMap 文件名->文件大小映射
     */
    public void saveTsMetaDataBatch(Map<String, Long> metaDataMap) {
        if (metaDataMap == null || metaDataMap.isEmpty()) {
            return;
        }

        synchronized (lock) {
            tsMetaDataMap.putAll(metaDataMap);
            isDirty = true;

            flushHandler.removeCallbacks(flushRunnable);
            flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);

        }
    }

    /**
     * ✅ 获取TS文件的元数据（从内存读取）
     * 不需要同步，ConcurrentHashMap 保证读取安全
     * @param fileName TS文件名
     * @return 文件大小，-1表示未找到
     */
    public long getTsMetaData(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return -1;
        }

        Long size = tsMetaDataMap.get(fileName);
        if (size != null && size > 0) {
//            LogUtils.e(TAG, "size: "+tsMetaDataMap.size()+" " + fileName + " = " + size);
            return size;
        }

        return -1;
    }

    /**
     * ✅ 获取所有TS文件的元数据（拷贝一份，避免外部修改）
     * @return 文件名->文件大小映射
     */
    public Map<String, Long> getAllMetaData() {
        return new HashMap<>(tsMetaDataMap);
    }

    /**
     * ✅ 检查是否包含某个TS文件的元数据
     * 不需要同步，ConcurrentHashMap 保证读取安全
     * @param fileName TS文件名
     * @return true表示包含
     */
    public boolean containsTsMetaData(String fileName) {
        return !TextUtils.isEmpty(fileName) && tsMetaDataMap.containsKey(fileName);
    }

    /**
     * ✅ 删除某个TS文件的元数据
     * 使用 synchronized 保证复合操作的原子性
     * @param fileName TS文件名
     */
    public void removeTsMetaData(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return;
        }

        synchronized (lock) {
            Long removed = tsMetaDataMap.remove(fileName);
            if (removed != null) {
                isDirty = true;
                flushHandler.removeCallbacks(flushRunnable);
                flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);
            }
        }
    }

    /**
     * ✅ 清理孤儿元数据（对应的TS文件已不存在）
     * 使用 synchronized 保证复合操作的原子性
     * @param cacheDir 缓存目录
     */
    public void cleanOrphanMetaData(File cacheDir) {
        if (cacheDir == null || !cacheDir.exists()) {
            return;
        }

        synchronized (lock) {
            // 获取所有TS文件
            File[] files = cacheDir.listFiles();
            if (files == null || files.length == 0) {
                // 如果没有TS文件，清理所有元数据
                if (!tsMetaDataMap.isEmpty()) {
                    tsMetaDataMap.clear();
                    isDirty = true;
                    flushHandler.removeCallbacks(flushRunnable);
                    flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);
                }
                return;
            }

            // 收集所有TS文件名
            Map<String, Boolean> existingFiles = new HashMap<>();
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".ts")) {
                    existingFiles.put(file.getName(), true);
                }
            }

            // 删除不存在的文件对应的元数据
            int removedCount = 0;
            Iterator<Map.Entry<String, Long>> iterator = tsMetaDataMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (!existingFiles.containsKey(entry.getKey())) {
                    iterator.remove();
                    removedCount++;
                    isDirty = true;
                }
            }

            if (removedCount > 0) {
                flushHandler.removeCallbacks(flushRunnable);
                flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);
            }
        }
    }

    /**
     * ✅ 验证文件是否完整（对比实际大小与元数据）
     * 不需要同步，ConcurrentHashMap 保证读取安全
     * @param fileName TS文件名
     * @param actualSize 实际文件大小
     * @return true表示完整
     */
    public boolean isFileComplete(String fileName, long actualSize) {
        if (TextUtils.isEmpty(fileName) || actualSize <= 0) {
            return false;
        }

        Long expectedSize = tsMetaDataMap.get(fileName);
        if (expectedSize == null || expectedSize <= 0) {
            // 没有元数据，无法验证
            return false;
        }

        boolean complete = VideoCacheUtils.sizeSimilar(actualSize, expectedSize);
        LogUtils.d(TAG, "File " + fileName + " complete: " + complete +
                " (actual=" + actualSize + ", expected=" + expectedSize + ")");
        return complete;
    }

    /**
     * ✅ 强制刷新到磁盘（应用退出时调用）
     * 使用 synchronized 保证复合操作的原子性
     */
    public void flushOnExit() {
        synchronized (lock) {
            flushHandler.removeCallbacks(flushRunnable);
            flushMetaData();
        }
    }

    /**
     * ✅ 立即刷新到磁盘（不延迟）
     * 使用 synchronized 保证复合操作的原子性
     */
    public void flushImmediate() {
        synchronized (lock) {
            flushHandler.removeCallbacks(flushRunnable);
            flushMetaData();
        }
    }

    /**
     * ✅ 将内存数据写入磁盘
     * 由调用方持有锁，或使用 synchronized 保护
     */
    private void flushMetaData() {
        // 注意：此方法由调用方在 synchronized(lock) 中调用
        // 或者由 flushRunnable 调用，也需要获取锁
        long start=System.currentTimeMillis();
        if (!isDirty) {
            LogUtils.d(TAG, "No dirty data to flush");
            return;
        }

        // 检查是否有数据
        if (tsMetaDataMap.isEmpty()) {
            LogUtils.d(TAG, "Meta data is empty, deleting meta file");
            if (metaFile.exists()) {
                FileUtils.deleteFile(metaFile);
            }
            isDirty = false;
            return;
        }

        try {
            // 确保父目录存在
            File parentDir = metaFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    LogUtils.e(TAG, "Failed to create parent directory: " + parentDir.getAbsolutePath());
                    return;
                }
            }

            // ✅ 构建JSON，使用快照避免并发修改问题
            Map<String, Long> snapshot = new HashMap<>(tsMetaDataMap);
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
                jsonObject.put(entry.getKey(), entry.getValue());
            }

            String jsonString = jsonObject.toString();

            // 先写入临时文件，再重命名，保证原子性
            File tempFile = new File(metaFile.getParent(), metaFile.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(jsonString.getBytes());
                fos.flush();
            }

            // 删除旧文件
            if (metaFile.exists()) {
                FileUtils.deleteFile(metaFile);
            }

            // 重命名临时文件
            FileUtils.rename(tempFile, metaFile);

            isDirty = false;
            LogUtils.d(TAG, "Successfully flushed meta data to disk");

        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to flush meta data", e);
        }
        long end=System.currentTimeMillis();
        Log.e(TAG,"load ts meta size:"+tsMetaDataMap.size()+" time:"+(end-start));
    }

    /**
     * ✅ 获取元数据文件大小
     * 不需要同步
     * @return 文件大小，-1表示不存在
     */
    public long getMetaFileSize() {
        if (metaFile.exists()) {
            return metaFile.length();
        }
        return -1;
    }

    /**
     * ✅ 获取缓存中的条目数量
     * 不需要同步
     */
    public int getEntryCount() {
        return tsMetaDataMap.size();
    }

    /**
     * ✅ 清空所有元数据（谨慎使用）
     * 使用 synchronized 保证复合操作的原子性
     */
    public void clearAll() {
        synchronized (lock) {
            if (!tsMetaDataMap.isEmpty()) {
                tsMetaDataMap.clear();
                isDirty = true;
                flushHandler.removeCallbacks(flushRunnable);
                flushHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS);
            }
        }
    }

    /**
     * ✅ 删除元数据文件（谨慎使用）
     * 使用 synchronized 保证复合操作的原子性
     */
    public void deleteMetaFile() {
        synchronized (lock) {
            if (metaFile.exists()) {
                FileUtils.deleteFile(metaFile);
            }
            tsMetaDataMap.clear();
            isDirty = false;
            flushHandler.removeCallbacks(flushRunnable);
        }
    }

    /**
     * 获取单例实例的调试信息
     */
    public static String getInstanceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("TsMetaDataManager instances: ").append(instances.size()).append("\n");
        for (Map.Entry<String, TsMetaDataManager> entry : instances.entrySet()) {
            sb.append("  ").append(entry.getKey())
                    .append(" -> entries: ").append(entry.getValue().getEntryCount())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * 销毁所有实例（仅在应用退出时使用）
     */
    public static void destroyAll() {
        for (TsMetaDataManager manager : instances.values()) {
            manager.flushOnExit();
        }
        instances.clear();
        LogUtils.d(TAG, "Destroyed all instances");
    }
}