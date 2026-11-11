package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Takes the small player actions a server asks for, on the client's main thread.
 *
 * <p>A click is one press counted on the binding behind the key, which the game turns into its
 * own click on its next tick, through the code a mouse click reaches. So the game's and Forge's
 * events of the click itself fire as for a person, and what the click does is the game's
 * decision. The raw mouse input events do not fire: no mouse button is pressed. The toolkit
 * never moves or turns the player.
 *
 * <p>Four things last across ticks: an action waiting out its delay, a held input, the tooltip
 * view, and an inventory whose screen is read a tick after the key. Each ends with its own
 * record, and each ends when the player leaves the world.
 *
 * <p>Client-only: registered through {@code ClientProxy} and never referenced from shared code.
 */
public final class PlayerInput implements ToolkitChannel.PlayerActionReceiver {

    /** The reason a waiting action ends when a newer action arrives. */
    static final String REASON_REPLACED = "nextAction";
    /** The tooltip view did not open: another mod cancelled or replaced it as it opened. */
    static final String REASON_VIEW_NOT_OPENED = "viewNotOpened";

    private final ConcurrentLinkedQueue<PlayerActionRequest> incoming = new ConcurrentLinkedQueue<PlayerActionRequest>();
    private final ClientActionExecutor executor;
    private final ScreenshotTaker screenshots;

    /** Client thread only. The one action waiting out its delay, and the ticks left. */
    private PlayerActionRequest waiting;
    private int waitingTicks;
    /** Client thread only. The one input held, until it is released. */
    private Hold hold;
    /** Client thread only. The tooltip view showing, until it closes. */
    private Tooltip tooltip;
    /** Client thread only. An inventory opened this tick, whose screen is read on the next. */
    private PlayerActionRequest inventoryOpened;
    private Map<String, String> inventoryFields;

    private static final class Hold {
        final PlayerActionRequest request;
        final KeyBinding binding;
        final boolean focusedAtStart;
        int heldTicks;

        Hold(PlayerActionRequest request, KeyBinding binding, boolean focusedAtStart) {
            this.request = request;
            this.binding = binding;
            this.focusedAtStart = focusedAtStart;
        }
    }

    private static final class Tooltip {
        final TooltipScreen view;
        final PlayerActionRequest request;
        final Map<String, String> fields;
        int shown;

        Tooltip(TooltipScreen view, PlayerActionRequest request, Map<String, String> fields) {
            this.view = view;
            this.request = request;
            this.fields = fields;
        }
    }

    public PlayerInput(ClientActionExecutor executor, ScreenshotTaker screenshots) {
        this.executor = executor;
        this.screenshots = screenshots;
    }

    @Override
    public void receive(PlayerActionRequest request) {
        incoming.add(request);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean worldOpen = mc.world != null && mc.player != null;
        if (inventoryOpened != null) {
            // The game opened its screen during this tick's key handling.
            PlayerActionRequest opened = inventoryOpened;
            Map<String, String> fields = inventoryFields;
            inventoryOpened = null;
            inventoryFields = null;
            finish(mc, opened, "done", null, "none", screenName(mc.currentScreen), fields);
        }
        if (hold != null) {
            advanceHold(mc, false, true);
        }
        if (tooltip != null) {
            advanceTooltip(mc);
        }
        if (waiting != null && !worldOpen) {
            // A delayed action belongs to the world it was asked in; it never acts in another.
            PlayerActionRequest gone = waiting;
            waiting = null;
            finish(mc, gone, "refused", PlayerActionGate.REASON_NO_WORLD, screenName(mc.currentScreen), null,
                    new LinkedHashMap<String, String>());
        }
        if (waiting != null && --waitingTicks <= 0) {
            PlayerActionRequest due = waiting;
            waiting = null;
            take(mc, due);
        }
        PlayerActionRequest request;
        while ((request = incoming.poll()) != null) {
            if (waiting != null) {
                // A newer action of any kind takes the waiting one's place.
                finish(mc, waiting, "refused", REASON_REPLACED, screenName(mc.currentScreen), null,
                        new LinkedHashMap<String, String>());
                waiting = null;
            }
            if (request.getDelay() > 0) {
                waiting = request;
                waitingTicks = request.getDelay();
            } else {
                take(mc, request);
            }
        }
    }

    /**
     * Keeps the held input pressed for one more tick, or releases it with its reason. Pressed
     * again every tick, since the game sets every key from the real keyboard and mouse when the
     * window gains focus. A tick end counts the tick the game has just run with the key down.
     */
    private void advanceHold(Minecraft mc, boolean nextAction, boolean tickEnd) {
        Hold h = hold;
        if (h == null) {
            return;
        }
        if (tickEnd) {
            h.heldTicks++;
        }
        boolean worldOpen = mc.world != null && mc.player != null;
        String released = HoldRelease.reason(worldOpen, worldOpen && !died(mc), mc.currentScreen != null,
                h.focusedAtStart, Display.isActive(), nextAction, h.heldTicks, h.request.getTicks());
        if (released == null) {
            KeyBinding.setKeyBindState(h.binding.getKeyCode(), true);
            return;
        }
        hold = null;
        KeyBinding.setKeyBindState(h.binding.getKeyCode(), false);
        if (h.heldTicks == 0) {
            // Released before the game ran a tick with it: the press counted at the start would
            // still click once on the next tick, so it is taken back.
            while (h.binding.isPressed()) {
                // Each call takes back one counted press.
            }
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("input", h.request.getArgument());
        fields.put("heldTicks", String.valueOf(h.heldTicks));
        fields.put("released", released);
        finish(mc, h.request, "done", null, screenName(mc.currentScreen), null, fields);
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
        } else if (died(mc)) {
            closed = "died";
        } else if (mc.currentScreen != t.view) {
            closed = mc.currentScreen == null ? "person" : "replaced";
        } else if (++t.shown >= t.request.getTicks()) {
            // As the inventory closes: the game is told the player's container closed.
            mc.player.closeScreen();
            closed = "done";
        }
        if (closed == null) {
            return;
        }
        tooltip = null;
        t.fields.put("closed", closed);
        finish(mc, t.request, "done", null, screenName(mc.currentScreen), null, t.fields);
    }

    /**
     * Whether the player has died. The death screen can arrive a tick before the health: the
     * server sends the death, which opens it, before the health update that brings the health
     * to 0. A death screen another mod puts in its place is not recognised here.
     */
    private static boolean died(Minecraft mc) {
        return mc.player.isDead || mc.player.getHealth() <= 0.0F || mc.currentScreen instanceof GuiGameOver;
    }

    /** Opens the tooltip view. Its record waits for the view to close. */
    private void openTooltip(Minecraft mc, PlayerActionRequest request, String screen) {
        ItemStack stack = mc.player.inventory.getStackInSlot(request.getSlot());
        TooltipScreen view = new TooltipScreen(mc.player, request.getSlot(), request.isAdvanced(),
                ModifierKeys.codes(request.modifierKeys()));
        mc.displayGuiScreen(view);
        if (mc.currentScreen != view) {
            finish(mc, request, "failed", REASON_VIEW_NOT_OPENED, screenName(mc.currentScreen), null,
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
            finish(mc, request, "failed", failure.getClass().getSimpleName(), screen, null,
                    new LinkedHashMap<String, String>());
            return;
        }
        screenshotAfter(request, fields);
        tooltip = new Tooltip(view, request, fields);
    }

    private void take(Minecraft mc, PlayerActionRequest request) {
        if (hold != null) {
            // Another action starts: the held input is released first.
            advanceHold(mc, true, false);
        }
        String screen = screenName(mc.currentScreen);
        if (!ToolkitConfigLoader.get().isClientAllowActions()) {
            // The server checks the hello too; this holds if the setting changed since.
            finish(mc, request, "refused", "notAllowed", screen, null, new LinkedHashMap<String, String>());
            return;
        }
        if (executor.isOnReconnect()) {
            finish(mc, request, "refused", "busy", screen, null, new LinkedHashMap<String, String>());
            return;
        }
        KeyBinding binding = binding(mc, request);
        boolean worldOpen = mc.world != null && mc.player != null;
        // The inventory asked this tick opens on the next; until then it counts as open.
        boolean screenOpen = mc.currentScreen != null || inventoryOpened != null || tooltip != null;
        String refusal = PlayerActionGate.refusal(worldOpen, screenOpen, binding != null,
                binding == null ? 0 : binding.getKeyCode(),
                request.isTooltip() && worldOpen && mc.player.inventory.getStackInSlot(request.getSlot()).isEmpty(),
                request.isTooltip() && !request.modifierKeys().isEmpty() && !ModifierKeys.available());
        if (refusal != null) {
            finish(mc, request, "refused", refusal, screen, null, new LinkedHashMap<String, String>());
            return;
        }

        if (request.isTooltip()) {
            openTooltip(mc, request, screen);
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
            finish(mc, request, "failed", failure.getClass().getSimpleName(), screen, null,
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
            KeyBinding.setKeyBindState(binding.getKeyCode(), true);
            KeyBinding.onTick(binding.getKeyCode());
            hold = new Hold(request, binding, Display.isActive());
            screenshotAfter(request, fields);
            finish(mc, request, "started", null, screen, null, fields);
            return;
        } else {
            KeyBinding.onTick(binding.getKeyCode());
            if (request.isInventory()) {
                // Answered next tick, with the screen the game opened.
                screenshotAfter(request, fields);
                inventoryOpened = request;
                inventoryFields = fields;
                return;
            }
        }
        screenshotAfter(request, fields);
        finish(mc, request, "done", null, screen, null, fields);
    }

    /**
     * Asks this client's screenshot taker for the action's screenshot, by the screenshot
     * command's rules: the file is {@code screenshots/<label>-<requestTick>.png} and its
     * {@code SCREENSHOT} records follow. Taken only for an action that went ahead.
     */
    private void screenshotAfter(PlayerActionRequest request, Map<String, String> fields) {
        if (request.getScreenshot().isEmpty()) {
            return;
        }
        fields.put("screenshot", request.getScreenshot());
        // What the picture leaves out is on the server's request record and the client's
        // SCREENSHOT record; the report's fields are full for a long tooltip.
        screenshots.receive(new ScreenshotRequest(request.getScreenshot(), request.getScreenshotDelay(),
                request.getRequestTick(), request.getScreenshotHide()));
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
    private static void describeTarget(Minecraft mc, RayTraceResult hit, Map<String, String> fields) {
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

    private static void describeHeld(ItemStack held, Map<String, String> fields) {
        if (held == null || held.isEmpty()) {
            fields.put("held", "empty");
            return;
        }
        ResourceLocation item = held.getItem().getRegistryName();
        fields.put("held", item == null ? "unknown" : item.toString());
        fields.put("heldCount", String.valueOf(held.getCount()));
    }

    /** The open screen's class, bounded so a long modded name still fits a report. */
    private static String screenName(GuiScreen screen) {
        if (screen == null) {
            return "none";
        }
        String name = screen.getClass().getName();
        return name.length() <= 128 ? name : name.substring(name.length() - 128);
    }

    /**
     * One record on the client, one report to the server, and whether the game window had
     * focus. The client's record has every field; the report has what fits it (a tooltip's first
     * lines). Only a 1.7.0 server sends a player action, so the report's extra fields always
     * reach a server that reads them. After a leave there is no server to tell.
     */
    private static void finish(Minecraft mc, PlayerActionRequest request, String result, String reason,
                               String screen, String showing, Map<String, String> fields) {
        if (request.getDelay() > 0) {
            fields.put("delay", String.valueOf(request.getDelay()));
        }
        fields.put("focus", String.valueOf(Display.isActive()));
        LogRecord record = LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", request.getStep())
                .add("result", result)
                .add("reason", reason)
                .add("screen", screen)
                .add("showing", showing);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            record.add(field.getKey(), field.getValue());
        }
        record.add("requestTick", request.getRequestTick());
        ToolkitLog.write(record);
        if (mc.getConnection() != null) {
            ToolkitChannel.sendToServer(new ClientActionReport(request.getStep(), result, reason, screen, showing,
                    request.getToken(), request.getRequestTick()).withFields(TooltipLines.forReport(fields)));
        }
    }
}
