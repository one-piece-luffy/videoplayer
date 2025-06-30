package com.baofu.videoplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;
import android.util.Log;

import com.baofu.downloader.model.VideoTaskItem;
import com.baofu.downloader.rules.VideoDownloadManager;
import com.baofu.videoplayer.adapter.MyFragmentStateAdapter;
import com.baofu.videoplayer.utils.Appconstants;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerActivity extends AppCompatActivity {
    ViewPager2 viewPager;
    MyFragmentStateAdapter adapter;

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
                Log.e("asdf","position:"+position);
                VideoDownloadManager.getInstance().curPlayUrl= list.get(position).url;
                VideoDownloadManager.getInstance().pauseAllDownloadTasks();
                List<VideoTaskItem> cacheList = getCacheList(position);
                if (cacheList != null) {
                    for (int i = 0; i < cacheList.size(); i++) {
                        VideoTaskItem item = cacheList.get(i);
                        VideoDownloadManager.getInstance().startDownload(item);
                    }

                }

            }
        });
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
        VideoDownloadManager.getInstance().pauseAllDownloadTasks();
    }
}