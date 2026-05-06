package com.example.radiodbtool;

import android.content.Context;
import android.content.SharedPreferences;

public class SyncProgressManager {
    private static final String PREF_NAME = "sync_progress";
    private static final String KEY_LAST_OFFSET = "last_offset";
    private static final String KEY_TOTAL_STATIONS = "total_stations";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_COUNTRY = "country";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_KEYWORD = "keyword";

    private SharedPreferences prefs;

    public SyncProgressManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSyncProgress(int offset, int total, String serverUrl, String country, String language, String keyword) {
        prefs.edit()
                .putInt(KEY_LAST_OFFSET, offset)
                .putInt(KEY_TOTAL_STATIONS, total)
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_COUNTRY, country)
                .putString(KEY_LANGUAGE, language)
                .putString(KEY_KEYWORD, keyword)
                .apply();
    }

    public int getLastOffset() {
        return prefs.getInt(KEY_LAST_OFFSET, 0);
    }

    public int getTotalStations() {
        return prefs.getInt(KEY_TOTAL_STATIONS, 0);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public String getCountry() {
        return prefs.getString(KEY_COUNTRY, "");
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "");
    }

    public String getKeyword() {
        return prefs.getString(KEY_KEYWORD, "");
    }

    public void clearProgress() {
        prefs.edit().clear().apply();
    }

    public boolean hasProgress() {
        return getLastOffset() > 0;
    }
}
