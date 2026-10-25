package dev.fireball.loader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Dedicated class loader for a single mod JAR.
 * Provides basic isolation while still allowing access to the Fireball API
 * (which is on the parent classloader).
 */
public final class ModClassLoader extends URLClassLoader {
    private final Path jarPath;
    private final String modId;

    public ModClassLoader(Path jarPath, String modId, ClassLoader parent) throws IOException {
        super(new URL[]{jarPath.toUri().toURL()}, parent);
        this.jarPath = jarPath;
        this.modId = modId;
    }

    public Path getJarPath() {
        return jarPath;
    }

    public String getModId() {
        return modId;
    }

    @Override
    public String toString() {
        return "ModClassLoader{modId='" + modId + "', jar=" + jarPath.getFileName() + "}";
    }
}
