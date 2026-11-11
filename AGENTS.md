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
            (or nothing, when inbox.openOnLocalJoin opens it at the owner's own join)
2. WRITE    run/config/devtool/bundles/<yourfile>.json
3. WRITE    run/config/devtool/inbox/<id>.tmp, then rename it to <id>.req
4. WAIT     for run/config/devtool/inbox/<id>.result.json
5. ASK      person performs the actions you listed, in order
6. READ     the result file and run/logs/latest.log
```

The inbox runs only bundles and read-only toolkit commands, never a vanilla command. See [The command inbox](#the-command-inbox) and worked example I.

### The loop from the title screen

When the owner has also enabled the client request channel and the inbox's opening at a local join, the owner only launches the game:

```
1. ASK      person launches the game
2. WAIT     for run/config/devtool/client-inbox/status.json with a startedAtMillis from this launch
3. WRITE    run/config/devtool/client-inbox/<id>.req (through .tmp):  create <name> ...
4. WAIT     for its result, then for run/config/devtool/inbox/status.json
5. WRITE    inbox requests as above, the last one:  client quit
6. READ     the result files, and run/logs/latest.log once the game has exited
```

The client request channel takes menu steps only: create, open, connect, quit. In a world, the command inbox is the channel. See [The client request channel](#the-client-request-channel) and worked example K.

### Paths

| What | Where |
| --- | --- |
| Bundles you write | `run/config/devtool/bundles/*.json` (scanned recursively) |
| Configuration | `run/config/devtool/devtool.cfg` |
| Result files | `run/config/devtool/results/<bundle>-<startTick>.json` |
| Inbox requests and answers | `run/config/devtool/inbox/` |
| Client request channel requests and answers | `run/config/devtool/client-inbox/` of the client |
| Local-join secret (dedicated server only) | `run/config/devtool/local-join.secret` |
| Screenshots on request | `run/screenshots/` of the client that took them |
| The log you read | `run/logs/latest.log` |

On a dedicated server these live under the **server's** directory, not the client's, except screenshots and the client request channel, which belong to the client. A client-side copy of a bundle or a request has no effect on what the server runs.

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
- **The toolkit has failure keys of its own**, in the `devtool.` namespace, and you tolerate them the same way: `devtool.wait.timeout` when a wait ran out of time, `devtool.wait.cancelled` when it became impossible, such as the caller changing dimension mid-wait. `devtool.placeholder.unbound` when a placeholder has no value yet, `devtool.placeholder.unsafe` when its value could break the command. `devtool.assert.failed` when an assertion did not match, `devtool.assert.unloaded` when an absence could not be judged because the place is not loaded, `devtool.assert.ambiguous` when a modifier name matches several modifiers. `devtool.scenario.mismatch` when a scenario checkpoint expects a different bundle, or this bundle with different text. `devtool.inspect.notLoaded` when an inspection names a position whose chunk is not loaded. `devtool.save.failed` when `devtool save` could not save a world. `devtool.screenshot.noClient` when the target player's client does not have the toolkit. `devtool.client.noClient` when your own client does not have the toolkit, 1.4.0 or later (for `quit`, a client that allows actions but said hello with a version older than 1.5.0; for a player action or a reconnect's `logout`, a client whose hello says it is older than 1.7.0), for a `devtool client` step, `devtool.client.notAllowed` when it has not allowed client actions, `devtool.client.notDead` when a respawn is asked of a living player, `devtool.client.dead` when a click, hold, drop or tooltip is asked of a dead player, `devtool.client.busy` while your client is on a reconnect, from the request until it is back and reported. `devtool.baubles.absent` when Baubles is not installed, `devtool.baubles.rejected` when a bauble slot refuses the item. From 1.8.0, `devtool.find.unknownBlock` when `find block` names no block, `devtool.find.badState` when the block cannot have the state asked, `devtool.find.tooLarge` when the box covers more than 1024 chunks. That is the whole list; tolerate any of them on the one command that may fail that way.
- **Some refusals carry no key.** A screenshot label with a forbidden character fails with an `ERROR` record and a message, not a key, so a bundle cannot tolerate it by key. Fix the label instead.
- **Placeholders.** Bind the human once with `devtool session operator @s`, then write `${operator.name}`, `${operator.uuid}` or `${operator.selector}` in any command; a claimed fixture is `${fixture.<handle>.uuid}` once `devtool fixture claim` has named it. That is the whole list. An unknown placeholder fails the file at reload. A value is filled in when the command runs, and a command whose value is missing fails with `devtool.placeholder.unbound` instead of running with a blank.
- **Result files.** Put `"result": true` on a bundle and each run writes `run/config/devtool/results/<bundle>-<startTick>.json`: the `BUNDLE_END` counters including `tolerated`, then assertions with outcomes, failures, markers, checkpoints, waits, and `toleratedSteps` with one entry per `TOLERATED` record, all copied from the log records. From 1.6.0 it also says which run it is: `channel`, `caller` and, for a run an inbox request started (or a bundle nested in one), that request's `requestId`, all from `BUNDLE_START`; and `startedAtMillis` (wall-clock milliseconds, a number) and `world` (the save folder, left out when there is none), noted when the run started and carried by no record. Two runs of a bundle at the same tick share the file name, and the later overwrites the earlier; these fields tell you which one you are reading. The inbox answer of a `run` names the file as `resultFile` once it is written. Read that when you want a run's verdicts without grepping; read the log when you want everything.
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

Nine categories (`item_pickup` from 1.8.0). **All are off by default**, and a category that is off produces no records at all.

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

Dimensions are the **interior**: `create 20 10 20` gives twenty blocks to walk in. One arena per dimension, stored in the world save, so it survives a restart. Lighting is in the floor, not the ceiling, so it works at any height and nothing hostile spawns inside, except slimes, which ignore light (in a slime chunk below y 40).

`arena reset` at the top of every setup bundle is the single highest-value habit here. It is what makes two runs comparable.

Creating an arena also moves the caller's respawn point to its start position. Minecraft revalidates that on death and silently falls back to world spawn if the position has become obstructed, reporting **"Your home bed was missing or obstructed"**, which is misleading, since no bed is involved. If a person reports respawning far away after seeing that, the arena start was blocked.

Arena commands place blocks without emptying containers first. A loot chest inside the arena's volume therefore rolls its loot table and drops the items when `create`, `reset` or `clear` replaces it. Build loot tests outside the arena, or place the chest after the reset.

### Keeping test mobs and the player alive

A card with a hostile mob fails for the wrong reason when the mob burns in daylight or kills the player before the step the card is about. None of this needs a toolkit command:

- **An undead mob that does not burn.** A helmet in its head slot stops daylight burning, as long as the helmet lasts: the sun wears it down, and a plain leather helmet breaks after about two minutes, so make it unbreakable: `summon minecraft:zombie ~4 ~ ~ {PersistenceRequired:1b,ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",Count:1b,tag:{Unbreakable:1b}}]}` (the list is feet, legs, chest, head). Or give it Fire Resistance after summoning it: `effect @e[type=minecraft:zombie,r=8] minecraft:fire_resistance 1000000 0 true`; it still catches fire and looks it, only the damage stops, and a burning zombie can set the player alight. Or avoid the sun: `time set night` with `gamerule doDaylightCycle false`. A modded mob may burn by its own rule; check that one in game.
- **A player who survives the mob.** `effect @s minecraft:resistance 300 4 true` (level 5; each level takes 20 percent off, so level 5 takes all of it, though not damage from falling out of the world or from starving) and `effect @s minecraft:instant_health 1 4 true` before the mob arrives. A death in the middle of a card loses the state the card set up: drops, position, respawn point.
- **A clean test world.** A world the client request channel created or opened can fill with mobs while nobody is looking, and a flat world spawns slimes in its slime chunks (about one chunk in ten) below y 40, at any light level, `structures=false` or not. Start the first bundle of a launch with `gamerule doMobSpawning false`, a moment of `difficulty peaceful` (it removes every hostile mob in the loaded chunks, slimes included, over the next ticks; mobs saved in unloaded chunks come back with them, and a hardcore world stays hard), then the card's own difficulty, and remove the dropped items and orbs. A `kill` that finds nothing is already tolerated (`commands.generic.selector.notFound`):

```json
{
  "world_prepare": {
    "description": "No spawning, no hostile mobs, no drops, a healed player",
    "stopOnFailure": true,
    "commands": [
      "gamerule doMobSpawning false",
      "difficulty peaceful",
      { "command": "difficulty normal", "delayTicks": 20 },
      "kill @e[type=minecraft:item]",
      "kill @e[type=minecraft:xp_orb]",
      "effect @s minecraft:instant_health 1 4 true",
      "devtool mark WORLD_PREPARED"
    ]
  }
}
```

### Session and marks

| Command | Effect |
| --- | --- |
| `devtool session start <name>` | Stamp every subsequent record with a name and a relative tick |
| `devtool session stop` | End it |
| `devtool session status` | Report the active session and its operator, if bound |
| `devtool session operator <player>` | Bind exactly one online player as the session's operator, by name or selector. Zero or several matches are refused. The binding ends when the session stops or the player disconnects, and every change writes an `OPERATOR` record |
| `devtool mark <label>` | Write a labelled marker record. The chat reply repeats the label, so an operator can tell which marker fired |

Start a session in every setup bundle. Without one you are searching a log; with one you are reading a slice of it.

A session lives in memory. `session stop` forgets the session's fixture handles, but the tags stay on the entities, so a new session of the same name finds and adopts them again on its first `fixture list` or `fixture claim` (see the fixture rows below). A full restart of the game or the server ends the session; only a scenario checkpoint restores one.

A session belongs to one save. Leaving to the title screen and reopening the same save keeps it. Opening a different save stops it first, with `SESSION_STOP reason=worldChanged`, which also ends its operator binding and forgets its fixtures. See [Worlds and sessions](#worlds-and-sessions).

**Marks are how you tell "the action produced nothing" from "they never got that far."** When you ask a person for a sequence of actions, put a mark before each one. Then a missing record is informative instead of ambiguous.

### Inspection

| Command | Effect |
| --- | --- |
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects, and `openContainer`: `inventory`, or the class of another container the server has open for the player. The server cannot tell the player's own inventory screen from no screen: both are `inventory` |
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
| `devtool assert entity <uuid> present\|absent [at <x> <y> <z>]` | Whether a loaded entity has that UUID. A dying entity (health at zero, in its death animation) counts as absent, with `dead=true` on the record. Absent is only a verdict when the place it should be is loaded: give `at`, or use a claimed fixture, otherwise the outcome is `unloaded` with `devtool.assert.unloaded` |
| `devtool assert entity <uuid> dimension <d>` and `within <r> of <x> <y> <z>` | Where a loaded entity is |
| `devtool assert player <name> dimension <d>` and `gamemode <mode>` | Where a player is and how they play |
| `devtool assert attribute <target> <attribute> value <n>` | An attribute's final value, compared at two decimals. `<target>` is a selector or UUID matching exactly one living entity; `<attribute>` is the registry name, such as `generic.armor` |
| `devtool assert attribute <target> <attribute> modifier <uuid\|name> [amount <n>]` | Whether the attribute has that modifier, and with `amount` its amount. A name may hold spaces: every word up to `amount` is the name. A name that matches several modifiers fails with `devtool.assert.ambiguous` instead of choosing one; vanilla gives all four armour pieces the name `Armor modifier`, so assert those by UUID. A target that matches nothing, several entities, or a non-living entity fails before any comparison and writes no `ASSERT` |
| `devtool fixture claim <handle> <selector\|uuid\|entityId>` | Mark exactly one non-player entity as this session's fixture under a handle, by adding the tag `devtool.fx.<session>.<handle>`. A handle is letters, digits, `_` and `-`, no dot; in the tag, every other character of the session name (a `.` or a space included) becomes `_`. Claiming, under a handle the session does not hold yet, an entity that already carries this session's tag for that handle adopts it (`adopted=true`), as after a `session stop`. Refused: a handle the session already holds, even for the same entity (so after a `fixture list` has refilled the handles, a `claim` of the same fixture fails), a handle another entity holds, an entity that is already this session's fixture under another handle, and a handle whose cached fixture is unloaded while a loaded entity carries the tag; the refusals name the other side and have no key. From then on `${fixture.<handle>.uuid}` names it |
| `devtool fixture list` | Each fixture of the session as `loaded`, `unloaded`, `absent`, `removing` (dying or being removed), `untagged` (found, but its tag is gone) or `duplicate` (two living entities carry the tag, listed as `carriers`), judged at its last known place. A server never unloads its spawn chunks, so a fixture standing there is always `loaded` |
| `devtool fixture clear <handle\|all>` | Remove fixtures whose entity still carries this session's tag, without a death: no loot, no experience, no death events, riders left in the world. The `FIXTURE outcome=removed` record is written once the removal is confirmed, a tick later, with `confirmedTick`. An entity without the tag is refused (`notOwned`), a duplicate is refused, an unloaded fixture is `notLoaded`, a dying one `alreadyRemoving`; a removal not yet confirmed when the server stops is `unconfirmed reason=serverStopped` |
| `devtool fixture release <handle\|all>` | Take the tag off and forget the handle, leaving the entity in the world. Refused for an entity without the tag (`notOwned`) and for a duplicate; `notLoaded` for an unloaded fixture |
| `devtool scenario checkpoint <id> next <bundle> [clean\|fatal]` | Store, inside the world save, that `<bundle>` must be the next bundle run: its name and hash, the session, its operator, and its fixtures with their last places. Until it runs, `devtool run` of any other bundle, or of that bundle with changed text, is refused with `devtool.scenario.mismatch` naming both sides. Bundles the allowed bundle runs inside itself are not checked again |
| `devtool scenario status` | The standing checkpoint, or none, or corrupt |
| `devtool scenario complete <id>` and `abandon <id>` | Clear it, with a record. The resume bundle must not start a session itself; the resume restores the stored one, its operator and its fixtures before the bundle runs |
| `devtool reload` | Re-read bundles and configuration from disk. Writes `RELOAD trigger=command`. The same reload runs at every server start with `trigger=serverStart` |
| `devtool save` | Save player data and every loaded world, on single player and dedicated servers alike. Leaves each world's saving flag as it found it, so a `save-off` typed earlier stays off. Writes `SAVE`. A failure writes an `ERROR` and fails with `devtool.save.failed`. Vanilla `save-all` exists on a dedicated server only and needs level 4; this needs level 2 |
| `devtool screenshot <label> [delayTicks] [player]` | Ask a player's client to save a screenshot after the delay. The label is 1 to 48 letters, digits, `_` or `-`; the delay is 0 to 600 ticks, default 0. The target is the named player, else the sender, which in a bundle is its caller and through the inbox is the inbox operator. The client must have the toolkit installed, or the command fails with `devtool.screenshot.noClient` and nothing is sent |
| `devtool client reconnect [awaySeconds] [logout]\|respawn\|close\|quit` | Ask your own client to leave and come back, press Respawn on the death screen, close the open screen, or close the game. See [Client actions](#client-actions) |
| `devtool client select <0-8>\|use\|attack\|hold <use\|attack> <ticks>\|drop [all]\|inventory\|tooltip <0-40> [advanced] [shift] [ctrl] [alt]`, each with `[delay=<ticks>] [screenshot=<label>] [screenshotDelay=<ticks>]` | Ask your own client to hold a hotbar slot, right click once, left click once, keep a mouse button down for a time, drop the held item, open the inventory, or show an item's tooltip. See [Player actions](#player-actions) |
| `devtool inbox open\|close\|status` | Open, close or report the command inbox. See [The command inbox](#the-command-inbox) |
| `devtool bundle list` | Loaded bundles and their command counts |
| `devtool bundle show <name>` | One bundle's commands in order, with delays |
| `devtool capabilities` | Version, commands, inspection types, logging categories |
| `devtool environment` | Minecraft and Forge versions, dimension, difficulty, position |
| `devtool mods` | Every loaded mod id and version |

`capabilities` is read from the live command registry and the category enum, never from a written list, so it cannot claim a feature the jar does not have. Use it when you need to know what build is actually running.

`devtool run` refuses a call that does not come from the server thread. RCON runs its commands on its own thread, so start bundles from chat, the server console or the inbox instead.

**Screenshots.** The server writes `SCREENSHOT action=requested` with the file name it asked for, `screenshots/<label>-<requestTick>.png`, where `requestTick` is the target's world tick when the request was made. The client writes `SCREENSHOT side=CLIENT action=saved` with the file once it is on disk, or `action=failed` with a `reason`. The capture is of the whole window, an open inventory or menu included, so pair it with a `checkpoint cue` that tells the person what to have on screen. At most 8 screenshots wait on a client at once; more fail with `reason=queueFull`, and waiting ones fail with `reason=disconnected` when the player leaves the world. In a dedicated-server setup the file is on the client's computer, not the server's.

### Client actions

Four steps a person used to do by hand, so a test can run them from a bundle or the inbox:

| Command | What the client does |
| --- | --- |
| `devtool client reconnect [awaySeconds] [logout]` | Leaves the world, waits `awaySeconds` (0 to 60, default 0) at the menu, and comes back to the same place: the same server entry on a dedicated server, the same save in single player, saved in full before it stopped. With `logout` (from 1.7.0), a single-player leave logs the player out as Save and Quit does; see [A reconnect and the logout](#a-reconnect-and-the-logout) |
| `devtool client respawn` | Presses Respawn on the death screen, once its buttons are enabled (20 ticks). Waits up to 100 ticks for a death screen still opening |
| `devtool client close` | Closes the open screen the way Escape does, telling the server when it is a container. A tick later it records what is showing |
| `devtool client quit` | Closes the game. Reports `left` first, then leaves as a reconnect does and closes the game instead of coming back. In single player the world is saved as the game stops; on a dedicated server the client disconnects cleanly first |

**The target is always the sender**: the player typing it, the caller of a bundle, the inbox operator through the inbox. There is no player argument, so nobody can send another player's client away. The console, a command block and `/execute` are refused.

**It is off unless the client allows it.** The client needs the toolkit, 1.4.0 or later, and `client.allowActions=true` in its own `devtool.cfg`. At every login a 1.4.0 server says hello to such a client, and the client answers whether it allows actions. So the server refuses at once, with a key a bundle can tolerate: `devtool.client.noClient` when the client has no toolkit or one older than 1.4.0, `devtool.client.notAllowed` when it has not allowed actions (or its answer has not arrived). Neither side is ever sent a message it cannot read: a client older than 1.4.0 gets no hello, and a server older than 1.4.0 sends none, so a 1.4.0 client there never answers. `quit` needs a client that said hello with 1.5.0 or later: a 1.4.0 client that allows actions is refused `quit` with `devtool.client.noClient`, since it would drop a step it does not know and never answer. A 1.4.0 client that has not allowed actions gets `devtool.client.notAllowed` instead, because the version is checked only for a client that allows actions.

**The server refuses** with a key: a respawn asked of a living player (`devtool.client.notDead`), and any step while the sender's client is on a reconnect, about to leave or back but not yet reported (`devtool.client.busy`; it ends once the reconnect reports, 10 seconds after the player is back without a report, or after `inbox.rejoinWindowSeconds`). The refusal of a sender that is not a player (the console, a command block, `/execute`) has no key, so a bundle cannot tolerate it.

**The client refuses** what only it can know, with a reason in its record and in its report to the server; the command itself has already succeeded. Any step: `notAllowed` when its own setting changed since it said hello, `busy` for a close or respawn while a reconnect is under way.

- `close`: `noWorld`, `noScreen`, `deathScreenUseRespawn` (use `respawn`), `credits`, `bedScreen`, `loadingScreen`, `busy` (a close still pending); after closing, `failed reason=stillOpen` when another mod kept or reopened the same screen.
- `respawn`: `noWorld`, `credits`, `hardcore` (the button means Spectate World there), and, after waiting, `notDeadYet`, `noDeathScreen` or `timeout`; `busy` for a second respawn.
- `reconnect`: `noWorld`, `realms`, `openToLan` (stopping a world open to LAN would throw every other player out), `noServerData`; `busy` while a close or respawn is still pending.
- `quit`: `noWorld`; `busy` while a reconnect, a close or a respawn is still pending. A world open to LAN is not refused: the game closes, and every other player is thrown out with it.

**Records.** The server writes `CLIENT_ACTION action=requested` with `player`, `step`, `requestTick` and for a reconnect `awaySeconds`, before sending. The client writes `CLIENT_ACTION side=CLIENT` with `step`, `result` (`done`, `refused`, `failed`, and `left` for a reconnect or a quit leaving), `reason`, `screen` (the screen's class) and `showing` (what is open a tick after a close), and sends the same as a report; the server writes it as `CLIENT_ACTION action=reported`. A report nobody asked for is dropped without a record.

**Put `client reconnect` and `client quit` last in a bundle.** The client leaves about a second after the request, and the bundle's later steps are lost with it: on a dedicated server they end with `senderLost`, in single player the run is discarded when the integrated server stops. Do the work after the return in a second bundle or request.

**A reconnect, step by step.** The client writes `result=left` and reports it, waits at least 5 ticks and one second, so the run that asked for it is answered first, stops reading the connection and lets what it already received be handled, then leaves. In single player it stops the integrated server and waits at the title screen; on a dedicated server it disconnects as the pause menu does. At any menu, after at least 20 ticks and the away time, it rejoins. Back in the same world, the server says hello again and the client writes and reports `result=done` with `awayMs`. If the connection fails, a different world is joined, the save is gone, or it is not back within 60 seconds after the away time (4 minutes more while a single-player world is still loading), the client writes `result=failed` with `connectFailed`, `joinedElsewhere`, `saveNotFound`, `loadAborted`, `notAtMenu` or `timeout`, and `folder` in single player; that one cannot reach the server.

**What reconnect does not do.** It does not take you to another server or world, and it does nothing at the title screen: once the client is at the menu, no server can reach it. At the menu, the [client request channel](#the-client-request-channel) is the way in.

**A quit, step by step.** The client writes `result=left` and reports it, waits as a reconnect does, so the run or request that asked is answered first, and stops reading the connection. On a dedicated server it then disconnects as the pause menu does; then it closes the game. In single player the integrated server stops with the game and saves the world. There is no `done`: `left` is the client's last record, and the game process ends. The inbox closes, with `operatorLeft` on a dedicated server, or `serverStopped` or `operatorLeft` in single player, and `status.json` is deleted. **A quit is not a reconnect**: the inbox is not suspended, and nothing starts the game again. Starting it again is the owner's.


### A reconnect and the logout

**In single player a reconnect does not log the player out unless asked.** Its leave stops the integrated server without the quitting packet the pause menu sends first, because sending that packet from the toolkit once let the server stop before the client's shutdown and hang. So by default the server never writes `left the game` and `PlayerLoggedOutEvent` does not fire: a test of what a mod does at logout passes for the wrong reason. Add `logout` (`client reconnect 3 logout`, client and server 1.7.0 or later) to leave as Save and Quit does, quitting packet first, so the player is logged out before the world stops. On a dedicated server every reconnect logs the player out, with or without the word. A client older than 1.7.0 is refused `logout` with `devtool.client.noClient`, since it would leave without the logout; without the word it reconnects as before.

The reconnect's records say which happened: `logout=true` or `logout=false` on the server's `action=requested`, on the client's `left` and `done`, and in their reports.

**What the logout brings with it in single player.** The server handles the host's leave as any logout: the inbox writes `INBOX action=suspended reason=reconnect` at the logout (not at the server's stop), and a bound session operator is unbound (`OPERATOR action=unbound reason=disconnect`), as on a dedicated server. A bundle that uses operator placeholders after the return binds the operator again first.

**One record when the client is back.** When the reconnect's client returns with its token, the server writes `CLIENT_ACTION action=returned` with `player`, `uuid`, `requestId` (the inbox request that asked for the reconnect, when one did), `awayMs` (the client's own measure, from its leave to its return; for a client older than 1.7.0, which does not report it, the server's, from the request to the return), `logout`, `remote` (the connection's address) and `connection` (a number counted from 1 since the process that runs the server started, the game itself in single player, new at every connection), before the inbox writes `action=resumed`. A test that waits for the return reads this one record. A client that comes back with another token writes none.

### The join name's case

In single player the game keeps the host's player data in two places: in the world's `level.dat`, read only when the joining name equals the world owner's name exactly, with case, and in `playerdata/<uuid>.dat`, read otherwise. With a real account, the first join of a launch can carry the account's own spelling (`Mahghuuuls`) and later joins the launch name (`mahghuuuls`), so a reconnect in the same launch reads the other copy, and the player comes back with older data. The toolkit cannot change which copy the game reads. It writes `WARNING reason=hostNameCase player=<joining name> owner=<owner>` at such a join. Launch with the account's exact case to avoid it. The development client's name, `Developer`, never differs.

### Player actions

From 1.7.0 the toolkit can also take a few small player actions on your own client, so a test whose action is a click can run with no person:

| Command | What the client does |
| --- | --- |
| `devtool client select <0-8>` | Makes that hotbar slot the held one, as its number key does |
| `devtool client use` | One right click with the held item, on what the crosshair is on: a block, an entity, or the air |
| `devtool client attack` | One left click on what the crosshair is on |
| `devtool client hold use\|attack <ticks>` | Keeps the right or left mouse button's input down for 1 to 1200 ticks, then releases it: draws a bow, eats, breaks a block |
| `devtool client drop [all]` | Drops one of the held item, or with `all` the whole held stack, as the drop key does |
| `devtool client inventory` | Opens your own inventory, as the inventory key does: the creative inventory in creative, a ridden horse's inventory when riding one |
| `devtool client tooltip <0-40> [advanced] [shift] [ctrl] [alt]` | Shows your inventory with the tooltip of the item in that slot, as the game draws it when the mouse is over it, writes the tooltip's lines into the record, and closes the view after `showTicks=<ticks>` (1 to 1200, default 20). Slots: 0 to 8 hotbar, 9 to 35 main inventory, 36 to 39 armour (36 feet to 39 head), 40 off hand |

**A click goes through the game's key bindings.** The toolkit counts one press of the key bound to use or attack, and the game turns that into its own click on its next tick, through the same code a mouse click reaches. So the game's and Forge's events of the click itself fire as for a person (the interact and attack events, the item use), and what the click does is the game's decision. What does not fire: the raw mouse input events (Forge's `MouseEvent` and `InputEvent.MouseInputEvent`), since no mouse button is pressed, and the key is not held down: one `attack` on a block breaks it in creative, and in survival breaks only a block that breaks at once, as one quick click does; it does not keep breaking the way a held button does. A key bound with a modifier (Ctrl, Shift or Alt) is not pressed, though the record still says `done`; keep the use and attack keys on plain mouse buttons. `result=done` means the toolkit made the input. What the game then did is read from the game's own records and the toolkit's inspections, such as `devtool inspect block` for a placed block or the target's health after an attack.

**A delay and a screenshot with any action.** Add `delay=<ticks>` (0 to 1200) and the client waits that many ticks before it acts; one action waits at a time, and any newer action takes its place (the waiting one is refused `nextAction`); a waiting action never acts in another world: when the player leaves, it is refused `noWorld` (only the client's record has it then). Add `screenshot=<label>` and the same client takes a screenshot after the action, `screenshotDelay=<ticks>` (0 to 200, default 0) ticks after it, by the rules of `devtool screenshot`: the label is 1 to 48 letters, digits, `_` or `-`, the file is `screenshots/<label>-<requestTick>.png`, and the client writes its `SCREENSHOT side=CLIENT action=saved` record (the server writes no `SCREENSHOT action=requested` for it). The action's records name the label (`screenshot`) and the delay (`delay`). A refused action takes no screenshot. For `inventory`, the screenshot shows the inventory; for a `hold`, it counts from the input going down; for a `tooltip`, `screenshotDelay` is at most `showTicks` minus 2, so the picture is taken while the view shows (more is `badArguments`). The client's records carry no world tick: read the time from the server's `action=reported` `worldTick` against `requestTick`. Example: `client use screenshot=after_use`, `client attack delay=40`, `client hold use 20 screenshot=bow screenshotDelay=10`.

**The tooltip view.** `tooltip` opens a view of your own inventory, drawn as the game draws it, without the recipe book button and the potion effects beside it (also in creative, where the game's own inventory key opens the creative tabs instead), and draws the game's tooltip of the chosen slot as if the mouse were over it: the hover highlight, the tooltip, and the lines and drawing that other mods add through Forge's tooltip events. The real mouse does not move. Nothing moves in this view through the game's own handling: clicks, drags, number keys and the drop key do nothing; another mod's own handlers on container screens are not blocked. Escape or the inventory key closes it early. Its one record is written when it closes: `slot`, `advanced`, `keys`, `showTicks`, `item`, `count`, `lines` (how many lines the tooltip has), `line1`, `line2` and on (formatting codes removed, at most 256 characters each), and `closed`: `done` (after `showTicks`), `person` (closed with nothing else showing: Escape, the inventory key, `client close`), `replaced` (another screen took its place), `died` or `left`. The client's own record has up to 200 lines; the report to the server, and so the server's `action=reported`, has the first 20 with `lines` still counting all, so that it fits one network message. In single player both records are in the same `latest.log`. With a dedicated server, read the client's own log for a longer tooltip (a development client and server started from one `run` folder share and roll one `latest.log`, so take the server's records from its console capture there). With `advanced` the tooltip is the advanced (F3+H) form. Add `screenshot=<label>` to save the drawn tooltip (`screenshotDelay` at most `showTicks` minus 2). Add `shift`, `ctrl` or `alt`, in any order, for the lines a mod shows only while those keys are held: the toolkit marks them held in the keyboard library's own key state, which the game and mods read, only while it builds and draws the tooltip, and restores it at once; no key is pressed anywhere else. The record names them (`keys`), and `keysSeen` says which of them the game read as held while it built the lines, through the same keyboard check mods use; with `keys` but an empty `keysSeen`, the trick did not take effect. Where this client's keyboard library does not allow it, the action is refused `modifierUnavailable`. Refused `emptySlot` for an empty slot, `screenOpen` while another screen is open (an `inventory` asked in the same tick counts as open), `noWorld`; `failed` with an exception's name when another mod's tooltip handler threw (the view is closed again), `failed reason=viewNotOpened` when another mod cancelled or replaced the view as it opened.

**A hold always ends with the input released.** The client writes `CLIENT_ACTION step=hold result=started` when the input goes down (with `input`, `ticks`, the target and the held item), and `result=done` when it is released, with `heldTicks` and `released`: `done` (the ticks ran out; `heldTicks` counts the game ticks the input was down), `left` (no world any more), `died`, `screenOpen` (a screen opened; the game also lets go of every key then), `focusLost` (the window had focus when the hold started and lost it), or `nextAction` (another player action is taken: at once for one without a delay, at the end of its delay for a delayed one; it runs after the release). A hold started while the window has no focus is not released for focus. A held use works without focus (the game's use path has no focus check; seen in the 1.7.0 tests with the window in the back): a bow is drawn and released, and the arrow flies. A held attack keeps breaking a block only while the game holds the mouse (its in-game focus), which the game drops whenever a screen opens; when the screen closes it takes it back if the window has focus, and for a window in the back only when someone clicks into it. With the pause on lost focus off, a window put in the back keeps it. Without it, a held attack is one click. After a `left` release the client cannot report, so only the client's record has it.

**A drop and the inventory.** `drop` presses no key: it calls what the drop key calls, so no key binding matters, and the record names the item and count held before (`all` true or false). `inventory` presses the inventory key; its record is written one tick later, with `showing` naming the screen the game opened (`none` if nothing opened, for example while a ridden horse's inventory still waits for the server).

**Every player action's record says whether the game window had focus** (`focus=true` or `false`), so a test that runs with the window in the back can show it did.

**One more limit of the key path:** a click presses the one binding the game finds for that key, and a hold holds every binding on it, so another mod's binding on the same mouse button can take a click or be held along with a hold. Give an `attack` at least one tick after a `close` or a tooltip view closing: the game ignores a left click in the tick after a screen closes.

**The toolkit does not move or turn the player.** Aim with vanilla commands in the bundle before the click: `tp @s ~ ~ ~ <yaw> <pitch>` sets the look direction, or place the target in front of the player. Keep `client.noPauseOnLostFocus` on (it takes effect with `client.applyOnJoin`): with the game's pause on lost focus, a window without focus opens the pause menu, and every action is refused `screenOpen`.

**The rules of the client actions hold**: always your own client, the toolkit on it and `client.allowActions=true`. The server refuses at once with a key: `devtool.client.noClient` for a client without the toolkit or one whose hello says it is older than 1.7.0, whatever it allows (it has no handler for the action and would drop the connection), `devtool.client.notAllowed`, `devtool.client.busy` during a reconnect, and `devtool.client.dead` for `use`, `attack`, `hold`, `drop` and `tooltip` asked of a dead player. A `select` or an `inventory` is sent to a dead player, but the death screen is open, so the client refuses it `screenOpen`.

**The client refuses** with a reason in its record and report: `noWorld` when no world is open, `screenOpen` when a screen is open (a click would go to the screen, and the game ignores the action keys meanwhile), `unbound` when the key the action presses is unbound in the client's controls, `notAllowed` and `busy` as for the other steps. `failed` with the name of an exception when reading the target or the item threw; nothing was pressed then. The target and the held item are read when the toolkit presses the key, one tick before the game's click.

**Records.** The server writes `CLIENT_ACTION action=requested` with `step`, and `slot` for a select. The client writes `CLIENT_ACTION side=CLIENT` and reports it; the server writes the report as `action=reported`. Both carry what the click met and with what: `target=block` with `targetX`, `targetY`, `targetZ`, `face` and `targetBlock`; `target=entity` with `entityId`, `entityType` and `entityUuid`; or `target=miss`; and `held` (the main-hand item's id, or `empty`) with `heldCount`. A select carries `slot` and the newly held item.

### Baubles slots (only with Baubles installed)

| Command | Effect |
| --- | --- |
| `devtool baubles set <slot> <item> [count] [meta] [player]` | Put one item in a bauble slot through Baubles' own handler. A slot holds one item, so a count other than 1 is refused, and so is an item the slot does not accept. Both refusals write `BAUBLES outcome=rejected` and fail with `devtool.baubles.rejected`; nothing is forced. Tolerating that key therefore also hides a mistyped count. Replacing an item is allowed and named in the record as `replaced` |
| `devtool baubles clear <slot\|all> [player]` | Empty one slot or all of them. Empty slots are a success, so `clear all` is safe in a teardown |

Slots are numbered from 0. With Baubles 1.5.2 the seven slots are typed `amulet`, `ring`, `ring`, `belt`, `head`, `body`, `charm`, and the records carry that type as `slotType`. Both forms call the bauble's equip and unequip hooks, as the Baubles screen does, unless Baubles has its events blocked, so a mod that grants a bonus on equip behaves as it would for a player. Baubles' own `/baubles` command skips those hooks; this one does not. Each change writes a `BAUBLES` record. Without Baubles, both forms fail with `devtool.baubles.absent`.

Baubles is optional. The toolkit loads after it when it is present and never requires it; without it, everything else behaves exactly the same.

## The command inbox

The inbox lets you start a bundle, reload, save, run a read-only toolkit command, take a screenshot, or ask for a client action yourself, by writing a file. It is closed unless both hold:

1. the owner set `inbox.enabled` to true in `run/config/devtool/devtool.cfg` (`B:enabled=true` under `inbox`; false by default), and
2. since the current server started, a player typed `/devtool inbox open` in chat, or, with `inbox.openOnLocalJoin` also true, the owner joined from this computer (see [Opening at a local join](#opening-at-a-local-join)).

The opening is held in memory only, never written to disk. It closes when the player who opened it logs out (`reason=operatorLeft`), when the server stops (`serverStopped`), when a reload finds the key turned off (`disabled`), or when someone types `devtool inbox close` (`closed`). In single player leaving the world closes it, so the owner opens it again each time a world is opened, unless `inbox.openOnLocalJoin` opens it at each of their joins. `devtool inbox status` reports whether it is open, for whom, and whether it is suspended.

**Across a reconnect the toolkit started**, the opening is suspended instead of closed. In single player the toolkit tells the host's leave from a LAN guest's by the same name rule as the join, ignoring case (from 1.6.0; 1.5.0 closed a host's opening whose launch name differed in case). When the inbox operator's own `client reconnect` (from a request, a bundle, or typed) makes their client leave, the inbox writes `INBOX action=suspended reason=reconnect` with `resumeDeadlineMillis`, and stops reading requests; they wait in the folder. `status.json` stays, with `suspended: true`, `reason` and `resumeDeadlineMillis`. When the same client is back with the one-time token it was sent away with, the inbox writes `INBOX action=resumed` and reads the waiting requests, without anyone typing `inbox open`. Between the reconnect request and the leave, the inbox already holds further requests, for at most 15 seconds, so a batch's next line runs after the return. Every other path closes it, with a reason: the deadline, `inbox.rejoinWindowSeconds` (default 120) of wall-clock time, passing (`rejoinTimeout`, also while no server runs at all in single player); the operator logged in again but no token arrived within 10 seconds (`returnedOtherRoute`); a wrong token (`tokenMismatch`); a different world loading (`worldChanged`); the key turned off; `inbox close`; a dedicated server's stop. A leave by the pause menu is an ordinary leave and closes it (`operatorLeft`). A game quit while suspended leaves `status.json` behind, as a crash does, until the next server start deletes it; the new process starts closed.

Opening is refused, with an `INBOX action=refused` record, when the key is false (`reason=disabled`; the chat message names the key), when it is already open for another player (`openForAnother`), and when the command comes from a bundle (`fromBundle`). A bundle, or join automation (the bundle `join.bundle` runs when an operator joins), can never open the inbox, and neither can the console, a command block, or `/execute`. The opening at a local join below is separate from join automation: it is the toolkit's own check, made only under `inbox.openOnLocalJoin`. A person typing it while a bundle waits on a delay or a checkpoint is not refused. **Never put `devtool inbox open` in a bundle.** Ask the owner to type it, or to turn on `inbox.openOnLocalJoin`.

The player who opened it, by typing or by a local join, is the **inbox operator**. Every request runs with that player as the sender and with that player's permissions, and a bundle it starts carries `caller=<operator> channel=inbox`.

### Opening at a local join

With `inbox.openOnLocalJoin=true` (`B:openOnLocalJoin=true` under `inbox`; false by default) and `inbox.enabled=true`, the inbox opens without anyone typing `devtool inbox open` when the owner joins from this computer:

- **Single player**: the host's join, over the game's in-process connection. The host is the player whose name equals the world's owner ignoring case, as vanilla decides who has cheats: the owner is the launch name as typed, the player's name the account's own, so a development launch as `mahghuuuls` joining as `Mahghuuuls` is the host (from 1.6.0; 1.5.0 compared with case and stayed closed without a record). A LAN guest never opens it. The host must be allowed toolkit commands, which in single player means cheats on. The keys are read at every world opening, with the rest of the configuration.
- **Dedicated server**: an operator (level 2) whose client proves it shares the server's folder. When both keys are on as the server starts, it writes a fresh random secret into `config/devtool/local-join.secret`, and removes it at the stop. A client with the toolkit, 1.5.0 or later, reads that file from its own `config/devtool/`, so it finds it only when it runs from the same game folder as the server, as a development client and server sharing `run/` do. It sends the secret after the server's hello, only to a server at 1.5.0 or later that it reached over loopback, and the server accepts it only from a loopback connection. So join with `localhost` or `127.0.0.1`: a join by the computer's LAN address sends nothing, and nothing opens. The server reads the keys at start: turn them on, then restart it. `client.allowActions` is not needed for this.

The opening writes `INBOX action=opened reason=localJoin` with the `operator`. From then on it is exactly a typed opening: requests already waiting are refused `placedBeforeOpen`, `status.json` is written, requests run as that player, it closes when that player leaves, and a toolkit reconnect suspends it. When the inbox is already open, for this player or another, or suspended across a reconnect, nothing changes and nothing is recorded.

Refusals, as `INBOX action=refused` with the player as `operator`:

| `reason` | Meaning |
| --- | --- |
| `localJoinNotOperator` | A local join by a player who may not run toolkit commands: single player with cheats off, or a non-operator on a dedicated server. Written at each such join |
| `localJoinNotProven` | A client sent a secret that does not match, or not over loopback. Written once per player per server run |
| `localJoinOnlineMode` | With `inbox.localJoinOperator` on, a proven join to a server in online mode: no operator rights are given there (from 1.7.0) |
| `localJoinNotHost` | Single player: a player over the in-process connection whose name is not the world's owner, with `owner` naming it. Written at the join, from 1.6.0. Check the launch name: a player who is not the host by this rule usually has no cheats either, so a typed `devtool inbox open` is refused too |

Nothing is recorded when a key is off or when no secret arrived at all.

**Operator rights at a proven local join (from 1.7.0).** On a development dedicated server, the join still needs an operator. With `B:localJoinOperator=true` under `inbox` (besides `enabled` and `openOnLocalJoin`; off by default; `devtool reload` applies a change from the next join), a player whose join passes the proof above, and who is not in `ops.json` at any level, gets operator rights for that session, from the join's own name and UUID, at the server's `op-permission-level` as the `op` command gives them, before the inbox decision, so the inbox opens at that first join with nothing typed. The server writes `OP action=granted reason=localJoin` with `player` and `uuid`. A player already in `ops.json`, at any level, is left exactly as is (`OP action=kept`; if that level is below 2 the inbox is still refused `localJoinNotOperator`). The rights are taken back, with `OP action=revoked`, when that player leaves (`reason=logout`; a toolkit reconnect takes them back at the leave and the proven join that follows gives them again) or the server stops in order (`reason=serverStop`, as with `stop`). The toolkit writes each grant into its own list, `config/devtool/local-join-ops.json`, before giving it; when that list cannot be written or read, it gives nothing and logs an error. A server killed or crashed before its stop keeps the rights in `ops.json` until its next start with the toolkit, which takes them back before anyone joins (`reason=leftover`). It never removes a player it did not add, with two exceptions: an `op` given by hand to the same player while the toolkit's grant is active, or between a crash and the next start, is removed with it. When the list cannot be read, the rights given in the running server are still taken back from memory, and the file is left for you to look at. A join that fails the proof gets nothing (`localJoinNotProven`), and an online-mode server gives nothing, recorded at each such join as `localJoinOnlineMode` (one refusal for the join, not a second `localJoinNotOperator`). Single player never gives or takes back anything. Use it on a development server only: anyone who can run a game from that folder becomes an operator.

The dedicated-server case works only with a client and a server sharing one game folder, which means they also share `run/logs/`: the second process to start archives the first one's `latest.log`, and both then write into one file. Read [`latest.log` is not a stable identifier](#latestlog-is-not-a-stable-identifier) before you trust what that file holds.

### Writing a request

The folder is `run/config/devtool/inbox/`, on a dedicated server under the server's directory. While the inbox is open the toolkit reads it every 10 ticks, on the server thread. While it is closed, no request is read and no request, claim or result file is changed. The one exception is at server start: a `status.json` left behind by a crash or a killed process is deleted, since the inbox is closed.

1. Check that `status.json` exists. It is present exactly while the inbox is open, with `schemaVersion`, `open`, `operator`, `operatorUuid`, `world` (the save folder), `openedWorldTick` and `openedAtMillis`, and while suspended also `suspended`, `reason` and `resumeDeadlineMillis`. No file means closed or not started. A request written while it is suspended waits and runs after the return.
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
| `client reconnect [awaySeconds] [logout]`, `client respawn`, `client close`, `client quit` | `devtool client ...`, for the inbox operator's own client. `awaySeconds` 0 to 60; `logout` last. Lower case only: `client Reconnect` is `badArguments`. `client quit` is answered `done` in the tick it runs; then the game closes and the inbox with it |
| `client select <0-8>`, `client use`, `client attack`, `client hold use\|attack <ticks>`, `client drop [all]`, `client inventory`, `client tooltip <0-40> [advanced] [shift] [ctrl] [alt]` | `devtool client ...`, a [player action](#player-actions) on the inbox operator's own client. Lower case only; `delay=`, `screenshot=`, `screenshotDelay=` and, for a tooltip, `showTicks=` last; a select slot outside 0 to 8, a tooltip slot outside 0 to 40, ticks outside 1 to 1200, an option out of bounds, or `screenshotDelay` without `screenshot` is `badArguments` |

A leading `/` is accepted. Nothing else is: no vanilla command, no other toolkit command, no `devtool` or `att` prefix. Anything that changes the world goes in a bundle you wrote, which the owner can read, and the inbox starts it with `run`.

Refusals, on both the `INBOX` record and the result file:

| `reason` | Meaning |
| --- | --- |
| `verbNotAllowed` | The first word is not in the table above, `devtool` included. A line that starts with `devtool` or `/devtool` gets a `detail` with the same line without the root, such as `use:client respawn` for `devtool client respawn` (that line is then checked like any other, so it may be refused in turn), in its result file and its `INBOX action=refused` record |
| `badArguments` | The verb is allowed but its arguments are not, such as `run` with two words, `reload` with one, a screenshot delay over 600, or `client reconnect 99` |
| `malformed` | Empty, more than one line, not printable ASCII, over 512 characters, or a file over 4096 bytes |
| `placedBeforeOpen` | Already waiting when the inbox opened |
| `operatorLeft` | The inbox operator is no longer online. The inbox closes too |
| `duplicateId` | The id was already claimed. Record only, no result file |
| `noHandler` | The toolkit's request handler is not installed yet. Cannot happen once the mod has started; said rather than dropped |

An accepted request can still fail, with `outcome=failed`: `rootShadowed` when another mod has taken both `devtool` and `att`, so the toolkit will not hand your request to that mod's command; or the name of an exception that escaped while it ran.

### Reading the answer

Every claimed request, except a reused id, gets exactly one `<id>.result.json`. It is written under a temporary name and then moved, so you never read half a file. Fields:

- `schemaVersion`, `id`, `request` (the line), `verb`. A `malformed` request has no `verb`, and one whose file was unreadable or over 4096 bytes has no `request` either
- `outcome`: `done`, `failed` or `refused`
- `reason` when not `done`: a refusal reason above, or `commandFailed` (the command's message is in `detail`), `runNotStarted`, `bundleFailed`, `senderLost`, `serverStopped`, `rootShadowed`, or the name of an exception that escaped. A `run` whose bundle never started, because the bundle does not exist or a scenario checkpoint refused it, is `commandFailed` with the reason in `detail`. `runNotStarted` means the command reported success but no run began, as when another mod cancelled it
- for `run`: `bundle`, `hash`, `executed`, `failed`, `tolerated`, `total`, `stoppedEarly` and `durationTicks`, the same values as `BUNDLE_END`; `senderLost` when the operator disconnected mid-run; `discardedOnServerStop` when the server stopped first; `firstFailure`, the failing key or message of the first command that failed, when one did. For a vanilla command it is often the translation key, such as `commands.fill.failed`. From 1.6.0, `resultFile` (the file name in `results/`) when the bundle has `"result": true` and its file was written; the file is on disk before this answer appears

A `run` is answered when its bundle **ends**, not when it starts, so a bundle with a long checkpoint answers late. It is `done` only when the run finished with `failed=0`. Every other verb is answered in the tick it ran.

The log tells the same story: `INBOX action=claimed`, then `refused`, or the command's own records followed by `INBOX action=dispatched` (or `failed`). When a command fails, the `INBOX action=failed` record's `reason` is the command's own message, while the result file says `reason=commandFailed` and puts that message in `detail`. The result file is a convenience; the log is the authority.

**In single player a paused game reads nothing.** The game pauses when its window loses focus or a menu is open, and a paused single-player game runs no ticks, so requests wait until it resumes. `client.noPauseOnLostFocus` turns the focus pause off, but only together with `client.applyOnJoin=true`, and only from the next game launch if the first world join has already happened.

## The client request channel

At the title screen no server runs, so neither the command inbox nor a client action can reach the game. The client request channel covers that: the client itself reads request files from a folder of its own and takes one of four menu steps. Create a single-player world, open a save, connect to a server on this computer or the local network, or quit the game.

It is off unless the client's own `devtool.cfg` says `client.requestChannel=true` (`B:requestChannel=true` under `client`; false by default). With the key on it listens from game start, with no click. The key is read from the configuration the client holds, which changes at game start and, in single player, at every world opening and `devtool reload`. After changing it at the title screen, restart the game.

The steps call the functions the menus call; they do not press screens, and they are not gameplay. A world opened by a step behaves as one opened by hand: the server writes its `WORLD` and `RELOAD trigger=serverStart` records, the command inbox opens at a local join or when a person types it, client defaults apply at the first join of the launch, and a later `client reconnect` comes back to it.

### Writing a request

The folder is `run/config/devtool/client-inbox/`, under the **client's** game directory, beside the command inbox's folder. The file protocol is the command inbox's, so any program that writes command inbox requests can write these too, pointed at this folder:

1. Wait for `status.json` with a `startedAtMillis` from this launch.
2. Write the request to `<id>.tmp`, then rename it to `<id>.req`.
3. Wait for `<id>.result.json`.

Rules:

- `<id>` is 1 to 64 letters, digits, `_`, `.` or `-`. Files named any other way are ignored.
- **Ids must be unique.** The client claims a request by renaming it to `<id>.claimed`. A new `<id>.req` whose `<id>.claimed` is still there is never run: it gets one `MENU_STEP action=refused reason=duplicateId` record, said once per launch for that id, and no result file, since one would overwrite the earlier answer. The `.req` stays in the folder. This holds for a reused id already waiting when the game starts as well: it is not refused `placedBeforeStart`, gets no result file, and is reported `duplicateId` once, at that first read. Since files are never deleted, the old `<id>.result.json` is still there, so a loop waiting for that file returns at once with the earlier answer. Put a counter or a timestamp in every id, unique across launches, not only within one.
- One request per file: one line of printable ASCII, at most 512 characters, in a file of at most 4096 bytes. A trailing line break is fine. The step word may be in any case; option names and values are written exactly as below.
- The client reads the folder every 10 client ticks, on the client thread, from the title screen on.
- **Requests already waiting at the first read of a launch were placed before it.** Each one with a new id is claimed and refused `placedBeforeStart`, with a result file; one with a reused id only gets its `duplicateId` record. The same happens when the key is turned back on during a launch.
- **One step at a time.** Requests waiting in one read are taken in file-name order, and once one starts a step, the rest of that read are refused `busy`. Write the next request after the previous one's result.
- The toolkit never deletes request, claim or result files.

**`status.json`** holds `schemaVersion`, `listening` (true) and `startedAtMillis`, the launch's start of listening in wall-clock milliseconds. It is written at the first read of each launch, after the leftover requests are refused, and again when the key is turned back on. It is deleted when the key is turned off, and by a `quit` through this channel. It is **not** deleted by `devtool client quit`, a crash or a closed window, so a file from an earlier launch may still be there until this launch's first read deletes and rewrites it. Wait for a `startedAtMillis` later than the moment you asked for the launch, or different from the one you saw before it. A request written against the old file is refused `placedBeforeStart`.

### When a step runs

A step begins only at a menu: no world, no integrated server, no connection, and a menu screen. The "Disconnected" screen counts as a menu, since it stays until someone clicks. The connecting and loading screens do not, and neither does Forge's old-save confirmation. While the client ticks, every request is claimed and answered at once:

- `inWorld` while a world is open. In a world, use the command inbox, `client reconnect` and `client quit` included.
- `busy` while another step is under way, while a toolkit reconnect or quit is under way, or while the client is not at a menu: connecting, loading, or Forge's old-save confirmation waiting for a click.

While one of Forge's startup questions or notices is on screen, the client does not tick at all, and nothing is read or answered until someone clicks. See [Forge's startup questions](#forges-startup-questions).

### What a request may say

| Line | Does |
| --- | --- |
| `create <name> [option=value ...]` | Creates a single-player world with the settings the Create New World screen offers, and opens it |
| `open <folder>` | Opens an existing save in `saves/`, the way the world list does |
| `connect <host[:port]>` | Joins a server on this computer or the local network |
| `quit` | Writes its result and record, then closes the game |

Nothing else: no other menu, button or screen, and no command text.

**`create`.** The name is 1 to 32 letters, digits, `_` or `-`, and is also the save folder, except a name Windows reserves for a device, such as `CON`, which becomes `_CON_` as the screen makes it. The request is refused `exists` when any entry of that folder name is in `saves/`, readable or not: a create never replaces a save, and never picks another folder for you. Options, each at most once:

| Option | Values | Default |
| --- | --- | --- |
| `seed` | Text up to 32 characters, no spaces. A whole number is the seed itself; other text is hashed as the screen does. `0` is random | random |
| `type` | `default`, `flat`, `largeBiomes`, `amplified` | `default` |
| `flat` | A flat-world layer string, only with `type=flat` | Classic Flat |
| `structures` | `true` or `false` | `true` |
| `cheats` | `true` or `false` | `true` |
| `mode` | `survival`, `creative`, `adventure`, `spectator` | `creative` |
| `bonusChest` | `true` or `false` | `false` |

The game falls back silently on a bad value, so every value is checked before anything happens. The `flat` string has the 1.12.2 shape `3;<layers>;<biome>;<features>`:

- layers, bottom first, comma separated, each `[count*]namespace:block[:meta]`, such as `2*minecraft:dirt`. Meta is 0 to 15, the total height at most 256, and every block must be known to the game.
- the biome, a numeric id the game knows; 1 is plains.
- features, comma separated, from the 1.12.2 set: `village`, `biome_1` (temples), `mineshaft`, `stronghold`, `oceanmonument`, `lake`, `lava_lake`, `dungeon`, `decoration`. Each takes at most one parameter, as `village(size=2)`, because the request line cannot hold the space vanilla puts between two. The features part may be empty.

For example `flat=3;minecraft:bedrock,3*minecraft:stone,minecraft:grass;1;decoration`. `type=flat` without `flat` is Classic Flat: bedrock, two dirt, grass, plains, villages. With `structures=false`, a flat world loses its structure features (`village`, `biome_1`, `mineshaft`, `stronghold`, `oceanmonument`) while lakes, dungeons and decoration stay; any other type generates no structures, as the screen's button does.

A bad option is refused `badArguments` before anything is created, with `detail` naming the problem: `notNameValue:<word>`, `unknownOption:<name>`, `repeatedOption:<name>`, `seedTooLong`, `unknownType:<value>`, `unknownMode:<value>`, `notBoolean:<name>`, `flatWithoutTypeFlat`, `flatShape`, `flatNoLayers`, `flatLayer:<layer>`, `flatTooHigh`, `flatBiome:<value>`, `flatFeature:<feature>`, `unknownBlock:<id>`, `unknownBiome:<id>`. A bad name is `badArguments` with no `detail`.

**`open`.** The folder is the rest of the line: letters, digits, `_` and `-`, with single spaces between words, as in `open New World`, at most 64 characters. The name must match the folder's exactly, case included, even on Windows: `open new world` is `notFound` for a folder `New World`. Refused `notFound` when `saves/` holds no loadable save of that name, `newerVersion` when the save is from a newer Minecraft version.

**`connect`.** `host`, `host:port` or `[v6]:port`; the port is 1 to 65535, default 25565. The host is resolved once, and every address it resolves to must be local: loopback, private IPv4 (`10/8`, `172.16/12`, `192.168/16`), link-local, or IPv6 unique-local (`fc00::/7`). Never `0.0.0.0`, `::`, `100.64.0.0/10` (carrier networks and Tailscale use it to reach other machines), Tailscale's `fd7a:115c:a1e0::/48`, multicast, or anything public: those are refused `notLocal`, and nothing connects. A name that does not resolve is `unknownHost`. The client then connects to the resolved number, so no later DNS or SRV lookup can change the target. The lookup runs on the client thread, so a slow name server holds the game while it runs; `localhost` or a number avoids that.

**`quit`.** Writes `MENU_STEP action=done` and the result file, deletes `status.json`, and closes the game. Requests after it in the same read stay unread, and the next launch refuses them `placedBeforeStart`.

### Reading the answer

Every claimed request gets exactly one `<id>.result.json`, written under a temporary name and then moved. Fields:

- `schemaVersion`, `id`, `request` (the line; absent when the file was unreadable or over 4096 bytes, cut to 80 characters for a `malformed` one), `step` (absent for `verbNotAllowed`, when the line named none of the four, and for `malformed`)
- `outcome`: `done`, `failed` or `refused`
- `reason` when not `done`, `detail` for a `create` refused over its options, `folder` for `create` and `open` on `done` and `failed`

Refusals, answered at once:

| `reason` | Meaning |
| --- | --- |
| `verbNotAllowed` | The first word is not one of the four steps |
| `badArguments` | The step is allowed but its arguments are not, such as `quit now`, a bad name, option, folder or address |
| `malformed` | Empty, more than one line, not printable ASCII, over 512 characters, or a file over 4096 bytes |
| `placedBeforeStart` | Already waiting at the first read of this launch |
| `inWorld` | A world is open |
| `busy` | A step, a toolkit reconnect or a quit is under way, or the client is not at a menu |
| `exists` | `create`: an entry of that folder name is in `saves/` |
| `notFound`, `newerVersion` | `open`: no such save, or a save from a newer version |
| `notLocal`, `unknownHost` | `connect`: an address that is not local, or a name that does not resolve |
| `duplicateId` | The id was already claimed. Record only, once per launch per id, no result file |

A step that started is answered `done` once the player is in the expected world: for `create` and `open`, the single-player world of that folder; for `connect`, the server it connected to. Otherwise `failed`:

| `reason` | Meaning |
| --- | --- |
| `connectFailed`, `loadFailed` | The "Disconnected" screen appeared, for a connect or a world |
| `connectAborted`, `loadAborted` | Back at a menu for 40 ticks with nothing starting: the connection was cancelled, the world never started, or a Forge startup question was answered with cancel |
| `joinedElsewhere` | The player ended up in a different world |
| `timeout` | Not in the world within 60 seconds, plus 4 minutes while a single-player server exists. Counted only while the client ticks: see [Forge's startup questions](#forges-startup-questions) |

**After a `timeout`, a world or a connection still under way may yet finish.** Read the client's state before the next step: a `WORLD` record or a command inbox `status.json` means a world opened after all.

**Records.** Every claim and answer writes a `MENU_STEP` record with `side=CLIENT`: `action=claimed`, then `refused` with `reason` and `detail`, or `started`, then `done` or `failed` with `reason`. `folder` is on `started`, `done` and `failed` of `create` and `open`. A `quit` writes `done` straight after `claimed`. A reused id writes only `refused reason=duplicateId` with its `id`, with no `claimed` before it. They are in the client's log, which in single player is the one log.

### Forge's startup questions

Starting a single-player world can make Forge stop and ask first, and two kinds of screen behave differently.

**Startup questions and notices.** A yes/no question while the world starts, such as one about blocks or items that are missing or changed, or a notice such as "missing mods", holds the client thread inside the world's start until someone clicks. No client tick runs meanwhile: nothing in `client-inbox/` is claimed or answered, the step gets no `timeout` and later requests no `busy`, and no record is written. **Apply your own deadline**: a step with no answer long after its `started` record is most likely waiting on such a screen, and needs the owner. A `timeout` can appear only after the click, once ticks run again.

Launch the game with the JVM argument `-Dfml.queryResult=cancel` to answer every yes/no question with cancel at once: the world does not open, and the step fails `loadAborted`. That property answers questions only. A "missing mods" notice still waits for a click, with the client thread held as above.

**The old-save confirmation.** A save from an old Forge version whose data needs converting makes the world list show a confirmation screen before the world starts. That is an ordinary screen, the property does not answer it, and the client keeps ticking: new requests are answered `busy`, and since no single-player server exists yet, the step fails `timeout` after 60 seconds unless someone clicks first.

## Configuration

`run/config/devtool/devtool.cfg` is a Forge configuration file. Keys are written here as `<category>.<key>`; in the file `inbox.enabled` is `B:enabled=true` inside `inbox { }`. Server-side keys take effect at the next reload or server start.

The table lists the keys this release added and the ones you need for the inbox, spawn logging, join automation and client defaults. The arena keys (`arena.defaultWidth`, `arena.defaultHeight`, `arena.defaultLength`, `arena.maxArenaDimension`, `arena.constructionBlock`, `arena.ceiling`, `arena.setRespawnPoint`) and `diagnostics.maxNbtOutputLength` are described by the comments in the file itself.

| Key | Default | Effect |
| --- | --- | --- |
| `inbox.enabled` | `false` | Allows `devtool inbox open`. For a development or test instance only |
| `inbox.localJoinOperator` | `false` | On a development dedicated server, with `inbox.enabled` and `inbox.openOnLocalJoin`: a proven local join of a player not in `ops.json` gets operator rights for that session, so the inbox opens with nothing typed; taken back at the leave, at an orderly stop, or at the next start after a crash. See [Operator rights at a proven local join](#opening-at-a-local-join) |
| `inbox.openOnLocalJoin` | `false` | On a server, together with `inbox.enabled`: opens the inbox without `devtool inbox open` at the owner's own join, the single-player host's or, on a dedicated server, an operator's whose client returns the local-join secret over loopback. A dedicated server reads it at start, so restart it after turning it on. See [Opening at a local join](#opening-at-a-local-join) |
| `inbox.rejoinWindowSeconds` | `120` | How long, in wall-clock seconds, a suspended inbox waits for its operator to come back from a toolkit reconnect, 10 to 600. Keep it longer than the away time plus a world load |
| `client.allowActions` | `false` | On a client: lets a server ask this client for `devtool client` steps. Read by the client, sent to the server at every login. Unlike the other `client.` keys it does not need `applyOnJoin`. In single player, reopening a world picks up a change; a client joining a dedicated server reads it at game start, so restart the client after changing it |
| `client.requestChannel` | `false` | On a client: reads menu-step requests from `config/devtool/client-inbox/`, listening from game start with no click. It does not need `applyOnJoin`. The client's configuration changes at game start and, in single player, at a world opening or `devtool reload`, so restart the game after changing it at the title screen. See [The client request channel](#the-client-request-channel) |
| `diagnostics.spawnIncludeItems` | `false` | `entity_spawn` also records dropped items and experience orbs |
| `join.enabled`, `join.bundle` | `false`, empty | Run a bundle when an operator joins. Its `BUNDLE_START` says `channel=join` |
| `client.applyOnJoin` | `false` | Apply the client defaults below, once per launch at the first world join, and write the client `ENVIRONMENT` record. Every other `client.` key does nothing unless this is true. Ignored on a dedicated server |
| `client.brightness` | `1.0` | Gamma, 0 to 1. Always set to this value when `applyOnJoin` is on |
| `client.musicVolume` | `0.0` | Music volume, 0 to 1. Always set to this value when `applyOnJoin` is on, so the default mutes music |
| `client.masterVolume`, `client.weatherVolume`, `client.hostileVolume`, `client.friendlyVolume`, `client.ambientVolume` | `-1` | Volume of that sound category, 0 to 1. `-1`, or any negative value, leaves the category as it is. Friendly is vanilla's `neutral` category |
| `client.noPauseOnLostFocus` | `false` | When true, turns off vanilla's pause when the window loses focus. It only ever turns the pause off. Esc still opens the menu |

The volume keys and `client.noPauseOnLostFocus` act only when `client.applyOnJoin` is true. The client handles the first world join of each launch once, and marks it handled before it checks `applyOnJoin`, so a change to any `client.` key made after that join, `applyOnJoin` included, takes effect only after the game is restarted.

Every client default is saved to `options.txt`, as the game's own sliders are, so it stays after you turn the key back off. For the pause, F3+P in game turns it back on. The client `ENVIRONMENT` record names `brightness` and `music` every time, and `<name>Was`/`<name>Now` for the other categories and `pauseOnLostFocus` only when they were set, so an absent field always means "left as it was".

The toolkit records on the server. Install it on the client too only when you want screenshots, client defaults, client actions, the client request channel, or the inbox opening at a local join to a dedicated server; a client without it still joins a server that has it.

`-Pdev_runtime_baubles=true`, as in `./gradlew runClient -Pdev_runtime_baubles=true`, puts Baubles on the toolkit's own development runtime. It is for developing the toolkit. To test a mod with Baubles, install Baubles in that instance like any other mod.

## Record vocabulary

One line per record, never spanning lines:

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

Field names are camelCase. Field order is stable per type. A value containing whitespace is quoted. **An absent optional value is omitted entirely**, so you will never parse `field=`. Block coordinates are integers; entity positions and damage amounts have two decimals.

`side` leads every record, then `worldTick`, then `session` and `sessionTick` when a session is active. `worldTick` comes from the dimension where the event happened, and dimensions tick independently, so it is not comparable across dimensions.

**There are 52 record types and no others.** The vocabulary is closed: no near-synonyms, no renames between versions.

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
| `ITEM_PICKUP` | `item_pickup` (from 1.8.0): `player` `playerId` `item` `meta` `count` (the amount that entered the inventory) `entityId` and the item's `posX/Y/Z` |

**Command-driven.** No category gates these; if the command ran, the record exists:

| Record | Written by |
| --- | --- |
| `SESSION_START` / `SESSION_STOP` | `session start` / `stop`. `SESSION_STOP` also when a different save opens, then carrying `reason=worldChanged` |
| `MARK` | `mark`, carrying `label` |
| `OPERATOR` | `session operator`, a disconnect, or a session stop: `action` (`bound` or `unbound`), `name`, `uuid`, `replaced` when rebinding, `reason` when unbound (`disconnect`, `sessionStop`) |
| `BUNDLE_START` | a bundle beginning, with `bundle`, `hash`, `commands`, `caller` (the sender's name), `channel` and `permissionLevel` (the level the toolkit checked, always 2). `channel` is `chat`, `console`, `commandBlock`, `join` (join automation), `inbox`, `nested` (run by another bundle) or `other` (a sender wrapped by `/execute` or a function). From 1.6.0 also `requestId` when an inbox request started the run, or the run it is nested in |
| `BUNDLE_END` | a bundle finishing, with `bundle` `hash` `executed` `failed` `tolerated` `total` `stoppedEarly` `durationTicks`, plus `senderLost` if the caller disconnected. A run cut off by a server stop writes it with `stoppedEarly=true discardedOnServerStop=true` |
| `TOLERATED` | one bundle command whose failure was tolerated, by a declared key or the built-in no-op set: `bundle` `hash` `index` `command` (after placeholder substitution) `key`. Written as that command ends, so it sits between `BUNDLE_START` and the records of the commands after it |
| `RELOAD` | `reload`, and every server start: `bundles` loaded, `problems` found, `trigger` (`command` or `serverStart`). The `ERROR` record of each problem comes before it |
| `SAVE` | `save`: `worlds` saved, `dimensions` (their ids, comma separated; Forge unloads an empty Nether or End, so the count varies), `durationMs`; when a player ran it (the inbox operator through the inbox), from 1.7.0 also `playerFile` (`playerdata/<uuid>.dat` under the world folder), `playerFileSize`, `playerFileModifiedMillis`, and `hostDataInLevel=true` when that player is the single-player host whose name equals the owner's exactly, with case, so the game also keeps their data in `level.dat`. A missing file adds none of these |
| `OP` | operator rights given or taken back at a proven local join on a development server, from 1.7.0: `action` (`granted`, `kept`, `revoked`), `player`, `uuid`, `reason` (`localJoin`, `logout`, `serverStop`, `leftover`) |
| `WARNING` | something a test should know that is not a toolkit error, with `reason`. From 1.7.0: `reason=hostNameCase` with `player`, `owner` and `detail` at a single-player join whose name differs from the world owner's only in case. See [The join name's case](#the-join-names-case) |
| `INBOX` | the command inbox: `action` (`opened`, `closed`, `claimed`, `refused`, `dispatched`, `failed`, `suspended`, `resumed`), `id` and `request` for a request, `operator`, `reason` for closes, refusals, failures, suspensions and an opening at a local join (`localJoin`), `owner` for a `localJoinNotHost` refusal, and `resumeDeadlineMillis` for a suspension |
| `SCREENSHOT` | `screenshot`. On the server `action=requested` with `label` `delayTicks` `target` `requestTick` `file`. On the client, `side=CLIENT` with `label` `requestTick` and `action=saved` with `file`, or `action=failed` with `reason` (`queueFull`, `disconnected`, `noFile` with the expected `file`, or an exception name) |
| `CLIENT_ACTION` | `client`. On the server `action=requested` with `player` `step` `awaySeconds` and `logout` (reconnect only) `slot` (select and tooltip) `input` and `ticks` (hold only) `all` (drop only) `advanced` `keys` `showTicks` (tooltip only) `delay` and `screenshot` `screenshotDelay` (when given) `requestTick`, then `action=reported` with `player` `step` `requestTick` and the client's `result` `reason` `screen` `showing` and its fields, and `action=returned` when a reconnect's client is back, with `player` `uuid` `requestId` `awayMs` `logout` `remote` `connection` `requestTick`. On the client, `side=CLIENT` with `step` `result` (`done`, `refused`, `failed`, `left`, and `started` for a hold going down) `reason` `screen` `showing` `requestTick`; `logout` on a reconnect's `left` and `done`, `awayMs` on its `done`; on a player action `slot`, `target` with its fields, `held` `heldCount`, `focus`; on a hold `input` `ticks`, then `heldTicks` `released`; on a drop `all`; on an inventory `showing`; on a tooltip `slot` `advanced` `keys` `keysSeen` `showTicks` `item` `count` `lines` `line1`... `closed`, and its `screenshot`; `delay` and `screenshot` when given. See [Client actions](#client-actions), [A reconnect and the logout](#a-reconnect-and-the-logout) and [Player actions](#player-actions) |
| `MENU_STEP` | the client request channel, `side=CLIENT`: `action` (`claimed`, `refused`, `started`, `done`, `failed`) with `id`, `step`, `request`, `reason`, `detail`, and `folder` for `create` and `open`. A reused id writes only `refused` with `id` and `reason=duplicateId`, once per launch. See [The client request channel](#the-client-request-channel) |
| `BAUBLES` | `baubles set`: `action=set` `player` `slot` `slotType` `item` `count` `meta`, `replaced` when the slot held something, `outcome` (`set` or `rejected`). `baubles clear`: `action=clear` `player` `slots` (`all` or the index) `cleared` (how many held something) `outcome=cleared` |
| `BUNDLE_STEP` | one command of a bundle with `trace` on, just before it runs, with `bundle` `hash` `index` `command` `dueTick` `elapsedTicks` |
| `BUNDLE_SOURCE` | at load and reload: per file `source` `sha256` `bundles`; per bundle `bundle` `source` `hash`. The same `hash` is on that bundle's `BUNDLE_START` and `BUNDLE_END` |
| `AWAIT` | a wait ended: `condition` `outcome` (`satisfied` `timedOut` `cancelled` `pending`) `reason` `waitedTicks` `timeoutTicks`, and for `chunks` also `dimension` `chunks` `chunkCount` `loadedChunks` `firstUnloadedChunk` |
| `CHECKPOINT` | `action` of `arm`, `cue` or `ack` with `label`. Arm: `armedTick`, `timeoutTicks`, `operator`. Cue: `cueTick`, `form`, `sound`, `operator`. Ack: `askedTick`, `ackTick`, `latencyTicks`, and `answered` (`arm` or `cue`). An armed checkpoint that ends also writes an `AWAIT` with `condition=checkpoint`, `label`, `armedTick`, and when acknowledged `ackTick` and `latencyTicks` |
| `ASSERT` | One declared comparison: `predicate`, the scope fields it used, `expected`, `observed`, `outcome` of `pass`, `fail` or `unloaded`, `dead=true` when an entity counted absent was dying, and `note` when the outcome needs one. A verdict says only whether the observation matched the declared value; it never says the mod is right or wrong |
| `FIXTURE` | `action` of `claim`, `list`, `clear` or `release` with `handle` and `uuid`. Claim: `entity`, `tag`, `dimension`, `pos`, `adopted=true` when the entity already carried the tag. List: `state` (`loaded`, `unloaded`, `absent`, `removing`, `untagged`, `duplicate` with `carriers`, `unknown`), `fixtures`, last place. Clear: `outcome` (`removed` with `confirmedTick`, and `unloadPending=true` when its world has stopped updating entities; `failed reason=stillAlive`; `alreadyRemoving`; `refused` with `reason=notOwned` and the entity's `tags`, or `duplicate`; `notLoaded` with `state`; `unconfirmed reason=serverStopped`). The clear record is written a tick after the clear, once the removal is confirmed, so after a teardown that clears and stops in one tick it follows `SESSION_STOP` and `BUNDLE_END`. Release: `outcome` (`released`, `refused`, `notLoaded`) |
| `SCENARIO` | `action` of `checkpoint`, `load` (with `state` of `present`, `absent` or `corrupt` and `enforced`), `resume` (with `restoredFixtures`), `refuse` (with `reason` of `wrongBundle` or `changedHash`), `completed` or `abandoned`; with `scenarioId`, `expectedBundle`, `expectedHash`, `offeredBundle`, `offeredHash`, `operator`, `expectedStop`, `fixtures` as they apply |
| `ARENA_CREATE` / `ARENA_RESET` / `ARENA_CLEAR` | the matching `arena` action |
| `PLAYER_INSPECT` | `inspect player`, with `openContainer`: `inventory`, or the class of another container the server has open for the player |
| `ENTITY_INSPECT` | `inspect entity` |
| `BLOCK_INSPECT` | `inspect block`, with `tileEntityClass` when there is one |
| `CHUNK_INSPECT` | `inspect chunk` (from 1.8.0): `dimension` `chunkX` `chunkZ` `loaded`, and when loaded `terrainPopulated` `lightPopulated` `populated` |
| `BLOCK_FOUND` | `find block` (from 1.8.0), one per position: `block` `dimension` `posX/Y/Z` `blockState` |
| `BLOCK_SEARCH` | `find block` (from 1.8.0), the summary: `block` `state` `dimension` `from` `to` `found` `limit` `truncated` `chunksSearched` `chunksSkipped` |
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

- **`ENTITY_DAMAGE` collapses three game events into one record.** One hit is one line. `amountPreMitigation` and `amountFinal` are omitted when the damage was cancelled before reaching those stages, and `stoppedAt` tells you where it stopped. Do not expect three lines. A target's `ENTITY_DAMAGE` comes before its `ENTITY_DEATH` and before a `LOG_CONFIG` that turns the category off; a hit on the same target that stopped before the damage stage in that tick is still written at the tick end, after the death. Records of different targets may appear out of attack order; each carries its `worldTick`. An instant effect from `/effect` hurts at the target's next update, a tick after the command.
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

Observation is server-side. In single player everything lands in the one log. With a dedicated server, event records are on the **server**; command replies a person sees in chat are on the **client**. Four records are written by the client and carry `side=CLIENT`: `SCREENSHOT action=saved` or `failed`, the `ENVIRONMENT` record of the client defaults, `CLIENT_ACTION side=CLIENT`, and `MENU_STEP`. With a dedicated server they are in the client's log; the server writes its own `CLIENT_ACTION action=reported` from the client's report. `MENU_STEP` has no server copy.

A client without the toolkit can still join a server that has it. Everything server-side works as usual for that player; only what needs the client fails: a screenshot to that player fails with `devtool.screenshot.noClient`, a client action with `devtool.client.noClient`, and client defaults are not applied.

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

**Handles are a cache; the tag is the truth.** Placeholders, tab completion and scenario checkpoints read the session's handle cache. After a `session stop` and a new `session start` of the same name, a fixture known only by its tag is unbound in `${fixture.<handle>.uuid}` and missing from a checkpoint until a fixture command (`list`, `claim`) refills the cache. Session names that map to the same tag part (`my test`, `my_test`, `my.test`) share fixtures. If a fixture's tag moves to another entity, the handle follows the tag, and `clear` removes the new carrier. A 1.3.0 tag with a dotted handle, or from a session whose name had a dot, belongs to no session in 1.4.0: remove it with vanilla `kill` or `scoreboard players tag <entity> remove <tag>`. A 1.3.0 scenario checkpoint of a dotted session resumes its fixtures as `untagged`.

**What would fool you:** a fixture standing in the world's spawn chunks. A server never unloads those, so `unloaded` never appears there and an absence assertion there is a real verdict at once. Also: ownership is the tag, not the name. If something removed the tag, `clear` writes `outcome=refused reason=notOwned` and leaves the entity alone, and a second pig named `target` is never touched because it was never claimed. And `clear` removes without a death: expect no `ENTITY_DEATH`, no drops, and the `removed` record a tick after the clear.

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

**Once, by the owner:** `inbox.enabled` set to true, the world opened, `/devtool arena create` typed where the test should happen (once per world: the arena is stored in the save, and `arena reset` fails with "No arena in dimension N" without it), and `/devtool inbox open` typed in chat, or `inbox.openOnLocalJoin=true` set before the world opened, so the inbox opens at the owner's join (example K). In single player, if the owner will switch windows while you work, ask for `client.applyOnJoin=true` and `client.noPauseOnLostFocus=true` before the game is launched: the client defaults apply only at the first world join of a launch, and `applyOnJoin` also sets brightness and music to their configured values (1.0 and 0 by default).

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

- `INBOX action=opened operator=...`, with `reason=localJoin` when it opened at the owner's join, then `INBOX action=claimed id=w-001`.
- `BUNDLE_START bundle=feat_wand_inbox_setup caller=<operator> channel=inbox permissionLevel=2`. This is what proves the run came through the inbox and as whom.
- `TOLERATED key=commands.generic.selector.notFound` for the `kill`, `MARK label=READY_STRIKE`, then `BUNDLE_END failed=0 tolerated=1` with the same counters as the result file.
- `ENTITY_DAMAGE` after the mark, read as in example B.
- `w-002.result.json` with `outcome=done`, and the `ENTITY_INSPECT` it caused.
- `SCREENSHOT action=requested label=after_strike`, then `SCREENSHOT side=CLIENT action=saved file=screenshots/after_strike-<tick>.png`, and the image itself in the client's `screenshots` folder.

**What would fool you:** reusing `w-001` for the second request. It is never run, and it gets only an `INBOX action=refused reason=duplicateId` record, no result file, so a loop waiting for that file waits forever: wait with a timeout, and look for that record when it runs out. A request written before the inbox opened, by typing or at a local join, is refused with `placedBeforeOpen`. A bundle edited after the world opened, with no `reload` request, runs the old text; the `hash` in the result file and on `BUNDLE_START` tells you which text ran. A missing answer in single player may only mean the game is paused. If nobody created the arena, `arena reset` fails, the bundle stops there, and the result says `outcome=failed reason=bundleFailed` with `firstFailure` naming the missing arena. And `outcome=done` says the setup ran, not that the strike happened: only the `ENTITY_DAMAGE` after the mark says that.

### J. A reconnect in the middle of a test

Your feature saves something when a player leaves and loads it when they come back. You need the leave and the return without asking anyone to click.

**Once, by the owner:** `client.allowActions=true` in the client's `devtool.cfg` (in single player that is the same file), set before the world is opened (the client reads it when a world opens; with a dedicated server, at game start), and the inbox open. In single player also `client.applyOnJoin=true` and `client.noPauseOnLostFocus=true` before the game is launched, as in example I: the world reopens during the test, and a paused game reads no requests.

```json
{
  "feat_rejoin_check": {
    "description": "Mark, leave and come back after three seconds",
    "result": true,
    "commands": [
      "devtool mark BEFORE_LEAVE",
      "devtool client reconnect 3"
    ]
  }
}
```

**Your steps:** write `run feat_rejoin_check`, then, once its result is `done`, write `mark AFTER_RETURN` and `inspect player`. They wait while the client is away and run after it is back.

**Then read back:**

- `CLIENT_ACTION action=requested step=reconnect awaySeconds=3`, then `CLIENT_ACTION side=CLIENT result=left`.
- `INBOX action=suspended reason=reconnect`, the server's own records of the leave (and in single player its stop and start, with `WORLD sameAsSession=true` when a session was active), then `CLIENT_ACTION side=CLIENT result=done awayMs=...` and `INBOX action=resumed`.
- `MARK label=AFTER_RETURN` and the `PLAYER_INSPECT` after the resume.

**What would fool you:** a `done` result file for the `run` says the bundle asked for the reconnect, not that the client came back; `INBOX action=resumed` says that. A reconnect refused by the client (`openToLan`, `realms`, `busy`) leaves the inbox listening and reports the reason. A client back after the deadline finds the inbox closed (`rejoinTimeout`). And `client.allowActions` lives on the client: on a dedicated server, set it in the client's configuration, not the server's.

### K. A whole launch group, with the owner only launching the game

You want a fresh world, a setup, a check, and the game closed again, with nobody typing or clicking anything.

**Once, by the owner, before the launch**, in `run/config/devtool/devtool.cfg` (in single player the client and the server read the same file): `client.requestChannel=true`, `inbox.enabled=true`, `inbox.openOnLocalJoin=true`, `client.allowActions=true` for the quit, and `client.applyOnJoin=true` with `client.noPauseOnLostFocus=true`, so the game keeps running while the owner works in another window. If the instance has ever shown Forge startup questions, also `-Dfml.queryResult=cancel` among the game's JVM arguments. Then the owner launches the game and does nothing else.

**The bundle**, written before the world opens, so the reload at the world's start reads it:

```json
{
  "feat_launch_check": {
    "description": "Fresh flat world: arena, one probe, two checks; started through the inbox",
    "result": true,
    "commands": [
      "devtool session start launch_check",
      "devtool session operator @s",
      "devtool arena create 9 4 9",
      "summon minecraft:pig ~3 ~ ~ {CustomName:\"probe\",NoAI:1b,PersistenceRequired:1b}",
      "devtool assert entities count 1 within 8 name probe",
      "devtool assert player ${operator.name} gamemode creative",
      "devtool mark CHECKED"
    ]
  }
}
```

**Your steps**, every request through `.tmp` and a rename. `<run>` is a timestamp you take once at the start, such as `1793700000000`, so every id is new on every run:

```
1. note    the moment you ask for the launch, and the startedAtMillis in
           run/config/devtool/client-inbox/status.json if a file is there
2. wait    client-inbox/status.json with a newer startedAtMillis
3. write   client-inbox/k-<run>-1.req      create test1 type=flat structures=false
4. wait    client-inbox/k-<run>-1.result.json    outcome done, folder test1
5. wait    run/config/devtool/inbox/status.json  world test1, the owner as operator
6. write   inbox/k-<run>-2.req             run feat_launch_check
7. wait    inbox/k-<run>-2.result.json           outcome done, failed 0
8. write   inbox/k-<run>-3.req             client quit
9. wait    inbox/k-<run>-3.result.json
10. wait   until the game process has exited, or latest.log holds
           CLIENT_ACTION side=CLIENT step=quit result=left; then read run/logs/latest.log
           before anyone launches again
```

**Why a timestamp in every id.** Neither folder is ever emptied by the toolkit. With a fixed id such as `k-001`, a second run's wait for `k-001.result.json` returns at once with the first run's answer, while the new request is never run and only refused `duplicateId`.

**Why step 10.** The `client quit` result is written when the command is dispatched. The client leaves at least 5 ticks and one second later, after up to 2 seconds more letting the connection drain, so a log read straight after the result file may not hold the quit yet.

The answer to step 3, once the player is in the new world:

```json
{
  "schemaVersion": 1,
  "id": "k-1793700000000-1",
  "request": "create test1 type=flat structures=false",
  "step": "create",
  "outcome": "done",
  "folder": "test1"
}
```

**Then read back:**

- `MENU_STEP side=CLIENT action=claimed id=k-<run>-1 step=create`, then `MENU_STEP action=started folder=test1`.
- `WORLD folder=test1` and `RELOAD trigger=serverStart`, then `INBOX action=opened operator=<owner> reason=localJoin` and `MENU_STEP action=done folder=test1`. Those last two come from different threads, the server's and the client's, so either may come first. Nobody typed `inbox open`; `reason=localJoin` says how it opened.
- `INBOX action=claimed id=k-<run>-2`, `BUNDLE_START bundle=feat_launch_check channel=inbox`, two `ASSERT outcome=pass`, the second proving `mode` defaulted to creative, then `BUNDLE_END failed=0`.
- `INBOX action=claimed id=k-<run>-3`, `CLIENT_ACTION action=requested step=quit`, `CLIENT_ACTION side=CLIENT step=quit result=left`, the inbox closing, and the end of the log. The world was saved as the game stopped.

**What would fool you:** a stale `status.json` in `client-inbox/`. The previous launch ended with `client quit`, which leaves the channel's file in place, so until this launch's first read the file is there with the old `startedAtMillis`, and a request written against it is refused `placedBeforeStart`. Compare the number, not the file's presence. A create refused `exists`: the second time you run this group, `test1` from the earlier run is still in `saves/`, nothing opens, and no inbox appears. Give each run a new name, or write `open test1` when reusing the world is what you mean. `cheats=false` on the create: the host may not run toolkit commands, so the inbox's opening is refused with `INBOX action=refused reason=localJoinNotOperator`, no inbox `status.json` appears, and nobody could type `inbox open` either. A paused game: without `noPauseOnLostFocus` applied at this launch, the game pauses when its window loses focus, so the create still answers (the channel runs at the menu, where nothing pauses) but the inbox requests wait unread until the game resumes. And a `quit` written to `client-inbox/` while the world is open is answered `inWorld`: in a world, quit through the inbox. A `done` for `k-<run>-3` says the client was asked; `result=left` says it began to leave. And a create that never answers, with no `MENU_STEP` after `started`: a Forge startup question or notice is holding the client, which then answers nothing, not even `timeout`. Give up on your own deadline and ask the owner to look at the screen.

### L. A right click with no person

The block of example A, clicked by the toolkit. You write the setup and the click; nobody touches the game.

**Once, by the owner:** `B:allowActions=true` under `client`, the inbox open (`B:enabled=true` under `inbox`, then typed, or by itself with `B:openOnLocalJoin=true`), and, for a window in the back, `B:applyOnJoin=true` with `B:noPauseOnLostFocus=true` under `client` before the launch.

```json
{
  "feat_rsblock_click": {
    "description": "The test block in front of the caller, looked at, right-clicked once by the toolkit, inspected before and after",
    "result": true,
    "commands": [
      "gamemode survival",
      "tp @s ~ ~ ~ -90 30",
      "setblock ~2 ~ ~ yourmod:redstone_block",
      "devtool log player_interaction on",
      { "command": "devtool inspect block ~2 ~ ~", "delayTicks": 5 },
      { "command": "devtool client use screenshot=rsblock_after", "delayTicks": 5 },
      { "command": "devtool inspect block ~2 ~ ~", "delayTicks": 10 },
      "devtool mark RSBLOCK_CLICKED"
    ]
  }
}
```

`tp @s ~ ~ ~ -90 30` turns you east and 30 degrees down, so the block two blocks east is under the crosshair (work out the angle for your own placement: the eye is 1.62 above the feet). Run it through the inbox: `run feat_rsblock_click`.

**Then read back:**

- `CLIENT_ACTION side=CLIENT step=use result=done target=block` with `targetX/Y/Z` equal to the block. If the target is another block, or `miss`, the aim is wrong and the rest proves nothing.
- `PLAYER_INTERACT` for the block: the server received the click.
- The two `BLOCK_INSPECT` records: off, then on.
- `SCREENSHOT side=CLIENT action=saved file=screenshots/rsblock_after-<tick>.png`: look at it for anything only a picture shows.
- `focus=false` on the click is normal when the game window is in the back; the click works the same.

**What would fool you:** a click refused `screenOpen` because a chat or menu was open (the record says so; close it with `client close`), a mob or an earlier block standing in the ray (the record's `target` names what was met), or a dying mob from an earlier run, which the client keeps for its 20-tick death animation and a click can still meet: wait 30 ticks after removing one.

## Limits

**It does not play the game.** The toolkit prepares, records, and reports. On a client that has allowed it, it takes four lifecycle steps (leaving and rejoining, Respawn on the death screen, closing the open screen, closing the game) and, from 1.7.0, small player actions: choose a hotbar slot, right or left click, hold one of those inputs for a time, drop the held item, open the inventory, show an item's tooltip (see [Player actions](#player-actions)). At a menu, with the client request channel on, it takes four menu steps: create a world, open a save, connect to a local server, quit. It never moves or turns the player (a bundle aims with vanilla `tp`) and chooses nothing else in a menu; walking, and anything a test needs judged by eye, stay a person's.

**It can close the game, but not start it.** `client quit` and the channel's `quit` end the game process. Starting the game again is the owner's, since no toolkit runs until it has started.

**It does not know about your mod.** Every record is generic: blocks placed, entities damaged, items used. A modded tile entity's class is named; its contents are not interpreted, because interpreting them would require knowing how that mod works.

**It is not a substitute for your mod's own logging.** It can tell you 3 damage was dealt, by whom, to what, and when. It cannot tell you which branch of your damage calculation ran. **Most of what you need to establish about your own feature is a decision your code made, and only your code can record that.** Add logging to the mod under test and read both in the same file; that colocation is the point.

**It does not judge your mod.** Records are facts. An assertion compares one observation with one value you declared, and its verdict says only that the observation matched or did not match that value. It never says the mod is right or wrong, and it cannot know what the value should have been. Deciding is your job.

**A dense log is not discriminating evidence.** If you design a test around the facts this toolkit happens to record, rather than around the facts that would separate a pass from the ways a failure can look like a pass, you will get a long log that proves nothing.
