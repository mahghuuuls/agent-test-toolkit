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

A session lives in memory. `session stop` forgets the session's fixture handles, but the tags stay on the entities, so a new session of the same name finds and adopts them again, on its first `fixture list` or `fixture claim`; nothing is adopted by itself. A full restart of the game or the server ends the session; only a scenario checkpoint restores one.

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
| `devtool assert entity <uuid> present\|absent [at <x> <y> <z>]` | Whether a loaded entity has that UUID. A dying entity, at zero health in its death animation, counts as absent, with `dead=true` on the record. Absent is only a verdict when the place it should be is loaded: give `at`, or use a claimed fixture, otherwise the outcome is `unloaded` with `devtool.assert.unloaded` |
| `devtool assert entity <uuid> dimension <d>` and `within <r> of <x> <y> <z>` | Where a loaded entity is |
| `devtool assert player <name> dimension <d>` and `gamemode <mode>` | Where a player is and how they play |
| `devtool assert attribute <target> <attribute> value <n>` | An attribute's final value, compared at two decimals. `<target>` is a selector or UUID matching exactly one living entity; `<attribute>` is the registry name, such as `generic.armor` |
| `devtool assert attribute <target> <attribute> modifier <uuid\|name> [amount <n>]` | Whether the attribute has that modifier, and with `amount` its amount. A name may hold spaces: every word up to `amount` is the name. A name that matches several modifiers fails with `devtool.assert.ambiguous` rather than choosing one. Vanilla gives all four armour pieces the name `Armor modifier`, so assert those by UUID. A target that matches nothing, several entities, or a non-living entity fails before any comparison and writes no `ASSERT` record |
| `devtool fixture claim <handle> <selector\|uuid\|entityId>` | Mark exactly one non-player entity as this session's fixture under a handle, by adding the tag `devtool.fx.<session>.<handle>`. A handle is letters, digits, `_` and `-`, with no dot; in the tag, every other character of the session name, a `.` or a space included, becomes `_`. Claiming, under a handle the session does not hold yet, an entity that already carries this session's tag for that handle adopts it (`adopted=true`), as after a `session stop`. Refused: a handle the session already holds, even for the same entity (so after a `fixture list` has refilled the handles, a `claim` of the same fixture fails), a handle another entity holds, an entity that is already this session's fixture under another handle, and a handle whose cached fixture is unloaded while a loaded entity carries the tag. The refusals name the other side and have no key. From then on `${fixture.<handle>.uuid}` names it |
| `devtool fixture list` | Each fixture of the session as `loaded`, `unloaded`, `absent`, `removing` (dying or being removed), `untagged` (found, but its tag is gone) or `duplicate` (two living entities carry the tag, listed as `carriers`), judged at its last known place. A server never unloads its spawn chunks, so a fixture standing there is always `loaded` |
| `devtool fixture clear <handle\|all>` | Remove fixtures whose entity still carries this session's tag, without a death: no loot, no experience, no death events, riders left in the world. The `removed` record is written once the removal is confirmed, a tick later, with `confirmedTick`. An entity without the tag is refused (`notOwned`), a duplicate is refused, an unloaded fixture is `notLoaded`, a dying one `alreadyRemoving`. A removal not yet confirmed when the server stops is `unconfirmed reason=serverStopped` |
| `devtool fixture release <handle\|all>` | Take the tag off and forget the handle, leaving the entity in the world. Refused for an entity without the tag (`notOwned`) and for a duplicate; `notLoaded` for an unloaded fixture |
| `devtool scenario checkpoint <id> next <bundle> [clean\|fatal]` | Store, inside the world save, that `<bundle>` must be the next bundle run: its name and hash, the session, its operator, and its fixtures with their last places. Until it runs, `devtool run` of any other bundle, or of that bundle with changed text, is refused with `devtool.scenario.mismatch` naming both sides. Bundles the allowed bundle runs inside itself are not checked again |
| `devtool scenario status` | The standing checkpoint, or none, or corrupt |
| `devtool scenario complete <id>` and `abandon <id>` | Clear it, with a record. The resume bundle must not start a session itself; the resume restores the stored one, its operator and its fixtures before the bundle runs |
| `devtool reload` | Reload bundles and configuration from disk |
| `devtool save` | Save player data and every loaded world |

`reload` does not disturb an active session, enabled categories, their filters, or a bundle already running. A bundle in flight completes with the commands it started with. It writes a `RELOAD` record with `trigger=command`. The same reload also runs by itself at every server start, with `trigger=serverStart`, so a file edited while no world was open is read when the world opens. Type `reload` only after editing a file while a world is open.

`save` works on single player and dedicated servers alike, where vanilla `save-all` exists only on a dedicated server and needs permission level 4. It leaves each world's saving flag as it found it, so a `save-off` typed earlier stays off. It writes a `SAVE` record with `worlds`, `dimensions` and `durationMs`. A failure writes an `ERROR` and fails the command with `devtool.save.failed`.

`devtool run` refuses a call that does not come from the server thread. RCON runs its commands on its own thread, so start bundles from chat, the server console or the inbox instead.

**A fixture's handle is a cache; its tag is the truth.** Placeholders, tab completion and scenario checkpoints read the session's handles. After a `session stop` and a new `session start` of the same name, a fixture known only by its tag is unbound in `${fixture.<handle>.uuid}` and missing from a checkpoint until `fixture list` or `fixture claim` fills the handles again. Session names that map to the same tag part, such as `my test`, `my_test` and `my.test`, share fixtures. If a fixture's tag moves to another entity, the handle follows the tag, and `clear` removes the new carrier.

A 1.3.0 tag with a dotted handle, or from a session whose name had a dot, belongs to no session in 1.4.0. Remove it with vanilla `kill` or `scoreboard players tag <entity> remove <tag>`. A 1.3.0 scenario checkpoint of a dotted session resumes its fixtures as `untagged`.

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
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects, and `openContainer`: `inventory`, or the class of another container the server has open for the player. The server cannot tell the player's own inventory screen from no screen: both are `inventory` |
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

## Client actions

Three steps a person used to do by hand, so a test can run them from a bundle or the inbox.

| Command | What the client does |
| --- | --- |
| `devtool client reconnect [awaySeconds]` | Leave the world, wait `awaySeconds` at the menu (0 to 60, default 0), and come back to the same place: the same server entry on a dedicated server, the same save in single player, saved in full before it stopped |
| `devtool client respawn` | Press Respawn on the death screen, once its buttons are enabled (20 ticks). Waits up to 100 ticks for a death screen still opening |
| `devtool client close` | Close the open screen the way Escape does, telling the server when it is a container. A tick later it records what is showing |

- The target is always you: the player typing it, the caller of a bundle, or the inbox operator through the inbox. There is no player argument, so nobody can send another player's client away. The console, a command block and `/execute` are refused, with no key, so a bundle cannot tolerate that refusal.
- It is off unless your client allows it. The client needs the toolkit, 1.4.0 or later, and `B:allowActions=true` under `client` in its own `devtool.cfg`. Unlike the client defaults, this does not need `applyOnJoin`. In single player, reopening a world picks up a change; a client joining a dedicated server reads it at game start, so restart the client after changing it.
- At every login a 1.4.0 server says hello to such a client, and the client answers whether it allows actions. So the server refuses at once, with a key a bundle can tolerate: `devtool.client.noClient` when the client has no toolkit or one older than 1.4.0, and `devtool.client.notAllowed` when it has not allowed actions or its answer has not arrived. Neither side is ever sent a message it cannot read: a client older than 1.4.0 gets no hello, and a server older than 1.4.0 sends none, so a 1.4.0 client there never answers.
- The server also refuses a respawn asked of a living player, with `devtool.client.notDead`, and any step while your client is on a reconnect, about to leave or back but not yet reported, with `devtool.client.busy`. That ends once the reconnect reports, 10 seconds after the player is back without a report, or after `inbox.rejoinWindowSeconds`.

The client refuses what only it can know. The command itself has already succeeded, so the refusal is a `reason` in the client's record and in its report to the server:

| Step | Client refusals |
| --- | --- |
| Any step | `notAllowed` when the client's own setting changed since it said hello. `busy` for a close or respawn while a reconnect is under way |
| `close` | `noWorld`, `noScreen`, `deathScreenUseRespawn` (use `respawn`), `credits`, `bedScreen`, `loadingScreen`, `busy` (a close still pending). After closing, `failed reason=stillOpen` when another mod kept or reopened the same screen |
| `respawn` | `noWorld`, `credits`, `hardcore` (the button means Spectate World there), and, after waiting, `notDeadYet`, `noDeathScreen` or `timeout`. `busy` for a second respawn |
| `reconnect` | `noWorld`, `realms`, `openToLan` (stopping a world open to LAN would throw every other player out), `noServerData`. `busy` while a close or respawn is still pending |

The server writes `CLIENT_ACTION action=requested` before sending. The client writes `CLIENT_ACTION side=CLIENT` with `step`, `result` (`done`, `refused`, `failed`, or `left` for a reconnect leaving), `reason`, `screen` and `showing`, and sends the same as a report, which the server writes as `CLIENT_ACTION action=reported`, together with `player`, `step` and `requestTick`. A report nobody asked for is dropped without a record. See [logging.md](logging.md).

**Put `client reconnect` last in a bundle.** The client leaves about a second after the request, and the bundle's later steps are lost with it: on a dedicated server they end with `senderLost`, and in single player the run is discarded when the integrated server stops. Do the work after the return in a second bundle or request.

### A reconnect, step by step

1. The client writes `result=left` and reports it.
2. It waits at least 5 ticks and one second, so the run that asked for it is answered first, stops reading the connection, and lets what it already received be handled.
3. It leaves. In single player it stops the integrated server and waits at the title screen; on a dedicated server it disconnects as the pause menu does.
4. At any menu, after at least 20 ticks and the away time, it rejoins.
5. Back in the same world, the server says hello again, and the client writes and reports `result=done` with `awayMs`.

If the connection fails, a different world is joined, the save is gone, or the client is not back within 60 seconds after the away time (4 minutes more while a single-player world is still loading), the client writes `result=failed` with `connectFailed`, `joinedElsewhere`, `saveNotFound`, `loadAborted`, `notAtMenu` or `timeout`, and `folder` in single player. That record cannot reach the server.

A reconnect does not take you to another server or world, and it does nothing at the title screen: once the client is at the menu, no server can reach it.

## Command inbox

| Command | Effect |
| --- | --- |
| `devtool inbox open` | Open the inbox for yourself |
| `devtool inbox close` | Close it |
| `devtool inbox status` | Say whether it is open, for whom, and whether it is suspended |

The inbox lets an AI assistant start a bundle, reload, save, run a read-only toolkit command, take a screenshot, or ask for a client action by writing a file, without anyone typing it. It is closed unless both hold:

1. `B:enabled=true` under `inbox` in `config/devtool/devtool.cfg`. It is false by default.
2. A player typed `/devtool inbox open` in chat since the current server started.

The open state is held in memory only. The inbox closes when the player who opened it logs out, when the server stops, when a reload finds the key turned off, or on `inbox close`. In single player, leaving the world closes it, so you open it again each time you open a world. A reconnect the toolkit started itself only suspends it; see [Across a reconnect](#across-a-reconnect). Opening is refused when the key is false (the message names it), when the inbox is already open for another player, and when the command comes from a bundle (`fromBundle`), so a bundle or join automation can never open it. Typing it yourself while a bundle waits on a delay or a checkpoint is allowed. The console, a command block and `/execute` cannot open it.

The player who opened it is the **inbox operator**. Every request runs as that player, with that player's permissions, and a bundle it starts records `channel=inbox` and that player as `caller`.

### Across a reconnect

A reconnect the toolkit started suspends the inbox instead of closing it. When the inbox operator's own `client reconnect`, from a request, a bundle or typed, makes their client leave, the inbox writes `INBOX action=suspended reason=reconnect` with `resumeDeadlineMillis` and stops reading requests; they wait in the folder. When the same client is back with the one-time token it was sent away with, the inbox writes `INBOX action=resumed` and reads the waiting requests, without anyone typing `inbox open`.

Between the reconnect request and the leave, the inbox already holds further requests, for at most 15 seconds, so a batch's next line runs after the return.

Every other path closes it, with a reason:

- `rejoinTimeout`: the deadline passed. That is `I:rejoinWindowSeconds` under `inbox` (default 120, 10 to 600) of wall-clock time, and it also passes while no server runs at all in single player. Keep it longer than the away time plus a world load.
- `returnedOtherRoute`: the operator logged in again, but no token arrived within 10 seconds.
- `tokenMismatch`: the token was wrong.
- `worldChanged`: a different world loaded.
- The key turned off, `inbox close`, or a dedicated server's stop.

A leave by the pause menu is an ordinary leave and closes it with `operatorLeft`. A game quit while suspended leaves `status.json` behind, as a crash does, until the next server start deletes it; the new process starts closed.

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
| `client reconnect [awaySeconds]`, `client respawn`, `client close` | `devtool client ...`, for the inbox operator's own client. `awaySeconds` is 0 to 60. Lower case only: `client Reconnect` is `badArguments` |

A leading `/` is accepted. A vanilla command, any other toolkit command, or a line starting with `devtool` is refused with `verbNotAllowed`. Other refusal reasons: `badArguments` (an allowed verb with arguments it does not take, such as `client reconnect 99`), `malformed` (empty, several lines, non-ASCII, too long, or a file over 4096 bytes), `placedBeforeOpen`, `operatorLeft` (the operator is no longer online; the inbox closes too), and `duplicateId`. An accepted request fails with `rootShadowed` when another mod has taken both `devtool` and `att`, so the request is never handed to that mod's command.

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

While the inbox is open, `status.json` in the same folder holds `schemaVersion`, `open`, `operator`, `operatorUuid`, `world` (the save folder), `openedWorldTick` and `openedAtMillis`, and while suspended also `suspended`, `reason` and `resumeDeadlineMillis`. It is deleted when the inbox closes and when the server stops, and a stale one is removed at server start. A request written while the inbox is suspended waits and runs after the return.

Every open, close, claim, refusal, dispatch, suspension and resume writes an `INBOX` record. See [logging.md](logging.md).

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

`allowActions` under `client` is not one of these defaults. It needs no `applyOnJoin` and is described under [Client actions](#client-actions).
