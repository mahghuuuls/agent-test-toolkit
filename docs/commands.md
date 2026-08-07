# Command reference

The root command is `/devtool`, with `/att` as an alias.

The alias is a fallback, not a guarantee. Minecraft resolves duplicate command names by last registration wins, and an alias never displaces another command's own name. So a mod claiming `devtool` leaves `/att` working. But a mod whose own command **is** `att` takes the alias, and nothing reports that this happened.

All commands require permission level 2.

## Diagnostics

| Command | Effect |
| --- | --- |
| `devtool help` | List subcommands |
| `devtool capabilities` | Report this build's version, commands, inspection types and logging categories, read from the live registry |
| `devtool environment` | Minecraft and Forge versions, server context, dimension, difficulty, position |
| `devtool mods` | Every loaded mod id and version, one record each |

`capabilities` is derived from the running build, never from a written list, so it cannot claim a feature the jar does not have.

## Logging

| Command | Effect |
| --- | --- |
| `devtool log <category> on` | Enable a category |
| `devtool log <category> on arena` | Enable, restricted to this dimension's arena |
| `devtool log <category> on radius <n>` | Enable, restricted to within n blocks of where you stand |
| `devtool log <category> off` | Disable a category |
| `devtool log all off` | Disable everything and clear all filters |
| `devtool log status` | Every enabled category and its filter |

See [logging.md](logging.md) for the categories and the record format.

## Sessions and marks

| Command | Effect |
| --- | --- |
| `devtool session start <name>` | Start a session |
| `devtool session stop` | Stop it |
| `devtool session status` | Report the active session |
| `devtool mark <label>` | Write a labelled marker record |

## Bundles

| Command | Effect |
| --- | --- |
| `devtool bundle list` | Loaded bundles and their command counts |
| `devtool bundle show <name>` | One bundle's commands, in order, with delays |
| `devtool run <name>` | Run a bundle |
| `devtool reload` | Reload bundles and configuration from disk |

`reload` does not disturb an active session, enabled categories, their filters, or a bundle already running. A bundle in flight completes with the commands it started with.

See [bundles.md](bundles.md) for the file format.

## Arena

| Command | Effect |
| --- | --- |
| `devtool arena create [w] [h] [l] [block]` | Build an arena centred on you |
| `devtool arena info` | Bounds, origin, start position and construction block |
| `devtool arena reset` | Rebuild the structure and empty the interior |
| `devtool arena clear` | Empty the interior without rebuilding structure |

Width, height and length describe the **interior**. `create 20 10 20` gives twenty blocks to walk in.

One arena per dimension, stored in the world save. Creating a new one replaces the record without restoring the previous terrain.

`reset` is idempotent and safe to run at the start of every test. Neither `reset` nor `clear` removes players.

Creating an arena also moves your respawn point to its start position, unless `setRespawnPoint` is disabled in the config.

Minecraft revalidates a respawn point when you die, and falls back to the world spawn if the start position has become obstructed. When that happens it tells you **"Your home bed was missing or obstructed"**, which is misleading here because no bed is involved. If you die and land at world spawn after seeing that message, the arena start was blocked, not lost.

**These commands are destructive and do not prompt.** See the warning in the README.

## Inspection

| Command | Effect |
| --- | --- |
| `devtool inspect player [selector]` | Position, health, hunger, experience, gamemode, held items, effects |
| `devtool inspect entity <selector>` | Registry id, position, motion, health, effects |
| `devtool inspect block <x> <y> <z>` | Block id, metadata, blockstate, tile entity class |
| `devtool inspect inventory [selector]` | Occupied slots across main inventory, armour and offhand |
| `devtool entities nearby <radius>` | Every entity within the radius, one record each |
| `devtool nbt entity <selector>` | Raw NBT for an entity |
| `devtool nbt block <x> <y> <z>` | Raw NBT for a tile entity |
| `devtool nbt held` | Raw NBT for the held item |

A selector matching more than one entity is an error, not an invitation to pick one. Name your fixtures and select on the name.

NBT goes to the log, never to chat, and is truncated at `maxNbtOutputLength` characters. Truncation is always reported along with the original length, so you can tell whether raising the limit would recover the rest.

Inspection reports what Minecraft and Forge expose. A modded tile entity's class is named; its contents are not interpreted.
