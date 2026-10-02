# webjars-locator-core

Locates assets within WebJars on the classpath (`org.webjars:webjars-locator-core`). Published to Maven Central; superseded for new code by webjars-locator-lite.

Follow the `zen-of-projects` Skill (extract it with `./mvnw -q skillsjars:extract`); this file records
only project-specific facts and exceptions.

## Skills

`zen-of-projects`, `zen-of-james` (from `com.jamesward:skills`, extracted to the gitignored `.kiro/skills/`).

## MCP

`javadocs` (https://www.javadocs.dev/mcp), configured in `.mcp.json` / `.kiro/settings/mcp.json` and
approved in `.claude/settings.json`. Use its `get_latest_version` for version lookups and its
source/doc tools for API questions. In Claude Code its tools are deferred: load them with ToolSearch
(search `javadocs`).

## Build & test

- Full validation: `./mvnw -B -ntp verify`.

## Maintenance routine

`.factory/MAINTENANCE.md` (weekly), following the `zen-of-projects` Skill.

## Exceptions to zen-of-projects

- **Old Java target:** The bytecode target is Java 8 (`maven-compiler-plugin` `<source>`/`<target>` 1.8) and CI builds and tests on Java 8 (Linux and Windows), because existing applications on old JDKs still depend on this library. Don't raise the Java version or the bytecode target in the maintenance routine; label the PR `needs-human` if a dependency update requires it.
- **Releases:** never run a release or deploy in the maintenance routine.
