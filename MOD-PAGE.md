A development tool for people who write Minecraft mods, and for the AI coding assistants that help them. It is not a gameplay mod: it adds no items, blocks or recipes. In the game's mod list it appears as Agent Test Toolkit.

It automates the tests of your mod that need only a little player control. Your assistant prepares a test world, has your game click, hold a button, drop an item or open the inventory, and then reads from the log what the game did. You launch the game. A test that needs real aiming, walking around, or work inside another mod's screens still needs you; for those, a bundle still sets up the world and the log still records what happened.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

**Use a test world.** Arena commands replace blocks and remove entities at once, without asking and without undo. Do not add this mod to a pack meant for play.

## What it does

**Sets up a test in one step.** A bundle is a list of commands in a JSON file in `config/devtool/bundles/`, run with `/devtool run <name>`. It can build an arena (an enclosed, lit, empty space that one command resets between attempts), set game rules, give items, turn on logging, wait for an area to load, and stop at the first failure.

**Writes what the game did into the log.** Block placing and breaking, spawns, deaths, damage and interactions, each category off until you turn it on, and on request the state of a player, entity, block or inventory. Each is one line a program can read, in `latest.log` next to your own mod's output. A bundle can also compare what it sees with a value you give it and log the result.

**Player actions, in small steps.** Your assistant can ask your own game to pick a hotbar slot, right click or left click once, hold a mouse button for a set time (to draw a bow, for example), drop the held item, open the inventory, or show an item's tooltip and write its lines as text, optionally with Shift, Ctrl or Alt held (holding those keys is not available on Cleanroom). Each can be delayed and followed by a screenshot. The game handles the click as a player's click, though mods that read raw mouse input do not see it. The toolkit does not aim: a bundle points the player with a teleport first. It can also leave the world and come back, with a full logout if the test needs one, press Respawn after a death, close the open screen, or close the game.

**Files your assistant writes.** With the command inbox open, your assistant starts bundles, player actions and read-only commands by writing small files, and reads the results from files next to them. A separate client setting lets it act from the title screen the same way: create a test world, open a save, join a server on your own computer, or quit.

## Installation and settings

It runs on the server, so in single player install it as usual. Player actions, rejoining, screenshots and title-screen requests also need it, version 1.7.0, on your client. A client without it can still join. Commands need operator rights (cheats on in single player); start with `/devtool help`.

Everything that acts without you typing is off by default. The settings are in `config/devtool/devtool.cfg`, each described in the file (`inbox.enabled` is written `B:enabled=true` under `inbox` there, and so on): `inbox.enabled` lets the inbox be opened (`/devtool inbox open`, or by itself at your own join with `inbox.openOnLocalJoin`), `client.allowActions` allows player actions on your client, and `client.requestChannel` allows the title-screen requests. On a development server in offline mode, `inbox.localJoinOperator` gives you operator rights while you are joined from your own computer and game folder, and takes them back when you leave.

## Documentation

Point your assistant at `AGENTS.md`: it lists every command and log line, with worked examples. For a person, `README.md` is the place to start, then `docs/commands.md`, `docs/bundles.md` for the bundle format and `docs/logging.md` for the log lines. All are in the [source repository](https://github.com/mahghuuuls/agent-test-toolkit).
