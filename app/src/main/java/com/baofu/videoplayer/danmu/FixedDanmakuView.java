package com.baofu.videoplayer.danmu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;


// FixedDanmakuView.java - 带有详细注释的弹幕视图
public class FixedDanmakuView extends SurfaceView
        implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder surfaceHolder;          // SurfaceView的持有者，用于管理Canvas
    private Thread renderThread;                  // 渲染线程，负责动画循环
    private volatile boolean isRunning = false;   // 线程运行标志，volatile确保多线程可见性
    private volatile boolean isPaused = false;    // 暂停标志，控制渲染循环是否暂停

    // 弹幕管理
    private final List<DanmakuItem> activeDanmakus = new ArrayList<>();  // 当前活跃的弹幕列表
    private DanmakuPool danmakuPool;              // 弹幕对象池，用于复用弹幕对象减少GC
    private DanmakuSpatialGrid spatialGrid;       // 空间网格，优化点击检测性能
    private Random random = new Random();         // 随机数生成器，用于生成随机属性

    // 画笔
    private Paint baseTextPaint;                  // 基础文字画笔模板，所有弹幕文字都基于此
    private Paint baseBackgroundPaint;            // 基础背景画笔模板，用于弹幕背景

    // 触摸处理
    private float lastTouchX, lastTouchY;         // 上一次触摸的X/Y坐标，用于判断点击和滑动
    private boolean isTouching = false;           // 当前是否正在触摸
    private long touchDownTime;                   // 触摸按下的时间戳，用于计算点击时长
    private DanmakuItem currentTouchDanmaku;      // 当前触摸到的弹幕，可能为null
    private Handler uiHandler;                    // UI线程的Handler，用于在UI线程执行任务

    // 点击事件监听器
    private DanmakuClickListener danmakuClickListener;        // 弹幕点击监听器
    private DanmakuLongClickListener danmakuLongClickListener; // 弹幕长按监听器

    // 配置参数
    private boolean clickEnabled = true;          // 是否启用点击功能
    private boolean clickThroughEnabled = false;  // 是否启用穿透点击（弹幕不阻挡底层视图）
    private long clickThreshold = 200;            // 点击时间阈值（毫秒），超过此值不算点击
    private long longPressThreshold = 500;        // 长按时间阈值（毫秒），超过此值触发长按
    private float touchSlop = 10;                 // 触摸容差（像素），移动超过此值取消点击
    private int maxClickDetection = 50;           // 最大点击检测数量，限制每次检测的弹幕数

    // 弹幕控制
    private float globalSpeed = 1.0f;             // 全局速度倍率，影响所有弹幕的移动速度
    private float minSpeed = 0.6f;                // 最小速度，弹幕生成时的最小速度
    private float maxSpeed = 8.0f;                // 最大速度，弹幕生成时的最大速度
    private boolean showBackground = false;       // 是否显示弹幕背景
    private int backgroundColor = Color.argb(100, 0, 0, 0); // 弹幕背景颜色（带透明度）
    private float backgroundPadding = dp2px(getContext(),5);         // 弹幕背景内边距（dp）
    private float backgroundRadius = dp2px(getContext(),8);         // 弹幕背景圆角半径（dp）
    private boolean allowOverlap = false; // 控制是否允许弹幕重叠
    private boolean uniformSpeed = true;  // 控制是否使用匀速（false为变速）
    // 行数控制
    private int maxLines = 15;                    // 最大显示行数，-1表示无限制
    private float lineHeight = dp2px(getContext(),40);               // 每行的高度（dp）
    private boolean[] lineOccupied;               // 行占用标记数组，记录每行是否被占用
    private int calculatedMaxLines;               // 根据屏幕高度计算的最大行数

    // 性能优化开关
    private int maxDanmakuCount = 100;            // 最大弹幕数量限制，超过时移除最旧的弹幕
    private boolean enableSpatialGrid = true;     // 是否启用空间网格优化
    private boolean enableObjectPool = true;      // 是否启用对象池优化

    // 性能监控
    private long lastFpsTime;                     // 上一次计算FPS的时间（纳秒）
    private int frameCount;                       // 帧计数器，用于计算FPS
    private float currentFps;                     // 当前FPS（帧率）
    private long drawTime;                        // 上一次绘制耗时（纳秒）
    private long updateTime;                      // 上一次更新耗时（纳秒）
    private int clickDetections = 0;              // 点击检测次数统计

    // 文字大小调整控制
    private volatile boolean isTextSizeChanging = false; // 文字大小是否正在调整中
    private int currentTextSize = dp2px(getContext(),16);          // 当前文字大小（dp）

    // 性能统计
    private long totalUpdateTime = 0;             // 总更新耗时（纳秒）
    private long totalDrawTime = 0;               // 总绘制耗时（纳秒）
    private int updateCount = 0;                  // 更新次数统计
    private int drawCount = 0;                    // 绘制次数统计
    private long statsStartTime = System.currentTimeMillis(); // 统计开始时间

    // 构造方法
    public FixedDanmakuView(Context context) {
        super(context);
        init(); // 初始化
    }

    public FixedDanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(); // 初始化
    }

    public FixedDanmakuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(); // 初始化
    }

    /**
     * 初始化方法
     * 1. 设置SurfaceHolder回调
     * 2. 创建UI线程Handler
     * 3. 初始化画笔
     * 4. 初始化对象池（如果启用）
     * 5. 设置SurfaceView透明
     * 6. 启用触摸
     */
    private void init() {
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this); // 注册Surface生命周期回调

        uiHandler = new Handler(Looper.getMainLooper()); // 在主线程创建Handler
        initPaints(); // 初始化画笔

        if (enableObjectPool) {
            danmakuPool = new DanmakuPool(); // 初始化对象池
        }

        setZOrderOnTop(true); // 设置视图位于顶层
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT); // 设置透明背景

        setFocusable(true); // 允许获取焦点
        setClickable(true); // 允许点击

        lastFpsTime = System.nanoTime(); // 初始化FPS计算时间
    }

    /**
     * 初始化画笔
     * 1. 创建基础文字画笔模板
     * 2. 创建基础背景画笔模板
     * 3. 创建调试信息画笔
     */
    private void initPaints() {
        // 基础文字画笔
        baseTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 抗锯齿
        baseTextPaint.setTextSize(currentTextSize); // 设置文字大小
        baseTextPaint.setStyle(Paint.Style.FILL); // 填充模式

        // 基础背景画笔
        baseBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 抗锯齿
        baseBackgroundPaint.setStyle(Paint.Style.FILL); // 填充模式

    }

    /**
     * 视图尺寸变化回调
     * 1. 更新空间网格尺寸
     * 2. 更新行配置
     * @param w 新的宽度
     * @param h 新的高度
     * @param oldw 旧的宽度
     * @param oldh 旧的高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (enableSpatialGrid) {
            if (spatialGrid == null) {
                spatialGrid = new DanmakuSpatialGrid(w, h); // 初次创建空间网格
            } else {
                spatialGrid.updateSize(w, h); // 更新现有空间网格尺寸
            }
        }

        updateLineConfiguration(); // 更新行配置
    }

    /**
     * 更新行配置
     * 根据屏幕高度和行高计算实际显示行数
     */
    private void updateLineConfiguration() {
        calculatedMaxLines = Math.max(1, (int) (getHeight() / lineHeight)); // 计算最大行数
        int actualLines = maxLines == -1 ? calculatedMaxLines : Math.min(maxLines, calculatedMaxLines);
        lineOccupied = new boolean[actualLines]; // 初始化行占用数组
    }

    // ==================== 弹幕添加方法（重载） ====================

    /**
     * 添加弹幕（随机颜色）
     * @param text 弹幕文字
     */
    public void addDanmaku(String text) {
        addDanmaku(text, Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
    }

    /**
     * 添加弹幕（指定颜色）
     * @param text 弹幕文字
     * @param color 弹幕颜色
     */
    public void addDanmaku(String text, int color) {
        addDanmaku(text, color, true);
    }

    /**
     * 添加弹幕（指定颜色和可点击性）
     * @param text 弹幕文字
     * @param color 弹幕颜色
     * @param clickable 是否可点击
     */
    public void addDanmaku(String text, int color, boolean clickable) {
        addDanmaku(text, color, clickable, -1); // -1表示自动分配行
    }


    /**
     * 移除最旧的弹幕
     * 当弹幕数量达到上限时调用
     */
    private void removeOldestDanmaku() {
        synchronized (activeDanmakus) {
            if (!activeDanmakus.isEmpty()) {
                DanmakuItem oldest = activeDanmakus.get(0); // 获取最旧的弹幕

                // 从空间网格移除
                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.remove(oldest);
                }

                // 回收到对象池
                if (enableObjectPool && danmakuPool != null) {
                    danmakuPool.recycle(oldest);
                }

                // 从活动列表移除
                activeDanmakus.remove(0);

                // 更新行占用状态
                if (oldest.getLineNumber() >= 0 && oldest.getLineNumber() < lineOccupied.length) {
                    lineOccupied[oldest.getLineNumber()] = false;
                }
            }
        }
    }

    /**
     * 分配行号
     * 1. 如果指定了有效行号，直接返回
     * 2. 如果行数无限制，随机分配
     * 3. 查找可用行，有则随机分配
     * 4. 没有可用行，随机覆盖一行
     * @param requestedLine 请求的行号
     * @return 实际分配的行号
     */
    private int allocateLine(int requestedLine) {
        // 如果请求的行号有效，直接使用
        if (requestedLine >= 0 && requestedLine < lineOccupied.length) {
            return requestedLine;
        }

        // 行数无限制，随机分配
        if (maxLines == -1) {
            return random.nextInt(calculatedMaxLines);
        }

        // 查找可用行
        List<Integer> availableLines = new ArrayList<>();
        for (int i = 0; i < lineOccupied.length; i++) {
            if (!lineOccupied[i]) {
                availableLines.add(i);
            }
        }

        // 有可用行，随机选择一个
        if (!availableLines.isEmpty()) {
            return availableLines.get(random.nextInt(availableLines.size()));
        }

        // 没有可用行，随机覆盖一行
        return random.nextInt(lineOccupied.length);
    }

    /**
     * 计算Y坐标位置
     * 根据行号计算弹幕的垂直位置
     * @param line 行号
     * @return Y坐标
     */
    private float calculateYPosition(int line) {
        // 行号无效，随机位置
        if (line < 0 || line >= lineOccupied.length) {
            return random.nextInt(Math.max(1, getHeight() - 100)) + 50;
        }

        // 根据行号计算位置：行号×行高 + 行高一半 + 上边距
        float y = line * lineHeight + lineHeight / 2 + 20;
        return Math.min(y, getHeight() - 20); // 确保不超过屏幕
    }

    // ==================== 触摸事件处理 ====================

    /**
     * 触摸事件处理
     * 处理点击、长按、滑动等触摸事件
     * @param event 触摸事件
     * @return 是否消费事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!clickEnabled || isPaused) {
            return false; // 点击功能禁用或暂停中，不处理触摸
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                performClick(); // 触发点击回调
                return handleTouchDown(x, y); // 处理按下

            case MotionEvent.ACTION_MOVE:
                handleTouchMove(x, y); // 处理移动
                return true;

            case MotionEvent.ACTION_UP:
                return handleTouchUp(x, y); // 处理抬起

            case MotionEvent.ACTION_CANCEL:
                handleTouchCancel(); // 处理取消
                return true;
        }

        return false;
    }

    /**
     * 触发点击事件
     * 用于系统点击事件处理
     * @return 是否成功触发
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /**
     * 处理触摸按下事件
     * 1. 记录触摸位置和时间
     * 2. 查找触摸点下的弹幕
     * 3. 检查是否需要穿透
     * 4. 开始长按检测
     * @param x X坐标
     * @param y Y坐标
     * @return 是否消费事件（触摸到弹幕则消费）
     */
    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;
        isTouching = true;
        touchDownTime = System.currentTimeMillis();

        // 查找触摸点下的弹幕
        currentTouchDanmaku = findDanmakuAtPoint(x, y);

        // 检查是否需要穿透
        if (shouldClickThrough(currentTouchDanmaku)) {
            currentTouchDanmaku = null;
            isTouching = false;
            return false; // 穿透，不消费事件
        }

        // 开始长按检测
        if (currentTouchDanmaku != null) {
            uiHandler.postDelayed(longPressRunnable, longPressThreshold);
        }

        return currentTouchDanmaku != null; // 触摸到弹幕则消费事件
    }

    /**
     * 查找触摸点下的弹幕
     * 使用空间网格优化查找性能
     * @param x X坐标
     * @param y Y坐标
     * @return 触摸点下的弹幕，可能为null
     */
    private DanmakuItem findDanmakuAtPoint(float x, float y) {
        clickDetections++; // 统计点击检测次数

        List<DanmakuItem> candidates;

        // 使用空间网格优化查找
        if (enableSpatialGrid && spatialGrid != null) {
            candidates = spatialGrid.query(x, y); // 查询网格单元内的弹幕
        } else {
            // 传统遍历方式（从后往前，后添加的在上层）
            candidates = new ArrayList<>();
            synchronized (activeDanmakus) {
                int count = Math.min(maxClickDetection, activeDanmakus.size());
                for (int i = activeDanmakus.size() - 1; i >= Math.max(0, activeDanmakus.size() - count); i--) {
                    DanmakuItem item = activeDanmakus.get(i);
                    if (item.containsPoint(x, y)) {
                        candidates.add(item);
                        break; // 找到最上层的一个就返回
                    }
                }
            }
        }

        // 检查候选弹幕的可点击性
        for (DanmakuItem item : candidates) {
            if (item.isClickable() && !shouldClickThrough(item)) {
                return item;
            }
        }

        return null;
    }

    /**
     * 判断是否应该穿透点击
     * 1. 如果穿透功能禁用，不穿透
     * 2. 弹幕不可点击，穿透
     * 3. 弹幕透明度低于阈值，穿透
     * @param danmaku 弹幕对象
     * @return 是否应该穿透
     */
    private boolean shouldClickThrough(DanmakuItem danmaku) {
        if (!clickThroughEnabled || danmaku == null) {
            return false; // 穿透功能禁用或弹幕为空，不穿透
        }

        if (!danmaku.isClickable()) {
            return true; // 弹幕不可点击，穿透
        }

        // 检查透明度（alpha值小于51，约20%透明度）
        int alpha = Color.alpha(danmaku.getColor());
        return alpha < 51;
    }

    /**
     * 处理触摸移动事件
     * 如果移动距离超过阈值，取消当前触摸
     * @param x X坐标
     * @param y Y坐标
     */
    private void handleTouchMove(float x, float y) {
        // 移动距离超过阈值，取消点击
        if (isTouching &&
                (Math.abs(x - lastTouchX) > touchSlop ||
                        Math.abs(y - lastTouchY) > touchSlop)) {
            handleTouchCancel();
        }
    }

    /**
     * 处理触摸抬起事件
     * 1. 检查是否为点击（时间短、移动小）
     * 2. 触发点击事件
     * @param x X坐标
     * @param y Y坐标
     * @return 是否消费事件
     */
    private boolean handleTouchUp(float x, float y) {
        if (!isTouching) return false;

        // 取消长按检测
        uiHandler.removeCallbacks(longPressRunnable);

        long touchDuration = System.currentTimeMillis() - touchDownTime;

        // 检查是否为点击（时间短且移动距离小）
        if (touchDuration < clickThreshold &&
                Math.abs(x - lastTouchX) < touchSlop &&
                Math.abs(y - lastTouchY) < touchSlop) {

            return handleClick(x, y); // 处理点击
        }

        handleTouchCancel(); // 不是点击，取消触摸
        return false;
    }

    /**
     * 处理点击事件
     * 1. 查找点击点下的弹幕
     * 2. 检查是否应该穿透
     * 3. 触发点击回调
     * 4. 提供触觉反馈
     * 5. 显示点击反馈
     * @param x X坐标
     * @param y Y坐标
     * @return 是否消费事件
     */
    private boolean handleClick(float x, float y) {
        DanmakuItem clickedDanmaku = findDanmakuAtPoint(x, y);
        if (clickedDanmaku != null && !shouldClickThrough(clickedDanmaku)) {
            // 触发弹幕点击回调
            if (danmakuClickListener != null) {
                danmakuClickListener.onDanmakuClick(clickedDanmaku);
            }

            // 提供触觉反馈
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

            // 显示点击反馈
            showClickFeedback(clickedDanmaku);
            return true; // 消费事件
        }

        return false; // 没有点击到弹幕或应该穿透
    }

    /**
     * 显示点击反馈
     * 1. 临时改变弹幕样式
     * 2. 一段时间后恢复
     * @param danmaku 被点击的弹幕
     */
    private void showClickFeedback(DanmakuItem danmaku) {
        Paint originalPaint = danmaku.getPaint();
        int originalColor = danmaku.getColor();

        // 创建高亮效果
        Paint highlightPaint = new Paint(originalPaint);
        highlightPaint.setColor(Color.WHITE); // 变成白色
        highlightPaint.setUnderlineText(true); // 添加下划线
        highlightPaint.setShadowLayer(5, 0, 0, Color.YELLOW); // 添加黄色阴影

        danmaku.setPaint(highlightPaint); // 临时替换画笔
        invalidate(); // 重绘

        // 0.5秒后恢复原状
        uiHandler.postDelayed(() -> {
            danmaku.setPaint(originalPaint);
            danmaku.setColor(originalColor);
            invalidate();
        }, 500);
    }

    /**
     * 处理触摸取消事件
     * 清理触摸状态
     */
    private void handleTouchCancel() {
        uiHandler.removeCallbacks(longPressRunnable);
        isTouching = false;
        currentTouchDanmaku = null;
    }

    // 长按检测任务
    private Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            // 检查是否满足长按条件
            if (isTouching && currentTouchDanmaku != null && danmakuLongClickListener != null) {
                danmakuLongClickListener.onDanmakuLongClick(currentTouchDanmaku);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); // 长按震动
                handleTouchCancel(); // 清理触摸状态
            }
        }
    };

    // ==================== 渲染循环 ====================

    /**
     * 渲染线程主循环
     * 1. 更新弹幕位置
     * 2. 绘制弹幕
     * 3. 计算FPS
     * 4. 控制帧率（60FPS）
     */
    @Override
    public void run() {
        long lastUpdateTime = System.nanoTime(); // 上次更新时间

        while (isRunning) {
            // 暂停或文字大小调整中，等待
            if (isPaused || isTextSizeChanging) {
                try {
                    Thread.sleep(16); // 大约60FPS的间隔
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            long startTime = System.nanoTime();
            Canvas canvas = null;

            try {
                canvas = surfaceHolder.lockCanvas(); // 锁定Canvas开始绘制
                if (canvas != null) {
                    // 关键：完全清除画布（解决重影问题）
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                    // 计算时间差
                    long currentTime = System.nanoTime();
                    float deltaTime = (currentTime - lastUpdateTime) / 1_000_000f; // 转换为毫秒
                    lastUpdateTime = currentTime;

                    // 更新弹幕位置
                    long updateStart = System.nanoTime();
                    updateDanmakus(deltaTime);
                    long updateEnd = System.nanoTime();

                    // 绘制弹幕
                    long drawStart = System.nanoTime();
                    drawDanmakus(canvas);
                    long drawEnd = System.nanoTime();

                    // 更新性能统计
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

                    calculateFPS(); // 计算FPS
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas); // 解锁Canvas，显示内容
                }
            }

            // 控制帧率（约60FPS）
            try {
                long frameTime = System.nanoTime() - startTime;
                long sleepTime = Math.max(0, (16_666_666 - frameTime) / 1_000_000); // 16.67ms = 60FPS
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                break;
            }
        }
    }


    /**
     * 绘制所有弹幕
     * 每个弹幕使用独立的画笔实例，避免状态污染
     * @param canvas 画布
     */
    private void drawDanmakus(Canvas canvas) {
        if (canvas == null) return;

        synchronized (activeDanmakus) {
            for (DanmakuItem danmaku : activeDanmakus) {
                drawSingleDanmaku(canvas, danmaku);
            }
        }

    }

    /**
     * 绘制单个弹幕
     * 重要：每次绘制都创建新的画笔实例，避免状态污染
     * @param canvas 画布
     * @param danmaku 弹幕对象
     */
    private void drawSingleDanmaku(Canvas canvas, DanmakuItem danmaku) {
        if (danmaku == null || danmaku.getText() == null) return;

        float x = danmaku.getX();
        float y = danmaku.getY();
        String text = danmaku.getText();

        // 重要：每次绘制都创建新的画笔实例
        Paint textPaint = new Paint(baseTextPaint);
        textPaint.setColor(danmaku.getColor());
        textPaint.setTextSize(currentTextSize); // 使用当前文字大小

        // 绘制背景（如果启用）
        if (showBackground) {
            Paint bgPaint = new Paint(baseBackgroundPaint);
            bgPaint.setColor(backgroundColor);

            float textHeight = currentTextSize;
            float textWidth = danmaku.getWidth();
            float bgLeft = x - backgroundPadding;
            float bgTop = y - textHeight ;
            float bgRight = x + textWidth + backgroundPadding;
            float bgBottom = y + backgroundPadding;

            // 绘制圆角矩形背景
            canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom,
                    backgroundRadius, backgroundRadius, bgPaint);
        }

        // 绘制文字阴影
        Paint shadowPaint = new Paint(textPaint);
        shadowPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        canvas.drawText(text, x + 1, y + 1, shadowPaint);

        // 绘制文字
        canvas.drawText(text, x, y, textPaint);
    }

    /**
     * 计算FPS（帧率）
     * 每秒计算一次
     */
    private void calculateFPS() {
        frameCount++;
        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastFpsTime;

        // 每秒计算一次FPS
        if (elapsedTime >= 1_000_000_000L) { // 1秒 = 1,000,000,000纳秒
            currentFps = frameCount * 1_000_000_000f / elapsedTime;
            frameCount = 0;
            lastFpsTime = currentTime;
        }
    }

    /**
     * 获取占用的行数
     * @return 当前占用的行数
     */
    private int getOccupiedLines() {
        int count = 0;
        for (boolean occupied : lineOccupied) {
            if (occupied) count++;
        }
        return count;
    }

    // ==================== Surface生命周期回调 ====================

    /**
     * Surface创建回调
     * 启动渲染线程
     * @param holder SurfaceHolder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isRunning = true;
        isPaused = false;
        renderThread = new Thread(this); // 创建渲染线程
        renderThread.start(); // 启动线程
        // 恢复弹幕数据（如果有）
        restoreDanmakusState();
    }

    /**
     * Surface尺寸变化回调
     * 更新空间网格和行配置
     * @param holder SurfaceHolder
     * @param format 像素格式
     * @param width 新宽度
     * @param height 新高度
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (enableSpatialGrid && spatialGrid != null) {
            spatialGrid.updateSize(width, height); // 更新空间网格尺寸
        }
        updateLineConfiguration(); // 更新行配置
    }

    /**
     * Surface销毁回调
     * 停止渲染线程，清理资源
     * @param holder SurfaceHolder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        isPaused = true;

        // 等待渲染线程结束
        if (renderThread != null) {
            try {
                renderThread.join(200); // 最多等待200ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            renderThread = null;
        }
        // 注释掉清空弹幕的代码，保留数据
//        clearAll(); // 清理所有弹幕
        saveDanmakusState();
    }

    // 新增成员变量：临时存储后台时的弹幕
    private List<DanmakuItem> tempDanmakus = new ArrayList<>();

    // 在surfaceDestroyed前保存弹幕
    public void saveDanmakusState() {
        synchronized (activeDanmakus) {
            // 深拷贝当前活跃弹幕（避免引用问题）
            tempDanmakus.clear();
            for (DanmakuItem item : activeDanmakus) {
                tempDanmakus.add(new DanmakuItem(item)); // 需要DanmakuItem实现拷贝构造
            }
            Log.e("asdff","save tempDanmakus:"+tempDanmakus.size());
        }
    }

    // 恢复弹幕数据
    public void restoreDanmakusState() {
        synchronized (activeDanmakus) {
            activeDanmakus.clear();
            // 将临时存储的弹幕重新添加到活跃列表
            for (DanmakuItem item : tempDanmakus) {
                activeDanmakus.add(item);
                // 重新插入空间网格
                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.insert(item);
                }
            }
            Log.e("asdff","restore tempDanmakus:"+tempDanmakus.size());
            tempDanmakus.clear();
        }
    }

    // ==================== 控制方法 ====================



    /**
     * 获取当前全局速度
     * @return 全局速度倍率
     */
    public float getGlobalSpeed() {
        return globalSpeed;
    }

    /**
     * 设置文字大小
     * 重要：在UI线程执行，避免并发问题
     * 解决重影问题的关键方法
     * @param textSize 文字大小（dp）
     */
    public void setTextSize( int textSize) {
        if (textSize <= 0 || Math.abs(textSize - currentTextSize) < 0.1f) return;
        final int size= dp2px(getContext(),textSize);
        // 在UI线程执行，确保线程安全
        uiHandler.post(() -> {
            isTextSizeChanging = true; // 标记正在调整文字大小

            try {
                // 更新当前文字大小
                currentTextSize = size;
                baseTextPaint.setTextSize(size); // 更新基础画笔

                // 重新测量所有弹幕
                synchronized (activeDanmakus) {
                    for (DanmakuItem danmaku : activeDanmakus) {
                        danmaku.getPaint().setTextSize(size); // 更新弹幕画笔
                        danmaku.setMeasured(false); // 标记需要重新测量
                        danmaku.measure(baseTextPaint); // 重新测量
                        danmaku.updateClickArea(); // 更新点击区域
                    }
                }

                // 更新空间网格
                if (enableSpatialGrid && spatialGrid != null) {
                    spatialGrid.clear();
                    for (DanmakuItem danmaku : activeDanmakus) {
                        spatialGrid.insert(danmaku);
                    }
                }

                // 清除画布（解决重影问题）
                clearCanvas();

            } finally {
                isTextSizeChanging = false; // 标记调整完成
            }
        });
    }

    /**
     * 清除画布
     * 用于解决重影问题
     */
    private void clearCanvas() {
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                // 关键：完全清除画布
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

    /**
     * 设置是否显示弹幕背景
     * @param show true显示背景，false不显示
     */
    public void setShowBackground(boolean show) {
        this.showBackground = show;
    }

    /**
     * 设置最大行数
     * @param maxLines 最大行数（-1表示无限制）
     */
    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
        updateLineConfiguration(); // 更新行配置
        redistributeLines(); // 重新分配行
    }

    /**
     * 重新分配所有弹幕的行
     * 在行数变化时调用
     */
    private void redistributeLines() {
        synchronized (activeDanmakus) {
            Arrays.fill(lineOccupied, false); // 清空占用标记
            for (DanmakuItem danmaku : activeDanmakus) {
                int line = allocateLine(danmaku.getLineNumber()); // 重新分配行
                danmaku.setLineNumber(line);
                danmaku.setY(calculateYPosition(line)); // 更新Y坐标
                if (line >= 0 && line < lineOccupied.length) {
                    lineOccupied[line] = true; // 标记占用
                }
            }
        }
    }

    /**
     * 获取最大行数设置
     * @return 最大行数
     */
    public int getMaxLines() {
        return maxLines;
    }

    /**
     * 设置行高
     * @param height 行高（dp）
     */
    public void setLineHeight(int height) {
        this.lineHeight = Math.max(20, height); // 最小20像素
        updateLineConfiguration(); // 更新行配置
        redistributeLines(); // 重新分配行
    }

    /**
     * 设置是否启用穿透点击
     * @param enabled true启用穿透，false禁用
     */
    public void setClickThroughEnabled(boolean enabled) {
        this.clickThroughEnabled = enabled;
    }

    /**
     * 清空所有弹幕
     * 回收所有弹幕到对象池
     */
    public void clearAll() {
        synchronized (activeDanmakus) {
            // 回收所有弹幕到对象池
            if (enableObjectPool && danmakuPool != null) {
                for (DanmakuItem danmaku : activeDanmakus) {
                    danmakuPool.recycle(danmaku);
                }
            }
            activeDanmakus.clear(); // 清空活动列表

            // 清空空间网格
            if (enableSpatialGrid && spatialGrid != null) {
                spatialGrid.clear();
            }
        }
    }

    /**
     * 暂停渲染
     * 暂停动画循环
     */
    public void pause() {
        isPaused = true;
    }

    /**
     * 恢复渲染
     * 恢复动画循环
     */
    public void resume() {
        isPaused = false;
    }

    /**
     * 设置弹幕点击监听器
     * @param listener 点击监听器
     */
    public void setDanmakuClickListener(DanmakuClickListener listener) {
        this.danmakuClickListener = listener;
    }

    /**
     * 设置弹幕长按监听器
     * @param listener 长按监听器
     */
    public void setDanmakuLongClickListener(DanmakuLongClickListener listener) {
        this.danmakuLongClickListener = listener;
    }

    /**
     * 设置是否启用点击功能
     * @param enable true启用，false禁用
     */
    public void setEnableClick(boolean enable) {
        this.clickEnabled = enable;
    }

    /**
     * 获取当前活跃弹幕数量
     * @return 活跃弹幕数
     */
    public int getActiveDanmakuCount() {
        synchronized (activeDanmakus) {
            return activeDanmakus.size();
        }
    }

    /**
     * 获取性能统计信息
     * 包括FPS、弹幕数、绘制时间等
     * @return 格式化后的性能统计字符串
     */
    public String getPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        long runningTime = (currentTime - statsStartTime) / 1000; // 运行时间（秒）

        synchronized (this) {
            // 计算平均时间
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
                            "文字大小: %d",
                    runningTime,
                    currentFps,
                    getActiveDanmakuCount(),
                    avgUpdateTime,
                    avgDrawTime,
                    drawTime / 1_000_000f,
                    clickDetections,
                    getOccupiedLines(),
                    lineOccupied.length,
                    currentTextSize
            );

            // 添加对象池统计
            if (enableObjectPool && danmakuPool != null) {
                stats += "\n\n" + danmakuPool.getStats();
            }

            return stats;
        }
    }

    /**
     * 重置性能统计
     * 清空所有统计计数器
     */
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

    /**
     * dp 转 px
     * @param context 上下文（Activity/Application）
     * @param dpValue 待转换的 dp 值
     * @return 转换后的 px 值
     */
    public  int dp2px(Context context, float dpValue) {
        if(context==null){
            return 0;
        }
        // 获取屏幕密度比例（如 1.5 (120dpi)、2.0 (240dpi)、3.0 (360dpi) 等）
        final float scale = context.getResources().getDisplayMetrics().density;
        // 四舍五入避免精度丢失
        return (int) (dpValue * scale + 0.5f);
    }

    public float getMaxSpeed(){
        return maxSpeed;
    }



    // 添加setter方法用于控制开关
    public void setAllowOverlap(boolean allowOverlap) {
        this.allowOverlap = allowOverlap;
    }

    public void setUniformSpeed(boolean uniformSpeed) {
        this.uniformSpeed = uniformSpeed;
    }

    // 修改addDanmaku方法中的速度生成逻辑
    public void addDanmaku(String text, int color, boolean clickable, int line) {
        if (text == null || text.isEmpty()) return;

        if (activeDanmakus.size() >= maxDanmakuCount) {
            removeOldestDanmaku();
        }

        // 生成弹幕速度（根据匀速开关调整）
        float speed;
        if (uniformSpeed) {
            // 匀速模式 - 固定速度
            speed = globalSpeed;
        } else {
            // 变速模式 - 随机速度
            speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed);
            speed *= globalSpeed;
        }

        // 从对象池获取或创建弹幕（保持不变）
        DanmakuItem danmaku;
        if (enableObjectPool && danmakuPool != null) {
            danmaku = danmakuPool.obtain(text, color, speed);
        } else {
            danmaku = new DanmakuItem(text, color, speed);
        }

        // 设置弹幕属性（保持不变）
        danmaku.setClickable(clickable);
        danmaku.setUserId(random.nextInt(10000));
        danmaku.setUserName("用户" + random.nextInt(1000));

        Paint itemPaint = danmaku.getPaint();
        itemPaint.setTextSize(currentTextSize);
        itemPaint.setColor(color);

        // 分配行号（保持不变）
        int actualLine = allocateLine(line);
        danmaku.setLineNumber(actualLine);

        // 计算Y坐标位置（保持不变）
        float y = calculateYPosition(actualLine);
        danmaku.setY(y);

        // 测量弹幕（保持不变）
        danmaku.measure(baseTextPaint);
        danmaku.updateClickArea();

        // 计算X坐标（新增逻辑：处理不重叠情况）
        float x = calculateXPosition(danmaku, actualLine);
        danmaku.setX(x);

        // 添加到活动列表（保持不变）
        synchronized (activeDanmakus) {
            activeDanmakus.add(danmaku);
        }

        // 添加到空间网格（保持不变）
        if (enableSpatialGrid && spatialGrid != null) {
            spatialGrid.insert(danmaku);
        }

        // 标记行占用（保持不变）
        if (actualLine >= 0 && actualLine < lineOccupied.length) {
            lineOccupied[actualLine] = true;
        }
    }

    // 新增方法：计算X坐标位置（处理不重叠逻辑）
    private float calculateXPosition(DanmakuItem newDanmaku, int line) {
        // 如果允许重叠或没有同行动画，直接从右侧进入
        if (allowOverlap) {
            return getWidth();
        }

        // 查找同行动画中最右侧的弹幕
        float maxRight = getWidth(); // 默认从右侧进入
        synchronized (activeDanmakus) {
            for (DanmakuItem item : activeDanmakus) {
                // 只考虑同一行的弹幕
                if (item.getLineNumber() == line) {
                    // 计算当前弹幕的右边缘位置
                    float itemRight = item.getX() + item.getWidth();
                    if (itemRight > maxRight) {
                        maxRight = itemRight;
                    }
                }
            }
        }

        // 在最右侧弹幕的右边添加新弹幕，留出一定间距
        return maxRight + getWidth() * 0.05f; // 5%屏幕宽度的间距
    }

    /**
     * 更新所有弹幕位置
     * 1. 更新每个弹幕的X坐标
     * 2. 更新点击区域
     * 3. 更新行占用状态
     * 4. 移除离开屏幕的弹幕
     * 5. 更新空间网格
     * @param deltaTime 时间差（毫秒）
     */
    // 修改updateDanmakus方法，确保行占用状态正确更新
    private void updateDanmakus(float deltaTime) {
        synchronized (activeDanmakus) {
            Iterator<DanmakuItem> iterator = activeDanmakus.iterator();

            // 重置行占用状态
            Arrays.fill(lineOccupied, false);

            while (iterator.hasNext()) {
                DanmakuItem danmaku = iterator.next();

                // 更新位置
                danmaku.setX(danmaku.getX() - danmaku.getSpeed() * (deltaTime / 16.0f));
                danmaku.updateClickArea();

                // 更新行占用状态
                int line = danmaku.getLineNumber();
                if (line >= 0 && line < lineOccupied.length) {
                    lineOccupied[line] = true;
                }

                // 检查是否离开屏幕
                if (danmaku.getX() + danmaku.getWidth() < 0) {
                    if (enableSpatialGrid && spatialGrid != null) {
                        spatialGrid.remove(danmaku);
                    }
                    if (enableObjectPool && danmakuPool != null) {
                        danmakuPool.recycle(danmaku);
                    }
                    iterator.remove();
                }
            }

            // 更新空间网格
            if (enableSpatialGrid && spatialGrid != null) {
                spatialGrid.clear();
                for (DanmakuItem danmaku : activeDanmakus) {
                    spatialGrid.insert(danmaku);
                }
            }
        }
    }

    // 修改setGlobalSpeed方法以适应匀速/变速模式
    public void setGlobalSpeed(float speed) {
        this.globalSpeed = Math.max(minSpeed, Math.min(maxSpeed, speed));

        synchronized (activeDanmakus) {
            for (DanmakuItem danmaku : activeDanmakus) {
                if (uniformSpeed) {
                    // 匀速模式下直接设置为全局速度
                    danmaku.setSpeed(globalSpeed);
                } else {
                    // 变速模式下按比例调整
                    float baseSpeed = danmaku.getSpeed() / globalSpeed;
                    danmaku.setSpeed(baseSpeed * this.globalSpeed);
                }
            }
        }
    }
}