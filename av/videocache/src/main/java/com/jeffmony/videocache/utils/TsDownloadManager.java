package com.jeffmony.videocache.utils;

import android.util.Log;

import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.utils.OkHttpUtil;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Response;

/**
 * TS文件下载管理器
 *
 * 功能：
 * 1. 管理每个TS文件的下载请求
 * 2. 支持单个取消和全局取消
 * 3. 通过OkHttp的Call.cancel()实现真正的网络请求中断
 */
public class TsDownloadManager {

    private static final String TAG = "TsDownloadManager";

    /**
     * 存储所有正在进行的下载任务
     * Key: 文件名, Value: DownloadTask
     */
    private final ConcurrentHashMap<String, DownloadTask> mActiveDownloads = new ConcurrentHashMap<>();

    /**
     * 全局取消标志
     * 当设置为true时，所有新提交的下载任务将被拒绝
     */
    private final AtomicBoolean mGlobalCancel = new AtomicBoolean(false);

    /**
     * 下载任务封装类
     * 包含下载所需的所有信息和状态
     */
    public static class DownloadTask {
        public final String fileName;           // 文件名
        public final String url;                // 下载URL
        public final Map<String, String> headers; // 请求头

        public volatile Call call;              // OkHttp Call对象，用于取消
        public volatile boolean isCancelled = false; // 是否已取消
        public volatile boolean isCompleted = false; // 是否已完成
        public volatile Response response;      // HTTP响应
        public volatile IOException error;      // 错误信息

        public DownloadTask(String fileName, String url, Map<String, String> headers) {
            this.fileName = fileName;
            this.url = url;
            this.headers = headers;
        }

        /**
         * 取消下载
         * 设置取消标志，并调用OkHttp的cancel()中断网络请求
         */
        public void cancel() {
            isCancelled = true;
            if (call != null) {
                call.cancel();
                Log.i(TAG, "Cancelled download: " + fileName);
            }
        }

        /**
         * 判断是否已取消
         */
        public boolean isCancelled() {
            return isCancelled || (call != null && call.isCanceled());
        }
    }

    /**
     * 开始下载（同步执行，但支持取消）
     *
     * @param fileName 文件名
     * @param url 下载地址
     * @param headers 请求头
     * @return DownloadTask 包含下载结果
     */
    public DownloadTask startDownload(String fileName, String url, Map<String, String> headers) {
        // 如果全局取消标志为true，拒绝下载
        if (mGlobalCancel.get()) {
            Log.w(TAG, "Global cancel active, skip: " + fileName);
            return null;
        }

        // 创建下载任务并加入活跃列表
        DownloadTask task = new DownloadTask(fileName, url, headers);
        mActiveDownloads.put(fileName, task);

        try {
            // 执行下载（支持取消）
            task.response = executeWithCancelSupport(task);

            // 检查是否被取消
            if (task.isCancelled()) {
                Log.i(TAG, "Download cancelled: " + fileName);
                return task;
            }

            // 检查HTTP响应状态
            if (task.response != null && !task.response.isSuccessful()) {
                task.error = new IOException("HTTP " + task.response.code());
                return task;
            }

            task.isCompleted = true;
            return task;

        } catch (IOException e) {
            // 如果是取消导致的异常，不算错误
            if (task.isCancelled() || (e.getMessage() != null && e.getMessage().contains("cancel"))) {
                Log.i(TAG, "Download cancelled via exception: " + fileName);
//                PlayerProgressListenerManager.getInstance().log("Download cancelled via exception: " + fileName);
            } else {
                task.error = e;
                Log.e(TAG, "Download error: " + fileName, e);
            }
            return task;
        } finally {
            // 如果未被取消，从活跃列表中移除
            if (!task.isCancelled()) {
                mActiveDownloads.remove(fileName, task);
            }
        }
    }

    /**
     * 执行带取消支持的HTTP请求
     *
     * @param task 下载任务
     * @return Response
     * @throws IOException 网络异常或取消异常
     */
    private Response executeWithCancelSupport(DownloadTask task) throws IOException {
        // 检查是否已取消
        if (task.isCancelled()) {
            throw new IOException("Download cancelled before start");
        }

        // 创建OkHttp Call
        Call call = OkHttpUtil.getInstance().createCall(task.url, task.headers);
        task.call = call;

        // 执行同步请求（此过程可被call.cancel()中断）
        Response response = call.execute();

        // 执行完成后再次检查取消状态
        if (task.isCancelled()) {
            if (response != null) {
                response.close();
            }
            throw new IOException("Download cancelled after execution");
        }

        return response;
    }

    /**
     * 取消单个下载任务
     */
    public boolean cancelDownload(String fileName) {
        DownloadTask task = mActiveDownloads.remove(fileName);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }

    /**
     * 取消所有正在进行的下载
     * 设置全局取消标志，并逐个取消
     */
    public void cancelAllDownloads() {
        Log.i(TAG, "Cancelling all downloads, count: " + mActiveDownloads.size());
        mGlobalCancel.set(true);

        for (DownloadTask task : mActiveDownloads.values()) {
            task.cancel();
        }
        mActiveDownloads.clear();
    }

    /**
     * 重置全局取消标志（用于重新开始下载）
     */
    public void resetGlobalCancel() {
        mGlobalCancel.set(false);
    }

    /**
     * 获取活跃下载数量
     */
    public int getActiveDownloadCount() {
        return mActiveDownloads.size();
    }

    /**
     * 检查是否有活跃下载
     */
    public boolean hasActiveDownloads() {
        return !mActiveDownloads.isEmpty();
    }
}