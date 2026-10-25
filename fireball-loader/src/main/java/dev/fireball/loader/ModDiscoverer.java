package dev.fireball.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.fireball.ModContainer;
import dev.fireball.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers mods from a path.
 *
 * The path may be:
 * - A single .jar file (the mod under test)
 * - A directory containing one or more .jar files
 *
 * Files ending in .disabled (e.g. ExampleMod.jar.disabled) are skipped.
 */
public final class ModDiscoverer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModDiscoverer.class);
    private static final Gson GSON = new Gson();

    private final Path path;

    public ModDiscoverer(Path path) {
        this.path = path;
    }

    public List<ModContainer> discover() {
        List<ModContainer> result = new ArrayList<>();

        if (!Files.exists(path)) {
            LOGGER.warn("Path does not exist: {}", path);
            return result;
        }

        try {
            if (Files.isRegularFile(path)) {
                String name = path.getFileName().toString().toLowerCase();
                if (name.endsWith(".disabled")) {
                    LOGGER.info("Skipping disabled mod: {}", path.getFileName());
                    return result;
                }
                if (name.endsWith(".jar")) {
                    ModContainer container = discoverFromJar(path);
                    if (container != null) {
                        result.add(container);
                        LOGGER.info("Discovered mod: {} from {}", container.getMetadata(), path.getFileName());
                    }
                } else {
                    LOGGER.warn("Not a .jar file: {}", path);
                }
            } else if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry : stream) {
                        if (!Files.isRegularFile(entry)) {
                            continue;
                        }

                        String name = entry.getFileName().toString();
                        String lower = name.toLowerCase();

                        if (lower.endsWith(".disabled")) {
                            LOGGER.info("Skipping disabled mod: {}", name);
                            continue;
                        }

                        if (!lower.endsWith(".jar")) {
                            continue;
                        }

                        try {
                            ModContainer container = discoverFromJar(entry);
                            if (container != null) {
                                result.add(container);
                                LOGGER.info("Discovered mod: {} from {}", container.getMetadata(), name);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to discover mod from {}", name, e);
                        }
                    }
                }
            } else {
                LOGGER.warn("Path is neither a file nor a directory: {}", path);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan path: {}", path, e);
        }

        return result;
    }

    private ModContainer discoverFromJar(Path jarPath) throws IOException {
        if (Files.size(jarPath) == 0) {
            LOGGER.warn("Skipping empty placeholder jar: {}", jarPath.getFileName());
            return null;
        }

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry metaEntry = jar.getJarEntry("fireball.mod.json");
            if (metaEntry != null) {
                try (InputStream in = jar.getInputStream(metaEntry);
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) {
                        LOGGER.error("fireball.mod.json is empty or invalid in {}", jarPath.getFileName());
                        return null;
                    }
                    ModMetadata meta = parseMetadata(json, jarPath);
                    return new ModContainer(meta, jarPath);
                } catch (JsonParseException e) {
                    LOGGER.error("Failed to parse fireball.mod.json in {}", jarPath.getFileName(), e);
                    return null;
                }
            }

            JarEntry entrypointFile = jar.getJarEntry("META-INF/fireball.entrypoint");
            if (entrypointFile != null) {
                try (InputStream in = jar.getInputStream(entrypointFile)) {
                    String fqcn = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (!fqcn.isEmpty()) {
                        ModMetadata meta = ModMetadata.builder()
                                .id(guessIdFromJar(jarPath))
                                .name(guessIdFromJar(jarPath))
                                .entrypoint(fqcn)
                                .build();
                        return new ModContainer(meta, jarPath);
                    }
                }
            }

            LOGGER.warn("No fireball.mod.json or META-INF/fireball.entrypoint found in {}", jarPath.getFileName());
            return null;
        } catch (IOException e) {
            LOGGER.error("Not a valid JAR file: {}", jarPath.getFileName(), e);
            return null;
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid mod metadata in {}: {}", jarPath.getFileName(), e.getMessage());
            return null;
        }
    }

    private ModMetadata parseMetadata(JsonObject json, Path jarPath) {
        if (!json.has("id") || json.get("id").isJsonNull() || json.get("id").getAsString().isBlank()) {
            throw new IllegalArgumentException("fireball.mod.json missing required field 'id' in " + jarPath.getFileName());
        }

        String id = json.get("id").getAsString().trim();
        String name = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : id;
        String version = json.has("version") && !json.get("version").isJsonNull()
                ? json.get("version").getAsString() : "1.0.0";
        String description = json.has("description") && !json.get("description").isJsonNull()
                ? json.get("description").getAsString() : "";
        String entrypoint = json.has("entrypoint") && !json.get("entrypoint").isJsonNull()
                ? json.get("entrypoint").getAsString() : null;

        if (entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalArgumentException(
                    "fireball.mod.json missing required field 'entrypoint' in " + jarPath.getFileName());
        }

        List<String> authors = new ArrayList<>();
        if (json.has("authors") && json.get("authors").isJsonArray()) {
            json.getAsJsonArray("authors").forEach(e -> {
                if (e != null && !e.isJsonNull()) {
                    authors.add(e.getAsString());
                }
            });
        }

        List<String> depends = new ArrayList<>();
        if (json.has("depends") && json.get("depends").isJsonArray()) {
            json.getAsJsonArray("depends").forEach(e -> {
                if (e != null && !e.isJsonNull()) {
                    depends.add(e.getAsString());
                }
            });
        }
        // All content mods require the API mod (except the API mod itself)
        if (!"fireball_api".equals(id) && !depends.contains("fireball_api")) {
            depends.add(0, "fireball_api");
        }

        return ModMetadata.builder()
                .id(id)
                .name(name)
                .version(version)
                .description(description)
                .authors(authors)
                .entrypoint(entrypoint)
                .depends(depends)
                .build();
    }

    private String guessIdFromJar(Path jar) {
        String name = jar.getFileName().toString();
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceAll("-\\d+.*$", "").toLowerCase().replace(' ', '_');
    }
}
