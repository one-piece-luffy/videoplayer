package com.jeffmony.videocache.socket;

import android.os.SystemClock;
import android.text.TextUtils;

import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.common.SourceCreator;
import com.jeffmony.videocache.common.VideoCacheException;
import com.jeffmony.videocache.socket.request.HttpRequest;
import com.jeffmony.videocache.socket.response.BaseResponse;
import com.jeffmony.videocache.utils.HttpUtils;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.Pinger;
import com.jeffmony.videocache.utils.ProxyCacheUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 处理播放器与本地代理之间单个Socket连接的任务
 * LocalProxyVideoServer收到客户端socket，把这个任务丢进线程池执行run()
 * 1. 读取socket输入流，解析播放器发出的HTTP请求
 * 2. 解析代理URL，区分 MP4完整视频 / M3U8索引文件 / M3U8的TS分片文件
 * 3. 通过SourceCreator创建对应的Response对象
 * 4. Response内部做缓存逻辑（读本地缓存 or 网络下载），把http响应+视频字节写回socket输出流返回播放器
 * 5. finally关闭所有流和socket，释放资源
 */
public class SocketProcessTask implements Runnable {

    private static final String TAG  = "SocketProcessTask";
    /*
     * 原子计数器：统计当前正在处理的socket请求总数量
     * 每来一个请求+1，处理完毕finally中-1，日志打印方便排查并发请求问题
     */
    private static final AtomicInteger sRequestCountAtomic = new AtomicInteger(0);
    //当前TCP连接socket，播放器和本地代理之间的socket
    private final Socket mSocket;
    /**
     * SourceCreator 工厂类，用来生成不同类型视频的Response（M3U8Response / Mp4Response / Ts分片Response）
     * 从全局配置拿，上层可以自定义实现
     */
    private final SourceCreator mSourceCreator;
    /**
     * 当前任务创建的时间（系统开机后毫秒，不是系统wall时钟，不受系统时间修改影响）
     * 用来标记本次socket请求时间，库里面有一个逻辑：只处理最新时间标记的socket，旧的过期请求直接丢弃
     * 防止播放器同时发起多个Range请求，旧的连接还在跑，造成并发混乱
     */
    private final long mSocketTaskCreateTime = SystemClock.uptimeMillis();

    public SocketProcessTask(Socket socket) {
        mSocket = socket;
        mSourceCreator = ProxyCacheUtils.getConfig().getSourceCreator();
    }

    @Override
    public void run() {
        sRequestCountAtomic.addAndGet(1);
        LogUtils.i(TAG, "sRequestCountAtomic : " + sRequestCountAtomic.get());
        //socket输出流：写给播放器（http响应、视频二进制数据）
        OutputStream outputStream = null;
        // socket输入流：读播放器发过来的http请求报文
        InputStream inputStream = null;
        try {
            outputStream = mSocket.getOutputStream();
            inputStream = mSocket.getInputStream();
            HttpRequest request = new HttpRequest(inputStream, mSocket.getInetAddress());
            while(!mSocket.isClosed()) {
                request.parseRequest();
                BaseResponse response;
                String url = request.getUri();
                url = url.substring(1);
                LogUtils.d(TAG, "request url=" + url);
                if (Pinger.isPingRequest(url)) {
                    Pinger.responseToPing(mSocket);
                    break;
                }
                url = ProxyCacheUtils.decodeUriWithBase64(url);
                LogUtils.d(TAG, "decode request url=" + url);
                //m3u8里的ts视频请求:Range header=null
                LogUtils.d(TAG, "Range header=" + request.getRangeString());
                /* 最新的请求可以获得回应，旧的关闭；意味着播放器内部只能一个socket在请求数据
                * 把当前socket创建时间设置为全局最新socket时间
                * 库内部逻辑：旧的socket任务拿到这个全局时间对比，如果自己不是最新任务就放弃处理，避免播放器并发多个Range请求造成错乱
                 */
                long currentTime = mSocketTaskCreateTime; //System.currentTimeMillis();
                ProxyCacheUtils.setSocketTime(currentTime);
                if (url.contains(ProxyCacheUtils.VIDEO_PROXY_SPLIT_STR)) {
                    String[] videoInfoArr = url.split(ProxyCacheUtils.VIDEO_PROXY_SPLIT_STR);
                    if (videoInfoArr.length < 3) {
                        throw new VideoCacheException("Local Socket Error Argument");
                    }
                    String videoUrl = videoInfoArr[0];
                    String videoTypeInfo = videoInfoArr[1];
                    String videoHeaders = videoInfoArr[2];

                    Map<String, String> headers = ProxyCacheUtils.str2Map(videoHeaders);
                    LogUtils.d(TAG, videoUrl + "\n" + videoTypeInfo + "\n" + videoHeaders);

                    if (TextUtils.equals(ProxyCacheUtils.M3U8, videoTypeInfo)) {
                        response = mSourceCreator.createM3U8Response(request, videoUrl, headers, currentTime);
                    } else if (TextUtils.equals(ProxyCacheUtils.NON_M3U8, videoTypeInfo)) {
                        response = mSourceCreator.createMp4Response(request, videoUrl, headers, currentTime);
                    } else {
                        //无法从已知的信息判定视频信息，需要重新请求
                        HttpURLConnection connection = HttpUtils.getConnection(videoUrl, headers);
                        String contentType = connection.getContentType();
                        if (ProxyCacheUtils.isM3U8Mimetype(contentType)) {
                            response = mSourceCreator.createM3U8Response(request, videoUrl, headers, currentTime);
                        } else {
                            response = mSourceCreator.createMp4Response(request, videoUrl, headers, currentTime);
                        }
                    }
                    response.sendResponse(mSocket, outputStream);
                } else if (url.contains(ProxyCacheUtils.SEG_PROXY_SPLIT_STR)) {
                    //说明是M3U8 ts格式的文件
                    String[] videoInfoArr = url.split(ProxyCacheUtils.SEG_PROXY_SPLIT_STR);
                    if (videoInfoArr.length < 4) {
                        throw new VideoCacheException("Local Socket for M3U8 ts file Error Argument");
                    }
                    String parentUrl = videoInfoArr[0];
                    String videoUrl = videoInfoArr[1];
                    String fileName = videoInfoArr[2];
                    String videoHeaders = videoInfoArr[3];
                    Map<String, String> headers = ProxyCacheUtils.str2Map(videoHeaders);
                    LogUtils.d(TAG,  "ts request: parentUrl:" + parentUrl + "\nvideoUrl:" + videoUrl + "\nfileName:" + fileName + "\nvideoHeaders:" + videoHeaders);
                    // 创建TS分片处理的Response对象
                    response = mSourceCreator.createM3U8SegResponse(request, parentUrl, videoUrl, headers, currentTime, fileName);
                    // 写回TS分片二进制数据给播放器
                    response.sendResponse(mSocket, outputStream);
                } else {
                    throw new VideoCacheException("Local Socket Error url");
                }
                // 处理完一次http请求就跳出while循环，本socket任务结束，属于短连接
                break;
            }

        } catch (Exception e) {
            LogUtils.e(TAG,"socket request failed, exception=" + e);
            PlayerProgressListenerManager.getInstance().log("socket request failed, exception=" + e);

        } finally {
            ProxyCacheUtils.close(outputStream);
            ProxyCacheUtils.close(inputStream);
            ProxyCacheUtils.close(mSocket);
            int count = sRequestCountAtomic.decrementAndGet();
            LogUtils.i(TAG, "finally Socket solve count = " + count);
        }
    }
}
