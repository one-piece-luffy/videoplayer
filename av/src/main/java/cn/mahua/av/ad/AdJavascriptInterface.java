package cn.mahua.av.ad;

import android.annotation.SuppressLint;
import android.util.Log;
import android.webkit.JavascriptInterface;


public class AdJavascriptInterface {

    @SuppressWarnings("unused")
    @SuppressLint("JavascriptInterface")
    @JavascriptInterface
    public void onClickImg(String imageUrl) {
        //根据URL查看大图逻辑
        Log.e(getClass().getName(),imageUrl);
    }
}
