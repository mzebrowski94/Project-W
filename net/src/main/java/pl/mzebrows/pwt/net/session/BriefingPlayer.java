package pl.mzebrows.pwt.net.session;

/**
 * One player on the briefing (G0101): a slot, the machine it plays from, and whether that machine's link is
 * up. The host's machine first, then by slot - the order {@link PeerSession#players()} lists them in.
 *
 * @param machine the {@link MachineId} of the computer, which is all the game knows of who is there
 */
public record BriefingPlayer(int slot, String machine, boolean host, boolean online) {}
