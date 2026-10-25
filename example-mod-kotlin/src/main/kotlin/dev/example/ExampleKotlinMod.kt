package dev.example

import dev.fireball.api.ModContext
import dev.fireball.api.ModInitializer
import dev.fireball.api.annotation.Mod
import dev.fireball.api.event.ModLifecycleEvent

@Mod(
    id = "example_kotlin",
    name = "Example Kotlin Mod",
    version = "1.0.0",
    description = "A simple example mod written in Kotlin for Fireball",
    authors = ["Fireball Team"]
)
class ExampleKotlinMod : ModInitializer {

    override fun onInitialize(context: ModContext) {
        println("[ExampleKotlinMod] Hello from Kotlin! Mod ID: ${context.modId}")

        context.eventBus.register(ModLifecycleEvent::class.java) { event ->
            if (event.phase == ModLifecycleEvent.Phase.LOAD_COMPLETE) {
                println("[ExampleKotlinMod] All mods have finished loading!")
            }
        }
    }
}
