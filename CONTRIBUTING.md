# Contributing to mineui_server_utils

Thanks for helping improve mineui_server_utils! This guide covers the dev environment, project conventions, and the release-adjacent files you must keep in sync.

## Dev environment

- **JDK 17** to build (selected automatically by the Gradle toolchain via the Foojay resolver, so a local install isn't strictly required — but if you keep JDKs under `~/.local/jvm`, point `JAVA_HOME` at the `jdk-17.*` install):

  ```bash
  export JAVA_HOME=~/.local/jvm/jdk-17.0.12+7
  ./gradlew build
  ```

- The Gradle wrapper is committed — no local Gradle install is required.
- Useful commands:

  | Command | What it does |
  |---|---|
  | `./gradlew build` | Builds all modules (core, forge, paper) |
  | `./gradlew :core:build` | Builds and tests the loader-agnostic core only |
  | `./gradlew :forge:build` | Forge mod only (first run downloads Forge userdev + MC mappings — can take several minutes) |
  | `./gradlew :paper:build` | Paper plugin only |

## Module layout

The project is a Gradle multi-project split into a loader-agnostic core and thin per-platform adapters — see the README's Modules table for details. When contributing:

- **`core`** must stay free of Minecraft/Forge/Bukkit imports. HTTP API, auth, JSON, config, and system/cgroup stats live here behind the `ServerRuntime` interface.
- **`forge`** and **`paper`** are thin adapters that implement `ServerRuntime` for their platform. Keep behavior (endpoints, response shapes) consistent between them unless a platform genuinely can't support a feature (e.g. Paper's single rolling-average MSPT vs. Forge's per-window tracker — documented in the README, not silently diverged).
- A new platform (Fabric, NeoForge) is a new adapter module implementing `ServerRuntime`; the core never changes for it.

## Branches and commits

- Work happens on feature branches off `main`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `build:`, `ci:`, with optional scope — e.g. `fix(paper): …`), matching the existing history and the Dependabot `commit-message.prefix` config.
- Before opening a PR, run `./gradlew build` — the PR template checklist mirrors CI.

## CI conventions

- `build.yml` runs `./gradlew build --no-daemon --stacktrace` on every push/PR against JDK 17.
- A separate **action-currency** job (`check-action-versions`) compares every pinned GitHub Action in `.github/workflows/` to its latest upstream release and fails loudly (with a summary table) if any are behind a major version — a fallback for updates Dependabot can't open on its own (e.g. SHA pins). It doesn't gate `build`; treat its failures as a signal to update the pin, not a blocker to override.
- Dependabot keeps GitHub Actions (`ci` commit prefix) and Gradle plugins/dependencies (`build` commit prefix) current on the first and second Monday of each month — don't hand-roll version bump PRs for things Dependabot already covers.

## CHANGELOG

Add your change under `[Unreleased]` in `CHANGELOG.md` (Keep a Changelog format) for anything user-facing: new endpoints, config changes, behavior changes, security fixes.

## Security issues

Do **not** open a public issue for vulnerabilities — follow [SECURITY.md](SECURITY.md).

## License

By contributing you agree your work is licensed under [AGPL-3.0-only](LICENSE), the same license as the project.
