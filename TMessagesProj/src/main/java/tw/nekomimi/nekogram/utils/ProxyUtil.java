package tw.nekomimi.nekogram.utils;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XraySubscriptionStore;
import org.telegram.messenger.XraySubscriptionWorkScheduler;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.utils.proxy.XrayOutboundUrlParser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ProxyUtil {
    private static final Pattern IPV6_PATTERN = Pattern.compile("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$");
    private static final String PREF_HWID_VALUE = "proxy_hwid";
    private static final String PREF_SUBSCRIPTION_USER_AGENT = "proxy_subscription_user_agent";
    public static final String USER_AGENT_VXGRAM = "vXGram";
    public static final String USER_AGENT_HAPP = "Happ/3.10.0";

    private static class ParseResult {
        final ArrayList<SharedConfig.ProxyInfo> proxies;
        final boolean error;

        ParseResult(ArrayList<SharedConfig.ProxyInfo> proxies, boolean error) {
            this.proxies = proxies;
            this.error = error;
        }
    }

    private static class FetchResult {
        final ParseResult parseResult;
        final XraySubscriptionStore.Entry entry;

        FetchResult(ParseResult parseResult, XraySubscriptionStore.Entry entry) {
            this.parseResult = parseResult;
            this.entry = entry;
        }
    }

    private static void showToast(CharSequence text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show());
    }

    private static void showImportedDialog(Activity ctx, List<SharedConfig.ProxyInfo> proxies) {
        if (ctx == null) {
            return;
        }
        StringBuilder message = new StringBuilder(LocaleController.getString(R.string.ImportedProxies));
        message.append("\n\n");
        for (int i = 0; i < proxies.size(); i++) {
            if (i > 0) {
                message.append("\n");
            }
            message.append(proxies.get(i).settings.getAddress());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setMessage(message.toString());
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.show();
    }

    public static void importFromClipboard(Activity ctx) {
        if (ctx == null) {
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        String text = null;
        if (clipboardManager != null && clipboardManager.getPrimaryClip() != null && clipboardManager.getPrimaryClip().getItemCount() > 0) {
            CharSequence clipText = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(ApplicationLoader.applicationContext);
            if (clipText != null) {
                text = clipText.toString();
            }
        }

        ParseResult result = parseProxyText(text);
        if (result.proxies.isEmpty()) {
            if (!result.error) {
                showToast(LocaleController.getString(R.string.BrokenLink));
            }
            return;
        } else if (!result.error) {
            showImportedDialog(ctx, result.proxies);
        }

        for (SharedConfig.ProxyInfo info : result.proxies) {
            SharedConfig.addProxy(info);
        }

        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged));
    }

    public static void importFromUrl(Activity ctx, String url, boolean saveSubscription) {
        importSubscriptionWithOptions(ctx, url, null, false, 0, saveSubscription);
    }

    public static void importSubscriptionWithOptions(Activity ctx, String url, String remark, boolean autoUpdate, int intervalMinutes, boolean saveSubscription) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String normalizedUrl = normalizeSubscriptionUrl(url);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("proxy_sub: import url=" + normalizedUrl);
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                FetchResult fetchResult = fetchSubscription(normalizedUrl, remark, autoUpdate, intervalMinutes, saveSubscription);
                if (fetchResult == null || fetchResult.parseResult.proxies.isEmpty()) {
                    if (fetchResult == null || !fetchResult.parseResult.error) {
                        AndroidUtilities.runOnUIThread(() -> showToast(LocaleController.getString(R.string.BrokenLink)));
                    }
                    return;
                }
                if (!containsXrayProxy(fetchResult.parseResult.proxies)) {
                    AndroidUtilities.runOnUIThread(() -> showToast(LocaleController.getString(R.string.ProxySubscriptionNoXray)));
                    return;
                }
                String subscriptionName = fetchResult.entry.getDisplayTitle();
                if (saveSubscription) {
                    addSubscription(normalizedUrl);
                    XraySubscriptionStore.save(fetchResult.entry);
                    XraySubscriptionWorkScheduler.syncAll();
                }
                for (SharedConfig.ProxyInfo info : fetchResult.parseResult.proxies) {
                    if (saveSubscription) {
                        info.subscriptionName = subscriptionName;
                    }
                    SharedConfig.addProxy(info, saveSubscription);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    if (!fetchResult.parseResult.error) {
                        showToast(LocaleController.getString(R.string.ProxySubscriptionAdded));
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> showToast(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        });
    }

    public static boolean refreshSubscriptionUrlSync(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String normalizedUrl = normalizeSubscriptionUrl(url);
        XraySubscriptionStore.Entry existing = XraySubscriptionStore.getByUrl(normalizedUrl);
        try {
            FetchResult fetchResult = fetchSubscription(
                    normalizedUrl,
                    existing != null ? existing.remark : null,
                    existing != null && existing.autoUpdate,
                    existing != null ? existing.updateIntervalMinutes : 0,
                    true
            );
            if (fetchResult == null || fetchResult.parseResult.proxies.isEmpty()) {
                return false;
            }
            String title = fetchResult.entry.getDisplayTitle();
            String defaultTitle = LocaleController.getString(R.string.ProxyCategorySubscriptions);
            for (SharedConfig.ProxyInfo info : SharedConfig.getProxyList()) {
                if (!info.isSubscription) {
                    continue;
                }
                String groupTitle = TextUtils.isEmpty(info.subscriptionName) ? defaultTitle : info.subscriptionName;
                if (TextUtils.equals(groupTitle, title)) {
                    SharedConfig.deleteProxy(info);
                }
            }
            for (SharedConfig.ProxyInfo info : fetchResult.parseResult.proxies) {
                info.subscriptionName = title;
                SharedConfig.addProxy(info, true);
            }
            XraySubscriptionStore.save(fetchResult.entry);
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged));
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void refreshSubscriptions(Activity ctx) {
        List<String> urls = getSubscriptions();
        if (urls.isEmpty()) {
            showToast(LocaleController.getString(R.string.ProxySubscriptionEmpty));
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("proxy_sub: refresh count=" + urls.size());
        }
        Utilities.globalQueue.postRunnable(() -> {
            final boolean[] any = new boolean[]{false};
            for (String url : urls) {
                if (refreshSubscriptionUrlSync(url)) {
                    any[0] = true;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (any[0]) {
                    showToast(LocaleController.getString(R.string.ProxySubscriptionUpdated));
                } else {
                    showToast(LocaleController.getString(R.string.BrokenLink));
                }
            });
        });
    }

    public static void refreshSubscriptionsByTitle(Activity ctx, String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        List<String> urls = getSubscriptions();
        if (urls.isEmpty()) {
            showToast(LocaleController.getString(R.string.ProxySubscriptionEmpty));
            return;
        }
        String defaultTitle = LocaleController.getString(R.string.ProxyCategorySubscriptions);
        ArrayList<String> targetUrls = new ArrayList<>();
        for (String url : urls) {
            if (TextUtils.equals(getSubscriptionTitle(url), title)) {
                targetUrls.add(url);
            }
        }
        if (targetUrls.isEmpty()) {
            if (TextUtils.equals(title, defaultTitle)) {
                refreshSubscriptions(ctx);
            } else {
                showToast(LocaleController.getString(R.string.BrokenLink));
            }
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("proxy_sub: refresh group=" + title + " count=" + targetUrls.size());
        }
        Utilities.globalQueue.postRunnable(() -> {
            final boolean[] any = new boolean[]{false};
            for (String url : targetUrls) {
                if (refreshSubscriptionUrlSync(url)) {
                    any[0] = true;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (any[0]) {
                    showToast(LocaleController.getString(R.string.ProxySubscriptionUpdated));
                } else {
                    showToast(LocaleController.getString(R.string.BrokenLink));
                }
            });
        });
    }

    private static FetchResult fetchSubscription(String normalizedUrl, String remark, boolean autoUpdate, int intervalMinutes, boolean persistOptions) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(normalizedUrl).openConnection();
            applyHwidHeaders(conn);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            String text;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append('\n');
                }
                text = buffer.toString();
            }
            XraySubscriptionStore.Entry entry = XraySubscriptionStore.getByUrl(normalizedUrl);
            if (entry == null) {
                entry = new XraySubscriptionStore.Entry();
                entry.url = normalizedUrl;
            }
            if (persistOptions) {
                if (!TextUtils.isEmpty(remark)) {
                    entry.remark = remark;
                }
                entry.autoUpdate = autoUpdate;
                if (intervalMinutes > 0) {
                    entry.updateIntervalMinutes = Math.max(15, intervalMinutes);
                } else if (entry.updateIntervalMinutes <= 0) {
                    entry.updateIntervalMinutes = 1440;
                }
            }
            applySubscriptionHeaders(entry, conn);
            ParseResult result = parseProxyText(text);
            applySubscriptionJsonMetadata(entry, text);
            entry.configCount = result.proxies.size();
            entry.lastUpdateTime = System.currentTimeMillis();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("proxy_sub: fetch url=" + normalizedUrl + " bytes=" + text.length() + " proxies=" + result.proxies.size());
            }
            return new FetchResult(result, entry);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void applySubscriptionHeaders(XraySubscriptionStore.Entry entry, HttpURLConnection conn) {
        if (entry == null || conn == null) {
            return;
        }
        XraySubscriptionStore.applyUserInfo(entry, conn.getHeaderField("subscription-userinfo"));
        String title = decodeHeaderValue(conn.getHeaderField("profile-title"));
        if (!TextUtils.isEmpty(title)) {
            entry.title = title;
        }
        String interval = conn.getHeaderField("profile-update-interval");
        if (!TextUtils.isEmpty(interval)) {
            try {
                int value = Integer.parseInt(interval.trim());
                if (value > 0 && value <= 168) {
                    entry.updateIntervalMinutes = value * 60;
                } else if (value >= 15) {
                    entry.updateIntervalMinutes = value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String announce = decodeHeaderValue(conn.getHeaderField("announce"));
        if (!TextUtils.isEmpty(announce)) {
            entry.announce = announce;
        }
        String support = conn.getHeaderField("support-url");
        if (!TextUtils.isEmpty(support)) {
            entry.supportUrl = support.trim();
        }
        String web = conn.getHeaderField("profile-web-page-url");
        if (!TextUtils.isEmpty(web)) {
            entry.webPageUrl = web.trim();
        }
    }

    private static void applySubscriptionJsonMetadata(XraySubscriptionStore.Entry entry, String text) {
        if (entry == null || TextUtils.isEmpty(text)) {
            return;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(trimmed);
            if (obj.has("outbounds") || obj.has("vless") || obj.optJSONArray("outbounds") != null) {
                return;
            }
            if (obj.has("upload") || obj.has("download") || obj.has("total") || obj.has("expire")) {
                entry.upload = obj.optLong("upload", entry.upload);
                entry.download = obj.optLong("download", entry.download);
                entry.total = obj.optLong("total", entry.total);
                entry.expire = obj.optLong("expire", entry.expire);
            }
            if (obj.has("title") || obj.has("profile-title")) {
                String title = obj.optString("title", obj.optString("profile-title", ""));
                if (!TextUtils.isEmpty(title)) {
                    entry.title = title;
                }
            }
            if (obj.has("announce")) {
                entry.announce = obj.optString("announce", entry.announce);
            }
            if (obj.has("support-url")) {
                entry.supportUrl = obj.optString("support-url", entry.supportUrl);
            }
            if (obj.has("profile-web-page-url")) {
                entry.webPageUrl = obj.optString("profile-web-page-url", entry.webPageUrl);
            }
            if (obj.has("profile-update-interval")) {
                int value = obj.optInt("profile-update-interval", 0);
                if (value > 0 && value <= 168) {
                    entry.updateIntervalMinutes = value * 60;
                } else if (value >= 15) {
                    entry.updateIntervalMinutes = value;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String decodeHeaderValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String trimmed = value.trim();
        try {
            byte[] decoded = Base64.decode(trimmed, Base64.DEFAULT);
            String asText = new String(decoded, "UTF-8").trim();
            if (!TextUtils.isEmpty(asText)) {
                return asText;
            }
        } catch (Throwable ignored) {
        }
        return trimmed;
    }

    public static boolean isXrayProxy(SharedConfig.ProxyInfo info) {
        return info != null && info.isXrayVless();
    }

    private static boolean containsXrayProxy(List<SharedConfig.ProxyInfo> proxies) {
        if (proxies == null || proxies.isEmpty()) {
            return false;
        }
        for (SharedConfig.ProxyInfo info : proxies) {
            if (isXrayProxy(info)) {
                return true;
            }
        }
        return false;
    }

    private static ParseResult parseProxyText(String text) {
        ArrayList<SharedConfig.ProxyInfo> proxies = new ArrayList<>();
        final boolean[] error = new boolean[]{false};

        java.util.function.Consumer<String> handleLine = (line) -> {
            String token = normalizeShareToken(line);
            if (TextUtils.isEmpty(token) || !looksLikeShareLink(token)) {
                return;
            }
            try {
                proxies.add(SharedConfig.ProxyInfo.fromUrl(token));
            } catch (Throwable e) {
                error[0] = true;
                FileLog.e("proxy_sub: skip link " + token, e);
            }
        };

        if (text != null) {
            for (String token : extractShareLinks(text)) {
                handleLine.accept(token);
            }
        }

        if (text != null) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[") || trimmed.startsWith("{") || text.contains("\"outbounds\"") || text.contains("\"vless\"") || text.contains("\"vmess\"") || text.contains("\"trojan\"")) {
                proxies.addAll(parseXrayJson(text));
            }
        }

        if (proxies.isEmpty() && !error[0] && !TextUtils.isEmpty(text)) {
            try {
                String decoded = new String(Base64.decode(text, Base64.NO_PADDING));
                for (String token : extractShareLinks(decoded)) {
                    handleLine.accept(token);
                }
                if (decoded.trim().startsWith("[") || decoded.trim().startsWith("{")) {
                    proxies.addAll(parseXrayJson(decoded));
                }
            } catch (Throwable ignored) {
            }
        }

        FileLog.d("proxy_sub: parse result proxies=" + proxies.size() + " error=" + error[0]);
        return new ParseResult(proxies, error[0]);
    }

    private static String normalizeShareToken(String line) {
        if (TextUtils.isEmpty(line)) {
            return "";
        }
        String token = line.trim();
        while (token.length() >= 2 && ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'")))) {
            token = token.substring(1, token.length() - 1).trim();
        }
        while (token.endsWith(",") || token.endsWith(";")) {
            token = token.substring(0, token.length() - 1).trim();
        }
        return token;
    }

    private static boolean looksLikeShareLink(String token) {
        String lower = token.toLowerCase(Locale.US);
        return lower.startsWith("tg://proxy")
                || lower.startsWith("tg://socks")
                || lower.startsWith("tg://webproxy")
                || lower.startsWith("tg:proxy")
                || lower.startsWith("tg:socks")
                || lower.startsWith("https://t.me/proxy")
                || lower.startsWith("https://t.me/socks")
                || lower.startsWith("https://t.me/webproxy")
                || lower.startsWith("http://t.me/proxy")
                || lower.startsWith("http://t.me/socks")
                || lower.startsWith("vless://")
                || lower.startsWith("vmess://")
                || lower.startsWith("trojan://")
                || lower.startsWith("ss://")
                || lower.startsWith("socks://")
                || lower.startsWith("socks5://")
                || lower.startsWith("wg://")
                || lower.startsWith("hysteria://")
                || lower.startsWith("hysteria2://")
                || lower.startsWith("hy2://")
                || ((lower.startsWith("http://") || lower.startsWith("https://")) && token.contains("@") && !lower.contains("t.me/"));
    }

    private static ArrayList<String> extractShareLinks(String text) {
        ArrayList<String> links = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return links;
        }
        String[] schemes = new String[]{
                "vless://", "vmess://", "trojan://", "ss://", "socks://", "socks5://",
                "wg://", "hysteria://", "hysteria2://", "hy2://",
                "tg://proxy", "tg://socks", "tg://webproxy",
                "tg:proxy", "tg:socks",
                "https://t.me/proxy", "https://t.me/socks", "https://t.me/webproxy",
                "http://t.me/proxy", "http://t.me/socks"
        };
        String lower = text.toLowerCase(Locale.US);
        int index = 0;
        while (index < text.length()) {
            int found = -1;
            int schemeLen = 0;
            for (String scheme : schemes) {
                int at = lower.indexOf(scheme, index);
                if (at >= 0 && (found < 0 || at < found)) {
                    found = at;
                    schemeLen = scheme.length();
                }
            }
            if (found < 0) {
                break;
            }
            int end = found + schemeLen;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == ']' || c == '}' || c == '<' || c == '>') {
                    break;
                }
                end++;
            }
            String token = normalizeShareToken(text.substring(found, end));
            if (looksLikeShareLink(token)) {
                links.add(token);
            }
            index = Math.max(found + 1, end);
        }
        return links;
    }

    private static ArrayList<SharedConfig.ProxyInfo> parseXrayJson(String text) {
        ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return result;
        }
        String json = extractJsonPayload(text);
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    String asString = array.optString(i, null);
                    if (!TextUtils.isEmpty(asString) && looksLikeShareLink(normalizeShareToken(asString))) {
                        try {
                            result.add(SharedConfig.ProxyInfo.fromUrl(asString));
                        } catch (Throwable ignored) {
                        }
                        continue;
                    }
                    JSONObject obj = array.optJSONObject(i);
                    if (obj != null) {
                        result.addAll(parseXrayConfig(obj));
                    }
                }
            } else if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                result.addAll(parseXrayConfig(obj));
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static String extractJsonPayload(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return trimmed;
        }
        int arrayIdx = trimmed.indexOf('[');
        int objIdx = trimmed.indexOf('{');
        int idx = -1;
        if (arrayIdx >= 0 && objIdx >= 0) {
            idx = Math.min(arrayIdx, objIdx);
        } else if (arrayIdx >= 0) {
            idx = arrayIdx;
        } else if (objIdx >= 0) {
            idx = objIdx;
        }
        if (idx >= 0 && idx < trimmed.length()) {
            return trimmed.substring(idx);
        }
        return "";
    }

    private static ArrayList<SharedConfig.ProxyInfo> parseXrayConfig(JSONObject config) {
        ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        String remark = config.optString("remarks", config.optString("ps", ""));
        JSONArray outbounds = config.optJSONArray("outbounds");
        if (outbounds != null && outbounds.length() > 0) {
            for (int i = 0; i < outbounds.length(); i++) {
                SharedConfig.ProxyInfo info = parseXrayOutbound(outbounds.optJSONObject(i), remark);
                if (info != null) {
                    result.add(info);
                }
            }
            return result;
        }
        SharedConfig.ProxyInfo single = parseXrayOutbound(config, remark);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    private static SharedConfig.ProxyInfo parseXrayOutbound(JSONObject outbound, String remark) {
        if (outbound == null) {
            return null;
        }
        String protocol = outbound.optString("protocol", "");
        if (!XrayOutboundUrlParser.isSupportedOutboundProtocol(protocol)) {
            return null;
        }
        if ("vless".equalsIgnoreCase(protocol)) {
            SharedConfig.ProxyInfo vless = parseVlessOutbound(outbound, remark);
            if (vless != null) {
                return vless;
            }
        }
        try {
            return XrayOutboundUrlParser.fromOutboundJson(outbound, remark);
        } catch (Throwable e) {
            FileLog.e("proxy_sub: skip outbound " + protocol, e);
            return null;
        }
    }

    private static SharedConfig.ProxyInfo parseVlessOutbound(JSONObject outbound, String remark) {
        JSONObject settings = outbound.optJSONObject("settings");
        JSONArray vnext = settings != null ? settings.optJSONArray("vnext") : null;
        JSONObject server = vnext != null && vnext.length() > 0 ? vnext.optJSONObject(0) : null;
        if (server == null) {
            return null;
        }
        String address = server.optString("address", "");
        int port = server.optInt("port", 0);
        JSONArray users = server.optJSONArray("users");
        JSONObject user = users != null && users.length() > 0 ? users.optJSONObject(0) : null;
        if (TextUtils.isEmpty(address) || port <= 0 || user == null) {
            return null;
        }
        SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                ProxySettings.builder()
                        .setType(ProxySettings.Type.XRAY_VLESS)
                        .setAddress(address)
                        .setPort(port)
                        .build()
        );
        info.vlessId = user.optString("id", "");
        info.vlessEncryption = user.optString("encryption", "none");
        info.vlessFlow = user.optString("flow", "");
        if (!TextUtils.isEmpty(remark)) {
            info.vlessRemark = remark;
        }

        JSONObject stream = outbound.optJSONObject("streamSettings");
        if (stream != null) {
            info.vlessType = stream.optString("network", "");
            info.vlessSecurity = stream.optString("security", "");

            JSONObject tls = stream.optJSONObject("tlsSettings");
            if (tls != null) {
                info.vlessSni = tls.optString("serverName", info.vlessSni);
                info.vlessFp = tls.optString("fingerprint", info.vlessFp);
                JSONArray alpn = tls.optJSONArray("alpn");
                if (alpn != null && alpn.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < alpn.length(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(alpn.optString(i));
                    }
                    info.vlessAlpn = sb.toString();
                }
                info.vlessAllowInsecure = tls.optBoolean("allowInsecure", info.vlessAllowInsecure);
            }

            JSONObject reality = stream.optJSONObject("realitySettings");
            if (reality != null) {
                info.vlessSni = reality.optString("serverName", info.vlessSni);
                info.vlessFp = reality.optString("fingerprint", info.vlessFp);
                info.vlessPublicKey = reality.optString("publicKey", info.vlessPublicKey);
                info.vlessShortId = reality.optString("shortId", info.vlessShortId);
                info.vlessSpiderX = reality.optString("spiderX", info.vlessSpiderX);
            }

            JSONObject ws = stream.optJSONObject("wsSettings");
            if (ws != null) {
                info.vlessPath = ws.optString("path", info.vlessPath);
                JSONObject headers = ws.optJSONObject("headers");
                if (headers != null) {
                    info.vlessHost = headers.optString("Host", info.vlessHost);
                }
            }

            JSONObject grpc = stream.optJSONObject("grpcSettings");
            if (grpc != null) {
                info.vlessServiceName = grpc.optString("serviceName", info.vlessServiceName);
                if (grpc.optBoolean("multiMode", false)) {
                    info.vlessMode = "multi";
                }
            }

            JSONObject http = stream.optJSONObject("httpSettings");
            if (http != null) {
                info.vlessPath = http.optString("path", info.vlessPath);
                JSONArray host = http.optJSONArray("host");
                if (host != null && host.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < host.length(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(host.optString(i));
                    }
                    info.vlessHost = sb.toString();
                }
            }

            JSONObject kcp = stream.optJSONObject("kcpSettings");
            if (kcp != null) {
                info.vlessSeed = kcp.optString("seed", info.vlessSeed);
                JSONObject header = kcp.optJSONObject("header");
                if (header != null) {
                    info.vlessHeaderType = header.optString("type", info.vlessHeaderType);
                }
            }

            JSONObject quic = stream.optJSONObject("quicSettings");
            if (quic != null) {
                info.vlessQuicSecurity = quic.optString("security", info.vlessQuicSecurity);
                info.vlessQuicKey = quic.optString("key", info.vlessQuicKey);
                JSONObject header = quic.optJSONObject("header");
                if (header != null) {
                    info.vlessHeaderType = header.optString("type", info.vlessHeaderType);
                }
            }
        }
        info.normalizeVlessFields();
        return info;
    }

    private static List<String> getSubscriptions() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        String value = prefs.getString("proxy_subscriptions", "");
        if (value == null) {
            return new ArrayList<>();
        }
        String[] lines = value.split("\n");
        Set<String> result = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(normalizeSubscriptionUrl(trimmed));
            }
        }
        return new ArrayList<>(result);
    }

    private static void addSubscription(String url) {
        String normalizedUrl = normalizeSubscriptionUrl(url);
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        List<String> current = getSubscriptions();
        if (!current.contains(normalizedUrl)) {
            current.add(normalizedUrl);
            prefs.edit().putString("proxy_subscriptions", TextUtils.join("\n", current)).apply();
        }
    }

    private static String normalizeSubscriptionUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String trimmed = url.trim();
        if (!trimmed.contains("://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    private static String getSubscriptionTitle(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String normalized = normalizeSubscriptionUrl(url);
        XraySubscriptionStore.Entry entry = XraySubscriptionStore.getByUrl(normalized);
        if (entry != null) {
            String display = entry.getDisplayTitle();
            if (!TextUtils.isEmpty(display)) {
                return display;
            }
        }
        Uri uri = Uri.parse(normalized);
        String host = uri != null ? uri.getHost() : null;
        if (TextUtils.isEmpty(host)) {
            return normalized;
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        return host;
    }

    public static boolean isIpv6Address(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        String addr = value;
        if (addr.startsWith("[") && addr.contains("]")) {
            int end = addr.lastIndexOf("]");
            addr = addr.substring(1, end);
        }
        return IPV6_PATTERN.matcher(addr).matches();
    }

    public static boolean removeSubscriptionsByTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return false;
        }
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        List<String> current = getSubscriptions();
        if (current.isEmpty()) {
            return false;
        }
        String defaultTitle = LocaleController.getString(R.string.ProxyCategorySubscriptions);
        if (TextUtils.equals(title, defaultTitle)) {
            prefs.edit().putString("proxy_subscriptions", "").apply();
            for (XraySubscriptionStore.Entry entry : new ArrayList<>(XraySubscriptionStore.getAll())) {
                XraySubscriptionWorkScheduler.cancel(entry.url);
            }
            XraySubscriptionStore.clearAll();
            XraySubscriptionWorkScheduler.syncAll();
            return true;
        }
        boolean changed = false;
        ArrayList<String> keep = new ArrayList<>();
        for (String url : current) {
            String name = getSubscriptionTitle(url);
            if (TextUtils.equals(name, title)) {
                changed = true;
                XraySubscriptionWorkScheduler.cancel(url);
                continue;
            }
            keep.add(url);
        }
        if (changed) {
            prefs.edit().putString("proxy_subscriptions", TextUtils.join("\n", keep)).apply();
            XraySubscriptionStore.removeByDisplayTitle(title);
            XraySubscriptionWorkScheduler.syncAll();
        }
        return changed;
    }

    public static boolean isHwidModeEnabled() {
        return true;
    }

    public static void setHwidModeEnabled(boolean enabled) {
        getOrCreateHwid();
    }

    public static String getSubscriptionUserAgent() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        String ua = prefs.getString(PREF_SUBSCRIPTION_USER_AGENT, USER_AGENT_VXGRAM);
        if (TextUtils.isEmpty(ua)) {
            return USER_AGENT_VXGRAM;
        }
        if (USER_AGENT_HAPP.equals(ua) || USER_AGENT_VXGRAM.equals(ua)) {
            return ua;
        }
        return USER_AGENT_VXGRAM;
    }

    public static void setSubscriptionUserAgent(String userAgent) {
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = USER_AGENT_VXGRAM;
        }
        if (!USER_AGENT_HAPP.equals(userAgent) && !USER_AGENT_VXGRAM.equals(userAgent)) {
            userAgent = USER_AGENT_VXGRAM;
        }
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_SUBSCRIPTION_USER_AGENT, userAgent).apply();
    }

    private static void applyHwidHeaders(HttpURLConnection conn) {
        if (conn == null) {
            return;
        }
        String locale = getDeviceLocale();
        String hwid = getOrCreateHwid();
        String osVersion = getOsVersion();
        String model = getDeviceModel();
        conn.setRequestProperty("user-agent", getSubscriptionUserAgent());
        conn.setRequestProperty("x-device-locale", locale);
        conn.setRequestProperty("x-hwid", hwid);
        conn.setRequestProperty("x-device-os", "Android");
        conn.setRequestProperty("x-ver-os", osVersion);
        conn.setRequestProperty("x-device-model", model);
    }

    private static String getOrCreateHwid() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        String hwid = prefs.getString(PREF_HWID_VALUE, "");
        if (TextUtils.isEmpty(hwid)) {
            hwid = generateHwid();
            prefs.edit().putString(PREF_HWID_VALUE, hwid).apply();
        }
        return hwid;
    }

    private static String generateHwid() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        return Utilities.bytesToHex(bytes).toLowerCase(Locale.US);
    }

    private static String getDeviceLocale() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        String language = locale != null ? locale.getLanguage() : null;
        return TextUtils.isEmpty(language) ? "en" : language;
    }

    private static String getOsVersion() {
        String version = Build.VERSION.RELEASE;
        if (TextUtils.isEmpty(version)) {
            version = String.valueOf(Build.VERSION.SDK_INT);
        }
        return version;
    }

    private static String getDeviceModel() {
        String model = Build.MODEL;
        if (TextUtils.isEmpty(model)) {
            model = Build.DEVICE;
        }
        return TextUtils.isEmpty(model) ? "Android" : model;
    }

}
