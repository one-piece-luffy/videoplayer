package cn.mahua.av.widget.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;


import cn.mahua.av.DensityUtil;
import cn.mahua.av.R;

public class SortVodView extends HorizontalScrollView implements View.OnClickListener {


    private LinearLayout linearLayout;
    private String[] strings;
    private int textSize = 0;
    private OnClickListener onClickListener;

    public SortVodView(Context context) {
        this(context,null);
    }

    public SortVodView(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SortVodView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linearLayout = new LinearLayout(getContext());
        linearLayout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(linearLayout);
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public void setData(String data) {
        if ("".equals(data) || data == null) return;
        removeAllViews();
        strings = data.split(",");
        readData();
    }

    public void setData(String data, int textSize) {
        if ("".equals(data) || data == null) return;
        setTextSize(textSize);
        linearLayout.removeAllViews();
        strings = data.split(",");
        readData();
    }

    private void readData() {
        if (strings.length <= 0) return;
        for (int i = 0; i < strings.length; i++) {
            TextView textView = new TextView(getContext());
            if (textSize != 0) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, DensityUtil.sp2px(textView.getContext(),textSize));
            }
            textView.setTag(R.id.sortVodData, strings[i]);
            textView.setOnClickListener(this);
            if (i != 0) {
                textView.setText(" ");
            }
            if (i != strings.length - 1) {
                textView.append(strings[i] + " .");
            } else {
                textView.append(strings[i]);
            }
            linearLayout.addView(textView);
        }
        textSize = 0;
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Override
    public void onClick(View view) {
        String data = (String) view.getTag(R.id.sortVodData);
        if (onClickListener != null) {
            onClickListener.onClick(view,data);
        }
    }

    public interface OnClickListener{
        void onClick(View view, String data);
    }

}
