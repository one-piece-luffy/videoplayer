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
package com.yc.video.player;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.yc.video.controller.BaseVideoController;
import com.yc.video.tool.PlayerUtils;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2018/11/9
 *     desc  : 播放器帮助类
 *     revise:
 * </pre>
 */
public class VideoPlayerHelper {

    private static VideoPlayerHelper sInstance;

    /**
     * 构造方法，避免直接new
     */
    private VideoPlayerHelper() {
        //避免初始化
    }


    /**
     * 一定要使用单例模式，保证同一时刻只有一个视频在播放，其他的都是初始状态
     * 单例模式
     * @return          VideoPlayerManager对象
     */
    public static VideoPlayerHelper instance() {
        if (sInstance == null) {
            synchronized (VideoPlayerHelper.class){
                if (sInstance == null){
                    sInstance = new VideoPlayerHelper();
                }
            }
        }
        return sInstance;
    }

    /**
     * 获取DecorView
     * @param context                               上下文
     * @param videoController                       controller
     */
    protected ViewGroup getDecorView(Context context , BaseVideoController videoController) {
        Activity activity = VideoPlayerHelper.instance().getActivity(context,videoController);
        if (activity == null) {
            return null;
        }
        return (ViewGroup) activity.getWindow().getDecorView();
    }

    /**
     * 获取activity中的content view,其id为android.R.id.content
     * @param context                               上下文
     * @param videoController                       controller
     */
    protected ViewGroup getContentView(Context context , BaseVideoController videoController) {
        Activity activity = VideoPlayerHelper.instance().getActivity(context,videoController);
        if (activity == null) {
            return null;
        }
        return activity.findViewById(android.R.id.content);
    }


    /**
     * 获取Activity，优先通过Controller去获取Activity
     * @param context                               上下文
     * @param videoController                       controller
     */
    protected Activity getActivity(Context context , BaseVideoController videoController) {
        Activity activity;
        if (videoController != null) {
            activity = PlayerUtils.scanForActivity(videoController.getContext());
            if (activity == null) {
                activity = PlayerUtils.scanForActivity(context);
            }
        } else {
            activity = PlayerUtils.scanForActivity(context);
        }
        return activity;
    }

    /**
     * 显示NavigationBar和StatusBar
     * @param decorView                             decorView
     * @param context                               上下文
     * @param videoController                       controller
     */
    protected void showSysBar(ViewGroup decorView, Context context, BaseVideoController videoController) {
        Activity activity = VideoPlayerHelper.instance().getActivity(context, videoController);
        if (activity == null) return;

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(activity.getWindow(), decorView);
        // 显示状态栏 + 导航栏
        controller.show(WindowInsetsCompat.Type.systemBars());
        // 取消沉浸式粘性模式
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        // 清除全屏Flag
//        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * 隐藏NavigationBar和StatusBar
     * @param decorView                             decorView
     * @param context                               上下文
     * @param videoController                       controller
     */
    protected void hideSysBar(ViewGroup decorView,Context context , BaseVideoController videoController) {
        Activity activity = VideoPlayerHelper.instance().getActivity(context, videoController);
        if (activity == null) return;

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(activity.getWindow(), decorView);
        // 隐藏状态栏+导航栏
        controller.hide(WindowInsetsCompat.Type.systemBars());
        // 粘性沉浸式：下滑短暂显示，之后自动隐藏（等价旧的 IMMERSIVE_STICKY）
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // 添加全屏flag
//        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }


    /**
     * 判断是否为本地数据源，包括 本地文件、Asset、raw
     * @param url                                   url地址
     * @param assetFileDescriptor                   assets文件
     * @return                                      是否为本地数据源
     */
    protected boolean isLocalDataSource(String url, AssetFileDescriptor assetFileDescriptor) {
        if (assetFileDescriptor != null) {
            return true;
        } else if (!TextUtils.isEmpty(url)) {
            Uri uri = Uri.parse(url);
            return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())
                    || ContentResolver.SCHEME_FILE.equals(uri.getScheme())
                    || "rawresource".equals(uri.getScheme());
        }
        return false;
    }



}
