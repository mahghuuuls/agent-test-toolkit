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

`reload` re-reads bundles and configuration from disk. It does not disturb an active session, the enabled categories, their filters, or a bundle already running. You need it after every file edit; a person who forgets it will run the previous version of your bundle, and the log will not say so.

### Paths

| What | Where |
| --- | --- |
| Bundles you write | `run/config/devtool/bundles/*.json` (scanned recursively) |
| Configuration | `run/config/devtool/devtool.cfg` |
| The log you read | `run/logs/latest.log` |

On a dedicated server these live under the **server's** directory, not the client's. A client-side copy of a bundle has no effect on what the server runs.

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
- `delayTicks` is measured from the **previous command finishing**, not from the bundle's start. Two commands with `delayTicks: 20` put the second 40 ticks in. 20 ticks per second.
- **No comments.** JSON has none, and a `"_comment"` key at the top level fails the *entire file*, because every top-level entry must be a bundle. Use `description`.
- **A duplicate name loads from neither file**, and the error names both. Prefix your bundles so they cannot collide: `feat_`, or the feature's name.
- `example_` is reserved for the bundles the toolkit ships.
- **Not a scripting language.** No variables, conditionals, loops, or substitution. `say ${player}` prints the literal `${player}`. If you want a variable, write two bundles.
- **Nesting works**: a command may be `devtool run other_bundle`. The parent waits; the child's whole outcome counts as one command. Cycles and nesting past depth 10 are refused before anything runs.
- A bundle runs **as whoever ran it**, fully. Permissions, position, world, and sender identity are the caller's own, so a command may omit its player argument exactly as if typed in chat: `gamemode creative` works, and so do bare `clear`, `kill`, `tp`, `xp`, `spawnpoint`, `setworldspawn` and `scoreboard`. A bundle is a convenience for typing, never a way to widen permissions.
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

You can turn categories on and off **mid-test** from a second bundle. This is the right tool when a first run gives you an ambiguous result: re-run with one more category enabled rather than enabling everything up front.

## Commands

All require permission level 2. Root command `devtool`, alias `att` (a fallback, not a guarantee: a mod whose own command is `att` takes it, and nothing reports that).

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

### Session and marks

| Command | Effect |
| --- | --- |
| `devtool session start <name>` | Stamp every subsequent record with a name and a relative tick |
| `devtool session stop` | End it |
| `devtool session status` | Report the active session |
| `devtool mark <label>` | Write a labelled marker record |

Start a session in every setup bundle. Without one you are searching a log; with one you are reading a slice of it.

**Marks are how you tell "the action produced nothing" from "they never got that far."** When you ask a person for a sequence of actions, put a mark before each one. Then a missing record is informative instead of ambiguous.

### Inspection

| Command | Effect |
| --- | --- |
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects |
| `devtool inspect entity <selector>` | Registry id, position, motion, health, effects |
| `devtool inspect block <x> <y> <z>` | Block id, metadata, blockstate, tile entity class |
| `devtool inspect inventory [selector]` | Occupied slots across inventory, armour, offhand |
| `devtool entities nearby <radius>` | Every entity within the radius, one record each |
| `devtool nbt entity <selector>` | Raw NBT for an entity |
| `devtool nbt block <x> <y> <z>` | Raw NBT for a tile entity |
| `devtool nbt held` | Raw NBT for the held item |

**`inspect block` does not report container contents.** It answers what a block *is*: id, metadata, blockstate, tile entity class. For what is *inside* a chest, use `nbt block`, which writes the tile entity's raw NBT including every occupied slot. Two commands, two questions, and mistaking one for the other has already cost an external project a workaround it did not need.

A container whose loot table has not rolled writes a `LootTable` reference instead of items, so `nbt block` also distinguishes "loot not generated yet" from "these are the contents".

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
| `devtool reload` | Re-read bundles and configuration from disk |
| `devtool bundle list` | Loaded bundles and their command counts |
| `devtool bundle show <name>` | One bundle's commands in order, with delays |
| `devtool capabilities` | Version, commands, inspection types, logging categories |
| `devtool environment` | Minecraft and Forge versions, dimension, difficulty, position |
| `devtool mods` | Every loaded mod id and version |

`capabilities` is read from the live command registry and the category enum, never from a written list, so it cannot claim a feature the jar does not have. Use it when you need to know what build is actually running.

## Record vocabulary

One line per record, never spanning lines:

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

Field names are camelCase. Field order is stable per type. A value containing whitespace is quoted. **An absent optional value is omitted entirely**, so you will never parse `field=`. Block coordinates are integers; entity positions and damage amounts have two decimals.

`side` leads every record, then `worldTick`, then `session` and `sessionTick` when a session is active. `worldTick` comes from the dimension where the event happened, and dimensions tick independently, so it is not comparable across dimensions.

**There are 27 record types and no others.** The vocabulary is closed: no near-synonyms, no renames between versions.

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
| `SESSION_START` / `SESSION_STOP` | `session start` / `stop` |
| `MARK` | `mark`, carrying `label` |
| `BUNDLE_START` | a bundle beginning, with `bundle` and `commands` |
| `BUNDLE_END` | a bundle finishing, with `bundle` `executed` `failed` `total` `stoppedEarly` `durationTicks`, plus `senderLost` if the caller disconnected |
| `ARENA_CREATE` / `ARENA_RESET` / `ARENA_CLEAR` | the matching `arena` action |
| `PLAYER_INSPECT` | `inspect player` |
| `ENTITY_INSPECT` | `inspect entity` |
| `BLOCK_INSPECT` | `inspect block`, with `tileEntityClass` when there is one |
| `INVENTORY_INSPECT` | `inspect inventory` |
| `NBT` | `nbt`, carrying `truncated` always |
| `ENTITY_LIST` | `entities nearby`, including when nothing matched |
| `ENVIRONMENT` | `environment` |
| `CAPABILITIES` | `capabilities`, and one per mod for `mods` |
| `LOG_CONFIG` | any change to enabled categories, and `log status` |

**Toolkit-internal:**

| Record | Written when |
| --- | --- |
| `STARTUP` | once per launch, with version and bundle load result |
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

A client and a dedicated server started from the same directory share `run/logs/`, so the second to start archives the first one's log.

**The combined file can also be corrupted.** Two processes writing one Log4j file, on Windows in particular, can leave **NUL bytes** in it. One reported session had 6,377 NUL bytes in 43,542 retained bytes. Standard tooling then treats the file as binary and declines to search it, which looks exactly like "there are no records" rather than "this file needs different handling".

Running two processes from one directory:

- Read with a binary-tolerant tool. `rg -a` finds records where plain `rg` reports nothing.
- Retain **both** the current log and the dated archive. Neither alone holds the whole session.
- Attribute lines by thread, `[Server thread]` against `[Client thread]`, since both streams interleave.
- Use **separate directories** for client and server where the build allows it. That avoids all of the above.

The toolkit cannot fix this. It writes through the game's own logger, which is precisely what puts its records on one timeline with Forge and the mod under test.

So: read the log **before** anything restarts, and copy it to a scenario-named path if it needs to outlive the session. Hashing a file that is later overwritten proves nothing unless the bytes were kept too.

### Which process writes what

Observation is server-side. In single player everything lands in the one log. With a dedicated server, event records are on the **server**; command replies a person sees in chat are on the **client**.

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

## Limits

**It does not play the game.** A person performs every gameplay action. The toolkit prepares, records, and reports. It never presses a button.

**It does not know about your mod.** Every record is generic: blocks placed, entities damaged, items used. A modded tile entity's class is named; its contents are not interpreted, because interpreting them would require knowing how that mod works.

**It is not a substitute for your mod's own logging.** It can tell you 3 damage was dealt, by whom, to what, and when. It cannot tell you which branch of your damage calculation ran. **Most of what you need to establish about your own feature is a decision your code made, and only your code can record that.** Add logging to the mod under test and read both in the same file; that colocation is the point.

**It does not assert or conclude.** Records are facts. There is no pass, no fail, no verdict. Deciding is your job.

**A dense log is not discriminating evidence.** If you design a test around the facts this toolkit happens to record, rather than around the facts that would separate a pass from the ways a failure can look like a pass, you will get a long log that proves nothing.
