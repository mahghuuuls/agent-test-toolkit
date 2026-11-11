# Agent Test Toolkit

A development tool for Minecraft mod authors. **This is not a gameplay mod.** It adds no items, no blocks, no recipes, and nothing to find in a world.

It is for someone developing a Minecraft mod. It works on its own, as a way to set up a test scenario without typing the same eight commands every time. But its main purpose is to help an AI coding assistant stop relying on you to describe what happened on screen, by writing what the game actually did into `latest.log` in a form a program can read.

You still do the parts of a test that need a person: aiming, walking, working in other mods' screens. The toolkit sets the world up, takes small player actions such as a click, a held button or a tooltip, watches, and writes it down.

## Where to read what

| If you are | Read |
| --- | --- |
| An AI assistant using the toolkit | [AGENTS.md](AGENTS.md) |
| Looking for a command | [docs/commands.md](docs/commands.md) |
| Writing a bundle file | [docs/bundles.md](docs/bundles.md) |
| Parsing the log | [docs/logging.md](docs/logging.md) |
| Deciding whether to install it | [MOD-PAGE.md](MOD-PAGE.md) |

`AGENTS.md` is the one that matters most, and it is deliberately self-contained: an AI assistant pointed at that file alone has the whole command surface, the bundle format, all 52 record types, and twelve worked examples of testing a freshly implemented feature, one of them run entirely through the command inbox, one with a reconnect in the middle, and one a whole launch from the title screen to the game closing, with nobody typing. The `docs/` pages are the same material written for a person.

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

- **Per command tick delays**: the toolkit waits, then runs that command. Measured from the previous command finishing, so inserting a command shifts what follows rather than compressing the gaps.
- **Stop on failure**, on by default. A failure means the game raised a command error; a command that runs and changes nothing has succeeded, so neither a `kill` matching nothing nor a `clear` on an empty inventory halts a teardown on its second run.
- **Nesting.** A bundle can run another. The parent waits, and the child's failure counts as one failed command. Cycles are refused by name before anything runs.
- **`BUNDLE_START` and `BUNDLE_END`** in the log, so events your setup caused can be told apart from events of the test that followed. Both carry a hash of the bundle's text, and every load writes `BUNDLE_SOURCE` with the file's SHA-256, so a log line proves which text a run came from. `BUNDLE_START` also says who started the run and through which channel: chat, console, command block, join, inbox, or another bundle.
- **Tolerated failures, on the record.** Declare the failure key a command may end with, such as `commands.fill.failed` on a second run, and the bundle carries on. Each tolerated step writes a `TOLERATED` record, so it never reads as a command that simply worked.
- **Read again at every world start.** Bundles and configuration reload by themselves whenever a world opens, so a file edited at the title screen is used without typing `devtool reload`.
- **Waits.** `devtool await chunks` parks the bundle until a region is loaded, with a timeout that is always finite. Never a guessed delay for something the toolkit can check.
- **Checkpoints.** `devtool checkpoint arm` shows a label to the person and pauses until they type `devtool checkpoint ack`; `cue` shows a label without pausing. The ticks asked and answered are recorded.
- **An operator and placeholders.** Bind one player with `devtool session operator @s`, then write `${operator.name}`, `${operator.uuid}`, `${operator.selector}` or `${fixture.<handle>.uuid}` in any command. Those four are the whole list, and an unknown one refuses the file.
- **Assertions and result files.** `devtool assert` compares a count, a presence, a dimension or a game mode with a value you declare, and writes the outcome; `"result": true` on a bundle also writes one JSON file per run with every assertion, failure, marker, checkpoint and wait, and which run it was: how it started, by whom, from which inbox request, when, and in which world.
- **Fixtures.** `devtool fixture claim` names the one entity you just summoned; `list` says whether it is still there; `clear` removes exactly it, without a death, by a tag the toolkit owns, and nothing else; `release` takes the tag off and leaves it in the world.
- **Scenario checkpoints.** `devtool scenario checkpoint` stores in the world save which bundle must run next, across a stop and restart, and refuses anything else until it has run or you abandon it.

They live outside the world save, so they survive making a fresh test world. They are deliberately **not** a scripting language: no variables, no conditionals, no loops, and the placeholders are lookups rather than expressions.

A verdict from an assertion means only that an observation matched, or did not match, the value you declared. It never says the mod is right or wrong. Deciding that is still your job, and the toolkit's job is to make sure you decide from records rather than from a description.

### Command inbox

```
/devtool inbox open
```

Off by default, and opened by you typing it, once each time you open a world, or, with `B:openOnLocalJoin=true` under `inbox`, by itself when you join from your own computer: as the single-player host with cheats on, or as an operator connecting through `localhost` to a development server that shares your client's folder. While it is open, an AI assistant can start one of its bundles, reload, save, take a screenshot, ask for a client action, or run a read-only inspection by writing a small file into `config/devtool/inbox/`, and reads the answer from a result file next to it. Requests run as you, with your permissions. Nothing else is accepted, and no vanilla command in particular: anything that changes the world has to be in a bundle, where you can read it. It closes when you leave, when the server stops, or when you type `/devtool inbox close`. A reconnect the toolkit started itself only suspends it: requests wait, and run once you are back.

Turn it on with `B:enabled=true` under `inbox` in the configuration, in a development or test instance only.

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
/devtool inspect attributes
/devtool entities nearby 20
/devtool nbt held
```

Structured records rather than chat output. A selector matching more than one entity is an error rather than an invitation to pick one, so name your fixtures and select on the name.

`inspect block` answers what a block **is**: id, metadata, blockstate, tile entity class. `inspect container` answers what is **in** it, one record per occupied slot, and reports a loot table that has not rolled yet without rolling it. For the raw tile entity tag, use `nbt block`.

`inspect attributes` lists a living entity's attributes with every modifier, and `devtool assert attribute` checks a value or one modifier by UUID or name. With Baubles installed, `inspect inventory` includes the bauble slots, and `devtool baubles set` and `clear` fill and empty them. Baubles is optional and never required.

Inspection only observes. A position whose chunk is not loaded is refused rather than loaded or generated.

### Saves and screenshots

```
/devtool save
/devtool screenshot inv_open 200
```

`save` saves player data and every loaded world, in single player too, and leaves a `save-off` in place. `screenshot` asks a player's client to save a labelled screenshot after a delay; that client needs the toolkit installed.

NBT goes to the log, bounded by a configurable limit, and truncation is always reported along with the original length.

### Client actions

```
/devtool client reconnect 3
/devtool client respawn
/devtool client close
/devtool client quit
```

Four steps you used to do by hand: leave the world and come back to the same place, press Respawn on the death screen, close the open screen, close the game. They always act on your own client, never another player's, and only when your client has the toolkit and allows it with `B:allowActions=true` under `client`. Off by default.

### Player actions

```
/devtool client select 3
/devtool client use screenshot=after_use
/devtool client attack
/devtool client hold use 20
/devtool client drop all
/devtool client inventory
/devtool client tooltip 0 shift screenshot=tip
```

From 1.7.0, small player actions on your own client, so a test whose action is a click needs no person: pick a hotbar slot, right click, left click, keep a mouse button down for a time, drop the held item, open the inventory, or show an item's tooltip (with Shift, Ctrl or Alt held for it if asked) and write its lines into the log. A click goes through the game's own key handling, so the game and other mods see it as a click (except mods that read the raw mouse events); what it does is the game's decision. The toolkit never moves or turns you: a bundle aims with vanilla `tp`. Same rules as the client actions above.

### Client request channel

At the title screen no server runs, so nothing above can reach the game. With `B:requestChannel=true` under `client`, your client reads small request files from `config/devtool/client-inbox/` and takes one of four menu steps: create a single-player world with the settings the Create New World screen offers, open a save, connect to a server on your own computer or local network, or quit. A create never replaces an existing save, and a connect never goes anywhere outside your own network. Off by default; with it on, it listens from game start, with no click. Together with the inbox opening at your own join, an AI assistant can run a whole test from the title screen to closing the game while you only launch it.

### Sessions and marks

```
/devtool session start damage_test
/devtool mark ABOUT_TO_HIT
```

A session stamps every record with its name and a tick counter from its start. A mark is a labelled bookmark carrying no verdict. A session belongs to one save: every world start writes a `WORLD` record, and opening a different save ends the session first.

Put a mark before each step you ask a human to perform. Then a missing record means something: you can tell "the action produced nothing" from "they never got that far".

### Self reporting

```
/devtool capabilities
/devtool environment
/devtool mods
```

`capabilities` reads the live command registry and the logging category enum, so it reports what the running build supports rather than what a document claims. It cannot drift from the jar.

## Configuration

`config/devtool/devtool.cfg`. Arena defaults and construction block, the NBT truncation limit, whether a bundle runs when an operator joins, whether spawn logging includes dropped items, whether the command inbox may be opened, whether it opens by itself at your own join, and how long it waits for you across a reconnect, whether your client allows client actions, whether it reads menu-step requests, and optional client defaults: brightness, music, master, weather, hostile, friendly and ambient volume, and keeping the game running when its window loses focus.

Everything that could change your world or your settings without being asked is off until you turn it on.

## Warning: use a disposable world

**This mod modifies the world irreversibly and without confirmation.**

`arena create` replaces every block in its volume. `arena reset` and `arena clear` delete every non-player entity inside the bounds, dropped items included. A loot chest inside the volume rolls its loot and drops it when it is replaced. Nothing prompts, and nothing can be undone.

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

Minecraft 1.12.2 with Forge. Everything the toolkit records happens on the server, so that is where it needs to be installed. In singleplayer that means installing it normally. The optional client defaults, screenshots, client actions, the client request channel and the inbox opening at a local join to a dedicated server are the only parts that need it on a client, and they need it installed there too. A client without the toolkit can still join a server that has it.

Baubles is optional. When it is installed, the toolkit loads after it and adds bauble slots to inventory inspection; without it, nothing else changes.

Every subcommand needs permission level 2, typed or inside a bundle. Start with `/devtool help`.

Example bundles are written to `config/devtool/bundles/` on first run and never overwritten, so edit them freely.

## Licence

See [LICENSE](LICENSE).
