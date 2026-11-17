package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.InputRequest;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Takes the player actions and the input steps a server asks for, on the client's main thread.
 *
 * <p>A click is one press counted on the binding behind the key, which the game turns into its
 * own click on its next tick, through the code a mouse click reaches. So the game's and Forge's
 * events of the click itself fire as for a person, and what the click does is the game's
 * decision. By the binding route the raw mouse input events do not fire, since no mouse button
 * is pressed; the input steps' other routes add them (see {@link HeldInputs}).
 *
 * <p>What lasts across ticks: an action waiting out its delay, the held inputs and a turn (kept
 * by {@link HeldInputs}), the tooltip view, and an inventory whose screen is read a tick after
 * the key. Each ends with its own record, and each ends when the player leaves the world.
 *
 * <p>Client-only: registered through {@code ClientProxy} and never referenced from shared code.
 */
public final class PlayerInput implements ToolkitChannel.PlayerActionReceiver, ToolkitChannel.InputReceiver,
        HeldInputs.Records {

    /** The reason a waiting action ends when a newer action arrives. */
    static final String REASON_REPLACED = "nextAction";
    /** The tooltip view did not open: another mod cancelled or replaced it as it opened. */
    static final String REASON_VIEW_NOT_OPENED = "viewNotOpened";
    /** An input step whose words this client's parser does not accept, as from a newer server. */
    static final String REASON_BAD_ARGUMENTS = "badArguments";

    private final ConcurrentLinkedQueue<Asked> incoming = new ConcurrentLinkedQueue<Asked>();
    private final ClientActionExecutor executor;
    private final ScreenshotTaker screenshots;
    private final HeldInputs held = new HeldInputs(this);
    private final InputSteps steps = new InputSteps(held, this);

    /** Client thread only. The one action waiting out its delay, and the ticks left. */
    private Asked waiting;
    private int waitingTicks;
    /** Client thread only. The tooltip view showing, until it closes. */
    private Tooltip tooltip;
    /** Client thread only. An inventory opened this tick, whose screen is read on the next. */
    private Asked inventoryOpened;
    private Map<String, String> inventoryFields;

    private static final class Tooltip {
        final TooltipScreen view;
        final Asked asked;
        final Map<String, String> fields;
        int shown;

        Tooltip(TooltipScreen view, Asked asked, Map<String, String> fields) {
            this.view = view;
            this.asked = asked;
            this.fields = fields;
        }
    }

    public PlayerInput(ClientActionExecutor executor, ScreenshotTaker screenshots) {
        this.executor = executor;
        this.screenshots = screenshots;
    }

    @Override
    public void receive(PlayerActionRequest request) {
        incoming.add(new Asked(request));
    }

    @Override
    public void receive(InputRequest request) {
        incoming.add(new Asked(request));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            held.tickStart(mc);
            return;
        }
        boolean worldOpen = mc.world != null && mc.player != null;
        if (inventoryOpened != null) {
            // The game opened its screen during this tick's key handling.
            Asked opened = inventoryOpened;
            Map<String, String> fields = inventoryFields;
            inventoryOpened = null;
            inventoryFields = null;
            finish(mc, opened, "done", null, "none", screenName(mc.currentScreen), fields);
        }
        // Before anything asked this tick: a press made now is let go at the next tick's end.
        held.tickEnd(mc);
        if (tooltip != null) {
            advanceTooltip(mc);
        }
        if (waiting != null && !worldOpen) {
            // A delayed action belongs to the world it was asked in; it never acts in another.
            Asked gone = waiting;
            waiting = null;
            finish(mc, gone, "refused", PlayerActionGate.REASON_NO_WORLD, screenName(mc.currentScreen), null,
                    new LinkedHashMap<String, String>());
        }
        if (waiting != null && --waitingTicks <= 0) {
            Asked due = waiting;
            waiting = null;
            take(mc, due);
        }
        Asked asked;
        while ((asked = incoming.poll()) != null) {
            if (waiting != null) {
                // A newer action of any kind takes the waiting one's place.
                finish(mc, waiting, "refused", REASON_REPLACED, screenName(mc.currentScreen), null,
                        new LinkedHashMap<String, String>());
                waiting = null;
            }
            if (asked.delay() > 0) {
                waiting = asked;
                waitingTicks = asked.delay();
            } else {
                take(mc, asked);
            }
        }
    }

    /** The keyboard library refills its key state once per frame; held raw keys are written again before drawing. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            held.renderStart();
        }
    }

    /** Before any other listener of an input event: whether one of them takes a watched press. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeInput(InputEvent event) {
        held.beforeInputEvent();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterInput(InputEvent event) {
        held.afterInputEvent();
    }

    /** After every listener, cancelled or not: a mod that cancels a queued click's mouse event took it. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void afterMouse(MouseEvent event) {
        held.mouseEvent(event);
    }

    /**
     * Closes the tooltip view after its ticks, or notes what closed it: a person (Escape, the
     * inventory key, or nothing showing any more), another screen that replaced it, the
     * player's death, or the leave of the world. Its one record is written then.
     */
    private void advanceTooltip(Minecraft mc) {
        Tooltip t = tooltip;
        String closed = null;
        if (mc.world == null || mc.player == null) {
            closed = "left";
        } else if (HeldInputs.died(mc)) {
            closed = "died";
        } else if (mc.currentScreen != t.view) {
            closed = mc.currentScreen == null ? "person" : "replaced";
        } else if (++t.shown >= t.asked.action.getTicks()) {
            // As the inventory closes: the game is told the player's container closed.
            mc.player.closeScreen();
            closed = "done";
        }
        if (closed == null) {
            return;
        }
        tooltip = null;
        t.fields.put("closed", closed);
        finish(mc, t.asked, "done", null, screenName(mc.currentScreen), null, t.fields);
    }

    /** Opens the tooltip view. Its record waits for the view to close. */
    private void openTooltip(Minecraft mc, Asked asked, String screen) {
        PlayerActionRequest request = asked.action;
        ItemStack stack = mc.player.inventory.getStackInSlot(request.getSlot());
        TooltipScreen view = new TooltipScreen(mc.player, request.getSlot(), request.isAdvanced(),
                ModifierKeys.codes(request.modifierKeys()));
        mc.displayGuiScreen(view);
        if (mc.currentScreen != view) {
            finish(mc, asked, "failed", REASON_VIEW_NOT_OPENED, screenName(mc.currentScreen), null,
                    new LinkedHashMap<String, String>());
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("slot", String.valueOf(request.getSlot()));
        fields.put("advanced", String.valueOf(request.isAdvanced()));
        if (!request.modifierKeys().isEmpty()) {
            fields.put("keys", request.modifierKeys());
        }
        fields.put("showTicks", String.valueOf(request.getTicks()));
        try {
            ResourceLocation item = stack.getItem().getRegistryName();
            fields.put("item", item == null ? "unknown" : item.toString());
            fields.put("count", String.valueOf(stack.getCount()));
            fields.putAll(TooltipLines.fields(view.lines(stack)));
            if (!request.modifierKeys().isEmpty()) {
                fields.put("keysSeen", view.keysSeen());
            }
        } catch (RuntimeException failure) {
            // Another mod's tooltip handler threw: the view is closed again and the action failed.
            mc.player.closeScreen();
            finish(mc, asked, "failed", failure.getClass().getSimpleName(), screen, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        screenshotAfter(asked, fields);
        tooltip = new Tooltip(view, asked, fields);
    }

    private void take(Minecraft mc, Asked asked) {
        if (!asked.parsed()) {
            // Words this build's parser does not accept, as from a server newer than this client.
            finish(mc, asked, "refused", REASON_BAD_ARGUMENTS, screenName(mc.currentScreen), null,
                    new LinkedHashMap<String, String>());
            return;
        }
        if (asked.ending() == HoldRelease.Ending.ALL_NEXT_ACTION) {
            // Another action starts: the held inputs are released first. The other endings are
            // the step's own, made once it may act.
            held.endForAction(mc);
        }
        String screen = screenName(mc.currentScreen);
        if (!ToolkitConfigLoader.get().isClientAllowActions()) {
            // The server checks the hello too; this holds if the setting changed since.
            finish(mc, asked, "refused", "notAllowed", screen, null, new LinkedHashMap<String, String>());
            return;
        }
        if (executor.isOnReconnect()) {
            finish(mc, asked, "refused", "busy", screen, null, new LinkedHashMap<String, String>());
            return;
        }
        // The inventory asked this tick opens on the next; until then it counts as open.
        boolean screenOpen = mc.currentScreen != null || inventoryOpened != null || tooltip != null;
        if (asked.input != null) {
            steps.take(mc, asked, screenOpen);
            return;
        }
        PlayerActionRequest request = asked.action;
        KeyBinding binding = binding(mc, request);
        boolean worldOpen = mc.world != null && mc.player != null;
        String refusal = PlayerActionGate.refusal(worldOpen, screenOpen, binding != null,
                binding == null ? 0 : binding.getKeyCode(),
                request.isTooltip() && worldOpen && mc.player.inventory.getStackInSlot(request.getSlot()).isEmpty(),
                request.isTooltip() && !request.modifierKeys().isEmpty() && !ModifierKeys.available());
        if (refusal != null) {
            finish(mc, asked, "refused", refusal, screen, null, new LinkedHashMap<String, String>());
            return;
        }

        if (request.isTooltip()) {
            openTooltip(mc, asked, screen);
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        try {
            if (request.isSelect()) {
                fields.put("slot", String.valueOf(request.getSlot()));
                describeHeld(mc.player.inventory.getStackInSlot(request.getSlot()), fields);
            } else if (request.isHold()) {
                fields.put("input", request.getArgument());
                fields.put("ticks", String.valueOf(request.getTicks()));
                describeTarget(mc, mc.objectMouseOver, fields);
                describeHeld(mc.player.getHeldItemMainhand(), fields);
            } else if (request.isDrop()) {
                fields.put("all", String.valueOf(request.dropsAll()));
                describeHeld(mc.player.getHeldItemMainhand(), fields);
            } else if (!request.isInventory()) {
                // What the click will meet and with what, read before it is made.
                describeTarget(mc, mc.objectMouseOver, fields);
                describeHeld(mc.player.getHeldItemMainhand(), fields);
            }
        } catch (RuntimeException failure) {
            // Reading the target or the item threw, as a modded block or item can: nothing was
            // pressed, the action failed, and the client tick goes on.
            finish(mc, asked, "failed", failure.getClass().getSimpleName(), screen, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        if (request.isSelect()) {
            // The game sends the held-slot packet itself on its next update, as for a number key.
            mc.player.inventory.currentItem = request.getSlot();
        } else if (request.isDrop()) {
            // What the drop key calls; the game sends the drop to the server.
            mc.player.dropItem(request.dropsAll());
        } else if (request.isHold()) {
            // As a mouse button going down: the key is down, and one press is counted.
            Map<String, String> releaseFields = new LinkedHashMap<String, String>();
            releaseFields.put("input", request.getArgument());
            held.hold(mc, asked, binding, request.getTicks(), false, false, releaseFields);
            screenshotAfter(asked, fields);
            finish(mc, asked, "started", null, screen, null, fields);
            return;
        } else {
            KeyBinding.onTick(binding.getKeyCode());
            if (request.isInventory()) {
                // Answered next tick, with the screen the game opened.
                screenshotAfter(asked, fields);
                inventoryOpened = asked;
                inventoryFields = fields;
                return;
            }
        }
        screenshotAfter(asked, fields);
        finish(mc, asked, "done", null, screen, null, fields);
    }

    /**
     * Asks this client's screenshot taker for the step's screenshot, by the screenshot
     * command's rules: the file is {@code screenshots/<label>-<requestTick>.png} and its
     * {@code SCREENSHOT} records follow. Taken only for a step that went ahead.
     */
    @Override
    public void screenshotAfter(Asked asked, Map<String, String> fields) {
        if (asked.screenshot().isEmpty()) {
            return;
        }
        fields.put("screenshot", asked.screenshot());
        // What the picture leaves out is on the server's request record and the client's
        // SCREENSHOT record; the report's fields are full for a long tooltip.
        screenshots.receive(new ScreenshotRequest(asked.screenshot(), asked.screenshotDelay(), asked.requestTick(),
                asked.screenshotHide()));
    }

    /** The key an action presses, or null for one that presses none. */
    private static KeyBinding binding(Minecraft mc, PlayerActionRequest request) {
        if (request.isUse() || (request.isHold() && request.holdsUse())) {
            return mc.gameSettings.keyBindUseItem;
        }
        if (request.isAttack() || request.isHold()) {
            return mc.gameSettings.keyBindAttack;
        }
        if (request.isInventory()) {
            return mc.gameSettings.keyBindInventory;
        }
        return null;
    }

    /** What the crosshair is on: a block with its face, an entity, or nothing in reach. */
    static void describeTarget(Minecraft mc, RayTraceResult hit, Map<String, String> fields) {
        if (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS) {
            fields.put("target", "miss");
            return;
        }
        if (hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            Entity entity = hit.entityHit;
            ResourceLocation type = EntityList.getKey(entity);
            fields.put("target", "entity");
            fields.put("entityId", String.valueOf(entity.getEntityId()));
            fields.put("entityType", type == null ? entity.getClass().getSimpleName() : type.toString());
            fields.put("entityUuid", entity.getUniqueID().toString());
            return;
        }
        BlockPos pos = hit.getBlockPos();
        fields.put("target", "block");
        fields.put("targetX", String.valueOf(pos.getX()));
        fields.put("targetY", String.valueOf(pos.getY()));
        fields.put("targetZ", String.valueOf(pos.getZ()));
        fields.put("face", hit.sideHit == null ? "none" : hit.sideHit.getName());
        ResourceLocation block = mc.world.getBlockState(pos).getBlock().getRegistryName();
        fields.put("targetBlock", block == null ? "unknown" : block.toString());
    }

    static void describeHeld(ItemStack held, Map<String, String> fields) {
        if (held == null || held.isEmpty()) {
            fields.put("held", "empty");
            return;
        }
        ResourceLocation item = held.getItem().getRegistryName();
        fields.put("held", item == null ? "unknown" : item.toString());
        fields.put("heldCount", String.valueOf(held.getCount()));
    }

    /** The open screen's class, bounded so a long modded name still fits a report. */
    static String screenName(GuiScreen screen) {
        if (screen == null) {
            return "none";
        }
        String name = screen.getClass().getName();
        return name.length() <= 128 ? name : name.substring(name.length() - 128);
    }

    /**
     * One record on the client, one report to the server, and whether the game window had
     * focus. The client's record has every field; the report has what fits it (a tooltip's first
     * lines). A player action comes only from a 1.7.0 server and an input step only from a 1.9.0
     * one, so the report's fields always reach a server that reads them. After a leave there is
     * no server to tell.
     */
    @Override
    public void finish(Minecraft mc, Asked asked, String result, String reason, String screen, String showing,
                       Map<String, String> fields) {
        if (asked.delay() > 0) {
            fields.put("delay", String.valueOf(asked.delay()));
        }
        fields.put("focus", String.valueOf(Display.isActive()));
        LogRecord record = LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", asked.step())
                .add("result", result)
                .add("reason", reason)
                .add("screen", screen)
                .add("showing", showing);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            record.add(field.getKey(), field.getValue());
        }
        record.add("requestTick", asked.requestTick());
        ToolkitLog.write(record);
        if (mc.getConnection() != null) {
            ToolkitChannel.sendToServer(new ClientActionReport(asked.step(), result, reason, screen, showing,
                    asked.token(), asked.requestTick()).withFields(TooltipLines.forReport(fields)));
        }
    }
}
