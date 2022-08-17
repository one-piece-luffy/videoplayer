package cn.mahua.av.play;

import android.view.View;

public interface ControllerClickListener {
    void onClick(View view);
    void share();
    void next();
    void tryFix();
    void onSpeedClick(String speed);
    void onUserSeek(long position);
}
