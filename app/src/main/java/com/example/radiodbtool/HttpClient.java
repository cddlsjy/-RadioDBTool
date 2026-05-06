package com.example.radiodbtool;

import okhttp3.OkHttpClient;

public class HttpClient {
    private static final OkHttpClient clientInstance = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public static OkHttpClient getInstance() {
        return clientInstance;
    }

    private HttpClient() {
    }
}
