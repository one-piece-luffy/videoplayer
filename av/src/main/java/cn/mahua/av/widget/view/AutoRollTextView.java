package cn.mahua.av.widget.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

public class AutoRollTextView extends AppCompatTextView {

    //开始自动滚动的时间
    private long start_roll_time = 1500;

    private boolean focus = false;

    private Runnable roll = () -> {
        focus = true;
        setText(getText());
    };

    public AutoRollTextView(Context context) {
        super(context);
    }

    public AutoRollTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoRollTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean isFocused() {
        return focus;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            postDelayed(roll, start_roll_time);
        } else {
            focus = false;
            setText(getText());
            removeCallbacks(roll);
        }
    }

    @SuppressWarnings("unused")
    public void setStartRollTime(long start_roll_time) {
        this.start_roll_time = start_roll_time;
    }
}
