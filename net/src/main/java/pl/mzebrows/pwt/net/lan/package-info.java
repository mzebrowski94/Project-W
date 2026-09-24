/**
 * Finding a session on the local network (G0038): a host's {@link pl.mzebrows.pwt.net.lan.LanBeacon}
 * announces it, a joiner's {@link pl.mzebrows.pwt.net.lan.LanListener} hears it, and a {@link
 * pl.mzebrows.pwt.net.lan.RoomCode} names it so a player can say which one to join. Plain JDK datagram
 * sockets, polled from the game's thread like the transport - there is nothing here Netty would help with.
 */
@NullMarked
package pl.mzebrows.pwt.net.lan;

import org.jspecify.annotations.NullMarked;
