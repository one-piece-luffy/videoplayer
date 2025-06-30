package com.baofu.videoplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;
import android.util.Log;

import com.baofu.base.utils.CommonUtils;
import com.baofu.cache.downloader.model.VideoTaskItem;
import com.baofu.cache.downloader.rules.CacheDownloadManager;
import com.baofu.videoplayer.adapter.MyFragmentStateAdapter;
import com.baofu.videoplayer.utils.Appconstants;
import com.jeffmony.videocache.PlayerProgressListenerManager;
import com.jeffmony.videocache.listener.IPlayerProgressListener;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerActivity extends AppCompatActivity {
    ViewPager2 viewPager;
    MyFragmentStateAdapter adapter;
    String TAG="ViewPagerActivity";
    int lastpostion;

    IPlayerProgressListener iPlayerProgressListener=new IPlayerProgressListener() {
        @Override
        public void onTaskFirstTsDownload(String filename) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(isDestroyed()||isFinishing()){
                        return;
                    }
                    CommonUtils.showToast("task 第一个ts下载完成:"+filename);
                    Log.e("MainActivity","task 第一个ts下载完成:"+filename);
                }
            });
        }

        @Override
        public void onPlayerFirstTsDownload(String filename) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(isDestroyed()||isFinishing()){
                        return;
                    }
                    CommonUtils.showToast("播放器 第一个ts下载完成:"+filename);
                    Log.e(TAG,"player 第一个ts下载完成:"+filename);
                }
            });

        }

        @Override
        public void onM3U8ParsedFailed(String error) {
            CommonUtils.showToast("m3u8解析失败:"+error);
            Log.e(TAG,"m3u8解析失败:"+error);
        }

        @Override
        public void playerCacheLog(String log) {
            Log.e("===asdf",log);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pager);

        viewPager = findViewById(R.id.pager);
        List<MyModel> list = new ArrayList<>();
        list.add(new MyModel("镜双城",Appconstants.jsc));
        list.add(new MyModel("海贼王",Appconstants.hzw));
        list.add(new MyModel("画江湖",Appconstants.huajianghu));
        list.add(new MyModel("师兄啊师兄",Appconstants.shixiong));
        list.add(new MyModel("凡人修仙",Appconstants.fanren));
        adapter = new MyFragmentStateAdapter(this);
        viewPager.setAdapter(adapter);
        adapter.setData(list);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                Log.e("asdf","position:"+position+" last:"+lastpostion);

                if(position==lastpostion){
                    return;
                }
                lastpostion=position;
                startCache(position);

            }
        });
        PlayerProgressListenerManager.getInstance().setListener(iPlayerProgressListener);
        startCache(0);
    }

    private void startCache(int position){
        List<MyModel> list=adapter.getData();
        CacheDownloadManager.getInstance().curPlayUrl= list.get(position).url;
        CacheDownloadManager.getInstance().pauseAllDownloadTasks();
        List<VideoTaskItem> cacheList = getCacheList(position);
        if (cacheList != null) {
            for (int i = 0; i < cacheList.size(); i++) {
                VideoTaskItem item = cacheList.get(i);
                Log.e("asdf","开始下载:"+item.mName);
                CacheDownloadManager.getInstance().startDownload(item);
            }

        }
    }

    public List<VideoTaskItem> getCacheList(int index) {
        List<VideoTaskItem> result = new ArrayList<>();
        List<MyModel> list = adapter.getData();
        if (list == null || index >= list.size()) {
            return null;
        }
        int cacheCount = 1;
        for (int i = index + 1; i < list.size(); i++) {
            MyModel model=list.get(i);
            if (result.size() < cacheCount) {
                VideoTaskItem item=new VideoTaskItem(model.url);
                item.mName=model.name;
                item.overwrite=true;
                result.add(item);
            } else {
                break;
            }
        }
        return result;

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CacheDownloadManager.getInstance().pauseAllDownloadTasks();
        PlayerProgressListenerManager.getInstance().setListener(null);
    }
}