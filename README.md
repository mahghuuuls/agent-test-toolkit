# Agent Test Toolkit

Prepares Minecraft 1.12.2 test environments and records generic game events as structured text in `latest.log`, so a developer or an AI coding agent can diagnose a manual test without relying on screenshots.

> **Intended for disposable testing worlds.** Its world-changing commands have no confirmation prompt and no undo.

Minecraft 1.12.2, Forge. Required on the server, optional on the client. Clients without it can still connect.

## Status

Initialized baseline. No behavior is implemented yet.

## Build

```bash
./gradlew clean build
```

Gradle runs on Java 25. The compile toolchain is Java 8 and is provisioned automatically.

## License

MIT, see [LICENSE](LICENSE). Upstream template notices are retained in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
