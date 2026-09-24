package pl.mzebrows.pwt.net.lan;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * This machine's networks, as discovery needs them.
 *
 * <p><strong>Every interface, not the default one.</strong> A broadcast to 255.255.255.255 leaves Windows
 * by one adapter only, and it is never Hamachi's; a broadcast to each adapter's own broadcast address goes
 * out of that adapter, and Hamachi carries it to everyone on the virtual network. IPv4 only: IPv6 has no
 * broadcast, and nothing here needs it.
 */
public final class LanAddresses {

    private LanAddresses() {}

    /**
     * Where a beacon sends: each up interface's broadcast address, and this machine's own loopback, where
     * a copy of the game beside the host listens.
     */
    public static List<InetSocketAddress> beaconTargets(int port) {
        List<InetSocketAddress> targets = new ArrayList<>();
        targets.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        for (InterfaceAddress address : ipv4()) {
            InetAddress broadcast = address.getBroadcast();
            if (broadcast != null) {
                targets.add(new InetSocketAddress(broadcast, port));
            }
        }
        return targets;
    }

    /** The addresses other machines may reach this one at - LAN, Hamachi, whatever is up - for the host to read out. */
    public static List<String> reachable() {
        List<String> addresses = new ArrayList<>();
        for (InterfaceAddress address : ipv4()) {
            addresses.add(address.getAddress().getHostAddress());
        }
        return addresses;
    }

    private static List<InterfaceAddress> ipv4() {
        List<InterfaceAddress> found = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface each = interfaces.nextElement();
                if (!each.isUp() || each.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress address : each.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof Inet4Address) {
                        found.add(address);
                    }
                }
            }
        } catch (SocketException e) {
            // No interfaces to speak of: announce to this machine only.
        }
        return found;
    }
}
