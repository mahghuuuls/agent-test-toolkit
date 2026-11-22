# Changelog

## Unreleased (1.10.0)

### Fixed

- **The Baubles commands work with Bubbles.** `devtool baubles set`, `baubles clear` and the bauble part of `inspect inventory` failed with Bubbles 2.4.10, a reimplementation of Baubles. The toolkit now calls only the Baubles methods the installed provider has, calls the item's equip and unequip code exactly once per change, and names slot types (`slotType=ring`) on both providers. A provider it cannot drive fails the command with `devtool.baubles.unsupported`.

### Added

- **Waiting for the client.** In a bundle, the player actions (`select`, `use`, `attack`, `hold`, `drop`, `inventory`, `tooltip`), `client key ... press|hold`, `look`, `face`, `view` and the screen steps (`screen`, `click`, `scroll`, `tooltip container`) take `wait=started|done` and `waitTicks=<1-1200>`; `reconnect`, `respawn`, `close`, `quit` and `focus` do not. The bundle pauses until the client answers, and a refused or failed step fails with `devtool.client.actionFailed` and the client's reason; a hold cut short fails with `devtool.client.releasedEarly`. `devtool client await <use|attack|binding>` waits for a running hold. The wait writes `AWAIT condition=client`.
- **The expected target.** `client attack`, `use`, `hold` and `face` take `expect=<selector|uuid>`. A click on another entity fails with `devtool.client.wrongTarget`, on a dying one with `devtool.client.targetDead`; none or several matches are refused at once (`devtool.client.noTarget`, `devtool.client.ambiguousTarget`). Entity targets in the client records add `alive` and `health`.
- **`devtool assert entity <selector|uuid> health <n>`** checks any living entity's health at two decimals.
- **Picture reports.** A 1.10.0 client reports every picture asked with `devtool screenshot`, and the server writes `SCREENSHOT side=SERVER action=saved` or `action=failed` for each. A picture taken through a client step's `screenshot=` is not reported. `devtool await screenshot <label> [timeoutTicks]` pauses a bundle until the picture is saved, or fails with `devtool.screenshot.failed`. An older client is refused at once with `devtool.screenshot.noReport`.
- **Hiding the chat history.** The client option `client.hideChatHistory` and the key binding "Toggle chat history" (unbound by default) hide the chat lines in play and in the chat screen while the typed line still shows. Each change writes `CLIENT_OPTION`. While hidden, a toolkit failure line also shows in the prompt panel.
- **A late failure line.** A client step a single inbox request sent, which its client reports refused or failed after the request was answered, shows one red chat line when the report arrives.
- **One NBT value by path.** `devtool nbt entity|block|held ... path=<path>` writes one value as the new `NBT_VALUE` record, with paths such as `tag.ench[0].lvl` or `ForgeCaps."modid:cap".value`; for the held item the path reads the whole saved stack. `devtool assert nbt entity|block|held ... <path> <value>` compares it: as numbers at two decimals when both are numbers, otherwise as exact text.
- **A radius filter that follows.** `devtool log <category> on radius <n> follow` centres the filter on the player who turned it on, wherever they are at each event. While that player is offline nothing is recorded, and the new `LOGGING filter=paused reason=playerGone` record says so.
- **Why a connect or an open failed.** A client request channel `connect` or `open` that ends on the Disconnected screen (`connectFailed`, `loadFailed`) records the screen's `disconnectTitle` and `disconnectMessage` and `waitedMs`.
- **Which client answered.** The server's `CLIENT_ACTION action=requested` and `action=reported` records carry `playerUuid` and `connection`, so two clients, or one client before and after a rejoin, are told apart.
- **Tolerance records say where a tolerance came from.** `TOLERATED` carries `source=builtIn` or `source=declared`; `BUNDLE_END`, the result file and the inbox answer add `toleratedBuiltIn` and `toleratedDeclared` beside `tolerated`.

### Changed

- **Only five commands pass on "nothing matched" without a declaration.** In 1.9.0 a failure that only said no entity or player was found, or nothing was there to clear, was tolerated for every command. Now such a failure is tolerated only for `kill`, `clear`, `effect <target> clear`, `scoreboard players reset` and `scoreboard players tag <target> remove`. `testfor`, `execute`, `tp`, `teleport`, `give` and every other command now fail on it unless the step declares the key in `toleratedFailures`.
- **Raw `nbt` and `log ... radius <n>` refuse extra words.** 1.9.0 ignored words after the target or the radius, so a mistyped `path=` or `follow` gave an answer nobody asked for. Both are now refused, inbox requests included.
- **`face` records name the target after the turn**, as the client sees it then.
- **Quiet single inbox requests.** A successful inbox request other than `run` no longer sends a chat reply; its answer file and the log carry the result. `devtool checkpoint ack` sends no reply when every checkpoint it answers came from a quiet run. Failures still show one red line.
- **A logout forgets unanswered lifecycle steps.** A respawn, close or quit that a player's client had not answered when the player logged out is forgotten, so an answer after a rejoin is dropped rather than taken. A reconnect's own request is still kept across the logout it causes.
- **An equal bauble is not equipped again.** Setting a bauble slot to a stack equal to the one already there no longer calls the item's unequip and equip code, with official Baubles and with Bubbles.
- **`effect <target> clear` passes when there is nothing to clear.** Its `commands.effect.failure.notActive` and `notActive.all` failures are now tolerated without a declaration, as the other removal commands' are.
- **A wait can fail with a key.** `AWAIT` gains `outcome=failed` with `key`: a client step's wait adds `detail`, and a picture's wait adds `label`, `requestTick` and `saved`.
- **The `devtool.client.noTarget` message** now reads "No step: ... has no single loaded target for face or expect", since `expect=` uses it too. The key is unchanged.

## 1.9.0

The theme is fewer steps for the person at the keyboard: keys by name, turning the view, clicks that other mods hear, and work inside open screens and menus, so more of a test runs while the person only launches the game. It also fixes a reconnect that could freeze single player, keeps bundle output out of the chat, and shows the toolkit's prompts where the chat cannot cover them. The items came from other projects' test sessions with the toolkit and from the developer.

### Fixed

- **The logout reconnect no longer freezes single player.** In 1.8.0, `devtool client reconnect [awaySeconds] logout` could let the integrated server stop on its own while the client waited for it forever. The client now pauses the game first, with its own "Leaving..." screen, waits until the integrated server proves the pause, and then leaves in one tick as Save and Quit does: the player is logged out once and the reconnect comes back as before. The records add `leave=paused` and `leaveWaitMs`. When the pause is not proven within 10 seconds, the player stays in the world and the reconnect ends `failed reason=leaveTimeout`; another screen over the pause screen, or a leave by the player, ends it `failed reason=leaveInterrupted` before anything is sent. Without `logout`, and on a dedicated server, the reconnect is as before.

### Added

- **Keys by name.** `devtool client key <binding> press|hold <1-1200>|release` acts on one key binding of your own client, named as in `options.txt` without `key_`: `key.forward`, `key.jump`, `key.sneak`, or a mod's own. A bundle can now walk, jump, sneak off a mount or press a mod's key. The record names the binding, its key, modifier and context, the route, and the client tick the key went down and up (`pressedTick`, `releasedTick`). Refused `unknownKey`, `ambiguousKey` or `unbound`; a press the game counted on another binding fails `notCounted` and names it in `countedOn`.

- **Key routes.** By default a key is set through its binding (`route=binding`). `raw=true` also holds it in the keyboard library's own key state, for code that reads the keyboard directly. `route=queue` puts a real key event into the game's input queue, so the game runs its whole key path and posts Forge's `KeyInputEvent` itself, which a mod that listens only to that event needs: Hwyla's configuration key opens its screen through `route=queue` and not through the default route. Where the running game's input library offers no such state or queue, the step is refused `routeUnavailable`.

- **`devtool client keys [prefix]`** lists the key bindings, one `KEY_BINDING` record each, with `duplicate=true` for a name two bindings share. It changes nothing.

- **Several holds at once.** Holds are kept per key. `keepHold=true` on `use`, `attack`, `hold`, `key ... press` and `key ... hold` leaves the running holds, and a running turn, going: walk and sneak together, or keep a shield raised and attack. Any other step still ends them first (`released=nextAction`), and `key <binding> release` ends only that binding's hold. A press of the attack, use or pick-block binding while an item is in use says whether the game took it (`taken=true` or `false`).

- **Click routes.** `client use` and `client attack` take `route=binding|event|queue`. `binding`, the default, is the 1.8.0 click. `event` also posts Forge's `InputEvent.MouseInputEvent` right after the press; `queue` puts a real mouse-button press and release into the game's input queue, so the game's whole click path runs, Forge's `MouseEvent` included. Spartan Shields' shield bash answered `route=event` (shield raised with `client hold use 40`, then `client attack route=event keepHold=true`) and not the default click. RLCombat's own attack handling (its off-hand attack, a swing through a cobweb) answered only `route=queue` (tested on Cleanroom; see Known Issues); RLCombat ignores clicks while an item is in use, so a queued click during a held use does nothing there.

- **Turning, facing and the camera.** `client look <yaw> <pitch> [over=<ticks>]` turns your view as the mouse does, at once or evenly over up to 200 ticks. `client face <selector|uuid>` turns it to an entity's eyes, `client face block <x> <y> <z> [face]` to a block's centre or one of its faces; a target that is not loaded is refused with `devtool.client.noTarget`. The rotation reaches the server as your own. `client view first|back|front` sets the camera as F5 does. An `attack` after `face` hits only within the game's own reach (3 blocks in survival), so put the target close.

- **Focus, emulated.** `client focus lost|gained` does what the game does when its window loses or regains focus, without touching the window: keys are let go, holds and turns end `released=focusLost`, and the pause menu opens when the game pauses on lost focus. The records say `emulated=true windowChanged=false`.

- **Screens.** `client screen` writes the open screen's class, size and scale, its buttons and, for a container, its slots (`SCREEN_INFO`, `SCREEN_BUTTON`, `SCREEN_SLOT`). `client click slot <n> [left|right|shift|key=<1-9>]`, `client click button <id>` or `button label=<text>`, `client click at <x> <y> [left|right]` and `client scroll <amount>` act on the open screen through its own handling, as a person's click does; `expect=<screen class>` refuses a click on the wrong screen (`screenChanged`). Inventory Tweaks' sort button, named by its label, sorted a chest. Controls a screen keeps outside its button list (Hwyla's configuration screen, for one) are not listed. Dragging across slots is not offered.

- **A container slot's tooltip as drawn.** `client tooltip container <slot> [advanced] [shift] [ctrl] [alt]` draws the tooltip of a slot in the open container through the screen's own drawing and writes its lines, including a line the screen adds only while drawing, such as the creative search tab's category. `screenshot=<label>` takes a picture with it.

- **More client request channel steps.**
  - `close <screen class>`, in a world, only while single player is paused: Esc on the open screen, so a book or a mod's screen that pauses the game no longer leaves the inbox waiting for a person. A screen that asks a person to decide is refused `confirmScreen`.
  - `open <folder> onQuestion=report|confirm|cancel`: when a world asks a question as it opens (an old save, blocks or items of a mod no longer installed), `report`, the default, ends the step at once with `failed reason=question`, the kind and the text, instead of after the 60-second deadline; the screen stays for a person. `confirm` and `cancel` answer it as its buttons do. `confirm` on missing blocks makes Forge back the save up, then open it with each missing block kept as an invisible stand-in that acts as air; use it on copies of test saves.
  - `screen` and `screenshot <label> [hide=...]` at a menu: list the menu's buttons, or picture the title screen, another menu, or the screen of a lost connection.
  - `mods <modId> [screenshot=<label>]` at the title screen: opens the Mods list (Forge's, or Cleanroom's own), selects the mod, waits until its name, version and logo are drawn, takes the picture, and goes back.

- **`hide=toasts`, and lists.** A screenshot (the command, a player action's `screenshotHide`, a channel request) can leave the toasts out, and `hide` takes a comma list such as `hide=chat,toasts`. The toasts come back in order right after the picture.

- **`client.dismissTutorial`** (off by default) sets the tutorial to done at game start, so its hints never cover a test or a picture.

- **The prompt panel.** For a client with toolkit 1.9.0, `checkpoint arm` and `checkpoint cue` show in the toolkit's own panel on the right of the screen, with the acknowledgement command for an arm: below the toasts and the effect icons, above the subtitles, clear of a scoreboard sidebar and of the chat. It goes at the acknowledgement, at the timeout, or when the player leaves; F1 and `hide=hud` leave it out. `CHECKPOINT` records the `form`.

- **Quiet bundles.** While a quiet run's command executes, vanilla command feedback and the toolkit's own replies reach nobody's chat; the server log keeps the vanilla line, and a failed command still shows one line naming the bundle, the command and the key. `devtool run <name> quiet=true|false`, the new alias `devtool bundle run <name>`, or `"quiet"` in the bundle file decide it, and a nested run follows its parent. Chat a command sends by itself, such as `list`, `say` or `tellraw`, is not stopped. `BUNDLE_START` and `BUNDLE_END` carry `quiet`, and `BUNDLE_END` counts the kept-back lines in `chatSuppressed`.

- **`devtool fixture clear <handle|all> strict`** fails with `devtool.fixture.notLoaded` when a fixture is not loaded, so `stopOnFailure` stops the bundle; the handle is kept. Without `strict`, as before.

- **`viewDistance`** on the record of an `await chunks` timeout.

- **Four new record types**, `KEY_BINDING`, `SCREEN_INFO`, `SCREEN_BUTTON` and `SCREEN_SLOT`, for 56 in total. `MENU_STEP` gains `action=question` and `action=answered`.

### Changed

- **Bundles the inbox starts are quiet by default.** Their command output no longer reaches the chat. Write `run <bundle> quiet=false` in the inbox request, or `"quiet": false` in the bundle, for the 1.8.0 behaviour. A bundle typed in chat, at the console, by a command block or by join automation shows its chat as before.

- **Checkpoint prompts default to the panel** for a client with toolkit 1.9.0. In 1.8.0 an `arm` showed as a title and a `cue` in the action bar; `cue <label> chat|actionbar|title` still picks a form.

- **An unbound placeholder says what to run first**: `devtool session start <name>`, `devtool session operator <player>` or `devtool fixture claim <handle> ...`. The hint is also in the failure's `ERROR` `detail` and in the `detail` of the inbox answer, so an agent that reads only files sees it. The key stays `devtool.placeholder.unbound`. A fixture command without a session names `devtool session start <name>`.

- **A client too old for a step is told which versions.** The refusal keeps its key (`devtool.client.noClient`, `devtool.screenshot.noClient`) and now names the client's version and the one needed, such as `Developer (client toolkit 1.8.0, this step needs 1.9.0)`, in the server's `ERROR` record and the client's chat. The inbox answer's `detail` is the key.

- **Documentation** adds: `hide=hud` also hides the first-person hand; a server teleport can read the player as not on ground for up to 20 ticks; forcing an attribute with a held item; when to write the first client channel request after a launch; and `toleratedFailures` for vanilla commands expected to fail. The descriptions a new `devtool.cfg` gives for `client.allowActions` and `client.requestChannel` now name the 1.9.0 steps; keys and defaults are unchanged.

### Compatibility

- **A client older than 1.9.0** on a 1.9.0 server is refused every new step at once, with nothing sent; the 1.7.0 and 1.8.0 player actions work for it as before. Its cues show in the action bar, where chat lines can cover them.

- **Beside other mods** (seen on Cleanroom): clicks inside a chest gave the same results with Inventory Tweaks, Mouse Tweaks and Scaling GUIs installed, and the key steps and the prompt panel the same with ModifierKeyFix and Gnetum installed. The toolkit adds no integration with any of these mods and changes none of their settings.

- **Input on both runtimes.** The raw key state and the input queue (keys, clicks, scroll) work on the Forge development client and on Cleanroom.

### Known Issues

- **RLCombat 2.2.6 in a Forge development client** crashed the game at world start in RLCombat's own code (a `ClassCastException` in its client tick), before any toolkit input step ran. The same RLCombat runs on Cleanroom, where `route=queue` reached it. The cause was not confirmed.

- **With Gnetum installed**, chat cannot be hidden from screenshots (`hide=chat` still leaves it in the picture), as in 1.8.0; hiding the whole HUD (`hide=hud`) still works.

## 1.8.0

Small additions that save a person a step during a test: the screenshot without chat, the light level without F3, the reason a request waits, and checks a bundle could not make before. They came from another project's test sessions with the toolkit.

### Added

- **A screenshot without chat or the HUD.** `devtool screenshot <label> [delayTicks] [player] hide=chat|hud` leaves the chat lines, or the whole HUD as F1 does, out of that one picture; the player's own chat and F1 settings are unchanged after it. A player action takes the same choice as `screenshotHide=chat|hud`. With `hide`, the client prints no "Saved screenshot" chat line.

- **Light in the block inspection.** `BLOCK_INSPECT` carries `blockLight` and `skyLight`.

- **A health assert.** `devtool assert player <name> health <n>` compares the player's current health at two decimals.

- **A note while single player is paused.** While a screen such as a book pauses a single-player game, the client keeps `config/devtool/inbox/paused.json`, naming the open screen, and removes it when the game runs again. A title-screen request refused `inWorld` says `paused=` and the screen.

- **Waiting for decorated chunks.** `devtool await chunks ... populated` waits until the chunks are loaded and decorated (trees, ores, lakes), one chunk further toward -X and -Z, because decorating a chunk writes into its neighbours. A plain wait ends once the chunks are loaded.

- **`devtool inspect chunk [<x> <z>]`** says whether a chunk is loaded and, when it is, whether it is decorated (`CHUNK_INSPECT`).

- **`devtool find block <block> [<property=value,...>] <x1> <y1> <z1> <x2> <y2> <z2> [limit <n>]`** lists where a block is in a box, in loaded chunks only, without loading any (`BLOCK_FOUND`, then a `BLOCK_SEARCH` summary that counts the chunks it skipped). The inbox takes it as `find block ...`.

- **Item pickups in the log.** The `item_pickup` logging category writes `ITEM_PICKUP` with the item and the count that entered the inventory.

- **A cleared fixture can still be named.** After `devtool fixture clear`, `${fixture.<handle>.uuid}` still gives the cleared entity's UUID for the rest of the session, so a bundle can assert it is gone.

- **The operator's UUID on inbox records.** Every `INBOX` record and `status.json` carry `operatorUuid`; after a reconnect they show the name the player came back with.

- **Four new record types**, `CHUNK_INSPECT`, `BLOCK_FOUND`, `BLOCK_SEARCH` and `ITEM_PICKUP`, for 52 in total.

### Compatibility

- **A client with an older toolkit** is refused a screenshot or a player action with `hide` at once (`devtool.screenshot.noClient`, `devtool.client.noClient`); nothing is sent to it. Without `hide`, everything works as in 1.7.0.

### Known Issues

- **With Gnetum installed**, `hide=chat` leaves the chat in the picture, though the record says `hide=chat`. Use `hide=hud` there.

## 1.7.0

The theme is a test your assistant can run with small player input: a click, a held button, a dropped item, the inventory or a tooltip, without a person doing them. Tests that need aiming, walking or work in other screens still need a person. The items came from other projects' test sessions with the toolkit and from the developer.

### Added

- **Player actions.** `devtool client select <0-8>`, `use` (one right click), `attack` (one left click), `hold use|attack <1-1200>` (a button kept down for that many ticks, such as drawing a bow), `drop [all]` and `inventory` (open the player's own inventory as its key does). Each acts on the client of whoever asked, from chat, a bundle or the inbox (`client ...`), through the game's key bindings, so the game handles it as a player's input; the toolkit never aims or moves the player. They need the toolkit 1.7.0 on the client and `client.allowActions`. Every record says what was targeted, what was held and whether the window had focus. A hold is always released, and says why: its time ran out, the player left or died, a screen opened, the window lost focus, or the next action came.

- **Waits and screenshots with an action.** `delay=<ticks>` before any player action, and `screenshot=<label>` (with `screenshotDelay=<ticks>`) for a picture after it.

- **An item's tooltip.** `devtool client tooltip <0-40> [advanced] [shift] [ctrl] [alt]` shows the player's inventory with that slot's tooltip for a time (`showTicks`), then writes every line, including those other mods add, as text: up to 200 in the client's record, the first 20 in the server's. Nothing in that view can be moved. Shift, Ctrl and Alt are held only while the lines are read; where the game's input does not allow it (Cleanroom), the step is refused `modifierUnavailable`.

- **A reconnect that logs out.** `devtool client reconnect [awaySeconds] logout` leaves single player as Save and Quit does, so the server logs the player out; without the word a single-player reconnect still does not. When the client is back, the server writes one `CLIENT_ACTION action=returned` with the player, the request id, the time away and the connection.

- **Operator rights at a local join on a development server.** With `inbox.localJoinOperator` (off by default), a dedicated server gives a player who passes the local-join proof operator rights for the session, so the inbox opens at the first join, and takes them back at the leave, at an orderly stop, or at the next start after a crash. A player already in `ops.json` is left as is. Each step writes an `OP` record.

- **`SAVE` names the player file** (`playerFile`, its size and time after the flush), and says when the single-player host's data lives in `level.dat`.

- **A warning when a single-player join name differs from the world owner's only in case** (`WARNING reason=hostNameCase`), since the game then reads the host's data from `playerdata` instead of `level.dat`.

- **A refusal that names the accepted form.** An inbox line starting with `devtool` is refused with a `detail` giving the line without it, such as `use:client respawn`.

- **Two new record types**, `WARNING` and `OP`, for 48 in total.

### Fixed

- **A hold or a tooltip view ended by the player's death now reports `died`.** The death screen opens before the client's health reaches 0, so the end read as a screen opening.

### Compatibility

- **A client with an older toolkit** on a 1.7.0 server is refused every player action, and a reconnect with `logout`, at once with `devtool.client.noClient`; nothing is sent to it.

## 1.6.0

Small fixes and polish after 1.5.0. The first item came from another project's test sessions with the toolkit; the rest from the developer's own review of the 1.5.0 release.

### Fixed

- **The inbox opens at your own join whatever the case of the launch name.** 1.5.0 compared the player's name with the world's owner with case, so a development launch as `mahghuuuls` joining with the account name `Mahghuuuls` never got the inbox, and nothing in the log said why. The host is now recognised ignoring case, as vanilla decides who has cheats, both at the join and when the world stops for a toolkit reconnect. A player on the in-process connection who still is not taken for the host gets `INBOX action=refused reason=localJoinNotHost` with `owner`, instead of silence.

### Added

- **A bundle result file says which run it is.** `channel` and `caller`, `requestId` for a run an inbox request started (or a bundle nested in one), `startedAtMillis` and `world`. `BUNDLE_START` carries `requestId` too, and the inbox answer of a `run` names the file as `resultFile`. Every earlier field is unchanged.

- **Tips for test mobs in `AGENTS.md` and `docs/`**: keeping a summoned undead mob from burning (an unbreakable helmet, Fire Resistance, night), keeping the test player alive (Resistance), and clearing a fresh world, including the slimes a flat world spawns.

### Changed

- **The documentation coverage test also checks every refusal and failure reason** the source writes, so a new reason cannot ship undocumented. It found one older reason, `noHandler`, that the manual did not name; it is documented now.

## 1.5.0

The theme is a test that starts at the title screen and ends with the game closed, while the person at the keyboard only launches the game. The items came from the developer's own test sessions with an AI assistant.

### Added

- **`devtool client quit`** closes the game of whoever ran it, from chat, a bundle or the inbox, after the run or request that asked has its answer. In single player the world is saved first; on a server the player leaves as from the pause menu. It needs the toolkit 1.5.0 on the client and `client.allowActions`, as the other client actions do. The server refuses at once, with a key a bundle can tolerate, a client without the toolkit 1.5.0 (`devtool.client.noClient`), one that does not allow it (`devtool.client.notAllowed`) and one still busy with another client action (`devtool.client.busy`).

- **A client request channel.** At the title screen no server runs, so nothing can reach the game through the inbox. With `client.requestChannel` true in the client's own configuration, the client reads small request files from `config/devtool/client-inbox/` and takes one of four menu steps: `create <name> [option=value ...]` makes a single-player world with the settings of the Create New World screen (seed, type `default`, `flat`, `largeBiomes` or `amplified`, a flat layer string, structures, cheats, game mode, bonus chest; creative with cheats on by default) and opens it; `open <save folder>` opens a save; `connect <host[:port]>` joins a server on this computer or the local network only; `quit` closes the game. A create never replaces an existing save. Every value is checked before anything happens, and a request is refused with a reason (`exists`, `notFound`, `notLocal`, `inWorld`, `busy`, `badArguments` with a detail, and others), each with its own result file. Requests left from an earlier launch are refused `placedBeforeStart`. `status.json` says when the channel listens. Off by default; with it on, it listens from game start with no click.

- **The inbox opens by itself at your own join.** With `inbox.openOnLocalJoin` true as well as `inbox.enabled`, the command inbox opens without `devtool inbox open` when you join from your own computer: as the single-player host with cheats on, or as an operator on a development server whose folder your client shares and which you join through `localhost`. The server proves the second case with a secret file, `config/devtool/local-join.secret`, written at its start and removed at its stop, which only a client reading that folder can send back. A join from anywhere else opens nothing. Off by default.

- **One new record type**, `MENU_STEP`, for 46 in total.

### Compatibility

- **Forge's startup questions.** A yes/no question while a world starts, such as one about missing blocks or items, holds the game until someone clicks, and a menu step gets no answer meanwhile. Launch the game with the JVM argument `-Dfml.queryResult=cancel` to answer every such question with cancel at once: the world does not open, and the step fails `loadAborted`. A "missing mods" notice still waits for a click.

## 1.4.0

The theme is a test session that runs without anyone at the keyboard between its steps. Most items came from the developer's own test sessions with an AI assistant.

### Added

- **Client actions.** `devtool client reconnect [awaySeconds]`, `devtool client respawn` and `devtool client close` ask your own client to leave and come back to the same world or server, to press Respawn on the death screen, or to close the open screen. They always act on the client of whoever ran them, from chat, a bundle or the inbox, and never on another player's. Off unless the client allows it: `client.allowActions` in the client's own configuration, false by default. The server refuses at once, with a key a bundle can tolerate, what it already knows: `devtool.client.noClient` for a client without the toolkit 1.4.0, `devtool.client.notAllowed`, `devtool.client.notDead` and `devtool.client.busy`. The client refuses, with a reason, what only it can know, such as closing the death screen or reconnecting a world open to LAN. Every step writes `CLIENT_ACTION` records on both sides.

- **The inbox waits for a reconnect.** When the inbox operator's own `client reconnect` takes their client away, the inbox is suspended instead of closed, and resumes when that same client is back, so requests written meanwhile run after the return without anyone typing `inbox open` again. Any other way back, or the deadline `inbox.rejoinWindowSeconds` (120 seconds by default), closes it. `status.json` says when it is suspended.

- **`devtool fixture release <handle|all>`** takes a fixture's tag off and leaves the entity in the world.

- **`inspect player` names the open container**, as `openContainer`.

- **An icon** in the mod list.

- **One new record type**, `CLIENT_ACTION`, for 45 in total.

### Changed

- **Fixture ownership is the tag.** A fixture belongs to the session whose tag it carries, so a new session of the same name adopts its fixtures again after a `session stop`, and `fixture list` reports a fixture as `removing`, `untagged` or `duplicate` when that is what it is. A handle can no longer contain a dot, and a dot in a session name becomes `_` in the tag. A 1.3.0 tag with a dot in its handle or session belongs to no session in 1.4.0.

- **`fixture clear` removes without a death.** No loot, no experience, no death events. Its `FIXTURE outcome=removed` record is written a tick later, once the removal is confirmed, with `confirmedTick`.

- **`assert entity ... absent` counts a dying entity as absent**, with `dead=true` on the record.

- **Damage records come first.** A target's `ENTITY_DAMAGE` is now written before its `ENTITY_DEATH`, and before a `LOG_CONFIG` that changes the logging. In 1.3.0 a hit's record was written at the end of its tick, so the last hit of a kill appeared after the death, and a hit just before a logging change appeared after that change.

## 1.3.0

Most items in this release came from AI assistants that used 1.2.0 to test other mods, and from the developer's own test sessions. The theme is letting an assistant run a test session itself, from start to finish, while the person at the keyboard only opens the door.

### Added

- **A command inbox.** `devtool inbox open`, typed by a person once each time a world opens, lets an assistant write small request files into `config/devtool/inbox/` and read one result file per request: `run <bundle>`, `reload`, `save`, `mark`, the read-only `inspect`, `entities` and `nbt` commands, and `screenshot`. Requests run as the player who opened it, with that player's permissions. Nothing else is accepted, and no vanilla command. Off by default: the configuration key `inbox.enabled` must be true as well, and a bundle can never open it. It closes when that player leaves or the server stops, and `status.json` is present exactly while it is open. Every claim, refusal and result writes an `INBOX` record.

- **Who started a run.** `BUNDLE_START` now says who started the run and through which channel: chat, console, command block, join, inbox, or another bundle.

- **`devtool save`.** Saves player data and every loaded world, in single player and on a dedicated server, and leaves a `save-off` typed earlier in place. Writes `SAVE` with the dimensions saved. A failure fails with `devtool.save.failed`.

- **Screenshots on request.** `devtool screenshot <label> [delayTicks] [player]` asks a player's client to save a labelled screenshot after a delay, named after the label and the tick it was asked for. The client needs the toolkit installed; a player without it fails with `devtool.screenshot.noClient`, and can still join.

- **Attributes.** `devtool inspect attributes` lists a living entity's attributes with every modifier. `devtool assert attribute <target> <attribute> value <n>` checks the final value, and `... modifier <uuid|name> [amount <n>]` one modifier. A name matching several modifiers fails with `devtool.assert.ambiguous`.

- **Optional Baubles support.** With Baubles installed, `inspect inventory` includes the bauble slots with their slot type, and `devtool baubles set` and `clear` fill and empty them through Baubles' own handler, calling the bauble's equip and unequip hooks. Without Baubles, `devtool baubles` fails with `devtool.baubles.absent` and nothing else changes. Baubles is never required.

- **A session belongs to one world.** Every world start writes a `WORLD` record with the save folder and seed. Opening a different save ends the active session first, with `SESSION_STOP reason=worldChanged`.

- **Reload at every world start.** Bundles and configuration are read again whenever a world starts, writing `RELOAD trigger=serverStart`, so a file edited at the title screen is used without typing `devtool reload`. `devtool reload` now writes `RELOAD trigger=command` too.

- **Quieter, steadier test clients.** The opt-in client defaults can now also set master, weather, hostile, friendly and ambient volume, each left alone unless given a value, and `client.noPauseOnLostFocus` keeps the game running when its window loses focus. All only when `client.applyOnJoin` is on.

- **Nine new record types**, for 44 in total: `TOLERATED`, `RELOAD`, `INBOX`, `SAVE`, `WORLD`, `ATTRIBUTE`, `ATTRIBUTE_MODIFIER`, `SCREENSHOT`, `BAUBLES`.

### Changed

- **Inspection never loads a chunk.** `inspect block`, `inspect container` and `nbt block` refuse a position whose chunk is not loaded, with `devtool.inspect.notLoaded`, instead of making the game load or generate it.

- **Inspecting a container never rolls its loot.** A chest whose loot table has not rolled yet is reported as `lootRolled=false` with the table name, and stays unrolled.

- **An unknown subcommand names the closest real one** in the chat reply: `devtool logging` answers "Did you mean log?".

### Fixed

- **A tolerated failure now leaves a record.** 1.1.0 promised that a tolerated step would carry a note saying so; that note was never written. Each tolerated step now writes a `TOLERATED` record naming the key, and `BUNDLE_END` counts them as `tolerated`.

### Documentation

- `AGENTS.md` gains a worked example of a whole test run through the inbox, the full inbox reference, and every new command, record and failure key. A build check now fails if a record type or a `devtool.` failure key is missing from it.
- Every subcommand needs permission level 2, typed or inside a bundle.
- Arena commands roll and drop the loot of a loot chest they replace.
- On a dedicated server the bundle load records appear twice at start, once for the load and once for the start-up reload.

### Notes

- Bundles written for 1.2.0 work unchanged, except one that inspects a position whose chunk is not loaded: it now fails with `devtool.inspect.notLoaded` instead of loading the chunk. No command was removed.
- The client defaults and screenshots are the only parts that act on a client. Everything else still needs the toolkit on the server only.

## 1.2.0

Every item in this release came from one report written by an AI assistant that used 1.1.0 to test another mod. Ten items, ten answers.

### Added

- **A bundle can wait for something to be true.** `devtool await chunks x1 z1 x2 z2 [timeoutTicks]` parks the bundle until every chunk touching that rectangle is loaded, then continues. It never loads a chunk. A timeout fails the command with `devtool.wait.timeout`, a dimension change with `devtool.wait.cancelled`, and both honour `stopOnFailure` and `toleratedFailures`. Every wait writes an `AWAIT` record with how long it waited and, on a timeout, the first chunk that was not there.

- **A checkpoint for the person at the keyboard.** `devtool checkpoint arm <label> [timeoutTicks]` shows the label on screen and parks the bundle until they type `devtool checkpoint ack`, recording the tick asked, the tick answered and the latency. `devtool checkpoint cue <label> [chat|actionbar|title] [sound]` shows a label without pausing. Records between `armedTick` and `ackTick` are the ones that happened while the person acted.

- **An operator, and placeholders that name them.** `devtool session operator @s` binds one online player to the session with an `OPERATOR` record; disconnect and session stop unbind with a record. Bundle commands may then say `${operator.name}`, `${operator.uuid}` and `${operator.selector}`, filled in when the command runs. An unbound value fails the command with `devtool.placeholder.unbound`; a value that could break the command, with `devtool.placeholder.unsafe`.

- **Assertions.** `devtool assert entities count`, `assert entity <uuid> present|absent|dimension|within`, and `assert player <name> dimension|gamemode` write an `ASSERT` record with `predicate`, `expected`, `observed` and `outcome`. A mismatch fails the command with `devtool.assert.failed`; an absence that cannot be judged because the place is not loaded is `outcome=unloaded` with `devtool.assert.unloaded`, never a pass. A verdict says only that an observation matched or did not match a declared value. It never says the mod is right.

- **A result file per run.** `"result": true` on a bundle writes `results/<bundle>-<startTick>.json` beside the configuration: assertions, failures, markers, checkpoints and waits, copied from the log records, plus the bundle's identity, hash, session, operator and counters. Nothing is in the file that is not in the log.

- **Fixtures.** `devtool fixture claim <handle> <selector|uuid|entityId>` tags exactly one non-player entity as the session's own, `fixture list` says whether each is `loaded`, `unloaded` or `absent`, and `fixture clear <handle|all>` removes only entities that still carry the tag, logging every UUID. `${fixture.<handle>.uuid}` names a claimed entity in any later command. Ownership is the tag, never the name.

- **Scenario checkpoints across a restart.** `devtool scenario checkpoint <id> next <bundle> [clean|fatal]` stores, inside the world save, which bundle must run next, with its hash, the session, its operator and its fixtures. After the restart a `SCENARIO` record at world load reports it, running anything else is refused with `devtool.scenario.mismatch`, and running the expected bundle restores the session, the operator and the fixtures first. `complete` and `abandon` clear it. Unreadable stored data is reported as `corrupt` and refuses nothing.

- **Provenance.** Every load and reload writes a `BUNDLE_SOURCE` record per file with its SHA-256, and one per bundle with a normalized hash of what the bundle would do. `BUNDLE_START` and `BUNDLE_END` carry that hash, so a log line proves which text a run came from.

- **Trace records.** `"trace": true` on a bundle writes a `BUNDLE_STEP` before each command with the tick it became due and the tick it ran on, for when a delay is not doing what you expected.

- **Direct entity lookup.** `devtool nbt entity` accepts a UUID or an entity ID as well as a selector, finds the entity in any loaded dimension without the selector engine, and records which dimension and which lookup form. A miss lists the dimensions that were searched. A selector that matches nothing now writes an `ERROR` record with what the engine had to work with: `selectorPermission`, `dimensionCount`, `dimensionIds`, `senderDimension` and `liveEntities`.

### Changed

- **The marker chat reply names the label.** `devtool mark PRESS_H_NOW` answers `Marker written to log: PRESS_H_NOW`, so a person can tell which marker fired. The `MARK` record is unchanged.

- **An unknown `${...}` in a bundle command refuses the file at reload.** In earlier versions the text reached the game unchanged. Only the placeholder forms above are accepted; anything else inside `${` and `}` is a load error naming the file, the bundle and the placeholder. A bundle that relied on passing such text through literally must be rewritten.

- **Long checkpoint labels fit on screen.** A label over twelve characters is drawn on the subtitle line under a short heading rather than at title size.

- **Eight new record types**, for 35 in total: `BUNDLE_STEP`, `BUNDLE_SOURCE`, `AWAIT`, `CHECKPOINT`, `OPERATOR`, `ASSERT`, `FIXTURE`, `SCENARIO`. No existing record changed shape, except that `BUNDLE_START` and `BUNDLE_END` gained `hash`, and `NBT` for an entity gained `lookup` and `dimension`.

### Documentation

- `delayTicks` is now described as "wait, then run this command", with the pattern that puts the delay on the command that must follow the wait. A reader had reasonably taken it the other way round.
- Five new worked examples in `AGENTS.md`: a wait, a checkpoint, an assertion with a result file, a fixture claim and clear, and a scenario across a restart.
- The public copy states what a verdict means.

### Notes

- A report that a selector matched nothing on a dedicated server, right after `entities nearby` had listed the entity, could not be reproduced on 1.1.0 or on this build. The zero-match record now carries what the selector engine saw, so the next such report can be read from the log.
- Bundles written for 1.1.0 work unchanged unless they contain `${` text, see above. The command surface only grew; no command changed meaning.

## 1.1.0

### Added

- **A bundle command can declare failures it expects.** An idempotent reset such as `effect @s clear` fails when there is nothing to clear, and that halted the whole bundle. Declare the failure on that command and it is tolerated:

  ```json
  { "command": "effect @s clear", "toleratedFailures": ["commands.effect.failure.notActive.all"] }
  ```

  It tolerates **that key only**. A typo in the same command still fails the bundle, which turning off `stopOnFailure` would not have given you. The key is the `detail` field of the `ERROR` record, and a tolerated failure is recorded as a success carrying a note that names it, so it never reads as a command that simply worked.

- **`devtool inspect container <x> <y> <z>`** reports a container's occupied slots as one record each, with slot index, item, count, metadata and whether item NBT exists. Works with vanilla containers and modded blocks that expose Forge's item handler. Output is bounded, and truncation is reported on every record rather than silently.

### Fixed

- No more `Missing English translation for agenttesttoolkit` on startup. The mod has no translated strings, but a tool whose purpose is a clean log should not add noise about itself.

### Documentation

- **Block inspection does not report container contents, and now says so.** `inspect block` answers what a block is. For what is inside it, use `nbt block`, which has always written the full tile entity NBT including every slot. Neither command pointed at the other, and a project using the toolkit built a workaround for a problem that did not exist.

  Related: a container whose loot table has not yet rolled writes a `LootTable` reference instead of items, so `nbt block` distinguishes "loot not generated yet" from the generated contents.

- **Running a client and a dedicated server from one directory can corrupt the shared log.** The combined file can contain NUL bytes, and standard search tools then report no matches rather than saying the file is unreadable. Documented along with the practice that works: binary-tolerant reading, retaining both the current and rotated logs, and separate directories where the build allows.

### Notes

- No change to existing behaviour. Bundles written for 1.0.1 work unchanged, and a command without `toleratedFailures` behaves exactly as before.

## 1.0.1

### Fixed

- **A bundle command that leaves out its player argument now works.** `gamemode creative` inside a bundle failed in 1.0.0 with "You must specify which player you wish to perform this action on", even when a player ran the bundle. The same fault affected `clear`, `kill`, `scoreboard`, `setworldspawn`, `spawnpoint`, `tp` and `xp` whenever their player argument was omitted. Passing an explicit selector such as `@p` was the workaround and still works.

  The cause was that bundle commands were run through a stand-in sender rather than your own, so vanilla commands that check who is running them saw the wrong thing. Bundle commands now run as the caller's own sender, which is what the documentation always claimed. Reported by a project using the toolkit during its own development.

### Changed

- Failure classification reads the error the game raises rather than the message it prints. Behaviour is unchanged: a command that runs and affects nothing still counts as success, and a raised error still counts as failure. The mechanism no longer depends on rendered text.
- The repository documentation now explains where to download a release and how to check its SHA-256. A version tag on its own is not a release, and 1.0.0's jar is published retroactively for anyone who needs that version.

### Notes

- Commands the caller could not type themselves still fail exactly as they would if typed. The correction restored who a command runs as; it grants nothing. A bundle run from the server console is still refused for commands that need a player.
- No change to the bundle file format, the command surface, the record vocabulary, or configuration. Bundles written for 1.0.0 work unchanged.

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
