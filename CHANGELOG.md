# Changelog

## 1.5.0

The theme is a test that starts at the title screen and ends with the game closed, while the person at the keyboard only launches the game. The items came from the developer's own test sessions with an AI assistant.

### Added

- **`devtool client quit`** closes the game of whoever ran it, from chat, a bundle or the inbox, after the run or request that asked has its answer. In single player the world is saved first; on a server the player leaves as from the pause menu. It needs the toolkit 1.5.0 on the client and `client.allowActions`, as the other client actions do. The server refuses at once, with a key a bundle can tolerate, a client without the toolkit 1.5.0 (`devtool.client.noClient`), one that does not allow it (`devtool.client.notAllowed`) and one still busy with another client action (`devtool.client.busy`).

- **A client request channel.** At the title screen no server runs, so nothing can reach the game through the inbox. With `client.requestChannel` true in the client's own configuration, the client reads small request files from `config/devtool/client-inbox/` and takes one of four menu steps: `create <name> [option=value ...]` makes a single-player world with the settings of the Create New World screen (seed, type `default`, `flat`, `largeBiomes` or `amplified`, a flat layer string, structures, cheats, game mode, bonus chest; creative with cheats on by default) and opens it; `open <save folder>` opens a save; `connect <host[:port]>` joins a server on this computer or the local network only; `quit` closes the game. A create never replaces an existing save. Every value is checked before anything happens, and a request is refused with a reason (`exists`, `notFound`, `notLocal`, `inWorld`, `busy`, `badArguments` with a detail, and others), each with its own result file. Requests left from an earlier launch are refused `placedBeforeStart`. `status.json` says when the channel listens. Off by default; with it on, it listens from game start with no click.

- **The inbox opens by itself at your own join.** With `inbox.openOnLocalJoin` true as well as `inbox.enabled`, the command inbox opens without `devtool inbox open` when you join from your own computer: as the single-player host with cheats on, or as an operator on a development server whose folder your client shares and which you join through `localhost`. The server proves the second case with a secret file, `config/devtool/local-join.secret`, written at its start and removed at its stop, which only a client reading that folder can send back. A join from anywhere else opens nothing. Off by default.

- **One new record type**, `MENU_STEP`, for 46 in total.

### Compatibility

- **Forge's startup questions.** A yes/no question while a world starts, such as one about missing blocks or items, holds the game until someone clicks, and a menu step gets no answer meanwhile. Launch the game with the JVM argument `-Dfml.queryResult=cancel` to answer every such question with cancel at once: the world does not open, and the step fails `loadAborted`. A "missing mods" notice still waits for a click.

## 1.4.0

The theme is a test session that runs without anyone at the keyboard between its steps. Most items came from the developer's own test sessions with an AI assistant.

### Added

- **Client actions.** `devtool client reconnect [awaySeconds]`, `devtool client respawn` and `devtool client close` ask your own client to leave and come back to the same world or server, to press Respawn on the death screen, or to close the open screen. They always act on the client of whoever ran them, from chat, a bundle or the inbox, and never on another player's. Off unless the client allows it: `client.allowActions` in the client's own configuration, false by default. The server refuses at once, with a key a bundle can tolerate, what it already knows: `devtool.client.noClient` for a client without the toolkit 1.4.0, `devtool.client.notAllowed`, `devtool.client.notDead` and `devtool.client.busy`. The client refuses, with a reason, what only it can know, such as closing the death screen or reconnecting a world open to LAN. Every step writes `CLIENT_ACTION` records on both sides.

- **The inbox waits for a reconnect.** When the inbox operator's own `client reconnect` takes their client away, the inbox is suspended instead of closed, and resumes when that same client is back, so requests written meanwhile run after the return without anyone typing `inbox open` again. Any other way back, or the deadline `inbox.rejoinWindowSeconds` (120 seconds by default), closes it. `status.json` says when it is suspended.

- **`devtool fixture release <handle|all>`** takes a fixture's tag off and leaves the entity in the world.

- **`inspect player` names the open container**, as `openContainer`.

- **An icon** in the mod list.

- **One new record type**, `CLIENT_ACTION`, for 45 in total.

### Changed

- **Fixture ownership is the tag.** A fixture belongs to the session whose tag it carries, so a new session of the same name adopts its fixtures again after a `session stop`, and `fixture list` reports a fixture as `removing`, `untagged` or `duplicate` when that is what it is. A handle can no longer contain a dot, and a dot in a session name becomes `_` in the tag. A 1.3.0 tag with a dot in its handle or session belongs to no session in 1.4.0.

- **`fixture clear` removes without a death.** No loot, no experience, no death events. Its `FIXTURE outcome=removed` record is written a tick later, once the removal is confirmed, with `confirmedTick`.

- **`assert entity ... absent` counts a dying entity as absent**, with `dead=true` on the record.

- **Damage records come first.** A target's `ENTITY_DAMAGE` is now written before its `ENTITY_DEATH`, and before a `LOG_CONFIG` that changes the logging. In 1.3.0 a hit's record was written at the end of its tick, so the last hit of a kill appeared after the death, and a hit just before a logging change appeared after that change.

## 1.3.0

Most items in this release came from AI assistants that used 1.2.0 to test other mods, and from the developer's own test sessions. The theme is letting an assistant run a test session itself, from start to finish, while the person at the keyboard only opens the door.

### Added

- **A command inbox.** `devtool inbox open`, typed by a person once each time a world opens, lets an assistant write small request files into `config/devtool/inbox/` and read one result file per request: `run <bundle>`, `reload`, `save`, `mark`, the read-only `inspect`, `entities` and `nbt` commands, and `screenshot`. Requests run as the player who opened it, with that player's permissions. Nothing else is accepted, and no vanilla command. Off by default: the configuration key `inbox.enabled` must be true as well, and a bundle can never open it. It closes when that player leaves or the server stops, and `status.json` is present exactly while it is open. Every claim, refusal and result writes an `INBOX` record.

- **Who started a run.** `BUNDLE_START` now says who started the run and through which channel: chat, console, command block, join, inbox, or another bundle.

- **`devtool save`.** Saves player data and every loaded world, in single player and on a dedicated server, and leaves a `save-off` typed earlier in place. Writes `SAVE` with the dimensions saved. A failure fails with `devtool.save.failed`.

- **Screenshots on request.** `devtool screenshot <label> [delayTicks] [player]` asks a player's client to save a labelled screenshot after a delay, named after the label and the tick it was asked for. The client needs the toolkit installed; a player without it fails with `devtool.screenshot.noClient`, and can still join.

- **Attributes.** `devtool inspect attributes` lists a living entity's attributes with every modifier. `devtool assert attribute <target> <attribute> value <n>` checks the final value, and `... modifier <uuid|name> [amount <n>]` one modifier. A name matching several modifiers fails with `devtool.assert.ambiguous`.

- **Optional Baubles support.** With Baubles installed, `inspect inventory` includes the bauble slots with their slot type, and `devtool baubles set` and `clear` fill and empty them through Baubles' own handler, calling the bauble's equip and unequip hooks. Without Baubles, `devtool baubles` fails with `devtool.baubles.absent` and nothing else changes. Baubles is never required.

- **A session belongs to one world.** Every world start writes a `WORLD` record with the save folder and seed. Opening a different save ends the active session first, with `SESSION_STOP reason=worldChanged`.

- **Reload at every world start.** Bundles and configuration are read again whenever a world starts, writing `RELOAD trigger=serverStart`, so a file edited at the title screen is used without typing `devtool reload`. `devtool reload` now writes `RELOAD trigger=command` too.

- **Quieter, steadier test clients.** The opt-in client defaults can now also set master, weather, hostile, friendly and ambient volume, each left alone unless given a value, and `client.noPauseOnLostFocus` keeps the game running when its window loses focus. All only when `client.applyOnJoin` is on.

- **Nine new record types**, for 44 in total: `TOLERATED`, `RELOAD`, `INBOX`, `SAVE`, `WORLD`, `ATTRIBUTE`, `ATTRIBUTE_MODIFIER`, `SCREENSHOT`, `BAUBLES`.

### Changed

- **Inspection never loads a chunk.** `inspect block`, `inspect container` and `nbt block` refuse a position whose chunk is not loaded, with `devtool.inspect.notLoaded`, instead of making the game load or generate it.

- **Inspecting a container never rolls its loot.** A chest whose loot table has not rolled yet is reported as `lootRolled=false` with the table name, and stays unrolled.

- **An unknown subcommand names the closest real one** in the chat reply: `devtool logging` answers "Did you mean log?".

### Fixed

- **A tolerated failure now leaves a record.** 1.1.0 promised that a tolerated step would carry a note saying so; that note was never written. Each tolerated step now writes a `TOLERATED` record naming the key, and `BUNDLE_END` counts them as `tolerated`.

### Documentation

- `AGENTS.md` gains a worked example of a whole test run through the inbox, the full inbox reference, and every new command, record and failure key. A build check now fails if a record type or a `devtool.` failure key is missing from it.
- Every subcommand needs permission level 2, typed or inside a bundle.
- Arena commands roll and drop the loot of a loot chest they replace.
- On a dedicated server the bundle load records appear twice at start, once for the load and once for the start-up reload.

### Notes

- Bundles written for 1.2.0 work unchanged, except one that inspects a position whose chunk is not loaded: it now fails with `devtool.inspect.notLoaded` instead of loading the chunk. No command was removed.
- The client defaults and screenshots are the only parts that act on a client. Everything else still needs the toolkit on the server only.

## 1.2.0

Every item in this release came from one report written by an AI assistant that used 1.1.0 to test another mod. Ten items, ten answers.

### Added

- **A bundle can wait for something to be true.** `devtool await chunks x1 z1 x2 z2 [timeoutTicks]` parks the bundle until every chunk touching that rectangle is loaded, then continues. It never loads a chunk. A timeout fails the command with `devtool.wait.timeout`, a dimension change with `devtool.wait.cancelled`, and both honour `stopOnFailure` and `toleratedFailures`. Every wait writes an `AWAIT` record with how long it waited and, on a timeout, the first chunk that was not there.

- **A checkpoint for the person at the keyboard.** `devtool checkpoint arm <label> [timeoutTicks]` shows the label on screen and parks the bundle until they type `devtool checkpoint ack`, recording the tick asked, the tick answered and the latency. `devtool checkpoint cue <label> [chat|actionbar|title] [sound]` shows a label without pausing. Records between `armedTick` and `ackTick` are the ones that happened while the person acted.

- **An operator, and placeholders that name them.** `devtool session operator @s` binds one online player to the session with an `OPERATOR` record; disconnect and session stop unbind with a record. Bundle commands may then say `${operator.name}`, `${operator.uuid}` and `${operator.selector}`, filled in when the command runs. An unbound value fails the command with `devtool.placeholder.unbound`; a value that could break the command, with `devtool.placeholder.unsafe`.

- **Assertions.** `devtool assert entities count`, `assert entity <uuid> present|absent|dimension|within`, and `assert player <name> dimension|gamemode` write an `ASSERT` record with `predicate`, `expected`, `observed` and `outcome`. A mismatch fails the command with `devtool.assert.failed`; an absence that cannot be judged because the place is not loaded is `outcome=unloaded` with `devtool.assert.unloaded`, never a pass. A verdict says only that an observation matched or did not match a declared value. It never says the mod is right.

- **A result file per run.** `"result": true` on a bundle writes `results/<bundle>-<startTick>.json` beside the configuration: assertions, failures, markers, checkpoints and waits, copied from the log records, plus the bundle's identity, hash, session, operator and counters. Nothing is in the file that is not in the log.

- **Fixtures.** `devtool fixture claim <handle> <selector|uuid|entityId>` tags exactly one non-player entity as the session's own, `fixture list` says whether each is `loaded`, `unloaded` or `absent`, and `fixture clear <handle|all>` removes only entities that still carry the tag, logging every UUID. `${fixture.<handle>.uuid}` names a claimed entity in any later command. Ownership is the tag, never the name.

- **Scenario checkpoints across a restart.** `devtool scenario checkpoint <id> next <bundle> [clean|fatal]` stores, inside the world save, which bundle must run next, with its hash, the session, its operator and its fixtures. After the restart a `SCENARIO` record at world load reports it, running anything else is refused with `devtool.scenario.mismatch`, and running the expected bundle restores the session, the operator and the fixtures first. `complete` and `abandon` clear it. Unreadable stored data is reported as `corrupt` and refuses nothing.

- **Provenance.** Every load and reload writes a `BUNDLE_SOURCE` record per file with its SHA-256, and one per bundle with a normalized hash of what the bundle would do. `BUNDLE_START` and `BUNDLE_END` carry that hash, so a log line proves which text a run came from.

- **Trace records.** `"trace": true` on a bundle writes a `BUNDLE_STEP` before each command with the tick it became due and the tick it ran on, for when a delay is not doing what you expected.

- **Direct entity lookup.** `devtool nbt entity` accepts a UUID or an entity ID as well as a selector, finds the entity in any loaded dimension without the selector engine, and records which dimension and which lookup form. A miss lists the dimensions that were searched. A selector that matches nothing now writes an `ERROR` record with what the engine had to work with: `selectorPermission`, `dimensionCount`, `dimensionIds`, `senderDimension` and `liveEntities`.

### Changed

- **The marker chat reply names the label.** `devtool mark PRESS_H_NOW` answers `Marker written to log: PRESS_H_NOW`, so a person can tell which marker fired. The `MARK` record is unchanged.

- **An unknown `${...}` in a bundle command refuses the file at reload.** In earlier versions the text reached the game unchanged. Only the placeholder forms above are accepted; anything else inside `${` and `}` is a load error naming the file, the bundle and the placeholder. A bundle that relied on passing such text through literally must be rewritten.

- **Long checkpoint labels fit on screen.** A label over twelve characters is drawn on the subtitle line under a short heading rather than at title size.

- **Eight new record types**, for 35 in total: `BUNDLE_STEP`, `BUNDLE_SOURCE`, `AWAIT`, `CHECKPOINT`, `OPERATOR`, `ASSERT`, `FIXTURE`, `SCENARIO`. No existing record changed shape, except that `BUNDLE_START` and `BUNDLE_END` gained `hash`, and `NBT` for an entity gained `lookup` and `dimension`.

### Documentation

- `delayTicks` is now described as "wait, then run this command", with the pattern that puts the delay on the command that must follow the wait. A reader had reasonably taken it the other way round.
- Five new worked examples in `AGENTS.md`: a wait, a checkpoint, an assertion with a result file, a fixture claim and clear, and a scenario across a restart.
- The public copy states what a verdict means.

### Notes

- A report that a selector matched nothing on a dedicated server, right after `entities nearby` had listed the entity, could not be reproduced on 1.1.0 or on this build. The zero-match record now carries what the selector engine saw, so the next such report can be read from the log.
- Bundles written for 1.1.0 work unchanged unless they contain `${` text, see above. The command surface only grew; no command changed meaning.

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
