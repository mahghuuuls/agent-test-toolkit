# Bundle file format

Bundles live in `config/devtool/bundles/`, outside any world save. The directory is scanned recursively, so subdirectories are fine.

## Shape

A file is a JSON object mapping bundle names to definitions.

```json
{
  "spell_damage_setup": {
    "description": "Prepare one target for manual spell damage testing",
    "stopOnFailure": true,
    "commands": [
      "gamemode creative @p",
      "devtool arena reset",
      "summon minecraft:zombie ~3 ~ ~ {CustomName:\"target\",NoAI:1}",
      { "command": "devtool mark SETUP_COMPLETE", "delayTicks": 20 }
    ]
  }
}
```

- `description` is optional.
- `stopOnFailure` defaults to **true**. Setup bundles build on each other, so continuing past a failure usually produces a half prepared environment.
- `commands` is required. Each entry is either a bare string or an object with `command` and an optional `delayTicks` and `toleratedFailures`.
- `trace` is optional and defaults to `false`. When `true`, a `BUNDLE_STEP` record is written just before each command runs, carrying the tick it became due and the tick it ran on. Turn it on when a delay is not doing what you expected.
- `result` is optional and defaults to `false`. When `true`, a JSON file is written when the run ends, under `results` beside the toolkit configuration, named `<bundle>-<startTick>.json`. It lists the run's assertions, failures, markers, checkpoints, waits and tolerated steps, copied from the same records the log received. The log stays complete without it.

## Expected failures

An idempotent reset fails when there is nothing to reset. `effect @s clear` reports `commands.effect.failure.notActive.all` when no effect is active, and under the default `stopOnFailure` that halts the bundle before the real setup runs.

Declare the failure you expect:

```json
{
  "command": "effect @s clear",
  "toleratedFailures": ["commands.effect.failure.notActive.all"]
}
```

That command may now fail **with that key only**. Any other failure, including a typo in the same command, still fails the bundle. Turning `stopOnFailure` off would have hidden those too, which is why this is per command and per key rather than a switch.

**Finding the key.** Run the command once and read the `ERROR` record. Its `detail` field is the key.

**A tolerated failure is not a silent success.** The outcome is recorded as successful, with a note naming the tolerated key, so a reader can still tell that the command did nothing. Each tolerated command also writes one `TOLERATED` record with the bundle, its hash, the command's index, its text after placeholder substitution, the `key`, and from 1.10.0 its `source`: `declared` when the step declared the key, `builtIn` for the list below. `BUNDLE_END` counts them in `tolerated`, and from 1.10.0 splits the count into `toleratedBuiltIn` and `toleratedDeclared`. A declared key is counted as declared even when the list also covers it. `executed` and `failed` keep their meaning: a tolerated command is executed and not failed.

| Command | Tolerated without a declaration when it fails with |
| --- | --- |
| `kill` | any of the keys below |
| `clear` | any of the keys below |
| `effect <target> clear` | any of the keys below |
| `scoreboard players reset` | any of the keys below |
| `scoreboard players tag <target> remove` | any of the keys below |

The keys: `commands.generic.selector.notFound`, `commands.generic.entity.notFound`, `commands.generic.player.notFound`, `commands.clear.failure`, `commands.effect.failure.notActive`, `commands.effect.failure.notActive.all`. Each means only that nothing matched or there was nothing to remove. These commands remove something, so a teardown run twice is already where it wanted to be; they still write a `TOLERATED` record, with `source=builtIn`.

Every other command that matches nothing fails unless its step declares the key: `testfor`, `execute`, `tp`, `teleport`, `give`, and any command not in the table, another mod's included. A check that matches nothing has given its answer, and it is reported. Changed in 1.10.0: up to 1.9.0 the first three keys and `commands.clear.failure` were tolerated for every command, so a `testfor` or an `execute` that matched nothing passed. A bundle that relied on that declares the key on the step: `{ "command": "tp @e[tag=probe] 0 64 0", "toleratedFailures": ["commands.generic.selector.notFound"] }`.

Two vanilla keys you will often want to declare, because they are the second run of an idempotent setup:

- `commands.fill.failed`: `fill` changed no block, because the region already is the block you asked for.
- `commands.setblock.noChange`: `setblock` found the block already there.

Neither is tolerated unless you declare it, per command: `{ "command": "fill -2 99 -1 2 99 4 minecraft:stone", "toleratedFailures": ["commands.fill.failed"] }`. A setup run twice then ends `failed=0 tolerated=1`, and its inbox `run` answers `done`, instead of reporting a failure on every run after the first.

The toolkit's own failure keys are tolerated the same way, by declaring them:

| Key | When |
| --- | --- |
| `devtool.wait.timeout` | A wait or an armed checkpoint ran out of time |
| `devtool.wait.cancelled` | A wait became impossible, for example the caller changed dimension |
| `devtool.client.actionFailed` | From 1.10.0, a step waiting on its client: the client refused, failed or left the action |
| `devtool.client.releasedEarly` | From 1.10.0, a waiting hold or turn ended before its ticks ran out |
| `devtool.client.wrongTarget` | From 1.10.0, a step with `expect=`: the client reported another entity under the crosshair, or none |
| `devtool.client.targetDead` | From 1.10.0, a step with `expect=`: the client reported the expected entity dying or dead |
| `devtool.screenshot.failed` | From 1.10.0, a picture waited on with `await screenshot` could not be saved |
| `devtool.placeholder.unbound` | A placeholder has no value yet |
| `devtool.placeholder.unsafe` | A placeholder's value could break the command |
| `devtool.assert.failed` | An assertion did not match |
| `devtool.assert.unloaded` | An absence could not be judged because the place is not loaded |
| `devtool.assert.ambiguous` | A modifier name matches several modifiers |
| `devtool.scenario.mismatch` | A scenario checkpoint expects a different bundle, or different text |
| `devtool.inspect.notLoaded` | An inspection names a position whose chunk is not loaded |
| `devtool.fixture.notLoaded` | A `fixture clear ... strict` found a fixture not loaded (from 1.9.0); its handle is kept |
| `devtool.save.failed` | `devtool save` could not save a world |
| `devtool.screenshot.noClient` | The screenshot target's client does not have the toolkit; from 1.8.0, for a screenshot with `hide`, also one older than 1.8.0 |
| `devtool.client.noClient` | Your own client does not have the toolkit, or has one older than 1.4.0, for a `devtool client` step. For `quit`, also a client that allows actions but is older than 1.5.0; for a player action or a reconnect's `logout`, a client older than 1.7.0, whatever it allows; from 1.8.0, for a player action with `screenshotHide`, a client older than 1.8.0; from 1.9.0, for an input or screen step, a client older than 1.9.0, and the message names the client's version and the one needed |
| `devtool.client.notAllowed` | Your client has not allowed client actions |
| `devtool.client.notDead` | A respawn was asked of a living player |
| `devtool.client.dead` | A click, hold, drop or tooltip was asked of a dead player (1.7.0) |
| `devtool.client.busy` | Your client is on a reconnect, from the request until it is back and reported |
| `devtool.client.noTarget` | `client face` found no single loaded entity in your dimension, or the block is not loaded (1.9.0) |
| `devtool.baubles.absent` | Baubles is not installed |
| `devtool.baubles.rejected` | A bauble slot refused the item |
| `devtool.baubles.unsupported` | The installed Baubles provider cannot be driven: one of its methods failed, named in the message and in an `ERROR` record's `detail` (1.10.0) |
| `devtool.find.unknownBlock` | `devtool find block` names no block the game knows (1.8.0) |
| `devtool.find.badState` | `devtool find block` asks a state the block cannot have: a property it lacks, a value it cannot take, or a property the world does not store, such as a fence's sides (1.8.0) |
| `devtool.find.tooLarge` | The `devtool find block` box covers more than 1024 chunks (1.8.0) |

A screenshot label with a forbidden character fails with a message, not a key, so it cannot be tolerated.

## You cannot comment a bundle file

JSON has no comments, and a `"_comment"` key at the top level fails the **entire file**, because every top level entry must be a bundle definition. Use the `description` field instead.

This is strict on purpose. Silently ignoring unrecognised top level keys would let a mistyped bundle name disappear without a word.

## Delays

`delayTicks` means: **wait this many ticks, then run this command.** The wait starts when the preceding command completes, not at the start of the bundle. Two commands each with `delayTicks: 20` put the second 40 ticks after the first. Relative timing composes: inserting a command shifts everything after it, which is what editing a list should do.

The delay belongs on the command that must run **after** the wait. To let chunks load after a teleport, keep the teleport immediate and put the delay on the command that follows it:

```json
"tp @p 1000 70 1000",
{ "command": "fill 990 60 990 1010 62 1010 minecraft:stone", "delayTicks": 40 }
```

Putting the delay on the teleport itself would wait first and then teleport, and the fill would run on the very next tick into chunks that are not there yet. If you are unsure where a delay landed, set `"trace": true` and read the `BUNDLE_STEP` records.

There are 20 ticks per second.

`BUNDLE_END` is written after the last delayed command completes, not when the last command is dispatched.

## Waits

A bundle can pause until something is true. `devtool await chunks x1 z1 x2 z2 [timeoutTicks]` pauses the bundle until every chunk touching that rectangle, in the caller's current dimension, is loaded. It never loads or generates a chunk; it only looks. A region nobody is near never becomes ready, and the timeout is what catches that.

```json
"tp @p 1000 70 1000",
"devtool await chunks 990 990 1010 1010 200",
"fill 990 60 990 1010 62 1010 minecraft:stone"
```

Every wait has a finite timeout: 200 ticks unless you say otherwise, never more than 12000. A wait ends in exactly one of four ways, and each writes an `AWAIT` record:

- **satisfied**: the bundle continues.
- **timedOut**: the wait command fails with the key `devtool.wait.timeout`. `stopOnFailure` applies, and you can tolerate the key on that command if a timeout is acceptable.
- **cancelled**: the wait became impossible, for example the caller changed dimension. The key is `devtool.wait.cancelled`.
- **failed** (from 1.10.0): the awaited thing answered, and the answer fails the step with its own key, named as `key` in the record with a `detail`: a client that refused the action, a picture that was not saved. Tolerate that key on the command when the answer is acceptable.

### Waiting for the client (from 1.10.0)

A client step returns once it is sent. Add `wait=done` (or `wait=started`) and the bundle pauses until the client answers, and the answer becomes the step's success or failure; `expect=<selector|uuid>` on a click, a hold or a `face` also checks that the client hit that entity. `devtool client await <input>` comes back for a hold started earlier without a wait, and `devtool await screenshot <label>` waits until a picture the bundle asked for is saved:

```json
"devtool client key key.sneak hold 100 keepHold=true",
"devtool client key key.forward hold 60 keepHold=true wait=done",
"devtool client await key.sneak",
"devtool client attack expect=@e[type=minecraft:zombie,c=1]",
"devtool screenshot after 5",
"devtool await screenshot after"
```

`waitTicks=<1-1200>` (default 200) counts from the request, so give a hold's ticks plus its `delay` and some margin. Outside a bundle these are refused with `devtool.client.waitOutsideBundle`. Every option, key and field is in `AGENTS.md`, Waiting for the client.

A `delayTicks` on the command after a wait counts from the end of the wait. Outside a bundle, typed by hand, the command checks once, reports, and does not wait.

### Loaded is not decorated

A loaded chunk is not necessarily finished. The world generator decorates a chunk, with its trees, ores and lakes, only once its +X, +Z and +X+Z neighbours are loaded too, and that decoration writes into those neighbours. So in fresh terrain a chunk can be loaded while decoration is still to come into it, and a `fill` right after a plain wait can be partly overwritten a moment later.

From 1.8.0, add `populated` as the last word to wait for that as well:

```json
"tp @p 1000 70 1000",
"devtool await chunks 990 990 1010 1010 400 populated",
"fill 990 60 990 1010 75 1010 minecraft:air"
```

With `populated` the wait ends only when every chunk touching the rectangle, and every chunk one chunk toward -X and -Z of it, is loaded and decorated (the game's `TerrainPopulated` flag). The `AWAIT` record then also carries `populated=true`, `populatedChunks` (how many of those are loaded and decorated), `populatedChecked` (how many were checked) and, while one is not, `firstUnpopulatedChunk` in chunk coordinates. It still never loads or generates a chunk. `devtool inspect chunk` reports one chunk's flags.

## Placeholders

A command may name a value the toolkit fills in when the command runs. Exactly four forms exist:

| Placeholder | Value |
| --- | --- |
| `${operator.name}` | The bound operator's name |
| `${operator.uuid}` | Their UUID |
| `${operator.selector}` | `@p[name=<their name>]` |
| `${fixture.<handle>.uuid}` | The UUID of the fixture claimed under that handle in this session; from 1.8.0 also of the fixture `devtool fixture clear` removed under it, until the session ends |

From 1.9.0 the message of `devtool.placeholder.unbound` names what to run first: `devtool session start <name>`, `devtool session operator <player>`, or `devtool fixture claim <handle> ...`. The key does not change, so `toleratedFailures` written for it still match. The failure's `ERROR` record carries the same step in `detail`, after the key (`devtool.placeholder.unbound: run devtool session start <name> first`), and so does the `detail` of an inbox `run` answer whose first failure it is, so an agent reading only the files sees it.


Bind the operator first, inside the session:

```json
"devtool session start my_test",
"devtool session operator @s",
"gamemode creative ${operator.name}",
"summon minecraft:pig ~ ~ ~ {CustomName:\"${operator.name}\"}"
```

Rules that keep this a lookup and not a language:

- Anything else inside `${...}` is a parse error, and the whole file is refused at reload with the placeholder named.
- Values are filled in when the command runs, never when the file loads. A placeholder whose value is missing at that moment fails the command with `devtool.placeholder.unbound`. Tolerate that key on the command if you mean it.
- A value containing whitespace, a quote or a brace is never substituted; the command fails with `devtool.placeholder.unsafe`. Names, UUIDs and selectors never contain those, so this only ever catches something wrong.
- `BUNDLE_STEP` and `ERROR` records show the substituted text, so the log says what actually ran.
- `${fixture.<handle>.uuid}` reads the session's handles, not the tags. After a `session stop` and a new `session start` of the same name, a fixture known only by its tag is unbound until `devtool fixture list` or `devtool fixture claim` fills the handles again.
- From 1.8.0 a cleared fixture can still be named. After `devtool fixture clear <handle>`, `${fixture.<handle>.uuid}` still gives the removed entity's UUID until the session ends, so `devtool assert entity ${fixture.<handle>.uuid} absent` checks that it is gone. The handle itself is free: every fixture action treats it as unknown, and a new `claim` under it replaces the cleared UUID. A released handle is not kept, since its entity is still alive: its placeholder is unbound.

## Result files

A bundle with `"result": true` leaves one JSON file per run: `run/config/devtool/results/<bundle>-<startTick>.json`. It has a `schemaVersion`, the bundle name and hash, the session and operator, start and end ticks, the `BUNDLE_END` counters including `tolerated` (from 1.10.0 also `toleratedBuiltIn` and `toleratedDeclared`), and arrays `assertions`, `failures`, `markers`, `checkpoints`, `awaits`, `toleratedSteps` (one entry per `TOLERATED` record), and `steps` when traced. From 1.6.0 it also says which run it is: `channel` and `caller` from `BUNDLE_START`, `requestId` when an inbox request started the run (or the run it is nested in), and `startedAtMillis` (wall-clock milliseconds, a number) and `world` (the save folder, left out when there is none), noted when the run started. Two runs of a bundle at the same tick share a file name and the later overwrites the earlier; these fields say which run the file is. Every entry is the fields of one log record, copied as strings. Apart from `startedAtMillis`, `world` and the operator bound at the start, which no record of this run carries, nothing in the file is not also in `latest.log`; it exists so tooling can read a run without scraping prose, and the log remains the authority.

A file that cannot be written is an `ERROR` record and nothing more. It never changes the bundle's outcome.

## Names are global

Bundle names share one namespace across every file. A name defined in two files loads from **neither**, and the error names both files.

Picking a winner by file order would be deterministic but wrong: the two definitions differ, so running either is a coin flip on which one the author meant.

Names shipped with the toolkit are prefixed `example_` so they can never collide with yours.

## What counts as a failure

A command fails when it raises an error: an unknown command, bad syntax, a missing permission, a selector that cannot be parsed.

A removal command that finds nothing to remove has **succeeded** (the table under tolerated failures lists them; any other command that matches nothing fails from 1.10.0). That distinction matters most in teardown bundles, which are meant to be re-runnable:

```
kill @e[name=test_dummy]
clear @p
```

Neither halts a bundle on its second run when there is nothing left to kill or clear. Both are recorded with a note saying so, rather than passing silently, so a bundle that "worked" but changed nothing is still visible in the log.

## Nesting

A bundle command may be `devtool run other_bundle`. The parent waits for the child to finish, then counts the child's outcome as the result of that single command. A child that fails contributes exactly one failure to its parent.

`stopOnFailure` is per bundle and never inherited. A parent that continues past failures can invoke a child that stops on them.

Cycles are refused before the child starts, and the error names the whole route, for example `a -> b -> a`. Nesting deeper than 10 is refused the same way. Nothing recurses and nothing hangs.

## Quiet runs

From 1.9.0 a run can keep its chat to itself, so a person watching the screen while an agent drives the game is not buried in "Teleported ..." lines. A run started through the command inbox is quiet; a run typed in chat, at the console, by a command block or by join automation is not. `devtool run <name> quiet=true` or `quiet=false` (also `devtool bundle run`), or `"quiet": true` / `false` in the bundle file, decide it instead, the command's word first. A nested run follows its parent unless its own `quiet=` word or its file says otherwise.

In a quiet run, vanilla command feedback goes to no chat: not the sender's, not the grey copy other operators see. The server log keeps it when the `logAdminCommands` rule is on. The toolkit's own replies are dropped as well. A failed command still shows one line, `[DevToolkit] Bundle '<name>' command <index> failed: <key>`. `BUNDLE_START` and `BUNDLE_END` say `quiet`, and `BUNDLE_END` counts the lines kept back in `chatSuppressed`.

Not stopped: chat a command sends by itself rather than as feedback, such as `list`, `help`, `say`, `tellraw`, `msg` or a `gamerule` query, messages from other mods, the failure message of a command run inside `/execute`, which vanilla sends to the sender directly (with the `commandBlockOutput` rule on), titles and the action bar, and a checkpoint cue asked for in `chat`. `quiet` does not change the bundle's `hash`.

### What quiet mode keeps out of the chat (1.10.0)

Kept out: in a quiet run, vanilla command feedback and the toolkit's replies; for every command inbox request other than `run`, its replies and feedback too ("Reloaded", "Asked your client to close"), since the result file and the log carry its result; and the reply to `devtool checkpoint ack` when everything it answers was armed or cued by a quiet run. The panel closing is then the confirmation, and the `CHECKPOINT action=ack` record says `quiet=true`.

Not kept out: the one red failure line (`[DevToolkit] Bundle '<name>' command <index> failed: <key>` for a run, `[DevToolkit] Inbox request '<verb>' failed: <key>` for a single request, a `client` request named with its step, as `'client close'`), a refused acknowledgement's error, the lines listed above, vanilla's and other mods' own messages, and your own typed commands and their replies other than the acknowledgement. A `client` step that its client reports refused or failed after the request was answered, such as `client close` with nothing open, shows the same kind of line, `[DevToolkit] Inbox request 'client close' failed: noScreen`, when the report arrives; a step a bundle sent follows the bundle's rules.

## What bundles deliberately are not

No variables, no conditionals, no loops, no arithmetic. The four placeholder forms above are lookups the toolkit fills in; they are the whole list, and anything else inside `${...}` refuses the file at reload rather than reaching the game. Every other character of a command string reaches the game unchanged.

This boundary is a project identity decision, not a simplification waiting to be revisited.

## Permissions

A bundle runs as whoever ran it. Commands the caller could not type themselves fail exactly as if they had typed them. A bundle is a convenience for typing, never a way to widen what the caller can do.

Every `devtool` subcommand needs permission level 2, inside a bundle or not. A caller below level 2 cannot run any of them from a bundle either.

This covers identity as well as permission. The command receives the caller's own sender object, so a command that acts on "the player who sent this" works with the argument omitted:

```
gamemode creative
```

**This changed in 1.0.1.** In 1.0.0 the sender was substituted for a wrapper in order to read failure messages, and the eight vanilla commands that check their sender's concrete type failed with `commands.generic.player.unspecified` whenever their player argument was left out: `clear`, `gamemode`, `kill`, `scoreboard`, `setworldspawn`, `spawnpoint`, `tp`, `xp`. An explicit selector was the workaround and still works.

`devtool client reconnect`, `respawn`, `close` and `quit` act on the caller's own client, and have no player argument. A bundle can run them, and the target is whoever ran the bundle. The console, a command block and `/execute` are refused, with no key, so that refusal cannot be tolerated. See [commands.md](commands.md#client-actions).

**Put `client reconnect` and `client quit` last in a bundle.** The client leaves about a second after the request, and the bundle's later steps are lost with it: on a dedicated server they end with `senderLost`, and in single player the run is discarded when the integrated server stops. Do the work after the return in a second bundle or inbox request. After a quit there is no return: the game is closed.

## Where a run came from

`BUNDLE_START` records who started the run and how: `caller`, the sender's name; `channel`; and `permissionLevel`, the level the toolkit checked, which is always 2. The channels are:

| `channel` | Started by |
| --- | --- |
| `chat` | A player typing it |
| `console` | The server console |
| `commandBlock` | A command block or command block minecart |
| `join` | Join automation, when an operator joins |
| `inbox` | A request in the command inbox, run as the inbox operator |
| `nested` | Another bundle |
| `other` | A sender none of these describes, such as one wrapped by `/execute` or a function |

`devtool run` refuses a call that does not come from the server thread. RCON runs its commands on its own thread, so RCON cannot start a bundle. Use the console or the inbox. The inbox is described in [commands.md](commands.md#command-inbox).

## When files are read

Bundle files and configuration are read when the game starts, on `devtool reload`, and again at every server start, which in single player is every time a world opens. That last reload is the same as a typed one and writes `RELOAD trigger=serverStart`, so a file edited while no world was open is used as it is on disk. After editing a file while a world is open, run `devtool reload`; its record says `trigger=command`.

None of these touches an active session, its operator, fixtures, enabled logging categories, or a bundle already running.

## Provenance

Every load and reload writes one `BUNDLE_SOURCE` record per file, with the file's relative name and the SHA-256 of its bytes, and one per loaded bundle, with the bundle's **normalized hash**. Every `BUNDLE_START` and `BUNDLE_END` carries that same `hash`, so a log line proves which text a run came from.

The raw file hash changes when anything in the file changes, including whitespace and descriptions. The normalized hash changes only when the bundle would behave differently: a command, a delay, a tolerated key, `stopOnFailure`, or `trace`. Editing a `description` does not change it.

Normalization version 1 renders a bundle as: a version line; the name; `stopOnFailure`; `trace`; the command count; then for each command its text, its delay, its tolerated-key count, and each key. Every string is length-prefixed. The rendering is hashed with SHA-256. The version is part of the hashed text, so a future change to the rendering cannot collide with an old hash.

Absolute paths never appear in these records.
