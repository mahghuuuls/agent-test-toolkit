package com.mahghuuuls.agenttesttoolkit.integration.baubles;

import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import net.minecraft.command.CommandException;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider methods looked up by reflection, against fake handlers shaped like the two
 * providers' {@code IBaublesItemHandler}: official Baubles 1.5.2 has {@code isEventBlocked()} and
 * no per-slot type; Bubbles 2.4.10 has {@code getSlotType(int)} and no {@code isEventBlocked()}.
 * Baubles itself is not on the test classpath, so the fakes stand in for both.
 */
class ProviderMethodsTest {

    /** Official Baubles' handler, as far as these methods go. */
    public interface OfficialHandler {
        boolean isEventBlocked();
    }

    public static final class Official implements OfficialHandler {
        private final boolean blocked;

        Official(boolean blocked) {
            this.blocked = blocked;
        }

        @Override
        public boolean isEventBlocked() {
            return blocked;
        }
    }

    /** A per-slot type, named the way a Forge registry entry is. */
    public static final class SlotType {
        private final ResourceLocation name;

        SlotType(ResourceLocation name) {
            this.name = name;
        }

        public ResourceLocation getRegistryName() {
            return name;
        }
    }

    /** Bubbles' handler: types per slot, and no event flag at all. */
    public interface BubblesHandler {
        SlotType getSlotType(int slot);
    }

    public static final class Bubbles implements BubblesHandler {
        private final SlotType[] types;

        Bubbles(SlotType... types) {
            this.types = types;
        }

        @Override
        public SlotType getSlotType(int slot) {
            return slot < types.length ? types[slot] : null;
        }
    }

    /** A provider whose methods are there but fail when called. */
    public interface BrokenHandler {
        boolean isEventBlocked();

        SlotType getSlotType(int slot);
    }

    public static final class Broken implements BrokenHandler {
        @Override
        public boolean isEventBlocked() {
            throw new IllegalStateException("not ready");
        }

        @Override
        public SlotType getSlotType(int slot) {
            throw new IndexOutOfBoundsException("slot " + slot);
        }
    }

    /** A per-slot type with no registry name: the slot is named the older way. */
    public interface UnnamedTypes {
        Object getSlotType(int slot);
    }

    private static SlotType type(String name) {
        return new SlotType(new ResourceLocation(name));
    }

    @Test
    @DisplayName("a handler without isEventBlocked, as Bubbles has, reads as events not blocked and calls nothing missing")
    void bubblesHasNoEventFlag() throws CommandException {
        ProviderMethods methods = ProviderMethods.probe(BubblesHandler.class);
        assertFalse(methods.hasEventFlag());
        // Before the lookup, the bridge called isEventBlocked directly, and this provider threw
        // NoSuchMethodError at that call; the answer must now be a plain "not blocked".
        assertFalse(methods.eventsBlocked(new Bubbles()));
    }

    @Test
    @DisplayName("official Baubles' event flag is read through the lookup, blocked and not")
    void officialEventFlag() throws CommandException {
        ProviderMethods methods = ProviderMethods.probe(OfficialHandler.class);
        assertTrue(methods.hasEventFlag());
        assertTrue(methods.eventsBlocked(new Official(true)));
        assertFalse(methods.eventsBlocked(new Official(false)));
        assertFalse(methods.hasSlotTypes(), "official Baubles names no type per slot");
        assertNull(methods.slotTypeName(new Official(false), 1), "so the slot is named the older way");
    }

    @Test
    @DisplayName("Bubbles' per-slot type names the slot: a Baubles type by its path, another mod's in full")
    void bubblesSlotTypes() throws CommandException {
        ProviderMethods methods = ProviderMethods.probe(BubblesHandler.class);
        assertTrue(methods.hasSlotTypes());
        Bubbles handler = new Bubbles(type("baubles:amulet"), type("baubles:ring"), type("somemod:wing"));
        assertEquals("amulet", methods.slotTypeName(handler, 0));
        assertEquals("ring", methods.slotTypeName(handler, 1));
        assertEquals("somemod:wing", methods.slotTypeName(handler, 2));
        assertNull(methods.slotTypeName(handler, 3), "no type for the slot");
        assertNull(methods.slotTypeName(new Bubbles(new SlotType(null)), 0), "a type without a name");
    }

    @Test
    @DisplayName("a per-slot type without a registry name is not used")
    void unnamedTypesFallBack() throws CommandException {
        ProviderMethods methods = ProviderMethods.probe(UnnamedTypes.class);
        assertFalse(methods.hasSlotTypes());
        assertNull(methods.slotTypeName(new UnnamedTypes() {
            @Override
            public Object getSlotType(int slot) {
                return "baubles:ring";
            }
        }, 0));
    }

    @Test
    @DisplayName("a provider method that fails is the unsupported key, naming the method, never an uncaught error")
    void failingMethodIsUnsupported() {
        ProviderMethods methods = ProviderMethods.probe(BrokenHandler.class);
        CommandException events = assertThrows(CommandException.class, () -> methods.eventsBlocked(new Broken()));
        assertEquals(BaublesBridge.KEY_UNSUPPORTED, events.getMessage());
        assertEquals("[isEventBlocked (IllegalStateException)]", Arrays.toString(events.getErrorObjects()));
        CommandException slots = assertThrows(CommandException.class, () -> methods.slotTypeName(new Broken(), 4));
        assertEquals(BaublesBridge.KEY_UNSUPPORTED, slots.getMessage());
        assertEquals("[getSlotType (IndexOutOfBoundsException)]", Arrays.toString(slots.getErrorObjects()));
        CommandException wrongHandler = assertThrows(CommandException.class,
                () -> ProviderMethods.probe(OfficialHandler.class).eventsBlocked(new Bubbles()));
        assertTrue(String.valueOf(wrongHandler.getErrorObjects()[0]).startsWith("isEventBlocked"),
                "a handler of another type is reported, not thrown");
    }

    @Test
    @DisplayName("only the baubles namespace is shortened")
    void typeNames() {
        assertEquals("ring", ProviderMethods.typeName("baubles:ring"));
        assertEquals("somemod:wing", ProviderMethods.typeName("somemod:wing"));
        assertEquals("baublesextra:ring", ProviderMethods.typeName("baublesextra:ring"));
        assertEquals("ring", ProviderMethods.typeName("ring"));
    }
}
