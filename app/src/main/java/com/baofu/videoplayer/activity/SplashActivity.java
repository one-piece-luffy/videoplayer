package com.baofu.videoplayer.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.baofu.videoplayer.R;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.tvMain).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.tvList).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(SplashActivity.this, ViewPagerActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.tvDanmu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(SplashActivity.this, DanMuActivity.class);
                startActivity(intent);
            }
        });
    }
}