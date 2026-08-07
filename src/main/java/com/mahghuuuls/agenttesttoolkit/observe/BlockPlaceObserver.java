package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Observes block placement.
 *
 * <h2>Why this event</h2>
 *
 * <p>{@code BlockEvent.EntityPlaceEvent} rather than {@code PlaceEvent}, settled by reading
 * the Forge 1.12.2 source. {@code PlaceEvent} is annotated deprecated for removal in
 * 1.13. The hierarchy is {@code MultiPlaceEvent extends PlaceEvent extends EntityPlaceEvent},
 * with {@code EntityMultiPlaceEvent} a fourth sibling, so subscribing at the base type receives
 * all of them.
 *
 * <p>Exactly one record per placement action is not an assumption: {@code ForgeHooks} posts a
 * multi-place event when the snapshot list has more than one entry and a single-place event
 * otherwise, in mutually exclusive branches. Placing a door or a bed therefore produces one
 * record, not two.
 *
 * <h2>Why lowest priority</h2>
 *
 * <p>Cancelled events must not be recorded, because a cancelled placement did not happen and a
 * record claiming otherwise would be worse than no record. Handlers do not receive cancelled
 * events by default, so observing last means any other mod's cancellation has already taken
 * effect and this handler is simply never called. Observing early would record placements that
 * were subsequently prevented.
 */
public final class BlockPlaceObserver {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        World world = event.getWorld();
        // Filtered on the placed block's own position, not the placer's. An arena filter
        // should admit a block placed inside it from outside, since the event being observed
        // is the block appearing, not the arm that swung.
        net.minecraft.util.math.BlockPos placedAt = event.getPos();
        if (world == null || placedAt == null
                || !ObserverGate.shouldRecord(LoggingCategory.BLOCK_PLACE, world.isRemote,
                world.provider.getDimension(),
                placedAt.getX() + 0.5D, placedAt.getY() + 0.5D, placedAt.getZ() + 0.5D)) {
            return;
        }

        IBlockState placed = event.getPlacedBlock();
        BlockPos pos = event.getPos();

        LogRecord record = RecordContext.stamp(
                LogRecord.of(LoggingCategory.BLOCK_PLACE.getEventType()),
                RecordContext.snapshot(world));
        SessionStamp.apply(record);

        // Field order matches BLOCK_INSPECT, which describes the same physical concept.
        // Field order only has to be consistent within an event type, but two record types that
        // both describe a block should read the same way when an agent compares them, which
        // is exactly what the corroboration check does.
        record.add("block", registryName(placed));
        record.add("meta", placed.getBlock().getMetaFromState(placed));
        record.add("dimension", world.provider.getDimension());
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        record.add("blockState", placed.toString());

        // Nullable: a block can be placed by something that is not an entity, for example a
        // dispenser. Omitted rather than reported as "none", like every absent field.
        Entity placer = event.getEntity();
        if (placer != null) {
            ResourceLocation placerId = EntityList.getKey(placer);
            record.add("placedBy", placerId == null ? placer.getName() : placerId.toString());
            record.add("placedById", placer.getEntityId());
            // Entity ids are reassigned on reconnect, so they cannot join a placement to a
            // later inspection of the same player across a session boundary. The uuid can,
            // and every other record type in the project already carries one.
            record.add("placedByUuid", placer.getUniqueID().toString());
        }

        ToolkitLog.write(record);
    }


    private static String registryName(IBlockState state) {
        ResourceLocation id = Block.REGISTRY.getNameForObject(state.getBlock());
        return id == null ? null : id.toString();
    }
}
