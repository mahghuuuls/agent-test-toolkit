# Verification bundles

132 bundles used for the pre-release verification passes: the first release, then one file per reported item in each feedback cycle. They exercise the Minecraft-typed layer
that the unit tests cannot reach: the command surface, the arena, the observers and the bundle
scheduler in a running game.

**To use them,** copy the `v*.json` files into the toolkit's bundle directory and reload:

```
run/config/devtool/bundles/
```

```
/devtool reload
```

Then follow [checklist.md](checklist.md) for `v1` to `v10`. The later files were run from the cards in the process repository's cycle folders, `cycles/CYCLE-00N-*/campaign-*-card.md`, which say what to type and in what order; `v10`, `v25` and `v26` need a dedicated server, and `v24` needs a stop and restart.

They live here rather than in the mod repository because `run/` is ignored and would lose them,
and because they are development assets rather than something a user of the mod needs.

## What each file covers

| File | Bundles | Area |
|---|---|---|
| `v1_core` | 5 | Read-only commands, sessions, reload preservation, the player-sender regression |
| `v2_arena` | 8 | Lifecycle, defaults, replace, the 64/65 size boundary, smallest case, even-size rounding, idempotent reset, entity clearing |
| `v3_logging` | 6 | All eight categories, toggling, radius and arena filters, filter survival, rejected-filter safety |
| `v4_mechanics` | 15 | Delay spacing, end-after-delay, nesting, delayed children, child failure counting, both stop-on-failure modes, cycles, empty bundle |
| `v5_depth` | 11 | The nesting limit, built to run one link past `MAX_DEPTH` |
| `v6_inspect` | 6 | Block, entity, player and inventory inspection, NBT dumps, radius sweeps, truncation |
| `v7_failures` | 7 | Everything that must fail, plus the must-succeed no-effect rule |
| `v8_observers` | 5 | Observer setup for manual actions, spawn noise, damage correlation, death records |
| `v9_suite` | 4 | One-command sweeps grouping the above |
| `v10_disconnect` | 3 | The nested-sender case, which needs a dedicated server and a real disconnect |
| `v11_sender_identity` | 3 | The 1.0.1 sender-identity fix, from a player and from the console |
| `v12_tolerated_failures` | 3 | `toleratedFailures`: expected, narrow, and undeclared still halts |
| `v13_container_inspect` | 3 | `inspect container` on a chest and on a block that is not one |
| `v14_adt001_repro` | 3 | The 1.2.0 reproduction card for a selector miss after a listing; not reproduced |
| `v15_marker_trace` | 3 | Marker labels in chat, `trace` step records and their absence without the flag |
| `v16_lookup` | 3 | Lookup by UUID and entity id, and the zero-match record |
| `v17_provenance` | 1 | `BUNDLE_SOURCE` hashes carried into start and end records |
| `v18_await` | 6 | Chunk waits: satisfied, timed out, tolerated, after a teleport, oversized |
| `v19_operator_placeholders`, `v19_broken_placeholder` | 5 | Operator binding, the four placeholder forms, unbound and unknown placeholders |
| `v20_checkpoint` | 4 | Arm and ack, arm timeout, the three cue forms |
| `v21_assert` | 6 | Every assertion predicate, pass, fail, tolerated, unloaded, absent when loaded |
| `v22_result` | 5 | Result files, nested parents and children, the unflagged control |
| `v23_fixtures` | 5 | Claim, duplicate, unloaded, forged tag, cleanup; run away from the spawn chunks |
| `v24_scenario` | 4 | Scenario checkpoint across a real stop and restart, wrong bundle, changed hash |
| `v25_dedicated` | 5 | Dedicated-server cases: cues, disconnect while armed, dimension change mid-wait |
| `v26_cross_dimension` | 3 | An overworld fixture looked up by UUID from the Nether |
| `v27_tolerated` | 3 | The `TOLERATED` record: a selector miss, a declared key, and a control with none |
| `v28_origin` | 3 | Run origin (`caller`, `channel`, `permissionLevel`) from chat and nested, and the closest-name suggestion |
| `v29_inbox` | 2 | A bundle started through the inbox, and a bundle that tries to open the inbox and is refused |
| `v30_far_inspect` | 1 | Inspection of an unloaded position refused with `devtool.inspect.notLoaded`, nothing generated |
| `v31_loot` | 1 | `inspect container` on a chest whose loot has not been rolled, and on a plain chest |
| `v32_save` | 2 | `devtool save` in single player and on a dedicated server with `save-off` (dedicated only) |
| `v33_world` | 2 | A session across reopening the same world and opening another (owner switches worlds) |
| `v34_attributes` | 2 | Attribute inspection and assertions on four iron armor pieces, and a non-living target |
| `v35_reload` | 1 | Reload at world start: edit the mark at the title screen, then open a world |
| `v36_screenshot` | 2 | A screenshot on request with the inventory open, and a refused label |
| `v37_baubles` | 3 | Baubles set, inspect, refusal and relog (`-Pdev_runtime_baubles=true`), and the absent key without it |

## What they found

Five defects, all of which had survived 251 passing unit tests and 24 issues of verification.

1. An unknown subcommand reported **success**. Vanilla counts any command that returns without
   throwing, so a bundle carrying a typo reported `failed=0` and ran on past it. Observed
   defeating `stopOnFailure` in `v4_failing_child`.
2. The same, when a subcommand needed a player and the sender was a console.
3. The same again for an unknown `session` action, which had no `return` to find at all: it was
   the last branch of an if-else chain and simply ended.
4. Nested bundles held their sender instead of re-resolving it, so a bundle kept running after
   the player who started it had disconnected.
5. Right-clicking a block produced two records, one per hand. The suppression for this existed
   but had been applied to entities only.

Two design gaps were also recorded rather than fixed, since they are scope decisions:

- Nothing in `latest.log` reports which logging categories are enabled. `log status` answers
  that question to chat only, so an agent reading the log cannot tell whether a category was on.
- `commands.clear.failure` is not in the tolerated "ran but changed nothing" set, so a teardown
  bundle containing `clear @p` halts on its second run. That is the exact scenario the tolerated
  set exists to prevent.

## A note on writing these

Two bundles initially asserted the wrong thing, and both were my error rather than the mod's:

- Relative coordinates were expected to fail. They resolve correctly from a player sender; only
  a console sender is refused, because it has no meaningful position.
- `minecraft:oak_door` is not an item id in 1.12.2. The item is `minecraft:wooden_door`.

Worth recording because a test suite that asserts the wrong expectation is worse than no test:
it produces a confident failure that sends you looking in the wrong place.
