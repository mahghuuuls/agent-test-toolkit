# Pre-release verification pass

66 bundles in `run/config/devtool/bundles/`, files `v1_` through `v9_`. They cover the layer the
251 unit tests cannot reach: the Minecraft-typed command surface, the arena, the observers and
the bundle scheduler in a running game.

**Before you start**

- Use a disposable creative world. Several bundles place blocks, summon mobs, clear your
  inventory and kill you.
- Stand near world spawn. `v6_inspect_block` writes to the fixed coordinate `0 100 0`, which
  needs a loaded chunk.
- After each step, send me the new part of `latest.log`. Every bundle brackets its work with
  `MARK` records, so I can find the region without you trimming anything.

**Three defects are already known.** I found them reading the code while writing these bundles,
before running anything. Steps 3 and 8 are expected to fail until they are fixed, and I have
noted exactly where. Everything else should pass.

---

## 1. Launch check

Launch the client. Before touching anything, find the `STARTUP` record.

Expect `bundlesLoaded` to be about 70 (66 new plus the shipped examples) and `bundleProblems=0`.
Any `Bundle load problem` records name a file I got wrong; send those and I will fix them before
you go further.

## 2. The fast sweep

```
/devtool run v9_suite_fast
```

19 nested bundles covering read-only commands, sessions, reload, arenas and filters.

Expect a matching `BUNDLE_START` / `BUNDLE_END` pair per child, `failed=0` throughout, and one
record per command. The things worth checking closely:

- `v1_player_sender` — `PLAYER_INSPECT`, `INVENTORY_INSPECT`, `ENTITY_LIST` and `NBT` must all
  appear. These are the commands that silently did nothing before, so their presence is the
  regression check for this morning's defect.
- `v1_session` — records between the start and stop carry `session` and `sessionTick`; the ones
  after `AFTER_STOP` carry neither.
- `v1_reload_preserves` — session and category status identical either side of the reload.
- `v2_arena_boundary` — 64 succeeds, all three 65s fail.
- `v3_rejected_filter_changes_nothing` — the rejected arena filter must leave `block_break` OFF.

## 3. The failure sweep — EXPECTED TO FAIL

```
/devtool run v9_suite_failures
```

This is where the known defects show.

`v7_root_dispatch_defect` runs exactly one invalid command. Correct is `executed=1 failed=1`.

**It will report `failed=0`.** `DevToolCommand` logs the error, messages you, then returns
without throwing, so vanilla counts it as a success. Same defect class as the ten I fixed this
morning, in the dispatcher that routes to them.

`v7_must_fail_devtool` should report `failed=11` and will report `failed=10` for the same reason.

Everything else in this suite should be correct: `v7_must_fail_selectors` all failed,
`v7_must_fail_vanilla` all failed, and `v7_must_succeed_no_effect` reporting `failed=0` because a
`kill` that matches nothing has still run.

## 4. Mechanics

```
/devtool run v9_suite_mechanics
```

Takes a few seconds because of the delays.

- `v4_delay_spacing` — `durationTicks` near 40, not near 20. Both delays measured from the
  previous command, not from the start.
- `v4_delay_end_is_last` — `BUNDLE_END` after the delayed mark, never before.
- `v4_nest_parent` — order is `parent_BEFORE`, `child_ran`, `parent_AFTER`.
- `v4_child_failure_counts_once` — the failing child counts as exactly one failed command.
- `v4_stop_on_failure_true` — `v4_stop_SHOULD_NOT_APPEAR` must be absent, `stoppedEarly=true`.
- `v4_cycle_a` and `v4_self_cycle` — refused with an explicit record, no crash, no recursion.
- `v4_empty` — a `BUNDLE_START` and `BUNDLE_END` pair with nothing between.

## 5. Depth limit

Run standalone, not from a suite, so the chain starts at depth 1:

```
/devtool run v5_depth_01
```

Marks `v5_depth_01` through `v5_depth_10` must appear. **`v5_depth_11_SHOULD_NOT_APPEAR` must
not.** `v5_depth_10`'s call is refused with a nesting-too-deep record.

## 6. World effects

```
/devtool run v9_suite_world
```

- `v2_arena_clears_entities` — three mobs before the reset, none after, you still alive.
- `v6_inspect_block` — `BLOCK_INSPECT` and `NBT` for the chest at `0 100 0`.
- `v6_inspect_entity` — the named pig inspected and dumped, then removed.
- `v6_inventory_states` — `occupiedSlots=0` explicitly present after the clear, not omitted.
- `v6_nbt_truncation` — if `truncated=true`, then `nbtLength` and `outputLength` are both there.
- `v8_spawn_noise` — the pig produces a spawn record; the dropped item and the xp orb do not.

## 7. Observers — manual

```
/devtool run v8_observe_setup
```

Then, inside the arena it builds, do each of these once and note roughly what you did:

| Action | Expect |
|---|---|
| Place a stone block | one `BLOCK_PLACE` |
| Break it | one `BLOCK_BREAK` |
| Place the oak door | **one** `BLOCK_PLACE`, not two — a door is two blocks |
| Eat bread | `ITEM_USE` |
| Right-click a pig with an empty hand | **one** `ENTITY_INTERACT`, not two |
| Hit a pig with the sword | **one** `ENTITY_DAMAGE`, not three |
| Kill the pig | **one** `ENTITY_DEATH` |
| Use the spawn egg | `ENTITY_SPAWN` |
| Hold left-click on a block and keep holding | records must not stream continuously |

Then:

```
/devtool run v8_observe_teardown
```

The doubling cases are the ones that matter — each was a deliberate event-choice decision, and a
duplicate record means the wrong Forge event is subscribed.

Two more, run separately because they involve dying:

```
/devtool run v8_damage_correlation
/devtool run v8_death_single_record
```

`v8_death_single_record` kills you. One `ENTITY_DEATH` for the pig, one for you — a player death
posts the underlying event twice, so two records here would be a real defect.

## 8. Console — EXPECTED TO FAIL

Only on a dedicated server. From the server console, not in game:

```
devtool inspect player
```

Correct is an explicit failure. **It will report success**, because `DevToolCommand` returns
instead of throwing when a subcommand needs a player and the sender is the console. This is the
second half of the same defect as step 3.

## 9. Arena persistence across restart

1. `/devtool arena create 9 5 9 minecraft:stone`
2. `/devtool arena info` — note the coordinates
3. Quit to the title screen, then fully quit the game
4. Relaunch, load the same world
5. `/devtool arena info`

Same arena, same coordinates. This is the one check that proves `markDirty` is actually being
called; without it the arena lives only in memory and is lost on shutdown.

While you are there, this also covers the session rule: after step 3 the session state should
**survive** returning to the title screen within one launch, but not survive a full quit.

## 10. Tier 4 — client without the toolkit

The README claims a client without the toolkit can join a server that has it. Nothing has ever
tested this.

1. Start the dedicated server with the mod
2. Join from a Forge client that does **not** have the mod installed
3. Confirm the connection succeeds and the server's `latest.log` still records your actions

If this fails, `acceptableRemoteVersions = "*"` is not doing what the class comment says and the
README claim has to come out before release.

---

## What to send

For each step: the new section of `latest.log`. The marks make the boundaries obvious. For step 7
also tell me roughly what you did and in what order, since the log alone cannot tell me whether
you placed one door or two.

If something crashes, send the crash report path rather than the log.
