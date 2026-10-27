package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads a living entity's attributes and their modifiers into plain observations (ARC-020).
 *
 * <p>The observations carry no Minecraft types, so the records they become and the assertion
 * rules over them are tested without a world. Attributes are sorted by name and modifiers by
 * UUID, so two inspections of the same state write the same lines in the same order.
 */
public final class Attributes {

    /** Vanilla 1.12.2 operation numbers, by index. */
    private static final String[] OPERATION_NAMES = {"add", "multiplyBase", "multiplyTotal"};

    private Attributes() {
    }

    /** One modifier, as observed. */
    public static final class Modifier {
        private final String uuid;
        private final String name;
        private final int operation;
        private final double amount;

        public Modifier(String uuid, String name, int operation, double amount) {
            this.uuid = uuid;
            this.name = name;
            this.operation = operation;
            this.amount = amount;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getOperation() {
            return operation;
        }

        public double getAmount() {
            return amount;
        }
    }

    /** One attribute with its modifiers, as observed. */
    public static final class Attribute {
        private final String name;
        private final double base;
        private final double value;
        private final List<Modifier> modifiers;

        public Attribute(String name, double base, double value, List<Modifier> modifiers) {
            this.name = name;
            this.base = base;
            this.value = value;
            this.modifiers = Collections.unmodifiableList(new ArrayList<Modifier>(modifiers));
        }

        public String getName() {
            return name;
        }

        public double getBase() {
            return base;
        }

        public double getValue() {
            return value;
        }

        public List<Modifier> getModifiers() {
            return modifiers;
        }
    }

    /** Every attribute of the entity, sorted by name. */
    public static List<Attribute> read(EntityLivingBase entity) {
        List<Attribute> attributes = new ArrayList<Attribute>();
        for (IAttributeInstance instance : entity.getAttributeMap().getAllAttributes()) {
            attributes.add(observe(instance));
        }
        Collections.sort(attributes, new Comparator<Attribute>() {
            @Override
            public int compare(Attribute a, Attribute b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return attributes;
    }

    /** One attribute of the entity by its registry name, or null when it has none by that name. */
    public static Attribute read(EntityLivingBase entity, String name) {
        IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(name);
        return instance == null ? null : observe(instance);
    }

    private static Attribute observe(IAttributeInstance instance) {
        List<Modifier> modifiers = new ArrayList<Modifier>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            modifiers.add(new Modifier(modifier.getID().toString(), modifier.getName(),
                    modifier.getOperation(), modifier.getAmount()));
        }
        Collections.sort(modifiers, new Comparator<Modifier>() {
            @Override
            public int compare(Modifier a, Modifier b) {
                return a.getUuid().compareTo(b.getUuid());
            }
        });
        return new Attribute(instance.getAttribute().getName(), instance.getBaseValue(),
                instance.getAttributeValue(), modifiers);
    }

    /**
     * One {@code ATTRIBUTE} record per attribute, each followed by one {@code ATTRIBUTE_MODIFIER}
     * per modifier.
     *
     * @param target the entity's registry id, for attribution
     * @param uuid   the entity's UUID
     */
    public static List<LogRecord> records(String target, String uuid, List<Attribute> attributes) {
        List<LogRecord> records = new ArrayList<LogRecord>();
        for (Attribute attribute : attributes) {
            records.add(LogRecord.of(EventType.ATTRIBUTE)
                    .add("target", target)
                    .add("targetUuid", uuid)
                    .add("name", attribute.getName())
                    .addDecimal("base", attribute.getBase())
                    .addDecimal("value", attribute.getValue())
                    .add("modifiers", attribute.getModifiers().size()));
            for (Modifier modifier : attribute.getModifiers()) {
                records.add(LogRecord.of(EventType.ATTRIBUTE_MODIFIER)
                        .add("target", target)
                        .add("targetUuid", uuid)
                        .add("attribute", attribute.getName())
                        .add("uuid", modifier.getUuid())
                        .add("name", modifier.getName())
                        .add("operation", modifier.getOperation())
                        .add("operationName", operationName(modifier.getOperation()))
                        .addDecimal("amount", modifier.getAmount()));
            }
        }
        return records;
    }

    /** {@code add}, {@code multiplyBase} or {@code multiplyTotal}; the number as text otherwise. */
    public static String operationName(int operation) {
        return operation >= 0 && operation < OPERATION_NAMES.length
                ? OPERATION_NAMES[operation] : Integer.toString(operation);
    }

    /** True when the text has the shape of a UUID, so a modifier reference names one by id. */
    public static boolean looksLikeUuid(String text) {
        return text != null && text.toLowerCase(Locale.ROOT)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
