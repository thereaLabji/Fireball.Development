package dev.fireball.client;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Microsoft device-code -> Xbox Live -> XSTS -> Minecraft service exchange. */
final class AccountAuthenticator {
    private AccountAuthenticator() {}

    static MinecraftLauncher.Account login(HttpClient http, String clientId) throws Exception {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("--login requires --ms-client-id <your Microsoft Entra public-client application ID>");
        }
        JsonObject device = postForm(http, "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", Map.of("client_id", clientId, "scope", "XboxLive.signin offline_access"));
        System.out.println("Open " + device.get("verification_uri").getAsString() + " and enter code: " + device.get("user_code").getAsString());
        int interval = device.has("interval") ? device.get("interval").getAsInt() : 5;
        JsonObject token;
        while (true) { Thread.sleep(interval * 1000L); try { token = postForm(http, "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", clientId, "device_code", device.get("device_code").getAsString())); break; } catch (HttpStatusException e) { if (e.body.contains("authorization_pending")) continue; if (e.body.contains("slow_down")) { interval += 5; continue; } throw e; } }
        JsonObject xbl = postJson(http, "https://user.auth.xboxlive.com/user/authenticate", "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + token.get("access_token").getAsString() + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
        String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        JsonObject xsts = postJson(http, "https://xsts.auth.xboxlive.com/xsts/authorize", "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xbl.get("Token").getAsString() + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
        JsonObject mc = postJson(http, "https://api.minecraftservices.com/authentication/login_with_xbox", "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString() + "\"}");
        String access = mc.get("access_token").getAsString();
        JsonObject entitlements = get(http, "https://api.minecraftservices.com/entitlements/mcstore", access);
        if (!entitlements.has("items") || entitlements.getAsJsonArray("items").isEmpty()) {
            throw new IOException("The Microsoft account does not own Minecraft: Java Edition.");
        }
        JsonObject profile = get(http, "https://api.minecraftservices.com/minecraft/profile", access);
        return new MinecraftLauncher.Account(profile.get("name").getAsString(), profile.get("id").getAsString(), access, "msa");
    }
    private static JsonObject postForm(HttpClient h, String url, Map<String,String> form) throws Exception { String body = form.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&")); return send(h, HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build()); }
    private static JsonObject postJson(HttpClient h, String url, String body) throws Exception { return send(h, HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()); }
    private static JsonObject get(HttpClient h, String url, String token) throws Exception { return send(h, HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build()); }
    private static JsonObject send(HttpClient h, HttpRequest r) throws Exception { HttpResponse<String> response = h.send(r, HttpResponse.BodyHandlers.ofString()); if (response.statusCode()/100 != 2) throw new HttpStatusException(response.statusCode(), response.body()); return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private static final class HttpStatusException extends IOException { final String body; HttpStatusException(int status, String body) { super("Authentication failed: HTTP " + status + " " + body); this.body = body; } }
}
