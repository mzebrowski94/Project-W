package pl.mzebrows.pwt.relay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.RoomWire;
import pl.mzebrows.pwt.net.session.Sequencer;
import pl.mzebrows.pwt.net.session.SequencerThread;
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
 * link. After the door a link is a link to a sequencer (ADR 0023): the room's, built over a {@link RoomTransport}
 * that is the room's view of this relay's one listener, so the sequencer knows nothing of rooms. The host's link
 * is the one that opened the room; when it goes the sequencer holds his seats for the rejoin window (G0105) and
 * then hands the session on (G0104), and the room is over only when its sequencer ends. The relay reads no message in a room but to count it (and to see that a snapshot is not, and that a
 * link let in to come back says so first).
 *
 * <p>And what traffic needs: per link, a byte meter (inbound, snapshots not counted); per room, the idle close
 * (nothing but acknowledgements for {@code idleRoomMinutes}) and the late-seal warning. Their keys are read here
 * and nowhere else.
 *
 * <p>And the door's limits (stage 9, G0109): per address, how many links and rooms, how many refusals before it
 * is ignored, how long a new link may stay silent; per room, how many wrong passwords before it turns new machines
 * away. Every number is read in this class and nowhere else. A room's lock is a rule for a <em>new</em> machine:
 * a held-token return skips it.
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
    private final NetConfig net;
    private final NetConfig.Relay config;
    private final Sequencing sequencing;
    private final RandomGenerator random;
    private final Map<Integer, ByteBucket> buckets = new HashMap<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, Room> roomOfLink = new HashMap<>();
    private final TransportListener listener = new Listener();
    private final Map<String, Address> addresses = new HashMap<>();
    private final Map<Integer, String> addressOfLink = new HashMap<>();
    /**
     * Links that have not said anything yet, oldest first, with when they opened - and links the door has just let
     * into a room, with when it did, until they say their first word to the room's sequencer.
     */
    private final Map<Integer, Long> silentLinks = new LinkedHashMap<>();

    /** Links let in by a {@link Door.Rejoin} that have not yet said anything to the room: the first must be a return. */
    private final Set<Integer> comingBack = new HashSet<>();

    private final long helloTimeoutNanos;
    private final long refusalWindowNanos;
    private final long silenceNanos;
    private final long roomLockNanos;
    private final long idleRoomNanos;
    private final long sealLateWarnNanos;
    private long sweptAtNanos;

    /** Who pumps a room's sequencer. */
    public enum Sequencing {
        /** A {@code SequencerThread} per room: the process. */
        THREADED,
        /** {@link #poll()} pumps every room's sequencer, with the relay's clock: a test under a manual clock. */
        PUMPED
    }

    /** A relay whose sequencers {@link #poll()} pumps. */
    public Relay(Transport transport, Clock clock, NetLog log, NetConfig net, RandomGenerator random) {
        this(transport, clock, log, net, random, Sequencing.PUMPED);
    }

    /**
     * @param net the relay's own config: its limits, and the pacing every room's sequencer is built with (the
     *     host sends none, ADR 0023)
     * @param random where codes come from: a secure one in the process, a seeded one in a test
     */
    public Relay(
            Transport transport,
            Clock clock,
            NetLog log,
            NetConfig net,
            RandomGenerator random,
            Sequencing sequencing) {
        this.transport = transport;
        this.clock = clock;
        this.log = log;
        this.net = net;
        this.config = net.relay();
        this.sequencing = sequencing;
        this.random = random;
        this.idleRoomNanos = config.idleRoomMinutes() * 60L * 1_000_000_000L;
        this.sealLateWarnNanos = config.sealLateWarnMs() * 1_000_000L;
        this.helloTimeoutNanos = seconds(config.helloTimeoutSeconds());
        this.refusalWindowNanos = seconds(config.refusalWindowSeconds());
        this.silenceNanos = seconds(config.silenceSeconds());
        this.roomLockNanos = seconds(config.roomLockSeconds());
    }

    private static long seconds(int seconds) {
        return seconds * 1_000_000_000L;
    }

    /** Handles everything the transport has to report. */
    public void poll() {
        transport.poll(listener);
        long now = clock.nanos();
        tendRooms(now);
        dropSilentLinks(now);
        forgetAddresses(now);
    }

    /**
     * Each room: pumps its sequencer when this relay does, puts on the wire what the sequencer sent, closes a room
     * whose sequencer ended or that has gone idle, and says so when its seals run late.
     */
    private void tendRooms(long now) {
        Iterator<Room> it = rooms.values().iterator();
        while (it.hasNext()) {
            Room room = it.next();
            if (sequencing == Sequencing.PUMPED && !room.ended()) {
                room.sequencer().pump(now);
            }
            if (room.view().drainTo(transport)) {
                it.remove();
                finish(room, null); // the sequencer ended on its own: its thread is still to be stopped
                continue;
            }
            Sequencer sequencer = room.sequencer();
            if (room.hostNeverCame(now, sequencer.rejoinWindowNanos(), sequencer.hostWelcomed())) {
                it.remove();
                log.info("relay", "event=room-empty code=" + room.code());
                finish(room, "the host left the game");
                continue;
            }
            if (!room.sealLateWarned() && sequencer.sealLateMostNanos() > sealLateWarnNanos) {
                room.warnedSealLate();
                log.warn(
                        "relay",
                        "event=seal-late code=" + room.code() + " sealLateMostMs=" + sealLateMostMs(room) + " warnMs="
                                + config.sealLateWarnMs());
            }
            if (now - Math.max(room.openedAtNanos(), sequencer.lastActiveNanos()) >= idleRoomNanos) {
                it.remove();
                log.info("relay", "event=room-idle code=" + room.code() + " minutes=" + config.idleRoomMinutes());
                finish(room, "the room was idle");
            }
        }
    }

    private static long sealLateMostMs(Room room) {
        return room.sequencer().sealLateMostNanos() / 1_000_000L;
    }

    /**
     * A room is over: its sequencer stopped, its links told why ({@code reason}, or the sequencer's own "the host left
     * the game" when null), and what the relay holds for it let go. The caller has taken it out of the relay's rooms.
     * The reason goes as the session's end, not only as the link's close, so a machine does not take an idle or a
     * stopped room for a dropped link and try to come back to it for the whole window.
     */
    private void finish(Room room, @Nullable String reason) {
        if (!room.ended()) {
            room.end();
            SequencerThread thread = room.thread();
            if (thread != null) {
                thread.close(reason == null ? "" : reason);
            } else if (reason == null) {
                room.sequencer().close();
            } else {
                room.sequencer().end(reason);
            }
        }
        room.view().drainTo(transport);
        if (reason != null) {
            for (int link : room.links()) {
                transport.disconnect(link, reason); // any the sequencer did not tell: its thread did not stop in time
            }
        }
        for (int link : room.links()) {
            roomOfLink.remove(link);
            buckets.remove(link);
        }
        Address owner = addresses.get(room.openedBy());
        if (owner != null) {
            owner.rooms--;
        }
        log.info(
                "relay",
                "event=room-closed code=" + room.code() + " sealLateMostMs=" + sealLateMostMs(room) + " reason=\""
                        + (reason == null ? "the host left the game" : reason) + "\"");
    }

    /** Ends every room, telling its links the relay is stopping, and lets go of the transport. */
    public void close() {
        Iterator<Room> it = rooms.values().iterator();
        while (it.hasNext()) {
            Room room = it.next();
            it.remove();
            finish(room, "the relay stopped");
        }
        transport.close();
    }

    /** The sequencer of the room with this code, or null - for a test to read what a room's session is doing. */
    public @Nullable Sequencer sequencerOf(String code) {
        Room room = rooms.get(code);
        return room == null ? null : room.sequencer();
    }

    /** A link that has not said a word by the hello timeout is dropped; the oldest are first, so it stops at the first live one. */
    private void dropSilentLinks(long now) {
        Iterator<Map.Entry<Integer, Long>> it = silentLinks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() < helloTimeoutNanos) {
                return;
            }
            it.remove();
            log.info("relay", "event=hello-timeout link=" + entry.getKey());
            transport.disconnect(entry.getKey(), "no hello");
        }
    }

    /** Once a second at most, lets go of the addresses with nothing left to remember. */
    private void forgetAddresses(long now) {
        if (now - sweptAtNanos < 1_000_000_000L) {
            return;
        }
        sweptAtNanos = now;
        addresses.values().removeIf(address -> address.forgettable(now, refusalWindowNanos));
    }

    /** The address in a transport's remote text: after any slash, without a trailing port. */
    static String addressOf(String remote) {
        String text = remote.substring(remote.indexOf('/') + 1);
        int colon = text.lastIndexOf(':');
        if (colon > 0
                && colon < text.length() - 1
                && text.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            return text.substring(0, colon);
        }
        return text;
    }

    private void opened(int link, String remote) {
        String text = addressOf(remote);
        Address address = addresses.computeIfAbsent(text, key -> new Address());
        long now = clock.nanos();
        if (address.silent(now)) {
            log.debug("relay", "event=ignored link=" + link + " address=" + text + " why=silence");
            transport.disconnect(link, "ignored");
            return;
        }
        if (address.links >= config.linksPerAddressMax()) {
            log.info("relay", "event=links-over link=" + link + " address=" + text);
            transport.disconnect(link, "too many links");
            return;
        }
        address.links++;
        addressOfLink.put(link, text);
        silentLinks.put(link, now);
        log.debug("relay", "event=link link=" + link + " address=" + text);
    }

    public int roomCount() {
        return rooms.size();
    }

    /** How many links the room of this code holds, or zero for no such room. */
    public int linksIn(String code) {
        Room room = rooms.get(code);
        return room == null ? 0 : room.linkCount();
    }

    private void received(int link, Delivery delivery, byte[] data, int offset, int length) {
        if (!addressOfLink.containsKey(link)) {
            return; // closed unanswered, or never opened: nothing it says is read
        }
        silentLinks.remove(link);
        Room inRoom = roomOfLink.get(link);
        if (inRoom != null) {
            if (comingBack.remove(link) && !RoomWire.isRejoin(data, offset, length)) {
                log.warn("relay", "event=not-coming-back link=" + link + " code=" + inRoom.code());
                transport.disconnect(link, "a link let in to come back did not come back");
                countRefusal(link);
                return;
            }
            ByteBucket bucket = buckets.get(link);
            if (bucket != null && !RoomWire.isSnapshot(data, offset, length) && !bucket.take(length, clock.nanos())) {
                log.warn("relay", "event=over-rate link=" + link + " code=" + inRoom.code());
                transport.disconnect(link, "sending more than the relay takes");
                return;
            }
            inRoom.view().received(link, delivery, data, offset, length);
            return;
        }
        Door.Message message;
        try {
            message = Door.read(data, offset, length);
        } catch (IllegalArgumentException e) {
            log.warn("relay", "event=bad-door link=" + link + " why=\"" + e.getMessage() + "\"");
            transport.disconnect(link, "not a door message");
            countRefusal(link);
            return;
        }
        switch (message) {
            case Door.Open open -> open(link, open);
            case Door.Join join -> join(link, join);
            case Door.Rejoin rejoin -> rejoin(link, rejoin);
            default -> {
                log.warn("relay", "event=bad-door link=" + link + " why=\"a reply sent to the relay\"");
                transport.disconnect(link, "not a door message");
                countRefusal(link);
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
        NetConfig.Session session;
        try {
            session = RoomWire.sessionConfig(open.sessionConfig());
        } catch (IllegalArgumentException e) {
            refuse(link, "this relay cannot read that session config", "bad-config");
            return;
        }
        Address address = addresses.get(addressOfLink.get(link));
        if (address != null && address.rooms >= config.roomsPerAddressMax()) {
            refuse(link, "this address has all the rooms it may open", "rooms-per-address");
            return;
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
                clock.nanos(),
                addressOfLink.getOrDefault(link, ""));
        if (address != null) {
            address.rooms++;
        }
        // The pacing is this relay's own; the session config is the host's (ADR 0018, ADR 0023).
        Sequencer sequencer =
                new Sequencer(room.view(), net.with(session), log, MACHINES_MAX, open.hostKey(), open.buildId());
        // The sequencer is here, not on the host's machine: it outlives him, and hands the session on (G0104).
        sequencer.handsOver(true);
        room.sequence(
                sequencer,
                sequencing == Sequencing.THREADED ? new SequencerThread(sequencer, clock, log).start() : null,
                link);
        rooms.put(code.text(), room);
        log.info(
                "relay",
                "event=room-opened code=" + code + " link=" + link + " address=" + addressOfLink.getOrDefault(link, "?")
                        + " password=" + (password == null ? "no" : "yes")
                        + " build=" + room.buildId() + " version=" + room.buildVersion());
        send(link, new Door.Opened(code.text()));
        admit(room, link);
    }

    /**
     * A link the door has let into a room is, from now on, a link to the room's sequencer - and has the hello timeout
     * again to say its hello there, or it would hold a place in the room without ever being a machine in it.
     */
    private void admit(Room room, int link) {
        silentLinks.put(link, clock.nanos());
        room.add(link);
        roomOfLink.put(link, room);
        buckets.put(link, new ByteBucket(config.bytesPerSecondPerLink(), clock.nanos()));
        room.view().opened(link, addressOfLink.getOrDefault(link, ""));
    }

    private void join(int link, Door.Join join) {
        RoomCode parsed = RoomCode.parse(join.code());
        Room room = parsed == null ? null : rooms.get(parsed.text());
        if (room == null) {
            refuse(link, "no room " + (parsed == null ? join.code() : parsed.text()), "no-room");
            return;
        }
        String code = room.code().text();
        // A room's lock turns away a new machine, whatever it knows. A machine coming back by its token does not come
        // here.
        if (room.locked(clock.nanos())) {
            refuse(link, "room " + code + " is not taking machines for a moment", "room-locked");
            return;
        }
        if (room.hasPassword()) {
            if (join.password().isEmpty()) {
                refuse(link, "room " + code + " has a password", "password-missing");
                return;
            }
            if (!room.passwordIs(RoomCode.letters(join.password(), RoomCode.LENGTH))) {
                if (room.heardWrongPassword(clock.nanos(), config.wrongPasswordsPerRoomMax(), roomLockNanos)) {
                    log.warn("relay", "event=room-locked code=" + code + " seconds=" + config.roomLockSeconds());
                }
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
        log.info(
                "relay",
                "event=joined code=" + code + " link=" + link + " address=" + addressOfLink.getOrDefault(link, "?")
                        + " links=" + (room.linkCount() + 1));
        send(link, new Door.Accepted(code));
        admit(room, link);
    }

    /**
     * A machine whose link dropped asks back into its room (G0105). The relay cannot know the token - the sequencer
     * holds it - so the room's code is all the door asks: no password, no lock, no capacity (the machine may still be
     * linked on a link the relay has not noticed is dead). What stands in the way of a stranger is the sequencer,
     * which turns away a link whose token it does not hold and tells it to join again.
     */
    private void rejoin(int link, Door.Rejoin rejoin) {
        RoomCode parsed = RoomCode.parse(rejoin.code());
        Room room = parsed == null ? null : rooms.get(parsed.text());
        if (room == null) {
            refuse(link, Door.GIVEN_UP, "no-room");
            return;
        }
        log.info(
                "relay",
                "event=rejoined code=" + room.code() + " link=" + link + " address="
                        + addressOfLink.getOrDefault(link, "?") + " links=" + (room.linkCount() + 1));
        send(link, new Door.Accepted(room.code().text()));
        comingBack.add(link);
        admit(room, link);
    }

    private void refuse(int link, String reason, String tag) {
        log.info("relay", "event=refused link=" + link + " reason=" + tag);
        Door.Refused refused = Door.refusal(reason);
        send(link, refused);
        transport.disconnect(link, refused.text());
        countRefusal(link);
    }

    /** Counts a refusal against the link's address; the one that reaches the cap starts the address's silence. */
    private void countRefusal(int link) {
        String text = addressOfLink.get(link);
        Address address = text == null ? null : addresses.get(text);
        if (address != null
                && address.refused(clock.nanos(), config.refusalsPerAddressMax(), refusalWindowNanos, silenceNanos)) {
            log.warn("relay", "event=address-silenced address=" + text + " seconds=" + config.silenceSeconds());
        }
    }

    private void send(int link, Door.Message message) {
        byte[] bytes = Door.encode(message);
        transport.send(link, Delivery.RELIABLE, bytes, 0, bytes.length);
    }

    private void closed(int link, String reason) {
        String text = addressOfLink.remove(link);
        if (text != null) {
            silentLinks.remove(link);
            comingBack.remove(link);
            Address address = addresses.get(text);
            if (address != null) {
                address.links--;
            }
        }
        buckets.remove(link);
        Room room = roomOfLink.remove(link);
        if (room == null) {
            return;
        }
        room.remove(link);
        log.info(
                "relay",
                "event=link-left code=" + room.code() + " link=" + link + " address=" + (text == null ? "?" : text)
                        + " links=" + room.linkCount() + " reason=\"" + reason + "\"");
        // The sequencer keeps the seats of a machine whose link dropped for the rejoin window (G0105), the host's
        // among them: the room ends when the sequencer ends (the host's window passed), not with the link.
        if (link == room.hostLink()) {
            room.hostLinkClosed(clock.nanos());
        }
        room.view().closed(link, reason);
    }

    private final class Listener implements TransportListener {

        @Override
        public void opened(int link, String remote) {
            Relay.this.opened(link, remote);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            Relay.this.received(link, delivery, data, offset, length);
        }

        @Override
        public void closed(int link, String reason) {
            Relay.this.closed(link, reason);
        }
    }
}
