package com.mahghuuuls.agenttesttoolkit.inspect;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * Reads the one value an {@link NbtPath} names from a compound, as text a record can carry.
 *
 * <p>A number is written as the game stores it, without the type letter NBT text adds: a short
 * damage of 5 is {@code 5}, a float health of 20 is {@code 20.0}. A string is its text, without
 * quotes. A compound, a list or an array is its NBT text, cut to the configured length as raw NBT
 * output is, and says so. An index reads a list, and also a byte or int array, element by element.
 */
public final class NbtLookup {

    /** What a path found, or where it stopped. */
    public static final class Result {
        private final boolean found;
        private final String type;
        private final String value;
        private final boolean number;
        private final Truncation bounded;
        private final String missingAt;

        private Result(boolean found, String type, String value, boolean number, Truncation bounded,
                       String missingAt) {
            this.found = found;
            this.type = type;
            this.value = value;
            this.number = number;
            this.bounded = bounded;
            this.missingAt = missingAt;
        }

        public boolean isFound() {
            return found;
        }

        /** The tag's type, such as {@code short} or {@code compound}; null when missing. */
        public String getType() {
            return type;
        }

        /** The value text; null when missing. */
        public String getValue() {
            return value;
        }

        /** True when the tag is one of the six number types. */
        public boolean isNumber() {
            return number;
        }

        /** The bound applied to a compound's, a list's or an array's text; null for a number or a string. */
        public Truncation getBounded() {
            return bounded;
        }

        /** The steps up to the first one that found nothing; null when found. */
        public String getMissingAt() {
            return missingAt;
        }
    }

    private NbtLookup() {
    }

    /**
     * Follows the path from {@code root}. A key on something that is not a compound, an index on
     * something that is not a list or array, and an index past the end are all missing, with
     * {@link Result#getMissingAt()} naming the step.
     *
     * @param limit the most characters a compound's, a list's or an array's text may have
     */
    public static Result resolve(NBTTagCompound root, NbtPath path, int limit) {
        NBTBase current = root;
        int step = 0;
        for (NbtPath.Segment segment : path.getSegments()) {
            step++;
            NBTBase next = segment.isIndex() ? element(current, segment.getIndex()) : child(current, segment.getKey());
            if (next == null) {
                return new Result(false, null, null, false, null, path.prefix(step));
            }
            current = next;
        }
        return describe(current, limit);
    }

    private static NBTBase child(NBTBase parent, String key) {
        if (!(parent instanceof NBTTagCompound)) {
            return null;
        }
        NBTTagCompound compound = (NBTTagCompound) parent;
        return compound.hasKey(key) ? compound.getTag(key) : null;
    }

    private static NBTBase element(NBTBase parent, int index) {
        if (parent instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) parent;
            return index < list.tagCount() ? list.get(index) : null;
        }
        if (parent instanceof NBTTagByteArray) {
            byte[] bytes = ((NBTTagByteArray) parent).getByteArray();
            return index < bytes.length ? new NBTTagByte(bytes[index]) : null;
        }
        if (parent instanceof NBTTagIntArray) {
            int[] ints = ((NBTTagIntArray) parent).getIntArray();
            return index < ints.length ? new NBTTagInt(ints[index]) : null;
        }
        return null;
    }

    private static Result describe(NBTBase tag, int limit) {
        String type = typeName(tag.getId());
        if (tag instanceof NBTPrimitive) {
            return new Result(true, type, numberText((NBTPrimitive) tag), true, null, null);
        }
        if (tag instanceof NBTTagString) {
            return new Result(true, type, ((NBTTagString) tag).getString(), false, null, null);
        }
        Truncation bounded = Truncation.of(tag.toString(), limit);
        return new Result(true, type, bounded.getText(), false, bounded, null);
    }

    /** The number as stored, in Java's own text for its type: no {@code s}, {@code f} or {@code d} suffix. */
    private static String numberText(NBTPrimitive tag) {
        switch (tag.getId()) {
            case 1:
                return Byte.toString(tag.getByte());
            case 2:
                return Short.toString(tag.getShort());
            case 3:
                return Integer.toString(tag.getInt());
            case 4:
                return Long.toString(tag.getLong());
            case 5:
                return Float.toString(tag.getFloat());
            default:
                return Double.toString(tag.getDouble());
        }
    }

    /** The record's name for an NBT tag type, by the id the game saves it with. */
    static String typeName(int id) {
        switch (id) {
            case 1:
                return "byte";
            case 2:
                return "short";
            case 3:
                return "int";
            case 4:
                return "long";
            case 5:
                return "float";
            case 6:
                return "double";
            case 7:
                return "byteArray";
            case 8:
                return "string";
            case 9:
                return "list";
            case 10:
                return "compound";
            case 11:
                return "intArray";
            case 12:
                return "longArray";
            default:
                return "unknown";
        }
    }
}
