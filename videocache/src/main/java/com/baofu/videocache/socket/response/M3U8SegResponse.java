package com.baofu.videocache.socket.response;

import android.util.Log;

import com.baofu.videocache.VideoInfoParseManager;
import com.baofu.videocache.VideoProxyCacheManager;
import com.baofu.videocache.common.VideoCacheException;
import com.baofu.videocache.m3u8.M3U8;
import com.baofu.videocache.m3u8.M3U8Seg;
import com.baofu.videocache.socket.request.HttpRequest;
import com.baofu.videocache.socket.request.ResponseState;
import com.baofu.videocache.utils.AES128Utils;
import com.baofu.videocache.utils.HttpUtils;
import com.baofu.videocache.utils.LogUtils;
import com.baofu.videocache.utils.OkHttpUtil;
import com.baofu.videocache.utils.ProxyCacheUtils;
import com.baofu.videocache.utils.StorageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Response;

/**
 * @author jeffmony
 * M3U8-TS视频的local server端
 * <p>
 * https://iqiyi.cdn9-okzy.com/20210217/22550_b228d68b/1000k/hls/6f2ac117eac000000.ts&jeffmony_seg&/c462e3fd379ce23333aabed0a3837848/0.ts&jeffmony_seg&unknown
 */
public class M3U8SegResponse extends BaseResponse {

    private static final String TAG = "M3U8SegResponse";
    private String mParentUrl;
    private File mSegFile;
    private String mSegUrl;
    private String mM3U8Md5;    //对应M3U8 url的md5值
    private int mSegIndex;      //M3U8 ts对应的索引位置
    private long mSegLength;
    private String mFileName;
    int MAX_RETRY_COUNT = 1;
    int MAX_RETRY_COUNT_503 = 3;

    public M3U8SegResponse(HttpRequest request, String parentUrl, String videoUrl, Map<String, String> headers, long time, String fileName) throws Exception {
        super(request, videoUrl, headers, time);
        mParentUrl = parentUrl;
        mSegUrl = videoUrl;
        mSegFile = new File(mCachePath, fileName);
        LogUtils.i(TAG, "SegFilePath=" + mSegFile.getAbsolutePath());
        mFileName = mSegFile.getName();
        mM3U8Md5 = getM3U8Md5(fileName);
        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mHeaders.put("Connection", "close");
        mSegIndex = getSegIndex(fileName);
        mResponseState = ResponseState.OK;
        LogUtils.i(TAG, "index=" + mSegIndex + ", parentUrl=" + mParentUrl + ", segUrl=" + mSegUrl);
        VideoProxyCacheManager.getInstance().seekToCacheTaskFromServer(mParentUrl, mSegIndex);
    }

    private String getM3U8Md5(String str) throws VideoCacheException {
        str = str.substring(1);
        int index = str.indexOf("/");
        if (index == -1) {
            throw new VideoCacheException("Error index during getMd5");
        }
        return str.substring(0, index);
    }

    private int getSegIndex(String str) throws VideoCacheException {
        int idotIndex = str.lastIndexOf(".");
        if (idotIndex == -1) {
            throw new VideoCacheException("Error index during getTcd sIndex");
        }
        str = str.substring(0, idotIndex);
        int seperatorIndex = str.lastIndexOf("/");
        if (seperatorIndex == -1) {
            throw new VideoCacheException("Error index during getTsIndex");
        }
        str = str.substring(seperatorIndex + 1);
        if (str.startsWith(ProxyCacheUtils.INIT_SEGMENT_PREFIX)) {
            str = str.substring(ProxyCacheUtils.INIT_SEGMENT_PREFIX.length());
            LogUtils.i(TAG, "str = " + str);
        }
        return Integer.parseInt(str);
    }

    @Override
    public void sendBody(Socket socket, OutputStream outputStream, long pending) throws Exception {
        Log.e(TAG, "开始解析ts：" + mFileName);
        Log.e(TAG, "ts exits：" + mSegFile.exists());
        if (mSegFile.exists()) {
//            sendBody2(socket,outputStream);
            sendBody(outputStream, new FileInputStream(mSegFile));
            return;
        }

        if (mFileName.startsWith(ProxyCacheUtils.INIT_SEGMENT_PREFIX)) {
            Log.e(TAG, "ts startsWith INIT_SEGMENT_PREFIX");
            while (!mSegFile.exists()) {
                downloadFile(mSegUrl, mSegFile);
                if (mSegLength > 0 && mSegLength == mSegFile.length()) {
                    break;
                }
            }
        } else {

//            boolean isM3U8SegCompleted = VideoProxyCacheManager.getInstance().isM3U8SegCompleted(mM3U8Md5, mSegIndex, mSegFile.getAbsolutePath());
//            Log.e(TAG,"ts已经下载："+isM3U8SegCompleted);
//            while (!isM3U8SegCompleted) {
//                downloadFile(mSegUrl, mSegFile);
//                isM3U8SegCompleted = VideoProxyCacheManager.getInstance().isM3U8SegCompleted(mM3U8Md5, mSegIndex, mSegFile.getAbsolutePath());
//                Log.e(TAG,"isM3U8SegCompleted："+isM3U8SegCompleted);
//                if (mSegLength > 0 && mSegLength == mSegFile.length()) {
//
//                    Log.e(TAG,"break");
//
//                    break;
//                }
//            }
//            LogUtils.d(TAG,  "FileLength=" + mSegFile.length() + ", segLength=" + mSegLength + ", FilePath=" + mSegFile.getAbsolutePath());
            downloadFile(mSegUrl, mSegFile);
        }
//        sendBody2(socket,outputStream);
        sendBody(outputStream, new FileInputStream(mSegFile));
    }

    private void sendBody2(Socket socket, OutputStream outputStream) throws Exception {
        RandomAccessFile randomAccessFile = null;

        try {
            randomAccessFile = new RandomAccessFile(mSegFile, "r");
            if (randomAccessFile == null) {
                throw new VideoCacheException("M3U8 ts file not found, this=" + this);
            }
            int bufferedSize = StorageUtils.DEFAULT_BUFFER_SIZE;
            byte[] buffer = new byte[bufferedSize];
            long offset = 0;
            boolean shouldSendResponse = shouldSendResponse(socket, mM3U8Md5);
            Log.e(TAG, "isM3U8SegCompleted：" + shouldSendResponse);
            while (shouldSendResponse) {
                randomAccessFile.seek(offset);
                int readLength;
                while ((readLength = randomAccessFile.read(buffer, 0, buffer.length)) != -1) {
                    offset += readLength;
                    outputStream.write(buffer, 0, readLength);
                    randomAccessFile.seek(offset);
                }
                LogUtils.d(TAG, "Send M3U8 ts file end, this=" + this);
                break;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            ProxyCacheUtils.close(randomAccessFile);
        }
    }

    private void sendBody(OutputStream outputStream, InputStream mInputStream) throws IOException {
        long buffer_size = 8 * 1024;
        byte[] buff = new byte[(int) buffer_size];
        int read ;
        if (mInputStream == null) {
            Log.e(TAG, "inputstream is null");
            return;
        }
        while ((read = mInputStream.read(buff)) != -1) {
            outputStream.write(buff, 0, read);
//            Log.e(TAG,"write buff");
        }
        Log.e(TAG, "write finish");
    }

    private void downloadSegFile(String url, File file) throws Exception {
        Log.e(TAG, "开始下载ts 方法二：" + url + " file:" + file.getAbsolutePath());
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            connection = HttpUtils.getConnection(url, mHeaders);
            int responseCode = connection.getResponseCode();
            if (responseCode == ResponseState.OK.getResponseCode() || responseCode == ResponseState.PARTIAL_CONTENT.getResponseCode()) {
                inputStream = connection.getInputStream();
                mSegLength = connection.getContentLength();
                saveSegFile(inputStream, file);
            }
            Log.e(TAG, "ts下载完成");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            ProxyCacheUtils.close(inputStream);
        }
    }

    public void downloadFile(String videoUrl, File file)  {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if(parent!=null){
                File file1=new File(parent.getAbsolutePath());
                file1.mkdir();
            }
        }
        M3U8 m3u8 = VideoInfoParseManager.getInstance().m3u8;
        M3U8Seg ts = null;
        if (m3u8 == null) {
            Log.e(TAG, "m3u8 is null：" + videoUrl);
        }else {
            Log.e(TAG, "m3u8 list：" + m3u8.getSegList().size());
            for (int i = 0; i < m3u8.getSegList().size(); i++) {
                M3U8Seg m3U8Seg = m3u8.getSegList().get(i);
                if (m3U8Seg.getUrl().equals(videoUrl)) {
                    ts = m3U8Seg;
                    break;
                }
            }
        }

        if (ts == null) {
            Log.e(TAG, "ts is null：" + videoUrl);
            ts=new M3U8Seg();
            ts.setUrl(videoUrl);
        }
        Log.e(TAG, "开始下载ts 方法一：" + videoUrl + " file:" + file.getAbsolutePath());
        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        try {

            response = OkHttpUtil.getInstance().requestSync(videoUrl, mHeaders);
            int responseCode = response.code();
            if (responseCode == HttpUtils.RESPONSE_200 || responseCode == HttpUtils.RESPONSE_206) {
                ts.setRetryCount(0);
                inputStream = response.body().byteStream();
                long contentLength = response.body().contentLength();

                byte[] encryptionKey ;
                if( ts.encryptionKey == null&&m3u8!=null){
                    encryptionKey=m3u8.encryptionKey;
                }else {
                    encryptionKey=ts.encryptionKey;
                }
                String iv = ts.encryptionKey == null&&m3u8!=null ? m3u8.encryptionIV : ts.getKeyIv();
                if (encryptionKey != null) {

                    String tsInitSegmentName = ts.getInitSegmentName() + ".temp";
                    File tsInitSegmentFile = new File(file.getParentFile().getAbsolutePath(), tsInitSegmentName);


                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tsInitSegmentFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                    Log.e(TAG, "解密ts");
                    FileOutputStream fileOutputStream = null;
                    try {
                        byte[] result = AES128Utils.dencryption(AES128Utils.readFile(tsInitSegmentFile), encryptionKey, iv);
                        if (result != null) {
                            fileOutputStream = new FileOutputStream(file);
                            fileOutputStream.write(result);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                            tsInitSegmentFile.delete();
                        }
                    }
                } else {
                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(file);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                }

                if (contentLength <= 0) {
                    contentLength = file.length();
                }
                ts.setContentLength(contentLength);
                Log.e(TAG, "ts下载完成");
            } else {
                ts.setRetryCount(ts.getRetryCount() + 1);
                if (responseCode == HttpUtils.RESPONSE_503 || responseCode == HttpUtils.RESPONSE_429) {
                    if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
                        //遇到503，延迟[4,24]秒后再重试，区间间隔不能太小
                        int ran = 4000 + (int) (Math.random() * 20000);
                        Thread.sleep(ran);
                        Log.e(TAG, "sleep:" + ran);
                        downloadFile(videoUrl, file);
                    }
                } else if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
                    Log.e(TAG, "====retry1   responseCode=" + responseCode + "  ts:" + ts.getUrl());

                    downloadFile(videoUrl, file);
                } else {
                    Log.e(TAG, "====error   responseCode=" + responseCode + "  ts:" + ts.getUrl());
                }
            }


        } catch (InterruptedIOException e) {
            //被中断了，使用stop时会抛出这个，不需要处理
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            ts.setRetryCount(ts.getRetryCount() + 1);
            if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
                Log.e(TAG, "====retry, exception=" + e.getMessage());
                downloadFile(videoUrl, file);
            }
        } finally {
            ProxyCacheUtils.close(inputStream);
            ProxyCacheUtils.close(fos);
            if (response != null) {
                ProxyCacheUtils.close(response.body());
            }
            if (rbc != null) {
                try {
                    rbc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (foutc != null) {
                try {
                    foutc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }

    private void saveSegFile(InputStream inputStream, File file) throws Exception {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            int readLength;
            byte[] buffer = new byte[StorageUtils.DEFAULT_BUFFER_SIZE];
            while ((readLength = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, readLength);
            }
        } catch (Exception e) {
            if (file.exists() && mSegLength > 0 && mSegLength == file.length()) {
                //说明此文件下载完成
            } else {
                file.delete();
            }
            throw e;
        } finally {
            ProxyCacheUtils.close(fos);
            ProxyCacheUtils.close(inputStream);
        }
    }


}
