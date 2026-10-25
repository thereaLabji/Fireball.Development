package dev.fireball.api;

/**
 * Entry point interface for Fireball mods.
 * Implement this (or use the @Mod annotation) to have your mod loaded.
 */
public interface ModInitializer {
    /**
     * Called when the mod is being initialized.
     * @param context Provides access to the event bus and mod metadata.
     */
    void onInitialize(ModContext context);
}
