package dev.zebaji;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.ModLifecycleEvent;

@Mod(
    id = "tringus",
    name = "Tringus",
    version = "0.0.1",
    description = "A simple mod for Fireball, used to utilize minecraft.",
    authors = {"Fireball Team", "zebaji", "thereaLabji"}
)
public class Tringus implements ModInitializer {

    @Override
    public void onInitialize(ModContext context) {
        System.out.println("[Tringus] Hello, Java!");

        context.getEventBus().register(ModLifecycleEvent.class, event -> {
            if (event.getPhase() == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                System.out.println("[Tringus]: Load complete!");
            }
        });
    }
}
