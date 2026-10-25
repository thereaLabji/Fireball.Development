package dev.fireball;

import dev.fireball.api.event.EventBus;
import dev.fireball.api.event.ModLifecycleEvent;
import dev.fireball.config.ServerConfig;
import dev.fireball.event.SimpleEventBus;
import dev.fireball.loader.ModDiscoverer;
import dev.fireball.loader.ModLoader;
import dev.fireball.mojang.MojangIntegration;
import dev.fireball.server.MinecraftHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fireball Server Mod Loader.
 *
 * Configuration: server.yml in the server directory (auto-created).
 * CLI flags override values from server.yml.
 */
public final class FireballServer {
    public static final String LOADER_VERSION = Fireball.LOADER_VERSION;
    public static final String NAME = "Fireball Server";
    public static final String API_MOD_ID = ModLoader.API_MOD_ID;
    public static final int DEFAULT_PORT = 25565;

    public static final String MOBS_DIR_NAME = "mobs";
    public static final String CONFIG_DIR_NAME = "config";
    public static final String RESOURCES_DIR_NAME = "resources";
    public static final String RES_DIR_NAME = "res";
    public static final String LOGS_DIR_NAME = "logs";
    public static final String TMP_DIR_NAME = "tmp";

    public static final Set<String> SUPPORTED_MC_VERSIONS = Fireball.SUPPORTED_MC_VERSIONS;

    private static final Logger LOGGER = LoggerFactory.getLogger(FireballServer.class);

    private final Path serverDir;
    private final Path modPath;
    private final boolean noMod;
    private final ServerConfig config;
    private final EventBus eventBus;
    private final ModLoader modLoader;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile MinecraftHost currentHost;
    private volatile List<ModContainer> currentContainers = List.of();

    public FireballServer(Path serverDir, Path modPath, boolean noMod, ServerConfig config) {
        this.serverDir = serverDir;
        this.modPath = modPath;
        this.noMod = noMod;
        this.config = config;
        this.eventBus = new SimpleEventBus();
        this.modLoader = new ModLoader(eventBus);
    }

    public void start() {
        String mcVersion = config.getMcVersion();

        LOGGER.info("========================================");
        LOGGER.info("  {} v{}", NAME, LOADER_VERSION);
        LOGGER.info("========================================");
        LOGGER.info("Config file      : {}", serverDir.resolve(ServerConfig.FILE_NAME).toAbsolutePath());
        LOGGER.info("Target Minecraft : {}", mcVersion);
        LOGGER.info("Server directory : {}", serverDir.toAbsolutePath());
        LOGGER.info("Listen           : {}:{}", config.getServerIp(), config.getPort());
        LOGGER.info("MOTD             : {}", config.getMotd());
        LOGGER.info("Max players      : {}", config.getMaxPlayers());

        ensureServerLayout();
        try {
            MojangIntegration mojang = new MojangIntegration();
            mojang.ensureEulaFile(serverDir);
            LOGGER.info("Mojang metadata    : available for {}", mcVersion);
            if (!MojangIntegration.acceptedEula(serverDir)) {
                LOGGER.info("Mojang EULA        : not accepted (eula.txt=false; Fireball host remains usable)");
            } else {
                LOGGER.info("Mojang EULA        : accepted");
            }
        } catch (Exception e) {
            LOGGER.warn("Mojang integration unavailable: {}", e.getMessage());
        }

        List<ModContainer> containers = discoverMods();
        requireFireballApi(containers);

        if (containers.isEmpty()) {
            LOGGER.info("No mods to load.");
        } else {
            LOGGER.info("Discovered {} mod(s)", containers.size());
        }

        modLoader.loadAll(containers);
        currentContainers = List.copyOf(containers);

        boolean apiLoaded = containers.stream()
                .anyMatch(c -> API_MOD_ID.equals(c.getMetadata().getId()) && c.isLoaded());
        if (!apiLoaded) {
            LOGGER.error("FATAL: Fireball API mod (fireball_api) failed to load.");
            System.err.println("Error: Fireball API mod is required but failed to load.");
            System.exit(1);
        }

        long loaded = containers.stream().filter(ModContainer::isLoaded).count();
        eventBus.post(new ModLifecycleEvent(ModLifecycleEvent.Phase.LOAD_COMPLETE, "fireball-server"));
        LOGGER.info("Fireball Server initialization complete. {} mod(s) loaded.", loaded);

        hostMinecraft();
    }

    private List<ModContainer> discoverMods() {
        Path mobsDir = serverDir.resolve(MOBS_DIR_NAME);

        if (modPath != null) {
            LOGGER.info("Mod mode         : testing single mod (+ required API)");
            List<ModContainer> result = new ArrayList<>(new ModDiscoverer(modPath).discover());
            if (Files.isDirectory(mobsDir)) {
                for (ModContainer c : new ModDiscoverer(mobsDir).discover()) {
                    if (API_MOD_ID.equals(c.getMetadata().getId())) {
                        result.add(c);
                    }
                }
            }
            return result;
        }

        if (noMod) {
            LOGGER.info("Mod mode         : --no-mod (content mods skipped; API still required)");
            List<ModContainer> apiOnly = new ArrayList<>();
            if (Files.isDirectory(mobsDir)) {
                for (ModContainer c : new ModDiscoverer(mobsDir).discover()) {
                    if (API_MOD_ID.equals(c.getMetadata().getId())) {
                        apiOnly.add(c);
                    }
                }
            }
            return apiOnly;
        }

        LOGGER.info("Mod mode         : loading from {}/", MOBS_DIR_NAME);
        return new ModDiscoverer(mobsDir).discover();
    }

    private void requireFireballApi(List<ModContainer> containers) {
        boolean found = containers.stream()
                .anyMatch(c -> API_MOD_ID.equals(c.getMetadata().getId()));
        if (!found) {
            System.err.println("Error: Fireball API mod (fireball_api) is required to start the server.");
            System.err.println("Place fireball-api-*.jar into:");
            System.err.println("  " + serverDir.resolve(MOBS_DIR_NAME).toAbsolutePath());
            System.exit(1);
        }
        LOGGER.info("Fireball API     : found (required)");
    }

    private void hostMinecraft() {
        running.set(true);
        try (MinecraftHost host = new MinecraftHost(
                config.getMcVersion(),
                config.getServerIp(),
                config.getPort(),
                config.getMotd(),
                config.getMaxPlayers())) {
            currentHost = host;
            host.start();
            startConsole();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                host.close();
            }, "fireball-shutdown"));

            while (running.get() && host.isRunning()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start host on {}:{} — {}", config.getServerIp(), config.getPort(), e.getMessage(), e);
            System.err.println("Error: could not host: " + e.getMessage());
            System.exit(1);
        } finally {
            currentHost = null;
        }
        running.set(false);
        LOGGER.info("Fireball Server stopped.");
    }

    private void startConsole() {
        Thread console = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in))) {
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    handleCommand(line.trim());
                }
            } catch (java.io.IOException e) {
                LOGGER.debug("Console closed: {}", e.getMessage());
            }
        }, "fireball-console");
        console.setDaemon(true);
        console.start();
        System.out.println("Fireball console ready. Type 'help' for commands.");
    }

    private void handleCommand(String command) {
        if (command.isBlank()) return;
        String[] parts = command.split("\\s+", 2);
        switch (parts[0].toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> System.out.println("Commands: help, status, mods, version, mojang, gc, stop");
            case "version" -> System.out.println(NAME + " " + LOADER_VERSION + " | Minecraft " + config.getMcVersion());
            case "mojang" -> {
                try {
                    MojangIntegration integration = new MojangIntegration();
                    var release = integration.resolve(config.getMcVersion());
                    System.out.println("Mojang: " + release.version() + " | server.jar " + release.size() + " bytes | sha1 " + release.sha1());
                    System.out.println("Use --prepare-mojang to download the official server.jar.");
                } catch (Exception e) {
                    System.out.println("Mojang lookup failed: " + e.getMessage());
                }
            }
            case "status" -> {
                MinecraftHost host = currentHost;
                int online = host == null ? 0 : host.getOnlinePlayers();
                System.out.println("Status: " + (running.get() ? "RUNNING" : "STOPPING")
                        + " | " + config.getServerIp() + ":" + config.getPort()
                        + " | players " + online + "/" + config.getMaxPlayers()
                        + " | mods " + currentContainers.size());
            }
            case "mods", "list" -> {
                long loaded = currentContainers.stream().filter(ModContainer::isLoaded).count();
                System.out.println("Mods (" + loaded + "/" + currentContainers.size() + " loaded):");
                if (currentContainers.isEmpty()) System.out.println("  (none)");
                for (ModContainer c : currentContainers) {
                    System.out.println("  - " + c.getMetadata()
                            + (c.isLoaded() ? " [LOADED]" : " [FAILED]"));
                }
            }
            case "gc" -> {
                System.gc();
                System.out.println("GC requested.");
            }
            case "stop", "shutdown", "exit" -> {
                System.out.println("Stopping Fireball...");
                running.set(false);
                MinecraftHost host = currentHost;
                if (host != null) host.close();
            }
            default -> System.out.println("Unknown command. Type 'help'.");
        }
    }

    private void ensureServerLayout() {
        String[] dirs = {
                MOBS_DIR_NAME, CONFIG_DIR_NAME, RESOURCES_DIR_NAME, RES_DIR_NAME,
                LOGS_DIR_NAME, LOGS_DIR_NAME + "/crashes", TMP_DIR_NAME
        };
        for (String dir : dirs) {
            try {
                Path p = serverDir.resolve(dir);
                if (!Files.exists(p)) {
                    Files.createDirectories(p);
                }
            } catch (Exception e) {
                LOGGER.warn("Could not create directory {}: {}", dir, e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Path serverDir = Paths.get(".").toAbsolutePath().normalize();
        Path modPath = null;
        boolean noMod = false;
        boolean prepareMojang = false;

        // CLI overrides (null = not set → keep YAML value)
        String cliMcVersion = null;
        Integer cliPort = null;
        String cliMotd = null;
        Integer cliMaxPlayers = null;
        String cliServerIp = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-V", "--mcversion" -> {
                    if (i + 1 < args.length) cliMcVersion = args[++i];
                    else die("-V requires a version");
                }
                case "-t", "--test" -> {
                    if (i + 1 < args.length) modPath = Paths.get(args[++i]).toAbsolutePath().normalize();
                    else die("-t requires a path");
                }
                case "--no-mod" -> noMod = true;
                case "--prepare-mojang" -> prepareMojang = true;
                case "--serverDir", "--gameDir" -> {
                    if (i + 1 < args.length) serverDir = Paths.get(args[++i]).toAbsolutePath().normalize();
                    else die("--serverDir requires a path");
                }
                case "--port", "-p" -> {
                    if (i + 1 < args.length) {
                        try { cliPort = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { die("invalid port"); }
                    } else die("--port requires a number");
                }
                case "--motd" -> {
                    if (i + 1 < args.length) cliMotd = args[++i];
                    else die("--motd requires text");
                }
                case "--max-players" -> {
                    if (i + 1 < args.length) {
                        try { cliMaxPlayers = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { die("invalid max-players"); }
                    } else die("--max-players requires a number");
                }
                case "--server-ip" -> {
                    if (i + 1 < args.length) cliServerIp = args[++i];
                    else die("--server-ip requires an address");
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

        // Load or create server.yml
        ServerConfig config;
        try {
            Files.createDirectories(serverDir);
            config = ServerConfig.loadOrCreate(serverDir);
            LOGGER.info("Loaded {}", serverDir.resolve(ServerConfig.FILE_NAME));
        } catch (Exception e) {
            System.err.println("Error loading server.yml: " + e.getMessage());
            System.exit(1);
            return;
        }

        // CLI overrides YAML
        if (cliMcVersion != null) config.setMcVersion(cliMcVersion);
        if (cliPort != null) config.setPort(cliPort);
        if (cliMotd != null) config.setMotd(cliMotd);
        if (cliMaxPlayers != null) config.setMaxPlayers(cliMaxPlayers);
        if (cliServerIp != null) config.setServerIp(cliServerIp);

        // mc-version required from YAML or CLI
        if (config.getMcVersion() == null || config.getMcVersion().isBlank()) {
            die("mc-version must be set in server.yml or via -V");
        }
        if (!SUPPORTED_MC_VERSIONS.contains(config.getMcVersion())) {
            System.err.println("Error: Unsupported Minecraft version: " + config.getMcVersion());
            SUPPORTED_MC_VERSIONS.stream().sorted().forEach(v -> System.err.println("  " + v));
            System.exit(1);
        }
        if (config.getPort() < 1 || config.getPort() > 65535) {
            die("port must be 1-65535");
        }
        if (noMod && modPath != null) {
            die("--no-mod and -t cannot be used together");
        }
        if (modPath != null && !Files.exists(modPath)) {
            die("Mod path does not exist: " + modPath);
        }

        if (prepareMojang) {
            try {
                MojangIntegration integration = new MojangIntegration();
                integration.ensureEulaFile(serverDir);
                Path jar = integration.downloadServer(config.getMcVersion(), serverDir);
                System.out.println("Downloaded official Mojang server.jar: " + jar.toAbsolutePath());
                System.out.println("EULA file: " + serverDir.resolve("eula.txt").toAbsolutePath());
                return;
            } catch (Exception e) {
                System.err.println("Mojang download failed: " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        new FireballServer(serverDir, modPath, noMod, config).start();
    }

    private static void die(String msg) {
        System.err.println("Error: " + msg);
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println("""
            Fireball Server Mod Loader
            
            Usage: java -jar fireball-server-<version>.jar [options]
            
            Configuration file:
              <serverDir>/server.yml   Auto-created with defaults on first run.
              CLI flags override server.yml.
            
            Options:
              -V, --mcversion <ver>    Minecraft version (or set mc-version in server.yml)
              --serverDir <path>       Server directory (default: .)
              --port, -p <port>        Listen port (default from YAML: 25565)
              --motd <text>            Server list MOTD
              --max-players <n>        Max players
              --server-ip <addr>       Bind address (default: 0.0.0.0)
              --no-mod                 Skip content mods (API still required)
              --prepare-mojang         Download/verify the official Mojang server.jar for the selected version
              -t, --test <path>        Test one mod
              -v, --version            Print version
              -h, --help               Help
            
            server.yml keys:
              mc-version, port, motd, max-players, online-mode, server-ip,
              view-distance, simulation-distance, whitelist, max-tick-time,
              level-name, difficulty, pvp, allow-nether, enable-command-block,
              spawn-protection, resource-pack, resource-pack-sha1
            
            Examples:
              java -jar fireball-server-1.1.0.jar
              java -jar fireball-server-1.1.0.jar -V 1.21.1 --port 25566
              java -jar fireball-server-1.1.0.jar --serverDir ./myserver
            """);
    }
}
