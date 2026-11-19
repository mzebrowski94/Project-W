package pl.mzebrows.pwt.relay;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/**
 * The relay (G0103, ADR 0023): the machine online sessions meet through. An object before it is a process -
 * it is handed a {@link Transport} already listening, a clock, a log and its config, and does its work in
 * {@link #poll()}, so a test drives it over a loopback network with a manual clock.
 *
 * <p>This is the door. The first message of a link is an {@link Door.Open} (a host) or a {@link Door.Join}
 * (anybody else); the relay answers with the room's code, an acceptance, or a refusal after which it closes the
 * link. What a link in a room says is not read yet - the room's sequencer is stage 5's.
 */
public final class Relay {

    /**
     * Machines a room holds at most: {@code Players.MAX}, which the relay may not read (ADR 0023's fence);
     * {@code RelayDoorTest} holds the two together.
     */
    static final int MACHINES_MAX = 8;

    private final Transport transport;
    private final Clock clock;
    private final NetLog log;
    private final NetConfig.Relay config;
    private final RandomGenerator random;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, Room> roomOfLink = new HashMap<>();
    private final TransportListener listener = new Listener();

    /**
     * @param random where codes come from: a secure one in the process, a seeded one in a test
     */
    public Relay(Transport transport, Clock clock, NetLog log, NetConfig.Relay config, RandomGenerator random) {
        this.transport = transport;
        this.clock = clock;
        this.log = log;
        this.config = config;
        this.random = random;
    }

    /** Handles everything the transport has to report. */
    public void poll() {
        transport.poll(listener);
    }

    public int roomCount() {
        return rooms.size();
    }

    /** How many links the room of this code holds, or zero for no such room. */
    public int linksIn(String code) {
        Room room = rooms.get(code);
        return room == null ? 0 : room.linkCount();
    }

    private void received(int link, byte[] data, int offset, int length) {
        if (roomOfLink.containsKey(link)) {
            log.debug("relay", "event=ignored link=" + link + " why=\"no sequencer yet\"");
            return;
        }
        Door.Message message;
        try {
            message = Door.read(data, offset, length);
        } catch (IllegalArgumentException e) {
            log.warn("relay", "event=bad-door link=" + link + " why=\"" + e.getMessage() + "\"");
            transport.disconnect(link, "not a door message");
            return;
        }
        switch (message) {
            case Door.Open open -> open(link, open);
            case Door.Join join -> join(link, join);
            default -> {
                log.warn("relay", "event=bad-door link=" + link + " why=\"a reply sent to the relay\"");
                transport.disconnect(link, "not a door message");
            }
        }
    }

    private void open(int link, Door.Open open) {
        String password = null;
        if (!open.password().isEmpty()) {
            password = RoomCode.letters(open.password(), RoomCode.LENGTH);
            if (password == null) {
                refuse(link, "a password is four characters of the room code's alphabet", "bad-password");
                return;
            }
        }
        if (rooms.size() >= config.roomsMax()) {
            refuse(link, "no room is free", "rooms-full");
            return;
        }
        RoomCode code = RoomCode.random(random);
        while (rooms.containsKey(code.text())) {
            code = RoomCode.random(random);
        }
        Room room = new Room(
                code,
                password,
                open.buildId(),
                open.buildVersion(),
                open.hostKey(),
                open.sessionConfig(),
                clock.nanos());
        room.add(link);
        rooms.put(code.text(), room);
        roomOfLink.put(link, room);
        log.info(
                "relay",
                "event=room-opened code=" + code + " link=" + link + " password=" + (password == null ? "no" : "yes")
                        + " build=" + room.buildId() + " version=" + room.buildVersion());
        send(link, new Door.Opened(code.text()));
    }

    private void join(int link, Door.Join join) {
        RoomCode parsed = RoomCode.parse(join.code());
        Room room = parsed == null ? null : rooms.get(parsed.text());
        if (room == null) {
            refuse(link, "no room " + (parsed == null ? join.code() : parsed.text()), "no-room");
            return;
        }
        String code = room.code().text();
        if (room.hasPassword()) {
            if (join.password().isEmpty()) {
                refuse(link, "room " + code + " has a password", "password-missing");
                return;
            }
            if (!room.passwordIs(RoomCode.letters(join.password(), RoomCode.LENGTH))) {
                refuse(link, "wrong password for room " + code, "password-wrong");
                return;
            }
        }
        if (!room.buildId().equals(join.buildId())) {
            refuse(
                    link,
                    "room " + code + " runs " + room.buildVersion() + ", this copy is " + join.buildVersion(),
                    "other-build");
            return;
        }
        if (room.linkCount() >= MACHINES_MAX) {
            refuse(link, "room " + code + " is full", "full");
            return;
        }
        room.add(link);
        roomOfLink.put(link, room);
        log.info("relay", "event=joined code=" + code + " link=" + link + " links=" + room.linkCount());
        send(link, new Door.Accepted(code));
    }

    private void refuse(int link, String reason, String tag) {
        log.info("relay", "event=refused link=" + link + " reason=" + tag);
        Door.Refused refused = Door.refusal(reason);
        send(link, refused);
        transport.disconnect(link, refused.text());
    }

    private void send(int link, Door.Message message) {
        byte[] bytes = Door.encode(message);
        transport.send(link, Delivery.RELIABLE, bytes, 0, bytes.length);
    }

    private void closed(int link, String reason) {
        Room room = roomOfLink.remove(link);
        if (room == null) {
            return;
        }
        // Provisional, for review (AFK run): until stage 7's rejoin window, a room with no link left closes at
        // once, so a relay with no sequencer yet does not fill with rooms nobody is in.
        boolean empty = room.remove(link);
        log.info("relay", "event=link-left code=" + room.code() + " link=" + link + " reason=\"" + reason + "\"");
        if (empty) {
            rooms.remove(room.code().text());
            log.info("relay", "event=room-closed code=" + room.code());
        }
    }

    private final class Listener implements TransportListener {

        @Override
        public void opened(int link, String remote) {
            log.debug("relay", "event=link link=" + link + " remote=" + remote);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            Relay.this.received(link, data, offset, length);
        }

        @Override
        public void closed(int link, String reason) {
            Relay.this.closed(link, reason);
        }
    }
}
