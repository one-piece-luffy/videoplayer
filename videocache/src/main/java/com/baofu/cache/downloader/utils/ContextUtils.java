package com.baofu.cache.downloader.utils;

import android.content.Context;

public class ContextUtils {

    private static Context sApplicationContext;

    public static void initApplicationContext(Context appContext) {
        if (appContext == null) {
            throw new RuntimeException("Global application context set error");
        }
        sApplicationContext = appContext.getApplicationContext();
    }

    public static Context getApplicationContext() {
        return sApplicationContext;
    }
}
