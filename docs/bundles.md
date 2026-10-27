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

**A tolerated failure is not a silent success.** The outcome is recorded as successful, with a note naming the tolerated key, so a reader can still tell that the command did nothing. Each tolerated command also writes one `TOLERATED` record with the bundle, its hash, the command's index, its text after placeholder substitution, and the `key`. `BUNDLE_END` counts them in `tolerated`. `executed` and `failed` keep their meaning: a tolerated command is executed and not failed.

Three keys are tolerated for every command without being declared, because their no-op case is universal: `commands.generic.selector.notFound`, `commands.generic.entity.notFound`, `commands.generic.player.notFound`. So is `commands.clear.failure`. You do not need to declare those. They still write a `TOLERATED` record.

Two vanilla keys you will often want to declare, because they are the second run of an idempotent setup:

- `commands.fill.failed`: `fill` changed no block, because the region already is the block you asked for.
- `commands.setblock.noChange`: `setblock` found the block already there.

Neither is tolerated unless you declare it.

The toolkit's own failure keys are tolerated the same way, by declaring them:

| Key | When |
| --- | --- |
| `devtool.wait.timeout` | A wait or an armed checkpoint ran out of time |
| `devtool.wait.cancelled` | A wait became impossible, for example the caller changed dimension |
| `devtool.placeholder.unbound` | A placeholder has no value yet |
| `devtool.placeholder.unsafe` | A placeholder's value could break the command |
| `devtool.assert.failed` | An assertion did not match |
| `devtool.assert.unloaded` | An absence could not be judged because the place is not loaded |
| `devtool.assert.ambiguous` | A modifier name matches several modifiers |
| `devtool.scenario.mismatch` | A scenario checkpoint expects a different bundle, or different text |
| `devtool.inspect.notLoaded` | An inspection names a position whose chunk is not loaded |
| `devtool.save.failed` | `devtool save` could not save a world |
| `devtool.screenshot.noClient` | The screenshot target's client does not have the toolkit |
| `devtool.baubles.absent` | Baubles is not installed |
| `devtool.baubles.rejected` | A bauble slot refused the item |

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

Every wait has a finite timeout: 200 ticks unless you say otherwise, never more than 12000. A wait ends in exactly one of three ways, and each writes an `AWAIT` record:

- **satisfied**: the bundle continues.
- **timedOut**: the wait command fails with the key `devtool.wait.timeout`. `stopOnFailure` applies, and you can tolerate the key on that command if a timeout is acceptable.
- **cancelled**: the wait became impossible, for example the caller changed dimension. The key is `devtool.wait.cancelled`.

A `delayTicks` on the command after a wait counts from the end of the wait. Outside a bundle, typed by hand, the command checks once, reports, and does not wait.

## Placeholders

A command may name a value the toolkit fills in when the command runs. Exactly four forms exist:

| Placeholder | Value |
| --- | --- |
| `${operator.name}` | The bound operator's name |
| `${operator.uuid}` | Their UUID |
| `${operator.selector}` | `@p[name=<their name>]` |
| `${fixture.<handle>.uuid}` | The UUID of the fixture claimed under that handle in this session |

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

## Result files

A bundle with `"result": true` leaves one JSON file per run: `run/config/devtool/results/<bundle>-<startTick>.json`. It has a `schemaVersion`, the bundle name and hash, the session and operator, start and end ticks, the `BUNDLE_END` counters including `tolerated`, and arrays `assertions`, `failures`, `markers`, `checkpoints`, `awaits`, `toleratedSteps` (one entry per `TOLERATED` record), and `steps` when traced. Every entry is the fields of one log record, copied as strings. Nothing in the file is not also in `latest.log`; it exists so tooling can read a run without scraping prose, and the log remains the authority.

A file that cannot be written is an `ERROR` record and nothing more. It never changes the bundle's outcome.

## Names are global

Bundle names share one namespace across every file. A name defined in two files loads from **neither**, and the error names both files.

Picking a winner by file order would be deterministic but wrong: the two definitions differ, so running either is a coin flip on which one the author meant.

Names shipped with the toolkit are prefixed `example_` so they can never collide with yours.

## What counts as a failure

A command fails when it raises an error: an unknown command, bad syntax, a missing permission, a selector that cannot be parsed.

A command that runs and changes nothing has **succeeded**. That distinction matters most in teardown bundles, which are meant to be re-runnable:

```
kill @e[name=test_dummy]
clear @p
```

Neither halts a bundle on its second run when there is nothing left to kill or clear. Both are recorded with a note saying so, rather than passing silently, so a bundle that "worked" but changed nothing is still visible in the log.

## Nesting

A bundle command may be `devtool run other_bundle`. The parent waits for the child to finish, then counts the child's outcome as the result of that single command. A child that fails contributes exactly one failure to its parent.

`stopOnFailure` is per bundle and never inherited. A parent that continues past failures can invoke a child that stops on them.

Cycles are refused before the child starts, and the error names the whole route, for example `a -> b -> a`. Nesting deeper than 10 is refused the same way. Nothing recurses and nothing hangs.

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
