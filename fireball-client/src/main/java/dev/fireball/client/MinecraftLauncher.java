package dev.fireball.client;

import com.google.gson.*;
import dev.fireball.mojang.MojangIntegration;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

/** Resolves Mojang's public metadata and starts the official client in its own JVM. */
final class MinecraftLauncher {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private final Options options;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private JsonObject metadata;
    private Path clientJar, nativesDir;

    MinecraftLauncher(Options options) { this.options = options; }

    void prepare() throws Exception {
        System.out.println("Resolving Minecraft " + options.version + " metadata...");
        metadata = versionMetadata();
        JsonObject client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
        clientJar = options.gameDir.resolve("versions").resolve(options.version).resolve(options.version + ".jar");
        System.out.println("Downloading/verifying client JAR...");
        download(client.get("url").getAsString(), clientJar, client.get("sha1").getAsString());
        System.out.println("Downloading/verifying libraries and natives...");
        downloadLibraries();
        System.out.println("Downloading/verifying game assets...");
        downloadAssets();
        System.out.println("Minecraft " + options.version + " prepared in " + options.gameDir);
    }

    void launch(boolean noMod) throws Exception {
        Account account = account();
        List<Path> libraries = downloadLibraries();
        nativesDir = options.gameDir.resolve("natives").resolve(options.version).resolve(platform());
        List<String> command = new ArrayList<>();
        command.add(options.javaPath == null ? "java" : options.javaPath);
        Path agent = agentJar();
        command.add("-javaagent:" + agent);
        command.add("-Dfireball.gameDir=" + options.gameDir);
        command.add("-Dfireball.mcVersion=" + options.version);
        command.add("-Dfireball.noMod=" + noMod);
        // The launcher is a short-lived parent process. Carry explicit memory
        // settings from `java -Xms... -Xmx... -jar fireball-client...` to the
        // actual Minecraft JVM so users do not need launcher-specific flags.
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-Xms") || argument.startsWith("-Xmx")) command.add(argument);
        }
        Map<String, String> values = substitutions(account, libraries, agent);
        if (options.fabric) {
            if (!"1.20.1".equals(options.version)) {
                throw new IllegalArgumentException("The Fireball Fabric bootstrap currently targets Minecraft 1.20.1 only.");
            }
            Path fabricMods = options.gameDir.resolve("fabric-mods");
            Files.createDirectories(fabricMods);
            command.add("-Dfabric.addMods=" + fabricMods.toAbsolutePath());
        }
        for (String arg : metadataArguments("jvm")) command.add(substitute(arg, values));
        command.add("-Djava.library.path=" + nativesDir);
        command.addAll(options.extraJvmArgs);
        command.add(options.fabric ? "net.fabricmc.loader.impl.launch.knot.KnotClient" : metadata.get("mainClass").getAsString());
        for (String arg : metadataArguments("game")) command.add(substitute(arg, values));
        System.out.println("Launching " + (options.fabric ? "Fabric Loader 0.16.10" : metadata.get("mainClass").getAsString()) + " as " + account.username);
        int exit = new ProcessBuilder(command).directory(options.gameDir.toFile()).inheritIO().start().waitFor();
        if (exit != 0) throw new IOException("Minecraft exited with status " + exit);
    }

    private Path agentJar() throws Exception {
        Path path = Path.of(FireballClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
            throw new IllegalStateException("Fireball's runtime bridge requires the packaged fireball-client JAR. Build with :fireball-client:shadowJar.");
        }
        return path.toAbsolutePath();
    }

    private JsonObject versionMetadata() throws Exception {
        Path cached = options.gameDir.resolve("versions").resolve(options.version).resolve(options.version + ".json");
        JsonObject manifest = json(MANIFEST);
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject entry = e.getAsJsonObject();
            if (options.version.equals(entry.get("id").getAsString())) {
                JsonObject result = json(entry.get("url").getAsString());
                Files.createDirectories(cached.getParent());
                Files.writeString(cached, result.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return result;
            }
        }
        if (Files.exists(cached)) return JsonParser.parseString(Files.readString(cached)).getAsJsonObject();
        throw new IOException("Minecraft version not present in Mojang manifest: " + options.version);
    }

    private List<Path> downloadLibraries() throws Exception {
        List<Path> classpath = new ArrayList<>();
        nativesDir = options.gameDir.resolve("natives").resolve(options.version).resolve(platform());
        Files.createDirectories(nativesDir);
        for (JsonElement e : metadata.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!allowed(lib)) continue;
            JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
            if (downloads == null) continue;
            if (downloads.has("artifact")) classpath.add(downloadArtifact(downloads.getAsJsonObject("artifact")));
            if (lib.has("natives")) {
                String key = lib.getAsJsonObject("natives").has(platform()) ? platform() : null;
                if (key != null && downloads.has("classifiers")) {
                    String classifier = lib.getAsJsonObject("natives").get(key).getAsString().replace("${arch}", architecture());
                    JsonObject artifact = downloads.getAsJsonObject("classifiers").has(classifier)
                            ? downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier) : null;
                    if (artifact != null) extractNative(downloadArtifact(artifact));
                }
            }
        }
        return classpath;
    }

    private Path downloadArtifact(JsonObject artifact) throws Exception {
        Path path = options.gameDir.resolve("libraries").resolve(artifact.get("path").getAsString().replace('/', File.separatorChar));
        download(artifact.get("url").getAsString(), path, artifact.has("sha1") ? artifact.get("sha1").getAsString() : null);
        return path;
    }

    private void downloadAssets() throws Exception {
        JsonObject index = metadata.getAsJsonObject("assetIndex");
        Path indexFile = options.gameDir.resolve("assets/indexes").resolve(index.get("id").getAsString() + ".json");
        download(index.get("url").getAsString(), indexFile, index.get("sha1").getAsString());
        JsonObject objects = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject().getAsJsonObject("objects");
        List<String> hashes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
            hashes.add(e.getValue().getAsJsonObject().get("hash").getAsString());
        }
        int total = hashes.size();
        AtomicInteger completed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> downloads = new ArrayList<>(total);
            for (String hash : hashes) downloads.add(pool.submit(() -> {
                try {
                    download("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                            options.gameDir.resolve("assets/objects").resolve(hash.substring(0, 2)).resolve(hash), hash);
                    int done = completed.incrementAndGet();
                    if (done == total || done % 100 == 0) System.out.println("Assets: " + done + "/" + total);
                } catch (Exception ex) { throw new CompletionException(ex); }
            }));
            for (Future<?> future : downloads) future.get();
        } finally {
            pool.shutdownNow();
        }
    }

    private Account account() throws Exception {
        if (options.accessToken != null) {
            if (options.uuid == null || options.username == null) throw new IllegalArgumentException("--accessToken requires --uuid and --username");
            return new Account(options.username, options.uuid.replace("-", ""), options.accessToken, "msa");
        }
        if (options.login) return AccountAuthenticator.login(http, options.microsoftClientId);
        if (options.offlineName != null) return Account.offline(options.offlineName);
        throw new IllegalArgumentException("Authentication is required. Use --login, or --offline <name> for single-player.");
    }

    private Map<String, String> substitutions(Account a, List<Path> libs, Path bridgeJar) {
        List<String> entries = new ArrayList<>(libs.stream().map(Path::toString).toList());
        entries.add(clientJar.toString());
        // Knot is packaged with Fireball's agent JAR, but the Java main-class
        // lookup also needs it on the application classpath.
        if (options.fabric) entries.add(bridgeJar.toString());
        String cp = String.join(File.pathSeparator, entries);
        Map<String, String> v = new HashMap<>();
        v.put("auth_player_name", a.username); v.put("auth_uuid", a.uuid); v.put("auth_access_token", a.token); v.put("user_type", a.type);
        v.put("version_name", options.version); v.put("version_type", metadata.get("type").getAsString()); v.put("game_directory", options.gameDir.toString());
        v.put("assets_root", options.gameDir.resolve("assets").toString()); v.put("assets_index_name", metadata.getAsJsonObject("assetIndex").get("id").getAsString());
        v.put("natives_directory", nativesDir.toString()); v.put("classpath", cp); v.put("classpath_separator", File.pathSeparator);
        v.put("library_directory", options.gameDir.resolve("libraries").toString()); v.put("user_properties", "{}"); v.put("auth_xuid", ""); v.put("clientid", "");
        return v;
    }

    private List<String> metadataArguments(String kind) {
        List<String> result = new ArrayList<>();
        if (metadata.has("arguments") && metadata.getAsJsonObject("arguments").has(kind)) {
            for (JsonElement e : metadata.getAsJsonObject("arguments").getAsJsonArray(kind)) {
                if (e.isJsonPrimitive()) result.add(e.getAsString());
                else {
                    JsonObject o = e.getAsJsonObject();
                    if (allowed(o)) {
                        JsonElement value = o.get("value");
                        if (value.isJsonArray()) for (JsonElement item : value.getAsJsonArray()) result.add(item.getAsString());
                        else result.add(value.getAsString());
                    }
                }
            }
        } else if ("game".equals(kind) && metadata.has("minecraftArguments")) result.addAll(List.of(metadata.get("minecraftArguments").getAsString().split(" ")));
        return result;
    }

    private boolean allowed(JsonObject object) {
        if (!object.has("rules")) return true; boolean permitted = false;
        for (JsonElement e : object.getAsJsonArray("rules")) { JsonObject r = e.getAsJsonObject(); if (matches(r)) permitted = "allow".equals(r.get("action").getAsString()); }
        return permitted;
    }
    private boolean matches(JsonObject rule) {
        if (rule.has("os")) {
            JsonObject os = rule.getAsJsonObject("os");
            if (os.has("name") && !platform().equals(os.get("name").getAsString())) return false;
            if (os.has("arch") && !System.getProperty("os.arch").equalsIgnoreCase(os.get("arch").getAsString())) return false;
        }
        // Mojang uses feature rules for optional arguments such as Quick Play.
        // Fireball currently requests none of those optional features.
        if (rule.has("features")) {
            for (Map.Entry<String, JsonElement> feature : rule.getAsJsonObject("features").entrySet()) {
                boolean requested = Boolean.getBoolean("fireball.feature." + feature.getKey());
                if (requested != feature.getValue().getAsBoolean()) return false;
            }
        }
        return true;
    }
    private String platform() { String n = System.getProperty("os.name").toLowerCase(Locale.ROOT); return n.contains("win") ? "windows" : n.contains("mac") ? "osx" : "linux"; }
    private String architecture() { return System.getProperty("os.arch").contains("64") ? "64" : "32"; }
    private String substitute(String s, Map<String, String> values) { for (var e : values.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue()); return s; }
    private JsonObject json(String url) throws Exception { HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofString()); if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + url); return JsonParser.parseString(r.body()).getAsJsonObject(); }
    private void download(String url, Path target, String expectedSha1) throws Exception { if (Files.exists(target) && (expectedSha1 == null || expectedSha1.equalsIgnoreCase(sha1(target)))) return; Files.createDirectories(target.getParent()); Path part = target.resolveSibling(target.getFileName() + ".part"); HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream()); if (r.statusCode() / 100 != 2) throw new IOException("Download failed: HTTP " + r.statusCode()); try (InputStream in = r.body()) { Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING); } if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(sha1(part))) { Files.deleteIfExists(part); throw new IOException("SHA-1 mismatch: " + target.getFileName()); } Files.move(part, target, StandardCopyOption.REPLACE_EXISTING); }
    private String sha1(Path p) throws Exception { MessageDigest d = MessageDigest.getInstance("SHA-1"); try (InputStream in = Files.newInputStream(p)) { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) >= 0;) d.update(b, 0, n); } return HexFormat.of().formatHex(d.digest()); }
    private void extractNative(Path archive) throws IOException { try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) { for (java.util.zip.ZipEntry e; (e = zip.getNextEntry()) != null;) { Path output = nativesDir.resolve(e.getName()).normalize(); if (e.isDirectory() || e.getName().startsWith("META-INF/") || !output.startsWith(nativesDir)) continue; Files.createDirectories(output.getParent()); Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING); } } }
    record Options(Path gameDir, String version, boolean prepareOnly, boolean login, boolean fabric, String offlineName, String accessToken, String uuid, String username, String javaPath, String microsoftClientId, List<String> extraJvmArgs) {}
    record Account(String username, String uuid, String token, String type) { static Account offline(String name) { return new Account(name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""), "0", "legacy"); } }
}
