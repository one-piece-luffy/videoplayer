package com.baofu.downloader.model;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.baofu.downloader.m3u8.M3U8;
import com.baofu.downloader.utils.VideoDownloadUtils;

import java.util.Map;

public class VideoTaskItem implements Cloneable, Parcelable {

    public int id;
    @NonNull
    public String mUrl="-1";                 //下载视频的url
    public String mCoverUrl;            //封面图的url
    public String mCoverPath;           //封面图存储的位置
    public long mDownloadCreateTime;    //下载创建的时间
    public int mTaskState;              //当前任务的状态
    public String mMimeType;            // 视频url的mime type
    public String mFinalUrl;            //30x跳转之后的url
    public int mErrorCode;              //当前任务下载错误码
    public int mVideoType;              //当前文件类型
    public M3U8 mM3U8;                  //M3U8结构,如果非M3U8,则为null
    public int mTotalTs;                //当前M3U8的总分片
    public int mCurTs;                  //当前M3U8已缓存的分片
    public float mSpeed;                //当前下载速度, getSpeedString 函数可以将速度格式化
    public float mPercent;              //当前下载百分比, 0 ~ 100,是浮点数
    public long mDownloadSize;          //已下载大小, getDownloadSizeString 函数可以将大小格式化
    public long mTotalSize;             //文件总大小, M3U8文件无法准确获知
    public String mFileHash;            //文件名的md5
    public String mSaveDir;             //保存视频文件的文件目录名
    public boolean mIsCompleted;        //是否下载完成
    public boolean mIsInDatabase;       //是否存到数据库中
    public long mLastUpdateTime;        //上一次更新数据库的时间
    private String fileName;            //文件名
    public String mFilePath;            //文件完整路径(包括文件名)
    public String mM3u8FilePath;            //m3u8文件完整路径(括文件名)
    public boolean mPaused;
    public String mName;//用于显示的名称

    public boolean merged;
    public String header;//下载时带的请求头
    public String sourceUrl;//源网页地址
    public String suffix;//文件名后缀
    public int newFile;//1新文件，0不新
    public String videoLengthStr;//视频时长
    public long videoLength;//视频时长
    public long estimateSize;//m3u8预估的总大小（第一个ts的大小乘以总的ts个数）
    public boolean privateFile;//隐私文件,false:下载到私有目录,true:下载到公有目录
    public String groupId;

    // 分辨率（质量）
    public String quality;
    //序号，下载后进行排序
    public int sort;
    //同名的话覆盖写入
    public boolean overwrite=true;
    //下载分组：用于区分是网站下载还是ins下载的
    public String downloadGroup;
    // post/get
    public String method;
    //sort为空时，用sort2排序
    public int sort2;
    public long createTime;
   
    public boolean isSelect;//是否选中
   
    public boolean isDownloadSuc;//下载成功
   
    public String contentType;
   

    public Exception exception;



    public VideoTaskItem(String url) {
        mUrl = url;
    }

    public VideoTaskItem(String url, String coverUrl,String name,String sourceUrl,String quality,Map<String,String> header) {
        mUrl = url;
        mCoverUrl = coverUrl;
        mName = name;
        this.header=VideoDownloadUtils.mapToJsonString(header);
        this.sourceUrl=sourceUrl;
        this.quality = quality;
    }


    public String getUrl() {
        return mUrl;
    }

    public void setCoverUrl(String coverUrl) { mCoverUrl = coverUrl; }

    public String getCoverUrl() { return mCoverUrl; }

    public void setCoverPath(String coverPath) { mCoverPath = coverPath; }

    public String getCoverPath() { return mCoverPath; }

    public void setDownloadCreateTime(long time) {
        mDownloadCreateTime = time;
    }

    public long getDownloadCreateTime() {
        return mDownloadCreateTime;
    }

    public void setTaskState(int state) {
        mTaskState = state;
    }

    public int getTaskState() {
        return mTaskState;
    }

    public void setMimeType(String mimeType) {
        mMimeType = mimeType;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setFinalUrl(String finalUrl) {
        mFinalUrl = finalUrl;
    }

    public String getFinalUrl() {
        return mFinalUrl;
    }

    public void setErrorCode(int errorCode) {
        mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    public void setVideoType(int type) {
        mVideoType = type;
    }

    public int getVideoType() {
        return mVideoType;
    }

    public void setM3U8(M3U8 m3u8) {
        mM3U8 = m3u8;
    }

    public M3U8 getM3U8() {
        return mM3U8;
    }

    public void setTotalTs(int count) {
        mTotalTs = count;
    }

    public int getTotalTs() {
        return mTotalTs;
    }

    public void setCurTs(int count) {
        mCurTs = count;
    }

    public int getCurTs() {
        return mCurTs;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setSpeed(float speed) {
        mSpeed = speed;
    }

    public float getSpeed() {
        return mSpeed;
    }

    public String getSpeedString() {
        return VideoDownloadUtils.getSizeStr((long) mSpeed) + "/s";
    }

    public void setPercent(float percent) {
        mPercent = percent;
    }

    public float getPercent() {
        return mPercent;
    }

    public String getPercentString() {
        return VideoDownloadUtils.getPercent(mPercent);
    }

    public void setDownloadSize(long size) {
        mDownloadSize = size;
    }

    public long getDownloadSize() {
        return mDownloadSize;
    }

    public String getDownloadSizeString() {
        return VideoDownloadUtils.getSizeStr(mDownloadSize);
    }

    public void setTotalSize(long size) {
        mTotalSize = size;
    }

    public long getTotalSize() {
        return mTotalSize;
    }

    public void setFileHash(String md5) {
        mFileHash = md5;
    }

    public String getFileHash() {
        return mFileHash;
    }

    public void setSaveDir(String path) {
        mSaveDir = path;
    }

    public String getSaveDir() {
        return mSaveDir;
    }

    public void setIsCompleted(boolean completed) {
        mIsCompleted = completed;
    }

    public boolean isCompleted() {
        return mIsCompleted;
    }

    public void setIsInDatabase(boolean in) {
        mIsInDatabase = in;
    }

    public boolean isInDatabase() {
        return mIsInDatabase;
    }

    public void setLastUpdateTime(long time) {
        mLastUpdateTime = time;
    }

    public long getLastUpdateTime() {
        return mLastUpdateTime;
    }

    public void setFilePath(String path) {
        mFilePath = path;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public void setPaused(boolean paused) {
        mPaused = paused;
    }

    public boolean isPaused() {
        return mPaused;
    }

    public boolean isRunningTask() {
        return mTaskState == VideoTaskState.DOWNLOADING;
    }

    public boolean isPendingTask() {
        return mTaskState == VideoTaskState.PENDING;
    }

    public boolean isErrorState() {
        return mTaskState == VideoTaskState.ERROR;
    }
    public boolean isPauseState() {
        return mTaskState == VideoTaskState.PAUSE||mPaused;
    }

    public boolean isSuccessState() {
        return mTaskState == VideoTaskState.SUCCESS;
    }

    public boolean isInterruptTask() {
        return mTaskState == VideoTaskState.PAUSE || mTaskState == VideoTaskState.ERROR;
    }

    public boolean isInitialTask() {
        return mTaskState == VideoTaskState.DEFAULT;
    }

    public boolean isHlsType() {
        return mVideoType == Video.Type.HLS_TYPE;
    }

    public String getName() {
        return mName;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * 设置文件名并过滤特殊字符
     * @param fileName
     */
    public void setFileName(String fileName) {
        String name = filterFileName(fileName);
        if (name != null) {
            if (name.contains(".m3u8")) {
            /*
              如果不是m3u8类型的文件，文件名不能包含.m3u8，否则exo播放器会认为是m3u8从而播放失败
              这个项目下载路径在公共目录，默认保存为MP4，所以需要过滤.m3u8文件名
             */
                name = name.replace(".m3u8", "_");
            }
        }

        this.fileName = name;
    }

    /**
     * 过滤一些不合法的文件名
     */
    public String filterFileName(String fileName){
        if(!TextUtils.isEmpty(fileName)){
            String specialChars = "/:<>?%#|'\"*\\";

            // 遍历文件名中的每个字符，检查是否为特殊字符
            for (int i = 0; i < fileName.length(); i++) {
                char c = fileName.charAt(i);
                if (specialChars.indexOf(c) != -1) {
                    // 如果找到特殊字符，则将其替换为下划线 "_"
                    fileName = fileName.replace(String.valueOf(c), "_");
                }
            }
            fileName = fileName.trim();
        }

        return fileName;

    }

    public void reset() {
        mTaskState = VideoTaskState.DEFAULT;
        mDownloadCreateTime = 0L;
        mMimeType = null;
        mErrorCode = 0;
        mVideoType = Video.Type.DEFAULT;
        mTaskState = VideoTaskState.DEFAULT;
        mM3U8 = null;
        mSpeed = 0.0f;
        mPercent = 0.0f;
        mDownloadSize = 0;
        mTotalSize = 0;
        fileName = null;
        mFilePath = null;
        mM3u8FilePath = null;
        mCoverUrl = null;
        mCoverPath = null;
    }
    public void startOver() {
        mTaskState = VideoTaskState.DEFAULT;
        mDownloadCreateTime = 0L;
        mMimeType = null;
        mErrorCode = 0;
        mVideoType = Video.Type.DEFAULT;
        mTaskState = VideoTaskState.DEFAULT;
        mM3U8 = null;
        mSpeed = 0.0f;
        mPercent = 0.0f;
        mDownloadSize = 0;
        mTotalSize = 0;
        fileName = null;
        mFilePath = null;
        mM3u8FilePath = null;
        mIsCompleted=false;
    }

    @Override
    public Object clone() {
        VideoTaskItem taskItem = new VideoTaskItem(mUrl);
        taskItem.setDownloadCreateTime(mDownloadCreateTime);
        taskItem.setTaskState(mTaskState);
        taskItem.setMimeType(mMimeType);
        taskItem.setErrorCode(mErrorCode);
        taskItem.setVideoType(mVideoType);
        taskItem.setPercent(mPercent);
        taskItem.setDownloadSize(mDownloadSize);
        taskItem.setSpeed(mSpeed);
        taskItem.setTotalSize(mTotalSize);
        taskItem.setFileHash(mFileHash);
        taskItem.setFilePath(mFilePath);
        taskItem.mM3u8FilePath=mM3u8FilePath;
        taskItem.fileName=fileName;
        taskItem.setCoverUrl(mCoverUrl);
        taskItem.setCoverPath(mCoverPath);
        taskItem.mName=mName;
        taskItem.sourceUrl=sourceUrl;
        taskItem.suffix=suffix;
        taskItem.videoLength=videoLength;
        taskItem.newFile=newFile;
        taskItem.mLastUpdateTime=mLastUpdateTime;
        taskItem.sort=sort;
        return taskItem;
    }




    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof VideoTaskItem) {
            String objUrl = ((VideoTaskItem) obj).getUrl();
            String objSourceUrl = ((VideoTaskItem) obj).sourceUrl;
            String objQuality = ((VideoTaskItem) obj).quality;


            if (TextUtils.isEmpty(sourceUrl)||TextUtils.isEmpty(quality)) {
                // 根据视频url判断
                if (mUrl.equals(objUrl)) {
                    return true;
                }
            } else if (sourceUrl.equals(objSourceUrl) && quality.equals(objQuality)) {
                // 根据网页链接和分辨率判断
                if (getUrl().contains("?") && objUrl.contains("?")) {
                    // 视频url里面包含参数的判断视频不带参数地址是否相同
                    if (getUrl().substring(0, getUrl().indexOf("?")).equals(objUrl.substring(0, objUrl.indexOf("?")))) {
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return "VideoTaskItem[Url=" + mUrl +
                ", Type=" + mVideoType +
                ", Percent=" + mPercent +
                ", DownloadSize=" + mDownloadSize +
                ", State=" + mTaskState +
                ", fileName=" + fileName +
                ", LocalFile=" + mFilePath +
                ", CoverUrl=" + mCoverUrl +
                ", CoverPath=" + mCoverPath +
                "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mUrl);
        dest.writeString(this.mCoverUrl);
        dest.writeString(this.mCoverPath);
        dest.writeLong(this.mDownloadCreateTime);
        dest.writeInt(this.mTaskState);
        dest.writeString(this.mMimeType);
        dest.writeString(this.mFinalUrl);
        dest.writeInt(this.mErrorCode);
        dest.writeInt(this.mVideoType);
        dest.writeParcelable(this.mM3U8, flags);
        dest.writeInt(this.mTotalTs);
        dest.writeInt(this.mCurTs);
        dest.writeFloat(this.mSpeed);
        dest.writeFloat(this.mPercent);
        dest.writeLong(this.mDownloadSize);
        dest.writeLong(this.mTotalSize);
        dest.writeString(this.mFileHash);
        dest.writeString(this.mSaveDir);
        dest.writeByte(this.mIsCompleted ? (byte) 1 : (byte) 0);
        dest.writeByte(this.mIsInDatabase ? (byte) 1 : (byte) 0);
        dest.writeLong(this.mLastUpdateTime);
        dest.writeString(this.fileName);
        dest.writeString(this.mFilePath);
        dest.writeString(this.mM3u8FilePath);
        dest.writeByte(this.mPaused ? (byte) 1 : (byte) 0);
        dest.writeString(this.mName);
        dest.writeString(this.sourceUrl);
        dest.writeString(this.quality);
        dest.writeString(this.suffix);
        dest.writeLong(this.videoLength);
        dest.writeInt(this.newFile);
        dest.writeByte(this.isSelect ? (byte) 1 : (byte) 0);
    }

    public void readFromParcel(Parcel source) {
        this.mUrl = source.readString();
        this.mCoverUrl = source.readString();
        this.mCoverPath = source.readString();
        this.mDownloadCreateTime = source.readLong();
        this.mTaskState = source.readInt();
        this.mMimeType = source.readString();
        this.mFinalUrl = source.readString();
        this.mErrorCode = source.readInt();
        this.mVideoType = source.readInt();
        this.mM3U8 = source.readParcelable(M3U8.class.getClassLoader());
        this.mTotalTs = source.readInt();
        this.mCurTs = source.readInt();
        this.mSpeed = source.readFloat();
        this.mPercent = source.readFloat();
        this.mDownloadSize = source.readLong();
        this.mTotalSize = source.readLong();
        this.mFileHash = source.readString();
        this.mSaveDir = source.readString();
        this.mIsCompleted = source.readByte() != 0;
        this.mIsInDatabase = source.readByte() != 0;
        this.mLastUpdateTime = source.readLong();
        this.fileName = source.readString();
        this.mFilePath = source.readString();
        this.mM3u8FilePath = source.readString();
        this.mPaused = source.readByte() != 0;
        this.mName = source.readString();
        this.sourceUrl = source.readString();
        this.quality = source.readString();
        this.suffix = source.readString();
        this.videoLength = source.readLong();
        this.newFile = source.readInt();
        this.isSelect = source.readByte() != 0;
    }

    protected VideoTaskItem(Parcel in) {
        this.mUrl = in.readString();
        this.mCoverUrl = in.readString();
        this.mCoverPath = in.readString();
        this.mDownloadCreateTime = in.readLong();
        this.mTaskState = in.readInt();
        this.mMimeType = in.readString();
        this.mFinalUrl = in.readString();
        this.mErrorCode = in.readInt();
        this.mVideoType = in.readInt();
        this.mM3U8 = in.readParcelable(M3U8.class.getClassLoader());
        this.mTotalTs = in.readInt();
        this.mCurTs = in.readInt();
        this.mSpeed = in.readFloat();
        this.mPercent = in.readFloat();
        this.mDownloadSize = in.readLong();
        this.mTotalSize = in.readLong();
        this.mFileHash = in.readString();
        this.mSaveDir = in.readString();
        this.mIsCompleted = in.readByte() != 0;
        this.mIsInDatabase = in.readByte() != 0;
        this.mLastUpdateTime = in.readLong();
        this.fileName = in.readString();
        this.mFilePath = in.readString();
        this.mM3u8FilePath = in.readString();
        this.mPaused = in.readByte() != 0;
        this.mName = in.readString();
        this.sourceUrl = in.readString();
        this.quality = in.readString();
        this.suffix = in.readString();
        this.videoLength = in.readLong();
        this.newFile = in.readInt();
        this.isSelect = in.readByte() != 0;
    }

    public static final Creator<VideoTaskItem> CREATOR = new Creator<VideoTaskItem>() {
        @Override
        public VideoTaskItem createFromParcel(Parcel source) {
            return new VideoTaskItem(source);
        }

        @Override
        public VideoTaskItem[] newArray(int size) {
            return new VideoTaskItem[size];
        }
    };


}
