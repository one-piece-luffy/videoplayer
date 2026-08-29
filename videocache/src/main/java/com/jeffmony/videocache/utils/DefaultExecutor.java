package com.jeffmony.videocache.utils;

import android.util.Log;

import com.jeffmony.videocache.PlayerProgressListenerManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 结合有界队列 + 自定义丢弃策略 + 队列主动清空，您将获得一个既能抗堆积、又不会触发栈溢出的健壮线程池实现。
 */
public class DefaultExecutor {
    private static final String TAG = DefaultExecutor.class.getName();
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_TIME = 5;
    //  使用有界队列，容量设为 50~100，防止无限堆积
    private static final int QUEUE_CAPACITY = 50;
    public static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "DefaultExecutor #" + mCount.getAndIncrement());
        }
    };
    // 自定义拒绝策略：丢弃最旧，若仍失败则放弃新任务（防止递归）
    private static final RejectedExecutionHandler sRejectHandler =
            (r, executor) -> {
                if (!executor.isShutdown()) {
                    executor.getQueue().poll(); // 丢弃队头
                    // 尝试入队一次，若失败则直接放弃
                    boolean offered = executor.getQueue().offer(r);
                    if (!offered) {
                        Log.w("DefaultExecutor", "Queue still full, new task discarded.");
                    }
                }
            };

    private static final ExecutorService mExecutorService =
            new ThreadPoolExecutor(MAXIMUM_POOL_SIZE, MAXIMUM_POOL_SIZE,
                    KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    sPoolWorkQueue, sThreadFactory, sRejectHandler);
    static {
        // 允许核心线程超时回收，释放资源
        ((ThreadPoolExecutor) mExecutorService).allowCoreThreadTimeOut(true);
    }
    public static void execute(Runnable runnable) {
//        mExecutorService.execute(runnable);
        mExecutorService.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                // 捕获所有异常并记录
                t.printStackTrace();
                Log.e("DefaultExecutor", "Task execution failed", t);
                PlayerProgressListenerManager.getInstance().log("DefaultExecutor Task execution failed"); // 关键日志
            }
        });
    }

    public static ExecutorService getExecutorService() {
        return mExecutorService;
    }

    /**
     *  主动清空 DefaultExecutor 中已积压的旧启动任务
     */
    public static void clear(){
        if (mExecutorService instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) mExecutorService).getQueue().clear();
            Log.i(TAG, "Cleared pending tasks in DefaultExecutor.");
        }
    }

    public static void startMonitorThread() {

        int queueSize = sPoolWorkQueue.size();
            // 检查线程池是否还有活动线程
            boolean hasActiveThread = false;
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            Thread[] threads = new Thread[group.activeCount()];
            group.enumerate(threads);
            for (Thread t : threads) {
                if (t != null && t.getName().startsWith("DefaultExecutor") && t.isAlive()) {
                    hasActiveThread = true;
                    break;
                }
            }
            if (!hasActiveThread && queueSize > 0) {
                PlayerProgressListenerManager.getInstance().log("所有工作线程已死亡但队列还有任务，重建线程池"); // 关键日志
            }
    }

}
