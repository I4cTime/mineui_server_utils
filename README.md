# MineUI Server Utils

An in-server agent that exposes live Minecraft server information to the
[MineUI](../mineui) desktop app over a small HTTP API.

Because it runs inside the server JVM, it reports data a detached process
cannot reach: real tick-based TPS/MSPT, loaded chunk and entity counts per
dimension/world, the actual loaded mod/plugin list, and — when the server runs
in a container — the container's own cgroup resource usage.

> This is a server mod/plugin, not a standalone jar. There is no `main()` and
> `java -jar` will not work. Install the built jar into your server.

## Modules

The project is a Gradle multi-project split into a loader-agnostic core and
thin per-platform adapters:

| Module | Output | Target |
| --- | --- | --- |
| `core` | (bundled into the adapters) | HTTP API, auth, JSON, config, system/cgroup stats, and the `ServerRuntime` interface — no Minecraft/loader imports |
| `forge` | Forge mod jar | Forge **1.20.1** |
| `paper` | Bukkit plugin jar | Paper/Spigot/Purpur/Folia and Bukkit-compatible hybrids (Mohist, Arclight, …) |

Adding a new platform (Fabric, NeoForge) is a new adapter module implementing
`ServerRuntime`; the core never changes.

## Requirements

- JDK **17** to build (selected automatically by the Gradle toolchain)
- Forge adapter: Minecraft **1.20.1**, Forge **47.4.0** (see `gradle.properties`)
- Paper adapter: Bukkit API **1.20.1** (`paperApiVersion` in `gradle.properties`)

## Build

```bash
./gradlew build          # builds all modules
./gradlew :forge:build   # Forge mod only
./gradlew :paper:build   # Paper plugin only
```

Output jars:

```
forge/build/libs/forge-<version>.jar
paper/build/libs/paper-<version>.jar
```

The Gradle wrapper is committed, so no local Gradle install is required. The
first Forge build downloads Forge's userdev artifacts and Minecraft mappings
and may take several minutes.

## Install

- **Forge:** drop the forge jar into the server's `mods/` directory.
- **Paper/Bukkit:** drop the paper jar into the server's `plugins/` directory.

Restart the server. The API comes up when the server finishes starting and
shuts down with it.

## Configuration (environment variables)

| Variable | Default | Purpose |
| --- | --- | --- |
| `HTTP_HOST` | `127.0.0.1` | Bind address. Loopback by default. Binding a non-loopback address (e.g. `0.0.0.0`) **requires** `MINEUI_SERVER_UTILS_TOKEN`; without it the API refuses to start. |
| `HTTP_PORT` | `8787` | Listen port. |
| `MINEUI_SERVER_UTILS_TOKEN` | _(unset)_ | Shared secret. When set, every endpoint except `/health` requires `Authorization: Bearer <token>`. Required to expose the API off-host. |
| `SERVER_PROPERTIES_PATH` | `<serverDir>/server.properties` | Path to `server.properties`. |

### Security model

- **Loopback-only by default.** Out of the box the API is reachable only from
  the same machine, so a local MineUI instance works with zero config.
- **Remote access is opt-in and authenticated.** To let another machine on the
  LAN (e.g. MineUI on a laptop, server on a desktop) reach it, set both
  `HTTP_HOST=0.0.0.0` and a strong `MINEUI_SERVER_UTILS_TOKEN`. The server will
  not bind a public interface without a token.
- **`server.properties` is filtered.** `rcon.password`, `rcon.port`, and
  `query.port` are never returned by `/properties` or `/status`.
- Tokens are compared in constant time.

## Endpoints

All return JSON except `/health`. All except `/health` require the bearer token
when one is configured.

| Endpoint | Returns |
| --- | --- |
| `GET /health` | `ok` (plain text, unauthenticated) |
| `GET /properties` | Filtered `server.properties` key/values |
| `GET /players` | `{ ok, players[] }` — online player names |
| `GET /tps` | `{ ok, one, five, fifteen, raw }` — in-JVM tick rate |
| `GET /metrics` | TPS, MSPT, chunks, entities, per-dimension counts, system/container stats |
| `GET /mods` | `{ ok, mods[] }` — loaded mods (Forge) or plugins (Paper) as id/name/version |
| `GET /status` | Aggregate of properties, players, tps, metrics, and system stats |

Example:

```bash
curl -H "Authorization: Bearer $MINEUI_SERVER_UTILS_TOKEN" \
  http://127.0.0.1:8787/metrics
```

## Notes

- **Container stats** are read directly from the kernel cgroup interface
  (`/sys/fs/cgroup`), so they work from inside a Podman/Docker container with no
  `podman`/`docker` CLI present. They are only reported when the process is
  actually containerized; on a bare-metal server the host-level metrics apply
  instead.
- **Paper MSPT** is a single rolling average (`Bukkit.getAverageTickTime()`);
  the 1m/5m/15m fields all report that value since Bukkit has no per-window
  MSPT breakdown. The Forge adapter reports true per-window MSPT from its own
  tick tracker.
