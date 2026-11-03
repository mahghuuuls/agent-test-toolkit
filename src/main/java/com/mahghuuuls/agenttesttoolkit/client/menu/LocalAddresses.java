package com.mahghuuuls.agenttesttoolkit.client.menu;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

/**
 * Whether an address is on the owner's own computer or the local network (D22, REQ-219, ARC-028).
 *
 * <p>Pure, for tests (rule 18). {@code connect} resolves a host once and goes ahead only when
 * every resolved address passes, then connects to that number, so no later lookup can change the
 * target (FQ40). Local: loopback, private IPv4, link-local, IPv6 unique-local ({@code fc00::/7},
 * which Java has no test for). Never: the unspecified address, the shared range
 * {@code 100.64.0.0/10} that carrier networks and Tailscale use for other machines, Tailscale's
 * IPv6 range {@code fd7a:115c:a1e0::/48}, or anything public.
 */
public final class LocalAddresses {

    private LocalAddresses() {
    }

    /** True when the list is not empty and every address is local. */
    public static boolean allLocal(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (!isLocal(address)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isLocal(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            // Loopback, 10/8, 172.16/12, 192.168/16, link-local 169.254/16.
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        }
        if (address instanceof Inet6Address) {
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            // Tailscale's fd7a:115c:a1e0::/48 is unique-local in form but reaches other machines,
            // like its 100.64/10 on IPv4.
            if ((b[0] & 0xFF) == 0xFD && (b[1] & 0xFF) == 0x7A && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0x5C
                    && (b[4] & 0xFF) == 0xA1 && (b[5] & 0xFF) == 0xE0) {
                return false;
            }
            // Unique-local fc00::/7. The deprecated site-local fec0::/10 is not trusted.
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
