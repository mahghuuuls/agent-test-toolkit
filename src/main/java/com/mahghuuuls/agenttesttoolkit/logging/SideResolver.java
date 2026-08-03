package com.mahghuuuls.agenttesttoolkit.logging;

import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

/**
 * Resolves the logical side for a record's {@code side} field.
 *
 * <p>REQ-041 requires every record to carry this field. In v1 the toolkit only observes the
 * logical server, so the value is effectively constant, but it is emitted anyway so the
 * record format does not change if client side observation is ever added. A field that is
 * constant today and correct tomorrow is cheaper than a format migration.
 *
 * <p>The value is derived from the world rather than from the process, because in single
 * player both logical sides live in one JVM.
 *
 * <p>Lives in {@code logging} rather than {@code command} on purpose. The event handlers
 * added by IMP-005 will need it, and the architecture's dependency rule 3 forbids
 * {@code observe} from depending on {@code command}. Every subsystem may depend on
 * {@code logging}, so this is the only placement that serves both callers without either
 * duplicating the logic or breaking the rule.
 */
public final class SideResolver {

    public static final String SERVER = "SERVER";
    public static final String CLIENT = "CLIENT";

    private SideResolver() {
    }

    public static String of(ICommandSender sender) {
        if (sender == null) {
            return SERVER;
        }
        return of(sender.getEntityWorld());
    }

    public static String of(World world) {
        return (world != null && world.isRemote) ? CLIENT : SERVER;
    }
}
