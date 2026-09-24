/**
 * The lockstep session (ADR 0012): the {@link pl.mzebrows.pwt.net.session.Sequencer} that seals every
 * tick's inputs into a bundle, the {@link pl.mzebrows.pwt.net.session.PeerSession} each machine plays
 * through, and the wire format between them. Everything here runs on a transport and a clock it is handed,
 * so a whole session - host, peers, a bad network - runs in one test on one thread.
 */
@NullMarked
package pl.mzebrows.pwt.net.session;

import org.jspecify.annotations.NullMarked;
