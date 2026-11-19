package pl.mzebrows.pwt.net.session;

import java.nio.charset.StandardCharsets;

/**
 * What a world is built from, as the host ships it to everyone who joins (ADR 0012, "the host ships its
 * config"): the text of its {@code assets/sim/*.properties}, which scenario, which seed. Raw text rather than a
 * parsed config, so that peers parse it with their own loader - which the build id already says is the
 * same - and tuning a playtest needs no file copied to anyone.
 *
 * <p>It travels as {@link #toBytes() opaque bytes}: the sequencer, and the relay after it, keep and forward them
 * without reading them (ADR 0023, G0101). The shape is the same on every machine and nothing else:
 * the scenario as a length-prefixed UTF-8 string, then the seed as eight big-endian bytes, then the config
 * text as length-prefixed UTF-8.
 */
public record SessionSetup(String configText, String scenario, long seed) {

    /** The longest the scenario's name may be, in bytes. */
    static final int MAX_SCENARIO_BYTES = Msg.MAX_TEXT;

    /** The most bytes a setup may take: a name, a seed and a config. */
    static final int MAX_BYTES = Msg.MAX_START;

    /** This setup as the bytes a START carries. */
    public byte[] toBytes() {
        byte[] config = configText.getBytes(StandardCharsets.UTF_8);
        WireWriter out = new WireWriter(config.length + scenario.length() * 4 + 32);
        out.putString(scenario).putLong(seed).putBytes(config);
        byte[] bytes = new byte[out.size()];
        System.arraycopy(out.array(), 0, bytes, 0, bytes.length);
        return bytes;
    }

    /** What {@link #toBytes()} wrote; {@link WireException} if these bytes are not one. */
    public static SessionSetup fromBytes(byte[] bytes) {
        WireReader in = new WireReader();
        in.wrap(bytes, 0, bytes.length);
        String scenario = in.getString(MAX_SCENARIO_BYTES);
        long seed = in.getLong();
        byte[] config = in.getBytes(Msg.MAX_CONFIG);
        in.expectEnd();
        return new SessionSetup(new String(config, StandardCharsets.UTF_8), scenario, seed);
    }
}
