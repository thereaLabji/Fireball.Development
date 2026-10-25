package dev.fireball.loader;

import dev.fireball.ModContainer;
import dev.fireball.ModMetadata;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.EventBus;
import dev.fireball.api.event.ModLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Responsible for loading discovered mods: dependency checks, classloaders,
 * instantiating entrypoints, and calling onInitialize.
 */
public final class ModLoader {
    public static final String API_MOD_ID = "fireball_api";

    private static final Logger LOGGER = LoggerFactory.getLogger(ModLoader.class);

    private final EventBus eventBus;
    private final ClassLoader parentClassLoader;

    public ModLoader(EventBus eventBus) {
        this.eventBus = eventBus;
        this.parentClassLoader = ModLoader.class.getClassLoader();
    }

    public void loadAll(List<ModContainer> containers) {
        Map<String, ModContainer> byId = new HashMap<>();
        for (ModContainer c : containers) {
            String id = c.getMetadata().getId();
            if (byId.containsKey(id)) {
                LOGGER.error("Duplicate mod id '{}'. Only the first will be loaded; skipping {}",
                        id, c.getSourceJar().getFileName());
                c.setLoadError(new IllegalStateException("Duplicate mod id: " + id));
            } else {
                byId.put(id, c);
            }
        }

        // Resolve load order (dependencies first) and validate depends
        List<ModContainer> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (ModContainer c : containers) {
            if (c.getLoadError() != null) continue;
            try {
                visit(c, byId, visiting, visited, ordered);
            } catch (Exception e) {
                c.setLoadError(e);
                LOGGER.error("Dependency resolution failed for {}: {}", c.getMetadata().getId(), e.getMessage());
            }
        }

        for (ModContainer container : ordered) {
            if (container.getLoadError() != null) continue;
            try {
                load(container);
            } catch (Exception e) {
                container.setLoadError(e);
                container.setLoaded(false);
                LOGGER.error("Failed to load mod {}", container.getMetadata().getId(), e);
            }
        }
    }

    private void visit(ModContainer container,
                       Map<String, ModContainer> byId,
                       Set<String> visiting,
                       Set<String> visited,
                       List<ModContainer> ordered) {
        String id = container.getMetadata().getId();
        if (visited.contains(id) || container.getLoadError() != null) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalStateException("Circular dependency involving mod '" + id + "'");
        }

        for (String dep : container.getMetadata().getDepends()) {
            ModContainer depContainer = byId.get(dep);
            if (depContainer == null) {
                // fireball_api is mandatory for non-API mods
                if (API_MOD_ID.equals(dep)) {
                    throw new IllegalStateException(
                            "Mod '" + id + "' requires the Fireball API mod (fireball_api), but it was not found in mobs/");
                }
                throw new IllegalStateException(
                        "Mod '" + id + "' depends on '" + dep + "', which was not found");
            }
            if (depContainer.getLoadError() != null) {
                throw new IllegalStateException(
                        "Mod '" + id + "' depends on '" + dep + "', which failed to load");
            }
            visit(depContainer, byId, visiting, visited, ordered);
        }

        visiting.remove(id);
        visited.add(id);
        ordered.add(container);
    }

    private void load(ModContainer container) throws Exception {
        ModMetadata meta = container.getMetadata();
        LOGGER.info("Loading mod: {}", meta);

        ModClassLoader cl = new ModClassLoader(container.getSourceJar(), meta.getId(), parentClassLoader);
        container.setClassLoader(cl);

        String entrypoint = meta.getEntrypoint();
        if (entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalStateException("No entrypoint defined for mod " + meta.getId());
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(entrypoint, true, cl);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Entrypoint class '" + entrypoint + "' not found in " + container.getSourceJar().getFileName(), e);
        }

        Mod annotation = clazz.getAnnotation(Mod.class);
        if (annotation != null && !annotation.id().equals(meta.getId())) {
            LOGGER.warn("Mod id mismatch: JSON says '{}', @Mod says '{}'. Using JSON.",
                    meta.getId(), annotation.id());
        }

        if (!ModInitializer.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Entrypoint class " + entrypoint +
                    " does not implement ModInitializer");
        }

        Constructor<?> ctor;
        try {
            ctor = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Entrypoint class " + entrypoint + " must have a public no-arg constructor", e);
        }
        ctor.setAccessible(true);
        ModInitializer initializer = (ModInitializer) ctor.newInstance();
        container.setInitializer(initializer);

        eventBus.post(new ModLifecycleEvent(ModLifecycleEvent.Phase.PRE_INIT, meta.getId()));
        initializer.onInitialize(container.createContext(eventBus));
        eventBus.post(new ModLifecycleEvent(ModLifecycleEvent.Phase.INIT, meta.getId()));

        container.setLoaded(true);
        LOGGER.info("Successfully loaded mod: {}", meta);
    }
}
