package com.baofu.videoplayer.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.baofu.videoplayer.MyModel;
import com.baofu.videoplayer.fragment.PlayerFragement;

import java.util.ArrayList;
import java.util.List;

public class MyFragmentStateAdapter extends FragmentStateAdapter {
    private List<MyModel> data = new ArrayList();
    public void setData( List<MyModel> list){
        if (list == null || list.size() <= 0) return;
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    public List<MyModel> getData() {
        return data;
    }

    public MyFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return PlayerFragement.newInstance(data.get(position),position);
    }

    @Override
    public int getItemCount() {
        return data==null?0:data.size();
    }
}
