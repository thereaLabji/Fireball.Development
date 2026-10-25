# Fireball Mod Loader 1.2/1.0
>!(WARN)
> This is not a ready project, it's in development and some items need to be fixed.
> If you see that it's ready (aka replaced with this text with Note), you can start using it.
> Mod making for this loader is not optional but PLEASE add Fireball API into `mobs/` dir so it can start.
TODO: Fix items.

**Fireball** is an educational, from-scratch Minecraft mod loader prototype written in **Java**.

Runs on **Windows**, **macOS**, and **Linux** (Java 17+).

## Round 3 client + server artifacts

| File | Purpose |
|------|---------|
| `fireball-client-1.2.0.jar` | **Normal Fireball client-side loader/bootstrap** |
| `fireball-server-1.2.0.jar` | **Fireball server loader** |
| `fireball-api-1.0.0.jar` | Required Fireball API mod |
| `fireball-assistant-1.0.0.jar` | Fireball's own mod; requires `fireball_api` |
| `fireball-client.bat` / `.sh` | One-command client launcher |
| `fireball-server.bat` / `.sh` | One-command server launcher |

## One-command quick start

### Windows

Open Command Prompt in the Fireball folder and run:

```bat
fireball-server.bat -V 1.21.1
```

### Linux / macOS

Run:

```bash
./fireball-server.sh -V 1.21.1
```

If needed, make the script executable once with `chmod +x fireball-server.sh`.

### Optional: download Mojang's official server JAR

```text
Windows: fireball-server.bat -V 1.21.1 --prepare-mojang
Linux/macOS: ./fireball-server.sh -V 1.21.1 --prepare-mojang
```

This downloads and SHA-1 verifies the official Mojang `server.jar` into `mojang/<version>/`. Fireball does not bundle Mojang's proprietary server JAR.

For a full step-by-step guide, see `RUN.md`.

## Round 3 prototype upgrades (1.1.0)

This build keeps the original prototype architecture and adds a small server-management layer:

- **Interactive console**: `help`, `status`, `mods`, `version`, `gc`, `stop`
- **Live server status**: bind address, port, player count, and loaded/failed mod count
- **Persistent MOTD** in `server.yml` under `voltage.java.motd`
- **Graceful shutdown** through the `stop` console command
- **Online player accessor** exposed by the minimal Minecraft host
- Existing **mod discovery, dependency ordering, class isolation, lifecycle events, and Server List Ping** remain intact

> Prototype limitation: this is still **not a full Minecraft implementation**. Server List Ping works, while the Login/Play path intentionally disconnects because the full Minecraft game protocol is not implemented yet.

## Arguments

| Flag | Description |
|------|-------------|
| **`-V <mcversion>`** | **Required.** Target Minecraft version |
| *(none)* | Load all mods from `<serverDir>/mobs` |
| `--no-mod` | Run with no mods |
| `-t <path>` | Test a specific mod JAR |
| `--serverDir <path>` | Server directory (default: `.`) |
| `-v` | Print version |
| `-h` | Help |

Paths work with both `\` (Windows) and `/` (macOS/Linux).

## Server layout

```
server/
├── mobs/              ← mod JARs (.jar.disabled to disable)
├── config/
├── resources/
├── res/
├── logs/crashes/
└── tmp/
```

Example: `fireball-example-dir/`

## Supported Minecraft Versions

| Family | Versions |
|--------|----------|
| **1.19** | 1.19, 1.19.1 |
| **1.20** | 1.20, 1.20.1, 1.20.2, 1.20.4, 1.20.6 |
| **1.21** | 1.21 – 1.21.11 |
| **26.x** | 26.1, 26.2, 26.x |

## Requirements

- **Java 17+** (JDK or JRE) on PATH  
  - Windows: https://adoptium.net  
  - macOS: `brew install openjdk@17` or Adoptium  
  - Linux: `sudo apt install openjdk-17-jre` (or equivalent)

## Building

```bash
./gradlew :fireball-loader:dist
```

Produces JARs + launch scripts under `dist/`.

## Mojang integration

Round 3 now includes an **official Mojang metadata/server download integration**. Fireball does not bundle or redistribute Mojang's proprietary server JAR; it resolves the selected version from Mojang's official version manifest and downloads the server JAR directly when requested.

```bash
java -jar fireball-server-1.1.0.jar -V 1.21.1 --prepare-mojang
```

This creates:

```text
mojang/1.21.1/server.jar
```

and an `eula.txt` template. The downloaded JAR is SHA-1 verified against Mojang's metadata. The `mojang` console command prints the official server artifact metadata.

**Important:** Fireball's custom host is still a prototype and does not yet inject Fireball mods into Mojang's server JAR. The next major step is a real vanilla protocol/runtime bridge (Login, Play, registries, chunks, entities, commands, etc.).

## Round 3: client/server split

Fireball now has separate runnable client and server JARs. Both share the same Fireball mod-loading core and can load mods from `mobs/`.

The included `fireball-assistant` is Fireball's own mod and explicitly declares `fireball_api` in its dependency list. Fireball validates this dependency before loading the mod.

### Client

```text
fireball-client.bat -V 1.21.1
```

or:

```text
./fireball-client.sh -V 1.21.1
```

### Server

```text
fireball-server.bat -V 1.21.1
```

or:

```text
./fireball-server.sh -V 1.21.1
```

Put these into `mobs/` to load the included mod:

```text
mobs/fireball-api-1.0.0.jar
mobs/fireball-assistant-1.0.0.jar
```

The official Mojang client/server artifacts remain separate from Fireball's loader JARs. Fireball can download the official Mojang artifacts from Mojang's metadata service and verify their SHA-1 hashes; it does not bypass Minecraft authentication. Minecraft 1.21.1 officially has both client and server artifacts and requires Java 21.
