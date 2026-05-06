package com.example.radiodbtool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.OkHttpClient;

public class Utils {

    public static int parseIntWithDefault(String number, int defaultVal) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static String sanitizeName(String str) {
        return str.replaceAll("\\W+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
    }

    public static boolean hasAnyConnection(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    public static String downloadFeed(OkHttpClient httpClient, Context ctx, String theURI, boolean forceUpdate, Map<String, String> dictParams) {
        Log.i("DOWN", "Url=" + theURI);
        if (!forceUpdate) {
            String cache = getCacheFile(ctx, theURI);
            if (cache != null) {
                return cache;
            }
        }
        Log.i("DOWN", "Url=" + theURI + " (not cached)");

        try {
            HttpUrl url = HttpUrl.parse(theURI);
            Request.Builder requestBuilder = new Request.Builder().url(url);

            if (dictParams != null) {
                MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
                Gson gson = new Gson();
                String json = gson.toJson(dictParams);
                RequestBody requestBody = RequestBody.create(jsonMediaType, json);
                requestBuilder.post(requestBody);
            } else {
                requestBuilder.get();
            }

            Request request = requestBuilder.build();
            Response response = httpClient.newCall(request).execute();

            String responseStr = response.body().string();

            if (!response.isSuccessful()) {
                Log.e("UTIL", "HTTP请求失败: URL=" + theURI + ", 状态码=" + response.code());
                return null;
            }

            writeFileCache(ctx, theURI, responseStr);
            return responseStr;
        } catch (java.net.SocketTimeoutException e) {
            Log.e("UTIL", "网络请求超时: URL=" + theURI);
        } catch (java.net.UnknownHostException e) {
            Log.e("UTIL", "DNS解析失败: URL=" + theURI);
        } catch (java.net.ConnectException e) {
            Log.e("UTIL", "连接失败: URL=" + theURI);
        } catch (java.io.IOException e) {
            Log.e("UTIL", "IO错误: URL=" + theURI);
        } catch (Exception e) {
            Log.e("UTIL", "downloadFeed() 未知错误: URL=" + theURI);
        }

        return null;
    }

    public static String downloadFeedFromServer(OkHttpClient httpClient, Context ctx, String server, String path, boolean useHttps) {
        String protocol = useHttps ? "https://" : "http://";
        String endpoint = protocol + server + "/" + path;
        return downloadFeed(httpClient, ctx, endpoint, true, null);
    }

    public static String getCacheFile(Context ctx, String theURI) {
        StringBuilder chaine = new StringBuilder("");
        try {
            String aFileName = theURI.toLowerCase().replace("http://", "");
            aFileName = aFileName.toLowerCase().replace("https://", "");
            aFileName = sanitizeName(aFileName);

            File file = new File(ctx.getCacheDir().getAbsolutePath() + "/" + aFileName);
            Date lastModDate = new Date(file.lastModified());
            Date now = new Date();
            long millis = now.getTime() - file.lastModified();
            long hours = millis / (1000 * 60 * 60);

            if (hours < 1) {
                FileInputStream aStream = new FileInputStream(file);
                BufferedReader rd = new BufferedReader(new InputStreamReader(aStream));
                String line;
                while ((line = rd.readLine()) != null) {
                    chaine.append(line);
                }
                rd.close();
                return chaine.toString();
            }
        } catch (Exception e) {
            Log.e("UTIL", "getCacheFile() " + e);
        }
        return null;
    }

    public static void writeFileCache(Context ctx, String theURI, String content) {
        try {
            String aFileName = theURI.toLowerCase().replace("http://", "");
            aFileName = aFileName.toLowerCase().replace("https://", "");
            aFileName = sanitizeName(aFileName);

            File f = new File(ctx.getCacheDir() + "/" + aFileName);
            FileOutputStream aStream = new FileOutputStream(f);
            aStream.write(content.getBytes("utf-8"));
            aStream.close();
        } catch (Exception e) {
            Log.e("UTIL", "writeFileCache() could not write to cache file for:" + theURI);
        }
    }
}
