package com.example.radiodbtool;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.room.RoomDatabase;

import com.example.radiodbtool.database.RadioDroidDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DatabaseImportExportHelper {
    private static final String TAG = "DatabaseImportExportHelper";
    private static final String DATABASE_NAME = "radio_stations.db";

    public interface ImportExportCallback {
        void onSuccess(String message);
        void onError(String error);
        void onProgress(String message);
    }

    public static File getDatabaseFile(Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

    public static void exportDatabase(Context context, Uri destinationUri, ImportExportCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("正在导出数据库...");

                File dbFile = getDatabaseFile(context);
                if (!dbFile.exists()) {
                    callback.onError("数据库文件不存在");
                    return;
                }

                try (InputStream inputStream = new FileInputStream(dbFile);
                     OutputStream outputStream = context.getContentResolver().openOutputStream(destinationUri)) {
                    if (outputStream == null) {
                        callback.onError("无法打开输出流");
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                callback.onSuccess("数据库导出成功");
                Log.d(TAG, "数据库导出成功: " + destinationUri);
            } catch (Exception e) {
                Log.e(TAG, "导出数据库失败", e);
                callback.onError("导出失败: " + e.getMessage());
            }
        }).start();
    }

    public static void importDatabase(Context context, Uri sourceUri, ImportExportCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("正在导入数据库...");

                File tempFile = new File(context.getCacheDir(), "imported_temp.db");

                try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                     OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                    if (inputStream == null) {
                        callback.onError("无法读取选择的文件");
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                RadioDroidDatabase oldDb = RadioDroidDatabase.getDatabase(context);
                oldDb.close();

                RadioDroidDatabase.destroyInstance();

                File dbFile = getDatabaseFile(context);
                dbFile.delete();
                new File(dbFile.getAbsolutePath() + "-wal").delete();
                new File(dbFile.getAbsolutePath() + "-shm").delete();

                if (!tempFile.renameTo(dbFile)) {
                    callback.onError("替换数据库文件失败");
                    return;
                }

                RadioDroidDatabase.getDatabase(context);

                callback.onSuccess("数据库导入成功，数据已更新");
                Log.d(TAG, "数据库导入成功");
            } catch (Exception e) {
                Log.e(TAG, "导入数据库失败", e);
                callback.onError("导入失败: " + e.getMessage());
            }
        }).start();
    }

    public static boolean hasData(Context context) {
        try {
            RadioDroidDatabase db = RadioDroidDatabase.getDatabase(context);
            int count = db.radioStationDao().getCount();
            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}