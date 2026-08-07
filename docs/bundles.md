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
- `commands` is required. Each entry is either a bare string or an object with `command` and an optional `delayTicks`.

## You cannot comment a bundle file

JSON has no comments, and a `"_comment"` key at the top level fails the **entire file**, because every top level entry must be a bundle definition. Use the `description` field instead.

This is strict on purpose. Silently ignoring unrecognised top level keys would let a mistyped bundle name disappear without a word.

## Delays

`delayTicks` is measured from the completion of the **preceding** command, not from the start of the bundle. Two commands each with `delayTicks: 20` put the second 40 ticks after the first. Relative timing composes: inserting a command shifts everything after it, which is what editing a list should do.

There are 20 ticks per second.

`BUNDLE_END` is written after the last delayed command completes, not when the last command is dispatched.

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

No variables, no conditionals, no loops, no arithmetic, no placeholder expansion. A command string reaches the game unchanged. `say ${player}` prints the literal text `${player}`.

This boundary is a project identity decision, not a simplification waiting to be revisited.

## Permissions

A bundle runs as whoever ran it. Commands the caller could not type themselves fail exactly as if they had typed them. A bundle is a convenience for typing, never a way to widen what the caller can do.
