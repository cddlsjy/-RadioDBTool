package com.example.radiodbtool.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RadioStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RadioStation> stations);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RadioStation station);

    @Update
    void update(RadioStation station);

    @Delete
    void delete(RadioStation station);

    @Query("DELETE FROM radio_stations")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM radio_stations")
    int getCount();

    @Query("SELECT * FROM radio_stations")
    List<RadioStation> getAllStations();

    @Query("SELECT * FROM radio_stations ORDER BY clickcount DESC LIMIT :limit")
    List<RadioStation> getTopClickStationsSync(int limit);

    @Query("SELECT DISTINCT country FROM radio_stations WHERE country != '' ORDER BY country ASC")
    List<String> getAllCountriesSync();

    @Query("SELECT DISTINCT language FROM radio_stations WHERE language != '' ORDER BY language ASC")
    List<String> getAllLanguagesSync();

    @Query("SELECT * FROM radio_stations WHERE country = :country ORDER BY clickcount DESC")
    List<RadioStation> getStationsByCountrySync(String country);

    @Query("SELECT * FROM radio_stations WHERE language = :language ORDER BY clickcount DESC")
    List<RadioStation> getStationsByLanguageSync(String language);

    @Query("SELECT * FROM radio_stations WHERE tags LIKE '%' || :tag || '%' ORDER BY clickcount DESC")
    List<RadioStation> getStationsByTagSync(String tag);

    @Query("SELECT * FROM radio_stations WHERE (:country = '' OR country = :country) " +
           "AND (:language = '' OR language = :language) " +
           "AND (:keyword = '' OR name LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%') " +
           "ORDER BY clickcount DESC")
    List<RadioStation> getFilteredStations(String country, String language, String keyword);

    @Query("SELECT station_uuid FROM radio_stations")
    List<String> getAllStationIds();

    @Query("SELECT * FROM radio_stations WHERE station_uuid = :stationId")
    RadioStation getStationById(String stationId);

    @Query("SELECT COUNT(*) FROM radio_stations WHERE lastcheckok = 1")
    int getWorkingStationCount();

    @Query("SELECT COUNT(*) FROM radio_stations WHERE country = :country")
    int getStationCountByCountrySync(String country);

    @Query("SELECT COUNT(*) FROM radio_stations WHERE language = :language")
    int getStationCountByLanguageSync(String language);

    @Query("SELECT country, COUNT(*) as stationCount FROM radio_stations WHERE country != '' GROUP BY country HAVING COUNT(*) > 0 ORDER BY country ASC")
    List<CountryCount> getAllCountriesWithCountSync();

    @Query("SELECT language, COUNT(*) as stationCount FROM radio_stations WHERE language != '' GROUP BY language HAVING COUNT(*) > 0 ORDER BY language ASC")
    List<LanguageCount> getAllLanguagesWithCountSync();
}
