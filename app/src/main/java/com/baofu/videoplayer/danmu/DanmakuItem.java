package com.baofu.videoplayer.danmu;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

// DanmakuItem.java
public class DanmakuItem {
    private String text;
    private int color;
    private float speed;
    private float x;
    private float y;
    private Paint paint;
    private float width;
    private boolean isMeasured = false;
    private long createTime;
    private RectF clickArea;
    private boolean clickable = true;
    private int lineNumber = -1;
    private Object tag;
    private int userId;
    private String userName;
    public long show_time;
    public int like_count;
    public String id;
    private boolean isUserOwned = false;  // 默认不是用户自己的弹幕
    private int priority = 0;  // 优先级（0-10），数值越大越优先展示（用户弹幕可设为更高优先级）
    public static final int MAX_PRIORITY=10;  //最高优先级
    private boolean showBackground = false;  // 是否显示背景，默认为false
    private int backgroundColor = 0;         // 背景颜色
    private float backgroundPadding = 0;     // 背景内边距
    private float backgroundRadius = 0;      // 背景圆角半径
    // ==================== 渐变色相关属性 ====================
    private int[] gradientColors = null;        // 渐变颜色数组
    private float[] gradientPositions = null;   // 渐变位置数组
    private int gradientType = GRADIENT_NONE;   // 渐变类型
    private float gradientAngle = 0;           // 渐变角度（度）
    private boolean gradientReversed = false;   // 是否反转渐变

    // ==================== 渐变类型常量 ====================
    public static final int GRADIENT_NONE = 0;      // 无渐变
    public static final int GRADIENT_LINEAR = 1;    // 线性渐变
    public static final int GRADIENT_RADIAL = 2;    // 径向渐变
    public static final int GRADIENT_SWEEP = 3;     // 扫描渐变

    // ==================== 新增：渐变方向常量 ====================
    public static final int DIRECTION_LEFT_TO_RIGHT = 0;
    public static final int DIRECTION_TOP_TO_BOTTOM = 1;
    public static final int DIRECTION_RIGHT_TO_LEFT = 2;
    public static final int DIRECTION_BOTTOM_TO_TOP = 3;
    public static final int DIRECTION_DIAGONAL_TOPLEFT_TO_BOTTOMRIGHT = 4;
    public static final int DIRECTION_DIAGONAL_TOPRIGHT_TO_BOTTOMLEFT = 5;
    public static final int DIRECTION_DIAGONAL_BOTTOMLEFT_TO_TOPRIGHT = 6;
    public static final int DIRECTION_DIAGONAL_BOTTOMRIGHT_TO_TOPLEFT = 7;
    // 新增方法：单独设置垂直padding的比例
    private float verticalPaddingRatio = 0.3f; // 垂直padding占水平padding的比例

    public DanmakuItem(String text, int color, float speed) {
        this.text = text;
        this.color = color;
        this.speed = speed;
        this.createTime = System.currentTimeMillis();
        this.clickArea = new RectF();

        this.isUserOwned = false;  // 默认不是用户自己的
        this.priority = 0;  // 默认优先级

        this.showBackground = false;
        this.backgroundColor = 0;
        this.backgroundPadding = 0;
        this.backgroundRadius = 0;

        this.gradientColors = null;
        this.gradientPositions = null;
        this.gradientType = GRADIENT_NONE;
        this.gradientAngle = 0;
        this.gradientReversed = false;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(40);
    }
    // 拷贝构造函数
    public DanmakuItem(DanmakuItem other) {
        this.text = other.text;
        this.color = other.color;
        this.speed = other.speed;
        this.x = other.x;
        this.y = other.y;
        this.lineNumber = other.lineNumber;
        // 拷贝其他必要属性...
        this.paint=other.getPaint();
        this.width=other.getWidth();
        this.isMeasured = other.isMeasured;
        this.createTime=other.getCreateTime();
        this.clickArea=other.getClickArea();
        this.clickable = other.clickable;
        this.tag=other.tag;
        this.userId=other.userId;
        this.userName=other.userName;
        this.show_time=other.show_time;
        this.like_count=other.like_count;
        this.id=other.id;
        this.isUserOwned = other.isUserOwned;
        this.priority = other.priority;
        this.showBackground = other.showBackground;
        this.backgroundColor = other.backgroundColor;
        this.backgroundPadding = other.backgroundPadding;
        this.backgroundRadius = other.backgroundRadius;
        if (other.gradientColors != null) {
            this.gradientColors = other.gradientColors.clone();
        }
        if (other.gradientPositions != null) {
            this.gradientPositions = other.gradientPositions.clone();
        }
        this.gradientType = other.gradientType;
        this.gradientAngle = other.gradientAngle;
        this.gradientReversed = other.gradientReversed;
    }


    public void measure(Paint referencePaint) {
        if (!isMeasured || referencePaint == null) {
            // 确保使用参考画笔的文本大小
            float originalSize = paint.getTextSize();
            float targetSize = referencePaint.getTextSize();

            if (Math.abs(originalSize - targetSize) > 0.1f) {
                paint.setTextSize(targetSize);
            }

            width = paint.measureText(text);
            isMeasured = true;
        }
    }
    // ==================== 新增：标记为用户弹幕的方法 ====================
    public void markAsUserOwned() {
        this.isUserOwned = true;
        this.priority = 10;  // 用户弹幕设置较高优先级
    }

    // ==================== 新增：带有用户标记的构造函数 ====================
    public DanmakuItem(String text, int color, float speed, boolean isUserOwned) {
        this(text, color, speed);
        if (isUserOwned) {
            markAsUserOwned();
        }
    }

    public boolean isUserOwned() {
        return isUserOwned;
    }

    public void setUserOwned(boolean userOwned) {
        this.isUserOwned = userOwned;
        if (userOwned) {
            this.priority = MAX_PRIORITY;  // 用户弹幕自动提升优先级
        } else {
            this.priority = 0;   // 恢复默认优先级
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority > MAX_PRIORITY) {
            priority = MAX_PRIORITY;
        }
        this.priority = priority;
    }

    public boolean hasHighPriority() {
        return priority > 0;
    }

    /**
     * 设置弹幕的点击区域
     */
    public void updateClickArea() {
        updateClickAreaExact();

    }

    /**
     * 使用文字的实际边界框
     */
    public void updateClickAreaPrecise() {
        // 方法1：使用文字的实际边界框
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textTop = y + fontMetrics.top;
        float textBottom = y + fontMetrics.bottom;

        // 根据文字长度和字体大小动态计算padding
        float dynamicHorizontalPadding = Math.min(width * 0.05f, 8f);
        float dynamicVerticalPadding = Math.min(Math.abs(fontMetrics.top - fontMetrics.bottom) * 0.1f, 4f);

        clickArea.set(
                x - dynamicHorizontalPadding,
                textTop - dynamicVerticalPadding,
                x + width + dynamicHorizontalPadding,
                textBottom + dynamicVerticalPadding
        );
    }

    /**
     * 使用文字实际占用的矩形更新点击区域
     */
    public void updateClickAreaExact() {
        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);

        // 将相对坐标转换为绝对坐标
        float textTop = y + textBounds.top;
        float textBottom = y + textBounds.bottom;
        float textLeft = x + textBounds.left;
        float textRight = x + textBounds.right;

        // 很小的padding确保可点击性
        float padding = Math.min(3f, Math.min(width, Math.abs(textBounds.height())) * 0.05f);

        clickArea.set(
                textLeft - padding,
                textTop - padding,
                textRight + padding,
                textBottom + padding
        );
    }

    public boolean containsPoint(float x, float y) {
        return clickArea.contains(x, y);
    }

    // 独立绘制方法，避免画笔状态污染
    public void draw(Canvas canvas, Paint defaultPaint) {
        if (canvas == null || text == null) return;

        // 创建独立的画笔实例
        Paint drawPaint = new Paint(defaultPaint);
        drawPaint.setColor(color);
        drawPaint.setTextSize(defaultPaint.getTextSize());
        if (isUserOwned) {
            // 用户弹幕可以添加特殊效果，比如描边或阴影
            drawPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            drawPaint.setStrokeWidth(1.5f);
        }

        // 绘制文字
        canvas.drawText(text, x, y, drawPaint);
    }

    public void drawBackground(Canvas canvas, Paint defaultBgPaint,
                               float horizontalPadding, float verticalPadding,
                               float radius) {
        if (canvas == null || !showBackground) return;

        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textTop = y + fontMetrics.ascent;
        float textBottom = y + fontMetrics.descent;

        // 只对左右使用horizontalPadding，上下使用verticalPadding
        float bgTop = textTop - verticalPadding;
        float bgBottom = textBottom + verticalPadding;
        float bgLeft = x - horizontalPadding;
        float bgRight = x + width + horizontalPadding;

        RectF bgRect = new RectF(bgLeft, bgTop, bgRight, bgBottom);

        Paint bgPaint = new Paint(defaultBgPaint);

        if (hasGradient()) {
            Shader gradientShader = createGradientShader(bgRect);
            if (gradientShader != null) {
                bgPaint.setShader(gradientShader);
            } else {
                bgPaint.setColor(backgroundColor != 0 ? backgroundColor : 0x64000000);
                bgPaint.setShader(null);
            }
        } else {
            bgPaint.setColor(backgroundColor != 0 ? backgroundColor : 0x64000000);
            bgPaint.setShader(null);
        }

        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
    }

    // 简化方法：使用单个padding参数，但上下使用较小的值
    public void drawBackground(Canvas canvas, Paint defaultBgPaint,
                               float padding, float radius) {
        // 默认上下padding是左右padding的0.3倍
        float verticalPadding = padding * 0.3f;
        drawBackground(canvas, defaultBgPaint, padding, verticalPadding, radius);
    }

    // 新增方法：分别设置水平和垂直padding
    public void drawBackground(Canvas canvas, Paint defaultBgPaint) {
        if (canvas == null || !showBackground) return;

        float horizontalPadding = backgroundPadding > 0 ? backgroundPadding : 5;
        float verticalPadding = horizontalPadding * 0.3f; // 上下padding是左右的30%
        float radius = backgroundRadius > 0 ? backgroundRadius : 8;

        drawBackground(canvas, defaultBgPaint, horizontalPadding, verticalPadding, radius);
    }

    /**
     * 创建渐变着色器
     */
    private Shader createGradientShader(RectF rect) {
        if (gradientColors == null || gradientColors.length < 2) {
            return null;
        }

        float centerX = rect.centerX();
        float centerY = rect.centerY();
        float width = rect.width();
        float height = rect.height();
        float radius = Math.max(width, height) / 2;

        int[] colors = gradientColors;
        float[] positions = gradientPositions;

        // 如果需要反转
        if (gradientReversed) {
            colors = new int[gradientColors.length];
            for (int i = 0; i < gradientColors.length; i++) {
                colors[i] = gradientColors[gradientColors.length - 1 - i];
            }
        }

        switch (gradientType) {
            case GRADIENT_LINEAR:
                // 计算线性渐变的起点和终点
                double angleRad = Math.toRadians(gradientAngle);
                float cos = (float) Math.cos(angleRad);
                float sin = (float) Math.sin(angleRad);

                // 计算斜边长度
                float diagonal = (float) Math.sqrt(width * width + height * height);

                // 计算起点和终点
                float startX = centerX - cos * diagonal / 2;
                float startY = centerY - sin * diagonal / 2;
                float endX = centerX + cos * diagonal / 2;
                float endY = centerY + sin * diagonal / 2;

                return new LinearGradient(startX, startY, endX, endY,
                        colors, positions, Shader.TileMode.CLAMP);

            case GRADIENT_RADIAL:
                // 径向渐变
                float centerXPercent = (int) (gradientAngle / 1000) / 1000f;
                float centerYPercent = gradientAngle % 1000 / 1000f;

                float radialCenterX = rect.left + width * centerXPercent;
                float radialCenterY = rect.top + height * centerYPercent;

                return new RadialGradient(radialCenterX, radialCenterY, radius,
                        colors, positions, Shader.TileMode.CLAMP);

            case GRADIENT_SWEEP:
                // 扫描渐变
                return new SweepGradient(centerX, centerY, colors, positions);

            default:
                return null;
        }
    }

    // Getter and Setter
    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        this.isMeasured = false;
    }

    public int getColor() { return color; }
    public void setColor(int color) {
        this.color = color;
        paint.setColor(color);
    }

    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public Paint getPaint() { return paint; }

    public float getWidth() { return width; }

    public boolean isMeasured() { return isMeasured; }
    public void setMeasured(boolean measured) { isMeasured = measured; }

    public long getCreateTime() { return createTime; }

    public RectF getClickArea() { return clickArea; }

    public boolean isClickable() { return clickable; }
    public void setClickable(boolean clickable) { this.clickable = clickable; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public Object getTag() { return tag; }
    public void setTag(Object tag) { this.tag = tag; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public void setPaint(Paint paint) {
        this.paint = paint;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public void setClickArea(RectF clickArea) {
        this.clickArea = clickArea;
    }

    public boolean isShowBackground() {
        return showBackground;
    }

    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        // 如果设置了纯色背景，清除渐变
        if (backgroundColor != 0) {
            clearGradient();
        }
    }

    public float getBackgroundPadding() {
        return backgroundPadding;
    }

    public void setBackgroundPadding(float backgroundPadding) {
        this.backgroundPadding = backgroundPadding;
    }

    public float getBackgroundRadius() {
        return backgroundRadius;
    }

    public void setBackgroundRadius(float backgroundRadius) {
        this.backgroundRadius = backgroundRadius;
    }

    // 修改背景设置方法，添加垂直padding参数
    public void setBackground(boolean show, int color,
                              float horizontalPadding, float verticalPadding,
                              float radius) {
        this.showBackground = show;
        this.backgroundColor = color;
        this.backgroundPadding = horizontalPadding; // 保存为水平padding
        this.backgroundRadius = radius;
        if (color != 0) {
            clearGradient();
        }
    }

    // 修改原有的setBackground方法，保持兼容性
    public void setBackground(boolean show, int color, float padding, float radius) {
        // 这里传入的padding作为水平padding
        setBackground(show, color, padding, padding * 0.1f, radius);
    }

    /**
     * 设置背景（默认参数）
     */
    public void setBackground(boolean show) {
        if (show) {
            setBackground(true, 0x64000000);
        } else {
            this.showBackground = false;
        }
    }


    /**
     * 设置背景（带颜色）
     */
    public void setBackground(boolean show, int color) {
        setBackground(show, color, 5, 5 * 0.3f, 8);
    }
    /**
     * 设置线性渐变
     * @param colors 渐变颜色数组
     * @param direction 渐变方向（使用DIRECTION常量）
     */
    public void setLinearGradient(int[] colors, int direction) {
        if (colors == null || colors.length < 2) {
            gradientType = GRADIENT_NONE;
            return;
        }

        this.gradientType = GRADIENT_LINEAR;
        this.gradientColors = colors.clone();

        // 根据方向设置位置（如果需要）
        this.gradientPositions = createPositionsArray(colors.length);

        // 设置角度（根据方向）
        this.gradientAngle = getAngleForDirection(direction);
    }

    /**
     * 设置线性渐变（带角度）
     */
    public void setLinearGradient(int[] colors, float angle) {
        if (colors == null || colors.length < 2) {
            gradientType = GRADIENT_NONE;
            return;
        }

        this.gradientType = GRADIENT_LINEAR;
        this.gradientColors = colors.clone();
        this.gradientPositions = createPositionsArray(colors.length);
        this.gradientAngle = angle;
    }

    /**
     * 设置线性渐变（带位置）
     */
    public void setLinearGradient(int[] colors, float[] positions, float angle) {
        if (colors == null || colors.length < 2) {
            gradientType = GRADIENT_NONE;
            return;
        }

        this.gradientType = GRADIENT_LINEAR;
        this.gradientColors = colors.clone();

        if (positions != null && positions.length == colors.length) {
            this.gradientPositions = positions.clone();
        } else {
            this.gradientPositions = createPositionsArray(colors.length);
        }

        this.gradientAngle = angle;
    }

    /**
     * 设置径向渐变
     */
    public void setRadialGradient(int[] colors, float centerXPercent, float centerYPercent) {
        if (colors == null || colors.length < 2) {
            gradientType = GRADIENT_NONE;
            return;
        }

        this.gradientType = GRADIENT_RADIAL;
        this.gradientColors = colors.clone();
        this.gradientPositions = createPositionsArray(colors.length);
        // 中心点百分比存储在gradientAngle中（x * 1000 + y）
        this.gradientAngle = centerXPercent * 1000 + centerYPercent;
    }

    /**
     * 设置扫描渐变
     */
    public void setSweepGradient(int[] colors) {
        if (colors == null || colors.length < 2) {
            gradientType = GRADIENT_NONE;
            return;
        }

        this.gradientType = GRADIENT_SWEEP;
        this.gradientColors = colors.clone();
        this.gradientPositions = createPositionsArray(colors.length);
    }

    /**
     * 清除渐变
     */
    public void clearGradient() {
        this.gradientType = GRADIENT_NONE;
        this.gradientColors = null;
        this.gradientPositions = null;
        this.gradientAngle = 0;
        this.gradientReversed = false;
    }

    /**
     * 是否有渐变
     */
    public boolean hasGradient() {
        return gradientType != GRADIENT_NONE && gradientColors != null && gradientColors.length >= 2;
    }

    /**
     * 获取渐变类型
     */
    public int getGradientType() {
        return gradientType;
    }

    /**
     * 获取渐变颜色数组
     */
    public int[] getGradientColors() {
        return gradientColors;
    }

    /**
     * 获取渐变位置数组
     */
    public float[] getGradientPositions() {
        return gradientPositions;
    }

    /**
     * 获取渐变角度
     */
    public float getGradientAngle() {
        return gradientAngle;
    }

    /**
     * 是否反转渐变
     */
    public boolean isGradientReversed() {
        return gradientReversed;
    }

    public void setGradientReversed(boolean reversed) {
        this.gradientReversed = reversed;
    }

    /**
     * 反转渐变颜色
     */
    public void reverseGradient() {
        if (gradientColors != null && gradientColors.length > 1) {
            int[] reversed = new int[gradientColors.length];
            for (int i = 0; i < gradientColors.length; i++) {
                reversed[i] = gradientColors[gradientColors.length - 1 - i];
            }
            gradientColors = reversed;
            gradientReversed = !gradientReversed;
        }
    }

    /**
     * 创建等间距位置数组
     */
    private float[] createPositionsArray(int length) {
        float[] positions = new float[length];
        float step = 1.0f / (length - 1);
        for (int i = 0; i < length; i++) {
            positions[i] = i * step;
        }
        return positions;
    }

    /**
     * 根据方向获取角度
     */
    private float getAngleForDirection(int direction) {
        switch (direction) {
            case DIRECTION_LEFT_TO_RIGHT:
                return 0;
            case DIRECTION_TOP_TO_BOTTOM:
                return 90;
            case DIRECTION_RIGHT_TO_LEFT:
                return 180;
            case DIRECTION_BOTTOM_TO_TOP:
                return 270;
            case DIRECTION_DIAGONAL_TOPLEFT_TO_BOTTOMRIGHT:
                return 45;
            case DIRECTION_DIAGONAL_TOPRIGHT_TO_BOTTOMLEFT:
                return 135;
            case DIRECTION_DIAGONAL_BOTTOMLEFT_TO_TOPRIGHT:
                return 315;
            case DIRECTION_DIAGONAL_BOTTOMRIGHT_TO_TOPLEFT:
                return 225;
            default:
                return 0;
        }
    }

    /**
     * 预定义渐变方案
     */

    // 彩虹渐变
    public static final int[] GRADIENT_RAINBOW = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    };

    // 火焰渐变
    public static final int[] GRADIENT_FIRE = {
            0xFFFF0000, 0xFFFF5500, 0xFFFFAA00, 0xFFFFFF00
    };

    // 海洋渐变
    public static final int[] GRADIENT_OCEAN = {
            0xFF0066CC, 0xFF0099FF, 0xFF66CCFF, 0xFF99FFFF
    };

    // 森林渐变
    public static final int[] GRADIENT_FOREST = {
            0xFF006600, 0xFF009900, 0xFF66CC00, 0xFF99FF99
    };

    // 日落渐变
    public static final int[] GRADIENT_SUNSET = {
            0xAAFF4500, 0x88FFA500, 0x66FFD700, 0x11FFEC8B
    };

    // 霓虹渐变
    public static final int[] GRADIENT_NEON = {
            0xFFFF00FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00
    };

    /**
     * 设置预定义渐变
     */
    public void setPresetGradient(int[] presetColors, int direction) {
        setLinearGradient(presetColors, direction);
    }

    /**
     * 设置彩虹渐变
     */
    public void setRainbowGradient(int direction) {
        setLinearGradient(GRADIENT_RAINBOW, direction);
    }

    /**
     * 设置双色渐变
     */
    public void setTwoColorGradient(int startColor, int endColor, int direction) {
        int[] colors = {startColor, endColor};
        setLinearGradient(colors, direction);
    }

    /**
     * 设置三色渐变
     */
    public void setThreeColorGradient(int startColor, int middleColor, int endColor, int direction) {
        int[] colors = {startColor, middleColor, endColor};
        setLinearGradient(colors, direction);
    }
    /**
     * 设置渐变背景
     */
    public void setGradientBackground(boolean show, int[] gradientColors, int direction,
                                      float padding, float radius) {
        this.showBackground = show;
        if (show) {
            setLinearGradient(gradientColors, direction);
            this.backgroundPadding = padding;
            this.backgroundRadius = radius;
            this.backgroundColor = 0; // 清除纯色背景
        }
    }

    /**
     * 设置渐变背景（简化版）
     */
    public void setGradientBackground(int[] gradientColors, int direction) {
        setGradientBackground(true, gradientColors, direction, 5, 8);
    }
    public float getVerticalPaddingRatio() {
        return verticalPaddingRatio;
    }

    public void setVerticalPaddingRatio(float ratio) {
        this.verticalPaddingRatio = Math.max(0, Math.min(ratio, 1.0f));
    }

    // 使用比例计算垂直padding
    private float calculateVerticalPadding(float horizontalPadding) {
        return horizontalPadding * verticalPaddingRatio;
    }
}