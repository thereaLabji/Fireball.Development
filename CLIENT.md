# Fireball 1.2.0 — Client + Server

Fireball now ships as two runnable JARs:

- `fireball-client-1.2.0.jar` — normal client-side Fireball loader/bootstrap
- `fireball-server-1.2.0.jar` — dedicated server-side Fireball loader

Both use the same Fireball mod API and mod discovery system.

## One command — client

Windows:

```bat
fireball-client.bat -V 1.21.1
```

Linux/macOS:

```bash
./fireball-client.sh -V 1.21.1
```

Prepare all of the official Minecraft files without starting the game:

```bat
fireball-client.bat -V 1.21.1 --prepare-mojang
```

This resolves the official version metadata, downloads and SHA-1 verifies the client JAR, libraries, asset index/assets, and platform natives into the selected game directory.

## Launching Minecraft 1.21.1

Minecraft 1.21.1 requires Java 21. Sign in using Microsoft's device-code page:

```bat
fireball-client.bat -V 1.21.1 --login --ms-client-id YOUR_APP_ID
```

For an explicitly offline, single-player-only session:

```bat
fireball-client.bat -V 1.21.1 --offline PlayerName
```

The launcher can also accept an already-issued Minecraft access token:

```bat
fireball-client.bat -V 1.21.1 --accessToken TOKEN --uuid UUID --username NAME
```

Use `--java C:\path\to\java.exe` when Java 21 is not first on `PATH`. Fireball starts the official client with its packaged Java agent. The agent reuses the existing API, event bus, discovery, and mod lifecycle inside the Minecraft JVM, rather than in the launcher process. The first runtime bridge revision is intentionally observation-only: it establishes the safe injection boundary but does not yet transform Minecraft bytecode.

Microsoft does not permit Fireball to impersonate its official launcher application. Create an app registration under **Microsoft Entra ID → App registrations → New registration**, select **Accounts in any organizational directory and personal Microsoft accounts**, then enable **Allow public client flows** under Authentication. Pass its Application (client) ID with `--ms-client-id`. A public client has no client secret.

## Fabric bootstrap (experimental)

Fireball can start Minecraft 1.20.1 through the bundled Fabric Loader 0.16.10/Knot runtime:

```bat
fireball-client.bat -V 1.20.1 --offline PlayerName --fabric
```

Place Fabric mod JARs in `fabric-mods/`, not `mobs/`. This is the first compatibility bootstrap. It provides Fabric Loader and Mixin startup; Fabric API and Sodium are the next validation targets.

## One command — server

Windows:

```bat
fireball-server.bat -V 1.21.1
```

Linux/macOS:

```bash
./fireball-server.sh -V 1.21.1
```

## Fireball API requirement

`fireball-assistant-1.0.0.jar` is Fireball's own mod for this round. Its metadata explicitly requires:

```json
"depends": ["fireball_api"]
```

Put **both** of these into the runtime's `mobs/` directory:

```text
mobs/
├── fireball-api-1.0.0.jar
└── fireball-assistant-1.0.0.jar
```

If `fireball-api` is missing, Fireball rejects the assistant mod instead of silently loading it.

## What the own mod demonstrates

When loaded, Fireball Assistant prints:

```text
[Fireball Assistant] Loaded through Fireball API.
[Fireball Assistant] API dependency: fireball_api ✓
[Fireball Assistant] Fireball is ready. 🔥
```

It works with both the client loader and server loader because both share the same Fireball API/mod-loading core.
