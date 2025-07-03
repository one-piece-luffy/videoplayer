package cn.mahua.av.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import cn.mahua.av.R;
import cn.mahua.av.SpeedInterface;
import cn.mahua.av.listener.OnSpeedClickListener;

public class SpeedBottomSheetDialog extends BottomSheetDialog {
    List<String> playList = new ArrayList();
    String speed;
    OnSpeedClickListener listener;
    RecyclerView recyclerView;
    Context mContext;

    public SpeedBottomSheetDialog(@NonNull Context context, String speed, OnSpeedClickListener listener) {
        super(context, R.style.AppBottomSheetDialogTheme);
        this.speed = speed;
        mContext = context;
        this.listener = listener;
        playList.add(SpeedInterface.sp4_0);
        playList.add(SpeedInterface.sp3_0);
        playList.add(SpeedInterface.sp2_0);
        playList.add(SpeedInterface.sp1_75);
        playList.add(SpeedInterface.sp1_50);
        playList.add(SpeedInterface.sp1_25);
        playList.add(SpeedInterface.sp1_0);
        playList.add(SpeedInterface.sp0_75);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.av_dialog_speed_list2);

        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);

            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);

            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        setCanceledOnTouchOutside(true);

        recyclerView = findViewById(R.id.rvSelectWorks);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(linearLayoutManager);
        MyAdapter adapter = new MyAdapter(playList);
        recyclerView.setAdapter(adapter);
    }

    class MyAdapter extends RecyclerView.Adapter {
        public List<String> list;

        public MyAdapter(List<String> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.av_item_dialog_speed_list2, parent, false);
            return new SpeedViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            try {
                String item = list.get(position);
                SpeedViewHolder viewHolder = (SpeedViewHolder) holder;
                float curSpeed = Float.parseFloat(speed);
                float itemSpeed = Float.parseFloat(item);
                viewHolder.tv.setText(item + "x");
                if (curSpeed == itemSpeed) {
                    viewHolder.tv.setTextColor(ContextCompat.getColor(mContext, R.color.av_speed_color));
                    viewHolder.ivCheck.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.tv.setTextColor(ContextCompat.getColor(mContext, R.color.black));
                    viewHolder.ivCheck.setVisibility(View.GONE);
                }
                holder.itemView.setOnClickListener(v -> {
                    speed = list.get(position);
                    if (listener != null) {
                        listener.onSpeedClick(speed);
                    }
                    notifyDataSetChanged();
                    dismiss();
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
        public ImageView ivCheck;

        public SpeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv);
            ivCheck = itemView.findViewById(R.id.iv_check);
        }
    }
}