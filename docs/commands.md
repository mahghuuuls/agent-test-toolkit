# Command reference

The root command is `/devtool`, with `/att` as an alias.

The alias is a fallback, not a guarantee. Minecraft resolves duplicate command names by last registration wins, and an alias never displaces another command's own name. So a mod claiming `devtool` leaves `/att` working. But a mod whose own command **is** `att` takes the alias, and nothing reports that this happened.

Every subcommand requires permission level 2, whether typed or run from a bundle. A bundle runs as its caller, so a caller below level 2 cannot run any of them from a bundle either.

An unknown subcommand fails and, in the chat reply, names the closest real one when there is one: `/devtool logging` answers "Did you mean log?". The `ERROR` record in the log carries only the typed name. A typed word shorter than three letters gets no suggestion. Otherwise the suggestion is a subcommand one edit away; failing that, a subcommand of at least three letters that the typed word starts with, or that starts with the typed word; failing that, a subcommand two edits away. Nothing runs.

## Diagnostics

| Command | Effect |
| --- | --- |
| `devtool help` | List subcommands |
| `devtool capabilities` | Report this build's version, commands, inspection types and logging categories, read from the live registry |
| `devtool environment` | Minecraft and Forge versions, server context, dimension, difficulty, position |
| `devtool mods` | Every loaded mod id and version, one record each |

`capabilities` is derived from the running build, never from a written list, so it cannot claim a feature the jar does not have.

## Logging

| Command | Effect |
| --- | --- |
| `devtool log <category> on` | Enable a category |
| `devtool log <category> on arena` | Enable, restricted to this dimension's arena |
| `devtool log <category> on radius <n>` | Enable, restricted to within n blocks of where you stand |
| `devtool log <category> off` | Disable a category |
| `devtool log all off` | Disable everything and clear all filters |
| `devtool log status` | Every enabled category and its filter |

See [logging.md](logging.md) for the categories and the record format.

## Sessions and marks

| Command | Effect |
| --- | --- |
| `devtool session start <name>` | Start a session |
| `devtool session stop` | Stop it |
| `devtool session status` | Report the active session and its operator, if bound |
| `devtool session operator <player>` | Bind exactly one online player as the session's operator, by name or selector. Zero or several matches are refused. The binding ends when the session stops or the player disconnects, and every change writes an `OPERATOR` record |
| `devtool mark <label>` | Write a labelled marker record. The chat reply repeats the label, so an operator can tell which marker fired |

A session belongs to one world save. Leaving to the title screen and reopening the same save keeps it. Opening a different save stops it first, with `SESSION_STOP reason=worldChanged`, and ends its operator binding and fixtures. See [logging.md](logging.md#worlds).

## Bundles

| Command | Effect |
| --- | --- |
| `devtool bundle list` | Loaded bundles and their command counts |
| `devtool bundle show <name>` | One bundle's commands, in order, with delays |
| `devtool run <name>` | Run a bundle |
| `devtool await chunks <x1> <z1> <x2> <z2> [timeoutTicks]` | Inside a bundle: pause it until every chunk touching that block rectangle, in the caller's current dimension, is loaded, or the timeout passes. Coordinates may be `~`-relative. Default timeout 200 ticks, maximum 12000, region capped at 1024 chunks. A timeout fails the command with `devtool.wait.timeout`; a dimension change cancels it with `devtool.wait.cancelled`. Never loads a chunk. Typed outside a bundle it checks once and reports |
| `devtool checkpoint arm <label> [timeoutTicks]` | Show the label to the operator as a title (labels over 12 characters go on the subtitle line, so they fit) and, inside a bundle, pause until they type `devtool checkpoint ack` or the timeout passes (default 1200 ticks). A timeout fails with `devtool.wait.timeout`. Only one checkpoint may be armed at a time |
| `devtool checkpoint cue <label> [chat|actionbar|title] [sound]` | Show the label prominently, action bar by default, with a sound if asked. Does not pause. Records the cue tick |
| `devtool checkpoint ack` | Record that the operator is ready or has acted, with the latency since they were last asked. Records only; never performs any action |
| `devtool assert entities count <n> within <r> [type <id>] [name <name>]` | Exact count of entities around the caller. Writes an `ASSERT` record with `expected`, `observed` and `outcome`; a mismatch fails the command with `devtool.assert.failed` |
| `devtool assert entity <uuid> present\|absent [at <x> <y> <z>]` | Whether a loaded entity has that UUID. Absent is only a verdict when the place it should be is loaded: give `at`, or use a claimed fixture, otherwise the outcome is `unloaded` with `devtool.assert.unloaded` |
| `devtool assert entity <uuid> dimension <d>` and `within <r> of <x> <y> <z>` | Where a loaded entity is |
| `devtool assert player <name> dimension <d>` and `gamemode <mode>` | Where a player is and how they play |
| `devtool assert attribute <target> <attribute> value <n>` | An attribute's final value, compared at two decimals. `<target>` is a selector or UUID matching exactly one living entity; `<attribute>` is the registry name, such as `generic.armor` |
| `devtool assert attribute <target> <attribute> modifier <uuid\|name> [amount <n>]` | Whether the attribute has that modifier, and with `amount` its amount. A name may hold spaces: every word up to `amount` is the name. A name that matches several modifiers fails with `devtool.assert.ambiguous` rather than choosing one. Vanilla gives all four armour pieces the name `Armor modifier`, so assert those by UUID. A target that matches nothing, several entities, or a non-living entity fails before any comparison and writes no `ASSERT` record |
| `devtool fixture claim <handle> <selector\|uuid\|entityId>` | Mark exactly one non-player entity as this session's fixture under a handle, by adding the tag `devtool.fx.<session>.<handle>`. Refuses a duplicate handle. From then on `${fixture.<handle>.uuid}` names it |
| `devtool fixture list` | Each fixture of the session as `loaded`, `unloaded`, or `absent`, judged at its last known place. A server never unloads its spawn chunks, so a fixture standing there is always `loaded` |
| `devtool fixture clear <handle\|all>` | Remove fixtures whose entity still carries this session's tag, logging each UUID. An entity without the tag is refused, not removed |
| `devtool scenario checkpoint <id> next <bundle> [clean\|fatal]` | Store, inside the world save, that `<bundle>` must be the next bundle run: its name and hash, the session, its operator, and its fixtures with their last places. Until it runs, `devtool run` of any other bundle, or of that bundle with changed text, is refused with `devtool.scenario.mismatch` naming both sides. Bundles the allowed bundle runs inside itself are not checked again |
| `devtool scenario status` | The standing checkpoint, or none, or corrupt |
| `devtool scenario complete <id>` and `abandon <id>` | Clear it, with a record. The resume bundle must not start a session itself; the resume restores the stored one, its operator and its fixtures before the bundle runs |
| `devtool reload` | Reload bundles and configuration from disk |
| `devtool save` | Save player data and every loaded world |

`reload` does not disturb an active session, enabled categories, their filters, or a bundle already running. A bundle in flight completes with the commands it started with. It writes a `RELOAD` record with `trigger=command`. The same reload also runs by itself at every server start, with `trigger=serverStart`, so a file edited while no world was open is read when the world opens. Type `reload` only after editing a file while a world is open.

`save` works on single player and dedicated servers alike, where vanilla `save-all` exists only on a dedicated server and needs permission level 4. It leaves each world's saving flag as it found it, so a `save-off` typed earlier stays off. It writes a `SAVE` record with `worlds`, `dimensions` and `durationMs`. A failure writes an `ERROR` and fails the command with `devtool.save.failed`.

`devtool run` refuses a call that does not come from the server thread. RCON runs its commands on its own thread, so start bundles from chat, the server console or the inbox instead.

See [bundles.md](bundles.md) for the file format.

## Arena

| Command | Effect |
| --- | --- |
| `devtool arena create [w] [h] [l] [block]` | Build an arena centred on you |
| `devtool arena info` | Bounds, origin, start position and construction block |
| `devtool arena reset` | Rebuild the structure and empty the interior |
| `devtool arena clear` | Empty the interior without rebuilding structure |

Width, height and length describe the **interior**. `create 20 10 20` gives twenty blocks to walk in.

One arena per dimension, stored in the world save. Creating a new one replaces the record without restoring the previous terrain.

`reset` is idempotent and safe to run at the start of every test. Neither `reset` nor `clear` removes players.

Creating an arena also moves your respawn point to its start position, unless `setRespawnPoint` is disabled in the config.

Minecraft revalidates a respawn point when you die, and falls back to the world spawn if the start position has become obstructed. When that happens it tells you **"Your home bed was missing or obstructed"**, which is misleading here because no bed is involved. If you die and land at world spawn after seeing that message, the arena start was blocked, not lost.

Arena commands place blocks without emptying containers first. A loot chest inside the arena's volume rolls its loot table and drops the items when `create`, `reset` or `clear` replaces it.

**These commands are destructive and do not prompt.** See the warning in the README.

## Inspection

| Command | Effect |
| --- | --- |
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects |
| `devtool inspect entity <selector>` | Registry id, position, motion, health, effects |
| `devtool inspect block <x> <y> <z>` | Block id, metadata, blockstate, tile entity class |
| `devtool inspect container <x> <y> <z>` | A container's occupied slots, one record each |
| `devtool inspect inventory [selector]` | Occupied slots across main inventory, armour and offhand, and bauble slots when Baubles is installed |
| `devtool inspect attributes [selector\|uuid]` | Every attribute of a living entity, default yourself, with its base value, final value and modifiers |
| `devtool entities nearby <radius>` | Every entity within the radius, one record each |
| `devtool nbt entity <selector|uuid|entityId>` | Raw NBT for an entity. A UUID or an entity ID finds a loaded entity in any dimension without the selector engine; the record says which dimension, and a miss writes an `ERROR` with the `dimensionIds` that were searched, so an entity left in a dimension that has since unloaded reads as exactly that. When a selector matches nothing, the `ERROR` record carries `selectorPermission`, `dimensionCount`, `dimensionIds`, `senderDimension` and `liveEntities` |
| `devtool nbt block <x> <y> <z>` | Raw NBT for a tile entity |
| `devtool nbt held` | Raw NBT for the held item |

A selector matching more than one entity is an error, not an invitation to pick one. Name your fixtures and select on the name.

NBT goes to the log, never to chat, and is truncated at `maxNbtOutputLength` characters. Truncation is always reported along with the original length, so you can tell whether raising the limit would recover the rest.

Inspection reports what Minecraft and Forge expose. A modded tile entity's class is named; its contents are not interpreted.

**Inspection never loads a chunk.** `inspect block`, `inspect container` and `nbt block` check that the position is loaded before reading it. Reading an unloaded position would make Minecraft load the chunk, or generate and save it if it was never made, so a read would change the world. An unloaded position fails with `devtool.inspect.notLoaded` and an `ERROR` record naming the position and dimension. This holds in chat, in bundles and through the inbox.

`inspect inventory` with Baubles installed adds one record per occupied bauble slot, with `section=baubles area=baubles` and the slot's type as `slotType`, for example `slot=1 slotType=ring`.

`inspect attributes` writes one `ATTRIBUTE` record per attribute, sorted by name, each followed by one `ATTRIBUTE_MODIFIER` record per modifier, sorted by UUID. A target that is not a living entity fails with an `ERROR`.

### Two commands inspect a block, and they answer different questions

`inspect block` reports **identity and state**: the block id, metadata, blockstate, and the tile entity class. It does **not** report what is inside a container. That is by design, and it is easy to mistake for the whole answer.

`nbt block` reports the tile entity's **raw NBT**, which for a container includes every occupied slot with its item id, count, damage and per-item NBT. If you want to know what is in a chest, this is the command.

```
devtool inspect block 10 64 -3
devtool nbt block 10 64 -3
```

`inspect container` reports the same contents as one parseable record per occupied slot.

One useful consequence: a container whose loot table has not yet rolled writes a `LootTable` reference **instead of** items. So `nbt block` tells "the loot has not been generated yet" apart from "these are the generated contents".

`inspect container` gives the same answer without rolling the loot. Reading any slot of such a container would make vanilla fill it, so the slots are not read. The command writes one record with `lootRolled=false` and `lootTable=<id>`, and no `occupiedSlots`, so it never reads as an empty container. A container with no pending loot table is reported as before.

NBT output is truncated at `maxNbtOutputLength`, and truncation is always reported with the original length, so a large container may need that limit raised.

## Screenshots

| Command | Effect |
| --- | --- |
| `devtool screenshot <label> [delayTicks] [player]` | Ask a player's client to save a screenshot after the delay |

- The label is 1 to 48 letters, digits, `_` or `-`. It becomes a file name, so nothing else is accepted. A refused label writes an `ERROR` and fails with a message, not a failure key.
- The delay is 0 to 600 ticks, default 0.
- The target is the named player, or else you. In a bundle that is whoever ran the bundle; through the inbox it is the inbox operator. The command's chat reply goes to whoever ran it. The target sees only vanilla's "Saved screenshot" line on their own client, when the capture happens.
- The file is `screenshots/<label>-<requestTick>.png` on the target's computer, where `requestTick` is the target's world tick when the request was made. The capture includes an open inventory or menu.
- The target's client must have the toolkit installed. If it does not, nothing is sent and the command fails with `devtool.screenshot.noClient`. A client without the toolkit can still join a server that has it.

The server writes `SCREENSHOT action=requested`. The client writes `SCREENSHOT side=CLIENT action=saved` with the file once it is on disk, or `action=failed` with a `reason`; `reason=noFile`, when no new file appeared, also names the `file`. At most 8 screenshots wait on one client; more fail with `reason=queueFull`, and waiting ones fail with `reason=disconnected` when the player leaves the world.

## Command inbox

| Command | Effect |
| --- | --- |
| `devtool inbox open` | Open the inbox for yourself |
| `devtool inbox close` | Close it |
| `devtool inbox status` | Say whether it is open, and for whom |

The inbox lets an AI assistant start a bundle, reload, save, or run a read-only toolkit command by writing a file, without anyone typing it. It is closed unless both hold:

1. `B:enabled=true` under `inbox` in `config/devtool/devtool.cfg`. It is false by default.
2. A player typed `/devtool inbox open` in chat since the current server started.

The open state is held in memory only. The inbox closes when the player who opened it logs out, when the server stops, when a reload finds the key turned off, or on `inbox close`. In single player, leaving the world closes it, so you open it again each time you open a world. Opening is refused when the key is false (the message names it), when the inbox is already open for another player, and when the command comes from a bundle (`fromBundle`), so a bundle or join automation can never open it. Typing it yourself while a bundle waits on a delay or a checkpoint is allowed. The console, a command block and `/execute` cannot open it.

The player who opened it is the **inbox operator**. Every request runs as that player, with that player's permissions, and a bundle it starts records `channel=inbox` and that player as `caller`.

### Requests

The folder is `config/devtool/inbox/`, on the machine running the server. While the inbox is open the toolkit reads it every 10 ticks. While it is closed, no request is read and no request, claim or result file is changed. The one exception is at server start: a `status.json` left behind by a crash or a killed process is deleted, since the inbox is closed.

- A request is a file `<id>.req` holding one line of printable ASCII, at most 512 characters. The id is 1 to 64 letters, digits, `_`, `.` or `-`. Other files are ignored.
- Write it as `<id>.tmp` and rename it to `<id>.req` when complete. The rename is required: on some systems a file written straight to its `.req` name can be read half-written.
- The toolkit claims a request by renaming it to `<id>.claimed` before reading it, so each runs at most once. Ids must be unique: a new `<id>.req` whose `<id>.claimed` is still there is never run, and only an `INBOX action=refused reason=duplicateId` record says so. It gets no result file, since one would overwrite the earlier answer.
- Requests already waiting when the inbox opens are refused with `placedBeforeOpen`, never run.
- Requests run in file-name order. A `reload` request ends the batch; the rest wait for the next poll.
- The toolkit never deletes request, claim or result files.

Accepted lines, and nothing else:

| Line | Runs |
| --- | --- |
| `run <bundle>` | `devtool run <bundle>` |
| `reload` | `devtool reload` |
| `save` | `devtool save` |
| `mark <label>` | `devtool mark <label>` |
| `inspect ...`, `entities ...`, `nbt ...` | The toolkit command of that name, with the same arguments |
| `screenshot <label> [delayTicks]` | `devtool screenshot`, to the inbox operator only |

A leading `/` is accepted. A vanilla command, any other toolkit command, or a line starting with `devtool` is refused with `verbNotAllowed`. Other refusal reasons: `badArguments` (an allowed verb with arguments it does not take), `malformed` (empty, several lines, non-ASCII, too long, or a file over 4096 bytes), `placedBeforeOpen`, `operatorLeft` (the operator is no longer online; the inbox closes too), and `duplicateId`. An accepted request fails with `rootShadowed` when another mod has taken both `devtool` and `att`, so the request is never handed to that mod's command.

### Results and status

Every claimed request except a reused id gets exactly one `<id>.result.json`, written under a temporary name and then moved into place. It holds `schemaVersion`, `id`, `request`, `verb`, `outcome` (`done`, `failed` or `refused`) and a `reason` when not `done`. A `malformed` request has no `verb`, and one whose file was unreadable or over 4096 bytes has no `request` either.

`reason` is one of:

- a refusal reason above
- `commandFailed`: the command failed, with its message in `detail`. A `run` whose bundle never started, because the bundle does not exist or a scenario checkpoint refused it, is answered this way
- `runNotStarted`: the command reported success but no run began, as when another mod cancelled it
- `bundleFailed`, `senderLost` or `serverStopped`: a run that did not finish cleanly
- `rootShadowed`
- the name of an exception that escaped while the request ran

A `run` is answered when its bundle ends, with the bundle's `BUNDLE_END` counters (`bundle`, `hash`, `executed`, `failed`, `tolerated`, `total`, `stoppedEarly`, `durationTicks`), `senderLost` or `discardedOnServerStop` when they apply, and `firstFailure`, the failing key or message of the first command that failed. For a vanilla command it is often the translation key, such as `commands.fill.failed`.

The log differs in one detail: when a command fails, the `INBOX action=failed` record's `reason` is the command's own message, where the result file says `commandFailed` and puts the message in `detail`.

While the inbox is open, `status.json` in the same folder holds `schemaVersion`, `open`, `operator`, `operatorUuid`, `world` (the save folder), `openedWorldTick` and `openedAtMillis`. It is deleted when the inbox closes and when the server stops, and a stale one is removed at server start.

Every open, close, claim, refusal and dispatch writes an `INBOX` record. See [logging.md](logging.md).

In single player the game pauses when its window loses focus, and a paused game reads no requests. `noPauseOnLostFocus` under `client`, together with `applyOnJoin`, turns that pause off; see [Client defaults](#client-defaults).

**Use the inbox in a development or test instance only.** A mod that runs commands as the real player, as some quest or macro mods do, looks like chat to the toolkit and could open the inbox. That needs such a mod installed and configured, but it is why the inbox is off by default.

## Baubles

Available only when Baubles is installed. Baubles is optional: the toolkit loads after it when present and never requires it. Without it these commands fail with `devtool.baubles.absent` and everything else is unchanged.

| Command | Effect |
| --- | --- |
| `devtool baubles set <slot> <item> [count] [meta] [player]` | Put one item in a bauble slot |
| `devtool baubles clear <slot\|all> [player]` | Empty one slot, or all of them |

- Slots are numbered from 0. With Baubles 1.5.2 they are `amulet`, `ring`, `ring`, `belt`, `head`, `body`, `charm`.
- A slot holds one item, as the Baubles screen allows, so a count other than 1 is refused, and so is an item the slot does not accept. Both write a `BAUBLES` record with `outcome=rejected` and fail with `devtool.baubles.rejected`; nothing is forced. Tolerating that key in a bundle therefore also hides a mistyped count. Replacing an item is allowed and recorded.
- Both commands call the bauble's equip and unequip hooks, as the Baubles screen does, unless Baubles has its events blocked. A mod that grants a bonus on equip behaves as it would for a player. Baubles' own `/baubles` command skips those hooks.
- `clear` on empty slots succeeds, so `clear all` is safe in a teardown bundle.
- Each change writes a `BAUBLES` record.

Building the toolkit with `-Pdev_runtime_baubles=true` puts Baubles on its development runtime. That is for developing the toolkit; to test your own mod with Baubles, install Baubles like any other mod.

## Client defaults

Optional settings applied on your client, off unless `B:applyOnJoin=true` under `client` in `config/devtool/devtool.cfg`. They apply once per launch, at the first world join, and are ignored on a dedicated server. That join is handled once whether or not `applyOnJoin` is on, so a change to any of these keys, `applyOnJoin` included, takes effect after you restart the game. Every key below other than `applyOnJoin` does nothing unless `applyOnJoin` is true, and with it on, brightness and music are always set to their configured values.

| Key under `client` | Default | Effect |
| --- | --- | --- |
| `brightness` | `1.0` | Gamma, 0 to 1 |
| `musicVolume` | `0.0` | Music volume, 0 to 1 |
| `masterVolume`, `weatherVolume`, `hostileVolume`, `friendlyVolume`, `ambientVolume` | `-1` | Volume of that sound category, 0 to 1. `-1` leaves it as it is. Friendly is vanilla's `neutral` category |
| `noPauseOnLostFocus` | `false` | Turn off the pause when the game window loses focus. It only ever turns the pause off. Esc still opens the menu |

Each setting is saved to `options.txt`, like the game's own sliders, so it stays after you turn the key off. Press F3+P in game to turn the pause on lost focus back on.

The client writes one `ENVIRONMENT` record with `side=CLIENT` when it applies them: `brightnessWas`, `brightnessNow`, `musicWas`, `musicNow`, and `<name>Was` and `<name>Now` only for the volumes and the pause it actually set. An absent field means that setting was left alone.
