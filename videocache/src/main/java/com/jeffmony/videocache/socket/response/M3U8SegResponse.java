package com.jeffmony.videocache.socket.response;

import android.os.Build;
import android.util.Log;

import com.jeffmony.videocache.VideoInfoParseManager;
import com.jeffmony.videocache.VideoProxyCacheManager;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.m3u8.M3U8;
import com.jeffmony.videocache.m3u8.M3U8Seg;
import com.jeffmony.videocache.socket.request.HttpRequest;
import com.jeffmony.videocache.socket.request.ResponseState;
import com.jeffmony.videocache.utils.AES128Utils;
import com.jeffmony.videocache.utils.DefaultExecutor;
import com.jeffmony.videocache.utils.FileUtils;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.OkHttpUtil;
import com.jeffmony.videocache.utils.ProxyCacheUtils;
import com.jeffmony.videocache.utils.StorageUtils;

import java.io.File;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Response;

/**
 * @author jeffmony
 * M3U8-TS视频的local server端
 *
 * https://iqiyi.cdn9-okzy.com/20210217/22550_b228d68b/1000k/hls/6f2ac117eac000000.ts&jeffmony_seg&/c462e3fd379ce23333aabed0a3837848/0.ts&jeffmony_seg&unknown
 */
public class M3U8SegResponse extends BaseResponse {

    private static final String TAG = "M3U8SegResponse";

    private static final String TEMP_POSTFIX = ".player_downloading";

    private String mParentUrl;
    private final File mSegFile;
    private final String mSegUrl;
    private final String mM3U8Md5;    //对应M3U8 url的md5值
    private final int mSegIndex;      //M3U8 ts对应的索引位置
    private long mSegLength;
    private String mFileName;
    int MAX_RETRY_COUNT = 2;
    int MAX_RETRY_COUNT_503 = 3;
    AtomicBoolean downloading = new AtomicBoolean(false);

    public M3U8SegResponse(HttpRequest request, String parentUrl, String videoUrl, Map<String, String> headers, long time, String fileName) throws Exception {
        super(request, videoUrl, headers, time);
        mParentUrl = parentUrl;
        mSegUrl = videoUrl;
        mSegFile = new File(mCachePath, fileName);
        LogUtils.i(TAG, "SegFilePath="+mSegFile.getAbsolutePath());
        mFileName = mSegFile.getName();
        mM3U8Md5 = getM3U8Md5(fileName);
        if (mHeaders == null) {
            mHeaders = new HashMap<>();
        }
        mHeaders.put("Connection", "close");
        mSegIndex = getSegIndex(fileName);
        mResponseState = ResponseState.OK;
        LogUtils.i(TAG, "start M3U8SegResponse: index=" + mSegIndex +", parentUrl=" + mParentUrl + "\n, segUrl=" + mSegUrl);
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
        if (idotIndex> -1) {
            str = str.substring(0, idotIndex);
        }

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
        //因为下载过程的文件名称和已经完成的不一样，可以简化判断条件

        while (!mSegFile.exists()) {
            if (!downloading.get()) {
                downloading.set(true);
                DefaultExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        //Log.e(TAG,"ts不存在,开始下载："+mSegFile.getAbsolutePath());
                       //downloadSegFile(mSegUrl, mSegFile);
                        downloadFile(mSegUrl, mSegFile);
                    }
                });

            }
            if ((mSegLength > 0 && mSegLength == mSegFile.length()) || (mSegLength == -1 && mSegFile.length() > 0)) {
                break;
            }
//            LogUtils.e(TAG, "FileLength=" + mSegFile.length() + ", segLength=" + mSegLength + ", FilePath=" + mSegFile.getAbsolutePath());
        }
//        if (mFileName.startsWith(ProxyCacheUtils.INIT_SEGMENT_PREFIX)) {
//            while(!mSegFile.exists()) {
//                downloadSegFile(mSegUrl, mSegFile);
//                if ((mSegLength > 0 && mSegLength == mSegFile.length()) || (mSegLength == -1 && mSegFile.length() > 0)) {
//                    break;
//                }
//            }
//        } else {
//            boolean isM3U8SegCompleted = VideoProxyCacheManager.getInstance().isM3U8SegCompleted(mM3U8Md5, mSegIndex, mSegFile.getAbsolutePath());
//            while (!isM3U8SegCompleted) {
//                downloadSegFile(mSegUrl, mSegFile);
//                isM3U8SegCompleted = VideoProxyCacheManager.getInstance().isM3U8SegCompleted(mM3U8Md5, mSegIndex, mSegFile.getAbsolutePath());
//                if ((mSegLength > 0 && mSegLength == mSegFile.length()) || (mSegLength == -1 && mSegFile.length() > 0)) {
//                    break;
//                }
//            }
//            LogUtils.d(TAG,  "FileLength=" + mSegFile.length() + ", segLength=" + mSegLength + ", FilePath=" + mSegFile.getAbsolutePath());
//        }
        Log.e(TAG,mSegFile.getName()+"已存在，发往服务器");
        RandomAccessFile randomAccessFile = null;

        try {
            randomAccessFile = new RandomAccessFile(mSegFile, "r");
            int bufferedSize = StorageUtils.DEFAULT_BUFFER_SIZE;
            byte[] buffer = new byte[bufferedSize];
            long offset = 0;

            if(shouldSendResponse(socket, mM3U8Md5)) {
                randomAccessFile.seek(offset);
                int readLength;
                while((readLength = randomAccessFile.read(buffer, 0, buffer.length)) != -1) {
                    offset += readLength;
                    outputStream.write(buffer, 0, readLength);
                    randomAccessFile.seek(offset);
                }
                LogUtils.d(TAG, "Send M3U8 ts file end, this="+this);
            }
        } catch (Exception e) {
            Log.e(TAG,"出错了",e);
            throw e;
        } finally {
            ProxyCacheUtils.close(randomAccessFile);
        }
    }

    private void downloadSegFile(String url, File file) throws Exception {
        LogUtils.i(TAG, "downloadSegFile file:" + file);
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
        } catch (Exception e) {
            Log.e(TAG,"ts下载出错了",e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            ProxyCacheUtils.close(inputStream);
        }
    }



    private void saveSegFile(InputStream inputStream, File file) throws Exception {
        FileOutputStream fos = null;
        long totalLength = 0;
        File tmpFile = new File(file.getParentFile(), file.getName() + TEMP_POSTFIX);
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        try {
            fos = new FileOutputStream(tmpFile);
            int readLength;
            byte[] buffer = new byte[StorageUtils.DEFAULT_BUFFER_SIZE];
            while ((readLength = inputStream.read(buffer)) != -1) {
                totalLength += readLength;
                fos.write(buffer, 0, readLength);
            }
            tmpFile.renameTo(file);
        } catch (Exception e) {
            if (tmpFile.exists() && ((mSegLength > 0 && mSegLength == tmpFile.length()) || (mSegLength == -1 && tmpFile.length() == totalLength))) {
                //说明此文件下载完成
                tmpFile.renameTo(file);
            } else {
                tmpFile.delete();
            }
            throw e;
        } finally {
            ProxyCacheUtils.close(fos);
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
            Log.i(TAG, "m3u8 is null：" + videoUrl);
        }else {
//            Log.i(TAG, "m3u8 list：" + m3u8.getSegList().size());
            for (int i = 0; i < m3u8.getSegList().size(); i++) {
                M3U8Seg m3U8Seg = m3u8.getSegList().get(i);
                if (m3U8Seg.getUrl().equals(videoUrl)) {
                    ts = m3U8Seg;
                    break;
                }
            }
        }

        if (ts == null) {
//            Log.i(TAG, "ts is null：" + videoUrl);
            ts=new M3U8Seg();
            ts.setUrl(videoUrl);
        }
//        Log.i(TAG, "开始下载ts 方法一：" + videoUrl + " file:" + file.getAbsolutePath());
        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        File tmpFile = new File(file.getParentFile(), file.getName() + TEMP_POSTFIX);
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
                    Log.e(TAG,"播放器正在下载:"+file.getName()+" ts是加密过的");

                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tmpFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
//                    Log.i(TAG, "解密ts");
                    FileOutputStream fileOutputStream = null;
                    try {
                        byte[] result = AES128Utils.dencryption(AES128Utils.readFile(tmpFile), encryptionKey, iv);
                        if (result != null) {
                            //这里写入的是临时文件
                            fileOutputStream = new FileOutputStream(tmpFile);
                            fileOutputStream.write(result);
                            //解密后文件的大小和content-length不一致，所以直接赋值为文件大小
                            contentLength = tmpFile.length();
                            Log.e(TAG, "ts下载完成"+file.getName());
                            FileUtils.handleRename(tmpFile,file);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                        FileUtils.deleteFile(tmpFile);
                    }
                } else {
                    Log.e(TAG,"播放器正在下载:"+file.getName());
                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tmpFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
                    /**
                     *  todo
                     *  这里需要引入临时文件，下载完成后再重命名为原来的名字，不然 M3U8SegResponse的sendBody()的判断会出问题
                     *  sendBody 监听文件是否存在，只要文件存在就将数据发给播放器，这时候的文件可能是不完整的。所以这里等全部下载完成再重命名。
                     *  这时候sendBody监听到的就是完整的文件。
                     */
                    FileUtils.handleRename(tmpFile,file);
                    Log.e(TAG, "ts下载完成"+file.getName());
                    if (contentLength <= 0) {
                        contentLength = file.length();
                    }
                }


                mSegLength = contentLength;
                ts.setContentLength(contentLength);

            } else {
                ts.setRetryCount(ts.getRetryCount() + 1);
                if (responseCode == HttpUtils.RESPONSE_503 || responseCode == HttpUtils.RESPONSE_429) {
                    if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
                        //遇到503，延迟[4,24]秒后再重试，区间间隔不能太小
                        int ran = 4000 + (int) (Math.random() * 20000);
                        Thread.sleep(ran);
                        Log.i(TAG, "sleep:" + ran);
                        downloadFile(videoUrl, file);
                    }
                } else if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
//                    Log.i(TAG, "====retry1   responseCode=" + responseCode + "  ts:" + ts.getUrl());

                    downloadFile(videoUrl, file);
                }
            }


        } catch (InterruptedIOException e) {
            //被中断了，使用stop时会抛出这个，不需要处理
        } catch (Exception e) {
            Log.e(TAG,"exception:"+e);
            ts.setRetryCount(ts.getRetryCount() + 1);
            if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
                downloadFile(videoUrl, file);
            } else {
                if (tmpFile.exists() && ((mSegLength > 0 && mSegLength == tmpFile.length()) || (mSegLength == -1 && tmpFile.length() > 0))) {
                    //说明此文件下载完成
                    FileUtils.rename(tmpFile, file);
                } else {
                    tmpFile.delete();
                }
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
            downloading.set(false);
        }

    }




}
