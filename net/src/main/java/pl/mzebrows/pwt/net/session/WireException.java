package pl.mzebrows.pwt.net.session;

/**
 * A message that is not what the protocol says it should be: too short, too long, a slot the session
 * does not have. The link that sent it is dropped - a malformed message is a bug or a stranger, and
 * neither is worth guessing at.
 */
public final class WireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    WireException(String message) {
        super(message);
    }
}
