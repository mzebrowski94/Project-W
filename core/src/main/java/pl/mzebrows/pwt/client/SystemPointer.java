package pl.mzebrows.pwt.client;

/**
 * The operating system's mouse pointer, hidden while the game draws its own reticle and shown again on
 * pages. A port because libGDX has no call that hides the pointer and keeps it absolute: its "caught"
 * cursor goes relative, which would break mouse aim, and a blank cursor image leaves whatever the
 * operating system draws for it. The launcher supplies the backend's own switch.
 */
public interface SystemPointer {

    void hide();

    void show();
}
