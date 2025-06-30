package com.baofu.downloader.utils;

import android.content.Context;

import com.baofu.downloader.common.VideoDownloadConstants;

public class VideoDownloadConfig {

    //公共目录保存路径
    public String publicPath;
    //私有目录保存路径
    public String privatePath;
    private int readTimeOut;
    private int connTimeOut;
    private int writeTimeOut;
    //并发下载数
    public int concurrentCount;
    public Context context;
    //下载失败重试次数
    public int retryCount = 1;
    public boolean threadSchedule;


    public int getReadTimeOut(){
        if(readTimeOut==0){
            return VideoDownloadConstants.READ_TIMEOUT;
        }
        return readTimeOut;
    }
    public int getConnTimeOut(){
        if(connTimeOut==0){
            return VideoDownloadConstants.CONN_TIMEOUT;
        }
        return connTimeOut;
    }
    public int getWriteTimeOut(){
        if(writeTimeOut==0){
            return VideoDownloadConstants.WRITE_TIMEOUT;
        }
        return writeTimeOut;
    }
    public static class Builder {
        VideoDownloadConfig mConfig;

        public Builder( Context context) {
            mConfig = new VideoDownloadConfig();
            ContextUtils.initApplicationContext(context);
        }

        public Builder publicPath(String publicPath) {
            mConfig.publicPath = publicPath;
            return this;
        }


        public Builder privatePath(String privatePath) {
            mConfig.privatePath = privatePath;
            return this;
        }


        public Builder readTimeOut(int readTimeOut) {
            mConfig.readTimeOut = readTimeOut;
            return this;
        }


        public Builder connTimeOut(int connTimeOut) {
            mConfig.connTimeOut = connTimeOut;
            return this;
        }
        public Builder writeTimeOut(int writeTimeOut) {
            mConfig.writeTimeOut = writeTimeOut;
            return this;
        }



        public Builder concurrentCount(int concurrentCount) {
            mConfig.concurrentCount = concurrentCount;
            return this;
        }


        public Builder context(Context context) {
            mConfig.context = context;
            return this;
        }
        public Builder threadSchedule(boolean threadSchedule) {
            mConfig.threadSchedule = threadSchedule;
            return this;
        }


        public Builder retryCount(int retryCount) {
            mConfig.retryCount = retryCount;
            return this;
        }
        public VideoDownloadConfig build() {
            return mConfig;
        }
    }

}
