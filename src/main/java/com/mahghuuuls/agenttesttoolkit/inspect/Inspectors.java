package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Builds inspection records from current generic game state.
 *
 * <p>Answers "what is true now", as opposed to event logging which answers "what happened".
 *
 * <p>Reports only what Minecraft and Forge expose. A modded block's tile entity class is
 * reported; its contents are not interpreted, because interpreting them would require knowing
 * how that mod works, which is the boundary this project does not cross. Per concept identity
 * decision 2, that information belongs in the mod under test, not here.
 *
 * <p>Fields that do not apply to a given target are omitted rather than reported as zero or
 * empty. An inspection must not fail merely because a target has no health.
 */
public final class Inspectors {

    private Inspectors() {
    }

    public static LogRecord player(EntityPlayer player) {
        LogRecord record = LogRecord.of(EventType.PLAYER_INSPECT)
                .add("name", player.getName())
                .add("uuid", player.getUniqueID().toString())
                .add("dimension", player.world.provider.getDimension());

        addPosition(record, player);
        addLivingState(record, player);

        record.add("hunger", player.getFoodStats().getFoodLevel());
        record.addDecimal("saturation", player.getFoodStats().getSaturationLevel());
        // "Experience" is ambiguous, and level alone answers the narrower question, so the
        // cumulative total is reported alongside it.
        record.add("experienceLevel", player.experienceLevel);
        record.add("experienceTotal", player.experienceTotal);

        if (player instanceof EntityPlayerMP) {
            record.add("gameMode", ((EntityPlayerMP) player).interactionManager.getGameType().getName());
        }

        addStack(record, "mainHand", player.getHeldItemMainhand());
        addStack(record, "offHand", player.getHeldItemOffhand());
        addPotionEffects(record, player);
        return record;
    }

    public static LogRecord entity(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        LogRecord record = LogRecord.of(EventType.ENTITY_INSPECT)
                // A player has no entity registry key, so the field is omitted rather than
                // rendered as "null", and the entity is still fully inspectable.
                .add("entity", id == null ? null : id.toString())
                .add("entityId", entity.getEntityId())
                .add("uuid", entity.getUniqueID().toString())
                .add("dimension", entity.world.provider.getDimension());

        if (entity.hasCustomName()) {
            record.add("name", entity.getCustomNameTag());
        }

        addPosition(record, entity);
        record.addEntityPos("motion", entity.motionX, entity.motionY, entity.motionZ);

        // The remaining fire tick count would be the more useful field, but Minecraft 1.12.2
        // keeps it in a protected field with no public accessor; reaching it would need an
        // access transformer, which this project deliberately does without and should not
        // introduce for one optional field. Burning state is reported instead,
        // and only when true, so the field is absent rather than misleading.
        if (entity.isBurning()) {
            record.add("burning", true);
        }

        if (entity instanceof EntityLivingBase) {
            addLivingState(record, (EntityLivingBase) entity);
            addPotionEffects(record, (EntityLivingBase) entity);
        }
        return record;
    }

    public static LogRecord block(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation id = Block.REGISTRY.getNameForObject(block);

        LogRecord record = LogRecord.of(EventType.BLOCK_INSPECT)
                .add("block", id == null ? null : id.toString())
                .add("meta", block.getMetaFromState(state))
                .add("dimension", world.provider.getDimension());
        record.addBlockPos("pos", pos.getX(), pos.getY(), pos.getZ());
        record.add("blockState", state.toString());

        TileEntity tile = world.getTileEntity(pos);
        if (tile != null) {
            // The class name only. Interpreting a modded tile entity's contents would require
            // knowing that mod's internals, which is out of scope by design.
            record.add("tileEntityClass", tile.getClass().getName());
        }
        return record;
    }

    private static void addPosition(LogRecord record, Entity entity) {
        record.addEntityPos("pos", entity.posX, entity.posY, entity.posZ);
    }

    private static void addLivingState(LogRecord record, EntityLivingBase living) {
        record.addDecimal("health", living.getHealth());
        record.addDecimal("maxHealth", living.getMaxHealth());
    }

    /**
     * Renders active effects as {@code id:amplifier:duration}, comma separated.
     *
     * <p>Relies on registry ids containing no whitespace, which keeps the whole value a
     * single unquoted field. Were that to change, {@link LogRecord} would quote and escape
     * the entire value, which stays parseable but shifts where the delimiters sit. Noted so a
     * future change to the separator choice is made deliberately.
     */
    private static void addPotionEffects(LogRecord record, EntityLivingBase living) {
        StringBuilder effects = new StringBuilder();
        for (PotionEffect effect : living.getActivePotionEffects()) {
            ResourceLocation id = Potion.REGISTRY.getNameForObject(effect.getPotion());
            if (effects.length() > 0) {
                effects.append(',');
            }
            effects.append(id == null ? "unknown" : id.toString())
                    .append(':').append(effect.getAmplifier())
                    .append(':').append(effect.getDuration());
        }
        // Omitted entirely when there are none, rather than reported as an empty list.
        record.add("potionEffects", effects.toString());
    }

    private static void addStack(LogRecord record, String prefix, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        record.add(prefix, id == null ? null : id.toString())
                .add(prefix + "Count", stack.getCount())
                .add(prefix + "Meta", stack.getMetadata());
        if (stack.hasTagCompound()) {
            record.add(prefix + "HasNbt", true);
        }
    }
}
