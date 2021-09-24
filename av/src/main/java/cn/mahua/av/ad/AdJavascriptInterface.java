package cn.mahua.av.ad;

import android.annotation.SuppressLint;
import android.webkit.JavascriptInterface;

import com.blankj.utilcode.util.LogUtils;

public class AdJavascriptInterface {

    @SuppressWarnings("unused")
    @SuppressLint("JavascriptInterface")
    @JavascriptInterface
    public void onClickImg(String imageUrl) {
        //根据URL查看大图逻辑
        LogUtils.e(imageUrl);
    }
}
