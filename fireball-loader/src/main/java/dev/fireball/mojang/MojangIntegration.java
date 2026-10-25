package dev.fireball.mojang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Official Mojang metadata/server-jar integration. Does not bundle Mojang assets. */
public final class MojangIntegration {
    public static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public MojangRelease resolve(String version) throws IOException, InterruptedException {
        JsonObject manifest = getJson(VERSION_MANIFEST);
        for (var element : manifest.getAsJsonArray("versions")) {
            JsonObject v = element.getAsJsonObject();
            if (version.equals(v.get("id").getAsString())) {
                String metadataUrl = v.get("url").getAsString();
                JsonObject metadata = getJson(metadataUrl);
                JsonObject downloads = metadata.getAsJsonObject("downloads");
                JsonObject server = downloads.getAsJsonObject("server");
                JsonObject client = downloads.getAsJsonObject("client");
                if (server == null) throw new IOException("Mojang did not publish a server download for " + version);
                return new MojangRelease(version,
                        server.get("url").getAsString(), server.get("sha1").getAsString(), server.get("size").getAsLong(),
                        client == null ? null : client.get("url").getAsString(),
                        client == null ? null : client.get("sha1").getAsString(),
                        client == null ? 0L : client.get("size").getAsLong());
            }
        }
        throw new IOException("Minecraft version not found in Mojang manifest: " + version);
    }

    public Path downloadServer(String version, Path serverDir) throws IOException, InterruptedException {
        MojangRelease release = resolve(version);
        Path vanillaDir = serverDir.resolve("mojang").resolve(version);
        Files.createDirectories(vanillaDir);
        Path jar = vanillaDir.resolve("server.jar");

        if (Files.exists(jar) && sha1(jar).equalsIgnoreCase(release.sha1())) return jar;

        HttpRequest request = HttpRequest.newBuilder(URI.create(release.serverUrl()))
                .timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) throw new IOException("Mojang server download failed: HTTP " + response.statusCode());
        Path tmp = vanillaDir.resolve("server.jar.part");
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha1(tmp).equalsIgnoreCase(release.sha1())) {
            Files.deleteIfExists(tmp);
            throw new IOException("SHA-1 mismatch for Mojang server.jar");
        }
        Files.move(tmp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    public Path downloadClient(MojangRelease release, Path target) throws IOException, InterruptedException {
        if (release.clientUrl() == null) throw new IOException("Mojang did not publish a client download for " + release.version());
        Files.createDirectories(target.getParent());
        if (Files.exists(target) && sha1(target).equalsIgnoreCase(release.clientSha1())) return target;
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.clientUrl()))
                .timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) throw new IOException("Mojang client download failed: HTTP " + response.statusCode());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha1(tmp).equalsIgnoreCase(release.clientSha1())) {
            Files.deleteIfExists(tmp);
            throw new IOException("SHA-1 mismatch for Mojang client.jar");
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public void ensureEulaFile(Path serverDir) throws IOException {
        Path eula = serverDir.resolve("eula.txt");
        if (!Files.exists(eula)) {
            Files.writeString(eula, "# By changing the setting below to TRUE you are indicating your agreement to the EULA.\n" +
                    "# See https://aka.ms/MinecraftEULA\n" + "eula=false\n");
        }
    }

    public static boolean acceptedEula(Path serverDir) throws IOException {
        Path eula = serverDir.resolve("eula.txt");
        if (!Files.exists(eula)) return false;
        for (String line : Files.readAllLines(eula)) {
            if (line.trim().equalsIgnoreCase("eula=true")) return true;
        }
        return false;
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Mojang metadata request failed: HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public record MojangRelease(String version, String serverUrl, String sha1, long size, String clientUrl, String clientSha1, long clientSize) {}
}
