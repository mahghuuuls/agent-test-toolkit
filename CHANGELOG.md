# Changelog

## 1.1.0

### Added

- **A bundle command can declare failures it expects.** An idempotent reset such as `effect @s clear` fails when there is nothing to clear, and that halted the whole bundle. Declare the failure on that command and it is tolerated:

  ```json
  { "command": "effect @s clear", "toleratedFailures": ["commands.effect.failure.notActive.all"] }
  ```

  It tolerates **that key only**. A typo in the same command still fails the bundle, which turning off `stopOnFailure` would not have given you. The key is the `detail` field of the `ERROR` record, and a tolerated failure is recorded as a success carrying a note that names it, so it never reads as a command that simply worked.

- **`devtool inspect container <x> <y> <z>`** reports a container's occupied slots as one record each, with slot index, item, count, metadata and whether item NBT exists. Works with vanilla containers and modded blocks that expose Forge's item handler. Output is bounded, and truncation is reported on every record rather than silently.

### Fixed

- No more `Missing English translation for agenttesttoolkit` on startup. The mod has no translated strings, but a tool whose purpose is a clean log should not add noise about itself.

### Documentation

- **Block inspection does not report container contents, and now says so.** `inspect block` answers what a block is. For what is inside it, use `nbt block`, which has always written the full tile entity NBT including every slot. Neither command pointed at the other, and a project using the toolkit built a workaround for a problem that did not exist.

  Related: a container whose loot table has not yet rolled writes a `LootTable` reference instead of items, so `nbt block` distinguishes "loot not generated yet" from the generated contents.

- **Running a client and a dedicated server from one directory can corrupt the shared log.** The combined file can contain NUL bytes, and standard search tools then report no matches rather than saying the file is unreadable. Documented along with the practice that works: binary-tolerant reading, retaining both the current and rotated logs, and separate directories where the build allows.

### Notes

- No change to existing behaviour. Bundles written for 1.0.1 work unchanged, and a command without `toleratedFailures` behaves exactly as before.

## 1.0.1

### Fixed

- **A bundle command that leaves out its player argument now works.** `gamemode creative` inside a bundle failed in 1.0.0 with "You must specify which player you wish to perform this action on", even when a player ran the bundle. The same fault affected `clear`, `kill`, `scoreboard`, `setworldspawn`, `spawnpoint`, `tp` and `xp` whenever their player argument was omitted. Passing an explicit selector such as `@p` was the workaround and still works.

  The cause was that bundle commands were run through a stand-in sender rather than your own, so vanilla commands that check who is running them saw the wrong thing. Bundle commands now run as the caller's own sender, which is what the documentation always claimed. Reported by a project using the toolkit during its own development.

### Changed

- Failure classification reads the error the game raises rather than the message it prints. Behaviour is unchanged: a command that runs and affects nothing still counts as success, and a raised error still counts as failure. The mechanism no longer depends on rendered text.
- The repository documentation now explains where to download a release and how to check its SHA-256. A version tag on its own is not a release, and 1.0.0's jar is published retroactively for anyone who needs that version.

### Notes

- Commands the caller could not type themselves still fail exactly as they would if typed. The correction restored who a command runs as; it grants nothing. A bundle run from the server console is still refused for commands that need a player.
- No change to the bundle file format, the command surface, the record vocabulary, or configuration. Bundles written for 1.0.0 work unchanged.

## 1.0.0

First release.

### Added

- **Command bundles.** Ordered lists of commands in JSON, run with one command. Per command tick delays, stop on first failure, and nesting with cycle detection. Bundles live in the config folder rather than a world save, so they survive making a fresh test world, and they mark their own start and end in the log. A command that runs and changes nothing counts as a success, so teardown bundles stay re-runnable.
- **Test arenas.** An enclosed, lit, empty box with deterministic bounds, one per dimension, stored in the world save. Reset repairs the structure and clears the interior, and is safe to run repeatedly. Creating an arena moves your respawn point into it.
- **Event logging.** Eight categories of generic game event, all disabled by default: block placement and breaking, entity spawn, death and damage, player and entity interaction, and item use. Each can be narrowed to an arena or a radius. Every change to what is being recorded is itself written to the log, so an absent event can be told apart from a category that was never on.
- **Inspection.** Player, entity, block, inventory, nearby entity listing, and raw NBT, reported as structured records. NBT output is bounded and truncation is always reported.
- **Sessions and markers.** A session names and timestamps a run of records. A marker is a labelled bookmark placed before the action being tested.
- **Self reporting.** Environment, loaded mod list, and a capability report read from the running build rather than from documentation.
- **Join automation.** Optionally run a bundle when an operator joins. Disabled by default, and the bundle name is empty by default.
- **Client defaults.** Optionally set brightness and music volume on joining a world. Disabled by default.
- **Example bundles.** Written to the config folder on first run only, never overwritten afterwards. Includes a lit nether portal and a ready end portal, both of which are ordinary commands rather than mod features.

### Notes

- Requires Forge for Minecraft 1.12.2. Everything it records happens on the server, so that is where it installs. In singleplayer that just means installing it.
- Arena creation and reset are destructive, immediate, and cannot be undone. Use a disposable world.
- Everything that could change your world or your settings without being asked is off by default.
