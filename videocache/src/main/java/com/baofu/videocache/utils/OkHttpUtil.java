package com.baofu.videocache.utils;


import android.text.TextUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;


/**
 * 基于OkHttp
 */

public class OkHttpUtil {
    private OkHttpClient mOkHttpClient;
    private static OkHttpUtil mInstance;
    public static final String URL_INVALID="Expected URL scheme 'http' or 'https' but no colon was found ";
    public static final String NO_SPACE="No space ";


    /**
     * @param url        下载链接
     * @param callback   回调
     * @throws IOException
     */
    public void request(String url, Map<String,String> header, Callback callback) throws IOException {
        // 创建一个Request
        Request.Builder builder = new Request.Builder()
                .url(url)
//                .addHeader("Connection","close")
                ;
        if (header != null) {
            Iterator<Map.Entry<String, String>> it = header.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                if(entry==null)
                    continue;
                String key=entry.getKey();
                String value=entry.getValue();
                if(TextUtils.isEmpty(key)||TextUtils.isEmpty(value)){
                    continue;
                }
                builder.addHeader(key,value);
            }

        }
        Request request=builder.build();
        doAsync(request, callback);
    }
    public Response requestSync(String url,Map<String,String> header) throws IOException {
        // 创建一个Request
        Request.Builder builder = new Request.Builder()
//                .addHeader("Connection","close")
                .url(url)
                ;
        if (header != null) {
            Iterator<Map.Entry<String, String>> it = header.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                if(entry==null)
                    continue;
                String key=entry.getKey();
                String value=entry.getValue();
                if(TextUtils.isEmpty(key)||TextUtils.isEmpty(value)){
                    continue;
                }
                builder.addHeader(key,value);
            }

        }
        Request request=builder.build();
        return doSync(request);
    }



    /**
     * 异步请求
     */
    private void doAsync(Request request, Callback callback) throws IOException {
        //创建请求会话
        Call call = mOkHttpClient.newCall(request);
        //同步执行会话请求
        call.enqueue(callback);
    }


    /**
     * 同步请求
     */
    private Response doSync(Request request) throws IOException {

        //创建请求会话
        Call call = mOkHttpClient.newCall(request);
        //同步执行会话请求
        return call.execute();
    }


    /**
     * @return HttpUtil实例对象
     */
    public static OkHttpUtil getInstance() {
        if (null == mInstance) {
            synchronized (OkHttpClient.class) {
                if (null == mInstance) {
                    mInstance = new OkHttpUtil();
                }
            }
        }
        return mInstance;
    }

    /**
     * 构造方法,配置OkHttpClient
     */
    public OkHttpUtil() {
        //创建okHttpClient对象
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);
//        builder.connectionPool(new ConnectionPool(32,5,TimeUnit.MINUTES));
        builder.addInterceptor(new RedirectInterceptor());
        builder.sslSocketFactory(SSLUtil.getInstance().getSSLSocketFactory(), SSLUtil.getInstance().getTrustManager());
        builder.hostnameVerifier(SSLUtil.getInstance().getHostnameVerifier());
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .cipherSuites(
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256)
                .build();
        builder.connectionSpecs(Collections.singletonList(spec));
        mOkHttpClient = builder.build();
    }



}
