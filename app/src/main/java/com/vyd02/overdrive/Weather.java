package com.vyd02.overdrive;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** Current weather from Open-Meteo (free, no key). Location is a saved place, or found from the internet connection. */
final class Weather {

    static final String KEY_PLACE = "weather_place"; // what the user typed, "" = automatic
    static final String KEY_LAT = "weather_lat";
    static final String KEY_LON = "weather_lon";
    static final String KEY_NAME = "weather_name";

    static class Result {
        int temp;
        int hi, lo;
        String icon;
        String desc;
        String place;
    }

    private Weather() { }

    /** Turns a city or ZIP into coordinates and saves them. Returns the place name or throws. */
    static String setPlace(SharedPreferences p, String query) throws Exception {
        String q = query.trim();
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                + URLEncoder.encode(q, "UTF-8");
        if (q.matches("\\d{5}")) url += "&countryCode=US";
        JSONObject j = new JSONObject(Net.get(url));
        JSONArray arr = j.optJSONArray("results");
        if (arr == null || arr.length() == 0) throw new Exception("Couldn't find \"" + q + "\"");
        JSONObject r = arr.getJSONObject(0);
        String name = r.optString("name");
        String admin = r.optString("admin1", "");
        String label = admin.isEmpty() ? name : name + ", " + abbrev(admin);
        p.edit().putString(KEY_PLACE, q)
                .putFloat(KEY_LAT, (float) r.getDouble("latitude"))
                .putFloat(KEY_LON, (float) r.getDouble("longitude"))
                .putString(KEY_NAME, label).apply();
        return label;
    }

    static void clearPlace(SharedPreferences p) {
        p.edit().remove(KEY_PLACE).remove(KEY_LAT).remove(KEY_LON).remove(KEY_NAME).apply();
    }

    static Result fetch(SharedPreferences p) throws Exception {
        double lat, lon;
        String name;
        if (p.contains(KEY_LAT)) {
            lat = p.getFloat(KEY_LAT, 0);
            lon = p.getFloat(KEY_LON, 0);
            name = p.getString(KEY_NAME, "");
        } else {
            JSONObject ip = new JSONObject(Net.get("https://ipwho.is/?fields=success,city,region_code,latitude,longitude"));
            if (!ip.optBoolean("success", false)) throw new Exception("No location");
            lat = ip.getDouble("latitude");
            lon = ip.getDouble("longitude");
            name = ip.optString("city", "");
            String rc = ip.optString("region_code", "");
            if (!rc.isEmpty()) name += ", " + rc;
        }
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
                + "&temperature_unit=fahrenheit&timezone=auto";
        JSONObject j = new JSONObject(Net.get(url));
        JSONObject cur = j.getJSONObject("current");
        JSONObject daily = j.getJSONObject("daily");
        Result r = new Result();
        r.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        r.hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        r.lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        r.icon = icon(code, day);
        r.desc = desc(code);
        r.place = name;
        return r;
    }

    static String icon(int c, boolean day) {
        if (c == 0) return day ? "☀️" : "🌙";
        if (c <= 2) return day ? "⛅" : "☁️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 67) return "🌧️";
        if (c >= 71 && c <= 77) return "❄️";
        if (c >= 80 && c <= 82) return "🌦️";
        if (c >= 85 && c <= 86) return "🌨️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }

    static String desc(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c >= 85 && c <= 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "";
    }

    private static final String[][] STATES = {
            {"Alabama", "AL"}, {"Alaska", "AK"}, {"Arizona", "AZ"}, {"Arkansas", "AR"}, {"California", "CA"},
            {"Colorado", "CO"}, {"Connecticut", "CT"}, {"Delaware", "DE"}, {"Florida", "FL"}, {"Georgia", "GA"},
            {"Hawaii", "HI"}, {"Idaho", "ID"}, {"Illinois", "IL"}, {"Indiana", "IN"}, {"Iowa", "IA"},
            {"Kansas", "KS"}, {"Kentucky", "KY"}, {"Louisiana", "LA"}, {"Maine", "ME"}, {"Maryland", "MD"},
            {"Massachusetts", "MA"}, {"Michigan", "MI"}, {"Minnesota", "MN"}, {"Mississippi", "MS"},
            {"Missouri", "MO"}, {"Montana", "MT"}, {"Nebraska", "NE"}, {"Nevada", "NV"}, {"New Hampshire", "NH"},
            {"New Jersey", "NJ"}, {"New Mexico", "NM"}, {"New York", "NY"}, {"North Carolina", "NC"},
            {"North Dakota", "ND"}, {"Ohio", "OH"}, {"Oklahoma", "OK"}, {"Oregon", "OR"}, {"Pennsylvania", "PA"},
            {"Rhode Island", "RI"}, {"South Carolina", "SC"}, {"South Dakota", "SD"}, {"Tennessee", "TN"},
            {"Texas", "TX"}, {"Utah", "UT"}, {"Vermont", "VT"}, {"Virginia", "VA"}, {"Washington", "WA"},
            {"West Virginia", "WV"}, {"Wisconsin", "WI"}, {"Wyoming", "WY"}, {"Guam", "GU"}, {"Puerto Rico", "PR"}
    };

    static String abbrev(String state) {
        for (String[] s : STATES) if (s[0].equalsIgnoreCase(state)) return s[1];
        return state;
    }
}
