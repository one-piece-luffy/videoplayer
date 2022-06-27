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
package com.yc.video.config;


import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 *     @author yangchong
 *     blog  : https://github.com/yangchong211
 *     time  : 2018/1/29
 *     desc  : 视频信息实体类
 *     revise:
 * </pre>
 */
public class VideoInfoBean implements Parcelable {

    /**
     * 视频的标题
     */
    private String title;
    /**
     * 播放的视频地址
     */
    private String videoUrl;
    /**
     * 请求header
     */
    private Map<String, String> headers;
    /**
     * 视频封面
     */
    private String cover;
    /**
     * 视频时长
     */
    private long length;
    /**
     * 清晰度等级
     */
    private String grade;
    /**
     * 270P、480P、720P、1080P、4K ...
     */
    private String p;

    public VideoInfoBean(String title, String cover, String url) {
        this.title = title;
        this.videoUrl = url;
        this.cover = cover;
    }

    public VideoInfoBean(String title ,String grade, String p, String videoUrl) {
        this.title = title;
        this.grade = grade;
        this.p = p;
        this.videoUrl = videoUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getP() {
        return p;
    }

    public void setP(String p) {
        this.p = p;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.title);
        dest.writeString(this.videoUrl);
        dest.writeInt(this.headers.size());
        for (Map.Entry<String, String> entry : this.headers.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
        dest.writeString(this.cover);
        dest.writeLong(this.length);
        dest.writeString(this.grade);
        dest.writeString(this.p);
    }

    public void readFromParcel(Parcel source) {
        this.title = source.readString();
        this.videoUrl = source.readString();
        int headersSize = source.readInt();
        this.headers = new HashMap<String, String>(headersSize);
        for (int i = 0; i < headersSize; i++) {
            String key = source.readString();
            String value = source.readString();
            this.headers.put(key, value);
        }
        this.cover = source.readString();
        this.length = source.readLong();
        this.grade = source.readString();
        this.p = source.readString();
    }

    protected VideoInfoBean(Parcel in) {
        this.title = in.readString();
        this.videoUrl = in.readString();
        int headersSize = in.readInt();
        this.headers = new HashMap<String, String>(headersSize);
        for (int i = 0; i < headersSize; i++) {
            String key = in.readString();
            String value = in.readString();
            this.headers.put(key, value);
        }
        this.cover = in.readString();
        this.length = in.readLong();
        this.grade = in.readString();
        this.p = in.readString();
    }

    public static final Parcelable.Creator<VideoInfoBean> CREATOR = new Parcelable.Creator<VideoInfoBean>() {
        @Override
        public VideoInfoBean createFromParcel(Parcel source) {
            return new VideoInfoBean(source);
        }

        @Override
        public VideoInfoBean[] newArray(int size) {
            return new VideoInfoBean[size];
        }
    };
}
