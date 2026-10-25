# Fireball — One-command run

## Windows

Open **Command Prompt** in the Fireball folder and run:

```bat
fireball-server.bat -V 1.21.1
```

That is the one command needed to start the Fireball server prototype.

If you want Fireball to download/verify Mojang's official server JAR first:

```bat
fireball-server.bat -V 1.21.1 --prepare-mojang
```

Then start it with the first command.

## Linux / macOS

From the Fireball folder:

```bash
./fireball-server.sh -V 1.21.1
```

If the script is not executable yet, run this once:

```bash
chmod +x fireball-server.sh
```

To download/verify Mojang's official server JAR first:

```bash
./fireball-server.sh -V 1.21.1 --prepare-mojang
```

## First run

Fireball automatically creates `server.yml`, `mobs/`, `config/`, `resources/`, `res/`, `logs/`, and `tmp/`.

The Mojang integration also creates `eula.txt`. If you intend to run the official Mojang server itself, you must review and accept Mojang's EULA yourself by setting `eula=true`.

## Change Minecraft version

Replace `1.21.1` with a supported version:

```text
fireball-server.bat -V <version>
```

or:

```bash
./fireball-server.sh -V <version>
```

## Server console

Once running:

```text
help
status
mods
version
mojang
gc
stop
```

`stop` performs a graceful Fireball shutdown.
