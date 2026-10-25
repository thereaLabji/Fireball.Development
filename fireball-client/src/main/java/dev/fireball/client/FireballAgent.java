package dev.fireball.client;

import dev.fireball.Fireball;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;

/**
 * Injected by the launcher into the Minecraft JVM. This deliberately reuses
 * Fireball's normal loader, event bus, discovery and ModLoader unchanged.
 */
public final class FireballAgent {
    private FireballAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            Path gameDir = Path.of(required("fireball.gameDir")).toAbsolutePath().normalize();
            String version = required("fireball.mcVersion");
            boolean noMod = Boolean.parseBoolean(System.getProperty("fireball.noMod", "false"));
            System.out.println("[Fireball] Runtime bridge attached to Minecraft " + version);
            instrumentation.addTransformer(new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String className, Class<?> type,
                                        ProtectionDomain domain, byte[] bytes) {
                    if ("net/minecraft/client/main/Main".equals(className)) {
                        System.out.println("[Fireball] Minecraft client entrypoint detected; runtime hooks are available.");
                    }
                    return null; // First bridge revision observes loading; it never mutates Minecraft classes.
                }
            });
            new Fireball(gameDir, null, noMod, version).start();
        } catch (Throwable error) {
            System.err.println("[Fireball] Runtime bridge failed: " + error.getMessage());
            error.printStackTrace(System.err);
            throw new IllegalStateException("Fireball runtime bridge could not initialize", error);
        }
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing JVM property: " + name);
        return value;
    }
}
