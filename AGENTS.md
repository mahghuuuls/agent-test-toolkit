# Agent Test Toolkit

Guidance for an AI coding agent working on a Minecraft 1.12.2 mod, using this toolkit to diagnose manual tests.

## What this is

A Forge 1.12.2 utility mod that prepares test environments and writes structured, single line records into `latest.log`, so that an agent can read what happened during a manual test instead of relying on screenshots or a human's description.

**What it is mostly for is evidence discipline, not the event categories.** Sessions, marks, bundles, deterministic arena reset, one stable timeline, and a capability report that reflects the running build: those are the parts that change how a test is run and how far it can be trusted. The eight logging categories are useful corroboration of what the game did, and they are genuinely not the main thing.

That ordering matters because of a specific failure mode. A dense generic log is easy to mistake for discriminating evidence. If a test is designed around the facts this toolkit happens to record, rather than around the facts that would distinguish a pass from the ways it could look like a pass, the log will be long and prove nothing. Most of what you actually need to establish about **your** mod is a decision your mod made, and only your mod can record that.

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

Nothing is written when a filter excludes something. Before concluding the game did not do something, check what was actually being recorded at the time.

You do not need to run a command for this. Every change to the enabled set writes a `LOG_CONFIG` record, and each one carries the full set:

```
[DevToolkit][LOG_CONFIG] side=SERVER worldTick=1204 action=enable category=entity_spawn filter="radius=32.0 at 10.5,64.0,-3.5 dim=0" enabledCount=2 enabledCategories=block_place,entity_spawn filters="entity_spawn=radius=32.0 at 10.5,64.0,-3.5 dim=0"
```

Search backwards from the gap in your log for the nearest `LOG_CONFIG`. Its `enabledCategories` and `filters` tell you what was being watched, so you can distinguish "the event did not happen" from "nothing was listening for it".

`enabledCategories` is comma separated; `filters` is **semicolon** separated, because a filter description contains commas of its own.

`devtool log status` writes one of these too, if you want a reading at a specific point rather than at the last change.

### Sessions group the evidence

```
devtool session start spell_damage
```

Every record written while a session is active carries the session name and a tick counter relative to its start. Use one per test. It is the difference between reading a log and searching one.

### Prove the transition, not the final value

The single most useful rule for designing a check:

> **A successful-looking final value is not evidence unless the log also proves the tested transition occurred from the intended initial state.**

A test that expects a resource to end at 20, and observes 20, has established nothing if the resource never left 20. The run proves the behaviour only when the log shows the intermediate states: 20, then 5, then 20 again.

The corollary is that you must rule out the ways a test can appear to pass without running. Before trusting a human's "done":

1. confirm the intended build and environment actually started
2. confirm each setup command **took effect**, not merely that it was sent
3. confirm a mark proves the human reached the action
4. confirm a positive record proves the action occurred at all
5. confirm the records after that mark concern the same entity and side
6. confirm the result distinguishes the expected behaviour from bypass paths: creative mode, a cancelled event, a disabled config, the wrong hand, an insufficient resource
7. confirm no relevant error was recorded

Point 6 is where checks usually fail. A player in creative mode, or a config flag left off from an earlier test, produces a log that looks exactly like the feature working.

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

## Evidence: read this before you rely on a log

**`latest.log` is not a stable evidence identifier.** Minecraft renames it to a dated `.gz` archive every time a process starts. If you restart and then read `latest.log`, you are reading a different file than the one your test wrote to, and you will conclude that nothing was recorded.

**A client and a dedicated server started from the same directory share `run/logs/`.** The second process to start archives the first one's log. Running a client against a local dedicated server therefore rotates the server's log out from under it.

This is not hypothetical. It cost this toolkit's own development two near-miss conclusions during testing.

The practice that works:

1. Finish the check and **stop the runtime**. Confirm it shut down normally.
2. Read the relevant region of the log for immediate diagnosis.
3. **Copy** the log to a scenario-named path before anything restarts. Hash the copy if the evidence needs to be durable.
4. In dedicated testing, archive the server and client logs **separately**, with the role and the scenario in the filename.
5. Only then start anything again.

Hashing a file that is later overwritten proves nothing unless the bytes were kept too.

### Which log receives what

Records are written by whichever process observed the event. Observation is server side, so in integrated single player the records land in that one process's log. With a dedicated server, event records are on the **server**; command replies you see in chat are on the **client**. Client environment defaults, if you enable them, are the only records written client side.

## Reading the output

Records go to `latest.log`, alongside Forge output and your own mod's output. That colocation is the point. You get one timeline, not three files to correlate.

Format is one line per record:

```
[DevToolkit][BLOCK_PLACE] side=SERVER worldTick=1078 block=minecraft:stone posX=10 posY=64 posZ=-3 placedBy=Developer
```

Field names are camelCase, order is stable per event type, values containing whitespace are quoted, and an absent optional value is omitted rather than filled with a placeholder. An empty field never appears, so `field=` is not something you need to parse for.

## Details that are easy to get wrong

**Commands need permission level 2.** In chat they take a leading slash, `/devtool ...`. In a server console they do not, `devtool ...`. Inside a bundle file they do not.

**A bundle command fails only when the game raises a command error.** A command that runs and changes nothing has succeeded. `kill @e[type=zombie,r=10]` matching nothing, and `clear @p` on an already empty inventory, both count as success, deliberately, so a teardown bundle survives a second run. Unknown command, missing permission and bad syntax all count as failure.

**Nothing is rolled back.** A bundle that stops half way leaves the world half prepared. `stopOnFailure` limits the damage; it does not undo it.

**Arena dimensions describe the interior.** `arena create 20 10 20` gives twenty blocks of usable space, with the shell outside that. The arena is centred horizontally on you, its floor level with your feet, one per dimension, capped by `maxArenaDimension` in the config.

**On a dedicated server, configuration and bundles live on the server**, under its own `config/devtool/`. `devtool reload` re-reads them there. A client copy has no effect on what the server runs.

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
