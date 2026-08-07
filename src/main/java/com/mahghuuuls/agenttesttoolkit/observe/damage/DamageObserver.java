package com.mahghuuuls.agenttesttoolkit.observe.damage;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.observe.ObserverGate;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Produces exactly one {@code ENTITY_DAMAGE} record per logical damage attempt.
 *
 * <p>See {@link DamageCorrelation} for why three Forge events are stages rather than
 * duplicates, and {@link DamageCorrelationBuffer} for why entries are held until a tick
 * boundary instead of being emitted at the final stage.
 *
 * <h2>Cancellation, and why two handlers opt in to receiving cancelled events</h2>
 *
 * <p>Forge's dispatcher skips a handler entirely when an event is cancelable, cancelled, and
 * the handler has not opted in. All three damage events are cancelable, and these handlers run
 * last, so any other mod's cancellation is already in effect by the time they would be called.
 *
 * <p>For the attack stage that default is actively wrong. If another mod cancels the attack,
 * the handler never runs, no correlation is opened, and the hit disappears from the log
 * completely. A cancelled hit that leaves no trace is the precise failure this observer exists
 * to prevent, and the not-applied default cannot save it because nothing was created to
 * default. So {@code onLivingAttack} opts in: it must observe every attempt, including ones
 * another mod refuses.
 *
 * <p>{@code onLivingHurt} opts in for accuracy rather than existence. A hurt stage cancelled by
 * another mod did happen, and skipping it would report {@code stoppedAt=ATTACK} for something
 * that actually reached and was stopped at the hurt stage, which have different causes.
 *
 * <p>{@code onLivingDamage} deliberately keeps the default. A cancelled damage event means the
 * damage was not applied, and skipping the handler leaves the correlation at not-applied with
 * {@code stoppedAt=HURT}, which is exactly right. Opting in there would flip the outcome to
 * applied for damage that never touched health.
 */
public final class DamageObserver {

    private final DamageCorrelationBuffer<Entity> buffer = new DamageCorrelationBuffer<Entity>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttack(LivingAttackEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (!shouldObserve(target)) {
            return;
        }
        DiagnosticSession session = ToolkitState.getActiveSession();
        buffer.onAttack(target,
                target.world.getTotalWorldTime(),
                target.world.provider.getDimension(),
                RecordContext.side(target.world),
                session == null ? null : session.getName(),
                session == null ? -1L : session.getTick(),
                event.getSource() == null ? null : event.getSource().getDamageType(),
                event.getAmount(),
                target.getHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (!shouldObserve(target)) {
            return;
        }
        buffer.onHurt(target, event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (!shouldObserve(target)) {
            return;
        }
        buffer.onDamage(target, event.getAmount());
    }

    /**
     * Flushes at the end of the server tick.
     *
     * <p>Damage resolves synchronously within a tick, so by this point every attempt observed
     * during it has finished advancing through whatever stages it was going to reach. This is
     * also why health after the fact can be read directly rather than computed by subtraction:
     * the game has already applied the damage.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || buffer.isEmpty()) {
            return;
        }
        List<DamageCorrelationBuffer.Entry<Entity>> drained = buffer.drain();
        for (DamageCorrelationBuffer.Entry<Entity> entry : drained) {
            emit(entry.getTarget(), entry.getCorrelation());
        }
    }

    private void emit(Entity target, DamageCorrelation correlation) {
        LogRecord record = LogRecord.of(EventType.ENTITY_DAMAGE);

        // Every context field comes from the correlation rather than from the current moment,
        // because the record is written up to a tick after the attack was observed. Reading
        // the session tick here instead would report the following tick's value, since the
        // session ticker is registered first and has already advanced it by the time this runs.
        record.add("side", correlation.getSide());
        record.add("worldTick", correlation.getWorldTick());
        if (correlation.getSessionName() != null) {
            record.add("session", correlation.getSessionName());
            record.add("sessionTick", correlation.getSessionTick());
        }

        ResourceLocation id = EntityList.getKey(target);
        record.add("target", id == null ? null : id.toString());
        record.add("targetId", target.getEntityId());
        record.add("targetUuid", target.getUniqueID().toString());
        if (target.hasCustomName()) {
            record.add("name", target.getCustomNameTag());
        }
        record.add("dimension", correlation.getDimension());

        record.addDecimal("amountRaw", correlation.getAmountRaw());
        if (correlation.reachedHurt()) {
            record.addDecimal("amountPreMitigation", correlation.getAmountPreMitigation());
        }
        if (correlation.reachedDamage()) {
            record.addDecimal("amountFinal", correlation.getAmountFinal());
        }

        record.addDecimal("healthBefore", correlation.getHealthBefore());
        if (target instanceof EntityLivingBase) {
            // Read now rather than computed as healthBefore minus amountFinal. A read reflects
            // whatever the game actually did, including effects the three stages do not expose,
            // and disagreeing with the subtraction is itself diagnostically useful.
            record.addDecimal("healthAfter", ((EntityLivingBase) target).getHealth());
        }

        record.add("source", correlation.getSource());
        record.add("outcome", correlation.getOutcome());
        record.add("stoppedAt", correlation.getStoppedAt());

        ToolkitLog.write(record);
    }

    /**
     * Discards pending correlations when the server stops.
     *
     * <p>In-flight, server-bound state must not survive the server it is bound to.
     * Entries hold entity references from a world about to unload, and this observer
     * is registered permanently, so a crash between the attack stage and the tick-end flush
     * would otherwise leak them into the next world's first tick.
     *
     * <p>Distinct from {@code ToolkitState}, which deliberately survives a server stop.
     */
    public int discardPending() {
        return buffer.discardPending();
    }

    private static boolean shouldObserve(EntityLivingBase target) {
        if (target == null || target.world == null) {
            return false;
        }
        // Filtered on the victim's position. Damage from a distant source into a filtered
        // region is still damage happening in that region, which is what an operator narrowing
        // to an arena is asking about.
        return ObserverGate.shouldRecord(LoggingCategory.ENTITY_DAMAGE, target.world.isRemote,
                target.world.provider.getDimension(), target.posX, target.posY, target.posZ);
    }
}
