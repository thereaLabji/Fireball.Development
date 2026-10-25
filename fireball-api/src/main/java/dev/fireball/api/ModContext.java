package dev.fireball.api;

import dev.fireball.api.event.EventBus;

/**
 * Context provided to mods during initialization.
 */
public interface ModContext {
    /**
     * @return Unique mod ID
     */
    String getModId();

    /**
     * @return Mod display name
     */
    String getModName();

    /**
     * @return Mod version
     */
    String getModVersion();

    /**
     * @return Shared event bus for inter-mod communication
     */
    EventBus getEventBus();
}
