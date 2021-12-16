package cn.mahua.av.ad;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AlertDialog;

public class AdWebView extends WebView {

    //替换img属性
    private static final String imgJs = "<script type='text/javascript'> \nwindow.onload = function()\n{var $img = document.getElementsByTagName('img');for(var p in  $img){$img[p].style.width = '100%'; $img[p].style.height ='100%'}}\n</script>";
    //点击查看
    private static final String jsimg = "function()\n { var imgs = document.getElementsByTagName(\"img\");for(var i = 0; i < imgs.length; i++){  imgs[i].onclick = function()\n{AdJavascriptInterface.onClickImg(this.src);}}}";


    public AdWebView(Context context) {
        this(context, null);
    }

    public AdWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initSetting();
    }

    private void initSetting() {
        addJavascriptInterface(new AdJavascriptInterface(), "AdJavascriptInterface");
        setWebViewClient(new MyWebViewClient());

        WebSettings webSettings = getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setLoadsImagesAutomatically(true); //支持自动加载图片
    }

    public void loadHtmlBody(String html) {
        String data = AdBaseHtml.getHtml(html);
        data = imgJs + data;
        loadData(data, "text/html", "UTF-8");
    }


    /**
     *
     */
    public void onDestroy() {
        removeJavascriptInterface("AdJavascriptInterface");
        setWebViewClient(new MyWebViewClient());
        WebSettings webSettings = getSettings();
        webSettings.setJavaScriptEnabled(false);
        loadData("", "text/html", "UTF-8");
    }

    class MyWebViewClient extends WebViewClient{
        @Override
        public void onPageFinished(WebView webView, String s) {
            webView.loadUrl("javascript:(" + jsimg + ")()");
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                Log.e(getClass().getName(),url);
                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                getContext().startActivity(intent);
            }catch (Exception e){
                e.printStackTrace();
            }

            return true;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if(view==null||view.getContext()==null){
                return;
            }
            try {
                Activity activity = (Activity) view.getContext();
                if (activity.isFinishing()) {
                    return;
                }
            } catch (Exception e) {

            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
            AlertDialog dialog = null;
            String message = "SSL Certificate error.";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED:
                    message = "The certificate authority is not trusted.";
                    break;
                case SslError.SSL_EXPIRED:
                    message = "The certificate has expired.";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message = "The certificate Hostname mismatch.";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message = "The certificate is not yet valid.";
                    break;
            }
            message += " Do you want to continue anyway?";

            builder.setTitle("SSL Certificate Error");
            builder.setMessage(message);
            builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(view==null||view.getContext()==null){
                        return;
                    }
                    if (handler != null) {
                        handler.proceed();
                    }
                    if (dialog != null) {
                        dialog.dismiss();
                    }

                }
            });
            builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(view==null||view.getContext()==null){
                        return;
                    }
                    if (handler != null) {
                        handler.proceed();
                    }
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                }
            });
            dialog = builder.create();
            dialog.show();
        }
    }
}
