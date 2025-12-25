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
import cn.mahua.av.utils.AvSharePreference;

public class SpeedDialog extends Dialog {
    List<String> playList = new ArrayList();
    String mSpeed;
    OnSpeedClickListener listener;
    //播放速度rv
    RecyclerView recyclerView;
    //长按倍速rv
    RecyclerView recyclerView2;
    TextView tvLongSpeed;
    Context mContext;
    ViewGroup normalLayout;
    ViewGroup secondLayout;

    public SpeedDialog(@NonNull Context context, String speed, OnSpeedClickListener listener) {
        super(context, R.style.PlayListDialogStyle);
        this.mSpeed = speed;
        mContext = context;
        this.listener = listener;
        playList.add(SpeedInterface.sp0_75);
        playList.add(SpeedInterface.sp1_0);
        playList.add(SpeedInterface.sp1_25);
        playList.add(SpeedInterface.sp1_50);
        playList.add(SpeedInterface.sp1_75);
        playList.add(SpeedInterface.sp2_0);
        playList.add(SpeedInterface.sp3_0);
        playList.add(SpeedInterface.sp3_5);
        playList.add(SpeedInterface.sp4_0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.av_dialog_speed_list);

        resetWidth();

        setCanceledOnTouchOutside(true);

        recyclerView = findViewById(R.id.rv);
        recyclerView2 = findViewById(R.id.rv2);
        tvLongSpeed = findViewById(R.id.tvLongSpeed);
        normalLayout = findViewById(R.id.normalLayout);
        secondLayout = findViewById(R.id.secondLayout);
        tvLongSpeed.setText("x"+AvSharePreference.getLongPressSpeed(getContext()));

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        MyAdapter adapter = new MyAdapter(playList,mSpeed,true,listener);
        recyclerView.setAdapter(adapter);

        MyAdapter adapter2 = new MyAdapter(playList,AvSharePreference.getLongPressSpeed(getContext())+"",false, new OnSpeedClickListener() {
            @Override
            public void onSpeedClick(String speed) {
                try {
                    AvSharePreference.saveLongPressSpeed(getContext(),speed);
                    tvLongSpeed.setText("x"+speed);

                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        });
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false);
        gridLayoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView2.setLayoutManager(gridLayoutManager);
        recyclerView2.setAdapter(adapter2);

        findViewById(R.id.longSpeedLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secondLayout.setVisibility(View.VISIBLE);
                normalLayout.setVisibility(View.GONE);
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secondLayout.setVisibility(View.GONE);
                normalLayout.setVisibility(View.VISIBLE);
            }
        });

    }

    class MyAdapter extends RecyclerView.Adapter {

        public List<String> list;
        public String speed;
        public int curIndex;
        //一级页面
        boolean firstPage;
        OnSpeedClickListener onSpeedClickListener;
        public MyAdapter(List<String> list,String speed,  boolean firstPage,  OnSpeedClickListener listener) {
            this.list = list;
            this.speed=speed;
            this.firstPage=firstPage;
            this.onSpeedClickListener=listener;
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
                        if(firstPage){
                            dismiss();
                        }
                        if(onSpeedClickListener!=null){
                            onSpeedClickListener.onSpeedClick(speed);
                        }

                        notifyDataSetChanged();
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
        params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 3.5 / 10);
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
