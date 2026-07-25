# MineUI Server Utils

A **Forge mod** that runs inside a Minecraft server and exposes live server
information to the [MineUI](../mineui) desktop app over a small HTTP API.

Because it runs in the server JVM, it reports data a detached process cannot
reach: real tick-based TPS/MSPT, loaded chunk and entity counts per dimension,
the actual loaded mod list, and — when the server runs in a container — the
container's own cgroup resource usage.

> This is a mod, not a standalone jar. There is no `main()` and `java -jar`
> will not work. Install the built jar into a Forge server's `mods/` folder.

## Requirements

- Minecraft **1.20.1**, Forge **47.4.0** (see `gradle.properties`)
- JDK **17** to build (selected automatically by the Gradle toolchain)

## Build

```bash
./gradlew build
```

Output jar:

```
build/libs/mineui_server_utils-<version>.jar
```

The Gradle wrapper is committed, so no local Gradle install is required. The
first build downloads Forge's userdev artifacts and Minecraft mappings and may
take several minutes.

## Install

Drop the built jar into the Forge server's `mods/` directory and start (or
restart) the server. The API comes up when the server finishes starting and
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
| `GET /mods` | `{ ok, mods[] }` — loaded mod id/name/version |
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
- This mod is currently **Forge 1.20.1 only**. A loader-agnostic core plus a
  Paper/Bukkit adapter is the planned path to broader server coverage.
