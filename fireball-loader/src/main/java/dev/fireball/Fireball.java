package dev.fireball;

import dev.fireball.api.event.EventBus;
import dev.fireball.api.event.ModLifecycleEvent;
import dev.fireball.event.SimpleEventBus;
import dev.fireball.loader.ModDiscoverer;
import dev.fireball.loader.ModLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Fireball Mod Loader - Main entry point.
 *
 * Expected server layout (example):
 *   server/
 *   ├── mobs/              ← mod JARs go here (.jar.disabled to disable)
 *   ├── config/            ← mod & loader configs
 *   ├── resources/         ← resource packs
 *   ├── res/               ← Minecraft data
 *   ├── logs/              ← logs (+ logs/crashes/)
 *   └── tmp/
 *
 * Usage:
 *   java -jar fireball-<version>.jar -V <mcversion>                  # load mods from ./mobs
 *   java -jar fireball-<version>.jar -V <mcversion> --no-mod         # run with no mods
 *   java -jar fireball-<version>.jar -V <mcversion> -t <mod.jar>     # test a specific mod
 */
public final class Fireball {
    public static final String LOADER_VERSION = "1.2.0";
    public static final String NAME = "Fireball";

    /** Default folder name for mod JARs (matches the example server layout). */
    public static final String MOBS_DIR_NAME = "mobs";

    public static final Set<String> SUPPORTED_MC_VERSIONS = Set.of(
            // 1.19 family
            "1.19", "1.19.1",
            // 1.20 family
            "1.20", "1.20.1", "1.20.2", "1.20.4", "1.20.6",
            // 1.21 family
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
            "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
            // 26.x family
            "26.1", "26.2", "26.x"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(Fireball.class);

    private final Path gameDir;
    private final Path modPath;      // used when -t is given
    private final boolean noMod;     // true when --no-mod
    private final String mcVersion;
    private final EventBus eventBus;
    private final ModLoader modLoader;

    public Fireball(Path gameDir, Path modPath, boolean noMod, String mcVersion) {
        this.gameDir = gameDir;
        this.modPath = modPath;
        this.noMod = noMod;
        this.mcVersion = mcVersion;
        this.eventBus = new SimpleEventBus();
        this.modLoader = new ModLoader(eventBus);
    }

    public void start() {
        LOGGER.info("========================================");
        LOGGER.info("  {} Mod Loader v{}", NAME, LOADER_VERSION);
        LOGGER.info("========================================");
        LOGGER.info("Target Minecraft : {}", mcVersion);
        LOGGER.info("Game directory   : {}", gameDir.toAbsolutePath());

        List<ModContainer> containers;

        if (noMod) {
            LOGGER.info("Mod mode         : --no-mod (no mods will be loaded)");
            containers = Collections.emptyList();
        } else if (modPath != null) {
            LOGGER.info("Mod mode         : testing single mod");
            LOGGER.info("Mod under test   : {}", modPath.toAbsolutePath());
            ModDiscoverer discoverer = new ModDiscoverer(modPath);
            containers = discoverer.discover();
            if (containers.isEmpty()) {
                LOGGER.error("No valid mod found at {}", modPath);
                System.exit(1);
            }
        } else {
            Path mobsDir = gameDir.resolve(MOBS_DIR_NAME);
            LOGGER.info("Mod mode         : loading from {} directory", MOBS_DIR_NAME);
            LOGGER.info("Mobs directory   : {}", mobsDir.toAbsolutePath());
            try {
                Files.createDirectories(mobsDir);
            } catch (Exception e) {
                LOGGER.error("Failed to create mobs directory", e);
                System.exit(1);
            }
            ModDiscoverer discoverer = new ModDiscoverer(mobsDir);
            containers = discoverer.discover();
        }

        if (containers.isEmpty()) {
            LOGGER.info("No mods to load.");
        } else {
            LOGGER.info("Discovered {} mod(s)", containers.size());
        }

        modLoader.loadAll(containers);

        // PRE_INIT and INIT are fired per-mod inside ModLoader.
        // Post LOAD_COMPLETE once everything is done.
        eventBus.post(new ModLifecycleEvent(ModLifecycleEvent.Phase.LOAD_COMPLETE, "fireball"));

        long loaded = containers.stream().filter(ModContainer::isLoaded).count();
        LOGGER.info("Fireball initialization complete. {} mod(s) loaded.", loaded);
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public static void main(String[] args) {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        Path modPath = null;
        boolean noMod = false;
        String mcVersion = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-V", "--mcversion" -> {
                    if (i + 1 < args.length) {
                        mcVersion = args[++i];
                    } else {
                        System.err.println("Error: -V requires a Minecraft version argument");
                        System.exit(1);
                    }
                }
                case "-t", "--test" -> {
                    if (i + 1 < args.length) {
                        modPath = Paths.get(args[++i]).toAbsolutePath().normalize();
                    } else {
                        System.err.println("Error: -t requires a path to the mod to be tested");
                        System.exit(1);
                    }
                }
                case "--no-mod" -> noMod = true;
                case "--gameDir" -> {
                    if (i + 1 < args.length) {
                        gameDir = Paths.get(args[++i]).toAbsolutePath().normalize();
                    }
                }
                case "-v", "--version" -> {
                    System.out.println(NAME + " " + LOADER_VERSION);
                    return;
                }
                case "-h", "--help" -> {
                    printHelp();
                    return;
                }
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printHelp();
                    System.exit(1);
                }
            }
        }

        if (mcVersion == null) {
            System.err.println("Error: Minecraft version is required. Use -V <mcversion>");
            System.err.println("Example: java -jar fireball-" + LOADER_VERSION + ".jar -V 1.21.1");
            System.exit(1);
        }

        if (!SUPPORTED_MC_VERSIONS.contains(mcVersion)) {
            System.err.println("Error: Unsupported Minecraft version: " + mcVersion);
            System.err.println("Supported versions:");
            SUPPORTED_MC_VERSIONS.stream().sorted().forEach(v -> System.err.println("  " + v));
            System.exit(1);
        }

        if (noMod && modPath != null) {
            System.err.println("Error: --no-mod and -t cannot be used together");
            System.exit(1);
        }

        if (modPath != null && !Files.exists(modPath)) {
            System.err.println("Error: Mod path does not exist: " + modPath);
            System.exit(1);
        }

        new Fireball(gameDir, modPath, noMod, mcVersion).start();
    }

    private static void printHelp() {
        System.out.println("""
            Fireball Mod Loader
            
            Usage: java -jar fireball-<version>.jar -V <mcversion> [options]
            
            Required:
              -V, --mcversion <ver>   Target Minecraft version (e.g. 1.21.1, 1.20.6, 26.x)
            
            Mod selection (pick one):
              (none)                  Load all mods from <gameDir>/mobs
              --no-mod                Run with no mods
              -t, --test <path>       Test a specific mod JAR (or directory)
            
            Options:
              --gameDir <path>        Game / server directory (default: current directory)
              -v, --version           Print Fireball loader version
              -h, --help              Show this help
            
            Server layout:
              mobs/         Mod JARs (.jar.disabled to disable a mod)
              config/       Config files
              resources/    Resource packs
              res/          Minecraft data
              logs/         Logs (logs/crashes/ for crash reports)
              tmp/          Temporary files
            
            Examples:
              java -jar fireball-1.0.0.jar -V 1.21.1
              java -jar fireball-1.0.0.jar -V 1.21.1 --no-mod
              java -jar fireball-1.0.0.jar -V 1.21.1 -t ./test-mod.jar
              java -jar fireball-1.0.0.jar -v
            """);
    }
}
