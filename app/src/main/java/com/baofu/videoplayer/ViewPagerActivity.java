package com.baofu.videoplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;

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
        List<String> list = new ArrayList<>();
        list.add(Appconstants.jsc);
        list.add(Appconstants.hzw);
        list.add(Appconstants.huajianghu);
        list.add(Appconstants.shixiong);
        list.add(Appconstants.fanren);
        adapter = new MyFragmentStateAdapter(this);
        viewPager.setAdapter(adapter);
        adapter.setData(list);
    }
}