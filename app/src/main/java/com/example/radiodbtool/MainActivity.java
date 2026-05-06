package com.example.radiodbtool;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.radiodbtool.database.RadioStation;
import com.example.radiodbtool.database.RadioStationRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SAVE_FILE = 1;

    private EditText etServerUrl;
    private Button btnSync;
    private ProgressBar progressBarSync;
    private TextView tvSyncStatus;

    private EditText etCountry;
    private EditText etLanguage;
    private EditText etKeyword;
    private Spinner spinnerExportFormat;
    private Button btnExport;
    private ProgressBar progressBarExport;
    private TextView tvExportStatus;

    private RadioStationRepository repository;
    private List<RadioStation> stationsToExport;
    private int exportFormat;
    private String exportCountry, exportLanguage, exportKeyword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = RadioStationRepository.getInstance(this);

        etServerUrl = findViewById(R.id.etServerUrl);
        btnSync = findViewById(R.id.btnSync);
        progressBarSync = findViewById(R.id.progressBarSync);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);

        etCountry = findViewById(R.id.etCountry);
        etLanguage = findViewById(R.id.etLanguage);
        etKeyword = findViewById(R.id.etKeyword);
        spinnerExportFormat = findViewById(R.id.spinnerExportFormat);
        btnExport = findViewById(R.id.btnExport);
        progressBarExport = findViewById(R.id.progressBarExport);
        tvExportStatus = findViewById(R.id.tvExportStatus);

        setupSpinner();
        setupSyncButton();
        setupExportButton();
        loadServerUrl();
    }

    private void setupSpinner() {
        String[] formats = {getString(R.string.export_format_m3u), 
                           getString(R.string.export_format_csv), 
                           getString(R.string.export_format_json),
                           getString(R.string.export_format_db)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, formats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerExportFormat.setAdapter(adapter);
    }

    private void setupSyncButton() {
        btnSync.setOnClickListener(v -> {
            String serverUrl = etServerUrl.getText().toString().trim();
            if (serverUrl.isEmpty()) {
                Toast.makeText(this, R.string.error_empty_server, Toast.LENGTH_SHORT).show();
                return;
            }

            String country = etCountry.getText().toString().trim();
            String language = etLanguage.getText().toString().trim();
            String keyword = etKeyword.getText().toString().trim();

            saveServerUrl(serverUrl);

            btnSync.setEnabled(false);
            progressBarSync.setVisibility(View.VISIBLE);
            progressBarSync.setIndeterminate(true);
            tvSyncStatus.setText("正在同步，请稍候...");

            boolean hasFilter = !country.isEmpty() || !language.isEmpty() || !keyword.isEmpty();

            new Thread(() -> {
                RadioStationRepository.ProgressListener listener = new RadioStationRepository.ProgressListener() {
                    @Override
                    public void onProgress(String message, int current, int total) {
                        runOnUiThread(() -> {
                            if (total > 0) {
                                progressBarSync.setIndeterminate(false);
                                progressBarSync.setMax(total);
                                progressBarSync.setProgress(current);
                                tvSyncStatus.setText(message + " " + current + "/" + total);
                            } else {
                                progressBarSync.setIndeterminate(true);
                                tvSyncStatus.setText(message + " " + current + " 条");
                            }
                        });
                    }

                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            tvSyncStatus.setText(message);
                            btnSync.setEnabled(true);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            tvSyncStatus.setText("同步失败：" + error);
                            btnSync.setEnabled(true);
                            Toast.makeText(MainActivity.this, "同步失败：" + error, Toast.LENGTH_LONG).show();
                        });
                    }
                };

                if (hasFilter) {
                    repository.syncStationsByFilter(MainActivity.this, serverUrl, country, language, keyword, listener);
                } else {
                    repository.syncAllStations(MainActivity.this, serverUrl, listener);
                }
            }).start();
        });
    }

    private void setupExportButton() {
        btnExport.setOnClickListener(v -> {
            String country = etCountry.getText().toString().trim();
            String language = etLanguage.getText().toString().trim();
            String keyword = etKeyword.getText().toString().trim();

            btnExport.setEnabled(false);
            progressBarExport.setVisibility(View.VISIBLE);
            tvExportStatus.setText("正在准备导出...");

            exportFormat = spinnerExportFormat.getSelectedItemPosition();

            if (exportFormat == 3) {
                startDatabaseExport(country, language, keyword);
            } else {
                new Thread(() -> {
                    List<RadioStation> stations = repository.getFilteredStations(country, language, keyword);

                    runOnUiThread(() -> {
                        progressBarExport.setVisibility(View.GONE);

                        if (stations.isEmpty()) {
                            tvExportStatus.setText(getString(R.string.error_no_stations));
                            Toast.makeText(MainActivity.this, R.string.error_no_stations, Toast.LENGTH_SHORT).show();
                            btnExport.setEnabled(true);
                        } else {
                            tvExportStatus.setText("找到 " + stations.size() + " 个电台");
                            stationsToExport = stations;
                            startExport(stations, exportFormat);
                        }
                    });
                }).start();
            }
        });
    }

    private void startDatabaseExport(String country, String language, String keyword) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "radio_stations.db");

        this.exportCountry = country;
        this.exportLanguage = language;
        this.exportKeyword = keyword;

        startActivityForResult(intent, REQUEST_SAVE_FILE);
    }

    private void startExport(List<RadioStation> stations, int format) {
        this.stationsToExport = stations;
        this.exportFormat = format;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        switch (format) {
            case 0:
                intent.setType("audio/x-mpegurl");
                intent.putExtra(Intent.EXTRA_TITLE, "stations.m3u");
                break;
            case 1:
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, "stations.csv");
                break;
            case 2:
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "stations.json");
                break;
        }

        startActivityForResult(intent, REQUEST_SAVE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (exportFormat == 3) {
                    exportDatabaseToUri(uri, exportCountry, exportLanguage, exportKeyword);
                } else {
                    exportToUri(uri, stationsToExport, exportFormat);
                }
            }
        } else {
            btnExport.setEnabled(true);
            progressBarExport.setVisibility(View.GONE);
        }
    }

    private void exportDatabaseToUri(Uri uri, String country, String language, String keyword) {
        progressBarExport.setVisibility(View.VISIBLE);
        tvExportStatus.setText("正在导出数据库...");

        DatabaseExportHelper.exportFilteredDatabase(this, uri, country, language, keyword,
                new DatabaseExportHelper.ExportCallback() {
                    @Override
                    public void onSuccess(int count, Uri uri) {
                        runOnUiThread(() -> {
                            progressBarExport.setVisibility(View.GONE);
                            tvExportStatus.setText("成功导出 " + count + " 个电台到数据库文件");
                            Toast.makeText(MainActivity.this,
                                    "成功导出 " + count + " 个电台到数据库文件",
                                    Toast.LENGTH_LONG).show();
                            btnExport.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressBarExport.setVisibility(View.GONE);
                            tvExportStatus.setText("导出失败: " + error);
                            Toast.makeText(MainActivity.this, "导出失败: " + error, Toast.LENGTH_LONG).show();
                            btnExport.setEnabled(true);
                        });
                    }
                });
    }

    private void exportToUri(Uri uri, List<RadioStation> stations, int format) {
        progressBarExport.setVisibility(View.VISIBLE);
        tvExportStatus.setText("正在导出...");

        new Thread(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("无法打开输出流");

                switch (format) {
                    case 0:
                        ExportHelper.exportToM3U(stations, os);
                        break;
                    case 1:
                        ExportHelper.exportToCSV(stations, os);
                        break;
                    case 2:
                        ExportHelper.exportToJSON(stations, os);
                        break;
                }

                runOnUiThread(() -> {
                    progressBarExport.setVisibility(View.GONE);
                    tvExportStatus.setText(getString(R.string.success_export));
                    Toast.makeText(MainActivity.this, R.string.success_export, Toast.LENGTH_SHORT).show();
                    btnExport.setEnabled(true);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    progressBarExport.setVisibility(View.GONE);
                    tvExportStatus.setText(String.format(getString(R.string.error_export_failed), e.getMessage()));
                    Toast.makeText(MainActivity.this, String.format(getString(R.string.error_export_failed), e.getMessage()), Toast.LENGTH_LONG).show();
                    btnExport.setEnabled(true);
                });
            }
        }).start();
    }

    private void saveServerUrl(String url) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("server_url", url).apply();
    }

    private void loadServerUrl() {
        String savedUrl = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("server_url", "https://de1.api.radio-browser.info");
        etServerUrl.setText(savedUrl);
    }
}
