# MineUI Server Utils (Java)

Lightweight Java service that exposes Minecraft server information over HTTP.

## Build

```bash
gradle clean jar
```

Jar output:

```
build/libs/mineui-server-utils.jar
```

## Run

```bash
java -jar build/libs/mineui-server-utils.jar
```

## Configuration (env vars)

- `HTTP_PORT` (default `8787`)
- `HTTP_HOST` (default `0.0.0.0`)
- `RCON_HOST` (default `127.0.0.1`)
- `RCON_PORT` (default `25575`)
- `RCON_PASSWORD` (required for RCON endpoints)
- `RCON_TIMEOUT_MS` (default `3000`)
- `SERVER_PROPERTIES_PATH` (default `server.properties`)

## Endpoints

- `GET /health`
- `GET /properties`
- `GET /players`
- `GET /tps`
- `GET /metrics`
- `GET /mods`
- `GET /status`
