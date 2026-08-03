<h1 align="center">MineUI Server Utils</h1>

<p align="center">
  <b>The in-server agent behind MineUI's enriched metrics.</b><br>
  A Forge mod / Paper plugin that exposes live Minecraft server data over<br>
  a small, authenticated HTTP API — because it runs inside the JVM, it sees<br>
  what a detached process can't.
</p>

<p align="center">
  <a href="https://github.com/I4cTime/mineui_server_utils/actions/workflows/build.yml"><img src="https://github.com/I4cTime/mineui_server_utils/actions/workflows/build.yml/badge.svg" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-blue" alt="License: AGPL-3.0"></a>
  <img src="https://img.shields.io/badge/platform-Java%2017%20%7C%20Minecraft%201.20.1-informational" alt="Platform: Java 17, Minecraft 1.20.1">
  <a href="https://discord.gg/5uEApw5uEz"><img src="https://img.shields.io/badge/discord-join%20the%20studio-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
</p>

<p align="center">
  <a href="#why-mineui-server-utils">Why</a> &middot;
  <a href="#install">Install</a> &middot;
  <a href="#features">Features</a> &middot;
  <a href="#development">Development</a> &middot;
  <a href="#license">License</a>
</p>

---

## Why MineUI Server Utils?

[MineUI](https://github.com/I4cTime/mineui) can already read TPS and player
data from outside the server — an RCON `tps` command, a status ping — but
that view is limited: no true per-window MSPT, no per-dimension chunk/entity
counts, no accurate loaded-mod list, and no way to see a container's own
cgroup resource usage from inside it.

MineUI Server Utils closes that gap by running *inside* the server JVM as a
mod or plugin and exposing what it sees over a small HTTP API, so MineUI (or
anything else) can read it. It is not a standalone jar — there's no `main()`
and `java -jar` won't work; it has to be loaded by a Forge or Paper server.

## Install

- **Forge:** drop the built jar into the server's `mods/` directory.
- **Paper/Bukkit:** drop the built jar into the server's `plugins/`
  directory.

Restart the server — the API comes up when the server finishes starting and
shuts down with it. There's no packaged release yet; build the jars yourself
— see [Development](#development).

## Features

### Two adapters, one core

A Gradle multi-project split into a loader-agnostic `core` (HTTP API, auth,
JSON, config, system/cgroup stats — no Minecraft/loader imports) and thin
per-platform adapters implementing a shared `ServerRuntime` interface:

| Module | Output | Target |
| --- | --- | --- |
| `core` | (bundled into the adapters) | Shared logic, no MC/loader imports |
| `forge` | Forge mod jar | Forge **1.20.1** |
| `paper` | Bukkit plugin jar | Paper/Spigot/Purpur/Folia and Bukkit-compatible hybrids (Mohist, Arclight, …) |

Adding a new platform (Fabric, NeoForge) means a new adapter module
implementing `ServerRuntime`; the core never changes.

### Endpoints

All responses are JSON except `/health`. All except `/health` require the
bearer token when one is configured.

| Endpoint | Returns |
| --- | --- |
| `GET /health` | `ok` (plain text, unauthenticated) |
| `GET /properties` | Filtered `server.properties` key/values |
| `GET /players` | `{ ok, players[] }` — online player names |
| `GET /tps` | `{ ok, one, five, fifteen, raw }` — in-JVM tick rate |
| `GET /metrics` | TPS, MSPT, chunks, entities, per-dimension counts, system/container stats |
| `GET /mods` | `{ ok, mods[] }` — loaded mods (Forge) or plugins (Paper) as id/name/version |
| `GET /status` | Aggregate of properties, players, tps, metrics, and system stats |

```bash
curl -H "Authorization: Bearer $MINEUI_SERVER_UTILS_TOKEN" \
  http://127.0.0.1:8787/metrics
```

### Loopback-only by default, authenticated when remote

| Variable | Default | Purpose |
| --- | --- | --- |
| `HTTP_HOST` | `127.0.0.1` | Bind address. Binding non-loopback (e.g. `0.0.0.0`) **requires** `MINEUI_SERVER_UTILS_TOKEN` — the API refuses to start without it. |
| `HTTP_PORT` | `8787` | Listen port. |
| `MINEUI_SERVER_UTILS_TOKEN` | _(unset)_ | Shared secret. When set, every endpoint except `/health` requires `Authorization: Bearer <token>`, compared in constant time. |
| `SERVER_PROPERTIES_PATH` | `<serverDir>/server.properties` | Path to `server.properties`. |

Out of the box the API is reachable only from the same machine, so a local
MineUI instance works with zero config. To let another machine (e.g. MineUI
on a laptop, server on a desktop) reach it, set both `HTTP_HOST=0.0.0.0` and
a strong token — the server won't bind a public interface without one.
`rcon.password`, `rcon.port`, and `query.port` are always stripped from
`/properties` and `/status`.

### Container-aware, without a container CLI

CPU/memory stats are read directly from the kernel cgroup interface
(`/sys/fs/cgroup`), so they work from inside a Podman/Docker container with
no `podman`/`docker` CLI present. They're only reported when the process is
actually containerized; a bare-metal server gets host-level metrics instead.

### One caveat: Paper's MSPT is a single average

Paper reports `Bukkit.getAverageTickTime()`, so the 1m/5m/15m fields in
`/tps` and `/metrics` all report that same rolling value — Bukkit has no
per-window MSPT breakdown. The Forge adapter reports true per-window MSPT
from its own tick tracker.

## Development

Requires **JDK 17** (auto-provisioned by the Gradle toolchain's Foojay
resolver if you don't have one). The Gradle wrapper is committed, so no
local Gradle install is needed.

```bash
./gradlew build          # builds and tests all modules
./gradlew :forge:build   # Forge mod only
./gradlew :paper:build   # Paper plugin only
```

Output jars: `forge/build/libs/forge-<version>.jar` and
`paper/build/libs/paper-<version>.jar`. The first Forge build downloads
Forge's userdev artifacts and Minecraft mappings and can take several
minutes.

CI (`.github/workflows/build.yml`, workflow name `build`) runs
`./gradlew build --no-daemon --stacktrace` on every push and PR, plus a
weekly job that flags any pinned GitHub Action that's fallen behind a major
version. See [CONTRIBUTING.md](CONTRIBUTING.md) for module boundaries and PR
conventions.

## License

AGPL-3.0-only — see [LICENSE](LICENSE).
