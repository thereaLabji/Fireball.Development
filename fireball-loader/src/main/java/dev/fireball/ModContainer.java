package dev.fireball;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.event.EventBus;
import dev.fireball.loader.ModClassLoader;

import java.nio.file.Path;

/**
 * Represents a discovered and (optionally) loaded mod.
 */
public final class ModContainer {
    private final ModMetadata metadata;
    private final Path sourceJar;
    private ModClassLoader classLoader;
    private ModInitializer initializer;
    private boolean loaded;
    private Throwable loadError;

    public ModContainer(ModMetadata metadata, Path sourceJar) {
        this.metadata = metadata;
        this.sourceJar = sourceJar;
    }

    public ModMetadata getMetadata() {
        return metadata;
    }

    public Path getSourceJar() {
        return sourceJar;
    }

    public ModClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ModClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ModInitializer getInitializer() {
        return initializer;
    }

    public void setInitializer(ModInitializer initializer) {
        this.initializer = initializer;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public Throwable getLoadError() {
        return loadError;
    }

    public void setLoadError(Throwable loadError) {
        this.loadError = loadError;
    }

    /**
     * Creates a ModContext bound to this container.
     */
    public ModContext createContext(EventBus eventBus) {
        return new ModContext() {
            @Override public String getModId() { return metadata.getId(); }
            @Override public String getModName() { return metadata.getName(); }
            @Override public String getModVersion() { return metadata.getVersion(); }
            @Override public EventBus getEventBus() { return eventBus; }
        };
    }

    @Override
    public String toString() {
        return metadata.toString();
    }
}
