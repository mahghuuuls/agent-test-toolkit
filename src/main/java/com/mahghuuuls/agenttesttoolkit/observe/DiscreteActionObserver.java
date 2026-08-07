package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The five remaining discrete-action categories.
 *
 * <p>Grouped in one class because they share the same shape: gate, stamp, record. Splitting them
 * per category would multiply the boilerplate without separating anything that varies.
 *
 * <p>Every event choice here was settled against the Forge 1.12.2 sources, and three
 * of them are counter-intuitive enough that the reasoning is repeated at each handler. Picking
 * the obvious event instead would produce a log that is quietly wrong rather than obviously
 * broken, which is the worst failure mode this project has.
 *
 * <p>Handlers are registered once at startup and left registered, gated on a boolean rather
 * than subscribed and unsubscribed as categories are toggled. This package reads only
 * {@code logging} and {@code state}, and must not grow a dependency on {@code command} or
 * {@code session}.
 */
public final class DiscreteActionObserver {

    /**
     * Suppresses the second hand of a single interaction.
     *
     * <p>Found in testing, not by reading the source. Vanilla tries the main hand and then the
     * off hand when the first does not consume the action, so one right-click fires its event
     * <b>twice</b>, in the same tick, differing only by hand. The log showed exact pairs at
     * eight consecutive ticks.
     *
     * <p>This affects blocks as well as entities. {@code RightClickBlock} pairs the same way as
     * {@code EntityInteractSpecific}, and was missed the first time round: the suppression was
     * added for entities only, so right-clicking a block kept producing two records with a
     * {@code hand} field to tell them apart. That is the outcome the paragraph below rejects.
     *
     * <p>The log carries one record per logical action, and two hands attempting one click is
     * one action. Carrying a {@code hand} field and calling it explained would have been an
     * excuse rather than a fix: an agent counting interactions would still count double.
     *
     * <p>Keyed on player, target and tick together. Tick alone would drop a genuine interaction
     * with a second entity in the same tick, which is rare but real when a bundle runs.
     */
    private long lastEntityInteractionKey = Long.MIN_VALUE;
    private long lastEntityInteractionTick = Long.MIN_VALUE;

    private long lastBlockInteractionKey = Long.MIN_VALUE;
    private long lastBlockInteractionTick = Long.MIN_VALUE;

    boolean isRepeatEntityHand(int playerId, int targetId, long tick) {
        long key = ((long) playerId << 32) ^ (targetId & 0xFFFFFFFFL);
        if (tick == lastEntityInteractionTick && key == lastEntityInteractionKey) {
            return true;
        }
        lastEntityInteractionTick = tick;
        lastEntityInteractionKey = key;
        return false;
    }

    /**
     * The same suppression for a block.
     *
     * <p>Kept on its own slot rather than sharing one with the entity case. A block interaction
     * and an entity interaction in the same tick would otherwise evict each other, and the
     * evicted one's second hand would come through as a duplicate.
     *
     * <p>The button is part of the key. A left and a right click on the same block in the same
     * tick are two actions rather than one action repeated.
     *
     * <p>Takes the packed position as a {@code long} rather than a {@link BlockPos} so the rule
     * stays free of Minecraft types and can be exercised without a running game. That matters
     * here more than usual: the entity half of this suppression shipped correct while the block
     * half was missing entirely, and nothing could tell the difference without launching.
     */
    boolean isRepeatBlockHand(int playerId, long packedPos, String button, long tick) {
        long key = packedPos ^ ((long) playerId << 40) ^ (long) button.hashCode();
        if (tick == lastBlockInteractionTick && key == lastBlockInteractionKey) {
            return true;
        }
        lastBlockInteractionTick = tick;
        lastBlockInteractionKey = key;
        return false;
    }

    /**
     * Block breaking.
     *
     * <p>{@code BreakEvent} had no competing candidate. Cancelled breaks are not recorded: the
     * block does not break, so recording it would assert something that did not happen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (world == null || pos == null || event.isCanceled()) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.BLOCK_BREAK, world.isRemote,
                world.provider.getDimension(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D)) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BLOCK_BREAK), RecordContext.snapshot(world));
        SessionStamp.apply(record);
        addBlock(record, event.getState());
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        addActor(record, "brokenBy", event.getPlayer());
        ToolkitLog.write(record);
    }

    /**
     * Entity death.
     *
     * <p>Verified in the sources: one player death travelling the {@code EntityPlayer} path posts
     * {@code LivingDeathEvent} <b>twice</b>. A mob death posts once, and a server player death
     * through {@code EntityPlayerMP} posts once. The server-side filter is therefore not
     * defensive boilerplate here, it is what keeps player deaths from being recorded twice.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world == null || event.isCanceled()) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.ENTITY_DEATH, victim.world.isRemote,
                victim.world.provider.getDimension(), victim.posX, victim.posY, victim.posZ)) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ENTITY_DEATH), RecordContext.snapshot(victim.world));
        SessionStamp.apply(record);
        addEntity(record, "entity", victim);
        record.addEntityPos("pos", victim.posX, victim.posY, victim.posZ);
        if (event.getSource() != null) {
            record.add("damageType", event.getSource().getDamageType());
            addActor(record, "killedBy", event.getSource().getTrueSource());
        }
        ToolkitLog.write(record);
    }

    /**
     * Right-clicking a block.
     *
     * <p>Cancelled events are skipped. {@code RightClickEmpty} is not subscribed because the
     * Forge source states it is client-only and the server is never informed, so recording it
     * is impossible rather than merely undesirable.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        interaction(event, "RIGHT");
    }

    /**
     * Left-clicking a block.
     *
     * <p>Verified in the sources: when a {@code LeftClickBlock} is <b>cancelled</b> and the player
     * holds the mouse down, the event keeps firing, because of how vanilla calls the left-click
     * handlers. Recording cancelled events would therefore emit a stream of records for one
     * held button. Skipping cancelled events removes that entirely, and is also correct on its
     * own terms: a cancelled interaction did not happen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        interaction(event, "LEFT");
    }

    private void interaction(PlayerInteractEvent event, String button) {
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (player == null || world == null || pos == null || event.isCanceled()) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.PLAYER_INTERACTION, world.isRemote,
                world.provider.getDimension(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D)) {
            return;
        }
        // The gate runs first so a suppressed hand does not consume the dedupe slot for a
        // filtered-out interaction, which would then let the next real one through twice.
        if (isRepeatBlockHand(player.getEntityId(), pos.toLong(), button,
                world.getTotalWorldTime())) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.PLAYER_INTERACT), RecordContext.snapshot(world));
        SessionStamp.apply(record);
        record.add("button", button);
        // The hand that actually produced the record. Only the first is recorded: the second
        // is suppressed above, so this reads as which hand won rather than as one of a pair.
        record.add("hand", event.getHand() == null ? null : event.getHand().name());
        addBlock(record, world.getBlockState(pos));
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        addActor(record, "player", player);
        addHeld(record, player);
        ToolkitLog.write(record);
    }

    /**
     * Right-clicking an entity.
     *
     * <p>The opposite of the obvious choice. {@code EntityInteractSpecific} fires on
     * <b>every</b> right-click on an entity; {@code EntityInteract} fires only when the
     * specific event's result was not {@code SUCCESS}. Subscribing to {@code EntityInteract},
     * which reads as the more general name, would silently miss every <i>successful</i>
     * interaction, and subscribing to both would double-record the unsuccessful ones.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        EntityPlayer player = event.getEntityPlayer();
        Entity target = event.getTarget();
        World world = event.getWorld();
        if (player == null || target == null || world == null || event.isCanceled()) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.ENTITY_INTERACTION, world.isRemote,
                world.provider.getDimension(), target.posX, target.posY, target.posZ)) {
            return;
        }
        // The gate runs first so a suppressed hand does not consume the dedupe slot for a
        // filtered-out interaction, which would then let the next real one through twice.
        if (isRepeatEntityHand(player.getEntityId(), target.getEntityId(),
                world.getTotalWorldTime())) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ENTITY_INTERACT), RecordContext.snapshot(world));
        SessionStamp.apply(record);
        addEntity(record, "target", target);
        record.addEntityPos("pos", target.posX, target.posY, target.posZ);
        record.add("hand", event.getHand() == null ? null : event.getHand().name());
        addActor(record, "player", player);
        addHeld(record, player);
        ToolkitLog.write(record);
    }

    /**
     * Using an item while targeting neither a block nor an entity.
     *
     * <p>{@code RightClickItem} fires only in that case, so it cannot double-record with
     * {@code player_interaction} or {@code entity_interaction}. {@code LivingEntityUseItemEvent
     * .Tick} was rejected outright: it fires every tick while an item is held in use, which
     * would bury a single bow draw under dozens of records.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        if (player == null || world == null || event.isCanceled()) {
            return;
        }
        if (!ObserverGate.shouldRecord(LoggingCategory.ITEM_USE, world.isRemote,
                world.provider.getDimension(), player.posX, player.posY, player.posZ)) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ITEM_USE), RecordContext.snapshot(world));
        SessionStamp.apply(record);
        record.add("hand", event.getHand() == null ? null : event.getHand().name());
        addActor(record, "player", player);
        addHeld(record, player);
        record.addEntityPos("pos", player.posX, player.posY, player.posZ);
        ToolkitLog.write(record);
    }

    // --- shared field building -------------------------------------------------------

    private static void addBlock(LogRecord record, IBlockState state) {
        if (state == null) {
            return;
        }
        ResourceLocation id = Block.REGISTRY.getNameForObject(state.getBlock());
        record.add("block", id == null ? null : id.toString());
        record.add("meta", state.getBlock().getMetaFromState(state));
        record.add("blockState", state.toString());
    }

    private static void addEntity(LogRecord record, String prefix, Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        // A player has no entity registry key, so the field is omitted rather than rendered
        // as "null", and the record still identifies the entity by id and name.
        record.add(prefix, id == null ? null : id.toString());
        record.add(prefix + "Id", entity.getEntityId());
        if (entity.hasCustomName()) {
            record.add(prefix + "Name", entity.getCustomNameTag());
        }
    }

    private static void addActor(LogRecord record, String prefix, Entity actor) {
        if (actor == null) {
            // Environmental causes have no actor. Omitted entirely rather than reported as
            // "none", which would be indistinguishable from a real entity called none.
            return;
        }
        record.add(prefix, actor.getName());
        record.add(prefix + "Id", actor.getEntityId());
    }

    private static void addHeld(LogRecord record, EntityPlayer player) {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        record.add("held", id == null ? null : id.toString());
        record.add("heldMeta", stack.getMetadata());
    }
}
