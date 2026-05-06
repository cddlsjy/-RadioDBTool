package com.example.radiodbtool;

import com.example.radiodbtool.database.RadioStation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

public class ExportHelper {

    public static void exportToM3U(List<RadioStation> stations, OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.write("#EXTM3U\n");
        for (RadioStation s : stations) {
            writer.write("#EXTINF:-1," + escapeM3UString(s.name) + "\n");
            writer.write(s.url + "\n\n");
        }
        writer.flush();
    }

    public static void exportToCSV(List<RadioStation> stations, OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.write("station_uuid,name,url,homepage,favicon,country,countrycode,state,tags,language,clickcount,votes,bitrate,codec\n");
        
        for (RadioStation s : stations) {
            writer.write(escapeCSVField(s.stationUuid) + "," +
                        escapeCSVField(s.name) + "," +
                        escapeCSVField(s.url) + "," +
                        escapeCSVField(s.homepage) + "," +
                        escapeCSVField(s.favicon) + "," +
                        escapeCSVField(s.country) + "," +
                        escapeCSVField(s.countrycode) + "," +
                        escapeCSVField(s.state) + "," +
                        escapeCSVField(s.tags) + "," +
                        escapeCSVField(s.language) + "," +
                        s.clickcount + "," +
                        s.votes + "," +
                        s.bitrate + "," +
                        escapeCSVField(s.codec) + "\n");
        }
        writer.flush();
    }

    public static void exportToJSON(List<RadioStation> stations, OutputStream out) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(stations);
        out.write(json.getBytes("UTF-8"));
        out.flush();
    }

    private static String escapeM3UString(String str) {
        if (str == null) return "";
        return str.replace("\n", "").replace("\r", "");
    }

    private static String escapeCSVField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
