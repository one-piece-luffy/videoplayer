package com.baofu.videoplayer.danmu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

// FixedDanmakuView.java - 修复重叠问题的弹幕视图
public class FixedDanmakuView extends SurfaceView
        implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder surfaceHolder;
    private Thread renderThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;

    // 弹幕管理
    private final List<DanmakuItem> activeDanmakus = new ArrayList<>();
    private DanmakuPool danmakuPool;
    private DanmakuSpatialGrid spatialGrid;
    private Random random = new Random();

    // 画笔
    private Paint baseTextPaint;
    private Paint baseBackgroundPaint;

    // 触摸处理
    private float lastTouchX, lastTouchY;
    private boolean isTouching = false;
    private long touchDownTime;
    private DanmakuItem currentTouchDanmaku;
    private Handler uiHandler;

    // 点击事件监听器
    private DanmakuClickListener danmakuClickListener;
    private DanmakuLongClickListener danmakuLongClickListener;

    // 配置参数
    private boolean clickEnabled = true;
    private boolean clickThroughEnabled = false;
    private long clickThreshold = 200;
    private long longPressThreshold = 500;
    private float touchSlop = 10;
    private int maxClickDetection = 50;

    // 弹幕控制
    private float globalSpeed = 1.0f;
    private float minSpeed = 0.6f;
    private float maxSpeed = 8.0f;

    // ==================== 保留默认背景设置作为备用 ====================
    private int defaultBackgroundColor = Color.argb(100, 0, 0, 0);
    private float defaultBackgroundPadding = dp2px(getContext(), 5);
    private float defaultBackgroundRadius = dp2px(getContext(), 8);

    // ==================== 默认渐变色设置 ====================
    private int[] defaultGradientColors = null;
    private float[] defaultGradientPositions = null;
    private int defaultGradientType = DanmakuItem.GRADIENT_NONE;
    private float defaultGradientAngle = 0;

    private boolean allowOverlap = false;
    private boolean uniformSpeed = true;

    // 行数控制
    private int maxLines = 15;
    private float lineHeight = dp2px(getContext(), 40);
    private boolean[] lineOccupied;
    private int calculatedMaxLines;

    // 性能优化开关
    private int maxDanmakuCount = 100;
    private boolean enableSpatialGrid = true;
    private boolean enableObjectPool = true;

    // 性能监控
    private long lastFpsTime;
    private int frameCount;
    private float currentFps;
    private long drawTime;
    private long updateTime;
    private int clickDetections = 0;

    // 文字大小调整控制
    private volatile boolean isTextSizeChanging = false;
    private int currentTextSize = dp2px(getContext(), 16);

    // 性能统计
    private long totalUpdateTime = 0;
    private long totalDrawTime = 0;
    private int updateCount = 0;
    private int drawCount = 0;
    private long statsStartTime = System.currentTimeMillis();

    // ==================== 新增：解决重叠问题的关键字段 ====================
    private Map<Integer, Queue<DanmakuItem>> waitingQueues = new HashMap<>();
    private long maxWaitTime = 3000;
    private long maxDanmakuLife = 8000;
    private Handler delayHandler;
    private boolean isQueueChecking = false;
    private long lastQueueCleanupTime = 0;
    private static final long QUEUE_CLEANUP_INTERVAL = 2000;
    private int maxWaitingQueueSize = 20;

    // 弹幕间距和时间间隔控制
    private Map<Integer, Long> lastDanmakuTimePerLine = new HashMap<>();
    private float minIntervalBetweenDanmakus = 100;
    private float danmakuSpacingRatio = 0.05f;
    private float minDanmakuSpacing = dp2px(getContext(), 10);
    private float maxDanmakuSpacing = dp2px(getContext(), 50);

    // 分别定义水平和垂直的默认padding
    private float defaultBackgroundHorizontalPadding = dp2px(getContext(), 5);
    private float defaultBackgroundVerticalPaddingRatio = 0.3f; // 垂直padding占水平的30%

    // 弹幕添加同步锁（解决同时添加多条弹幕重叠的关键）
    private final Object danmakuAddLock = new Object();
    private List<DanmakuItem> pendingDanmakus = new ArrayList<>();
    private boolean isProcessingPending = false;

    // 临时存储后台时的弹幕
    private List<DanmakuItem> tempDanmakus = new ArrayList<>();
    // FixedDanmakuView.java - 修改核心更新逻辑
    private long lastUpdateTime = System.currentTimeMillis();
    // 构造方法
    public FixedDanmakuView(Context context) {
        super(context);
        init();
    }

    public FixedDanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FixedDanmakuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);

        uiHandler = new Handler(Looper.getMainLooper());
        delayHandler = new Handler(Looper.getMainLooper());
        initPaints();

        if (enableObjectPool) {
            danmakuPool = new DanmakuPool();
        }

        setZOrderOnTop(true);
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);

        setFocusable(true);
        setClickable(true);

        lastFpsTime = System.nanoTime();
        lastQueueCleanupTime = System.currentTimeMillis();
    }

    private void initPaints() {
        baseTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baseTextPaint.setTextSize(currentTextSize);
        baseTextPaint.setStyle(Paint.Style.FILL);

        baseBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baseBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (enableSpatialGrid) {
            if (spatialGrid == null) {
                spatialGrid = new DanmakuSpatialGrid(w, h);
            } else {
                spatialGrid.updateSize(w, h);
            }
        }

        updateLineConfiguration();
    }

    private void updateLineConfiguration() {
        calculatedMaxLines = Math.max(1, (int) (getHeight() / lineHeight));
        int actualLines = maxLines == -1 ? calculatedMaxLines : Math.min(maxLines, calculatedMaxLines);
        lineOccupied = new boolean[actualLines];
    }


    /**
     * 系统弹幕
     * @param text
     */
    public void addSystemDanmaku(String text) {
        //随机颜色
//        addDanmaku(text, Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)),true,System.currentTimeMillis(),
//                null,0,false);

        addDanmaku(text, 0xffffffff, false, System.currentTimeMillis(),
                null, 0, false, DanmakuItem.MAX_PRIORITY, true, 0xAAAC4718, defaultBackgroundPadding, defaultBackgroundRadius,
                DanmakuItem.GRADIENT_SUNSET, null, DanmakuItem.GRADIENT_LINEAR, getAngleForDirection(DanmakuItem.DIRECTION_LEFT_TO_RIGHT), false);
    }


    /**
     * 用户弹幕
     * @param text
     * @param color
     */
    public void addUserDanmaku(String text, int color) {
        addDanmaku(text, color, true, System.currentTimeMillis(), null, 0, true, DanmakuItem.MAX_PRIORITY,
                false, defaultBackgroundColor, defaultBackgroundPadding, defaultBackgroundRadius,
                null, null, DanmakuItem.GRADIENT_NONE, 0, false);
    }

    /**
     * 常规弹幕
     */
    public void addDanmaku(String text, int color,  long showTime,String id,int like_count,int priority
    ) {
        addDanmaku(text, color, true, showTime, id, like_count, false, priority, false, defaultBackgroundColor, defaultBackgroundPadding, defaultBackgroundRadius,
                null, null, DanmakuItem.GRADIENT_NONE, 0, false);
    }


    /**
     * 添加弹幕（线程安全版本，解决重叠问题）
     */
    public void addDanmaku(String text, int color, boolean clickable,  long showTime,String id,int like_count,
                           boolean isUserOwned, int priority,boolean showBackground, int backgroundColor,
                           float backgroundPadding, float backgroundRadius,
                           int[] gradientColors, float[] gradientPositions,
                           int gradientType, float gradientAngle, boolean gradientReversed) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 创建弹幕对象（速度暂设为0，后面会计算）
        DanmakuItem danmaku;
        if (enableObjectPool && danmakuPool != null) {
            danmaku = danmakuPool.obtain(text, color, 0);
        } else {
            danmaku = new DanmakuItem(text, color, 0);
        }

        danmaku.setCreateTime(System.currentTimeMillis());
        danmaku.setClickable(clickable);
        danmaku.id=id;
        danmaku.show_time=showTime;
        danmaku.like_count=like_count;
        if (isUserOwned) {
            danmaku.markAsUserOwned();
        }
        if (showBackground) {
            danmaku.setBackground(true, backgroundColor, backgroundPadding, backgroundRadius);

            // ==================== 设置渐变色 ====================
            if (gradientColors != null && gradientColors.length >= 2 &&
                    gradientType != DanmakuItem.GRADIENT_NONE) {

                danmaku.setShowBackground(true);
                danmaku.setBackgroundPadding(backgroundPadding);
                danmaku.setBackgroundRadius(backgroundRadius);

                // 根据渐变类型设置
                switch (gradientType) {
                    case DanmakuItem.GRADIENT_LINEAR:
                        danmaku.setLinearGradient(gradientColors, gradientAngle);
                        break;
                    case DanmakuItem.GRADIENT_RADIAL:
                        danmaku.setRadialGradient(gradientColors, 0.5f, 0.5f);
                        break;
                    case DanmakuItem.GRADIENT_SWEEP:
                        danmaku.setSweepGradient(gradientColors);
                        break;
                }

                if (gradientPositions != null) {
                    // 这里需要修改DanmakuItem以支持自定义位置
                }

                if (gradientReversed) {
                    danmaku.setGradientReversed(true);
                }
            }
        }
        Paint itemPaint = danmaku.getPaint();
        itemPaint.setTextSize(currentTextSize);
        itemPaint.setColor(color);

        // 使用同步锁添加到待处理列表，确保弹幕按顺序处理
        synchronized (danmakuAddLock) {
            pendingDanmakus.add(danmaku);
        }

        // 触发处理
        schedulePendingDanmakusProcessing();
    }

    /**
     * 调度待处理弹幕处理
     */
    private void schedulePendingDanmakusProcessing() {
        if (!isProcessingPending) {
            isProcessingPending = true;
            uiHandler.post(this::processPendingDanmakus);
        }
    }

    /**
     * 处理待处理弹幕（关键方法，解决同时添加多条弹幕的重叠问题）
     */
    private void processPendingDanmakus() {
        List<DanmakuItem> toProcess;

        // 获取所有待处理弹幕
        synchronized (danmakuAddLock) {
            if (pendingDanmakus.isEmpty()) {
                isProcessingPending = false;
                return;
            }
            toProcess = new ArrayList<>(pendingDanmakus);
            pendingDanmakus.clear();
        }

        // 按照创建时间排序（确保先来的先处理）
//        toProcess.sort((d1, d2) -> Long.compare(d1.getCreateTime(), d2.getCreateTime()));
        // 用户弹幕（高优先级）优先处理
        toProcess.sort((d1, d2) -> {
            // 首先按优先级排序（数值大的优先）
            int priorityCompare = Integer.compare(d2.getPriority(), d1.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // 优先级相同则按创建时间排序（先来的先处理）
            return Long.compare(d1.getCreateTime(), d2.getCreateTime());
        });

        // 批量处理弹幕
        for (DanmakuItem danmaku : toProcess) {
            processSingleDanmaku(danmaku);
        }

        isProcessingPending = false;

        // 检查是否还有新的弹幕需要处理
        synchronized (danmakuAddLock) {
            if (!pendingDanmakus.isEmpty()) {
                schedulePendingDanmakusProcessing();
            }
        }
    }

    /**
     * 处理单个弹幕（核心逻辑）
     */
    private void processSingleDanmaku(DanmakuItem danmaku) {
        long currentTime = System.currentTimeMillis();

        // 检查弹幕是否过期
        if (currentTime - danmaku.getCreateTime() > maxDanmakuLife) {
            Log.d("Danmaku", "弹幕已过期，丢弃: " + danmaku.getText());
            if (enableObjectPool && danmakuPool != null) {
                danmakuPool.recycle(danmaku);
            }
            return;
        }

        cleanupExpiredWaitingDanmakusIfNeeded();

        if (isWaitingQueueTooLong()) {
            Log.d("Danmaku", "等待队列过长，丢弃新弹幕: " + danmaku.getText());
            if (enableObjectPool && danmakuPool != null) {
                danmakuPool.recycle(danmaku);
            }
            return;
        }

        if (activeDanmakus.size() >= maxDanmakuCount) {
            removeOldestDanmaku();
        }

        // 生成弹幕速度
        float speed;
        if (uniformSpeed) {
            speed = globalSpeed;
        } else {
            speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed);
            speed *= globalSpeed;
        }
        danmaku.setSpeed(speed);

        // 智能分配行号（考虑时间间隔，防止重叠）
        int actualLine = allocateLineWithTimeCheck(-1, currentTime);
        danmaku.setLineNumber(actualLine);

        // 更新该行最后添加弹幕的时间
        lastDanmakuTimePerLine.put(actualLine, currentTime);

        // 计算Y坐标位置
        float y = calculateYPosition(actualLine);
        danmaku.setY(y);

        // 测量弹幕
        danmaku.measure(baseTextPaint);
        danmaku.updateClickArea();

        // 检查是否可以立即显示
        if (canShowImmediately(danmaku, actualLine)) {
            showDanmakuImmediately(danmaku, actualLine);
        } else {
            // 添加到等待队列
            if (addToWaitingQueue(danmaku, actualLine)) {
                scheduleQueueCheck();
            }
        }
    }

    /**
     * 智能分配行号（考虑时间间隔）
     */
    private int allocateLineWithTimeCheck(int requestedLine, long currentTime) {
        if (requestedLine >= 0 && requestedLine < lineOccupied.length) {
            Long lastTime = lastDanmakuTimePerLine.get(requestedLine);
            if (lastTime != null && currentTime - lastTime < minIntervalBetweenDanmakus) {
                return findAlternativeLine(currentTime);
            }
            return requestedLine;
        }

        return findOptimalLine(currentTime);
    }

    /**
     * 查找最佳行
     */
    private int findOptimalLine(long currentTime) {
        List<Integer> availableLines = new ArrayList<>();

        // 第一轮：查找最近没有添加过弹幕的行
        for (int i = 0; i < lineOccupied.length; i++) {
            Long lastTime = lastDanmakuTimePerLine.get(i);
            if (lastTime == null || currentTime - lastTime > minIntervalBetweenDanmakus * 2) {
                if (!lineOccupied[i]) {
                    availableLines.add(i);
                }
            }
        }

        if (!availableLines.isEmpty()) {
            return availableLines.get(random.nextInt(availableLines.size()));
        }

        // 第二轮：查找所有可用行
        availableLines.clear();
        for (int i = 0; i < lineOccupied.length; i++) {
            if (!lineOccupied[i]) {
                availableLines.add(i);
            }
        }

        if (!availableLines.isEmpty()) {
            return availableLines.get(random.nextInt(availableLines.size()));
        }

        // 第三轮：随机选择一行
        return random.nextInt(lineOccupied.length);
    }

    /**
     * 查找替代行
     */
    private int findAlternativeLine(long currentTime) {
        List<Integer> alternativeLines = new ArrayList<>();

        for (int i = 0; i < lineOccupied.length; i++) {
            Long lastTime = lastDanmakuTimePerLine.get(i);
            if (lastTime == null || currentTime - lastTime > minIntervalBetweenDanmakus) {
                if (!lineOccupied[i]) {
                    alternativeLines.add(i);
                }
            }
        }

        if (!alternativeLines.isEmpty()) {
            return alternativeLines.get(random.nextInt(alternativeLines.size()));
        }

        return random.nextInt(lineOccupied.length);
    }

    private void removeOldestDanmaku() {
        synchronized (activeDanmakus) {
            if (!activeDanmakus.isEmpty()) {
                DanmakuItem oldest = activeDanmakus.get(0);

                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.remove(oldest);
                }

                if (enableObjectPool && danmakuPool != null) {
                    danmakuPool.recycle(oldest);
                }

                activeDanmakus.remove(0);

                if (oldest.getLineNumber() >= 0 && oldest.getLineNumber() < lineOccupied.length) {
                    lineOccupied[oldest.getLineNumber()] = false;
                }
            }
        }
    }

    private float calculateYPosition(int line) {
        if (line < 0 || line >= lineOccupied.length) {
            return random.nextInt(Math.max(1, getHeight() - 100)) + 50;
        }

        float y = line * lineHeight + lineHeight / 2 + 20;
        return Math.min(y, getHeight() - 20);
    }

    // ==================== 等待队列管理 ====================

    private boolean canShowImmediately(DanmakuItem danmaku, int line) {
        // 用户弹幕可以强制显示
        if (danmaku.isUserOwned()) {
            return true;
        }
        if (allowOverlap) {
            return true;
        }

        float screenWidth = getWidth();
        float requiredSpace = danmaku.getWidth() + calculateDynamicSpacing(screenWidth, danmaku.getWidth());

        synchronized (activeDanmakus) {
            for (DanmakuItem item : activeDanmakus) {
                if (item.getLineNumber() == line) {
                    float itemRight = item.getX() + item.getWidth();

                    if (itemRight > -screenWidth * 0.1f && itemRight < screenWidth + requiredSpace) {
                        float spaceNeeded = itemRight + requiredSpace;
                        if (spaceNeeded > screenWidth) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private void showDanmakuImmediately(DanmakuItem danmaku, int line) {
        float x = calculateXPositionForImmediateShow(danmaku, line);
        danmaku.setX(x);

        // 紧急重叠检查
        if (checkForImmediateOverlap(danmaku, line, x)) {
            x = adjustXForOverlap(danmaku, line, x);
            danmaku.setX(x);
        }

        synchronized (activeDanmakus) {
            activeDanmakus.add(danmaku);
        }

        if (enableSpatialGrid && spatialGrid != null) {
            spatialGrid.insert(danmaku);
        }

        if (line >= 0 && line < lineOccupied.length) {
            lineOccupied[line] = true;
        }
    }

    private float calculateXPositionForImmediateShow(DanmakuItem newDanmaku, int line) {
        if (allowOverlap) {
            return getWidth();
        }

        float screenWidth = getWidth();
        float maxRight = screenWidth;

        synchronized (activeDanmakus) {
            for (DanmakuItem item : activeDanmakus) {
                if (item.getLineNumber() == line) {
                    float itemRight = item.getX() + item.getWidth();
                    if (itemRight > -screenWidth * 0.1f && itemRight < screenWidth * 1.5f) {
                        if (itemRight > maxRight) {
                            maxRight = itemRight;
                        }
                    }
                }
            }
        }

        float spacing = calculateDynamicSpacing(screenWidth, newDanmaku.getWidth());
        return Math.max(screenWidth, maxRight + spacing);
    }

    /**
     * 紧急重叠检查
     */
    private boolean checkForImmediateOverlap(DanmakuItem newDanmaku, int line, float startX) {
        if (allowOverlap) {
            return false;
        }

        synchronized (activeDanmakus) {
            for (DanmakuItem item : activeDanmakus) {
                if (item.getLineNumber() == line) {
                    float itemLeft = item.getX();
                    float itemRight = item.getX() + item.getWidth();
                    float newLeft = startX;
                    float newRight = startX + newDanmaku.getWidth();

                    // 检查是否有重叠
                    if (newLeft < itemRight && newRight > itemLeft) {
                        Log.w("Danmaku", "检测到重叠！弹幕: " + newDanmaku.getText() +
                                " 与 " + item.getText() + " 重叠");
                        return true;
                    }

                    // 检查间距是否足够
                    float minSpacing = calculateDynamicSpacing(getWidth(), newDanmaku.getWidth());
                    if (itemRight > startX - minSpacing) {
                        Log.w("Danmaku", "间距不足！弹幕: " + newDanmaku.getText() +
                                " 与 " + item.getText() + " 间距太小");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 为重叠的弹幕调整X坐标
     */
    private float adjustXForOverlap(DanmakuItem danmaku, int line, float originalX) {
        float screenWidth = getWidth();
        float maxRight = screenWidth;

        synchronized (activeDanmakus) {
            for (DanmakuItem item : activeDanmakus) {
                if (item.getLineNumber() == line) {
                    float itemRight = item.getX() + item.getWidth();
                    if (itemRight > maxRight) {
                        maxRight = itemRight;
                    }
                }
            }
        }

        float spacing = calculateDynamicSpacing(screenWidth, danmaku.getWidth());
        float adjustedX = maxRight + spacing * 1.5f;

        Log.d("Danmaku", "调整弹幕X坐标: " + danmaku.getText() +
                " 从 " + originalX + " 到 " + adjustedX);

        return Math.max(screenWidth, adjustedX);
    }

    private float calculateDynamicSpacing(float screenWidth, float danmakuWidth) {
        float spacingByRatio = screenWidth * danmakuSpacingRatio;
        float spacingByWidth = danmakuWidth * 0.2f;
        float spacing = Math.max(spacingByRatio, spacingByWidth);
        spacing = Math.max(minDanmakuSpacing, Math.min(spacing, maxDanmakuSpacing));
        return spacing;
    }

    private boolean addToWaitingQueue(DanmakuItem danmaku, int line) {
        synchronized (waitingQueues) {
            Queue<DanmakuItem> queue = waitingQueues.get(line);
            if (queue == null) {
                queue = new LinkedList<>();
                waitingQueues.put(line, queue);
            }

            if (queue.size() >= maxWaitingQueueSize) {
//                Log.d("DanmakuQueue", "等待队列已满，丢弃弹幕: " + danmaku.getText());
                if (enableObjectPool && danmakuPool != null) {
                    danmakuPool.recycle(danmaku);
                }
                return false;
            }

            queue.offer(danmaku);
//            Log.d("DanmakuQueue", "弹幕进入等待队列: " + danmaku.getText() +
//                    ", 行: " + line + ", 等待队列大小: " + queue.size());
            return true;
        }
    }

    private void scheduleQueueCheck() {
        if (!isQueueChecking) {
            isQueueChecking = true;
            delayHandler.postDelayed(this::checkWaitingQueues, 100);
        }
    }

    private void checkWaitingQueues() {
        isQueueChecking = false;
        cleanupExpiredWaitingDanmakusIfNeeded();

        boolean hasProcessed = false;
        synchronized (waitingQueues) {
            for (Map.Entry<Integer, Queue<DanmakuItem>> entry : waitingQueues.entrySet()) {
                int line = entry.getKey();
                Queue<DanmakuItem> queue = entry.getValue();

                if (!queue.isEmpty()) {
                    // ==================== 修改：优先处理用户弹幕 ====================
                    // 检查队列中是否有用户弹幕
                    DanmakuItem userDanmaku = null;
                    //常规弹幕
                    DanmakuItem regularDanmaku = null;

                    for (DanmakuItem item : queue) {
                        if (item.isUserOwned()) {
                            userDanmaku = item;
                            break;
                        } else if (regularDanmaku == null) {
                            regularDanmaku = item;
                        }
                    }

                    // 优先处理用户弹幕
                    DanmakuItem danmakuToProcess = userDanmaku != null ? userDanmaku : regularDanmaku;

                    if (danmakuToProcess == null) {
                        continue;
                    }

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - danmakuToProcess.getCreateTime() > maxWaitTime) {
                        // 从队列中移除
                        queue.remove(danmakuToProcess);

                        if (enableObjectPool && danmakuPool != null) {
                            danmakuPool.recycle(danmakuToProcess);
                        }
                        continue;
                    }

                    if (canShowImmediately(danmakuToProcess, line)) {
                        // ==================== 修改：优先显示用户弹幕，即使有重叠 ====================
                        boolean forceShow = danmakuToProcess.isUserOwned();
                        if (forceShow || canShowImmediately(danmakuToProcess, line)) {
                            queue.remove(danmakuToProcess);
                            showDanmakuImmediately(danmakuToProcess, line);
                            hasProcessed = true;
                        }
                    }
                }
            }
        }

        if (hasWaitingDanmakus()) {
            scheduleQueueCheck();
        }
    }


    private boolean isWaitingQueueTooLong() {
        int totalWaiting = 0;
        synchronized (waitingQueues) {
            for (Queue<DanmakuItem> queue : waitingQueues.values()) {
                totalWaiting += queue.size();
                if (queue.size() >= maxWaitingQueueSize) {
                    return true;
                }
            }
        }
        return totalWaiting > maxDanmakuCount * 2;
    }

    private void cleanupExpiredWaitingDanmakusIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastQueueCleanupTime > QUEUE_CLEANUP_INTERVAL) {
            cleanupExpiredWaitingDanmakus();
            lastQueueCleanupTime = currentTime;
        }
    }

    private void cleanupExpiredWaitingDanmakus() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;

        synchronized (waitingQueues) {
            for (Queue<DanmakuItem> queue : waitingQueues.values()) {
                Iterator<DanmakuItem> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    DanmakuItem danmaku = iterator.next();
                    if (currentTime - danmaku.getCreateTime() > maxDanmakuLife) {
                        iterator.remove();
                        cleanedCount++;
                        if (enableObjectPool && danmakuPool != null) {
                            danmakuPool.recycle(danmaku);
                        }
//                        Log.d("DanmakuQueue", "清理过期弹幕: " + danmaku.getText());
                    }
                }
            }
        }

        if (cleanedCount > 0) {
            Log.d("DanmakuQueue", "清理了 " + cleanedCount + " 个过期弹幕");
        }
    }

    private boolean hasWaitingDanmakus() {
        synchronized (waitingQueues) {
            for (Queue<DanmakuItem> queue : waitingQueues.values()) {
                if (!queue.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 触摸事件处理 ====================

    // 在 FixedDanmakuView 类中添加以下方法

    /**
     * 修复横屏/全屏时的坐标转换问题
     */
    private float[] convertTouchCoordinates(MotionEvent event) {
        float[] result = new float[2];

        // 获取视图在屏幕上的位置
        int[] location = new int[2];
        getLocationOnScreen(location);

        // 获取视图的旋转状态
        float rotation = getRotation();

        float rawX = event.getRawX();
        float rawY = event.getRawY();

        // 转换为视图本地坐标
        result[0] = rawX - location[0];
        result[1] = rawY - location[1];

        // 考虑视图的缩放和旋转
        float scaleX = getScaleX();
        float scaleY = getScaleY();

        if (scaleX != 1.0f || scaleY != 1.0f) {
            result[0] /= scaleX;
            result[1] /= scaleY;
        }

        // 处理旋转
        if (rotation != 0) {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;

            // 将坐标平移到中心
            result[0] -= centerX;
            result[1] -= centerY;

            // 反向旋转
            double radians = Math.toRadians(-rotation);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);

            float rotatedX = result[0] * cos - result[1] * sin;
            float rotatedY = result[0] * sin + result[1] * cos;

            // 平移回原位置
            result[0] = rotatedX + centerX;
            result[1] = rotatedY + centerY;
        }

        // 确保坐标在视图范围内
        result[0] = Math.max(0, Math.min(result[0], getWidth()));
        result[1] = Math.max(0, Math.min(result[1], getHeight()));

        return result;
    }
    // 在 FixedDanmakuView 类中添加以下方法

    /**
     * 外部检查是否点击到了弹幕
     */
    public boolean isPointOnDanmaku(float x, float y) {
        DanmakuItem danmaku = findDanmakuAtPoint(x, y);
        return danmaku != null && !shouldClickThrough(danmaku);
    }

    /**
     * 获取点击到的弹幕（供外部使用）
     */
    public DanmakuItem getClickedDanmaku(float x, float y) {
        return findDanmakuAtPoint(x, y);
    }

    /**
     * 暴露方法供容器检查弹幕点击状态
     */
    public boolean isClickEnabled() {
        return clickEnabled;
    }

    public boolean isPaused() {
        return isPaused;
    }

    // 在 FixedDanmakuView 类中修改以下方法：

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        if (!clickEnabled || isPaused) {
        if (!clickEnabled ) {
            return false; // 不可点击，直接穿透
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                performClick();

                // 检查是否点击到了弹幕
                DanmakuItem clickedDanmaku = findDanmakuAtPoint(x, y);
                if (clickedDanmaku != null && !shouldClickThrough(clickedDanmaku)) {
                    // 点击到了弹幕，开始处理
                    lastTouchX = x;
                    lastTouchY = y;
                    isTouching = true;
                    touchDownTime = System.currentTimeMillis();
                    currentTouchDanmaku = clickedDanmaku;

                    // 设置长按监听
                    uiHandler.postDelayed(longPressRunnable, longPressThreshold);
                    return true; // 消费事件
                } else {
                    // 点击到空白区域，不消费事件，让事件穿透
                    isTouching = false; // 确保状态重置
                    return false;
                }

            case MotionEvent.ACTION_MOVE:
                if (isTouching && currentTouchDanmaku != null) {
                    // 如果正在处理弹幕点击，检查是否移动超出范围
                    if (Math.abs(x - lastTouchX) > touchSlop ||
                            Math.abs(y - lastTouchY) > touchSlop) {
                        handleTouchCancel();
                    }
                    return true;
                }
                // 空白区域的移动，不处理，让事件穿透
                return false;

            case MotionEvent.ACTION_UP:
                if (isTouching && currentTouchDanmaku != null) {
                    // 处理弹幕点击
                    uiHandler.removeCallbacks(longPressRunnable);

                    long touchDuration = System.currentTimeMillis() - touchDownTime;

                    if (touchDuration < clickThreshold &&
                            Math.abs(x - lastTouchX) < touchSlop &&
                            Math.abs(y - lastTouchY) < touchSlop) {

                        // 触发点击事件
                        if (danmakuClickListener != null) {
                            danmakuClickListener.onDanmakuClick(currentTouchDanmaku);
                        }
                        //震动
//                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        showClickFeedback(currentTouchDanmaku);
                    }

                    handleTouchCancel();
                    return true;
                }
                // 空白区域的抬起，不处理，让事件穿透
                return false;

            case MotionEvent.ACTION_CANCEL:
                handleTouchCancel();
                return false; // 取消事件，让事件穿透
        }

        // 默认不消费事件，让事件穿透
        return false;
    }

    /**
     * 修改 dispatchTouchEvent 确保事件正确传递
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 记录事件日志用于调试

        // 先调用父类方法
        boolean handled = super.dispatchTouchEvent(event);

        // 如果父类没有处理（可能是因为没有设置OnTouchListener）
        // 则调用自己的onTouchEvent
        if (!handled) {
            handled = onTouchEvent(event);
        }

        return handled;
    }


    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;
        isTouching = true;
        touchDownTime = System.currentTimeMillis();

        currentTouchDanmaku = findDanmakuAtPoint(x, y);

        if (shouldClickThrough(currentTouchDanmaku)) {
            currentTouchDanmaku = null;
            isTouching = false;
            return false;
        }

        if (currentTouchDanmaku != null) {
            uiHandler.postDelayed(longPressRunnable, longPressThreshold);
        }

        return currentTouchDanmaku != null;
    }

    public DanmakuItem findDanmakuAtPoint(float x, float y) {
        if (!clickEnabled) return null;

        // 从后往前检查（最后添加的弹幕在最上层）
        synchronized (activeDanmakus) {
            for (int i = activeDanmakus.size() - 1; i >= 0; i--) {
                DanmakuItem item = activeDanmakus.get(i);

                if (item.isClickable() && isPointInDanmaku(x, y, item)) {
                    return item;
                }
            }
        }

        return null;
    }
    // 更精确的点在弹幕内判断
    private boolean isPointInDanmaku(float x, float y, DanmakuItem danmaku) {
        if (danmaku == null) return false;

        // 获取点击区域
        RectF clickArea = danmaku.getClickArea();
        if (clickArea == null) return false;

        // 添加边界检查
        if (x < clickArea.left || x > clickArea.right ||
                y < clickArea.top || y > clickArea.bottom) {
            return false;
        }

        // 可选：对于特别长的弹幕，可以进一步缩小有效点击区域
        if (danmaku.getWidth() > getWidth() * 0.8f) {
            // 超长弹幕只允许点击中间部分
            float centerX = (clickArea.left + clickArea.right) / 2;
            float centerWidth = Math.min(danmaku.getWidth(), getWidth() * 0.3f);
            return Math.abs(x - centerX) < centerWidth / 2;
        }

        return true;
    }
    /**
     * 判断是否应该穿透点击
     */
    private boolean shouldClickThrough(DanmakuItem danmaku) {
        if (!clickThroughEnabled || danmaku == null) {
            return false; // 不穿透
        }

        if (!danmaku.isClickable()) {
            return true; // 弹幕不可点击，穿透
        }

        // 透明度检查
        int alpha = Color.alpha(danmaku.getColor());
        return alpha < 51; // 透明度太低也穿透
    }

    /**
     * 暴露给外部的方法
     */
    public boolean shouldClickThrough(float x, float y) {
        DanmakuItem danmaku = findDanmakuAtPoint(x, y);
        return shouldClickThrough(danmaku);
    }
    private void handleTouchMove(float x, float y) {
        if (isTouching &&
                (Math.abs(x - lastTouchX) > touchSlop ||
                        Math.abs(y - lastTouchY) > touchSlop)) {
            handleTouchCancel();
        }
    }

    private boolean handleTouchUp(float x, float y) {
        if (!isTouching) return false;

        uiHandler.removeCallbacks(longPressRunnable);

        long touchDuration = System.currentTimeMillis() - touchDownTime;

        if (touchDuration < clickThreshold &&
                Math.abs(x - lastTouchX) < touchSlop &&
                Math.abs(y - lastTouchY) < touchSlop) {

            return handleClick(x, y);
        }

        handleTouchCancel();
        return false;
    }

    private boolean handleClick(float x, float y) {
        DanmakuItem clickedDanmaku = findDanmakuAtPoint(x, y);
        if (clickedDanmaku != null && !shouldClickThrough(clickedDanmaku)) {
            if (danmakuClickListener != null) {
                danmakuClickListener.onDanmakuClick(clickedDanmaku);
            }

//            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            showClickFeedback(clickedDanmaku);
            return true;
        }

        return false;
    }

    /**
     * 弹幕点击视觉反馈效果
     * @param danmaku
     */
    private void showClickFeedback(DanmakuItem danmaku) {
        Paint originalPaint = danmaku.getPaint();
        int originalColor = danmaku.getColor();

        Paint highlightPaint = new Paint(originalPaint);
        highlightPaint.setColor(Color.WHITE);
        highlightPaint.setUnderlineText(true);
        highlightPaint.setShadowLayer(5, 0, 0, Color.YELLOW);

        danmaku.setPaint(highlightPaint);
        invalidate();

        uiHandler.postDelayed(() -> {
            danmaku.setPaint(originalPaint);
            danmaku.setColor(originalColor);
            invalidate();
        }, 500);
    }

    private void handleTouchCancel() {
        uiHandler.removeCallbacks(longPressRunnable);
        isTouching = false;
        currentTouchDanmaku = null;
    }

    private Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTouching && currentTouchDanmaku != null && danmakuLongClickListener != null) {
                danmakuLongClickListener.onDanmakuLongClick(currentTouchDanmaku);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                handleTouchCancel();
            }
        }
    };

    // ==================== 渲染循环 ====================

    @Override
    public void run() {
        long lastUpdateTime = System.nanoTime();

        while (isRunning) {
            if (isPaused || isTextSizeChanging) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            long startTime = System.nanoTime();
            Canvas canvas = null;

            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                    long currentTime = System.nanoTime();
                    float deltaTime = (currentTime - lastUpdateTime) / 1_000_000f;
                    lastUpdateTime = currentTime;

                    long updateStart = System.nanoTime();
                    updateDanmakus(deltaTime);
                    long updateEnd = System.nanoTime();

                    long drawStart = System.nanoTime();
                    drawDanmakus(canvas);
                    long drawEnd = System.nanoTime();

                    long updateDuration = updateEnd - updateStart;
                    long drawDuration = drawEnd - drawStart;

                    synchronized (this) {
                        totalUpdateTime += updateDuration;
                        totalDrawTime += drawDuration;
                        updateCount++;
                        drawCount++;
                    }

                    updateTime = updateDuration;
                    drawTime = drawDuration;

                    calculateFPS();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }

            try {
                long frameTime = System.nanoTime() - startTime;
                long sleepTime = Math.max(0, (16_666_666 - frameTime) / 1_000_000);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void updateDanmakus(float deltaTime) {
        long currentTime = System.currentTimeMillis();
        float actualDeltaTime = (currentTime - lastUpdateTime); // 实际经过的毫秒数
        lastUpdateTime = currentTime;

        // 如果暂停时间过长，跳过更新
        if (actualDeltaTime > 1000) {
            actualDeltaTime = 16.67f; // 限制最大deltaTime
        }

        synchronized (activeDanmakus) {
            Iterator<DanmakuItem> iterator = activeDanmakus.iterator();
            Arrays.fill(lineOccupied, false);

            while (iterator.hasNext()) {
                DanmakuItem danmaku = iterator.next();

                // 基于实际时间更新位置，而不是固定帧率
                // 公式：新位置 = 原位置 - (速度 × 实际经过时间 / 基准帧时间)
                float movement = danmaku.getSpeed() * (actualDeltaTime / 16.67f);
                danmaku.setX(danmaku.getX() - movement);
                danmaku.updateClickArea();

                int line = danmaku.getLineNumber();
                if (line >= 0 && line < lineOccupied.length) {
                    lineOccupied[line] = true;
                }

                // 检查弹幕是否完全离开屏幕
                if (danmaku.getX() + danmaku.getWidth() < -50) { // 留一点余量
                    if (line >= 0) {
                        lastDanmakuTimePerLine.remove(line);
                    }

                    if (enableSpatialGrid && spatialGrid != null) {
                        spatialGrid.remove(danmaku);
                    }
                    if (enableObjectPool && danmakuPool != null) {
                        danmakuPool.recycle(danmaku);
                    }
                    iterator.remove();
                }
            }

            if (enableSpatialGrid && spatialGrid != null) {
                spatialGrid.clear();
                for (DanmakuItem danmaku : activeDanmakus) {
                    spatialGrid.insert(danmaku);
                }
            }
        }
    }
    private float getAngleForDirection(int direction) {
        switch (direction) {
            case DanmakuItem.DIRECTION_LEFT_TO_RIGHT:
                return 0;
            case DanmakuItem.DIRECTION_TOP_TO_BOTTOM:
                return 90;
            case DanmakuItem.DIRECTION_RIGHT_TO_LEFT:
                return 180;
            case DanmakuItem.DIRECTION_BOTTOM_TO_TOP:
                return 270;
            case DanmakuItem.DIRECTION_DIAGONAL_TOPLEFT_TO_BOTTOMRIGHT:
                return 45;
            case DanmakuItem.DIRECTION_DIAGONAL_TOPRIGHT_TO_BOTTOMLEFT:
                return 135;
            case DanmakuItem.DIRECTION_DIAGONAL_BOTTOMLEFT_TO_TOPRIGHT:
                return 315;
            case DanmakuItem.DIRECTION_DIAGONAL_BOTTOMRIGHT_TO_TOPLEFT:
                return 225;
            default:
                return 0;
        }
    }
    private void drawDanmakus(Canvas canvas) {
        if (canvas == null) return;

        synchronized (activeDanmakus) {
            for (DanmakuItem danmaku : activeDanmakus) {
                drawSingleDanmaku(canvas, danmaku);
            }
        }
    }

    private void drawSingleDanmaku(Canvas canvas, DanmakuItem danmaku) {
        if (danmaku == null || danmaku.getText() == null) return;

        float x = danmaku.getX();
        float y = danmaku.getY();
        String text = danmaku.getText();

        Paint textPaint = new Paint(baseTextPaint);
        textPaint.setColor(danmaku.getColor());
        textPaint.setTextSize(currentTextSize);

        // ==================== 绘制背景（修正padding问题） ====================
        if (danmaku.isShowBackground()) {
            Paint bgPaint = new Paint(baseBackgroundPaint);

            // 获取弹幕自身的padding设置
            float horizontalPadding = danmaku.getBackgroundPadding();
            float radius = danmaku.getBackgroundRadius();

            // 如果没有设置，使用默认值
            if (horizontalPadding <= 0) {
                horizontalPadding = defaultBackgroundHorizontalPadding;
            }
            if (radius <= 0) {
                radius = defaultBackgroundRadius;
            }

            // 计算垂直padding（使用比例）
            float verticalPadding = calculateVerticalPadding(horizontalPadding);

            // 获取文字的实际边界
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textTop = y + fontMetrics.ascent;
            float textBottom = y + fontMetrics.descent;

            // 计算背景矩形
            float bgTop = textTop - verticalPadding;
            float bgBottom = textBottom + verticalPadding;
            float bgLeft = x - horizontalPadding;
            float bgRight = x + danmaku.getWidth() + horizontalPadding;

            RectF bgRect = new RectF(bgLeft, bgTop, bgRight, bgBottom);

            // 设置背景颜色或渐变
            if (danmaku.hasGradient()) {
                // 创建渐变着色器
                Shader gradientShader = createGradientShader(danmaku, bgRect);
                if (gradientShader != null) {
                    bgPaint.setShader(gradientShader);
                } else {
                    // 渐变创建失败，使用纯色
                    int bgColor = danmaku.getBackgroundColor() != 0 ?
                            danmaku.getBackgroundColor() : defaultBackgroundColor;
                    bgPaint.setColor(bgColor);
                    bgPaint.setShader(null);
                }
            } else {
                // 纯色背景
                int bgColor = danmaku.getBackgroundColor() != 0 ?
                        danmaku.getBackgroundColor() : defaultBackgroundColor;
                bgPaint.setColor(bgColor);
                bgPaint.setShader(null);
            }

            // 绘制背景
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
        }

        // 绘制主文字
        canvas.drawText(text, x, y, textPaint);
    }

    // 辅助方法：计算垂直padding
    private float calculateVerticalPadding(float horizontalPadding) {
        return horizontalPadding * defaultBackgroundVerticalPaddingRatio;
    }

    // 新增方法：设置垂直padding比例
    public void setBackgroundVerticalPaddingRatio(float ratio) {
        this.defaultBackgroundVerticalPaddingRatio = Math.max(0.1f, Math.min(ratio, 1.0f));
        Log.d("DanmakuView", "设置垂直padding比例: " + ratio + " (水平padding的" + (ratio * 100) + "%)");
    }

    // 新增方法：分别设置水平和垂直padding
    public void setBackgroundPadding(float horizontalPadding, float verticalPadding) {
        this.defaultBackgroundHorizontalPadding = horizontalPadding;
        // 计算比例
        if (horizontalPadding > 0) {
            this.defaultBackgroundVerticalPaddingRatio = verticalPadding / horizontalPadding;
        }
    }
    // 辅助方法：创建渐变着色器
    private Shader createGradientShader(DanmakuItem danmaku, RectF rect) {
        if (!danmaku.hasGradient()) return null;

        int[] colors = danmaku.getGradientColors();
        if (colors == null || colors.length < 2) return null;

        float centerX = rect.centerX();
        float centerY = rect.centerY();
        float width = rect.width();
        float height = rect.height();

        switch (danmaku.getGradientType()) {
            case DanmakuItem.GRADIENT_LINEAR:
                double angleRad = Math.toRadians(danmaku.getGradientAngle());
                float cos = (float) Math.cos(angleRad);
                float sin = (float) Math.sin(angleRad);

                float diagonal = (float) Math.sqrt(width * width + height * height);
                float startX = centerX - cos * diagonal / 2;
                float startY = centerY - sin * diagonal / 2;
                float endX = centerX + cos * diagonal / 2;
                float endY = centerY + sin * diagonal / 2;

                return new LinearGradient(startX, startY, endX, endY,
                        colors, null, Shader.TileMode.CLAMP);

            case DanmakuItem.GRADIENT_RADIAL:
                float radius = Math.max(width, height) / 2;
                return new RadialGradient(centerX, centerY, radius,
                        colors, null, Shader.TileMode.CLAMP);

            case DanmakuItem.GRADIENT_SWEEP:
                return new SweepGradient(centerX, centerY, colors, null);

            default:
                return null;
        }
    }
    private void calculateFPS() {
        frameCount++;
        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastFpsTime;

        if (elapsedTime >= 1_000_000_000L) {
            currentFps = frameCount * 1_000_000_000f / elapsedTime;
            frameCount = 0;
            lastFpsTime = currentTime;
        }
    }

    private int getOccupiedLines() {
        int count = 0;
        for (boolean occupied : lineOccupied) {
            if (occupied) count++;
        }
        return count;
    }

    // ==================== Surface生命周期回调 ====================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isRunning = true;
        isPaused = false;
        renderThread = new Thread(this);
        renderThread.start();
        restoreDanmakusState();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (enableSpatialGrid && spatialGrid != null) {
            spatialGrid.updateSize(width, height);
        }
        updateLineConfiguration();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        isPaused = true;

        if (renderThread != null) {
            try {
                renderThread.join(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            renderThread = null;
        }

        clearWaitingQueues();
        saveDanmakusState();
    }

    public void saveDanmakusState() {
        synchronized (activeDanmakus) {
            tempDanmakus.clear();
            for (DanmakuItem item : activeDanmakus) {
                tempDanmakus.add(new DanmakuItem(item));
            }
            Log.e("Danmaku", "保存临时弹幕: " + tempDanmakus.size());
        }
    }

    //todo 好像已经没有用了，用于状态保存的，现在改用让弹幕位置基于时间而非帧数
    public void restoreDanmakusState() {
        synchronized (activeDanmakus) {
            activeDanmakus.clear();
            for (DanmakuItem item : tempDanmakus) {
                activeDanmakus.add(item);
                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.insert(item);
                }
            }
            Log.e("Danmaku", "恢复临时弹幕: " + tempDanmakus.size());
            tempDanmakus.clear();
        }
    }

    // ==================== 控制方法 ====================

    private void clearWaitingQueues() {
        synchronized (waitingQueues) {
            for (Queue<DanmakuItem> queue : waitingQueues.values()) {
                while (!queue.isEmpty()) {
                    DanmakuItem danmaku = queue.poll();
                    if (enableObjectPool && danmakuPool != null) {
                        danmakuPool.recycle(danmaku);
                    }
                }
            }
            waitingQueues.clear();
        }
    }

    public float getGlobalSpeed() {
        return globalSpeed;
    }

    public void setTextSize(int textSize) {
        if (textSize <= 0 || Math.abs(textSize - currentTextSize) < 0.1f) return;
        final int size = dp2px(getContext(), textSize);

        uiHandler.post(() -> {
            isTextSizeChanging = true;

            try {
                currentTextSize = size;
                baseTextPaint.setTextSize(size);

                synchronized (activeDanmakus) {
                    for (DanmakuItem danmaku : activeDanmakus) {
                        danmaku.getPaint().setTextSize(size);
                        danmaku.setMeasured(false);
                        danmaku.measure(baseTextPaint);
                        danmaku.updateClickArea();
                    }
                }

                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.clear();
                    for (DanmakuItem danmaku : activeDanmakus) {
                        spatialGrid.insert(danmaku);
                    }
                }

                clearCanvas();

            } finally {
                isTextSizeChanging = false;
            }
        });
    }

    private void clearCanvas() {
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }


    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
        updateLineConfiguration();
        redistributeLines();
    }

    private void redistributeLines() {
        synchronized (activeDanmakus) {
            Arrays.fill(lineOccupied, false);
            for (DanmakuItem danmaku : activeDanmakus) {
                int line = allocateLineWithTimeCheck(danmaku.getLineNumber(), System.currentTimeMillis());
                danmaku.setLineNumber(line);
                danmaku.setY(calculateYPosition(line));
                if (line >= 0 && line < lineOccupied.length) {
                    lineOccupied[line] = true;
                }
            }
        }
    }

    public int getMaxLines() {
        return maxLines;
    }

    public void setLineHeight(int height) {
        this.lineHeight = Math.max(20, height);
        updateLineConfiguration();
        redistributeLines();
    }

    public void setClickThroughEnabled(boolean enabled) {
        this.clickThroughEnabled = enabled;
    }

    public void clearAll() {
        synchronized (activeDanmakus) {
            if (enableObjectPool && danmakuPool != null) {
                for (DanmakuItem danmaku : activeDanmakus) {
                    danmakuPool.recycle(danmaku);
                }
            }
            activeDanmakus.clear();

            if (enableSpatialGrid && spatialGrid != null) {
                spatialGrid.clear();
            }
        }

        clearWaitingQueues();
        lastDanmakuTimePerLine.clear();
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }

    public void setDanmakuClickListener(DanmakuClickListener listener) {
        this.danmakuClickListener = listener;
    }

    public void setDanmakuLongClickListener(DanmakuLongClickListener listener) {
        this.danmakuLongClickListener = listener;
    }

    public void setEnableClick(boolean enable) {
        this.clickEnabled = enable;
    }

    public int getActiveDanmakuCount() {
        synchronized (activeDanmakus) {
            return activeDanmakus.size();
        }
    }

    public String getPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        long runningTime = (currentTime - statsStartTime) / 1000;

        synchronized (this) {
            float avgUpdateTime = updateCount > 0 ? totalUpdateTime / (float) updateCount / 1_000_000f : 0;
            float avgDrawTime = drawCount > 0 ? totalDrawTime / (float) drawCount / 1_000_000f : 0;

            String stats = String.format(Locale.getDefault(),
                    "性能统计 (运行: %ds)\n" +
                            "当前FPS: %.1f\n" +
                            "活跃弹幕: %d\n" +
                            "平均更新: %.2fms\n" +
                            "平均绘制: %.2fms\n" +
                            "最近绘制: %.2fms\n" +
                            "点击检测: %d\n" +
                            "行数: %d/%d\n" +
                            "文字大小: %dpx\n" +
                            "等待队列: %d\n" +
                            "最大等待时间: %dms\n" +
                            "最大生存时间: %dms\n" +
                            "弹幕间距: %.1f%%\n" +
                            "时间间隔: %.0fms",
                    runningTime,
                    currentFps,
                    getActiveDanmakuCount(),
                    avgUpdateTime,
                    avgDrawTime,
                    drawTime / 1_000_000f,
                    clickDetections,
                    getOccupiedLines(),
                    lineOccupied.length,
                    currentTextSize,
                    getWaitingDanmakuCount(),
                    maxWaitTime,
                    maxDanmakuLife,
                    danmakuSpacingRatio * 100,
                    minIntervalBetweenDanmakus
            );

            if (enableObjectPool && danmakuPool != null) {
                stats += "\n\n" + danmakuPool.getStats();
            }

            return stats;
        }
    }

    private int getWaitingDanmakuCount() {
        int count = 0;
        synchronized (waitingQueues) {
            for (Queue<DanmakuItem> queue : waitingQueues.values()) {
                count += queue.size();
            }
        }
        return count;
    }


    public void resetStats() {
        synchronized (this) {
            totalUpdateTime = 0;
            totalDrawTime = 0;
            updateCount = 0;
            drawCount = 0;
            clickDetections = 0;
            statsStartTime = System.currentTimeMillis();
        }
    }

    public int dp2px(Context context, float dpValue) {
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public float getMaxSpeed() {
        return maxSpeed;
    }

    public void setAllowOverlap(boolean allowOverlap) {
        this.allowOverlap = allowOverlap;
    }

    public void setUniformSpeed(boolean uniformSpeed) {
        this.uniformSpeed = uniformSpeed;
    }

    public void setGlobalSpeed(float speed) {
        this.globalSpeed = Math.max(minSpeed, Math.min(maxSpeed, speed));

        synchronized (activeDanmakus) {
            for (DanmakuItem danmaku : activeDanmakus) {
                if (uniformSpeed) {
                    danmaku.setSpeed(globalSpeed);
                } else {
                    float baseSpeed = danmaku.getSpeed() / globalSpeed;
                    danmaku.setSpeed(baseSpeed * this.globalSpeed);
                }
            }
        }
    }

    // ==================== 新增配置方法 ====================

    /**
     * 设置最大等待时间
     */
    public void setMaxWaitTime(long maxWaitTime) {
        this.maxWaitTime = Math.max(0, maxWaitTime);
        Log.d("DanmakuConfig", "设置最大等待时间: " + maxWaitTime + "ms");
    }

    /**
     * 设置弹幕最大生存时间
     */
    public void setMaxDanmakuLife(long maxDanmakuLife) {
        this.maxDanmakuLife = Math.max(0, maxDanmakuLife);
        Log.d("DanmakuConfig", "设置弹幕最大生存时间: " + maxDanmakuLife + "ms");
    }

    /**
     * 获取等待队列大小
     */
    public int getWaitingQueueSize() {
        return getWaitingDanmakuCount();
    }

    /**
     * 强制清空并显示所有等待弹幕
     */
    public void flushWaitingQueue() {
        synchronized (waitingQueues) {
            int flushedCount = 0;
            for (Map.Entry<Integer, Queue<DanmakuItem>> entry : waitingQueues.entrySet()) {
                int line = entry.getKey();
                Queue<DanmakuItem> queue = entry.getValue();

                while (!queue.isEmpty()) {
                    DanmakuItem danmaku = queue.poll();
                    showDanmakuImmediately(danmaku, line);
                    flushedCount++;
                }
            }
            waitingQueues.clear();
//            Log.d("DanmakuQueue", "强制清空等待队列，显示了 " + flushedCount + " 个弹幕");
        }
    }

    /**
     * 设置弹幕间距（屏幕宽度比例）
     */
    public void setDanmakuSpacingRatio(float ratio) {
        this.danmakuSpacingRatio = Math.max(0.01f, Math.min(ratio, 0.2f));
//        Log.d("DanmakuConfig", "设置弹幕间距比例: " + danmakuSpacingRatio);
    }

    /**
     * 设置最小弹幕间距（像素）
     */
    public void setMinDanmakuSpacing(float minSpacing) {
        this.minDanmakuSpacing = Math.max(0, minSpacing);
        Log.d("DanmakuConfig", "设置最小弹幕间距: " + minDanmakuSpacing + "px");
    }

    /**
     * 设置最大弹幕间距（像素）
     */
    public void setMaxDanmakuSpacing(float maxSpacing) {
        this.maxDanmakuSpacing = Math.max(minDanmakuSpacing, maxSpacing);
        Log.d("DanmakuConfig", "设置最大弹幕间距: " + maxDanmakuSpacing + "px");
    }

    /**
     * 设置同行弹幕最小时间间隔
     */
    public void setMinIntervalBetweenDanmakus(float interval) {
        this.minIntervalBetweenDanmakus = Math.max(0, interval);
        Log.d("DanmakuConfig", "设置弹幕最小时间间隔: " + minIntervalBetweenDanmakus + "ms");
    }

    // 修改原有的setBackground相关方法
    public void setDefaultBackground(int color, float horizontalPadding, float radius) {
        this.defaultBackgroundColor = color;
        this.defaultBackgroundHorizontalPadding = horizontalPadding;
        this.defaultBackgroundRadius = radius;
        // 垂直padding使用默认比例计算
    }

    /**
     * 设置默认背景颜色
     */
    public void setDefaultBackgroundColor(int color) {
        this.defaultBackgroundColor = color;
    }

    /**
     * 设置默认背景内边距
     */
    public void setDefaultBackgroundPadding(float padding) {
        this.defaultBackgroundPadding = padding;
    }

    /**
     * 设置默认背景圆角半径
     */
    public void setDefaultBackgroundRadius(float radius) {
        this.defaultBackgroundRadius = radius;
    }
}