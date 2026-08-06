package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Records entities entering the world, excluding those that were merely loaded with a chunk.
 *
 * <p>The chunk-load distinction is what makes this category usable at all. {@code
 * EntityJoinWorldEvent} fires from four call sites and only {@code World#spawnEntity}
 * represents a new spawn; without the discriminator, walking into fresh terrain would produce
 * hundreds of records and bury the one the operator asked about. See
 * {@link SpawnClassification#isNewSpawn(boolean)} for the source reasoning.
 *
 * <p>Checks are ordered cheapest-first and all of them precede record construction, so an
 * excluded spawn costs a flag read and two {@code instanceof} tests.
 */
public final class EntitySpawnObserver {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        World world = event.getWorld();
        if (entity == null || world == null || event.isCanceled()) {
            return;
        }

        // Cheapest discriminator first, and the one that removes the most volume by far.
        if (!SpawnClassification.isNewSpawn(entity.addedToChunk)) {
            return;
        }

        boolean includeAll = ToolkitConfigLoader.get().isSpawnIncludingItems();
        if (!SpawnClassification.shouldRecordType(
                entity instanceof EntityItem, entity instanceof EntityXPOrb, includeAll)) {
            return;
        }

        if (!ObserverGate.shouldRecord(LoggingCategory.ENTITY_SPAWN, world.isRemote,
                world.provider.getDimension(), entity.posX, entity.posY, entity.posZ)) {
            return;
        }

        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ENTITY_SPAWN), RecordContext.snapshot(world));
        SessionStamp.apply(record);

        ResourceLocation id = EntityList.getKey(entity);
        // A player has no entity registry key, so the field is omitted rather than rendered as
        // "null". Players do join through this event, and a login is a real thing to record.
        record.add("entity", id == null ? null : id.toString());
        record.add("entityId", entity.getEntityId());
        if (entity.hasCustomName()) {
            record.add("name", entity.getCustomNameTag());
        }
        record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);

        // REQ: recorded fields must be readable and correct, not empty or default-valued. The
        // event fires before the entity is added to a chunk but after its constructor has run,
        // so position and health are populated.
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            record.addDecimal("health", living.getHealth());
            record.addDecimal("maxHealth", living.getMaxHealth());
        }
        ToolkitLog.write(record);
    }
}
