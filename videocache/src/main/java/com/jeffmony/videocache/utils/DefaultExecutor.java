package com.jeffmony.videocache.utils;

import android.util.Log;

import com.jeffmony.videocache.PlayerProgressListenerManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class DefaultExecutor {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;
    public static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>();

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "DefaultExecutor #" + mCount.getAndIncrement());
        }
    };

    private static final ExecutorService mExecutorService = new ThreadPoolExecutor(MAXIMUM_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                                                                                TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

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
