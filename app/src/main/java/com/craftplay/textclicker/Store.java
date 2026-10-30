package com.craftplay.textclicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Store {
    private static final int MAX_LOG = 200;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("textclicker", Context.MODE_PRIVATE);
    }

    // Cargar la lista de clicks
    public static List<ClickItem> load(Context c) {
        List<ClickItem> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new ClickItem(o.getString("text"), o.getInt("hour"), o.getInt("minute")));
            }
        } catch (JSONException e) {
            // lista vacía si algo falla
        }
        return out;
    }

    // Guardar la lista de clicks
    public static void save(Context c, List<ClickItem> list) {
        JSONArray a = new JSONArray();
        for (ClickItem it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("text", it.text);
                o.put("hour", it.hour);
                o.put("minute", it.minute);
                a.put(o);
            } catch (JSONException e) {
                // se omite este click
            }
        }
        prefs(c).edit().putString("items", a.toString()).apply();
    }

    public static void log(Context c, String msg) {
        String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = stamp + "  " + msg;
        String old = prefs(c).getString("log", "");
        String all = old.isEmpty() ? line : old + "\n" + line;
        String[] parts = all.split("\n");
        if (parts.length > MAX_LOG) {
            all = TextUtils.join("\n", Arrays.copyOfRange(parts, parts.length - MAX_LOG, parts.length));
        }
        prefs(c).edit().putString("log", all).apply();
    }

    public static String readLog(Context c) {
        String s = prefs(c).getString("log", "");
        return s.isEmpty() ? "Sin registros todavía" : s;
    }

    public static void clearLog(Context c) {
        prefs(c).edit().remove("log").apply();
    }
}
