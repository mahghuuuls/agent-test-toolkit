# Changelog

## 1.0.0

First release.

### Added

- **Command bundles.** Ordered lists of commands in JSON, run with one command. Per command tick delays, stop on first failure, and nesting with cycle detection. Bundles live in the config folder rather than a world save, so they survive making a fresh test world, and they mark their own start and end in the log. A command that runs and changes nothing counts as a success, so teardown bundles stay re-runnable.
- **Test arenas.** An enclosed, lit, empty box with deterministic bounds, one per dimension, stored in the world save. Reset repairs the structure and clears the interior, and is safe to run repeatedly. Creating an arena moves your respawn point into it.
- **Event logging.** Eight categories of generic game event, all disabled by default: block placement and breaking, entity spawn, death and damage, player and entity interaction, and item use. Each can be narrowed to an arena or a radius. Every change to what is being recorded is itself written to the log, so an absent event can be told apart from a category that was never on.
- **Inspection.** Player, entity, block, inventory, nearby entity listing, and raw NBT, reported as structured records. NBT output is bounded and truncation is always reported.
- **Sessions and markers.** A session names and timestamps a run of records. A marker is a labelled bookmark placed before the action being tested.
- **Self reporting.** Environment, loaded mod list, and a capability report read from the running build rather than from documentation.
- **Join automation.** Optionally run a bundle when an operator joins. Disabled by default, and the bundle name is empty by default.
- **Client defaults.** Optionally set brightness and music volume on joining a world. Disabled by default.
- **Example bundles.** Written to the config folder on first run only, never overwritten afterwards. Includes a lit nether portal and a ready end portal, both of which are ordinary commands rather than mod features.

### Notes

- Requires Forge for Minecraft 1.12.2. Everything it records happens on the server, so that is where it installs. In singleplayer that just means installing it.
- Arena creation and reset are destructive, immediate, and cannot be undone. Use a disposable world.
- Everything that could change your world or your settings without being asked is off by default.
