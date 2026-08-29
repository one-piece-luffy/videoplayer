package com.jeffmony.videocache.utils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;

public class OkHttpUtil {

    private final OkHttpClient mOkHttpClient;

    private static final OkHttpUtil sInstance = new OkHttpUtil();

    public static OkHttpUtil getInstance() {
        return sInstance;
    }
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
    /**
     * ✅ 创建 Call 对象（支持取消）
     */
    public Call createCall(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return mOkHttpClient.newCall(builder.build());
    }

    /**
     * 同步请求
     */
    public Response requestSync(String url, Map<String, String> headers) throws java.io.IOException {
        return createCall(url, headers).execute();
    }
}