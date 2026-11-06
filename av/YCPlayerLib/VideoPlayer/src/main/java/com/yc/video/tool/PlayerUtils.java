/*
Copyright 2017 yangchong211（github.com/yangchong211）

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.yc.video.tool;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yc.video.config.ConstantKeys;
import com.yc.video.config.VideoPlayerConfig;
import com.yc.video.player.VideoViewManager;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2017/10/21
 *     desc  : 工具类
 *     revise:
 * </pre>
 */
public final class PlayerUtils {

    /**
     * 通过上下文获取到activity，使用到了递归
     *
     * @param context       上下文
     * @return              对象的活动对象，如果它不是活动对象，则为空。
     */
    public static Activity scanForActivity(Context context) {
        if (context == null) {
            return null;
        }
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    /**
     * 判断某Activity是否挂掉
     * @param activity      activity
     * @return
     */
    public static boolean isActivityLiving(@Nullable Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }


    /**
     * Get AppCompatActivity from context
     * @param context           上下文
     * @return AppCompatActivity if it's not null
     */
    private static AppCompatActivity getAppCompActivity(Context context) {
        if (context == null) {
            return null;
        }
        if (context instanceof AppCompatActivity) {
            return (AppCompatActivity) context;
        } else if (context instanceof ContextThemeWrapper) {
            return getAppCompActivity(((ContextThemeWrapper) context).getBaseContext());
        }
        return null;
    }


    /**
     * 获取屏幕宽度
     */
    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * 获取屏幕高度
     */
    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * 获取状态栏安全内边距高度（推荐EdgeToEdge场景，替代旧status_bar_height）
     * 优先从windowInsets拿，拿不到再兜底旧资源方式
     */
    public static int getStatusBarHeight(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decorView);
            if (insets != null) {
                return insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            }
        }
        // 兜底：insets获取失败时，沿用原来的资源读取方案
//        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
//        if (resourceId > 0) {
//            return context.getResources().getDimensionPixelSize(resourceId);
//        }
        return 0;
    }

    /**
     * 获取竖屏下状态栏高度
     * 新体系下，横竖屏切换时insets会自动变化，这个方法本质和上面一致，保留函数名兼容老代码
     */
    public static int getStatusBarHeightPortrait(Context context) {
        return getStatusBarHeight(context);
    }
    /**
     * 获取导航栏安全内边距高度（EdgeToEdge标准）
     */
    public static int getNavigationBarHeight(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decorView);
            if (insets != null) {
                return insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            }
        }
        // 兜底旧方案
//        if (!hasNavigationBar(context)) {
//            return 0;
//        }
//        Resources resources = context.getResources();
//        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
//        if (resourceId > 0) {
//            return resources.getDimensionPixelSize(resourceId);
//        }
        return 0;
    }

    /**
     * 是否存在导航栏（手势导航/三键导航）
     * 新版判断：导航栏insets.bottom > 0 代表有导航条（比屏幕尺寸差值更准）
     */
    public static boolean hasNavigationBar(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decorView);
            if (insets != null) {
                return insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0;
            }
        }
        // 兜底：拿不到insets时，保留你原来的屏幕比对逻辑
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//            Display display = getWindowManager(context).getDefaultDisplay();
//            Point size = new Point();
//            Point realSize = new Point();
//            display.getSize(size);
//            display.getRealSize(realSize);
//            return realSize.x != size.x || realSize.y != size.y;
//        } else {
//            boolean menu = ViewConfiguration.get(context).hasPermanentMenuKey();
//            boolean back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
//            return !(menu || back);
//        }
        return false;
    }

    /**
     * 获取屏幕宽度
     */
    public static int getScreenWidth(Context context, boolean isIncludeNav) {
        if (isIncludeNav) {
            return context.getResources().getDisplayMetrics().widthPixels + getNavigationBarHeight(context);
        } else {
            return context.getResources().getDisplayMetrics().widthPixels;
        }
    }

    /**
     * 获取屏幕高度
     */
    public static int getScreenHeight(Context context, boolean isIncludeNav) {
        if (isIncludeNav) {
            return context.getResources().getDisplayMetrics().heightPixels + getNavigationBarHeight(context);
        } else {
            return context.getResources().getDisplayMetrics().heightPixels;
        }
    }

    /**
     * dp转为px
     */
    public static int dp2px(Context context, float dpValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, context.getResources().getDisplayMetrics());
    }

    /**
     * sp转为px
     */
    public static int sp2px(Context context, float dpValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, dpValue, context.getResources().getDisplayMetrics());
    }


    /**
     * 获取集合的快照
     */
    @NonNull
    public static <T> List<T> getSnapshot(@NonNull Collection<T> other) {
        List<T> result = new ArrayList<>(other.size());
        for (T item : other) {
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
     */
    public static WindowManager getWindowManager(Context context) {
        return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * 边缘检测
     */
    public static boolean isEdge(Context context, MotionEvent e) {
        int edgeSize = dp2px(context, 40);
        return e.getRawX() < edgeSize
                || e.getRawX() > getScreenWidth(context, true) - edgeSize
                || e.getRawY() < edgeSize
                || e.getRawY() > getScreenHeight(context, true) - edgeSize;
    }



    /**
     * 将毫秒数格式化为"##:##"的时间
     * @param milliseconds 毫秒数
     * @return ##:##
     */
    public static String formatTime(long milliseconds) {
        if (milliseconds <= 0 || milliseconds >= 24 * 60 * 60 * 1000) {
            return "00:00";
        }
        long totalSeconds = milliseconds / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        StringBuilder stringBuilder = new StringBuilder();
        Formatter mFormatter = new Formatter(stringBuilder, Locale.getDefault());
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    /**
     * 保存播放位置，以便下次播放时接着上次的位置继续播放.
     * @param context           上下文
     * @param url               视频链接url
     */
    public static synchronized void savePlayPosition(Context context, String url, long position) {
        if (context==null){
            return;
        }
        context.getSharedPreferences("VIDEO_PLAYER_PLAY_POSITION",
                Context.MODE_PRIVATE).edit().putLong(url, position).apply();
    }

    /**
     * 取出上次保存的播放位置
     * @param context           上下文
     * @param url               视频链接url
     * @return 上次保存的播放位置
     */
    public static synchronized long getSavedPlayPosition(Context context, String url) {
        if (context==null){
            return 0;
        }
        return context.getSharedPreferences("VIDEO_PLAYER_PLAY_POSITION",
                Context.MODE_PRIVATE).getLong(url, 0);
    }

    /**
     * 清楚播放位置的痕迹
     * @param context           上下文
     */
    public static synchronized void clearPlayPosition(Context context){
        if (context==null){
            return;
        }
        context.getSharedPreferences("VIDEO_PLAYER_PLAY_POSITION",
                Context.MODE_PRIVATE).getAll().clear();
    }


    /**
     * 判断网络是否连接
     * <p>需添加权限 {@code <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>}</p>
     *
     * @return {@code true}: 是<br>{@code false}: 否
     */
    public static boolean isConnected(Context context) {
        NetworkInfo info = getActiveNetworkInfo(context);
        return info != null && info.isConnected();
    }

    /**
     * 获取当前系统时间
     */
    public static String getCurrentSystemTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date date = new Date();
        return simpleDateFormat.format(date);
    }


    /**
     * 获取活动网络信息
     * <p>需添加权限
     * {@code <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />}</p>
     *
     * @return NetworkInfo
     */
    @SuppressLint("MissingPermission")
    private static NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return null;
        }
        return manager.getActiveNetworkInfo();
    }

    /**
     * 空指针检测，不能为null，可以为""
     * @param obj           obj对象
     * @param message       消息
     * @param <T>           泛型
     * @return              obj
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }

    /**
     * 空指针检测，不能为null，也不能为""
     * @param obj           obj对象
     * @param message       消息
     * @return              字符串
     */
    public static String requireNonEmpty(String obj, String message) {
        if (TextUtils.isEmpty(obj)) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }


    /**
     * 将毫秒换成00:00:00
     * @param time                          毫秒
     * @return                              时间字符串
     */
    public static String getCountTimeByLong(long time) {
        //秒
        long totalTime = time / 1000;
        //时，分，秒
        long hour = 0, minute = 0, second = 0;

        if (3600 <= totalTime) {
            hour = totalTime / 3600;
            totalTime = totalTime - 3600 * hour;
        }
        if (60 <= totalTime) {
            minute = totalTime / 60;
            totalTime = totalTime - 60 * minute;
        }
        if (0 <= totalTime) {
            second = totalTime;
        }
        StringBuilder sb = new StringBuilder();
        if (hour < 10) {
            sb.append("0").append(hour).append(":");
        } else {
            sb.append(hour).append(":");
        }
        if (minute < 10) {
            sb.append("0").append(minute).append(":");
        } else {
            sb.append(minute).append(":");
        }
        if (second < 10) {
            sb.append("0").append(second);
        } else {
            sb.append(second);
        }
        return sb.toString();
    }


    /**
     * 将毫秒换成 00:00 或者 00，这个根据具体时间来计算
     * @param time                          毫秒
     * @return                              时间字符串
     */
    public static String getCountTime(long time) {
        //秒
        long totalTime = time / 1000;
        //时，分，秒
        long hour = 0, minute = 0, second = 0;

        if (3600 <= totalTime) {
            hour = totalTime / 3600;
            totalTime = totalTime - 3600 * hour;
        }
        if (60 <= totalTime) {
            minute = totalTime / 60;
            totalTime = totalTime - 60 * minute;
        }
        if (0 <= totalTime) {
            second = totalTime;
        }
        StringBuilder sb = new StringBuilder();
        if (hour>0){
            if (hour < 10) {
                sb.append("0").append(hour).append(":");
            } else {
                sb.append(hour).append(":");
            }
        }
        if (minute>0){
            if (minute < 10) {
                sb.append("0").append(minute).append(":");
            } else {
                sb.append(minute).append(":");
            }
        }
        if (second < 10) {
            sb.append("0").append(second);
        } else {
            sb.append(second);
        }
        return sb.toString();
    }


    /**
     * 获取当前的播放核心
     */
    public static Object getCurrentPlayerFactory() {
        VideoPlayerConfig config = VideoViewManager.getConfig();
        Object playerFactory = null;
        try {
            Field mPlayerFactoryField = config.getClass().getDeclaredField("mPlayerFactory");
            mPlayerFactoryField.setAccessible(true);
            playerFactory = mPlayerFactoryField.get(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playerFactory;
    }

    /**
     * 将View从父控件中移除
     */
    public static void removeViewFormParent(View v) {
        if (v == null) {
            return;
        }
        ViewParent parent = v.getParent();
        if (parent instanceof FrameLayout) {
            ((FrameLayout) parent).removeView(v);
        }
    }

    public static String playState2str(int state) {
        String playStateString;
        switch (state) {
            default:
            case ConstantKeys.CurrentState.STATE_IDLE:
                playStateString = "idle";
                break;
            case ConstantKeys.CurrentState.STATE_PREPARING:
                playStateString = "preparing";
                break;
            case ConstantKeys.CurrentState.STATE_PREPARED:
                playStateString = "prepared";
                break;
            case ConstantKeys.CurrentState.STATE_PLAYING:
                playStateString = "playing";
                break;
            case ConstantKeys.CurrentState.STATE_PAUSED:
                playStateString = "pause";
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PAUSED:
                playStateString = "buffering";
                break;
            case ConstantKeys.CurrentState.STATE_COMPLETED:
                playStateString = "buffered";
                break;
            case ConstantKeys.CurrentState.STATE_BUFFERING_PLAYING:
                playStateString = "playback completed";
                break;
            case ConstantKeys.CurrentState.STATE_ERROR:
                playStateString = "error";
                break;
        }
        return String.format("playState: %s", playStateString);
    }

    public static String playerState2str(int state) {
        String playerStateString;
        switch (state) {
            default:
            case ConstantKeys.PlayMode.MODE_NORMAL:
                playerStateString = "normal";
                break;
            case ConstantKeys.PlayMode.MODE_FULL_SCREEN:
                playerStateString = "full screen";
                break;
            case ConstantKeys.PlayMode.MODE_TINY_WINDOW:
                playerStateString = "tiny screen";
                break;
        }
        return String.format("playerState: %s", playerStateString);
    }

    /**
     * 是否是竖屏
     */
    public static boolean isPortraitOrientation(int orientation) {
        return orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }
}
