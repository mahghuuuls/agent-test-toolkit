package com.mahghuuuls.agenttesttoolkit.integration.baubles;

/**
 * Which equip calls a slot change makes, and the order a slot change makes them in.
 *
 * <p>No Baubles provider makes these calls when a slot is set from code; each makes them only
 * from its own slot screen. So the toolkit makes them itself, exactly once each: the old
 * stack's unequip when there was one, the new stack's equip when there is one. Setting a stack
 * equal to the one already there makes neither, as a provider's own screen does, so a repeated
 * set does not grant an equip bonus twice. Nothing is called while the provider says its
 * events are blocked.
 *
 * <p>Free of Baubles and Minecraft types, so the whole slot change is tested without the game:
 * the bridge supplies the slot through {@link Slot}.
 */
final class BaubleEquipCalls {

    /** One bauble slot as the bridge reaches it, in whatever stack type it uses. */
    interface Slot<S> {
        S get();

        void put(S stack);

        boolean isEmpty(S stack);

        /** Whether the two stacks are the same item, count and tag. */
        boolean equal(S a, S b);

        /** Tells this stack's bauble it was unequipped. */
        void unequipped(S stack);

        /** Tells this stack's bauble it was equipped. */
        void equipped(S stack);
    }

    private static final BaubleEquipCalls NONE = new BaubleEquipCalls(false, false);

    private final boolean unequipOld;
    private final boolean equipNew;

    private BaubleEquipCalls(boolean unequipOld, boolean equipNew) {
        this.unequipOld = unequipOld;
        this.equipNew = equipNew;
    }

    static BaubleEquipCalls decide(boolean oldEmpty, boolean newEmpty, boolean oldEqualsNew, boolean eventsBlocked) {
        if (eventsBlocked || oldEqualsNew) {
            return NONE;
        }
        return new BaubleEquipCalls(!oldEmpty, !newEmpty);
    }

    /**
     * Puts the stack in the slot the way a provider's slot screen does: the old stack is told it
     * was unequipped before the slot changes, the new one that it was equipped after, each only
     * as {@link #decide} allows.
     */
    static <S> void apply(Slot<S> slot, S stack, boolean eventsBlocked) {
        S previous = slot.get();
        BaubleEquipCalls calls = decide(slot.isEmpty(previous), slot.isEmpty(stack), slot.equal(previous, stack),
                eventsBlocked);
        if (calls.unequipOld) {
            slot.unequipped(previous);
        }
        slot.put(stack);
        if (calls.equipNew) {
            slot.equipped(stack);
        }
    }

    /** Whether the old stack is told it was unequipped, before the slot changes. */
    boolean unequipOld() {
        return unequipOld;
    }

    /** Whether the new stack is told it was equipped, after the slot changes. */
    boolean equipNew() {
        return equipNew;
    }
}
