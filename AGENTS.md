# Agent Test Toolkit

Guidance for an AI coding agent working on a Minecraft 1.12.2 mod, using this toolkit to diagnose manual tests.

## What this is

A Forge 1.12.2 utility mod that prepares test environments and writes structured, single line records into `latest.log`, so that an agent can read what happened during a manual test instead of relying on screenshots or a human's description.

## What this is not

**It does not play the game.** A human performs every gameplay action. The toolkit prepares the environment, records what occurred, and reports current state. It never presses a button on your behalf.

**It does not know about your mod.** Every record it writes is generic: blocks placed, entities damaged, items used. It reports what Minecraft and Forge expose and nothing else. A modded block's tile entity class is reported; its contents are not interpreted, because interpreting them would require knowing how that mod works.

**It is not a substitute for your mod's own diagnostics.** If you need to know why your spell did 3 damage instead of 5, this toolkit can tell you that 3 damage was dealt, by whom, to what, and when. It cannot tell you which branch of your damage calculation ran. Add logging to the mod under test for that. The toolkit tells you what the game observed; only your mod can tell you what your mod decided.

**It does not assert or conclude.** Records are facts. There is no pass, no fail, and no verdict anywhere in the output. Deciding whether a result is correct is your job.

## Before you use it

Read this section. It is short and it will save you a wasted test run.

### Enable only the categories you need

Every logging category is off by default, and that default is deliberate. `entity_spawn` in particular will produce hundreds of records if the player explores, because generating fresh terrain genuinely spawns hundreds of entities. That is correct behaviour and it will still make your log unreadable.

Narrow it. Both of these work:

```
devtool log entity_spawn on arena
devtool log entity_spawn on radius 32
```

A filter is not optional housekeeping for the busy categories. It is the difference between a usable log and 700 lines of chickens.

### An excluded event and an event that never happened look identical

Nothing is written when a filter excludes something. If you are missing a record you expected, check what is actually enabled before concluding the game did not do it:

```
devtool log status
```

That command reports every enabled category and the filter on each. It exists precisely because absence is ambiguous.

### Sessions group the evidence

```
devtool session start spell_damage
```

Every record written while a session is active carries the session name and a tick counter relative to its start. Use one per test. It is the difference between reading a log and searching one.

### Marks are how you find the interesting part

```
devtool mark ABOUT_TO_CAST
```

Drop one immediately before the action under test. A mark asserts nothing. It is a bookmark, and it is the fastest way to locate the region of `latest.log` that matters.

If you are asking a human to perform a sequence, put a mark before each step. Then a missing record means something: you can tell "the action produced nothing" from "the human never got that far". Without the mark, those two look the same.

## Bundles

A bundle is a named, ordered list of commands in a JSON file under `config/devtool/bundles/`.

```
devtool run example_test_ready
```

Bundles are how you stop asking a human to type eight setup commands. They support per command tick delays, stop on failure, and nesting, and they emit `BUNDLE_START` and `BUNDLE_END` around everything they do, so you can tell which observed events your setup caused and which came from the test that followed.

**Bundles are not a scripting language and will not become one.** No variables, no conditionals, no loops, no arithmetic, no placeholder expansion. A command line is passed to the game unchanged. If you find yourself wanting a variable, write two bundles.

**Bundle files cannot carry comments.** JSON has none, and a `"_comment"` key at the top level fails the entire file, because every top level entry must be a bundle. Use each bundle's `description` field for notes. That field exists for this.

Example bundles are written into the bundles directory the first time the toolkit runs, and never touched again. Edit them freely. They are yours after that first write.

## Arenas

```
devtool arena create 20 10 20
devtool arena reset
```

An arena is an enclosed, lit, empty box with deterministic bounds, one per dimension, stored in the world save. `reset` restores it and removes everything inside, and it is safe to run at the start of every test because running it twice produces the same state.

Creating an arena also moves your respawn point into it, so dying during a test does not send you hundreds of blocks away.

Note one thing about that: setting a respawn point does not guarantee respawning there. Minecraft revalidates the position when you die and falls back to the world spawn if it is obstructed, silently. If you build something at the arena's start position, expect to respawn elsewhere.

## Reading the output

Records go to `latest.log`, alongside Forge output and your own mod's output. That colocation is the point. You get one timeline, not three files to correlate.

Format is one line per record:

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

Field names are camelCase, order is stable per event type, values containing whitespace are quoted, and an absent optional value is omitted rather than filled with a placeholder. An empty field never appears, so `field=` is not something you need to parse for.

## Command reference

```
devtool help
```

The command is `/devtool`, with `/att` as an alias. Be aware that the alias is a fallback, not a guarantee: another mod holding `att` as its own command name takes it, and nothing reports that it happened.

Full reference: `docs/commands.md`.

## Warning: use a disposable world

**This toolkit modifies the world irreversibly and without confirmation.**

`arena create` replaces every block in its volume. `arena reset` and `arena clear` delete every non player entity inside the bounds, dropped items included. None of these prompt, and none of them can be undone. That is deliberate, because a prompt would make them unusable from a bundle, and `arena reset` is the most used command in a setup bundle.

Commands can also be run from a command block, which means a bundle can be triggered by redstone with no human in the loop.

Use a world you are willing to lose.
