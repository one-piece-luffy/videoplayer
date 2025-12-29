package com.baofu.videoplayer.danmu;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
// DanmakuPool.java
public class DanmakuPool {
    private static final int DEFAULT_MAX_POOL_SIZE = 200;

    private final int maxPoolSize;
    private final Queue<DanmakuItem> pool;
    private int createdCount = 0;
    private int recycledCount = 0;
    private int reusedCount = 0;

    public DanmakuPool() {
        this(DEFAULT_MAX_POOL_SIZE);
    }

    public DanmakuPool(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        this.pool = new LinkedList<>();
    }

    public DanmakuItem obtain(String text, int color, float speed) {
        DanmakuItem item = pool.poll();

        if (item == null) {
            item = new DanmakuItem(text, color, speed);
            createdCount++;
        } else {
            reusedCount++;
            item.setText(text);
            item.setColor(color);
            item.setSpeed(speed);
            item.setMeasured(false);
            // ==================== 重置背景属性,需要确保背景属性在回收时被重置 ====================
            item.setShowBackground(false);
            item.setBackgroundColor(0);
            item.setBackgroundPadding(0);
            item.setBackgroundRadius(0);
            item.setUserOwned(false);
            item.setPriority(0);

            item.clearGradient();
        }

        item.setX(0);
        item.setY(0);
        item.setClickable(true);
        item.setLineNumber(-1);
        item.setTag(null);
        item.setCreateTime(System.currentTimeMillis());

        return item;
    }

    public void recycle(DanmakuItem item) {
        if (item == null || pool.size() >= maxPoolSize) {
            return;
        }

        item.setTag(null);
        pool.offer(item);
        recycledCount++;
    }

    public void clear() {
        pool.clear();
    }

    public int size() {
        return pool.size();
    }

    public String getStats() {
        float reuseRate = getReuseRate();
        return String.format(Locale.getDefault(),
                "对象池统计:\n" +
                        "创建总数: %d\n" +
                        "回收总数: %d\n" +
                        "复用次数: %d\n" +
                        "复用率: %.1f%%\n" +
                        "当前池大小: %d/%d",
                createdCount, recycledCount, reusedCount,
                reuseRate, size(), maxPoolSize);
    }

    private float getReuseRate() {
        if (createdCount == 0) return 0;
        return reusedCount / (float) createdCount * 100;
    }
}