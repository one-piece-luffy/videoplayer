package cn.mahua.av.widget;

import android.annotation.SuppressLint;
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
import androidx.recyclerview.widget.GridLayoutManager;
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

        resetWidth();

        setCanceledOnTouchOutside(true);

        recyclerView = findViewById(R.id.rv);

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        MyAdapter adapter = new MyAdapter(playList);
        recyclerView.setAdapter(adapter);

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
                    viewHolder.tv.setTextColor(0xffffffff);
                    viewHolder.root.setBackgroundResource(R.drawable.bg_dialog_speed_select);
                } else {
                    viewHolder.tv.setTextColor(0xffffffff);
                    viewHolder.root.setBackgroundResource(R.drawable.bg_dialog_speed);
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
        public ViewGroup root;

        public SpeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv);
            root = itemView.findViewById(R.id.root);
        }
    }

    public void resetWidth() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = getContext().getResources().getDisplayMetrics().widthPixels * 4 / 10;
        params.height = getContext().getResources().getDisplayMetrics().heightPixels;
        getWindow().setAttributes(params);

        Window window = getWindow();
        if (window != null) {
            // 设置弹窗在底部
            window.setGravity(Gravity.END);

            // 关键1：取消背景变暗（遮罩透明度设为 0）
            window.setDimAmount(0.0f);
            // 关键2：设置 Dialog 窗口背景为透明（避免默认黑色背景）
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
