package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class XraySubscriptionStore {

    private static final String PREF_NAME = "xray_subscription_store";
    private static final String KEY_ENTRIES = "entries";

    private XraySubscriptionStore() {
    }

    public static class Entry {
        public String url = "";
        public String remark = "";
        public String title = "";
        public long upload;
        public long download;
        public long total;
        public long expire;
        public String announce = "";
        public String supportUrl = "";
        public String webPageUrl = "";
        public int updateIntervalMinutes;
        public long lastUpdateTime;
        public int configCount;
        public boolean autoUpdate;
        public boolean pinned;
        public String sortMode = "added";

        public String getDisplayTitle() {
            if (!TextUtils.isEmpty(remark)) {
                return remark;
            }
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            return url;
        }
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static List<Entry> getAll() {
        ArrayList<Entry> result = new ArrayList<>();
        String raw = prefs().getString(KEY_ENTRIES, "[]");
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                Entry entry = fromJson(obj);
                if (entry != null && !TextUtils.isEmpty(entry.url)) {
                    result.add(entry);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    public static Entry getByUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String normalized = normalizeUrl(url);
        for (Entry entry : getAll()) {
            if (TextUtils.equals(normalizeUrl(entry.url), normalized)) {
                return entry;
            }
        }
        return null;
    }

    public static Entry getByDisplayTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        for (Entry entry : getAll()) {
            if (TextUtils.equals(entry.getDisplayTitle(), title)) {
                return entry;
            }
        }
        return null;
    }

    public static void save(Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url)) {
            return;
        }
        entry.url = normalizeUrl(entry.url);
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry existing : getAll()) {
            map.put(normalizeUrl(existing.url), existing);
        }
        map.put(entry.url, entry);
        persist(new ArrayList<>(map.values()));
    }

    public static void removeByUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String normalized = normalizeUrl(url);
        ArrayList<Entry> keep = new ArrayList<>();
        for (Entry entry : getAll()) {
            if (!TextUtils.equals(normalizeUrl(entry.url), normalized)) {
                keep.add(entry);
            }
        }
        persist(keep);
    }

    public static void removeByDisplayTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        ArrayList<Entry> keep = new ArrayList<>();
        for (Entry entry : getAll()) {
            if (!TextUtils.equals(entry.getDisplayTitle(), title)) {
                keep.add(entry);
            }
        }
        persist(keep);
    }

    public static void clearAll() {
        persist(new ArrayList<>());
    }

    public static String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String trimmed = url.trim();
        if (!trimmed.contains("://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    private static void persist(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            array.put(toJson(entry));
        }
        prefs().edit().putString(KEY_ENTRIES, array.toString()).apply();
    }

    private static Entry fromJson(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        Entry entry = new Entry();
        entry.url = obj.optString("url", "");
        entry.remark = obj.optString("remark", "");
        entry.title = obj.optString("title", "");
        entry.upload = obj.optLong("upload", 0);
        entry.download = obj.optLong("download", 0);
        entry.total = obj.optLong("total", 0);
        entry.expire = obj.optLong("expire", 0);
        entry.announce = obj.optString("announce", "");
        entry.supportUrl = obj.optString("supportUrl", "");
        entry.webPageUrl = obj.optString("webPageUrl", "");
        entry.updateIntervalMinutes = obj.optInt("updateIntervalMinutes", 0);
        entry.lastUpdateTime = obj.optLong("lastUpdateTime", 0);
        entry.configCount = obj.optInt("configCount", 0);
        entry.autoUpdate = obj.optBoolean("autoUpdate", false);
        entry.pinned = obj.optBoolean("pinned", false);
        entry.sortMode = obj.optString("sortMode", "added");
        if (TextUtils.isEmpty(entry.sortMode)) {
            entry.sortMode = "added";
        }
        return entry;
    }

    private static JSONObject toJson(Entry entry) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("url", entry.url != null ? entry.url : "");
            obj.put("remark", entry.remark != null ? entry.remark : "");
            obj.put("title", entry.title != null ? entry.title : "");
            obj.put("upload", entry.upload);
            obj.put("download", entry.download);
            obj.put("total", entry.total);
            obj.put("expire", entry.expire);
            obj.put("announce", entry.announce != null ? entry.announce : "");
            obj.put("supportUrl", entry.supportUrl != null ? entry.supportUrl : "");
            obj.put("webPageUrl", entry.webPageUrl != null ? entry.webPageUrl : "");
            obj.put("updateIntervalMinutes", entry.updateIntervalMinutes);
            obj.put("lastUpdateTime", entry.lastUpdateTime);
            obj.put("configCount", entry.configCount);
            obj.put("autoUpdate", entry.autoUpdate);
            obj.put("pinned", entry.pinned);
            obj.put("sortMode", entry.sortMode != null ? entry.sortMode : "added");
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return obj;
    }

    public static void applyUserInfo(Entry entry, String userInfoHeader) {
        if (entry == null || TextUtils.isEmpty(userInfoHeader)) {
            return;
        }
        String[] parts = userInfoHeader.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().toLowerCase(Locale.US);
            String value = kv[1].trim();
            try {
                long num = Long.parseLong(value);
                switch (key) {
                    case "upload":
                        entry.upload = num;
                        break;
                    case "download":
                        entry.download = num;
                        break;
                    case "total":
                        entry.total = num;
                        break;
                    case "expire":
                        entry.expire = num;
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
