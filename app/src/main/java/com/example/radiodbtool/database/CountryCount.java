package com.example.radiodbtool.database;

import androidx.room.Ignore;

public class CountryCount {
    public String country;
    public int stationCount;
    
    public CountryCount() {
    }
    
    @Ignore
    public CountryCount(String country, int stationCount) {
        this.country = country;
        this.stationCount = stationCount;
    }
}
