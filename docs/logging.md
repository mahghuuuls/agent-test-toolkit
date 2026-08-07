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

`side` is always reported. In this version observation is server side only, so it is effectively constant, but it is emitted so the format does not change when client side observation is added.

`worldTick` comes from the dimension in which the event occurred. Dimensions keep independent tick counts, so records from different dimensions are not comparable on this field.

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

## Sessions

```
devtool session start spell_damage
devtool session stop
```

While a session is active, every record carries `session` and `sessionTick`, the latter counted from the session's start. Sessions survive returning to the title screen and leaving a world. They end when the game does.

## Marks

```
devtool mark ABOUT_TO_CAST
```

A marker record with a label and nothing else. It asserts nothing and carries no verdict. Use it to find the interesting part of a long log, and put one before each step when a human is performing a sequence, so that a missing record can be told apart from a step that was never reached.
