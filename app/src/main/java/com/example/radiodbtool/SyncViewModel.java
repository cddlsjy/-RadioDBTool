package com.example.radiodbtool;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.radiodbtool.database.RadioStationRepository;

public class SyncViewModel extends AndroidViewModel {
    private static final String TAG = "SyncViewModel";

    private RadioStationRepository repository;

    private MutableLiveData<Boolean> isSyncing = new MutableLiveData<>(false);
    private MutableLiveData<Integer> progressCurrent = new MutableLiveData<>(0);
    private MutableLiveData<Integer> progressTotal = new MutableLiveData<>(0);
    private MutableLiveData<String> syncStatus = new MutableLiveData<>("就绪");
    private MutableLiveData<String> syncError = new MutableLiveData<>();
    private MutableLiveData<Boolean> hasPendingSync = new MutableLiveData<>(false);

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public SyncViewModel(@NonNull Application application) {
        super(application);
        repository = RadioStationRepository.getInstance(application);
        checkPendingSync();
    }

    private void checkPendingSync() {
        boolean hasProgress = new SyncProgressManager(getApplication()).hasProgress();
        hasPendingSync.postValue(hasProgress);
    }

    public LiveData<Boolean> isSyncing() {
        return isSyncing;
    }

    public LiveData<Integer> getProgressCurrent() {
        return progressCurrent;
    }

    public LiveData<Integer> getProgressTotal() {
        return progressTotal;
    }

    public LiveData<String> getSyncStatus() {
        return syncStatus;
    }

    public LiveData<String> getSyncError() {
        return syncError;
    }

    public LiveData<Boolean> hasPendingSync() {
        return hasPendingSync;
    }

    public void startSync(String serverUrl, String country, String language, String keyword) {
        if (isSyncing.getValue() != null && isSyncing.getValue()) {
            return;
        }

        isSyncing.postValue(true);
        syncError.postValue(null);

        boolean hasFilter = !country.isEmpty() || !language.isEmpty() || !keyword.isEmpty();

        RadioStationRepository.ProgressListener listener = new RadioStationRepository.ProgressListener() {
            @Override
            public void onProgress(String message, int current, int total) {
                mainHandler.post(() -> {
                    progressCurrent.postValue(current);
                    progressTotal.postValue(total);
                    syncStatus.postValue(message + " " + current + (total > 0 ? "/" + total : " 条"));
                });
            }

            @Override
            public void onSuccess(String message) {
                mainHandler.post(() -> {
                    syncStatus.postValue(message);
                    isSyncing.postValue(false);
                    checkPendingSync();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    syncStatus.postValue("同步失败：" + error);
                    syncError.postValue(error);
                    isSyncing.postValue(false);
                });
            }
        };

        if (hasFilter) {
            repository.syncStationsByFilter(getApplication(), serverUrl, country, language, keyword, listener);
        } else {
            repository.syncAllStations(getApplication(), serverUrl, listener);
        }
    }

    public void resumeSync() {
        if (isSyncing.getValue() != null && isSyncing.getValue()) {
            return;
        }

        SyncProgressManager progressManager = new SyncProgressManager(getApplication());
        String serverUrl = progressManager.getServerUrl();
        String country = progressManager.getCountry();
        String language = progressManager.getLanguage();
        String keyword = progressManager.getKeyword();

        if (serverUrl.isEmpty()) {
            syncError.postValue("没有可恢复的同步任务");
            return;
        }

        isSyncing.postValue(true);
        syncError.postValue(null);

        boolean hasFilter = !country.isEmpty() || !language.isEmpty() || !keyword.isEmpty();

        RadioStationRepository.ProgressListener listener = new RadioStationRepository.ProgressListener() {
            @Override
            public void onProgress(String message, int current, int total) {
                mainHandler.post(() -> {
                    progressCurrent.postValue(current);
                    progressTotal.postValue(total);
                    syncStatus.postValue(message + " " + current + (total > 0 ? "/" + total : " 条"));
                });
            }

            @Override
            public void onSuccess(String message) {
                mainHandler.post(() -> {
                    syncStatus.postValue(message);
                    isSyncing.postValue(false);
                    checkPendingSync();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    syncStatus.postValue("同步失败：" + error);
                    syncError.postValue(error);
                    isSyncing.postValue(false);
                });
            }
        };

        if (hasFilter) {
            repository.syncStationsByFilter(getApplication(), serverUrl, country, language, keyword, listener);
        } else {
            repository.resumeSyncAllStations(getApplication(), serverUrl, listener);
        }
    }
}
