package com.jeffmony.videocache;

import com.jeffmony.videocache.listener.IPlayerProgressListener;

public class PlayerProgressListenerManager {
    private static volatile PlayerProgressListenerManager sInstance = null;
    private  IPlayerProgressListener mListener;

    public static PlayerProgressListenerManager getInstance() {
        if (sInstance == null) {
            synchronized (PlayerProgressListenerManager.class) {
                if (sInstance == null) {
                    sInstance = new PlayerProgressListenerManager();
                }
            }
        }
        return sInstance;
    }

    public IPlayerProgressListener getListener() {
        return mListener;
    }

    public void setListener(IPlayerProgressListener mListener) {
        this.mListener = mListener;
    }

    public void log(String log) {
        if (mListener != null) {
            mListener.playerCacheLog(log);
        }
    }
}
