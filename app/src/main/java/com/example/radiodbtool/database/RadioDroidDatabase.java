package com.example.radiodbtool.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {RadioStation.class, UpdateTimestamp.class}, version = 1, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class RadioDroidDatabase extends RoomDatabase {
    public abstract RadioStationDao radioStationDao();
    
    public abstract UpdateTimestampDao updateTimestampDao();

    private static volatile RadioDroidDatabase INSTANCE;

    public static RadioDroidDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (RadioDroidDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            RadioDroidDatabase.class, "radio_droid_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
