package dev.fireball.apimod;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.ModLifecycleEvent;

/**
 * Official Fireball API mod.
 *
 * Provides the shared Fireball API to other mods at runtime and
 * acts as a stable entrypoint for API-level lifecycle hooks.
 */
@Mod(
        id = "fireball_api",
        name = "Fireball API",
        version = "1.0.0",
        description = "Official Fireball API for mods (events, initializers, annotations)",
        authors = {"Fireball Team"}
)
public final class FireballApiMod implements ModInitializer {

    public static final String MOD_ID = "fireball_api";
    public static final String VERSION = "1.0.0";

    private static ModContext context;

    @Override
    public void onInitialize(ModContext ctx) {
        context = ctx;
        System.out.println("[Fireball API] v" + VERSION + " initialized (mod id: " + ctx.getModId() + ")");

        ctx.getEventBus().register(ModLifecycleEvent.class, event -> {
            if (event.getPhase() == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                System.out.println("[Fireball API] Load complete - API is ready for other mods.");
            }
        });
    }

    /**
     * @return the API mod context, or null if not yet initialized
     */
    public static ModContext getContext() {
        return context;
    }

    /**
     * @return true if the API mod has finished onInitialize
     */
    public static boolean isReady() {
        return context != null;
    }
}
