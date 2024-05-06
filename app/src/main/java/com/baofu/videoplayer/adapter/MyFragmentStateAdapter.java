package com.baofu.videoplayer.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.baofu.videoplayer.fragment.PlayerFragement;

import java.util.ArrayList;
import java.util.List;

public class MyFragmentStateAdapter extends FragmentStateAdapter {
    private List<String> mTitles = new ArrayList();
    public void setData( List<String> list){
        if (list == null || list.size() <= 0) return;
        mTitles.clear();
        mTitles.addAll(list);
        notifyDataSetChanged();
    }

    public MyFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return PlayerFragement.newInstance(mTitles.get(position),position);
    }

    @Override
    public int getItemCount() {
        return mTitles==null?0:mTitles.size();
    }
}
