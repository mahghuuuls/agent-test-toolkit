# Verification bundles

70 bundles used for the pre-release verification pass. They exercise the Minecraft-typed layer
that the unit tests cannot reach: the command surface, the arena, the observers and the bundle
scheduler in a running game.

**To use them,** copy the `v*.json` files into the toolkit's bundle directory and reload:

```
run/config/devtool/bundles/
```

```
/devtool reload
```

Then follow [checklist.md](checklist.md).

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
