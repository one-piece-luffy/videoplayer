package com.jeffmony.videocache.socket.response;

import android.os.Build;
import android.os.Looper;
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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private String mVideoName;
//    protected ThreadPoolExecutor mTaskExecutor;

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
        mVideoName=ProxyCacheUtils.decodeUriWithBase64(mHeaders.get(CacheConstants.HEADER_KEY_NAME));
        mResponseState = ResponseState.OK;
        LogUtils.i(TAG, "start M3U8SegResponse: index=" + mSegIndex +", parentUrl=" + mParentUrl + "\n, segUrl=" + mSegUrl);
        VideoProxyCacheManager.getInstance().seekToCacheTaskFromServer(mParentUrl, mSegIndex);
//        mTaskExecutor = new ThreadPoolExecutor(1, 1, 0L,
//                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(),
//                new ThreadPoolExecutor.DiscardOldestPolicy());
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
        Object lock = VideoLockManager.getInstance().getLock(mM3U8Md5);
        long wait = 0;
        //todo 如何感知播放器已经断开，避免占用线程池
        while (!mSegFile.exists()&& wait < TIME_OUT) {
            synchronized (lock) {
                lock.wait(WAIT_TIME);
            }
            wait += WAIT_TIME;
            if (!downloading.get()) {
                downloading.set(true);
                //新开个线程，这样才可以同时监听player下载和task下载，不会互相阻塞
//                mTaskExecutor.execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        downloadFile(mSegUrl, mSegFile);
//                    }
//                });
                DefaultExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        downloadFile(mSegUrl, mSegFile);
                    }
                });

            }

            if ((mSegLength > 0 && mSegLength == mSegFile.length()) || (mSegLength == -1 && mSegFile.length() > 0)) {
                break;
            }
//            LogUtils.e(TAG, "FileLength=" + mSegFile.length() + ", segLength=" + mSegLength + ", FilePath=" + mSegFile.getAbsolutePath());
        }
        if (!mSegFile.exists()) {
            PlayerProgressListenerManager.getInstance().log("==timeout " + mSegFile.getName() + "  "+wait+" ms==");
//            LogUtils.e(TAG, "wait " + mSegFile.getName() + " timeout" + " socket.isClosed:" + socket.isClosed() + ",socket.isOutputShutdown:" + socket.isOutputShutdown());
            outputStream.write(null, 0, 0);
            return;
        }

//        if (mFileName.startsWith(ProxyCacheUtils.INIT_SEGMENT_PREFIX)) {
//        Log.e(TAG,mSegFile.getName()+"已存在，发往服务器");
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
            PlayerProgressListenerManager.getInstance().log("ts发送出错："+e.getMessage());
            throw e;
        } finally {
            ProxyCacheUtils.close(randomAccessFile);
        }
    }


    /**
     * 下载ts文件，不需要失败重试，因为前面while循环判断了，否则下载失败会OOM
     * @param videoUrl
     * @param file
     */
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
        } else {
            for (int i = 0; i < m3u8.getSegList().size(); i++) {
                M3U8Seg m3U8Seg = m3u8.getSegList().get(i);
                if (m3U8Seg.getUrl().equals(videoUrl)) {
                    ts = m3U8Seg;
                    break;
                }
            }
        }

        if (ts == null) {
            ts=new M3U8Seg();
            ts.setUrl(videoUrl);
        }
//        Log.e(TAG, "开始下载ts：mSegIndex=" + mSegIndex +",index="+ts.getSegIndex()+", name="+mFileName+" url="+videoUrl);

//
//            try {
//                ThreadPoolExecutor tpe = ((ThreadPoolExecutor) mTaskExecutor);
//                int queueSize = tpe.getQueue().size();
//                int activeCount = tpe.getActiveCount();
//                long completedTaskCount = tpe.getCompletedTaskCount();
//                long taskCount = tpe.getTaskCount();
//                Log.e(TAG, " 当前排队线程数：" + queueSize + " 当前活动线程数：" + activeCount + " 执行完成线程数：" + completedTaskCount + " 总线程数：" + taskCount);
//            } catch (Exception e) {
//                Log.e(TAG, "发生异常: ", e);
//            }

        InputStream inputStream = null;

        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        FileChannel foutc = null;
        Response response = null;
        String filename=file.getName();
        File tmpFile = new File(file.getParentFile(), filename + TEMP_POSTFIX);
        try {
            PlayerProgressListenerManager.getInstance().log("播放器正在下载:"+mVideoName+" "+filename);
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
//                    Log.e(TAG,"播放器正在下载:"+filename+" ts是加密过的");
                    rbc = Channels.newChannel(inputStream);
                    fos = new FileOutputStream(tmpFile);
                    foutc = fos.getChannel();
                    foutc.transferFrom(rbc, 0, Long.MAX_VALUE);
//                    Log.i(TAG, "解密ts");
                    FileOutputStream fileOutputStream = null;
                    try {
                        byte[] result = AES128Utils.dencryption(AES128Utils.readFile(tmpFile), encryptionKey, iv);
                        if (result == null) {
                            //todo下载失败
                            PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:"+ts.getSegName());
                            try {
                                Thread.sleep(7000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt(); // 恢复中断状态
                                // 处理中断逻辑，例如退出循环或任务
                            }
                            return;
                        } else {
                            //这里写入的是临时文件
                            fileOutputStream = new FileOutputStream(tmpFile);
                            fileOutputStream.write(result);
                            //解密后文件的大小和content-length不一致，所以直接赋值为文件大小
                            contentLength = tmpFile.length();
//                            Log.i(TAG, "ts下载完成"+filename);
                            PlayerProgressListenerManager.getInstance().log("播放器ts下载完成:" + filename+" ts是加密过的 ");
                            FileUtils.handleRename(tmpFile, file);
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
//                    Log.e(TAG,"播放器正在下载:"+filename);
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
//                    Log.i(TAG, "ts下载完成"+filename);
                    PlayerProgressListenerManager.getInstance().log("播放器ts下载完成:"+filename);
                    if (contentLength <= 0) {
                        contentLength = file.length();
                    }
                }
                if (file.exists() && filename.startsWith("0.")) {
                    if (PlayerProgressListenerManager.getInstance().getListener() != null) {
                        PlayerProgressListenerManager.getInstance().getListener().onPlayerFirstTsDownload(filename);
                    }
                }
                mSegLength = contentLength;
                ts.setContentLength(contentLength);

            } else {
                //不需要重试否则会导致oom， downloading.set(false);会触发重新下载

//                ts.setRetryCount(ts.getRetryCount() + 1);
//                if (responseCode == HttpUtils.RESPONSE_503 || responseCode == HttpUtils.RESPONSE_429) {
//                    if (ts.getRetryCount() <= MAX_RETRY_COUNT_503) {
//                        //遇到503，延迟[4,24]秒后再重试，区间间隔不能太小
//                        int ran = 4000 + (int) (Math.random() * 20000);
//                        Thread.sleep(ran);
//                        Log.i(TAG, "sleep:" + ran);
//                        downloadFile(videoUrl, file);
//                    }
//                } else if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
////                    Log.i(TAG, "====retry1   responseCode=" + responseCode + "  ts:" + ts.getUrl());
//
//                    downloadFile(videoUrl, file);
//                }
                PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:"+ts.getSegName()+" code:"+responseCode);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    // 处理中断逻辑，例如退出循环或任务
                }
            }


        }  catch (Exception e) {
            PlayerProgressListenerManager.getInstance().log("播放器ts下载失败:"+ts.getSegName()+" msg:"+e.getMessage());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                // 处理中断逻辑，例如退出循环或任务
            }
//            ts.setRetryCount(ts.getRetryCount() + 1);
//            if (ts.getRetryCount() <= MAX_RETRY_COUNT) {
//                downloadFile(videoUrl, file);
//            } else {
//                if (tmpFile.exists() && ((mSegLength > 0 && mSegLength == tmpFile.length()) || (mSegLength == -1 && tmpFile.length() > 0))) {
//                    //说明此文件下载完成
//                    FileUtils.rename(tmpFile, file);
//                } else {
//                    tmpFile.delete();
//                }
//            }
            if (tmpFile.exists() && ((mSegLength > 0 && mSegLength == tmpFile.length()) || (mSegLength == -1 && tmpFile.length() > 0))) {
                //说明此文件下载完成
                FileUtils.rename(tmpFile, file);
            } else {
                tmpFile.delete();
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
