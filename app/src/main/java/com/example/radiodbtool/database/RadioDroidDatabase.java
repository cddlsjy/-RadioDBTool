package com.example.radiodbtool.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {RadioStation.class, UpdateTimestamp.class}, version = 1, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class RadioDroidDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "radio_stations.db";

    public abstract RadioStationDao radioStationDao();
    
    public abstract UpdateTimestampDao updateTimestampDao();

    private static volatile RadioDroidDatabase INSTANCE;

    public static RadioDroidDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (RadioDroidDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            RadioDroidDatabase.class, DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
        }
    }
}
