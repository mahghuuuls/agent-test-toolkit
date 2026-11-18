A development tool for people who write Minecraft mods, and for the AI coding assistants that help them. It is not a gameplay mod: it adds no items, blocks or recipes. In the game's mod list it appears as Agent Test Toolkit.

It automates tests that need only input the toolkit can make: key presses, clicks, turning toward a target, and clicks inside an open screen. Your assistant prepares a test world, has your game make that input, and then reads from the log what the game did. You launch the game. A test that needs your own judgment, or input the toolkit cannot make, such as dragging items across slots, still needs you. For those, a command bundle sets up the world and the log records what happened.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

**Use a test world.** Arena commands replace blocks and remove entities at once, without asking and without undo. Do not add this mod to a pack meant for play; a test copy of a pack is fine.

## What it does

It has no Java API, and your mod does not depend on it: you use it through commands, bundle files, request files and the log. It sees only what Minecraft and Forge show, so your mod still logs its own decisions.

**Sets up a test in one step.** A command bundle is a named list of commands, kept in a JSON file in `config/devtool/bundles/` (one file can hold several) and run with `/devtool run <name>`. It can build an arena (an enclosed, lit, empty space that one command resets between attempts), set game rules, give items, turn on logging, wait for an area to load (or to be decorated, with its trees and ores placed), and stop at the first failure.

**Writes what the game did into the log.** Block placing and breaking, spawns, deaths, damage, interactions and item pickups, each category off until you turn it on. On request, the state of a player, entity, block (with its light level), chunk or inventory. It can also list where a given block appears in the loaded part of an area. Each is one line a program can read, in `latest.log` next to your own mod's output. A bundle can also compare what it sees with a value you give it, such as a player's health, and log the result.

**Acts as the player.** Your assistant can have your own game pick a hotbar slot, click, hold a mouse button for a set time (to draw a bow, for example), drop the held item or open the inventory. It can press or hold any key binding by name (walk, jump, sneak, or another mod's key), several at once, turn to a direction or to face an entity or block, and switch the camera view. It can also show an item's tooltip and write its lines as text, with Shift, Ctrl or Alt held where the game's keyboard library allows it (the step is refused otherwise). Each step can be delayed and followed by a screenshot, which can leave out the chat, the toasts or the whole HUD. With [Gnetum](https://www.curseforge.com/minecraft/mc-mods/gnetum) installed, chat cannot be hidden from screenshots; hiding the whole HUD still works.

By default the game handles a key or click as a player's. For a mod that listens to Forge's input events or reads the keyboard or mouse itself, a key or click can go through those instead. For example, [Spartan Shields](https://www.curseforge.com/minecraft/mc-mods/spartan-shields)' shield bash responds to a click sent through Forge's input event, and [RLCombat](https://www.curseforge.com/minecraft/mc-mods/rlcombat)'s own attacks respond to a click sent through the game's input queue (tested on Cleanroom).

**Works inside open screens.** It lists the open screen's buttons and slots, clicks a slot (with the left or right button, with Shift, or with a number key for a hotbar swap), a button by its label, or a point on the screen, scrolls, and writes a slot's tooltip as the screen draws it. Controls a mod keeps outside the screen's button list are not listed; click them by screen position.

It can also leave the world and come back, with a full logout if the test needs one, press Respawn after a death, close the open screen, or close the game.

**Takes requests from files.** With the command inbox open, your assistant starts bundles, player input and read-only commands by writing small files, and reads the results from files next to them. Bundles started this way keep their command output out of the chat, and when a test needs you, the prompt shows in a panel on the right of the screen, where the chat does not cover it. A menu or book that pauses single player holds the inbox until it closes.

**Client request channel.** With `client.requestChannel` on, your assistant can also act from the title screen the same way: create a test world, open a save (reporting or answering Forge's missing-blocks or old-world question as it opens), join a server on your own computer or local network, list the current menu or take a screenshot of it, take a screenshot of a mod's entry in the Mods list, or quit. In a world, it can close a menu or book that pauses single player.

## Installation and settings

It runs on the server, so in single player install it as usual. Everything driven on your client (player input, rejoining, screenshots, client defaults, the client request channel, opening the inbox at your join to a dedicated server) also needs it there, at the same version as the server; an older client is refused only the newer features. A client without it can still join. Commands need operator rights (cheats on in single player); start with `/devtool help`.

Out of the box it only runs commands you type. The settings are in `config/devtool/devtool.cfg`, each described in the file. For a test where you only launch the game, after the first launch set these lines in that file before the next launching:

```
client {
    B:allowActions=true
    B:applyOnJoin=true
    B:dismissTutorial=true
    B:noPauseOnLostFocus=true
    B:requestChannel=true
}

inbox {
    B:enabled=true
    B:openOnLocalJoin=true
}
```

In single player, the client and the server read this one file. `allowActions` lets your assistant drive player input and the rejoin, respawn, close and quit steps; `requestChannel` opens the client request channel; the two `inbox` keys open the inbox by itself at your own join. `noPauseOnLostFocus` keeps the game running while its window is in the back, and works only with `applyOnJoin`, which also sets full brightness and muted music. `dismissTutorial` keeps the tutorial's hints out of tests and screenshots. These client defaults (brightness, music, focus pause, tutorial) are saved in `options.txt` and stay after the keys are turned off. Your assistant needs to read and write the game folder. On a development server in offline mode, `inbox.localJoinOperator` gives you operator rights while you are joined from your own computer and game folder, and takes them back when you leave.

## Documentation

Point your assistant at [`AGENTS.md`](https://github.com/mahghuuuls/agent-test-toolkit/blob/main/AGENTS.md): it lists every command and log line, with worked examples. For a person, [`README.md`](https://github.com/mahghuuuls/agent-test-toolkit/blob/main/README.md) is the place to start, then [`docs/commands.md`](https://github.com/mahghuuuls/agent-test-toolkit/blob/main/docs/commands.md), [`docs/bundles.md`](https://github.com/mahghuuuls/agent-test-toolkit/blob/main/docs/bundles.md) for the bundle format and [`docs/logging.md`](https://github.com/mahghuuuls/agent-test-toolkit/blob/main/docs/logging.md) for the log lines. Example bundles are written to `config/devtool/bundles/` on first run.
