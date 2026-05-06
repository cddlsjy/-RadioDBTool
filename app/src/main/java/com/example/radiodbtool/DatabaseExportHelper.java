package com.example.radiodbtool;

import android.content.Context;
import android.net.Uri;

import com.example.radiodbtool.database.RadioStation;
import com.example.radiodbtool.database.RadioStationRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DatabaseExportHelper {

    public interface ExportCallback {
        void onSuccess(int count, Uri uri);
        void onError(String error);
    }

    public static void exportFilteredDatabase(Context context, Uri outputUri,
                                              String country, String language,
                                              String keyword, ExportCallback callback) {
        new Thread(() -> {
            try {
                RadioStationRepository repository = RadioStationRepository.getInstance(context);
                List<RadioStation> stations = repository.getFilteredStationsSync(country, language, keyword);
                if (stations == null || stations.isEmpty()) {
                    callback.onError("没有符合筛选条件的电台");
                    return;
                }

                try (OutputStream os = context.getContentResolver().openOutputStream(outputUri)) {
                    if (os == null) {
                        callback.onError("无法打开输出流");
                        return;
                    }
                    File tempFile = File.createTempFile("export_", ".db", context.getCacheDir());
                    sqlite3(tempFile, stations);
                    FileInputStream fis = new FileInputStream(tempFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                    fis.close();
                    tempFile.delete();
                    callback.onSuccess(stations.size(), outputUri);
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static void sqlite3(File dbFile, List<RadioStation> stations) throws Exception {
        android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS stations (" +
                "station_uuid TEXT PRIMARY KEY," +
                "change_uuid TEXT," +
                "name TEXT," +
                "url TEXT," +
                "homepage TEXT," +
                "favicon TEXT," +
                "country TEXT," +
                "countrycode TEXT," +
                "state TEXT," +
                "tags TEXT," +
                "language TEXT," +
                "clickcount INTEGER," +
                "clicktrend INTEGER," +
                "votes INTEGER," +
                "bitrate INTEGER," +
                "codec TEXT," +
                "lastcheckok INTEGER," +
                "hls INTEGER," +
                "lastchangetime TEXT" +
                ")");
        db.beginTransaction();
        try {
            for (RadioStation s : stations) {
                db.execSQL("INSERT OR IGNORE INTO stations VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                                s.stationUuid, s.changeUuid, s.name, s.url, s.homepage, s.favicon,
                                s.country, s.countrycode, s.state, s.tags, s.language,
                                s.clickcount, s.clicktrend, s.votes, s.bitrate, s.codec,
                                s.lastcheckok ? 1 : 0, s.hls ? 1 : 0, s.lastchangetime
                        });
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
