package pl.mzebrows.pwt.ui.hud;

/**
 * Turns a dispatch into the words the terminal shows. The client's, because the words live in its
 * strings; asked only when a line is shown or changes, never per offer and never per frame.
 */
@FunctionalInterface
public interface DispatchWords {

    String words(Dispatch dispatch);
}
