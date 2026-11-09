package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.PlayerActionRequest;
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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
 * <p>Client-only: registered through {@code ClientProxy} and never referenced from shared code.
 */
public final class PlayerInput implements ToolkitChannel.PlayerActionReceiver {

    private final ConcurrentLinkedQueue<PlayerActionRequest> incoming = new ConcurrentLinkedQueue<PlayerActionRequest>();
    private final ClientActionExecutor executor;

    public PlayerInput(ClientActionExecutor executor) {
        this.executor = executor;
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
        PlayerActionRequest request;
        while ((request = incoming.poll()) != null) {
            take(mc, request);
        }
    }

    private void take(Minecraft mc, PlayerActionRequest request) {
        String screen = screenName(mc.currentScreen);
        if (!ToolkitConfigLoader.get().isClientAllowActions()) {
            // The server checks the hello too; this holds if the setting changed since.
            finish(request, "refused", "notAllowed", screen, new LinkedHashMap<String, String>());
            return;
        }
        if (executor.isOnReconnect()) {
            finish(request, "refused", "busy", screen, new LinkedHashMap<String, String>());
            return;
        }
        KeyBinding binding = request.isUse() ? mc.gameSettings.keyBindUseItem
                : request.isAttack() ? mc.gameSettings.keyBindAttack : null;
        String refusal = PlayerActionGate.refusal(mc.world != null && mc.player != null, mc.currentScreen != null,
                binding != null, binding == null ? 0 : binding.getKeyCode());
        if (refusal != null) {
            finish(request, "refused", refusal, screen, new LinkedHashMap<String, String>());
            return;
        }

        Map<String, String> fields = new LinkedHashMap<String, String>();
        try {
            if (request.isSelect()) {
                fields.put("slot", String.valueOf(request.getSlot()));
                describeHeld(mc.player.inventory.getStackInSlot(request.getSlot()), fields);
            } else {
                // What the click will meet and with what, read before it is made.
                describeTarget(mc, mc.objectMouseOver, fields);
                describeHeld(mc.player.getHeldItemMainhand(), fields);
            }
        } catch (RuntimeException failure) {
            // Reading the target or the item threw, as a modded block or item can: nothing was
            // pressed, the action failed, and the client tick goes on.
            finish(request, "failed", failure.getClass().getSimpleName(), screen, new LinkedHashMap<String, String>());
            return;
        }
        if (request.isSelect()) {
            // The game sends the held-slot packet itself on its next update, as for a number key.
            mc.player.inventory.currentItem = request.getSlot();
        } else {
            KeyBinding.onTick(binding.getKeyCode());
        }
        finish(request, "done", null, screen, fields);
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
     * One record on the client, one report to the server with the same fields. Only a 1.7.0
     * server sends a player action, so the report's extra fields always reach a server that
     * reads them.
     */
    private static void finish(PlayerActionRequest request, String result, String reason, String screen,
                               Map<String, String> fields) {
        LogRecord record = LogRecord.of(EventType.CLIENT_ACTION)
                .add("side", "CLIENT")
                .add("step", request.getStep())
                .add("result", result)
                .add("reason", reason)
                .add("screen", screen);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            record.add(field.getKey(), field.getValue());
        }
        record.add("requestTick", request.getRequestTick());
        ToolkitLog.write(record);
        ToolkitChannel.sendToServer(new ClientActionReport(request.getStep(), result, reason, screen, null,
                request.getToken(), request.getRequestTick()).withFields(fields));
    }
}
