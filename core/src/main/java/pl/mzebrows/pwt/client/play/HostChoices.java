package pl.mzebrows.pwt.client.play;

import org.jspecify.annotations.Nullable;

/**
 * What HOST GAME decides (G0101, G0103): where the session is open - a room on the relay, or this network only - and,
 * for the internet, its password. Kept out of the screen so it can be tested without a window.
 *
 * <p>The internet can be chosen only while a relay is set ({@code net.relay.host}); hosting opens on this network
 * until it is chosen. The password belongs to a room, so its line is live only while the internet is the choice.
 */
public final class HostChoices {

    /** Where a hosted session is reached. */
    public enum Where {
        /** A room on the relay: the relay hands out the code, the password lets a machine in. */
        INTERNET,
        /** The host's own network, Hamachi included: a code the host makes up, nothing to ask. */
        THIS_NETWORK
    }

    private final boolean relaySet;
    private final PasswordLine password;
    private Where where = Where.THIS_NETWORK;
    private String problem = "";

    /**
     * @param relaySet {@code net.relay.host} names a relay
     * @param remembered the password this machine asked for last time, or empty
     */
    public HostChoices(boolean relaySet, String remembered) {
        this.relaySet = relaySet;
        this.password = new PasswordLine(remembered);
    }

    public boolean relaySet() {
        return relaySet;
    }

    public Where where() {
        return where;
    }

    /** Whether OPEN would ask the relay for a room. */
    public boolean internet() {
        return where == Where.INTERNET;
    }

    /** Steps the choice row; the internet is passed over while no relay is set. */
    public void step() {
        problem = "";
        where = relaySet && where == Where.THIS_NETWORK ? Where.INTERNET : Where.THIS_NETWORK;
    }

    /** Whether the password line takes anything: the internet only. */
    public boolean passwordEnabled() {
        return internet();
    }

    /** One typed character into the password line, if it is live; a keyboard types into it at any time. */
    public void type(char c) {
        if (passwordEnabled()) {
            problem = "";
            password.type(c);
        }
    }

    /** Replaces the password - with one picked on a pad. */
    public void replacePassword(String text) {
        if (passwordEnabled()) {
            problem = "";
            password.replace(text);
        }
    }

    /** The password as typed so far, for the line. */
    public String typedPassword() {
        return password.text();
    }

    /**
     * What OPEN asks the relay for as the password: four characters, or empty for none - null, with {@link #problem()}
     * saying why, while the line holds only part of one. Meaningless on this network.
     */
    public @Nullable String password() {
        String read = password.read();
        problem = read == null ? PasswordLine.INCOMPLETE : "";
        return read;
    }

    /** Why the last {@link #password()} came back empty, or nothing. */
    public String problem() {
        return problem;
    }
}
