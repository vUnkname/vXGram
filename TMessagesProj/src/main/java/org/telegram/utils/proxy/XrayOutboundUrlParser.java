package org.telegram.utils.proxy;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.SharedConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class XrayOutboundUrlParser {

    private XrayOutboundUrlParser() {
    }

    public static SharedConfig.ProxyInfo parse(String url) {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("empty url");
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        try {
        if (lower.startsWith("vmess://")) {
            return fromVmess(trimmed);
        }
        if (lower.startsWith("trojan://")) {
            return fromTrojan(trimmed);
        }
        if (lower.startsWith("ss://")) {
            return fromShadowsocks(trimmed);
        }
        if (lower.startsWith("socks://") || lower.startsWith("socks5://")) {
            return fromSocks(trimmed);
        }
        if (lower.startsWith("http://") && trimmed.contains("@")) {
            return fromHttpProxy(trimmed);
        }
        if (lower.startsWith("https://") && trimmed.contains("@") && !lower.contains("t.me/")) {
            return fromHttpProxy(trimmed);
        }
        if (lower.startsWith("wg://")) {
            return fromWireGuard(trimmed);
        }
        if (lower.startsWith("hysteria2://") || lower.startsWith("hy2://")) {
            return fromHysteria2(trimmed);
        }
        if (lower.startsWith("hysteria://")) {
            return fromHysteria(trimmed);
        }
        throw new IllegalArgumentException(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException(trimmed, e);
        }
    }

    public static SharedConfig.ProxyInfo fromOutboundJson(JSONObject outbound, String remark) throws Exception {
        if (outbound == null) {
            throw new IllegalArgumentException("empty outbound");
        }
        String protocol = outbound.optString("protocol", "");
        if (TextUtils.isEmpty(protocol) || !isSupportedOutboundProtocol(protocol)) {
            throw new IllegalArgumentException(protocol);
        }
        String address = "";
        int port = 0;
        JSONObject settings = outbound.optJSONObject("settings");
        if (settings != null) {
            JSONArray vnext = settings.optJSONArray("vnext");
            JSONObject server = vnext != null && vnext.length() > 0 ? vnext.optJSONObject(0) : null;
            if (server == null) {
                JSONArray servers = settings.optJSONArray("servers");
                server = servers != null && servers.length() > 0 ? servers.optJSONObject(0) : null;
            }
            if (server != null) {
                address = server.optString("address", "");
                port = server.optInt("port", 0);
            }
            if (TextUtils.isEmpty(address)) {
                JSONArray peers = settings.optJSONArray("peers");
                JSONObject peer = peers != null && peers.length() > 0 ? peers.optJSONObject(0) : null;
                String endpoint = peer != null ? peer.optString("endpoint", "") : "";
                int colon = endpoint.lastIndexOf(':');
                if (colon > 0) {
                    address = endpoint.substring(0, colon);
                    try {
                        port = Integer.parseInt(endpoint.substring(colon + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (port <= 0) {
            port = 443;
        }
        JSONObject copy = new JSONObject(outbound.toString());
        if (!copy.has("tag")) {
            copy.put("tag", "proxy");
        }
        return generic(address, port, remark, copy);
    }

    public static boolean isSupportedOutboundProtocol(String protocol) {
        if (TextUtils.isEmpty(protocol)) {
            return false;
        }
        switch (protocol.toLowerCase(Locale.US)) {
            case "vless":
            case "vmess":
            case "trojan":
            case "shadowsocks":
            case "socks":
            case "http":
            case "wireguard":
            case "hysteria":
            case "hysteria2":
                return true;
            default:
                return false;
        }
    }

    private static SharedConfig.ProxyInfo generic(String address, int port, String remark, JSONObject outbound) {
        SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                ProxySettings.builder()
                        .setType(ProxySettings.Type.XRAY_VLESS)
                        .setAddress(address != null ? address : "")
                        .setPort(port > 0 ? port : 443)
                        .build()
        );
        if (!TextUtils.isEmpty(remark)) {
            info.vlessRemark = remark;
        }
        info.vlessAdvancedJson = outbound.toString();
        info.normalizeVlessFields();
        return info;
    }

    private static SharedConfig.ProxyInfo fromVmess(String url) throws Exception {
        String payload = url.substring("vmess://".length());
        int hashIdx = payload.indexOf('#');
        String encoded = hashIdx >= 0 ? payload.substring(0, hashIdx) : payload;
        String fragment = hashIdx >= 0 ? payload.substring(hashIdx + 1) : "";
        String jsonStr = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        JSONObject vmess = new JSONObject(jsonStr);
        String address = vmess.optString("add", "");
        int port = vmess.optInt("port", 443);
        String id = vmess.optString("id", "");
        String network = vmess.optString("net", "tcp");
        String tls = vmess.optString("tls", "");
        String host = vmess.optString("host", "");
        String path = vmess.optString("path", "");
        String sni = vmess.optString("sni", host);
        String remark = !TextUtils.isEmpty(fragment) ? decodeFragment(fragment) : vmess.optString("ps", "");

        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "vmess");
        JSONObject settings = new JSONObject();
        JSONObject user = new JSONObject();
        user.put("id", id);
        user.put("alterId", vmess.optInt("aid", 0));
        user.put("security", vmess.optString("scy", "auto"));
        settings.put("vnext", new JSONArray().put(new JSONObject()
                .put("address", address)
                .put("port", port)
                .put("users", new JSONArray().put(user))));
        outbound.put("settings", settings);

        JSONObject stream = new JSONObject();
        stream.put("network", "h2".equalsIgnoreCase(network) ? "http" : network);
        if ("tls".equalsIgnoreCase(tls) || "xtls".equalsIgnoreCase(tls)) {
            stream.put("security", "tls");
            JSONObject tlsSettings = new JSONObject();
            if (!TextUtils.isEmpty(sni)) {
                tlsSettings.put("serverName", sni);
            }
            String fp = vmess.optString("fp", "");
            if (!TextUtils.isEmpty(fp)) {
                tlsSettings.put("fingerprint", fp);
            }
            stream.put("tlsSettings", tlsSettings);
        } else {
            stream.put("security", "none");
        }
        applyTransport(stream, network, host, path, vmess.optString("type", ""));
        outbound.put("streamSettings", stream);
        return generic(address, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromTrojan(String url) throws Exception {
        Uri uri = Uri.parse(url);
        String password = uri.getUserInfo();
        String address = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        String remark = uri.getFragment() != null ? decodeFragment(uri.getFragment()) : "";
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "trojan");
        JSONObject settings = new JSONObject();
        JSONArray servers = new JSONArray().put(new JSONObject()
                .put("address", address)
                .put("password", password)
                .put("port", port));
        settings.put("servers", servers);
        outbound.put("settings", settings);
        JSONObject stream = new JSONObject();
        stream.put("network", "tcp");
        stream.put("security", "tls");
        JSONObject tls = new JSONObject();
        String sni = uri.getQueryParameter("sni");
        if (TextUtils.isEmpty(sni)) {
            sni = address;
        }
        tls.put("serverName", sni);
        String fp = uri.getQueryParameter("fp");
        if (!TextUtils.isEmpty(fp)) {
            tls.put("fingerprint", fp);
        }
        stream.put("tlsSettings", tls);
        String type = uri.getQueryParameter("type");
        String host = uri.getQueryParameter("host");
        String path = uri.getQueryParameter("path");
        if (!TextUtils.isEmpty(type)) {
            applyTransport(stream, type, host, path, "");
        }
        outbound.put("streamSettings", stream);
        return generic(address, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromShadowsocks(String url) throws Exception {
        String withoutScheme = url.substring("ss://".length());
        String remark = "";
        int hashIdx = withoutScheme.indexOf('#');
        if (hashIdx >= 0) {
            remark = decodeFragment(withoutScheme.substring(hashIdx + 1));
            withoutScheme = withoutScheme.substring(0, hashIdx);
        }
        String method;
        String password;
        String host;
        int port;
        if (withoutScheme.contains("@")) {
            String userInfoPart = withoutScheme.substring(0, withoutScheme.indexOf('@'));
            String hostPart = withoutScheme.substring(withoutScheme.indexOf('@') + 1);
            String decodedUser = new String(Base64.decode(userInfoPart, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8);
            int colon = decodedUser.indexOf(':');
            method = colon >= 0 ? decodedUser.substring(0, colon) : decodedUser;
            password = colon >= 0 ? decodedUser.substring(colon + 1) : "";
            Uri hostUri = Uri.parse("ss://" + hostPart);
            host = hostUri.getHost();
            port = hostUri.getPort();
        } else {
            String decoded = new String(Base64.decode(withoutScheme, Base64.DEFAULT), StandardCharsets.UTF_8);
            int at = decoded.lastIndexOf('@');
            if (at < 0) {
                throw new IllegalArgumentException(url);
            }
            String userPart = decoded.substring(0, at);
            String hostPart = decoded.substring(at + 1);
            int colon = userPart.indexOf(':');
            method = colon >= 0 ? userPart.substring(0, colon) : userPart;
            password = colon >= 0 ? userPart.substring(colon + 1) : "";
            int portColon = hostPart.lastIndexOf(':');
            host = portColon >= 0 ? hostPart.substring(0, portColon) : hostPart;
            port = portColon >= 0 ? Integer.parseInt(hostPart.substring(portColon + 1)) : 8388;
        }
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "shadowsocks");
        JSONObject settings = new JSONObject();
        settings.put("servers", new JSONArray().put(new JSONObject()
                .put("address", host)
                .put("port", port)
                .put("method", method)
                .put("password", password)));
        outbound.put("settings", settings);
        return generic(host, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromSocks(String url) throws Exception {
        Uri uri = Uri.parse(url.replace("socks5://", "socks://"));
        String address = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 1080;
        String user = uri.getUserInfo();
        String username = "";
        String password = "";
        if (!TextUtils.isEmpty(user)) {
            int colon = user.indexOf(':');
            if (colon >= 0) {
                username = user.substring(0, colon);
                password = user.substring(colon + 1);
            } else {
                username = user;
            }
        }
        String remark = uri.getFragment() != null ? decodeFragment(uri.getFragment()) : "";
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "socks");
        JSONObject settings = new JSONObject();
        JSONObject server = new JSONObject()
                .put("address", address)
                .put("port", port);
        if (!TextUtils.isEmpty(username)) {
            server.put("users", new JSONArray().put(new JSONObject()
                    .put("user", username)
                    .put("pass", password)));
        }
        settings.put("servers", new JSONArray().put(server));
        outbound.put("settings", settings);
        return generic(address, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromHttpProxy(String url) throws Exception {
        Uri uri = Uri.parse(url);
        String address = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String user = uri.getUserInfo();
        String username = "";
        String password = "";
        if (!TextUtils.isEmpty(user)) {
            int colon = user.indexOf(':');
            if (colon >= 0) {
                username = user.substring(0, colon);
                password = user.substring(colon + 1);
            } else {
                username = user;
            }
        }
        String remark = uri.getFragment() != null ? decodeFragment(uri.getFragment()) : "";
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "http");
        JSONObject settings = new JSONObject();
        JSONObject server = new JSONObject()
                .put("address", address)
                .put("port", port);
        if (!TextUtils.isEmpty(username)) {
            server.put("users", new JSONArray().put(new JSONObject()
                    .put("user", username)
                    .put("pass", password)));
        }
        settings.put("servers", new JSONArray().put(server));
        outbound.put("settings", settings);
        return generic(address, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromWireGuard(String url) throws Exception {
        Uri uri = Uri.parse(url);
        String privateKey = uri.getUserInfo();
        String address = uri.getQueryParameter("address");
        if (TextUtils.isEmpty(address)) {
            address = uri.getHost();
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 51820;
        String remark = uri.getFragment() != null ? decodeFragment(uri.getFragment()) : "";
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "wireguard");
        JSONObject settings = new JSONObject();
        JSONObject peer = new JSONObject();
        peer.put("publicKey", uri.getQueryParameter("publickey"));
        peer.put("endpoint", host + ":" + port);
        if (!TextUtils.isEmpty(uri.getQueryParameter("reserved"))) {
            peer.put("reserved", uri.getQueryParameter("reserved"));
        }
        settings.put("secretKey", privateKey);
        if (!TextUtils.isEmpty(address)) {
            settings.put("address", new JSONArray().put(address));
        }
        settings.put("peers", new JSONArray().put(peer));
        outbound.put("settings", settings);
        return generic(host, port, remark, outbound);
    }

    private static SharedConfig.ProxyInfo fromHysteria2(String url) throws Exception {
        return fromHysteriaFamily(url, "hysteria2");
    }

    private static SharedConfig.ProxyInfo fromHysteria(String url) throws Exception {
        return fromHysteriaFamily(url, "hysteria");
    }

    private static SharedConfig.ProxyInfo fromHysteriaFamily(String url, String protocol) throws Exception {
        Uri uri = Uri.parse(url);
        String password = uri.getUserInfo();
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        String remark = uri.getFragment() != null ? decodeFragment(uri.getFragment()) : "";
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", protocol);
        JSONObject settings = new JSONObject();
        JSONObject server = new JSONObject()
                .put("address", host)
                .put("port", port);
        if (!TextUtils.isEmpty(password)) {
            server.put("password", password);
        }
        settings.put("servers", new JSONArray().put(server));
        outbound.put("settings", settings);
        JSONObject stream = new JSONObject();
        stream.put("security", "tls");
        JSONObject tls = new JSONObject();
        String sni = uri.getQueryParameter("sni");
        if (!TextUtils.isEmpty(sni)) {
            tls.put("serverName", sni);
        }
        stream.put("tlsSettings", tls);
        outbound.put("streamSettings", stream);
        return generic(host, port, remark, outbound);
    }

    private static void applyTransport(JSONObject stream, String network, String host, String path, String headerType) throws Exception {
        if ("ws".equalsIgnoreCase(network)) {
            JSONObject ws = new JSONObject();
            if (!TextUtils.isEmpty(path)) {
                ws.put("path", path);
            }
            if (!TextUtils.isEmpty(host)) {
                ws.put("headers", new JSONObject().put("Host", host));
            }
            stream.put("wsSettings", ws);
        } else if ("grpc".equalsIgnoreCase(network)) {
            JSONObject grpc = new JSONObject();
            if (!TextUtils.isEmpty(path)) {
                grpc.put("serviceName", path);
            }
            stream.put("grpcSettings", grpc);
        } else if ("http".equalsIgnoreCase(network) || "h2".equalsIgnoreCase(network)) {
            JSONObject http = new JSONObject();
            if (!TextUtils.isEmpty(path)) {
                http.put("path", path);
            }
            if (!TextUtils.isEmpty(host)) {
                http.put("host", new JSONArray().put(host));
            }
            stream.put("httpSettings", http);
        } else if ("tcp".equalsIgnoreCase(network) && !TextUtils.isEmpty(headerType)) {
            JSONObject tcp = new JSONObject();
            tcp.put("header", new JSONObject().put("type", headerType));
            stream.put("tcpSettings", tcp);
        }
    }

    private static String decodeFragment(String fragment) {
        try {
            return URLDecoder.decode(fragment, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return fragment;
        }
    }
}
