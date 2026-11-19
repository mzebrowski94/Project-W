package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.door.DoorClient;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.net.session.PeerSession;
import pl.mzebrows.pwt.net.session.RoomWire;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;

/**
 * A copy of the game reaching a room on the relay (G0103): what its door tells it reaches the player as the page reads
 * it. The relay here is a stand-in that answers the door's first message as the real one would - the real door is
 * tested in the {@code relay} module, which {@code core} does not see.
 */
class OnlineSessionRelayTest {

    private static final String VERSION = "2026.11.19-test";
    private static final long FRAME = 16_666_667L;

    private final ManualClock clock = new ManualClock();
    private final NetConfig config = NetConfig.defaults();
    private final LoopbackNetwork network = new LoopbackNetwork(config);
    private final Transport relayEnd = network.endpoint("relay");
    private final PlayerInputs idle = new PlayerInputs(Players.MAX);
    private Door.@Nullable Message answer;
    private Door.@Nullable Message heard;
    private @Nullable OnlineSession session;

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
        }
        relayEnd.close();
    }

    @Test
    void tellsAJoinerTheRelaysRefusalWholeAndEndsThere() {
        relayListens(Door.refusal("room ACDE has a password"));
        session = joinsWith("ACDE", "");

        frames(30);

        assertThat(session.relayRefusal()).isEqualTo("refused by the relay: room ACDE has a password");
        assertThat(session.ended()).isEqualTo("refused by the relay: room ACDE has a password");
        assertThat(session.relayAnswered()).isFalse();
        assertThat(heard).isEqualTo(new Door.Join("ACDE", "", BuildId.current(), VERSION));
    }

    @Test
    void sendsThePasswordItWasGivenToTheDoor() {
        relayListens(new Door.Accepted("ACDE"));
        session = joinsWith("ACDE", "KJ34");

        frames(30);

        assertThat(heard).isEqualTo(new Door.Join("ACDE", "KJ34", BuildId.current(), VERSION));
        assertThat(session.relayAnswered()).isTrue();
        assertThat(session.roomCode()).isEqualTo("ACDE");
        assertThat(session.roomPassword()).isEqualTo("KJ34");
        assertThat(session.invitation()).isEqualTo("room ACDE   password KJ34");
        assertThat(session.hosts()).isFalse();
    }

    @Test
    void takesTheRelaysCodeAsTheHostsRoomAndAnnouncesNothingUntilItHasOne() {
        relayListens(new Door.Opened("KJ3X"));
        session = hostsWith("4TYU");

        assertThat(session.roomCode()).isEmpty();
        assertThat(session.hosts()).isTrue();
        assertThat(session.canRestart()).isTrue();
        frames(30);

        assertThat(session.relayAnswered()).isTrue();
        assertThat(session.roomCode()).isEqualTo("KJ3X");
        assertThat(session.roomPassword()).isEqualTo("4TYU");
        assertThat(session.invitation()).isEqualTo("room KJ3X   password 4TYU");
        assertThat(heard).isInstanceOf(Door.Open.class);
        assertThat(((Door.Open) heard).password()).isEqualTo("4TYU");
        assertThat(session.relayRefusal()).isNull();
    }

    @Test
    void readsAHostWithNoPasswordAsJustItsCode() {
        relayListens(new Door.Opened("KJ3X"));
        session = hostsWith("");

        frames(30);

        assertThat(session.roomPassword()).isEmpty();
        assertThat(session.invitation()).isEqualTo("room KJ3X");
    }

    @Test
    void saysTheRelayCannotBeReachedWhenNothingAnswersAtItsAddress() {
        // Nothing listens at "relay": the link closes before any door answers.
        session = joinsWith("ACDE", "");

        frames(300);

        assertThat(session.relayUnreachable()).isTrue();
        assertThat(session.ended()).isEqualTo("THE RELAY CANNOT BE REACHED");
        assertThat(session.relayRefusal()).isNull();
    }

    @Test
    void leavesASessionOfThisNetworkWithNoRoomAtAll() {
        OnlineSession lan = new OnlineSession(
                peerTo("relay", false, "", "ACDE"), null, null, List.of(), NetLog.silent(64), clock, "x", null, false);
        session = lan;

        assertThat(lan.relayed()).isFalse();
        assertThat(lan.roomCode()).isEmpty();
        assertThat(lan.roomPassword()).isEmpty();
        assertThat(lan.invitation()).isNull();
        assertThat(lan.relayAnswered()).isFalse();
        assertThat(lan.relayUnreachable()).isFalse();
    }

    private OnlineSession joinsWith(String room, String password) {
        return new OnlineSession(
                peerTo("relay", false, password, room),
                null,
                null,
                List.of(),
                NetLog.silent(64),
                clock,
                "room " + room,
                null,
                false,
                true,
                password);
    }

    private OnlineSession hostsWith(String password) {
        Invitation invitation = Invitation.roomed(
                new LanBeacon(config.lan(), clock, NetLog.silent(64), List.of()),
                5L,
                config.relay().port(),
                "pc");
        return new OnlineSession(
                peerTo("relay", true, password, ""),
                null,
                null,
                List.of(),
                NetLog.silent(64),
                clock,
                "the relay",
                invitation,
                true,
                true,
                password);
    }

    private PeerSession peerTo(String host, boolean opens, String password, String room) {
        PeerSession peer = new PeerSession(
                network.endpoint("peer"),
                config,
                NetLog.silent(64),
                new ScenarioWorlds(),
                null,
                BuildId.current(),
                "test");
        DoorClient door = opens
                ? DoorClient.open(BuildId.current(), VERSION, 9L, RoomWire.sessionConfig(config.session()), password)
                : DoorClient.join(room, password, BuildId.current(), VERSION);
        peer.connect(host, config.relay().port(), opens ? 9L : 0L, door);
        return peer;
    }

    /** A relay that listens where a client looks for it and answers whatever the first message is with {@code reply}. */
    private void relayListens(Door.Message reply) {
        answer = reply;
        relayEnd.listen(config.relay().port(), 0);
    }

    private void frames(int count) {
        TransportListener door = new TransportListener() {
            @Override
            public void opened(int link, String remote) {}

            @Override
            public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
                if (heard != null) {
                    return; // the session's own messages after the door: not this stand-in's to answer
                }
                heard = Door.read(data, offset, length);
                byte[] reply = Door.encode(answer);
                relayEnd.send(link, Delivery.RELIABLE, reply, 0, reply.length);
                if (answer instanceof Door.Refused refused) {
                    relayEnd.disconnect(link, refused.text());
                }
            }

            @Override
            public void closed(int link, String reason) {}
        };
        for (int i = 0; i < count; i++) {
            clock.advanceNanos(FRAME);
            relayEnd.poll(door);
            session.advance(1f / 60f, idle);
        }
    }
}
