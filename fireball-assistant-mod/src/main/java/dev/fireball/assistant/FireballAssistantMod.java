package dev.fireball.assistant;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.ModLifecycleEvent;

/** Fireball's own example mod for Round 3. */
@Mod(
    id = "fireball_assistant",
    name = "Fireball Assistant",
    version = "1.0.0",
    description = "Fireball's built-in challenge mod.",
    authors = {"Fireball Team"}
)
public final class FireballAssistantMod implements ModInitializer {
    @Override
    public void onInitialize(ModContext context) {
        System.out.println("[Fireball Assistant] Loaded through Fireball API.");
        System.out.println("[Fireball Assistant] API dependency: fireball_api ✓");
        context.getEventBus().register(ModLifecycleEvent.class, event -> {
            if (event.getPhase() == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                System.out.println("[Fireball Assistant] Fireball is ready. 🔥");
            }
        });
    }
}
