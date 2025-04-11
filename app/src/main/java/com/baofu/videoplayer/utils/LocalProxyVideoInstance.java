package com.baofu.videoplayer.utils;

import com.jeffmony.videocache.control.LocalProxyVideoControl;

import java.util.Map;

public class LocalProxyVideoInstance {
    private static LocalProxyVideoInstance instance;
    LocalProxyVideoControl localProxyVideoControl;

    public static LocalProxyVideoInstance getInstance() {
        if (instance == null) {
            instance = new LocalProxyVideoInstance();
        }
        return instance;
    }

    public void init() {
        if (localProxyVideoControl == null) {
            localProxyVideoControl = new LocalProxyVideoControl();
        }
    }

    public void pause() {
        if (localProxyVideoControl == null) {
            init();
        }
        DefaultExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    localProxyVideoControl.pauseLocalProxyTask();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
    }

    public void resume() {
        if (localProxyVideoControl == null) {
            init();
        }
        DefaultExecutor.execute(() -> localProxyVideoControl.resumeLocalProxyTask());
    }

    public void release() {
        if (localProxyVideoControl == null) {
            init();
        }
        DefaultExecutor.execute(() -> localProxyVideoControl.releaseLocalProxyResources());
    }

    public void start(String url,String name, Map<String, String> header, Map<String, Object> param) {
        if (localProxyVideoControl == null) {
            init();
        }
        DefaultExecutor.execute(() -> localProxyVideoControl.startRequestVideoInfo(url, name,header, param));
    }

    public void seekto(long position, long totalDuration) {
        if (localProxyVideoControl == null) {
            init();
        }
        DefaultExecutor.execute(new Runnable() {
            @Override
            public void run() {
                localProxyVideoControl.seekToCachePosition(position, totalDuration);
            }
        });
    }

}
