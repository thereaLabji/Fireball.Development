package dev.example;

import dev.fireball.api.ModContext;
import dev.fireball.api.ModInitializer;
import dev.fireball.api.annotation.Mod;
import dev.fireball.api.event.ModLifecycleEvent;

@Mod(
    id = "example_java",
    name = "Example Java Mod",
    version = "1.0.0",
    description = "A simple example mod written in Java for Fireball",
    authors = {"Fireball Team"}
)
public class ExampleJavaMod implements ModInitializer {

    @Override
    public void onInitialize(ModContext context) {
        System.out.println("[ExampleJavaMod] Hello from Java! Mod ID: " + context.getModId());

        context.getEventBus().register(ModLifecycleEvent.class, event -> {
            if (event.getPhase() == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                System.out.println("[ExampleJavaMod] All mods have finished loading!");
            }
        });
    }
}
