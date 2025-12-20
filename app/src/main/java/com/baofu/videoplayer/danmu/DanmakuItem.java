package com.baofu.videoplayer.danmu;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

    public DanmakuItem(String text, int color, float speed) {
        this.text = text;
        this.color = color;
        this.speed = speed;
        this.createTime = System.currentTimeMillis();
        this.clickArea = new RectF();

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

    public void updateClickArea() {
        float textHeight = paint.getTextSize();
        updateClickArea(x, y, width, textHeight);
    }

    public void updateClickArea(float x, float y, float textWidth, float textHeight) {
        float horizontalPadding = 20f;
        float verticalPadding = 15f;
        float topOffset = textHeight * 0.8f;

        clickArea.left = x - horizontalPadding;
        clickArea.top = y - topOffset - verticalPadding;
        clickArea.right = x + textWidth + horizontalPadding;
        clickArea.bottom = y + textHeight * 0.2f + verticalPadding;
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

        // 绘制文字
        canvas.drawText(text, x, y, drawPaint);
    }

    // 绘制背景
    public void drawBackground(Canvas canvas, Paint bgPaint, float padding, float radius) {
        if (canvas == null || bgPaint == null) return;

        float textHeight = paint.getTextSize();
        float bgLeft = x - padding;
        float bgTop = y - textHeight + padding;
        float bgRight = x + width + padding;
        float bgBottom = y + padding;

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, radius, radius, bgPaint);
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
}