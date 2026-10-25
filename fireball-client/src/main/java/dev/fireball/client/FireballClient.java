package dev.fireball.client;

import dev.fireball.Fireball;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Fireball's normal client-side mod-loader entrypoint. */
public final class FireballClient {
    public static final String VERSION = "1.2.0";

    public static void main(String[] args) throws Exception {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        String mcVersion = null;
        boolean noMod = false;
        boolean prepareOnly = false;
        boolean login = false;
        boolean fabric = false;
        String offlineName = null, accessToken = null, uuid = null, username = null, javaPath = null, microsoftClientId = null;
        List<String> extraJvmArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-V", "--mcversion" -> {
                    if (++i >= args.length) die("-V requires a Minecraft version");
                    mcVersion = args[i];
                }
                case "--gameDir" -> {
                    if (++i >= args.length) die("--gameDir requires a path");
                    gameDir = Paths.get(args[i]).toAbsolutePath().normalize();
                }
                case "--no-mod" -> noMod = true;
                case "--prepare-mojang", "--prepare-only" -> prepareOnly = true;
                case "--login" -> login = true;
                case "--fabric" -> fabric = true;
                case "--offline" -> { if (++i >= args.length) die("--offline requires a player name"); offlineName = args[i]; }
                case "--accessToken" -> { if (++i >= args.length) die("--accessToken requires a token"); accessToken = args[i]; }
                case "--uuid" -> { if (++i >= args.length) die("--uuid requires a UUID"); uuid = args[i]; }
                case "--username" -> { if (++i >= args.length) die("--username requires a name"); username = args[i]; }
                case "--java" -> { if (++i >= args.length) die("--java requires a Java executable path"); javaPath = args[i]; }
                case "--ms-client-id" -> { if (++i >= args.length) die("--ms-client-id requires an Azure application client ID"); microsoftClientId = args[i]; }
                case "--jvmArg" -> { if (++i >= args.length) die("--jvmArg requires an argument"); extraJvmArgs.add(args[i]); }
                case "-v", "--version" -> {
                    System.out.println("Fireball Client " + VERSION);
                    return;
                }
                case "-h", "--help" -> { printHelp(); return; }
                default -> die("Unknown option: " + args[i]);
            }
        }

        if (mcVersion == null) die("Minecraft version is required. Use -V <mcversion>");
        if (!Fireball.SUPPORTED_MC_VERSIONS.contains(mcVersion)) {
            die("Unsupported Minecraft version: " + mcVersion);
        }

        Files.createDirectories(gameDir.resolve("mobs"));
        Files.createDirectories(gameDir.resolve("config"));
        Files.createDirectories(gameDir.resolve("logs"));

        MinecraftLauncher.Options options = new MinecraftLauncher.Options(
                gameDir, mcVersion, prepareOnly, login, fabric, offlineName, accessToken, uuid, username, javaPath, microsoftClientId, extraJvmArgs);
        MinecraftLauncher launcher = new MinecraftLauncher(options);
        launcher.prepare();
        if (prepareOnly) return;

        // The Java agent loads the unchanged Fireball lifecycle inside Minecraft's JVM.
        launcher.launch(noMod);
    }

    private static void die(String message) {
        System.err.println("Error: " + message);
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println("""
            Fireball Client
            Usage: java -jar fireball-client-1.2.0.jar -V <mcversion> [options]
              -V, --mcversion <ver>   Target Minecraft version
              --gameDir <path>        Client directory (default: current directory)
              --no-mod                Start without mods
              --prepare-mojang        Download/verify all official launch files, then exit
              --login                 Sign in with Microsoft device-code authentication
              --fabric                Start through Fabric Loader (1.20.1 bootstrap)
              --offline <name>        Launch without online services (single-player only)
              --accessToken <token> --uuid <uuid> --username <name>
                                    Use an existing Minecraft access token
              --java <path>           Java 21 executable (default: java on PATH)
              --ms-client-id <uuid>   Your Microsoft Entra public-client application ID
              --jvmArg <arg>          Append a JVM argument (repeatable)
              -v, --version           Print version
              -h, --help              Show this help
            """);
    }
}
