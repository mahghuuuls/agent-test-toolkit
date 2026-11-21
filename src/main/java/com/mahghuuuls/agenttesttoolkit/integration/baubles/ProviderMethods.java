package com.mahghuuuls.agenttesttoolkit.integration.baubles;

import com.mahghuuuls.agenttesttoolkit.integration.BaublesBridge;
import net.minecraft.command.CommandException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The Baubles handler methods that not every provider has, looked up once by reflection.
 *
 * <p>Official Baubles and its reimplementations share the {@code baubles.api} package but not
 * all of its methods: official Baubles 1.5.2 has {@code isEventBlocked()} and no per-slot type,
 * Bubbles has {@code getSlotType(int)} and no {@code isEventBlocked()}. A direct call to a
 * method the installed provider lacks fails with a {@code NoSuchMethodError} at the call, so
 * these methods are only ever reached through here, and a missing one is an answer rather than
 * an error. The per-slot type is never named as a type either, so the toolkit compiles and
 * loads without it.
 *
 * <p>A call that fails once found becomes {@code devtool.baubles.unsupported} naming the method;
 * nothing that happens inside a provider reaches the caller as anything else.
 */
final class ProviderMethods {

    static final String IS_EVENT_BLOCKED = "isEventBlocked";
    static final String GET_SLOT_TYPE = "getSlotType";
    static final String GET_REGISTRY_NAME = "getRegistryName";
    /** The namespace whose types are written by their path alone, such as {@code ring}. */
    static final String BAUBLES_NAMESPACE = "baubles";

    private final Method isEventBlocked;
    private final Method getSlotType;
    private final Method getRegistryName;

    private ProviderMethods(Method isEventBlocked, Method getSlotType, Method getRegistryName) {
        this.isEventBlocked = isEventBlocked;
        this.getSlotType = getSlotType;
        this.getRegistryName = getRegistryName;
    }

    /**
     * Looks the methods up on the provider's handler type: {@code isEventBlocked()} and
     * {@code getSlotType(int)}, and {@code getRegistryName()} on what the latter returns. Each is
     * null when the provider does not have it.
     */
    static ProviderMethods probe(Class<?> handlerType) {
        Method events = find(handlerType, IS_EVENT_BLOCKED);
        if (events != null && events.getReturnType() != boolean.class) {
            events = null;
        }
        Method slotType = find(handlerType, GET_SLOT_TYPE, int.class);
        Method registryName = slotType == null ? null : find(slotType.getReturnType(), GET_REGISTRY_NAME);
        return new ProviderMethods(events, slotType, registryName);
    }

    private static Method find(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException | LinkageError absent) {
            // Linkage too: resolving a signature loads the types it names, and a provider may
            // name one that is not there. The method is then as good as missing.
            return null;
        }
    }

    boolean hasEventFlag() {
        return isEventBlocked != null;
    }

    boolean hasSlotTypes() {
        return getSlotType != null && getRegistryName != null;
    }

    /** Whether the provider has its equip events blocked; never, for a provider without the flag. */
    boolean eventsBlocked(Object handler) throws CommandException {
        if (isEventBlocked == null) {
            return false;
        }
        return Boolean.TRUE.equals(invoke(isEventBlocked, handler));
    }

    /**
     * The slot's type as the provider names it, or null when it names none: the path for a
     * Baubles type ({@code ring}), the full name for another mod's ({@code somemod:wing}).
     */
    String slotTypeName(Object handler, int slot) throws CommandException {
        if (!hasSlotTypes()) {
            return null;
        }
        Object type = invoke(getSlotType, handler, Integer.valueOf(slot));
        if (type == null) {
            return null;
        }
        Object name = invoke(getRegistryName, type);
        return name == null ? null : typeName(name.toString());
    }

    /** {@code baubles:ring} is written {@code ring}; any other name is written in full. */
    static String typeName(String registryName) {
        int colon = registryName.indexOf(':');
        if (colon > 0 && BAUBLES_NAMESPACE.equals(registryName.substring(0, colon))) {
            return registryName.substring(colon + 1);
        }
        return registryName;
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws CommandException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw unsupported(method, e.getCause() == null ? e : e.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException | NullPointerException | LinkageError e) {
            throw unsupported(method, e);
        }
    }

    private static CommandException unsupported(Method method, Throwable cause) {
        return new CommandException(BaublesBridge.KEY_UNSUPPORTED,
                method.getName() + " (" + cause.getClass().getSimpleName() + ")");
    }
}
