package com.jeffmony.videocache.proxy;

import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.socket.SocketProcessTask;
import com.jeffmony.videocache.utils.LogUtils;
import com.jeffmony.videocache.utils.Pinger;
import com.jeffmony.videocache.utils.ProxyCacheUtils;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jeffmony
 * 本地代理服务端类
 * 核心：开启本机的Socket代理服务器，播放器访问本机这个代理，间接播放视频，实现边播放边缓存
 * 播放器请求格式示例：http ://127.0.0.1:xxxx/原始视频url
 */

public class LocalProxyVideoServer {

    private static final String TAG = "LocalProxyCacheServer";
    /*
     * socket连接处理线程池
     * newFixedThreadPool(8)：最多同时处理8个客户端socket连接，超过任务进入队列排队
     * 每一个播放器的请求过来，都会提交一个SocketProcessTask到这个线程池执行
     */
    private final ExecutorService mSocketPool = Executors.newFixedThreadPool(8);
    /*
    `AbortPolicy`（默认）抛 RejectedExecutionException⚠️可以用，但**必须捕获异常**，否则 accept 线程死亡`
     DiscardPolicy`悄悄丢弃任务，**不抛异常**✅推荐本场景；队列满直接丢掉任务，不会抛异常，不需要额外 catch；socket 对象直接丢弃，播放器等待超时重试`
     DiscardOldestPolicy`丢弃队列**最老未执行任务**，加入新任务不适合；会丢掉排队中的 ts 分片请求，视频播放异常
     */
//    private final ExecutorService mSocketPool = new ThreadPoolExecutor(
//            4,
//            16,
//            60L, TimeUnit.SECONDS,
//            new ArrayBlockingQueue<>(16), //有界队列，最多排队16个任务
//            new ThreadPoolExecutor.AbortPolicy() //队列满直接抛异常拒绝
//    );


    public ServerSocket mLocalServer;
    /**
     * 专门用来循环调用 mLocalServer.accept() 等待客户端连接的后台线程
     * accept()是阻塞方法，必须放到子线程，不能阻塞主线程
     */
    private Thread mRequestThread;

    /**
     * 构造方法：启动本地代理服务器
     * 调用后会创建ServerSocket、开启后台监听线程，阻塞等待服务初始化完成才退出构造
     */
    public LocalProxyVideoServer() {
        try {
            InetAddress address = InetAddress.getByName(ProxyCacheUtils.LOCAL_PROXY_HOST);
            /*
              创建ServerSocket
              参数1：0=随机选一个可用端口号，不写死端口避免端口被占用冲突
              参数2：8 服务端等待连接队列最大长度
              参数3：绑定到本机回环地址，只能本机访问
             */
            mLocalServer = new ServerSocket(0, 8, address);
            // 获取系统分配给ServerSocket的实际端口号
            int mPort = mLocalServer.getLocalPort();
            ProxyCacheUtils.getConfig().setPort(mPort);
            ProxyCacheUtils.setLocalPort(mPort);
            /*
             * CountDownLatch 闭锁，用于线程间等待
             * 目的：保证构造函数中，必须等到监听线程真正启动完成之后，构造方法才能返回
             * 防止外部拿到代理端口，但是服务还没开始监听，播放器请求直接失败
             */
            CountDownLatch startSignal = new CountDownLatch(1);
            WaitSocketRequestsTask task = new WaitSocketRequestsTask(startSignal);
            mRequestThread = new Thread(task);
            mRequestThread.setName("LocalProxyServerThread");
            mRequestThread.start();
            // 当前（构造函数所在线程）阻塞等待，直到 startSignal.countDown()被调用，才往下执行
            startSignal.await();
        } catch (Exception e) {
            // 如果创建服务端Socket发生任何异常，直接关闭服务，释放资源
            shutdown();
            String message="Cannot create serverSocket, exception=" + e;
            LogUtils.w(TAG,message);
            PlayerProgressListenerManager.getInstance().log(message);
        }
    }
    /**
     * 内部Runnable：等待Socket连接任务
     * 运行在 mRequestThread 子线程
     */
    private class WaitSocketRequestsTask implements Runnable {

        private final CountDownLatch mLatch;

        public WaitSocketRequestsTask(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void run() {
            // 计数器-1，唤醒构造函数里面阻塞的 startSignal.await()
            mLatch.countDown();
            // 正式开启循环监听客户端socket连接
            initSocketProcessor();
        }
    }
    /**
     * 循环阻塞等待播放器客户端Socket连接
     * 死循环，只要ServerSocket没有关闭，就一直等待新连接进来
     */
    private void initSocketProcessor() {
        do {
            try {
                Socket socket = mLocalServer.accept();
                socket.setSoTimeout(ProxyCacheUtils.getConfig().getConnTimeOut()>0?ProxyCacheUtils.getConfig().getConnTimeOut():60);
                /*
                 * 把新建立的socket交给线程池任务SocketProcessTask
                 * SocketProcessTask内部实现http协议解析，解析播放器的http请求，处理缓存逻辑，返回视频流
                 * accept不能做耗时操作，快速把任务丢线程池，继续等待下一个连接
                 */
                mSocketPool.submit(new SocketProcessTask(socket));

            } catch (Exception e) {
                LogUtils.w(TAG, "WaitRequestsRun ServerSocket accept failed, exception=" + e);
                PlayerProgressListenerManager.getInstance().log("WaitRequestsRun ServerSocket accept failed, exception=" + e);
            }
        } while (!mLocalServer.isClosed());
    }
    /**
     * 关闭整个本地代理服务，释放所有资源
     * 关闭ServerSocket、关闭线程池、中断监听线程
     */
    public void shutdown() {
        if (mLocalServer != null) {
            try {
                // 关闭服务端socket，会让阻塞在 accept() 的方法抛出异常，跳出do‑while循环
                mLocalServer.close();
            } catch (Exception e) {
                LogUtils.w(TAG,"ServerSocket close failed, exception=" + e);
                PlayerProgressListenerManager.getInstance().log("ServerSocket close failed, exception=" + e);
            } finally {
                // 关闭socket处理线程池，不再接受新任务
                mSocketPool.shutdown();
                // 如果监听线程还活着，发出中断标记
                if (mRequestThread != null && mRequestThread.isAlive()) {
                    mRequestThread.interrupt();
                }
            }
        }
    }

    public boolean isServerObjAlive() {
        if(mLocalServer == null) {
            return false;
        }
        return !mLocalServer.isClosed();
    }
    public boolean isRequestThreadAlive() {
        return mRequestThread != null && mRequestThread.isAlive();
    }
    public boolean isThreadOOM(){
        ThreadPoolExecutor pool = ((ThreadPoolExecutor)mSocketPool);
        int active = pool.getActiveCount();       //正在干活的活跃线程
        int queueSize = pool.getQueue().size();   //排队任务数
        int max = pool.getMaximumPoolSize();

//告警条件：活跃线程达到最大线程，同时队列持续增长 → 大概率线程卡死泄漏
        if(active >= max && queueSize > 8) {
            return true;
        }
        return false;
    }
    public String isThreadInfo(){
        ThreadPoolExecutor pool = ((ThreadPoolExecutor)mSocketPool);
        int active = pool.getActiveCount();       //正在干活的活跃线程
        int queueSize = pool.getQueue().size();   //排队任务数
        int max = pool.getMaximumPoolSize();

//告警条件：活跃线程达到最大线程，同时队列持续增长 → 大概率线程卡死泄漏
        return "活跃数:"+active+" 排队数:"+queueSize+" max:"+max;
    }
    /**
     * 访问本地代理ping接口，真正验证代理服务完整可用
     * @return true 代理正常；false代理失效
     * 和ProxyCacheUtils.isServerAlive()一直，可以直接替代
     */
    public boolean pingProxyHealth() {
        return Pinger.ping(2,100);
    }

}
