package com.example.radiodbtool.database;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.room.Room;

import com.example.radiodbtool.HttpClient;
import com.example.radiodbtool.SyncProgressManager;
import com.example.radiodbtool.Utils;
import com.example.radiodbtool.station.DataRadioStation;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class RadioStationRepository {
    private static final String TAG = "RadioStationRepository";
    
    private RadioStationDao radioStationDao;
    private RadioStationDao tempRadioStationDao;
    private Context context;
    private Executor executor = Executors.newSingleThreadExecutor();
    
    private static final Object sSyncLock = new Object();
    private static volatile RadioStationRepository INSTANCE;
    private SyncProgressManager progressManager;
    
    public static RadioStationRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RadioStationRepository.class) {
                if (INSTANCE == null) {
                    RadioDroidDatabase db = RadioDroidDatabase.getDatabase(context);
                    INSTANCE = new RadioStationRepository(db.radioStationDao(), context);
                }
            }
        }
        return INSTANCE;
    }
    
    private RadioStationRepository(RadioStationDao radioStationDao, Context context) {
        this.radioStationDao = radioStationDao;
        RadioDroidDatabase tempDatabase = Room.databaseBuilder(context.getApplicationContext(),
                RadioDroidDatabase.class, "radio_droid_database_temp")
                .fallbackToDestructiveMigration()
                .build();
        this.tempRadioStationDao = tempDatabase.radioStationDao();
        this.context = context;
        this.progressManager = new SyncProgressManager(context);
    }
    
    public interface ProgressListener {
        void onProgress(String message, int current, int total);
        void onSuccess(String message);
        void onError(String error);
    }
    
    public void syncAllStations(Context context, String serverUrl, ProgressListener listener) {
        executor.execute(() -> syncAllStationsInternal(context, serverUrl, listener, false));
    }

    public void resumeSyncAllStations(Context context, String serverUrl, ProgressListener listener) {
        executor.execute(() -> syncAllStationsInternal(context, serverUrl, listener, true));
    }

    private void syncAllStationsInternal(Context context, String serverUrl, ProgressListener listener, boolean resume) {
        synchronized (sSyncLock) {
            try {
                listener.onProgress("正在检查网络...", 0, 100);

                if (!Utils.hasAnyConnection(context)) {
                    listener.onError("网络连接不可用");
                    return;
                }

                OkHttpClient httpClient = HttpClient.getInstance();
                boolean useHttps = serverUrl.startsWith("https://");
                String server = serverUrl.replace("http://", "").replace("https://", "");

                if (server.contains("/")) {
                    server = server.substring(0, server.indexOf("/"));
                }

                listener.onProgress("正在获取电台数量...", 0, 100);

                String statsResult = Utils.downloadFeedFromServer(httpClient, context, server, "json/stats", useHttps);
                if (statsResult == null) {
                    listener.onError("获取服务器统计信息失败");
                    return;
                }

                int totalStations;
                try {
                    org.json.JSONObject stats = new org.json.JSONObject(statsResult);
                    totalStations = stats.getInt("stations");
                    Log.d(TAG, "解析得到的电台总数: " + totalStations);
                } catch (Exception e) {
                    listener.onError("解析统计信息失败: " + e.getMessage());
                    return;
                }

                listener.onProgress("发现 " + totalStations + " 个电台", 0, totalStations);

                String filterKey = "full";
                int startOffset = 0;

                if (resume) {
                    startOffset = progressManager.getLastOffset();
                    if (startOffset > 0 && progressManager.hasProgress()) {
                        listener.onProgress("从断点恢复，已下载 " + startOffset + " 个电台", startOffset, totalStations);
                        Log.d(TAG, "断点续传: 从 offset=" + startOffset + " 继续");
                    }
                }

                if (startOffset >= totalStations) {
                    listener.onSuccess("数据已是最新");
                    return;
                }

                tempRadioStationDao.deleteAll();

                final int pageSize = 100;
                int totalPages = (int) Math.ceil((double) totalStations / pageSize);
                int totalDownloaded = startOffset;

                for (int page = startOffset / pageSize; page < totalPages; page++) {
                    int skip = page * pageSize;
                    String path = "json/stations?limit=" + pageSize + "&offset=" + skip;

                    String resultString = Utils.downloadFeedFromServer(httpClient, context, server, path, useHttps);

                    if (resultString != null) {
                        List<DataRadioStation> dataStations = DataRadioStation.DecodeJson(resultString);

                        if (dataStations != null && !dataStations.isEmpty()) {
                            List<RadioStation> radioStations = new ArrayList<>();
                            for (DataRadioStation dataStation : dataStations) {
                                RadioStation radioStation = RadioStation.fromDataRadioStation(dataStation);
                                radioStations.add(radioStation);
                            }

                            tempRadioStationDao.insertAll(radioStations);
                            totalDownloaded += radioStations.size();

                            progressManager.saveSyncProgress(totalDownloaded, totalStations, serverUrl, "", "", "");
                            listener.onProgress("正在下载电台数据...", totalDownloaded, totalStations);
                        }
                    }
                }
                
                if (totalDownloaded > 0) {
                    listener.onProgress("正在处理数据...", totalDownloaded, totalDownloaded);
                    
                    radioStationDao.deleteAll();
                    
                    List<RadioStation> allStationsFromTemp = tempRadioStationDao.getAllStations();
                    
                    if (!allStationsFromTemp.isEmpty()) {
                        listener.onProgress("正在写入数据库...", totalDownloaded, totalDownloaded);
                        
                        final int insertBatchSize = 1000;
                        for (int i = 0; i < allStationsFromTemp.size(); i += insertBatchSize) {
                            int endIndex = Math.min(i + insertBatchSize, allStationsFromTemp.size());
                            List<RadioStation> batchToInsert = allStationsFromTemp.subList(i, endIndex);
                            radioStationDao.insertAll(batchToInsert);
                        }
                    }
                    
                    tempRadioStationDao.deleteAll();
                    listener.onSuccess("同步完成，共 " + totalDownloaded + " 个电台");
                    progressManager.clearProgress();
                } else {
                    listener.onError("没有获取到任何电台数据");
                }
            } catch (Exception e) {
                Log.e(TAG, "同步电台数据时出错", e);
                listener.onError("同步失败: " + e.getMessage());
            }
        }
    }
    
    public List<RadioStation> getFilteredStations(String country, String language, String keyword) {
        return radioStationDao.getFilteredStations(country, language, keyword);
    }
    
    public List<RadioStation> getFilteredStationsSync(String country, String language, String keyword) {
        return radioStationDao.getFilteredStationsSync(country, language, keyword);
    }
    
    public int getStationCount() {
        return radioStationDao.getCount();
    }
    
    public List<String> getAllCountries() {
        return radioStationDao.getAllCountriesSync();
    }
    
    public List<String> getAllLanguages() {
        return radioStationDao.getAllLanguagesSync();
    }
    
    public void syncStationsByFilter(Context context, String serverUrl, String country, String language, String keyword, ProgressListener listener) {
        executor.execute(() -> syncStationsByFilterInternal(context, serverUrl, country, language, keyword, listener));
    }
    
    private String buildFilterKey(String country, String language, String keyword) {
        try {
            String input = country + "|" + language + "|" + keyword;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "filter_" + country.hashCode() + "_" + language.hashCode() + "_" + keyword.hashCode();
        }
    }

    private void syncStationsByFilterInternal(Context context, String serverUrl, String country, String language, String keyword, ProgressListener listener) {
        synchronized (sSyncLock) {
            try {
                listener.onProgress("正在检查网络...", 0, 100);

                if (!Utils.hasAnyConnection(context)) {
                    listener.onError("网络连接不可用");
                    return;
                }

                OkHttpClient httpClient = HttpClient.getInstance();
                boolean useHttps = serverUrl.startsWith("https://");
                String server = serverUrl.replace("http://", "").replace("https://", "");

                if (server.contains("/")) {
                    server = server.substring(0, server.indexOf("/"));
                }

                listener.onProgress("正在下载符合条件的电台...", 0, 0);

                String filterKey = buildFilterKey(country, language, keyword);
                int startOffset = progressManager.getLastOffset();

                if (startOffset > 0) {
                    listener.onProgress("从断点恢复，已下载 " + startOffset + " 个电台", startOffset, 0);
                    Log.d(TAG, "断点续传: filter=" + filterKey + ", offset=" + startOffset);
                }

                tempRadioStationDao.deleteAll();

                final int limit = 500;
                int offset = startOffset;
                int totalDownloaded = startOffset;

                while (true) {
                    String url = buildFilterUrl(server, useHttps, country, language, keyword, limit, offset);
                    String path = url.replace(useHttps ? "https://" : "http://", "").replace(server + "/", "");

                    String resultString = Utils.downloadFeedFromServer(httpClient, context, server, path, useHttps);

                    if (resultString == null) {
                        listener.onError("获取电台数据失败");
                        return;
                    }

                    List<DataRadioStation> dataStations = DataRadioStation.DecodeJson(resultString);

                    if (dataStations == null || dataStations.isEmpty()) {
                        break;
                    }

                    List<RadioStation> radioStations = new ArrayList<>();
                    for (DataRadioStation ds : dataStations) {
                        radioStations.add(RadioStation.fromDataRadioStation(ds));
                    }

                    tempRadioStationDao.insertAll(radioStations);
                    totalDownloaded += dataStations.size();

                    progressManager.saveSyncProgress(totalDownloaded, 0, serverUrl, country, language, keyword);
                    listener.onProgress("正在下载电台数据...", totalDownloaded, 0);

                    offset += limit;
                    if (dataStations.size() < limit) break;

                    Thread.sleep(100);
                }
                
                if (totalDownloaded > 0) {
                    listener.onProgress("正在处理数据...", totalDownloaded, totalDownloaded);
                    
                    radioStationDao.deleteAll();
                    
                    List<RadioStation> allStationsFromTemp = tempRadioStationDao.getAllStations();
                    
                    if (!allStationsFromTemp.isEmpty()) {
                        listener.onProgress("正在写入数据库...", totalDownloaded, totalDownloaded);
                        
                        final int insertBatchSize = 1000;
                        for (int i = 0; i < allStationsFromTemp.size(); i += insertBatchSize) {
                            int endIndex = Math.min(i + insertBatchSize, allStationsFromTemp.size());
                            List<RadioStation> batchToInsert = allStationsFromTemp.subList(i, endIndex);
                            radioStationDao.insertAll(batchToInsert);
                        }
                    }
                    
                    tempRadioStationDao.deleteAll();
                    listener.onSuccess("同步完成，共 " + totalDownloaded + " 个电台");
                    progressManager.clearProgress();
                } else {
                    listener.onError("没有找到符合条件的电台");
                }
            } catch (Exception e) {
                Log.e(TAG, "条件同步电台数据时出错", e);
                listener.onError("同步失败: " + e.getMessage());
            }
        }
    }
    
    private String buildFilterUrl(String server, boolean useHttps, String country, String language, String keyword, int limit, int offset) {
        StringBuilder url = new StringBuilder();
        url.append("json/stations?limit=").append(limit).append("&offset=").append(offset);
        if (!TextUtils.isEmpty(country)) {
            url.append("&country=").append(Uri.encode(country));
        }
        if (!TextUtils.isEmpty(language)) {
            url.append("&language=").append(Uri.encode(language));
        }
        if (!TextUtils.isEmpty(keyword)) {
            url.append("&name=").append(Uri.encode(keyword));
        }
        return url.toString();
    }
}
