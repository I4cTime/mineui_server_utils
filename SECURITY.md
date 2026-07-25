# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.2.x   | Yes       |
| < 0.2   | No        |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please report them privately using one of the following methods:

### 1. GitHub Private Vulnerability Reporting (preferred)

Use GitHub's built-in private reporting:

**[Report a vulnerability](https://github.com/I4cTime/mineui_server_utils/security/advisories/new)**

### 2. Email

Send details to the maintainer directly. You can find contact information on the [@I4cTime GitHub profile](https://github.com/I4cTime).

## What to Include

When reporting, please provide:

- **Description** of the vulnerability and its potential impact.
- **Steps to reproduce** or a proof of concept.
- **Affected version(s)** of mineui_server_utils.
- **Environment** details (Forge or Paper, Minecraft/server version, Java version, containerized or bare metal).
- **Suggested fix**, if you have one.

## Response Timeline

| Stage | Timeframe |
|-------|-----------|
| Acknowledgement | Within 48 hours |
| Initial assessment | Within 7 days |
| Fix or mitigation | Varies by severity |
| Public disclosure | After a fix is released |

## Scope

The following areas are in scope for security reports:

- **HTTP API authentication** — bypasses of the bearer-token check, timing side channels in token comparison, or the API binding to a non-loopback address without a token configured.
- **`server.properties` filtering** — leakage of `rcon.password`, `rcon.port`, `query.port`, or other sensitive keys via `/properties` or `/status`.
- **Container stats reader** — path traversal or unintended host filesystem access via the cgroup interface (`/sys/fs/cgroup`) reader.
- **Forge and Paper adapters** — any way an unauthenticated request reaches server internals, or a way a request from one dimension/world context leaks data across server boundaries.
- **Build supply chain** — compromised Gradle plugins/dependencies, or GitHub Actions running with excess permissions.

## Out of Scope

- Vulnerabilities in upstream dependencies (report those to the respective project — Forge, Paper/Bukkit, etc.).
- Issues requiring physical or already-authenticated admin access to the server host.
- Social engineering attacks.

## Recognition

We're happy to credit security researchers in the release notes and CHANGELOG unless you prefer to remain anonymous. Let us know your preference when reporting.
