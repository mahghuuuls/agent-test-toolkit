# Agent Test Toolkit

A Minecraft Forge 1.12.2 utility mod that prepares test environments and records what happens as structured text in `latest.log`, so an AI coding agent can diagnose a manual test without screenshots.

A human plays. The toolkit sets up, observes, and reports.

## Warning: use a disposable world

**This mod modifies the world irreversibly and without confirmation.**

`arena create` replaces every block in its volume. `arena reset` and `arena clear` delete every non player entity inside the arena bounds, dropped items included. Nothing prompts, and nothing can be undone.

That is a deliberate design choice, not an oversight. A confirmation prompt would make these commands unusable from a bundle, and resetting the arena is the most common first step of a setup bundle. The size limit on arena creation is the only guard.

Commands can also run from a command block, so a bundle can be triggered by redstone with no human involved.

Use a world you are willing to lose.

## What it does

The core of it is evidence discipline:

- **Command bundles.** Named lists of commands in JSON, run with one command, with per command tick delays and stop on failure. They live outside the world save and mark their own start and end in the log.
- **Test arenas.** An enclosed, lit, empty box with deterministic bounds, one per dimension, persisted in the world save. Resetting is idempotent, so it is safe at the start of every run.
- **Sessions and marks.** A session names and timestamps a run of records. A mark is a bookmark placed before the action under test, which is what lets you tell "the action produced nothing" from "the human never got that far".
- **Self reporting.** What the running build actually supports, read from the live registry rather than from documentation.

On top of that sits observation:

- **Event logging.** Eight categories of generic game event, off by default, each narrowable to an arena or a radius.
- **Inspection.** Player, entity, block, inventory and raw NBT reported as structured records.

Everything writes single line, key value records into `latest.log`, alongside Forge output and the output of the mod under test.

**That ordering is deliberate.** The event categories observe the game's exterior. Most of what you need to establish about your own mod is a decision your mod made, and only your mod can record that. The categories corroborate; they are not the oracle. A long generic log is easy to mistake for discriminating evidence, and a test designed around the facts this toolkit happens to record will produce plenty of output and settle nothing.

## How this differs from what you already have

Two parts of this toolkit overlap things that already exist. The honest comparison matters more than a feature list.

### Bundles compared with vanilla functions

Minecraft 1.12 has functions, and they are also ordered lists of commands. Bundles are not a capability vanilla lacks. The differences are specific:

- Bundles live in `config/devtool/bundles/`, outside any world save, so they survive making a fresh test world. A function lives in a datapack inside the save and has to be recreated for every new world, which is exactly the repetitive work this toolkit exists to remove.
- Bundles support per command tick delays. Functions cannot express a delay.
- Bundles support stop on failure. Functions run every line regardless.
- Bundles emit `BUNDLE_START` and `BUNDLE_END` into the log, so the events a setup routine caused can be separated from the events of the test that followed.

If none of those matter to you, use functions.

### Inspection and entity listing compared with TellMe

TellMe covers comparable ground and does it well for a human audience. This toolkit is not filling a gap. The differences are:

- Output goes to `latest.log` alongside Forge and target mod output, rather than to separate timestamped dump files. One timeline instead of several files to correlate.
- Targeting is by deterministic selector rather than interactive right click, so an agent can ask a human to run an exact command instead of describing what to point at.
- The format is stable single line key value records intended for parsing, rather than formatted tables intended for reading.

If you are a human reading output yourself, TellMe is likely the better tool.

### Portals, gamerules, and other setup

There is no portal command, and there is no world defaults feature, because neither needs one. A lit nether portal is an obsidian frame and one fire block, which is six lines in a bundle. Gamerules are gamerules. The shipped example bundles include both, partly as conveniences and partly to show that reaching for a new command is usually the wrong instinct here.

## Requirements

- Minecraft 1.12.2
- Minecraft Forge 14.23.5.2847 or compatible

Required on the server. Installation on the client is optional, and a client without it can still connect.

## Getting started

Drop the jar in `mods/`, start the game, and run:

```
/devtool help
```

Example bundles are written to `config/devtool/bundles/` on first run. They are seeded once and never overwritten, so edit them freely.

For the agent facing guide, including how to avoid flooding your own log, see [AGENTS.md](AGENTS.md).

## Documentation

The repository is the authoritative documentation.

- [AGENTS.md](AGENTS.md), the guide for an AI agent using the toolkit
- [docs/commands.md](docs/commands.md), the command reference
- [docs/bundles.md](docs/bundles.md), the bundle file format
- [docs/logging.md](docs/logging.md), record formats and categories

## Licence

See [LICENSE](LICENSE).
