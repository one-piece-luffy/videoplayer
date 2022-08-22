package cn.mahua.av.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class AvSharePreference {

    private static void putString(Context context,String key, String value) {
        SharedPreferences mSharedPreferences =context.getSharedPreferences("app", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private static String getString(Context context,String key, String defaultValue) {
        SharedPreferences sp = context.getSharedPreferences("app", Context.MODE_PRIVATE);
        return sp.getString(key, defaultValue);
    }

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

    /**
     * 保存上一次的播放速度
     */
    public static void saveLastPlaySpeed(Context context,String value) {
        putString(context,"last_play_speed", value);
    }

    /**
     * 获取上一次的播放速度
     */
    public static String getLastPlaySpeed(Context context) {
        return getString(context,"last_play_speed", null);
    }


}
