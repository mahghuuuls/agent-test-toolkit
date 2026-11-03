# Agent Test Toolkit

A Forge 1.12.2 mod that turns a manual test into something you can build from a file and read back from a log.

This document is self-contained. Everything you need to use the toolkit is here: the commands, the bundle format, the record vocabulary, and worked examples. `docs/` holds the same material written for a human.

## What you use this for

You have just implemented a feature. You cannot play the game, and asking a person "did it work?" gets you a sentence when you need evidence.

This toolkit gives you two things:

1. **A way to build the test environment from a file you write.** One JSON file becomes one command a person types. Arena, fixtures, gamerules, logging, marks: all of it set up identically every run.
2. **A machine-readable account of what the game did**, written into `latest.log` next to Forge's output and your own mod's output.

The intended shape of a test is: you write the setup, a person performs two or three specific actions, you read the log and decide.

## Your working loop

You never type in the game. **Your hands are the filesystem.**

```
1. WRITE    run/config/devtool/bundles/<yourfile>.json
2. ASK      person runs:  /devtool reload
                          /devtool run <bundle_name>
3. ASK      person performs the actions you listed, in order
4. READ     run/logs/latest.log
```

Step 2 is two commands and never more. If you find yourself asking a person to type a sequence of setup commands, that sequence belongs in the bundle instead.

`reload` re-reads bundles and configuration from disk. It does not disturb an active session, the enabled categories, their filters, or a bundle already running. The same reload runs by itself at every server start, so a file you edited while no world was open is read when the world opens, and nobody needs to type `reload` after opening a world. You need it after a file edit made **while a world is open**; a person who forgets it will run the previous version of your bundle, and the log will not say so. The `RELOAD` record says which reload ran (`trigger=command` or `trigger=serverStart`).

### The loop with the inbox

When the owner has enabled the command inbox, you can start bundles yourself:

```
1. ASK      person, once per world opening:  /devtool inbox open
2. WRITE    run/config/devtool/bundles/<yourfile>.json
3. WRITE    run/config/devtool/inbox/<id>.tmp, then rename it to <id>.req
4. WAIT     for run/config/devtool/inbox/<id>.result.json
5. ASK      person performs the actions you listed, in order
6. READ     the result file and run/logs/latest.log
```

The inbox runs only bundles and read-only toolkit commands, never a vanilla command. See [The command inbox](#the-command-inbox) and worked example I.

### Paths

| What | Where |
| --- | --- |
| Bundles you write | `run/config/devtool/bundles/*.json` (scanned recursively) |
| Configuration | `run/config/devtool/devtool.cfg` |
| Result files | `run/config/devtool/results/<bundle>-<startTick>.json` |
| Inbox requests and answers | `run/config/devtool/inbox/` |
| Screenshots on request | `run/screenshots/` of the client that took them |
| The log you read | `run/logs/latest.log` |

On a dedicated server these live under the **server's** directory, not the client's, except screenshots, which the client saves. A client-side copy of a bundle or a request has no effect on what the server runs.

## Writing a bundle

A file maps bundle names to definitions. One file can hold many bundles; names share a single global namespace across all files.

```json
{
  "feat_redstone_block_setup": {
    "description": "Arena, one placed test block, logging armed",
    "stopOnFailure": true,
    "commands": [
      "devtool session start redstone_block",
      "devtool arena reset",
      "gamerule doMobSpawning false",
      "gamerule doDaylightCycle false",
      "devtool log player_interaction on arena",
      "devtool log block_place on arena",
      { "command": "devtool mark SETUP_COMPLETE", "delayTicks": 20 }
    ]
  }
}
```

Rules that matter to you:

- **Commands take no leading slash inside a bundle.** In chat a person types `/devtool run x`; in the file it is `devtool run x`.
- `stopOnFailure` defaults to **true**. Leave it true for setup, so a broken step does not produce a half-prepared world you then test in.
- `delayTicks` means **wait, then run this command**. The wait starts when the previous command finishes, not at the bundle's start. Two commands with `delayTicks: 20` put the second 40 ticks in. 20 ticks per second. Put the delay on the command that must run after the wait: to let chunks load after a teleport, keep the teleport immediate and delay the command that follows it. Set `"trace": true` on a bundle to get a `BUNDLE_STEP` record per command showing when it was due and when it ran.
- **`toleratedFailures` lets one command fail without stopping the bundle**, for the specific reasons you name. An idempotent reset like `effect @s clear` fails when there is nothing to clear; declare `["commands.effect.failure.notActive.all"]` on that command and it will be tolerated, while a typo in the same command still fails. Find the key in the `detail` field of the `ERROR` record. A tolerated failure is recorded as a success carrying a note that names the key, so it never reads as a command that simply worked. Each tolerated step also writes one `TOLERATED` record naming the key, and `BUNDLE_END` counts them in `tolerated`; `failed` still counts only real failures.
- **Two vanilla keys you will often declare.** `commands.fill.failed` is what `fill` reports when every block in the region already is the block you asked for. `commands.setblock.noChange` is what `setblock` reports when the block is already there. Both are the second run of an idempotent setup, and neither is tolerated unless you declare it.
- **The toolkit has failure keys of its own**, in the `devtool.` namespace, and you tolerate them the same way: `devtool.wait.timeout` when a wait ran out of time, `devtool.wait.cancelled` when it became impossible, such as the caller changing dimension mid-wait. `devtool.placeholder.unbound` when a placeholder has no value yet, `devtool.placeholder.unsafe` when its value could break the command. `devtool.assert.failed` when an assertion did not match, `devtool.assert.unloaded` when an absence could not be judged because the place is not loaded, `devtool.assert.ambiguous` when a modifier name matches several modifiers. `devtool.scenario.mismatch` when a scenario checkpoint expects a different bundle, or this bundle with different text. `devtool.inspect.notLoaded` when an inspection names a position whose chunk is not loaded. `devtool.save.failed` when `devtool save` could not save a world. `devtool.screenshot.noClient` when the target player's client does not have the toolkit. `devtool.client.noClient` when your own client does not have the toolkit for a `devtool client` step, `devtool.client.notAllowed` when it has not allowed client actions, `devtool.client.notDead` when a respawn is asked of a living player, `devtool.client.busy` when a reconnect is about to leave. `devtool.baubles.absent` when Baubles is not installed, `devtool.baubles.rejected` when a bauble slot refuses the item. That is the whole list; tolerate any of them on the one command that may fail that way.
- **Some refusals carry no key.** A screenshot label with a forbidden character fails with an `ERROR` record and a message, not a key, so a bundle cannot tolerate it by key. Fix the label instead.
- **Placeholders.** Bind the human once with `devtool session operator @s`, then write `${operator.name}`, `${operator.uuid}` or `${operator.selector}` in any command; a claimed fixture is `${fixture.<handle>.uuid}` once `devtool fixture claim` has named it. That is the whole list. An unknown placeholder fails the file at reload. A value is filled in when the command runs, and a command whose value is missing fails with `devtool.placeholder.unbound` instead of running with a blank.
- **Result files.** Put `"result": true` on a bundle and each run writes `run/config/devtool/results/<bundle>-<startTick>.json`: the `BUNDLE_END` counters including `tolerated`, then assertions with outcomes, failures, markers, checkpoints, waits, and `toleratedSteps` with one entry per `TOLERATED` record, all copied from the log records. Read that when you want a run's verdicts without grepping; read the log when you want everything.
- **No comments.** JSON has none, and a `"_comment"` key at the top level fails the *entire file*, because every top-level entry must be a bundle. Use `description`.
- **A duplicate name loads from neither file**, and the error names both. Prefix your bundles so they cannot collide: `feat_`, or the feature's name.
- `example_` is reserved for the bundles the toolkit ships.
- **Not a scripting language.** No variables, conditionals, loops, or arithmetic. The four placeholder forms above are lookups, not a language: `say ${player}` is not one of them, so the file is refused at reload with the placeholder named. If you want a variable, write two bundles.
- **Nesting works**: a command may be `devtool run other_bundle`. The parent waits; the child's whole outcome counts as one command. Cycles and nesting past depth 10 are refused before anything runs.
- A bundle runs **as whoever ran it**, fully. Permissions, position, world, and sender identity are the caller's own, so a command may omit its player argument exactly as if typed in chat: `gamemode creative` works, and so do bare `clear`, `kill`, `tp`, `xp`, `spawnpoint`, `setworldspawn` and `scoreboard`. A bundle is a convenience for typing, never a way to widen permissions.
- **Every `devtool` subcommand needs permission level 2**, typed or inside a bundle. A caller below level 2 cannot run any of them from a bundle either. `BUNDLE_START` records the level the toolkit checked as `permissionLevel`, with `caller` and `channel`.
- **In 1.0.0 that was not true.** Those eight commands failed with `commands.generic.player.unspecified` when their player argument was omitted, because the sender was substituted. Fixed in 1.0.1. If you are writing for 1.0.0, pass an explicit selector such as `@p`.

### Split setup from teardown

Write them as separate bundles and make teardown re-runnable:

```json
{
  "feat_redstone_block_teardown": {
    "description": "Return to a neutral state without ending the session",
    "stopOnFailure": false,
    "commands": [
      "devtool log all off",
      "devtool arena clear",
      "kill @e[name=probe]",
      "clear @p"
    ]
  }
}
```

`kill` matching nothing and `clear` on an empty inventory both **succeed**, deliberately, so a teardown survives being run twice.

## What you can observe

Eight categories. **All are off by default**, and a category that is off produces no records at all.

| Category | Records | Emits |
| --- | --- | --- |
| `block_place` | a block being placed | `BLOCK_PLACE` |
| `block_break` | a block being broken | `BLOCK_BREAK` |
| `entity_spawn` | an entity genuinely spawning | `ENTITY_SPAWN` |
| `entity_death` | an entity dying | `ENTITY_DEATH` |
| `entity_damage` | damage dealt, with the outcome | `ENTITY_DAMAGE` |
| `player_interaction` | right or left clicking a block | `PLAYER_INTERACT` |
| `entity_interaction` | right clicking an entity | `ENTITY_INTERACT` |
| `item_use` | using an item while targeting nothing | `ITEM_USE` |

```
devtool log <category> on
devtool log <category> on arena
devtool log <category> on radius <n>
devtool log <category> off
devtool log all off
devtool log status
```

**Enable narrowly and late; disable early.** Put the enables at the end of your setup bundle, immediately before the mark, so the setup's own block placements do not fill the log you are about to read.

**Filter the noisy ones.** `entity_spawn` in particular will produce hundreds of records if a person walks into fresh terrain, because generating terrain genuinely spawns hundreds of entities. That is correct behaviour and it will still make your log unreadable. One filter per category, replacing any previous one; a radius filter is anchored where the person stood when it was applied and does not follow them.

**A spawn egg names its entity after `ENTITY_SPAWN` is written.** Minecraft spawns the entity first and applies the egg's custom name afterwards, so the spawn record of an entity from a named egg has no `name`. Select it by position or type, or inspect it a tick later, rather than expecting the name on the spawn record.

You can turn categories on and off **mid-test** from a second bundle. This is the right tool when a first run gives you an ambiguous result: re-run with one more category enabled rather than enabling everything up front.

## Commands

Every subcommand requires permission level 2, typed or inside a bundle. Root command `devtool`, alias `att` (a fallback, not a guarantee: a mod whose own command is `att` takes it, and nothing reports that).

An unknown subcommand fails with an `ERROR` record and, in the chat reply only, names the closest real one when there is one: `devtool logging` answers "Did you mean log?". The `ERROR` record carries only the typed name. A typed word shorter than three letters gets no suggestion. Otherwise the suggestion is a subcommand one edit away; failing that, a subcommand of at least three letters that the typed word starts with, or that starts with the typed word; failing that, a subcommand two edits away. It is text only; nothing runs.

### Environment preparation

| Command | Effect |
| --- | --- |
| `devtool arena create [w] [h] [l] [block]` | Build a sealed, lit, empty box centred on the caller |
| `devtool arena reset` | Rebuild structure and empty the interior. **Idempotent** |
| `devtool arena clear` | Empty the interior, leave the structure |
| `devtool arena info` | Bounds, origin, start position, construction block |

Dimensions are the **interior**: `create 20 10 20` gives twenty blocks to walk in. One arena per dimension, stored in the world save, so it survives a restart. Lighting is in the floor, not the ceiling, so it works at any height and nothing hostile spawns inside.

`arena reset` at the top of every setup bundle is the single highest-value habit here. It is what makes two runs comparable.

Creating an arena also moves the caller's respawn point to its start position. Minecraft revalidates that on death and silently falls back to world spawn if the position has become obstructed, reporting **"Your home bed was missing or obstructed"**, which is misleading, since no bed is involved. If a person reports respawning far away after seeing that, the arena start was blocked.

Arena commands place blocks without emptying containers first. A loot chest inside the arena's volume therefore rolls its loot table and drops the items when `create`, `reset` or `clear` replaces it. Build loot tests outside the arena, or place the chest after the reset.

### Session and marks

| Command | Effect |
| --- | --- |
| `devtool session start <name>` | Stamp every subsequent record with a name and a relative tick |
| `devtool session stop` | End it |
| `devtool session status` | Report the active session and its operator, if bound |
| `devtool session operator <player>` | Bind exactly one online player as the session's operator, by name or selector. Zero or several matches are refused. The binding ends when the session stops or the player disconnects, and every change writes an `OPERATOR` record |
| `devtool mark <label>` | Write a labelled marker record. The chat reply repeats the label, so an operator can tell which marker fired |

Start a session in every setup bundle. Without one you are searching a log; with one you are reading a slice of it.

A session belongs to one save. Leaving to the title screen and reopening the same save keeps it. Opening a different save stops it first, with `SESSION_STOP reason=worldChanged`, which also ends its operator binding and forgets its fixtures. See [Worlds and sessions](#worlds-and-sessions).

**Marks are how you tell "the action produced nothing" from "they never got that far."** When you ask a person for a sequence of actions, put a mark before each one. Then a missing record is informative instead of ambiguous.

### Inspection

| Command | Effect |
| --- | --- |
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects |
| `devtool inspect entity <selector>` | Registry id, position, motion, health, effects |
| `devtool inspect block <x> <y> <z>` | Block id, metadata, blockstate, tile entity class |
| `devtool inspect container <x> <y> <z>` | A container's occupied slots, one `INVENTORY_INSPECT` record each. A container whose loot table has not rolled yet is reported as `lootRolled=false lootTable=<id>` with no `occupiedSlots`, and its loot is **not** rolled |
| `devtool inspect inventory [selector]` | Occupied slots across inventory, armour, offhand, and bauble slots when Baubles is installed (`section=baubles area=baubles slotType=...`) |
| `devtool inspect attributes [selector\|uuid]` | One `ATTRIBUTE` record per attribute of a living entity (default the sender), each followed by one `ATTRIBUTE_MODIFIER` per modifier. A target that is not a living entity fails with an `ERROR` |
| `devtool entities nearby <radius>` | Every entity within the radius, one record each |
| `devtool nbt entity <selector|uuid|entityId>` | Raw NBT for an entity. A UUID or an entity ID finds a loaded entity in any dimension without the selector engine; the record says which dimension, and a miss writes an `ERROR` with the `dimensionIds` that were searched, so an entity left in a dimension that has since unloaded reads as exactly that. When a selector matches nothing, the `ERROR` record carries `selectorPermission`, `dimensionCount`, `dimensionIds`, `senderDimension` and `liveEntities` |
| `devtool nbt block <x> <y> <z>` | Raw NBT for a tile entity |
| `devtool nbt held` | Raw NBT for the held item |

**`inspect block` does not report container contents.** It answers what a block *is*: id, metadata, blockstate, tile entity class. For what is *inside* a chest, use `nbt block`, which writes the tile entity's raw NBT including every occupied slot. Two commands, two questions, and mistaking one for the other has already cost an external project a workaround it did not need.

A container whose loot table has not rolled writes a `LootTable` reference instead of items, so `nbt block` also distinguishes "loot not generated yet" from "these are the contents". `inspect container` says the same thing as `lootRolled=false` and leaves the loot unrolled, because reading any slot of such a container would roll it. Neither command changes the container.

**Inspection never loads a chunk.** `inspect block`, `inspect container` and `nbt block` check that the position is loaded before reading it. An unloaded position fails with `devtool.inspect.notLoaded` and an `ERROR` record naming the position and dimension, and nothing is loaded or generated. This holds in chat, in bundles and through the inbox. To inspect something far away, get a player there first, or wait for its chunks with `devtool await chunks`.

**A selector matching more than one entity is an error, not an invitation to pick one.** Name your fixtures on summon and select on the name:

```
summon minecraft:zombie ~3 ~ ~ {CustomName:"probe",NoAI:1}
devtool inspect entity @e[name=probe]
```

Inspection reports what Minecraft and Forge expose. A modded tile entity's class is named; its contents are not interpreted.

NBT goes to the log, never to chat, and is truncated at `maxNbtOutputLength`. Truncation is always reported with the original length, so you can tell whether raising the limit would recover the rest.

### Bundles and self-reporting

| Command | Effect |
| --- | --- |
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
| `devtool assert attribute <target> <attribute> modifier <uuid\|name> [amount <n>]` | Whether the attribute has that modifier, and with `amount` its amount. A name may hold spaces: every word up to `amount` is the name. A name that matches several modifiers fails with `devtool.assert.ambiguous` instead of choosing one; vanilla gives all four armour pieces the name `Armor modifier`, so assert those by UUID. A target that matches nothing, several entities, or a non-living entity fails before any comparison and writes no `ASSERT` |
| `devtool fixture claim <handle> <selector\|uuid\|entityId>` | Mark exactly one non-player entity as this session's fixture under a handle, by adding the tag `devtool.fx.<session>.<handle>`. Refuses a duplicate handle. From then on `${fixture.<handle>.uuid}` names it |
| `devtool fixture list` | Each fixture of the session as `loaded`, `unloaded`, or `absent`, judged at its last known place. A server never unloads its spawn chunks, so a fixture standing there is always `loaded` |
| `devtool fixture clear <handle\|all>` | Remove fixtures whose entity still carries this session's tag, logging each UUID. An entity without the tag is refused, not removed |
| `devtool scenario checkpoint <id> next <bundle> [clean\|fatal]` | Store, inside the world save, that `<bundle>` must be the next bundle run: its name and hash, the session, its operator, and its fixtures with their last places. Until it runs, `devtool run` of any other bundle, or of that bundle with changed text, is refused with `devtool.scenario.mismatch` naming both sides. Bundles the allowed bundle runs inside itself are not checked again |
| `devtool scenario status` | The standing checkpoint, or none, or corrupt |
| `devtool scenario complete <id>` and `abandon <id>` | Clear it, with a record. The resume bundle must not start a session itself; the resume restores the stored one, its operator and its fixtures before the bundle runs |
| `devtool reload` | Re-read bundles and configuration from disk. Writes `RELOAD trigger=command`. The same reload runs at every server start with `trigger=serverStart` |
| `devtool save` | Save player data and every loaded world, on single player and dedicated servers alike. Leaves each world's saving flag as it found it, so a `save-off` typed earlier stays off. Writes `SAVE`. A failure writes an `ERROR` and fails with `devtool.save.failed`. Vanilla `save-all` exists on a dedicated server only and needs level 4; this needs level 2 |
| `devtool screenshot <label> [delayTicks] [player]` | Ask a player's client to save a screenshot after the delay. The label is 1 to 48 letters, digits, `_` or `-`; the delay is 0 to 600 ticks, default 0. The target is the named player, else the sender, which in a bundle is its caller and through the inbox is the inbox operator. The client must have the toolkit installed, or the command fails with `devtool.screenshot.noClient` and nothing is sent |
| `devtool inbox open\|close\|status` | Open, close or report the command inbox. See [The command inbox](#the-command-inbox) |
| `devtool bundle list` | Loaded bundles and their command counts |
| `devtool bundle show <name>` | One bundle's commands in order, with delays |
| `devtool capabilities` | Version, commands, inspection types, logging categories |
| `devtool environment` | Minecraft and Forge versions, dimension, difficulty, position |
| `devtool mods` | Every loaded mod id and version |

`capabilities` is read from the live command registry and the category enum, never from a written list, so it cannot claim a feature the jar does not have. Use it when you need to know what build is actually running.

`devtool run` refuses a call that does not come from the server thread. RCON runs its commands on its own thread, so start bundles from chat, the server console or the inbox instead.

**Screenshots.** The server writes `SCREENSHOT action=requested` with the file name it asked for, `screenshots/<label>-<requestTick>.png`, where `requestTick` is the target's world tick when the request was made. The client writes `SCREENSHOT side=CLIENT action=saved` with the file once it is on disk, or `action=failed` with a `reason`. The capture is of the whole window, an open inventory or menu included, so pair it with a `checkpoint cue` that tells the person what to have on screen. At most 8 screenshots wait on a client at once; more fail with `reason=queueFull`, and waiting ones fail with `reason=disconnected` when the player leaves the world. In a dedicated-server setup the file is on the client's computer, not the server's.

### Baubles slots (only with Baubles installed)

| Command | Effect |
| --- | --- |
| `devtool baubles set <slot> <item> [count] [meta] [player]` | Put one item in a bauble slot through Baubles' own handler. A slot holds one item, so a count other than 1 is refused, and so is an item the slot does not accept. Both refusals write `BAUBLES outcome=rejected` and fail with `devtool.baubles.rejected`; nothing is forced. Tolerating that key therefore also hides a mistyped count. Replacing an item is allowed and named in the record as `replaced` |
| `devtool baubles clear <slot\|all> [player]` | Empty one slot or all of them. Empty slots are a success, so `clear all` is safe in a teardown |

Slots are numbered from 0. With Baubles 1.5.2 the seven slots are typed `amulet`, `ring`, `ring`, `belt`, `head`, `body`, `charm`, and the records carry that type as `slotType`. Both forms call the bauble's equip and unequip hooks, as the Baubles screen does, unless Baubles has its events blocked, so a mod that grants a bonus on equip behaves as it would for a player. Baubles' own `/baubles` command skips those hooks; this one does not. Each change writes a `BAUBLES` record. Without Baubles, both forms fail with `devtool.baubles.absent`.

Baubles is optional. The toolkit loads after it when it is present and never requires it; without it, everything else behaves exactly the same.

## The command inbox

The inbox lets you start a bundle, reload, save, or run a read-only toolkit command yourself, by writing a file. It is closed unless both hold:

1. the owner set `inbox.enabled` to true in `run/config/devtool/devtool.cfg` (`B:enabled=true` under `inbox`; false by default), and
2. a player typed `/devtool inbox open` in chat since the current server started.

The opening is held in memory only, never written to disk. It closes when the player who opened it logs out (`reason=operatorLeft`), when the server stops (`serverStopped`), when a reload finds the key turned off (`disabled`), or when someone types `devtool inbox close` (`closed`). In single player leaving the world closes it, so the owner opens it again each time a world is opened. `devtool inbox status` reports whether it is open and for whom.

Opening is refused, with an `INBOX action=refused` record, when the key is false (`reason=disabled`; the chat message names the key), when it is already open for another player (`openForAnother`), and when the command comes from a bundle (`fromBundle`). A bundle, or join automation, can never open the inbox, and neither can the console, a command block, or `/execute`. A person typing it while a bundle waits on a delay or a checkpoint is not refused. **Never put `devtool inbox open` in a bundle.** Ask the owner to type it.

The player who opened it is the **inbox operator**. Every request runs with that player as the sender and with that player's permissions, and a bundle it starts carries `caller=<operator> channel=inbox`.

### Writing a request

The folder is `run/config/devtool/inbox/`, on a dedicated server under the server's directory. While the inbox is open the toolkit reads it every 10 ticks, on the server thread. While it is closed, no request is read and no request, claim or result file is changed. The one exception is at server start: a `status.json` left behind by a crash or a killed process is deleted, since the inbox is closed.

1. Check that `status.json` exists. It is present exactly while the inbox is open, with `schemaVersion`, `open`, `operator`, `operatorUuid`, `world` (the save folder), `openedWorldTick` and `openedAtMillis`. No file means closed or not started.
2. Write the request to `<id>.tmp`, then rename it to `<id>.req`. The rename is mandatory: the toolkit reads only `.req` files, and on some systems a file written straight to its `.req` name can be claimed half-written.
3. Wait for `<id>.result.json`.

Rules:

- `<id>` is 1 to 64 letters, digits, `_`, `.` or `-`. Files named any other way are ignored.
- **Ids must be unique.** The toolkit claims a request by renaming `<id>.req` to `<id>.claimed` before reading it, so it runs at most once. A new `<id>.req` whose `<id>.claimed` is still in the folder is never run: it gets one `INBOX action=refused reason=duplicateId` record with the id and the request line, and **no result file**, since one would overwrite the earlier answer. Put a counter or a timestamp in every id.
- One request per file: one line of printable ASCII, at most 512 characters. A trailing line break is fine; a second line is not.
- A request placed before the inbox opened is never run. Opening claims every waiting `.req` and refuses it with `placedBeforeOpen`, each with a result file. Write requests after `status.json` appears.
- Requests waiting in one poll run in file-name order. A `reload` request ends that poll; the rest wait for the next one.
- The toolkit never deletes request, claim or result files. Tidy the folder yourself while the inbox is closed, or keep using new ids.

### What a request may say

| Line | Runs |
| --- | --- |
| `run <bundle>` | `devtool run <bundle>`. The name is 1 to 128 letters, digits, `_`, `.` or `-` |
| `reload` | `devtool reload` |
| `save` | `devtool save` |
| `mark <label>` | `devtool mark <label>` |
| `inspect ...` | `devtool inspect ...`, any form |
| `entities ...` | `devtool entities ...` |
| `nbt ...` | `devtool nbt ...` |
| `screenshot <label> [delayTicks]` | `devtool screenshot`, always to the inbox operator. No player argument |

A leading `/` is accepted. Nothing else is: no vanilla command, no other toolkit command, no `devtool` or `att` prefix. Anything that changes the world goes in a bundle you wrote, which the owner can read, and the inbox starts it with `run`.

Refusals, on both the `INBOX` record and the result file:

| `reason` | Meaning |
| --- | --- |
| `verbNotAllowed` | The first word is not in the table above, `devtool` included |
| `badArguments` | The verb is allowed but its arguments are not, such as `run` with two words, `reload` with one, or a screenshot delay over 600 |
| `malformed` | Empty, more than one line, not printable ASCII, over 512 characters, or a file over 4096 bytes |
| `placedBeforeOpen` | Already waiting when the inbox opened |
| `operatorLeft` | The inbox operator is no longer online. The inbox closes too |
| `duplicateId` | The id was already claimed. Record only, no result file |

An accepted request can still fail, with `outcome=failed`: `rootShadowed` when another mod has taken both `devtool` and `att`, so the toolkit will not hand your request to that mod's command; or the name of an exception that escaped while it ran.

### Reading the answer

Every claimed request, except a reused id, gets exactly one `<id>.result.json`. It is written under a temporary name and then moved, so you never read half a file. Fields:

- `schemaVersion`, `id`, `request` (the line), `verb`. A `malformed` request has no `verb`, and one whose file was unreadable or over 4096 bytes has no `request` either
- `outcome`: `done`, `failed` or `refused`
- `reason` when not `done`: a refusal reason above, or `commandFailed` (the command's message is in `detail`), `runNotStarted`, `bundleFailed`, `senderLost`, `serverStopped`, `rootShadowed`, or the name of an exception that escaped. A `run` whose bundle never started, because the bundle does not exist or a scenario checkpoint refused it, is `commandFailed` with the reason in `detail`. `runNotStarted` means the command reported success but no run began, as when another mod cancelled it
- for `run`: `bundle`, `hash`, `executed`, `failed`, `tolerated`, `total`, `stoppedEarly` and `durationTicks`, the same values as `BUNDLE_END`; `senderLost` when the operator disconnected mid-run; `discardedOnServerStop` when the server stopped first; `firstFailure`, the failing key or message of the first command that failed, when one did. For a vanilla command it is often the translation key, such as `commands.fill.failed`

A `run` is answered when its bundle **ends**, not when it starts, so a bundle with a long checkpoint answers late. It is `done` only when the run finished with `failed=0`. Every other verb is answered in the tick it ran.

The log tells the same story: `INBOX action=claimed`, then `refused`, or the command's own records followed by `INBOX action=dispatched` (or `failed`). When a command fails, the `INBOX action=failed` record's `reason` is the command's own message, while the result file says `reason=commandFailed` and puts that message in `detail`. The result file is a convenience; the log is the authority.

**In single player a paused game reads nothing.** The game pauses when its window loses focus or a menu is open, and a paused single-player game runs no ticks, so requests wait until it resumes. `client.noPauseOnLostFocus` turns the focus pause off, but only together with `client.applyOnJoin=true`, and only from the next game launch if the first world join has already happened.

## Configuration

`run/config/devtool/devtool.cfg` is a Forge configuration file. Keys are written here as `<category>.<key>`; in the file `inbox.enabled` is `B:enabled=true` inside `inbox { }`. Server-side keys take effect at the next reload or server start.

The table lists the keys this release added and the ones you need for the inbox, spawn logging, join automation and client defaults. The arena keys (`arena.defaultWidth`, `arena.defaultHeight`, `arena.defaultLength`, `arena.maxArenaDimension`, `arena.constructionBlock`, `arena.ceiling`, `arena.setRespawnPoint`) and `diagnostics.maxNbtOutputLength` are described by the comments in the file itself.

| Key | Default | Effect |
| --- | --- | --- |
| `inbox.enabled` | `false` | Allows `devtool inbox open`. For a development or test instance only |
| `diagnostics.spawnIncludeItems` | `false` | `entity_spawn` also records dropped items and experience orbs |
| `join.enabled`, `join.bundle` | `false`, empty | Run a bundle when an operator joins. Its `BUNDLE_START` says `channel=join` |
| `client.applyOnJoin` | `false` | Apply the client defaults below, once per launch at the first world join, and write the client `ENVIRONMENT` record. Every other `client.` key does nothing unless this is true. Ignored on a dedicated server |
| `client.brightness` | `1.0` | Gamma, 0 to 1. Always set to this value when `applyOnJoin` is on |
| `client.musicVolume` | `0.0` | Music volume, 0 to 1. Always set to this value when `applyOnJoin` is on, so the default mutes music |
| `client.masterVolume`, `client.weatherVolume`, `client.hostileVolume`, `client.friendlyVolume`, `client.ambientVolume` | `-1` | Volume of that sound category, 0 to 1. `-1`, or any negative value, leaves the category as it is. Friendly is vanilla's `neutral` category |
| `client.noPauseOnLostFocus` | `false` | When true, turns off vanilla's pause when the window loses focus. It only ever turns the pause off. Esc still opens the menu |

The volume keys and `client.noPauseOnLostFocus` act only when `client.applyOnJoin` is true. The client handles the first world join of each launch once, and marks it handled before it checks `applyOnJoin`, so a change to any `client.` key made after that join, `applyOnJoin` included, takes effect only after the game is restarted.

Every client default is saved to `options.txt`, as the game's own sliders are, so it stays after you turn the key back off. For the pause, F3+P in game turns it back on. The client `ENVIRONMENT` record names `brightness` and `music` every time, and `<name>Was`/`<name>Now` for the other categories and `pauseOnLostFocus` only when they were set, so an absent field always means "left as it was".

The toolkit records on the server. Install it on the client too only when you want screenshots or client defaults; a client without it still joins a server that has it.

`-Pdev_runtime_baubles=true`, as in `./gradlew runClient -Pdev_runtime_baubles=true`, puts Baubles on the toolkit's own development runtime. It is for developing the toolkit. To test a mod with Baubles, install Baubles in that instance like any other mod.

## Record vocabulary

One line per record, never spanning lines:

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

Field names are camelCase. Field order is stable per type. A value containing whitespace is quoted. **An absent optional value is omitted entirely**, so you will never parse `field=`. Block coordinates are integers; entity positions and damage amounts have two decimals.

`side` leads every record, then `worldTick`, then `session` and `sessionTick` when a session is active. `worldTick` comes from the dimension where the event happened, and dimensions tick independently, so it is not comparable across dimensions.

**There are 45 record types and no others.** The vocabulary is closed: no near-synonyms, no renames between versions.

**Category-gated.** These eight exist only while their category is on:

| Record | Key fields beyond the common ones |
| --- | --- |
| `BLOCK_PLACE` | `block` `meta` `blockState` `dimension` `posX/Y/Z` `placedBy` `placedById` `placedByUuid` |
| `BLOCK_BREAK` | `block` `meta` `blockState` `posX/Y/Z` |
| `ENTITY_SPAWN` | `entity` `entityId` `name` |
| `ENTITY_DEATH` | `posX/Y/Z` `damageType` |
| `ENTITY_DAMAGE` | `target` `targetId` `targetUuid` `name` `dimension` `amountRaw` `amountPreMitigation` `amountFinal` `healthBefore` `healthAfter` `source` `outcome` `stoppedAt` |
| `PLAYER_INTERACT` | `button` `hand` `block` `meta` `blockState` `posX/Y/Z` |
| `ENTITY_INTERACT` | `hand` `posX/Y/Z` and the target's id and name |
| `ITEM_USE` | `hand` `held` `heldMeta` `posX/Y/Z` |

**Command-driven.** No category gates these; if the command ran, the record exists:

| Record | Written by |
| --- | --- |
| `SESSION_START` / `SESSION_STOP` | `session start` / `stop`. `SESSION_STOP` also when a different save opens, then carrying `reason=worldChanged` |
| `MARK` | `mark`, carrying `label` |
| `OPERATOR` | `session operator`, a disconnect, or a session stop: `action` (`bound` or `unbound`), `name`, `uuid`, `replaced` when rebinding, `reason` when unbound (`disconnect`, `sessionStop`) |
| `BUNDLE_START` | a bundle beginning, with `bundle`, `hash`, `commands`, `caller` (the sender's name), `channel` and `permissionLevel` (the level the toolkit checked, always 2). `channel` is `chat`, `console`, `commandBlock`, `join` (join automation), `inbox`, `nested` (run by another bundle) or `other` (a sender wrapped by `/execute` or a function) |
| `BUNDLE_END` | a bundle finishing, with `bundle` `hash` `executed` `failed` `tolerated` `total` `stoppedEarly` `durationTicks`, plus `senderLost` if the caller disconnected. A run cut off by a server stop writes it with `stoppedEarly=true discardedOnServerStop=true` |
| `TOLERATED` | one bundle command whose failure was tolerated, by a declared key or the built-in no-op set: `bundle` `hash` `index` `command` (after placeholder substitution) `key`. Written as that command ends, so it sits between `BUNDLE_START` and the records of the commands after it |
| `RELOAD` | `reload`, and every server start: `bundles` loaded, `problems` found, `trigger` (`command` or `serverStart`). The `ERROR` record of each problem comes before it |
| `SAVE` | `save`: `worlds` saved, `dimensions` (their ids, comma separated; Forge unloads an empty Nether or End, so the count varies), `durationMs` |
| `INBOX` | the command inbox: `action` (`opened`, `closed`, `claimed`, `refused`, `dispatched`, `failed`), `id` and `request` for a request, `operator`, and `reason` for closes, refusals and failures |
| `SCREENSHOT` | `screenshot`. On the server `action=requested` with `label` `delayTicks` `target` `requestTick` `file`. On the client, `side=CLIENT` with `label` `requestTick` and `action=saved` with `file`, or `action=failed` with `reason` (`queueFull`, `disconnected`, `noFile` with the expected `file`, or an exception name) |
| `CLIENT_ACTION` | `client`. On the server `action=requested` with `player` `step` `awaySeconds` `requestTick`, then `action=reported` with the client's `result` and `reason`. On the client, `side=CLIENT` with `step` `result` (`done`, `refused`, `failed`) `reason` `screen` `showing` |
| `BAUBLES` | `baubles set`: `action=set` `player` `slot` `slotType` `item` `count` `meta`, `replaced` when the slot held something, `outcome` (`set` or `rejected`). `baubles clear`: `action=clear` `player` `slots` (`all` or the index) `cleared` (how many held something) `outcome=cleared` |
| `BUNDLE_STEP` | one command of a bundle with `trace` on, just before it runs, with `bundle` `hash` `index` `command` `dueTick` `elapsedTicks` |
| `BUNDLE_SOURCE` | at load and reload: per file `source` `sha256` `bundles`; per bundle `bundle` `source` `hash`. The same `hash` is on that bundle's `BUNDLE_START` and `BUNDLE_END` |
| `AWAIT` | a wait ended: `condition` `outcome` (`satisfied` `timedOut` `cancelled` `pending`) `reason` `waitedTicks` `timeoutTicks`, and for `chunks` also `dimension` `chunks` `chunkCount` `loadedChunks` `firstUnloadedChunk` |
| `CHECKPOINT` | `action` of `arm`, `cue` or `ack` with `label`. Arm: `armedTick`, `timeoutTicks`, `operator`. Cue: `cueTick`, `form`, `sound`, `operator`. Ack: `askedTick`, `ackTick`, `latencyTicks`, and `answered` (`arm` or `cue`). An armed checkpoint that ends also writes an `AWAIT` with `condition=checkpoint`, `label`, `armedTick`, and when acknowledged `ackTick` and `latencyTicks` |
| `ASSERT` | One declared comparison: `predicate`, the scope fields it used, `expected`, `observed`, `outcome` of `pass`, `fail` or `unloaded`, and `note` when the outcome needs one. A verdict says only whether the observation matched the declared value; it never says the mod is right or wrong |
| `FIXTURE` | `action` of `claim`, `list` or `clear` with `handle` and `uuid`. Claim: `entity`, `tag`, `dimension`, `pos`. List: `state` (`loaded`, `unloaded`, `absent`, `unknown`), `fixtures`, last place. Clear: `outcome` (`removed`, `refused` with `reason=notOwned` and the entity's `tags`, or `notLoaded` with `state`) |
| `SCENARIO` | `action` of `checkpoint`, `load` (with `state` of `present`, `absent` or `corrupt` and `enforced`), `resume` (with `restoredFixtures`), `refuse` (with `reason` of `wrongBundle` or `changedHash`), `completed` or `abandoned`; with `scenarioId`, `expectedBundle`, `expectedHash`, `offeredBundle`, `offeredHash`, `operator`, `expectedStop`, `fixtures` as they apply |
| `ARENA_CREATE` / `ARENA_RESET` / `ARENA_CLEAR` | the matching `arena` action |
| `PLAYER_INSPECT` | `inspect player` |
| `ENTITY_INSPECT` | `inspect entity` |
| `BLOCK_INSPECT` | `inspect block`, with `tileEntityClass` when there is one |
| `INVENTORY_INSPECT` | `inspect inventory` and `inspect container`: `occupiedSlots`, `slot`, `item`, `count`, `meta`, `hasNbt` when the item has a tag. For a player also `section` (`main`, `armor`, `offhand`, `baubles`); a bauble slot adds `area=baubles` and `slotType`. A container with loot not yet rolled writes one record with `lootRolled=false` and `lootTable`, and no `occupiedSlots` |
| `ATTRIBUTE` | `inspect attributes`, one per attribute: `target` `targetUuid` `name` `base` `value` (the final value) `modifiers` (the count) |
| `ATTRIBUTE_MODIFIER` | `inspect attributes`, one per modifier, after its attribute: `target` `targetUuid` `attribute` `uuid` `name` `operation` `operationName` (`add`, `multiplyBase`, `multiplyTotal`) `amount` |
| `NBT` | `nbt`, carrying `truncated` always; for an entity also `lookup` (`selector`, `uuid`, `entityId`, `name`) and `dimension` |
| `ENTITY_LIST` | `entities nearby`, including when nothing matched |
| `ENVIRONMENT` | `environment`. Also once per launch on the client when client defaults apply: `side=CLIENT clientDefaultsApplied=true` with `brightnessWas` `brightnessNow` `musicWas` `musicNow`, and `<name>Was` `<name>Now` only for each optional default that was set: `master`, `weather`, `hostile`, `friendly`, `ambient`, `pauseOnLostFocus` |
| `CAPABILITIES` | `capabilities`, and one per mod for `mods` |
| `LOG_CONFIG` | any change to enabled categories, and `log status` |

**Toolkit-internal:**

| Record | Written when |
| --- | --- |
| `STARTUP` | once per launch, with version and bundle load result |
| `WORLD` | every server start, when the overworld loads, before any other toolkit record of that world: `folder` (the save folder name), `seed`, and `sameAsSession` when a session was active |
| `ERROR` | anything that failed, with enough context to identify the cause |

### Three shapes that break naive parsers

- **`ENTITY_DAMAGE` collapses three game events into one record.** One hit is one line. `amountPreMitigation` and `amountFinal` are omitted when the damage was cancelled before reaching those stages, and `stoppedAt` tells you where it stopped. Do not expect three lines.
- **`INVENTORY_INSPECT` emits one record per occupied slot**, and still emits one carrying `occupiedSlots=0` for an empty inventory. Counting records is not counting items.
- **`devtool mods` emits `CAPABILITIES`**, one per mod, rather than a type of its own.

## Reading the log

### An excluded event and an event that never happened look identical

Nothing is written when a category is off or a filter excludes something. This is the single most common way to reach a wrong conclusion here.

You do not need to ask anyone. Every change to the enabled set writes a `LOG_CONFIG` carrying the **full** set:

```
[DevToolkit][LOG_CONFIG] side=SERVER worldTick=1204 action=enable category=entity_spawn filter="radius=32.0 at 10.5,64.0,-3.5 dim=0" enabledCount=2 enabledCategories=block_place,entity_spawn filters="entity_spawn=radius=32.0 at 10.5,64.0,-3.5 dim=0"
```

Search backwards from the gap to the nearest `LOG_CONFIG`. `action` is `enable`, `disable`, `disableAll` or `status`.

`enabledCategories` is comma separated. **`filters` is semicolon separated**, because a filter description contains commas of its own.

### Prove the transition, not the final value

> A successful-looking final value is not evidence unless the log also proves the tested transition occurred from the intended initial state.

A test expecting a resource to end at 20 and observing 20 has established nothing if the resource never left 20. Design the check so the log shows 20, then 5, then 20.

Before trusting a "done":

1. the intended build and environment actually started (`STARTUP`, `ENVIRONMENT`)
2. each setup command **took effect**, not merely that it was sent (`BUNDLE_END` with `failed=0`, plus the specific record: `ARENA_RESET`, `LOG_CONFIG`)
3. a mark proves the person reached the action
4. a positive record proves the action occurred at all
5. the records after that mark concern the same entity and side
6. the result distinguishes the real behaviour from bypass paths: creative mode, a cancelled event, a disabled config, the wrong hand, an insufficient resource
7. no relevant `ERROR` was recorded

**Point 6 is where checks usually fail.** A player in creative mode, or a config flag left off from an earlier run, produces a log that looks exactly like the feature working. `devtool inspect player` in your setup bundle costs one line and rules out the most common one.

### `latest.log` is not a stable identifier

Minecraft renames it to a dated `.gz` archive **every time a process starts**. If a restart happens and you then read `latest.log`, you are reading a different file from the one your test wrote to, and you will conclude nothing was recorded.

A client and a dedicated server started from the same directory share `run/logs/`, so the second to start archives the first one's log, and while both run they write into one file: a burst of records from one process can truncate or overwrite lines from the other, so a record you cannot find may have been lost on the way to disk rather than never written. Re-run the bundle rather than conclude from its absence.

**The combined file can also be corrupted.** Two processes writing one Log4j file, on Windows in particular, can leave **NUL bytes** in it. One reported session had 6,377 NUL bytes in 43,542 retained bytes. Standard tooling then treats the file as binary and declines to search it, which looks exactly like "there are no records" rather than "this file needs different handling".

Running two processes from one directory:

- Read with a binary-tolerant tool. `rg -a` finds records where plain `rg` reports nothing.
- Retain **both** the current log and the dated archive. Neither alone holds the whole session.
- Attribute lines by thread, `[Server thread]` against `[Client thread]`, since both streams interleave.
- Use **separate directories** for client and server where the build allows it. That avoids all of the above.

The toolkit cannot fix this. It writes through the game's own logger, which is precisely what puts its records on one timeline with Forge and the mod under test.

So: read the log **before** anything restarts, and copy it to a scenario-named path if it needs to outlive the session. Hashing a file that is later overwritten proves nothing unless the bytes were kept too.

### Which process writes what

Observation is server-side. In single player everything lands in the one log. With a dedicated server, event records are on the **server**; command replies a person sees in chat are on the **client**. Two records are written by the client and carry `side=CLIENT`: `SCREENSHOT action=saved` or `failed`, and the `ENVIRONMENT` record of the client defaults. With a dedicated server they are in the client's log.

A client without the toolkit can still join a server that has it. Everything server-side works as usual for that player; only what needs the client fails: a screenshot to that player fails with `devtool.screenshot.noClient`, and client defaults are not applied.

### Worlds and sessions

Every server start writes one `WORLD` record with the save's `folder` and `seed`, before any other toolkit record of that world. It is how you tell which save a stretch of log belongs to.

A session never spans two saves. "The same save" means the same folder name and seed:

- **Same save reopened** in the same process: the session continues, and `WORLD` carries `sameAsSession=true`.
- **Different save opened**: `SESSION_STOP reason=worldChanged` is written first, after an `OPERATOR action=unbound reason=sessionStop` if an operator was bound, and then `WORLD sameAsSession=false` with no session. Nothing from the new save carries the old session's name. Fixtures are forgotten; their entities stay where they were.
- **No session active**: `WORLD` has no `sameAsSession`.

Two edge cases. A world deleted and recreated with the same name and seed counts as the same save. A brand-new world generates its spawn area before the `WORLD` record, so if `entity_spawn` is still on from the previous world, its first animals are recorded under the old session. Enabled logging categories are not tied to a save: they last for the process.

At every server start, after `WORLD`, the toolkit also re-reads bundles and configuration exactly as `devtool reload` does and writes `RELOAD trigger=serverStart`. On a dedicated server the load records (`BUNDLE_SOURCE`, and an `ERROR` for each load problem) therefore appear **twice** at start: once from the process starting, once from this reload, the second batch followed by the `RELOAD` record. Unless a file changed in between, the two batches are the same. In single player the first batch is at game start and the second when a world opens.

### What counts as a bundle failure

A command fails when the game raises an error: unknown command, bad syntax, missing permission, unparseable selector. **A command that runs and changes nothing has succeeded.**

Read `BUNDLE_END` rather than assuming. `executed`, `failed` and `stoppedEarly` are the fields that tell you whether your setup actually happened.

## Worked examples

### A. A block that should emit redstone when right-clicked

**Setup.** Arena, a known-good comparison, the block under test placed at a fixed offset, then logging armed last:

```json
{
  "feat_rsblock_setup": {
    "description": "One test block placed at a known position, interaction logging armed",
    "commands": [
      "devtool session start rsblock",
      "devtool arena reset",
      "gamerule doMobSpawning false",
      "setblock ~2 ~ ~ yourmod:redstone_block",
      "setblock ~2 ~-1 ~1 minecraft:redstone_lamp",
      "devtool inspect player",
      "devtool inspect block ~2 ~ ~",
      "devtool log player_interaction on arena",
      { "command": "devtool mark READY_RIGHT_CLICK", "delayTicks": 20 }
    ]
  }
}
```

**Ask a person for:** right-click the block at 2 blocks east of where the bundle put you. Nothing else.

**Then read back:**

- `PLAYER_INSPECT`. Is `gameMode` what you expected? Creative would invalidate the run.
- `BLOCK_INSPECT` before the mark. The block is there, and `blockState` shows its powered property in the **off** state. This is your initial state, and without it a lamp that was already lit proves nothing.
- `MARK label=READY_RIGHT_CLICK`
- `PLAYER_INTERACT` with `button` and `posX/Y/Z` matching the block. If this is missing, the click never reached the server and the rest of the test is void.

**Second pass to prove the transition:** ask for a second `devtool inspect block ~2 ~ ~` after the click, and compare `blockState`. You now have off → interact → on, from records, in order.

**What would fool you:** a lamp lit by daylight, an already-powered block from a previous run (which `arena reset` prevents), or a right-click that fired on the off-hand and did nothing. The toolkit suppresses the duplicate hand event, so one click is one `PLAYER_INTERACT`; two records means two clicks.

### B. An item that should deal damage on use

```json
{
  "feat_damage_item_setup": {
    "description": "One named, immobile target and a single test item",
    "commands": [
      "devtool session start damage_item",
      "devtool arena reset",
      "gamerule doMobSpawning false",
      "clear @p",
      "give @p yourmod:test_wand 1",
      "summon minecraft:zombie ~4 ~ ~ {CustomName:\"probe\",NoAI:1,PersistenceRequired:1}",
      "devtool inspect entity @e[name=probe]",
      "devtool log entity_damage on arena",
      "devtool log entity_death on arena",
      { "command": "devtool mark READY_STRIKE", "delayTicks": 20 }
    ]
  }
}
```

`NoAI:1` matters: a target that wanders leaves the arena filter and its damage stops being recorded. `clear @p` first means `nbt held` and the `ITEM_USE` `held` field cannot report a leftover item from an earlier run.

**Ask a person for:** hit the zombie named `probe` once with the item you were given.

**Then read back the single `ENTITY_DAMAGE`:**

```
[DevToolkit][ENTITY_DAMAGE] side=SERVER worldTick=... session=damage_item sessionTick=... target=minecraft:zombie targetId=... name=probe dimension=0 amountRaw=5.00 amountPreMitigation=5.00 amountFinal=3.00 healthBefore=20.00 healthAfter=17.00 source=player outcome=... stoppedAt=...
```

This is the record that repays reading carefully, because the three amounts localise the discrepancy for you:

- `amountRaw` differs from what you expected → **your mod's calculation** is wrong.
- `amountRaw` is right but `amountFinal` is lower → armour, resistance or another mitigation, not your code.
- `amountPreMitigation` and `amountFinal` are **absent** → the damage was cancelled before those stages; `stoppedAt` names where.
- `healthAfter` disagrees with `healthBefore - amountFinal` → something outside the three stages changed the health, which is itself worth knowing. `healthAfter` is read, not computed, precisely so it can disagree.

**What would fool you:** invulnerability ticks swallowing a second hit, a target already damaged from a previous run, or the item's damage coming from the vanilla attack rather than your feature. The `source` field distinguishes the last one.

### C. A mob with custom drops

```json
{
  "feat_mob_drops_setup": {
    "description": "One named mob, empty inventory, item spawn logging on",
    "commands": [
      "devtool session start mob_drops",
      "devtool arena reset",
      "gamerule doMobSpawning false",
      "gamerule doMobLoot true",
      "clear @p",
      "devtool inspect inventory",
      "summon yourmod:test_mob ~4 ~ ~ {CustomName:\"probe\",PersistenceRequired:1}",
      "devtool log entity_death on arena",
      "devtool log entity_spawn on arena",
      { "command": "devtool mark READY_KILL", "delayTicks": 20 }
    ]
  }
}
```

This one needs a configuration change: dropped items and experience orbs are **excluded from `entity_spawn` by default**, because one mob death produces a burst of both. Set `spawnIncludeItems` in `run/config/devtool/devtool.cfg` and have the person run `/devtool reload`. Drop timing is exactly the case that option exists for.

**Ask a person for:** kill the mob named `probe`, then stand still for five seconds.

**Then read back:**

- `INVENTORY_INSPECT` with `occupiedSlots=0` before the mark. This is the initial state, so a drop cannot be confused with something already carried.
- `ENTITY_DEATH` with `damageType`
- one `ENTITY_SPAWN` per dropped item, after the death
- a second `devtool inspect inventory` after pickup, to prove the items were real and reachable

**What would fool you:** `doMobLoot` left false, drops falling outside the arena filter, or a despawn before pickup. The standing-still instruction is not politeness; it is what separates "nothing dropped" from "it dropped and vanished".

### D. A wait instead of a guessed delay

You want to build something 600 blocks away. A `delayTicks` after the teleport is a guess about how long chunks take to load; a wait is a fact.

```json
{
  "feat_far_platform": {
    "description": "Teleport far away, wait for the region, build, come back",
    "trace": true,
    "commands": [
      "devtool session start far_platform",
      "devtool mark FAR_BEGIN",
      "tp @p ~600 80 ~",
      "devtool await chunks ~-24 ~-24 ~24 ~24 200",
      "fill ~-2 78 ~-2 ~2 78 ~2 minecraft:stone",
      { "command": "tp @p ~-600 100 ~", "delayTicks": 20 },
      "devtool mark FAR_END"
    ]
  }
}
```

**Ask a person for:** run it and stand still until the second marker appears in chat.

**Then read back:**

- `AWAIT condition=chunks outcome=satisfied waitedTicks=...`. Above zero means the wait did something a delay could not have known about.
- `BUNDLE_STEP` records, because `trace` is on: the `fill` ran on the tick the wait ended, not before.
- `BUNDLE_END failed=0`.

**What would fool you:** a return teleport written as `~-600 ~ ~`. The person fell while the wait ran, so "the same height" is now the ground 600 blocks away, which at the start point may be inside rock. Give the return an absolute height. And a wait on a region nobody is near never becomes ready: `outcome=timedOut` with `firstUnloadedChunk` is the wait telling you that, and the command fails with `devtool.wait.timeout` so the bundle stops there instead of filling nothing.

### E. A checkpoint around an action only a person can perform

Your feature reacts to a key press. You need the records from after the press, and you need to know when the press happened.

```json
{
  "feat_keypress_probe": {
    "description": "Cue the person, arm a checkpoint, record what happens after they answer",
    "commands": [
      "devtool session start keypress",
      "devtool session operator @s",
      "devtool log item_use on radius 8",
      "devtool mark ARMED",
      "devtool checkpoint cue PRESS_H_NOW actionbar sound",
      "devtool checkpoint arm PRESS_H_THEN_ACK 600",
      "devtool inspect player ${operator.name}",
      "devtool mark AFTER_ACK"
    ]
  }
}
```

**Ask a person for:** when the label shows, press H once, then type `/devtool checkpoint ack`. Nothing else.

**Then read back:**

- `CHECKPOINT action=cue label=PRESS_H_NOW cueTick=...` and `CHECKPOINT action=arm label=PRESS_H_THEN_ACK armedTick=...`
- Your mod's own records, and any `ITEM_USE`, between `armedTick` and `ackTick`. That window is the whole point: everything inside it happened after the cue and before the person said they were done.
- `CHECKPOINT action=ack latencyTicks=...` and `AWAIT condition=checkpoint outcome=satisfied`.
- `PLAYER_INSPECT` after the ack, with whatever state the press should have changed.

**What would fool you:** a person who pressed the key before the label appeared. `armedTick` is the boundary; records before it belong to setup. A person who never answered: `outcome=timedOut`, the command fails with `devtool.wait.timeout`, and the marker `AFTER_ACK` is absent, which is what tells you no answer came rather than an answer with no effect. Only one checkpoint can be armed at a time; a second arm is refused with an `ERROR` record.

### F. An assertion, and a result file to read it from

You want a yes or no about a count, written by the toolkit, without grepping.

```json
{
  "feat_spawner_count": {
    "description": "One summon, then the count the feature should leave behind",
    "result": true,
    "commands": [
      "devtool session start spawner_count",
      "devtool session operator @s",
      "devtool mark BEGIN",
      "summon minecraft:pig ~3 ~1 ~ {CustomName:\"probe\",NoAI:1b,PersistenceRequired:1b}",
      "devtool assert entities count 1 within 12 name probe",
      "devtool assert player ${operator.name} gamemode creative",
      "devtool mark END"
    ]
  }
}
```

**Ask a person for:** run it.

**Then read back** `run/config/devtool/results/feat_spawner_count-<startTick>.json`: `assertions` holds one entry per `ASSERT` record with `predicate`, `expected`, `observed` and `outcome`, and `failed` is the bundle's own counter. The same records are in `latest.log`; the file is a convenience, and the log is the authority.

**What a verdict means.** `outcome=pass` says the observation matched the declared value, and `outcome=fail` says it did not. Neither says the mod is right. A count of one pig proves the summon worked; whether your feature should have left one is your judgement, made before you wrote the number down. A failing assertion stops the bundle, because `stopOnFailure` treats `devtool.assert.failed` like any other failure; tolerate that key on the command when a failure is an acceptable answer rather than a broken setup.

**Absent is not a verdict until the place is loaded.** `assert entity <uuid> absent` on an unloaded chunk reports `outcome=unloaded` and fails with `devtool.assert.unloaded`, never `pass`. Give it `at <x> <y> <z>`, or use a fixture, so the toolkit knows where to look.

### G. A fixture you can find again and remove exactly

You summon something, and later you need that one entity, not whatever else shares its name.

```json
{
  "feat_target_setup": {
    "description": "Summon one target and claim it as this session's fixture",
    "commands": [
      "devtool session start target",
      "devtool session operator @s",
      "summon minecraft:pig ~3 ~1 ~ {CustomName:\"target\",NoAI:1b,PersistenceRequired:1b}",
      "devtool fixture claim target @e[name=target]",
      "devtool nbt entity ${fixture.target.uuid}",
      "devtool fixture list",
      "devtool mark READY"
    ]
  },
  "feat_target_teardown": {
    "description": "Remove only what this session claimed",
    "stopOnFailure": false,
    "commands": [
      "devtool fixture clear all",
      "devtool session stop"
    ]
  }
}
```

**Then read back:**

- `FIXTURE action=claim handle=target uuid=... tag=devtool.fx.target.target`. The tag is a vanilla scoreboard tag on the entity, so `@e[tag=devtool.fx.target.target]` selects it from chat too.
- `NBT lookup=uuid`, found through the placeholder, in whichever dimension it stands.
- `FIXTURE action=list state=loaded`. After the person walks far away, `unloaded`; after the entity is gone from a loaded place, `absent`.
- On teardown, `FIXTURE action=clear outcome=removed` with the UUID, one per fixture.

**What would fool you:** a fixture standing in the world's spawn chunks. A server never unloads those, so `unloaded` never appears there and an absence assertion there is a real verdict at once. Also: ownership is the tag, not the name. If something removed the tag, `clear` writes `outcome=refused reason=notOwned` and leaves the entity alone, and a second pig named `target` is never touched because it was never claimed.

### H. A scenario that survives a restart

Your feature does something at world load. You need a bundle before the stop, a bundle after the start, and a guarantee that the second one is the one that runs.

```json
{
  "feat_reload_setup": {
    "description": "Before the restart: claim the fixture, then declare what runs next",
    "commands": [
      "devtool session start reload",
      "devtool session operator @s",
      "summon minecraft:pig ~3 ~1 ~ {CustomName:\"kept\",NoAI:1b,PersistenceRequired:1b}",
      "devtool fixture claim kept @e[name=kept]",
      "devtool scenario checkpoint reload next feat_reload_resume clean",
      "devtool mark STOP_THE_SERVER_NOW"
    ]
  },
  "feat_reload_resume": {
    "description": "After the restart. Does not start a session; the checkpoint restores it",
    "commands": [
      "devtool mark RESUMED",
      "devtool fixture list",
      "devtool assert entity ${fixture.kept.uuid} present",
      "devtool scenario complete reload"
    ]
  }
}
```

**Ask a person for:** run the setup, stop the server, start it, join, run the resume.

**Then read back:**

- `SCENARIO action=checkpoint expectedBundle=feat_reload_resume expectedHash=... fixtures=1` before the stop.
- `SCENARIO action=load state=present enforced=true` right after the start, before anyone typed anything. On a different world it says `state=absent`; on unreadable data `state=corrupt enforced=false`, and nothing is refused.
- `SCENARIO action=resume restoredFixtures=1`, then the session's own records continue under the old session name with the operator bound again, then `FIXTURE action=list state=loaded` for an entity that existed before the restart.
- `SCENARIO action=completed`.

**What would fool you:** running any other bundle first. It is refused with `devtool.scenario.mismatch` and a `SCENARIO action=refuse reason=wrongBundle` naming both. Editing the resume bundle between stop and start is refused the same way with `reason=changedHash` and both hashes, because the checkpoint pins the text you declared, not the name. `devtool scenario abandon reload` clears it when you meant to change course. The resume bundle must not start a session of its own; the restore happens before its first command.

### I. An agent-run session through the inbox

You want to prepare a test, have the person act once, and inspect the result, without asking anyone to type anything after the first command.

**Once, by the owner:** `inbox.enabled` set to true, the world opened, `/devtool arena create` typed where the test should happen (once per world: the arena is stored in the save, and `arena reset` fails with "No arena in dimension N" without it), and `/devtool inbox open` typed in chat. In single player, if the owner will switch windows while you work, ask for `client.applyOnJoin=true` and `client.noPauseOnLostFocus=true` before the game is launched: the client defaults apply only at the first world join of a launch, and `applyOnJoin` also sets brightness and music to their configured values (1.0 and 0 by default).

**The bundle**, written before the world opened, or followed by a `reload` request if written after:

```json
{
  "feat_wand_inbox_setup": {
    "description": "Arena, one named target, damage logging armed; started through the inbox",
    "result": true,
    "commands": [
      "devtool session start wand_inbox",
      "devtool session operator @s",
      "devtool arena reset",
      "kill @e[name=probe]",
      "summon minecraft:zombie ~4 ~ ~ {CustomName:\"probe\",NoAI:1,PersistenceRequired:1}",
      "devtool log entity_damage on arena",
      "devtool checkpoint cue HIT_PROBE_ONCE actionbar sound",
      "devtool mark READY_STRIKE"
    ]
  }
}
```

Run through the inbox, `@s` is the inbox operator, so the operator binding works exactly as from chat. `arena reset` removes every non-player entity inside the arena, a probe from an earlier run included, so the `kill` after it matches nothing as long as no other entity named `probe` stands in a loaded chunk outside the arena. That is tolerated, and shows up as a `TOLERATED` record.

**Your steps:**

```
1. read    run/config/devtool/inbox/status.json          must exist, names the operator and world
2. write   run/config/devtool/inbox/w-001.tmp            run feat_wand_inbox_setup
   rename  w-001.tmp to w-001.req
3. wait    run/config/devtool/inbox/w-001.result.json
```

For example, in a shell:

```bash
cd run/config/devtool/inbox
printf 'run feat_wand_inbox_setup\n' > w-001.tmp && mv w-001.tmp w-001.req
```

The answer, once the bundle has ended:

```json
{
  "schemaVersion": 1,
  "id": "w-001",
  "request": "run feat_wand_inbox_setup",
  "verb": "run",
  "outcome": "done",
  "bundle": "feat_wand_inbox_setup",
  "hash": "...",
  "executed": 8,
  "failed": 0,
  "tolerated": 1,
  "total": 8,
  "stoppedEarly": false,
  "durationTicks": 1
}
```

**Ask a person for:** when the cue shows, hit the zombie named `probe` once, then say so.

**Then write two more requests**, each with a new id, each through `.tmp`:

```
w-002   inspect entity @e[name=probe]
w-003   screenshot after_strike
```

**Then read back:**

- `INBOX action=opened operator=...`, then `INBOX action=claimed id=w-001`.
- `BUNDLE_START bundle=feat_wand_inbox_setup caller=<operator> channel=inbox permissionLevel=2`. This is what proves the run came through the inbox and as whom.
- `TOLERATED key=commands.generic.selector.notFound` for the `kill`, `MARK label=READY_STRIKE`, then `BUNDLE_END failed=0 tolerated=1` with the same counters as the result file.
- `ENTITY_DAMAGE` after the mark, read as in example B.
- `w-002.result.json` with `outcome=done`, and the `ENTITY_INSPECT` it caused.
- `SCREENSHOT action=requested label=after_strike`, then `SCREENSHOT side=CLIENT action=saved file=screenshots/after_strike-<tick>.png`, and the image itself in the client's `screenshots` folder.

**What would fool you:** reusing `w-001` for the second request. It is never run, and it gets only an `INBOX action=refused reason=duplicateId` record, no result file, so a loop waiting for that file waits forever: wait with a timeout, and look for that record when it runs out. A request written before the owner opened the inbox is refused with `placedBeforeOpen`. A bundle edited after the world opened, with no `reload` request, runs the old text; the `hash` in the result file and on `BUNDLE_START` tells you which text ran. A missing answer in single player may only mean the game is paused. If nobody created the arena, `arena reset` fails, the bundle stops there, and the result says `outcome=failed reason=bundleFailed` with `firstFailure` naming the missing arena. And `outcome=done` says the setup ran, not that the strike happened: only the `ENTITY_DAMAGE` after the mark says that.

## Limits

**It does not play the game.** A person performs every gameplay action. The toolkit prepares, records, and reports. It never presses a button.

**It does not know about your mod.** Every record is generic: blocks placed, entities damaged, items used. A modded tile entity's class is named; its contents are not interpreted, because interpreting them would require knowing how that mod works.

**It is not a substitute for your mod's own logging.** It can tell you 3 damage was dealt, by whom, to what, and when. It cannot tell you which branch of your damage calculation ran. **Most of what you need to establish about your own feature is a decision your code made, and only your code can record that.** Add logging to the mod under test and read both in the same file; that colocation is the point.

**It does not judge your mod.** Records are facts. An assertion compares one observation with one value you declared, and its verdict says only that the observation matched or did not match that value. It never says the mod is right or wrong, and it cannot know what the value should have been. Deciding is your job.

**A dense log is not discriminating evidence.** If you design a test around the facts this toolkit happens to record, rather than around the facts that would separate a pass from the ways a failure can look like a pass, you will get a long log that proves nothing.
