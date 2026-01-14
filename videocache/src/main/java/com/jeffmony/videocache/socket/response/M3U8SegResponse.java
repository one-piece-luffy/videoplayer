package com.jeffmony.videocache.socket.response;

import android.util.Log;

import com.jeffmony.videocache.CacheConstants;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.VideoInfoParseManager;
import com.jeffmony.videocache.VideoLockManager;
import com.jeffmony.videocache.VideoProxyCacheManager;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.m3u8.M3U8Seg;
import com.jeffmony.videocache.socket.request.HttpRequest;
import com.jeffmony.videocache.socket.request.ResponseState;
import com.jeffmony.videocache.utils.AES128Utils;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.OkHttpUtil;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.StorageUtils;
import com.jeffmony.videocache.utils.VideoCacheUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Response;

/**
 * @author jeffmony
 * M3U8-TS视频的local server端（优化版）
 */
public class M3U8SegResponse extends BaseResponse {

    private static final String TAG = "M3U8SegResponse";
    private static final String TEMP_POSTFIX = ".player_downloading";

    // 共享的下载线程池，避免为每个请求创建线程池
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(3);

    private final String mParentUrl;
    private final File mSegFile;
    private final String mSegUrl;
    private final String mM3U8Md5;
    private final int mSegIndex;
    private final String mVideoName;
    private final String mFileName;

    private long mSegLength;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private Future<?> downloadFuture;

    public M3U8SegResponse(HttpRequest request, String parentUrl, String videoUrl,
                           Map<String, String> headers, long time, String fileName) throws Exception {
        super(request, videoUrl, headers, time);

        mParentUrl = parentUrl;
        mSegUrl = videoUrl;
        mSegFile = new File(mCachePath, fileName);
        mFileName = mSegFile.getName();
        mM3U8Md5 = parseM3U8Md5(fileName);
        mSegIndex = parseSegIndex(fileName);
        mVideoName = ProxyCacheUtils.decodeUriWithBase64(mHeaders.get(CacheConstants.HEADER_KEY_NAME));

        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mHeaders.put("Connection", "close");

        mResponseState = ResponseState.OK;

        // 通知后台任务开始下载当前片段
        VideoProxyCacheManager.getInstance().seekToCacheTaskFromServerByM3u8(mParentUrl, mSegIndex);

//        LogUtils.d(TAG, "Created response for segIndex=" + mSegIndex +
//                ", file=" + mFileName + ", videoName=" + mVideoName);
    }

    @Override
    public void sendBody(Socket socket, OutputStream outputStream, long pending) throws Exception {
        Object lock = VideoLockManager.getInstance().getLock(mM3U8Md5);

        try {
            // 等待文件就绪或超时
            waitForFileReady(lock);

            if (shouldSendResponse(socket, mM3U8Md5) && mSegFile.exists()) {
                sendFileContent(outputStream);
            } else {
                outputStream.write(null, 0, 0);
                LogUtils.w(TAG, "Cannot send file, socket closed or file missing: " + mFileName);
            }

        } finally {
            // 确保清理资源
            cancelDownloadTask();
        }
    }

    /**
     * 等待文件就绪（带超时）
     */
    private void waitForFileReady(Object lock) throws InterruptedException, IOException {
        final long timeout = TIME_OUT;
        final long waitInterval = WAIT_TIME;
        long waited = 0;

        while (!mSegFile.exists() && waited < timeout) {
            // 如果文件不存在且没有在下载，则启动下载
            if (isDownloading.compareAndSet(false, true)) {
                startDownloadTask();
            }

            // 等待一段时间
            synchronized (lock) {
                lock.wait(waitInterval);
            }

            waited += waitInterval;

            // 检查文件是否完整下载
            if (isFileDownloadComplete()) {
                break;
            }
        }

        // 超时处理
        if (!mSegFile.exists()) {
            LogUtils.e(TAG, "Timeout waiting for file: " + mFileName +
                    " (waited " + waited + "ms)");
            PlayerProgressListenerManager.getInstance().log(
                    "==timeout " + mFileName + "  " + waited + " ms==");
        }
    }

    /**
     * 启动下载任务
     */
    private void startDownloadTask() {
        try {
            downloadFuture = DOWNLOAD_EXECUTOR.submit(() -> {
                try {
                    downloadSegment();
                } catch (Exception e) {
                    LogUtils.e(TAG, "Download failed for " + mFileName, e);
                    PlayerProgressListenerManager.getInstance().log(
                            "播放器ts下载失败:" + mFileName + " msg:" + e.getMessage());
                } finally {
                    isDownloading.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            isDownloading.set(false);
            LogUtils.w(TAG, "Download task rejected for " + mFileName+ e.getMessage());
        }
    }

    /**
     * 下载TS片段
     */
    private void downloadSegment()  {
        LogUtils.i(TAG, "开始下载TS: segIndex=" + mSegIndex + ", file=" + mFileName);

        // 确保目录存在
        File parentDir = mSegFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                LogUtils.e(TAG, "Failed to create directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        // 获取M3U8信息
        M3U8 m3u8 = VideoInfoParseManager.getInstance().m3u8;
        M3U8Seg ts = findTsInM3u8(m3u8, mSegUrl);

        if (ts == null) {
            ts = new M3U8Seg();
            ts.setUrl(mSegUrl);
        }

        // 下载文件
        try {
            downloadAndProcessFile(ts);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 在M3U8中查找对应的TS
     */
    private M3U8Seg findTsInM3u8(M3U8 m3u8, String url) {
        if (m3u8 == null || m3u8.getSegList() == null) {
            return null;
        }

        for (M3U8Seg seg : m3u8.getSegList()) {
            if (seg.getUrl().equals(url)) {
                return seg;
            }
        }
        return null;
    }

    /**
     * 下载并处理文件
     */
    private void downloadAndProcessFile(M3U8Seg ts)  {
        File tempFile = new File(mSegFile.getParentFile(), mFileName + TEMP_POSTFIX);

        try {
            PlayerProgressListenerManager.getInstance().log(
                    "播放器正在下载:" + mVideoName + " " + mFileName);

            Response response = OkHttpUtil.getInstance().requestSync(mSegUrl, mHeaders);
            int responseCode = response.code();

            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                ts.setRetryCount(0);

                // 处理响应
                processResponse(response, ts, tempFile);

            } else {
                handleHttpError(responseCode, ts);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 清理临时文件
            cleanupTempFile(tempFile);
        }
    }

    /**
     * 处理成功的HTTP响应
     */
    private void processResponse(Response response, M3U8Seg ts, File tempFile) throws IOException {
        try (InputStream inputStream = response.body().byteStream()) {
            long contentLength = response.body().contentLength();

            // 下载到临时文件
            downloadToTempFile(inputStream, tempFile, contentLength);

            // 解密处理（如果需要）
            if (shouldDecrypt(ts)) {
                decryptAndSave(tempFile, ts);
            } else {
                FileUtils.handleRename(tempFile, mSegFile);
            }

            // 更新片段信息
            updateTsInfo(ts, contentLength);

            PlayerProgressListenerManager.getInstance().log("播放器ts下载完成:" + mFileName);
            LogUtils.i(TAG, "TS下载完成: " + mFileName);

        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to process response for " + mFileName, e);
            throw new IOException("Failed to process TS file", e);
        }
    }

    /**
     * 下载到临时文件
     */
    private void downloadToTempFile(InputStream inputStream, File tempFile, long expectedLength)
            throws IOException {

        try (ReadableByteChannel rbc = Channels.newChannel(inputStream);
             FileOutputStream fos = new FileOutputStream(tempFile);
             FileChannel foutc = fos.getChannel()) {

            foutc.transferFrom(rbc, 0, Long.MAX_VALUE);

            // 验证文件大小
            validateFileSize(tempFile, expectedLength);
        }
    }

    /**
     * 验证文件大小
     */
    private void validateFileSize(File file, long expectedLength) throws IOException {
        if (expectedLength > 0) {
            long actualLength = file.length();
            if (!VideoCacheUtils.sizeSimilar(expectedLength, actualLength)) {
                String error = String.format(
                        "File size mismatch: expected=%d, actual=%d, file=%s",
                        expectedLength, actualLength, file.getName());
                PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:" + error);
                throw new IOException(error);
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt(); // 恢复中断状态
//                    // 处理中断逻辑，例如退出循环或任务
//                }
            }
        }
    }

    /**
     * 判断是否需要解密
     */
    private boolean shouldDecrypt(M3U8Seg ts) {
        M3U8 m3u8 = VideoInfoParseManager.getInstance().m3u8;
        byte[] key = (ts.encryptionKey != null) ? ts.encryptionKey :
                (m3u8 != null ? m3u8.encryptionKey : null);
        return key != null;
    }

    /**
     * 解密并保存文件
     */
    private void decryptAndSave(File tempFile, M3U8Seg ts) throws IOException {
        M3U8 m3u8 = VideoInfoParseManager.getInstance().m3u8;
        byte[] key = (ts.encryptionKey != null) ? ts.encryptionKey :
                (m3u8 != null ? m3u8.encryptionKey : null);
        String iv = (ts.encryptionKey != null) ? ts.getKeyIv() :
                (m3u8 != null ? m3u8.encryptionIV : null);

        File decryptedTempFile = new File(tempFile.getParent(), "decrypted_" + tempFile.getName());

        if (AES128Utils.decryptFile(tempFile, decryptedTempFile, key, iv)) {
            FileUtils.handleRename(decryptedTempFile, mSegFile);
        } else {
            String error="Decryption failed for " + mFileName;
            PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:" + error);
            throw new IOException(error);
        }
    }

    /**
     * 更新TS信息
     */
    private void updateTsInfo(M3U8Seg ts, long contentLength) {
        mSegLength = (contentLength > 0) ? contentLength : mSegFile.length();
        ts.setContentLength(mSegLength);

        // 通知监听器
        if (mSegIndex == 0) {
            PlayerProgressListenerManager.getInstance().getListener().onTaskFirstTsDownload(mFileName);
        }
    }

    /**
     * 处理HTTP错误
     */
    private void handleHttpError(int responseCode, M3U8Seg ts) {
        LogUtils.w(TAG, "HTTP error for " + mFileName + ": " + responseCode);
        PlayerProgressListenerManager.getInstance().log(
                "播放器ts下载失败:" + ts.getSegName() + " code:" + responseCode);

        // 简单的错误处理，不重试
        ts.setRetryCount(ts.getRetryCount() + 1);

        // 如果是服务器错误，短暂等待
        if (responseCode == 503 || responseCode == 429) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFile(File tempFile) {
        if (tempFile.exists()) {
            if (isFileDownloadComplete(tempFile)) {
                // 如果文件完整，重命名
                FileUtils.handleRename(tempFile, mSegFile);
            } else {
                // 删除不完整的文件
                if (!tempFile.delete()) {
                    LogUtils.w(TAG, "Failed to delete temp file: " + tempFile.getName());
                }
            }
        }
    }

    /**
     * 发送文件内容
     */
    private void sendFileContent(OutputStream outputStream) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(mSegFile, "r")) {
            byte[] buffer = new byte[StorageUtils.DEFAULT_BUFFER_SIZE];
            long offset = 0;

            randomAccessFile.seek(offset);
            int readLength;

            while ((readLength = randomAccessFile.read(buffer)) != -1) {
                offset += readLength;
                outputStream.write(buffer, 0, readLength);
                randomAccessFile.seek(offset);
            }

            LogUtils.d(TAG, "Sent TS file: " + mFileName + " (" + offset + " bytes)");
        }
    }

    /**
     * 检查文件是否下载完整
     */
    private boolean isFileDownloadComplete() {
        return isFileDownloadComplete(mSegFile);
    }

    private boolean isFileDownloadComplete(File file) {
        if (!file.exists()) return false;

        return (mSegLength > 0 && file.length() == mSegLength) ||
                (mSegLength == -1 && file.length() > 0);
    }

    /**
     * 取消下载任务
     */
    private void cancelDownloadTask() {
        if (downloadFuture != null && !downloadFuture.isDone()) {
            downloadFuture.cancel(true);
        }
        isDownloading.set(false);
    }

    /**
     * 解析M3U8 MD5
     */
    private String parseM3U8Md5(String str) throws VideoCacheException {
        if (str == null || str.length() < 2) {
            PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:" + "Invalid file name: " + str);
            throw new VideoCacheException("Invalid file name: " + str);
        }

        str = str.substring(1);
        int index = str.indexOf("/");
        if (index == -1) {
            PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:" + "Cannot find MD5 in: " + str);
            throw new VideoCacheException("Cannot find MD5 in: " + str);
        }
        return str.substring(0, index);
    }

    /**
     * 解析片段索引
     */
    private int parseSegIndex(String str) throws VideoCacheException {
        if (str == null) {
            throw new VideoCacheException("File name is null");
        }

        // 移除扩展名
        int dotIndex = str.lastIndexOf(".");
        if (dotIndex > -1) {
            str = str.substring(0, dotIndex);
        }

        // 获取文件名部分
        int separatorIndex = str.lastIndexOf("/");
        if (separatorIndex == -1) {
            throw new VideoCacheException("Cannot find separator in: " + str);
        }

        String indexStr = str.substring(separatorIndex + 1);

        // 处理初始化片段
        if (indexStr.startsWith(ProxyCacheUtils.INIT_SEGMENT_PREFIX)) {
            indexStr = indexStr.substring(ProxyCacheUtils.INIT_SEGMENT_PREFIX.length());
        }

        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new VideoCacheException("Invalid segment index: " + indexStr, e);
        }
    }

    /**
     * 关闭时清理资源
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            cancelDownloadTask();
        } finally {
            super.finalize();
        }
    }
}