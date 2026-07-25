# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.2.0] — 2026-07-25

### Added
- **Paper adapter** (`paper` module) — Bukkit plugin targeting Paper/Spigot/Purpur/Folia and Bukkit-compatible hybrids (Mohist, Arclight, ...), alongside the existing Forge 1.20.1 adapter.
- **Loader-agnostic `core` module** — HTTP API, auth, JSON, config, and system/cgroup stats extracted behind a `ServerRuntime` interface, shared by both adapters. Adding a future platform (Fabric, NeoForge) only requires a new adapter implementing `ServerRuntime`.
- **Bearer-token authentication** — when `MINEUI_SERVER_UTILS_TOKEN` is set, every endpoint except `/health` requires `Authorization: Bearer <token>`, compared in constant time.
- **Loopback-only default bind** — the API refuses to bind a non-loopback `HTTP_HOST` unless a token is configured, so remote exposure is opt-in and always authenticated.
- **`server.properties` filtering** — `rcon.password`, `rcon.port`, and `query.port` are stripped from `/properties` and `/status` responses.
- **Container-aware stats** — CPU/memory usage read directly from the cgroup interface (`/sys/fs/cgroup`) when running inside a Podman/Docker container, alongside host-level metrics on bare metal.
- **Gradle multi-project build** — `core`, `forge`, `paper` modules with a pinned JDK 17 toolchain (Foojay resolver) and a committed Gradle wrapper.
- **CI** — `build.yml` runs `./gradlew build` on every push/PR with least-privilege (`contents: read`) permissions, plus a weekly action-currency check that flags outdated pinned GitHub Actions.
- **Dependabot** for GitHub Actions and Gradle dependencies, grouped and weekly.

### Security
- Secrets (the bearer token) are never logged; startup failure messages avoid echoing the configured token value.

## [0.1.0] — 2026-01-28

### Added
- Initial Forge 1.20.1 mod exposing Minecraft server info (players, TPS, mods, properties) over an unauthenticated HTTP API for MineUI.
