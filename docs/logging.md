# Logging

Records go to `latest.log`, alongside Forge output and the output of the mod under test. That colocation is the point: one timeline rather than several files to correlate.

## Record format

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

- One line per record. A record never spans lines.
- Field names are camelCase.
- Field order is stable per event type.
- A value containing whitespace is quoted, and quotes inside it are escaped.
- An absent optional value is **omitted entirely**, never rendered as a placeholder or an empty value. You will not see `field=`.
- Block coordinates are integers. Entity positions and damage amounts are given to two decimal places.

`side` is always reported. Observation is server side only, so nearly every record says `SERVER`. Four records are written by the client and say `CLIENT`: `SCREENSHOT` with `action=saved` or `failed`, the `ENVIRONMENT` record of the client defaults, `CLIENT_ACTION side=CLIENT`, and `MENU_STEP`. With a dedicated server those four are in the client's log, not the server's; the server writes its own `CLIENT_ACTION action=reported` from the client's report. `MENU_STEP` has no server copy.

`worldTick` comes from the dimension in which the event occurred. Dimensions keep independent tick counts, so records from different dimensions are not comparable on this field.

## Every record type

52 in total. The name in brackets is the whole vocabulary: nothing else appears, and these names do not change.

**Written only when their category is enabled.** These are the observed game events, and they are the only ones you can turn off.

| Record | Written when |
| --- | --- |
| `BLOCK_PLACE` | A block is placed |
| `BLOCK_BREAK` | A block is broken |
| `ENTITY_SPAWN` | An entity genuinely spawns. Chunk loading does not count |
| `ENTITY_DEATH` | An entity dies |
| `ENTITY_DAMAGE` | One damage attempt, with all three stages correlated into a single record |
| `PLAYER_INTERACT` | A block is right or left clicked |
| `ENTITY_INTERACT` | An entity is right clicked |
| `ITEM_USE` | An item is used while targeting nothing |
| `ITEM_PICKUP` | From 1.8.0: a player picks an item up. Carries `player`, `playerId`, `item`, `meta`, `count` (the amount that entered the inventory), `entityId` and the position of the item entity |

A target's `ENTITY_DAMAGE` comes before its `ENTITY_DEATH`, and before a `LOG_CONFIG` that turns the category off. A hit on the same target that stopped before the damage stage in that tick is still written at the tick end, after the death. Records of different targets may appear out of attack order; each carries its `worldTick`. An instant effect from `/effect` hurts at the target's next update, a tick after the command.

**Written whenever the command runs.** No category gates these; if you ran the command, the record exists.

| Record | Written when |
| --- | --- |
| `SESSION_START` / `SESSION_STOP` | `devtool session start` / `stop`. `SESSION_STOP` is also written when a different save opens, with `reason=worldChanged` |
| `OPERATOR` | `devtool session operator`, a disconnect, or a session stop. `action` is `bound` or `unbound`, with `name`, `uuid`, `replaced` when rebinding, and `reason` (`disconnect`, `sessionStop`) when unbound |
| `MARK` | `devtool mark` |
| `BUNDLE_START` / `BUNDLE_END` | A bundle begins and finishes. Both carry `bundle` and `hash`. `BUNDLE_START` also carries `commands`, `caller` (the sender's name), `channel` (`chat`, `console`, `commandBlock`, `join`, `inbox`, `nested` or `other`), `permissionLevel` (always 2), and from 1.6.0 `requestId` when an inbox request started the run or the run it is nested in. `BUNDLE_END` also carries `executed`, `failed`, `tolerated`, `total`, `stoppedEarly`, `durationTicks`, `senderLost` when the caller disconnected, and `discardedOnServerStop` when the server stopped mid-run |
| `TOLERATED` | One bundle command whose failure was tolerated, by a declared key or the built-in no-op set. Carries `bundle`, `hash`, `index`, `command` after placeholder substitution, and `key` |
| `RELOAD` | `devtool reload`, and the same reload at every server start. Carries `bundles` loaded, `problems` found, and `trigger` (`command` or `serverStart`). Each problem's `ERROR` comes before it |
| `SAVE` | `devtool save`. Carries `worlds` saved, `dimensions` (their ids, comma separated), and `durationMs`. From 1.7.0, when a player ran it, also `playerFile` (`playerdata/<uuid>.dat`), `playerFileSize`, `playerFileModifiedMillis`, and `hostDataInLevel=true` for a single-player host whose name equals the owner's exactly |
| `WARNING` | From 1.7.0: something a test should know that is not a toolkit error. `reason=hostNameCase` with `player`, `owner` and `detail`, at a single-player join whose name differs from the owner's only in case (see Commands, Reconnect and logout) |
| `OP` | From 1.7.0: operator rights given or taken back at a proven local join on a development server. `action` (`granted`, `kept`, `revoked`), `player`, `uuid`, `reason` (`localJoin`, `logout`, `serverStop`, `leftover`) |
| `INBOX` | The command inbox. `action` is `opened`, `closed`, `claimed`, `refused`, `dispatched`, `failed`, `suspended` or `resumed`, with `id` and `request` for a request, `operator` and, from 1.8.0, `operatorUuid` whenever there is an operator, `reason` for closes, refusals, failures, suspensions and an opening at a local join (`localJoin`), `owner` for a `localJoinNotHost` refusal, and `resumeDeadlineMillis` for a suspension. A local join that cannot open it is `refused` with `localJoinNotOperator`, `localJoinNotProven` or, from 1.6.0, `localJoinNotHost`. See [commands.md](commands.md#opening-at-a-local-join). From 1.7.0 a refusal can carry `detail` (a line written with the `devtool` root: the same line without it), and `localJoinOnlineMode` is a refusal reason. From 1.8.0, after a reconnect, `operator` is the name the player came back with, which can differ in case from the one the inbox opened with |
| `SCREENSHOT` | `devtool screenshot`. On the server, `action=requested` with `label`, `delayTicks`, `target`, `requestTick` and `file`, and from 1.8.0 `hide` when given. On the client, `side=CLIENT` with `label`, `requestTick`, from 1.8.0 `hide` when given and `screen` (the open screen's class) when a screen was open then, and `action=saved` with `file`, or `action=failed` with `reason`, plus the expected `file` when the reason is `noFile` |
| `CLIENT_ACTION` | `devtool client`. On the server, `action=requested` with `player`, `step`, `awaySeconds` for a reconnect, and `requestTick`, then `action=reported` with `player`, `step`, `requestTick` and the client's `result`, `reason`, `screen` and `showing`. On the client, `side=CLIENT` with `step`, `result` (`done`, `refused`, `failed`, or `left` for a reconnect or a quit leaving), `reason`, `screen` (the screen's class), `showing` (what is open a tick after a close) and `requestTick`, plus `awayMs` on a reconnect's `done` and `folder` on a `failed` in single player. A quit has no `done`: `left` is its last record. See [commands.md](commands.md#client-actions). From 1.7.0: the player actions (`select`, `use`, `attack`, `hold`, `drop`, `inventory`, `tooltip`) with their fields (the target, the held item, `focus`, a hold's `input`, `heldTicks` and `released`, a tooltip's lines), the result `started` for a hold going down, `logout` on a reconnect, and `action=returned` on the server when a reconnect's client is back. From 1.8.0 the server's `action=requested` carries `screenshotHide` when given. See `AGENTS.md` for every field |
| `MENU_STEP` | The client request channel, always `side=CLIENT`. `action` is `claimed`, then `refused` with `reason` and `detail`, or `started`, then `done` or `failed` with `reason`; with `id`, `step` and `request`, and `folder` on `started`, `done` and `failed` of a `create` or an `open`. A `quit` writes `done` straight after `claimed`. A reused id writes only `refused` with `id` and `reason=duplicateId`, once per launch, with no `claimed` before it. From 1.8.0 an `inWorld` refusal's `detail` is `paused=<true|false>`, followed by ` screen=<class>` when a screen is open. See [commands.md](commands.md#the-client-request-channel) |
| `BAUBLES` | `devtool baubles`. A set carries `action=set`, `player`, `slot`, `slotType`, `item`, `count`, `meta`, `replaced` when the slot held something, and `outcome` (`set` or `rejected`). A clear carries `action=clear`, `player`, `slots`, `cleared` and `outcome=cleared` |
| `BUNDLE_STEP` | One command of a bundle that set `trace`, written just before it runs. Carries `bundle`, `hash`, `index`, `command`, `dueTick`, and `elapsedTicks`, both counted from submission like `durationTicks`; the stamped `worldTick` is the absolute moment |
| `BUNDLE_SOURCE` | Written at every load and reload. Per file: `source` and `sha256` of the bytes, and `bundles` loaded from it. Per bundle: `bundle`, `source`, and the normalized `hash`. `BUNDLE_START` and `BUNDLE_END` carry the same `hash` |
| `AWAIT` | A wait ended, or a single check outside a bundle answered. `command`, `condition`, `outcome` of `satisfied`, `timedOut`, `cancelled` or `pending`, `reason` when cancelled, `waitedTicks`, `timeoutTicks`, plus the condition's own fields: for `chunks`, `dimension`, `chunks` bounds, `chunkCount`, `loadedChunks`, and `firstUnloadedChunk` when any; from 1.8.0, with `populated`, also `populated=true`, `populatedChunks` (loaded and decorated), `populatedChecked` (the rectangle and one chunk toward -X and -Z) and `firstUnpopulatedChunk` while one is not. Inside a bundle also `bundle`, `hash`, `index` |
| `CHECKPOINT` | `action` of `arm`, `cue` or `ack` with `label`. Arm: `armedTick`, `timeoutTicks`, `operator`. Cue: `cueTick`, `form`, `sound`, `operator`. Ack: `askedTick`, `ackTick`, `latencyTicks`, and `answered` (`arm` or `cue`). An armed checkpoint that ends also writes an `AWAIT` with `condition=checkpoint`, `label`, `armedTick`, and when acknowledged `ackTick` and `latencyTicks` |
| `ASSERT` | One declared comparison: `predicate`, the scope fields it used, `expected`, `observed`, `outcome` of `pass`, `fail` or `unloaded`, `dead=true` when an entity counted absent was dying, and `note` when the outcome needs one. A verdict says only whether the observation matched the declared value; it never says the mod is right or wrong |
| `FIXTURE` | `action` of `claim`, `list`, `clear` or `release` with `handle` and `uuid`. Claim: `entity`, `tag`, `dimension`, `pos`, and `adopted=true` when the entity already carried the tag. List: `state` (`loaded`, `unloaded`, `absent`, `removing`, `untagged`, `duplicate` with `carriers`, `unknown`), `fixtures`, last place. Clear: `outcome` of `removed` with `confirmedTick`, plus `unloadPending=true` when its world has stopped updating entities; `failed reason=stillAlive`; `alreadyRemoving`; `refused` with `reason=notOwned` and the entity's `tags`, or `reason=duplicate`; `notLoaded` with `state`; or `unconfirmed reason=serverStopped`. Release: `outcome` of `released`, `refused` or `notLoaded`. The clear record is written a tick after the clear, once the removal is confirmed, so after a teardown that clears and stops in one tick it follows `SESSION_STOP` and `BUNDLE_END` |
| `SCENARIO` | `action` of `checkpoint`, `load` (with `state` of `present`, `absent` or `corrupt` and `enforced`), `resume` (with `restoredFixtures`), `refuse` (with `reason` of `wrongBundle` or `changedHash`), `completed` or `abandoned`; with `scenarioId`, `expectedBundle`, `expectedHash`, `offeredBundle`, `offeredHash`, `operator`, `expectedStop`, `fixtures` as they apply |
| `ARENA_CREATE` / `ARENA_RESET` / `ARENA_CLEAR` | The corresponding `devtool arena` action |
| `PLAYER_INSPECT` | `devtool inspect player`. Carries `openContainer`: `inventory`, or the class of another container the server has open for the player. The server cannot tell the player's own inventory screen from no screen: both are `inventory` |
| `ENTITY_INSPECT` | `devtool inspect entity` |
| `BLOCK_INSPECT` | `devtool inspect block`. Carries `block`, `meta`, `blockState`, `dimension`, the position, `tileEntityClass` when there is one, and from 1.8.0 `blockLight` and `skyLight`: the stored light values, 0 to 15. A solid block that gives no light reads 0, so read the air beside it; `skyLight` is before the time of day dims it |
| `CHUNK_INSPECT` | From 1.8.0: `devtool inspect chunk`. Carries `dimension`, `chunkX`, `chunkZ`, `loaded`, and when loaded the game's flags `terrainPopulated` (decorated), `lightPopulated` and `populated`. An unloaded chunk is `loaded=false`, and is not loaded |
| `BLOCK_FOUND` | From 1.8.0: `devtool find block`, one per position found, up to the limit. Carries `block`, `dimension`, the position and `blockState` |
| `BLOCK_SEARCH` | From 1.8.0: `devtool find block`, the summary after the `BLOCK_FOUND` records. Carries `block`, `state` when given, `dimension`, `from` and `to` (the box as searched, its height clamped to 0 to 255), `found`, `limit`, `truncated`, `chunksSearched`, `chunksSkipped` (not loaded, so not read), `chunksNotRead` (loaded, left unread once the limit was reached) and `elapsedMillis` |
| `INVENTORY_INSPECT` | `devtool inspect inventory` and `devtool inspect container`. One record per occupied slot, plus `occupiedSlots` even when zero. A bauble slot carries `section=baubles`, `area=baubles` and `slotType`. A container whose loot has not rolled writes one record with `lootRolled=false` and `lootTable`, and no `occupiedSlots` |
| `ATTRIBUTE` | `devtool inspect attributes`, one per attribute: `target`, `targetUuid`, `name`, `base`, `value` (the final value) and `modifiers` (the count) |
| `ATTRIBUTE_MODIFIER` | `devtool inspect attributes`, one per modifier, after its attribute: `target`, `targetUuid`, `attribute`, `uuid`, `name`, `operation`, `operationName` (`add`, `multiplyBase` or `multiplyTotal`) and `amount` |
| `NBT` | `devtool nbt`. Carries `truncated` always, not only when true |
| `ENTITY_LIST` | `devtool entities nearby`, including when nothing matched |
| `ENVIRONMENT` | `devtool environment`. Also written once per launch by the client when client defaults apply, with `side=CLIENT` and the values before and after each setting it applied |
| `CAPABILITIES` | `devtool capabilities`, and one per mod for `devtool mods` |
| `LOG_CONFIG` | Any change to the enabled categories, and `devtool log status` |

**Written by the toolkit itself.**

| Record | Written when |
| --- | --- |
| `STARTUP` | Once per launch. Carries the version and the bundle load result |
| `WORLD` | Every server start, when the overworld loads, before any other toolkit record of that world. Carries `folder` (the save folder name), `seed`, and `sameAsSession` when a session was active |
| `ERROR` | Anything that failed. Never silent, always with enough context to identify the cause |

## Categories

All off by default.

| Category | Records |
| --- | --- |
| `block_place` | A block being placed |
| `block_break` | A block being broken |
| `entity_spawn` | An entity entering the world |
| `entity_death` | An entity dying |
| `entity_damage` | Damage dealt, with the outcome |
| `player_interaction` | Right or left clicking a block |
| `entity_interaction` | Right clicking an entity |
| `item_use` | Using an item while targeting nothing |
| `item_pickup` | A player picking an item up (1.8.0) |

A pickup removes the item entity once the whole stack is in the inventory, so an entity count or `devtool entities nearby` right after it no longer finds the item: the `ITEM_PICKUP` record is the evidence. Its `count` is the amount that went in; a stack that only partly fits writes the part taken, and the rest gets its own record when it is picked up later. A filter is judged at the item entity's position.

```
devtool log block_place on
devtool log block_place off
devtool log all off
devtool log status
```

## Filters

A category can be narrowed to the dimension's arena or to a radius around you.

```
devtool log entity_spawn on arena
devtool log entity_spawn on radius 32
```

One filter per category, replacing any previous one. Filters do not compose into expressions, deliberately: the question "why is this event missing from my log?" should not require reasoning about a boolean expression.

A radius filter is anchored where you stood when you applied it, and does not follow you. A filter that moved would make whether an event was recorded depend on where you happened to be standing.

Applying an arena filter in a dimension with no arena fails, and changes nothing.

### Filters and silence

An excluded event and an event that never happened are identical in the log. Before concluding the game did not do something, check what was actually being watched.

The log answers that on its own. Every change to the enabled set writes a `LOG_CONFIG` record, and so does `devtool log status`:

```
[DevToolkit][LOG_CONFIG] side=SERVER worldTick=1204 action=enable category=entity_spawn filter="radius=32.0 at 10.5,64.0,-3.5 dim=0" enabledCount=2 enabledCategories=block_place,entity_spawn filters="entity_spawn=radius=32.0 at 10.5,64.0,-3.5 dim=0"
```

`action` is one of `enable`, `disable`, `disableAll` or `status`.

`enabledCategories` is comma separated. `filters` is **semicolon** separated, because a filter description contains commas of its own.

The full enabled set is repeated on every one of these, so any single line tells you what was being recorded at that moment. You do not have to accumulate state across the file, and you do not need chat.

## entity_spawn will flood if you let it

Generating fresh terrain genuinely spawns hundreds of entities, and they are all real spawns, so they are all recorded. This is correct behaviour and it will still make your log unusable.

Filter it. Dropped items and experience orbs are excluded by default for the same reason, since one mob death produces a burst of both; `spawnIncludeItems` in the config turns them back on when item drop timing is what you are testing.

Entities arriving because a chunk loaded are **not** recorded. Only genuine spawns are.

A spawn egg names its entity after the `ENTITY_SPAWN` record is written. Minecraft spawns the entity first and applies the egg's custom name afterwards, so the spawn record of an entity from a named egg has no `name`.

## Sessions

```
devtool session start spell_damage
devtool session stop
```

While a session is active, every record carries `session` and `sessionTick`, the latter counted from the session's start. A session lives in memory. It survives returning to the title screen and reopening the same save. Opening a different save ends it, as described below. A full restart of the game or the server ends it too; only a scenario checkpoint restores one.

`session stop` forgets the session's fixture handles, but the tags stay on the entities, so a new session of the same name finds and adopts them again on its first `devtool fixture list` or `devtool fixture claim`. Nothing is adopted by itself.

## Worlds

Every server start writes a `WORLD` record with the save's `folder` and `seed`, before any other toolkit record of that world. In single player a server starts each time a world opens, by hand or by a `create` or `open` of the client request channel alike.

A session never spans two saves. Two saves are the same when their folder name and seed match.

- Reopening the same save keeps the session, and `WORLD` carries `sameAsSession=true`.
- Opening a different save writes `SESSION_STOP reason=worldChanged` first, after an `OPERATOR action=unbound` if an operator was bound, and then `WORLD sameAsSession=false` with no session. Nothing from the new save carries the old session's name. The session's fixtures are forgotten; the entities themselves stay.
- With no session active, `WORLD` carries no `sameAsSession`.

A world deleted and recreated with the same name and seed counts as the same save. A brand new world generates its spawn area before the `WORLD` record, so with `entity_spawn` still on from the previous world, its first animals are recorded under the old session.

Enabled logging categories are not tied to a save. They last until the game closes.

After `WORLD`, and before the first tick, the server start also reloads bundles and configuration, exactly as `devtool reload` does, and writes `RELOAD trigger=serverStart`. On a dedicated server the load records, `BUNDLE_SOURCE` and an `ERROR` for each load problem, therefore appear twice at start: once from the process starting and once from this reload, the second batch followed by the `RELOAD` record. In single player the first batch is at game start and the second when a world opens.

## Marks

```
devtool mark ABOUT_TO_CAST
```

A marker record with a label and nothing else. It asserts nothing and carries no verdict. Use it to find the interesting part of a long log, and put one before each step when a human is performing a sequence, so that a missing record can be told apart from a step that was never reached.
