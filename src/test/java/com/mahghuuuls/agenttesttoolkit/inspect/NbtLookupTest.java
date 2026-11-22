package com.mahghuuuls.agenttesttoolkit.inspect;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading one value by path from real NBT compounds shaped as the game saves a stack, a zombie,
 * a chest and a capability, and the text each tag type gives.
 */
class NbtLookupTest {

    private static final int LIMIT = 4096;

    /** A diamond sword with damage 5 and Sharpness V, as {@code ItemStack.writeToNBT} writes it. */
    private static NBTTagCompound sword() {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setString("id", "minecraft:diamond_sword");
        stack.setByte("Count", (byte) 1);
        stack.setShort("Damage", (short) 5);
        NBTTagCompound sharpness = new NBTTagCompound();
        sharpness.setShort("id", (short) 16);
        sharpness.setShort("lvl", (short) 5);
        NBTTagList ench = new NBTTagList();
        ench.appendTag(sharpness);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("ench", ench);
        stack.setTag("tag", tag);
        return stack;
    }

    private static NbtLookup.Result read(NBTTagCompound root, String path) {
        return NbtLookup.resolve(root, NbtPath.parse(path), LIMIT);
    }

    @Test
    @DisplayName("numbers read as stored, without the letter NBT text adds: a short 5 is 5, a float 20 is 20.0")
    void numbersAsStored() {
        NbtLookup.Result damage = read(sword(), "Damage");
        assertTrue(damage.isFound());
        assertEquals("short", damage.getType());
        assertEquals("5", damage.getValue());
        assertTrue(damage.isNumber());
        assertEquals("5", read(sword(), "tag.ench[0].lvl").getValue());
        assertEquals("byte", read(sword(), "Count").getType());

        NBTTagCompound zombie = new NBTTagCompound();
        zombie.setFloat("Health", 20.0F);
        zombie.setDouble("FallDistance", 1.5D);
        zombie.setLong("UUIDMost", 1234567890123456789L);
        zombie.setInteger("Air", 300);
        assertEquals("20.0", read(zombie, "Health").getValue());
        assertEquals("float", read(zombie, "Health").getType());
        assertEquals("1.5", read(zombie, "FallDistance").getValue());
        assertEquals("1234567890123456789", read(zombie, "UUIDMost").getValue());
        assertEquals("int", read(zombie, "Air").getType());
    }

    @Test
    @DisplayName("a string reads as its text, and a chest's first item id is reached by index")
    void stringsAndIndexes() {
        NBTTagCompound stone = new NBTTagCompound();
        stone.setByte("Slot", (byte) 0);
        stone.setString("id", "minecraft:stone");
        NBTTagList items = new NBTTagList();
        items.appendTag(stone);
        NBTTagCompound chest = new NBTTagCompound();
        chest.setTag("Items", items);
        NbtLookup.Result id = read(chest, "Items[0].id");
        assertEquals("string", id.getType());
        assertEquals("minecraft:stone", id.getValue(), "no quotes around the text");
        assertFalse(id.isNumber());
    }

    @Test
    @DisplayName("a capability's value is reached through ForgeCaps, with the id quoted or bare")
    void capabilityPath() {
        NBTTagCompound mana = new NBTTagCompound();
        mana.setFloat("currentMana", 42.5F);
        NBTTagCompound caps = new NBTTagCompound();
        caps.setTag("jawms:player_mana", mana);
        NBTTagCompound player = new NBTTagCompound();
        player.setTag("ForgeCaps", caps);
        assertEquals("42.5", read(player, "ForgeCaps.\"jawms:player_mana\".currentMana").getValue());
        assertEquals("42.5", read(player, "ForgeCaps.jawms:player_mana.currentMana").getValue());
    }

    @Test
    @DisplayName("a missing key, an index past the end, and a step into a value that holds nothing are missing, naming where")
    void missing() {
        NbtLookup.Result noKey = read(sword(), "No.Such.Path");
        assertFalse(noKey.isFound());
        assertEquals("No", noKey.getMissingAt());
        assertNull(noKey.getType());
        assertNull(noKey.getValue());

        NbtLookup.Result pastEnd = read(sword(), "tag.ench[1].lvl");
        assertFalse(pastEnd.isFound());
        assertEquals("tag.ench[1]", pastEnd.getMissingAt());

        assertEquals("Damage.x", read(sword(), "Damage.x").getMissingAt(), "a key on a number");
        assertEquals("id[0]", read(sword(), "id[0]").getMissingAt(), "an index on a string");
        assertEquals("tag[0]", read(sword(), "tag[0]").getMissingAt(), "an index on a compound");
    }

    @Test
    @DisplayName("a compound or a list reads as its NBT text, cut to the limit, and says so")
    void structuredValuesBounded() {
        NbtLookup.Result tag = read(sword(), "tag");
        assertEquals("compound", tag.getType());
        assertEquals("{ench:[{lvl:5s,id:16s}]}".length(), tag.getValue().length());
        assertTrue(tag.getValue().startsWith("{ench:["));
        assertFalse(tag.getBounded().isTruncated());

        NbtLookup.Result cut = NbtLookup.resolve(sword(), NbtPath.parse("tag.ench"), 5);
        assertEquals("list", cut.getType());
        assertTrue(cut.getBounded().isTruncated());
        assertTrue(cut.getBounded().getOriginalLength() > 5);
        assertNull(read(sword(), "Damage").getBounded(), "a number is never cut");
    }

    @Test
    @DisplayName("an index reads an int array element by element, and the type names follow the saved ids")
    void arraysAndTypeNames() {
        NBTTagCompound root = new NBTTagCompound();
        root.setIntArray("Ids", new int[] {7, 8, 9});
        root.setTag("Name", new NBTTagString("x"));
        assertEquals("9", read(root, "Ids[2]").getValue());
        assertEquals("int", read(root, "Ids[2]").getType());
        assertEquals("Ids[3]", read(root, "Ids[3]").getMissingAt());
        assertEquals("intArray", read(root, "Ids").getType());
        assertEquals("longArray", NbtLookup.typeName(12));
        assertEquals("byteArray", NbtLookup.typeName(7));
        assertEquals("double", NbtLookup.typeName(6));
    }
}
