package cn.mahua.av.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class AvSharePreference {

    public static void saveShowAvGuide(Context context,boolean boo) {
        SharedPreferences mSharedPreferences = context.getSharedPreferences("app", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean("av_show_av_guide", boo);
        editor.apply();
    }

    public static boolean getShowAvGuide(Context context) {
        SharedPreferences sp = context.getSharedPreferences("app", Context.MODE_PRIVATE);
        return sp.getBoolean("av_show_av_guide", true);
    }

}
