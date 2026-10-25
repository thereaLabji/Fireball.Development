package dev.ember;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.ModLifecycleEvent;

/**
 * Ember — the first official content mod for Fireball.
 * Requires the Fireball API mod (fireball_api).
 */
@Mod(
        id = "ember",
        name = "Ember",
        version = "1.0.0",
        description = "The first Fireball content mod. Lights the way.",
        authors = {"Fireball Team","thereaLabji"}
)
public final class EmberMod implements ModInitializer {

    public static final String MOD_ID = "ember";

    @Override
    public void onInitialize(ModContext context) {
        System.out.println("[Ember] Igniting... (depends on Fireball API)");
        System.out.println("[Ember] Hello from Ember v1.0.0 - the first Fireball mod!");

        context.getEventBus().register(ModLifecycleEvent.class, event -> {
            if (event.getPhase() == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                System.out.println("[Ember] All systems go. Ember is ready.");
            }
        });
    }
}
