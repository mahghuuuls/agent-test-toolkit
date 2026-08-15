# Agent Test Toolkit

A development tool for Minecraft mod authors. **This is not a gameplay mod.** It adds no items, no blocks, no recipes, and nothing to find in a world.

It is for someone developing a Minecraft mod. It works on its own, as a way to set up a test scenario without typing the same eight commands every time. But its main purpose is to help an AI coding assistant stop relying on you to describe what happened on screen, by writing what the game actually did into `latest.log` in a form a program can read.

You still play. The toolkit sets the world up, watches, and writes it down.

## Where to read what

| If you are | Read |
| --- | --- |
| An AI assistant using the toolkit | [AGENTS.md](AGENTS.md) |
| Looking for a command | [docs/commands.md](docs/commands.md) |
| Writing a bundle file | [docs/bundles.md](docs/bundles.md) |
| Parsing the log | [docs/logging.md](docs/logging.md) |
| Deciding whether to install it | [MOD-PAGE.md](MOD-PAGE.md) |

`AGENTS.md` is the one that matters most, and it is deliberately self-contained: an AI assistant pointed at that file alone has the whole command surface, the bundle format, all 27 record types, and three worked examples of testing a freshly implemented feature. The `docs/` pages are the same material written for a person.

It also covers the traps that waste a test run: why an absent record is ambiguous, why `entity_spawn` floods, and why `latest.log` may not be the file you think it is.

## Features

### Test arenas

```
/devtool arena create 20 10 20
```

A sealed, lit, empty box, centred on you, floor level with your feet. The numbers are the **interior**, so you get twenty blocks of usable space and the walls sit outside that.

Lighting is embedded in the floor rather than the ceiling, so it works at any height. Nothing spawns inside.

One arena per dimension, stored in the world save, so it survives a restart. Creating one moves your respawn point inside it.

```
/devtool arena reset
```

Rebuilds the shell, clears every non-player entity and dropped item, and returns you to the start position. **Idempotent**: running it twice leaves the same state as running it once, which is what makes it safe at the top of every test.

### Command bundles

Named lists of commands in JSON, under `config/devtool/bundles/`, scanned recursively.

```json
{
  "damage_setup": {
    "description": "Prepare one target for manual damage testing",
    "commands": [
      "devtool arena reset",
      "gamerule doMobSpawning false",
      "summon minecraft:zombie ~3 ~ ~ {CustomName:\"target\",NoAI:1}",
      "devtool log entity_damage on arena",
      { "command": "devtool mark READY", "delayTicks": 20 }
    ]
  }
}
```

- **Per command tick delays**, measured from the previous command finishing, so inserting a command shifts what follows rather than compressing the gaps.
- **Stop on failure**, on by default. A failure means the game raised a command error; a command that runs and changes nothing has succeeded, so neither a `kill` matching nothing nor a `clear` on an empty inventory halts a teardown on its second run.
- **Nesting.** A bundle can run another. The parent waits, and the child's failure counts as one failed command. Cycles are refused by name before anything runs.
- **`BUNDLE_START` and `BUNDLE_END`** in the log, so events your setup caused can be told apart from events of the test that followed.

They live outside the world save, so they survive making a fresh test world. They are deliberately **not** a scripting language: no variables, no conditionals, no loops.

### Event logging

Eight categories, all off by default:

`block_place`, `block_break`, `entity_spawn`, `entity_death`, `entity_damage`, `player_interaction`, `entity_interaction`, `item_use`

```
/devtool log entity_damage on arena
/devtool log block_place on radius 32
/devtool log status
```

Each can be narrowed to the dimension's arena or to a radius anchored where you stood when you applied it. One filter per category.

An excluded event and an event that never happened look identical in the log. So every change to what is being recorded writes its own `LOG_CONFIG` record, as does `log status`, and each one carries the full enabled set. Any single line tells you what was being watched at that moment, without needing chat or the command history.

### Inspection

```
/devtool inspect player
/devtool inspect entity @e[name=target]
/devtool inspect block ~ ~-1 ~
/devtool inspect container 10 64 -3
/devtool inspect inventory
/devtool entities nearby 20
/devtool nbt held
```

Structured records rather than chat output. A selector matching more than one entity is an error rather than an invitation to pick one, so name your fixtures and select on the name.

`inspect block` answers what a block **is**: id, metadata, blockstate, tile entity class. `inspect container` answers what is **in** it, one record per occupied slot. For the raw tile entity tag, including a loot table that has not yet rolled, use `nbt block`.

NBT goes to the log, bounded by a configurable limit, and truncation is always reported along with the original length.

### Sessions and marks

```
/devtool session start damage_test
/devtool mark ABOUT_TO_HIT
```

A session stamps every record with its name and a tick counter from its start. A mark is a labelled bookmark carrying no verdict.

Put a mark before each step you ask a human to perform. Then a missing record means something: you can tell "the action produced nothing" from "they never got that far".

### Self reporting

```
/devtool capabilities
/devtool environment
/devtool mods
```

`capabilities` reads the live command registry and the logging category enum, so it reports what the running build supports rather than what a document claims. It cannot drift from the jar.

## Configuration

`config/devtool/devtool.cfg`. Arena defaults and construction block, the NBT truncation limit, whether a bundle runs when an operator joins, whether spawn logging includes dropped items, and optional client brightness and music defaults.

Everything that could change your world or your settings without being asked is off until you turn it on.

## Warning: use a disposable world

**This mod modifies the world irreversibly and without confirmation.**

`arena create` replaces every block in its volume. `arena reset` and `arena clear` delete every non-player entity inside the bounds, dropped items included. Nothing prompts, and nothing can be undone.

That is deliberate. A confirmation prompt would make these commands unusable from a bundle, and resetting the arena is the most common first step of a setup routine. The size limit on creation is the only guard.

Commands can also run from a command block, so a redstone circuit can trigger them with nobody watching.

Use a world you are willing to lose.

## How this differs from what you already have

Two parts of this overlap things that exist. The honest comparison matters more than a feature list.

**Bundles against vanilla functions.** Minecraft 1.12 has functions, which are also ordered command lists. Bundles are not a capability vanilla lacks. They differ in that they live outside the world save and survive a fresh test world, support per command delays and stop on failure, and mark their own boundaries in the log. If none of that matters to you, use functions.

**Inspection against TellMe.** TellMe covers comparable ground and does it well for a human audience. This is not filling a gap. Output lands in `latest.log` next to Forge and your own mod's output rather than in separate dump files, targeting is by deterministic selector rather than interactive right click, and the format is meant for parsing rather than reading. If you are reading it yourself, TellMe is likely the better tool.

## Installing

Two places carry released builds:

- **CurseForge.** The project page is titled **Agent Diagnostics Toolkit**, not Agent Test Toolkit. That platform reserves `Test` as a release-stage word, so the page had to be named differently. Same mod, same `agenttesttoolkit` mod id, same jar.
- **GitHub Releases**, on this repository, with the jar attached to the tag.

Each release records the SHA-256 of its jar in the release notes. To check what you downloaded:

```bash
sha256sum agenttesttoolkit-<version>.jar
```

```powershell
Get-FileHash agenttesttoolkit-<version>.jar -Algorithm SHA256
```

A version tag on its own is **not** a release. If you find a tag with no attached artifact, the jar for that version was never published, and building from source is the only way to obtain it. That is worth avoiding: an early consumer of this toolkit had to resolve the tag, build it, run its tests, inspect the jar and hash it themselves, purely to get a runtime that should have been a download.

Building from source needs a JDK and produces `build/libs/agenttesttoolkit-<version>.jar`:

```bash
./gradlew clean build
```

The build is reproducible from a given commit. Building the same revision twice produces a byte-identical jar, so a checksum published against a revision can be re-derived rather than taken on trust.

## Requirements

Minecraft 1.12.2 with Forge. Everything the toolkit records happens on the server, so that is where it needs to be installed. In singleplayer that means installing it normally. The optional brightness and music defaults are the only part that acts on a client, and they need it installed there too.

Commands need permission level 2. Start with `/devtool help`.

Example bundles are written to `config/devtool/bundles/` on first run and never overwritten, so edit them freely.

## Licence

See [LICENSE](LICENSE).
