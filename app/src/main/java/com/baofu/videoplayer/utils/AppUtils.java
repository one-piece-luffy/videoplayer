package com.baofu.videoplayer.utils;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class AppUtils {
    public static int hexToIntSupportAlpha(String hexColor) {
        if(TextUtils.isEmpty(hexColor)){
            hexColor="#FFFFFF";
        }
        String cleanHex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        String fullHex;

        if (cleanHex.length() == 6) {
            // 6位：补Alpha（不透明）
            fullHex = "FF" + cleanHex;
        } else if (cleanHex.length() == 8) {
            // 8位：直接使用（含Alpha）
            fullHex = cleanHex;
        } else {
//            throw new IllegalArgumentException("无效的颜色格式（需6/8位）：" + hexColor);
            fullHex="FFFFFF";
        }

        // 解析为整数（支持负数，因为int是有符号的）
        return Integer.parseUnsignedInt(fullHex, 16);
    }
    public static void hideKeyboardFrom(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
