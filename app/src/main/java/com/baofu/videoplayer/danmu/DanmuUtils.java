package com.baofu.videoplayer.danmu;

import android.content.Context;

public class DanmuUtils {
    public static int dp2px(Context context, float dpValue) {
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

}
