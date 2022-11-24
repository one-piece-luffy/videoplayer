package cn.mahua.av.widget.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.mahua.av.R;
import cn.mahua.av.SpeedInterface;
import cn.mahua.av.listener.OnSpeedClickListener;

public class SpeedDialog extends Dialog {
    List<String> playList = new ArrayList();
    String speed;
    OnSpeedClickListener listener;
    RecyclerView recyclerView;
    Context mContext;

    public SpeedDialog(@NonNull Context context, String speed, OnSpeedClickListener listener) {
        super(context, R.style.PlayListDialogStyle);
        this.speed = speed;
        mContext = context;
        this.listener = listener;
        playList.add(SpeedInterface.sp0_75);
        playList.add(SpeedInterface.sp1_0);
        playList.add(SpeedInterface.sp1_25);
        playList.add(SpeedInterface.sp1_50);
        playList.add(SpeedInterface.sp1_75);
        playList.add(SpeedInterface.sp2_0);
        playList.add(SpeedInterface.sp3_0);
        playList.add(SpeedInterface.sp4_0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.av_dialog_speed_list);
        Window window = this.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = getContext().getResources().getDisplayMetrics().widthPixels * 8 / 10;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.CENTER_VERTICAL;
        window.setAttributes(lp);

        setCanceledOnTouchOutside(true);

        recyclerView = findViewById(R.id.rvSelectWorks);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        recyclerView.setLayoutManager(linearLayoutManager);
        MyAdapter adapter = new MyAdapter(playList);
        recyclerView.setAdapter(adapter);
        recyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollCurIndex(recyclerView, adapter.curIndex);
            }
        },200);
    }

    class MyAdapter extends RecyclerView.Adapter {

        public List<String> list;
        public int curIndex;

        public MyAdapter(List<String> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.av_item_dialog_speed_list, parent, false);
            SpeedViewHolder viewHolder = new SpeedViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            try {
                String item = list.get(position);
                SpeedViewHolder viewHolder = (SpeedViewHolder) holder;
                float curSpeed = Float.parseFloat(speed);
                float itemSpeed = Float.parseFloat(item);
                if (curSpeed == itemSpeed) {
                    curIndex=position;
                    viewHolder.tv.setTextColor(0xffFF7000);
                } else {
                    viewHolder.tv.setTextColor(0xffffffff);
                }
                viewHolder.tv.setText(item);
                final int temp = position;
                viewHolder.tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        speed = list.get(temp);
                        listener.onSpeedClick(speed);
                        notifyDataSetChanged();
                        dismiss();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return list == null ? 0 : list.size();
        }
    }

    class SpeedViewHolder extends RecyclerView.ViewHolder {
        public TextView tv;

        public SpeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv);
        }
    }

    /**
     * 置顶当前播放的集数
     *
     * @param rvLastest
     */
    private void scrollCurIndex(RecyclerView rvLastest,int index) {
        if (rvLastest == null)
            return;
        if(index<0){
            index=0;
        }
        rvLastest.scrollToPosition(index);
        LinearLayoutManager mLayoutManager = (LinearLayoutManager) rvLastest.getLayoutManager();
        mLayoutManager.scrollToPositionWithOffset(index, 0);
    }

}
