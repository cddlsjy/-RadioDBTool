package com.example.radiodbtool.station;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DataRadioStation {
    static final String TAG = "DATAStation";

    public DataRadioStation() {
    }

    public String Name;
    public String StationUuid = "";
    public String ChangeUuid = "";
    public String StreamUrl;
    public String HomePageUrl;
    public String IconUrl;
    public String Country;
    public String CountryCode;
    public String State;
    public String TagsAll;
    public String Language;
    public String LastChangeTime;
    public int ClickCount;
    public int ClickTrend;
    public int Votes;
    public int Bitrate;
    public String Codec;
    public boolean Working = true;
    public boolean Hls = false;

    private void fixStationFields() {
        if (IconUrl == null || TextUtils.isEmpty(IconUrl.trim())) {
            IconUrl = "";
        }
    }

    public static List<DataRadioStation> DecodeJson(String result) {
        List<DataRadioStation> aList = new ArrayList<DataRadioStation>();
        if (result != null) {
            if (TextUtils.isGraphic(result)) {
                try {
                    JSONArray jsonArray = new JSONArray(result);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        try {
                            JSONObject anObject = jsonArray.getJSONObject(i);

                            DataRadioStation aStation = new DataRadioStation();
                            if (anObject.has("name")) {
                                aStation.Name = anObject.optString("name", "Unknown Station");
                            } else {
                                aStation.Name = "Unknown Station";
                            }
                            aStation.StreamUrl = "";
                            if (anObject.has("url")) {
                                aStation.StreamUrl = anObject.getString("url");
                            }
                            if (anObject.has("stationuuid")) {
                                aStation.StationUuid = anObject.getString("stationuuid");
                            }
                            if (anObject.has("changeuuid")) {
                                aStation.ChangeUuid = anObject.getString("changeuuid");
                            }
                            if (anObject.has("votes")) {
                                aStation.Votes = anObject.getInt("votes");
                            }
                            if (anObject.has("homepage")) {
                                aStation.HomePageUrl = anObject.getString("homepage");
                            }
                            if (anObject.has("tags")) {
                                aStation.TagsAll = anObject.getString("tags");
                            }
                            if (anObject.has("country")) {
                                aStation.Country = anObject.getString("country");
                            }
                            if (anObject.has("countrycode")) {
                                aStation.CountryCode = anObject.getString("countrycode");
                            }
                            if (anObject.has("state")) {
                                aStation.State = anObject.getString("state");
                            }
                            if (anObject.has("favicon")) {
                                aStation.IconUrl = anObject.getString("favicon");
                            }
                            if (anObject.has("language")) {
                                aStation.Language = anObject.getString("language");
                            }
                            if (anObject.has("lastchangetime")) {
                                aStation.LastChangeTime = anObject.getString("lastchangetime");
                            }
                            if (anObject.has("clickcount")) {
                                aStation.ClickCount = anObject.getInt("clickcount");
                            }
                            if (anObject.has("clicktrend")) {
                                aStation.ClickTrend = anObject.getInt("clicktrend");
                            }
                            if (anObject.has("bitrate")) {
                                aStation.Bitrate = anObject.getInt("bitrate");
                            }
                            if (anObject.has("codec")) {
                                aStation.Codec = anObject.getString("codec");
                            }
                            if (anObject.has("lastcheckok")) {
                                aStation.Working = anObject.getInt("lastcheckok") != 0;
                            }
                            if (anObject.has("hls")) {
                                aStation.Hls = anObject.getInt("hls") != 0;
                            }

                            aStation.fixStationFields();
                            aList.add(aStation);
                        } catch (Exception e) {
                            Log.e(TAG, "DecodeJson() #2 " + e);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "DecodeJson() #1 " + e);
                }
            }
        }
        return aList;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            if (TextUtils.isEmpty(StationUuid)) {
                obj.put("id", "");
            } else {
                obj.put("stationuuid", StationUuid);
            }
            obj.put("changeuuid", ChangeUuid);
            obj.put("name", Name);
            obj.put("homepage", HomePageUrl);
            obj.put("url", StreamUrl);
            obj.put("favicon", IconUrl);
            obj.put("country", Country);
            obj.put("countrycode", CountryCode);
            obj.put("state", State);
            obj.put("tags", TagsAll);
            obj.put("language", Language);
            obj.put("clickcount", ClickCount);
            obj.put("clicktrend", ClickTrend);
            obj.put("votes", Votes);
            obj.put("bitrate", "" + Bitrate);
            obj.put("codec", Codec);
            obj.put("lastcheckok", Working ? "1" : "0");
            return obj;
        } catch (JSONException e) {
            Log.e(TAG, "toJson() " + e);
        }
        return null;
    }

    public boolean hasValidUuid() {
        return !TextUtils.isEmpty(StationUuid);
    }
}
