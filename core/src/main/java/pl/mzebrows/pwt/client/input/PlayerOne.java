package pl.mzebrows.pwt.client.input;

/**
 * What player one plays with on this machine - seat zero's device. The keyboard and mouse by default; with {@link
 * #PAD} the first pad seated takes seat zero and the keyboard drives no commander, only the menus, the view and the
 * harness. Shipped as {@code controls.playerOne}, switched on the controls page and kept in the player's settings over
 * it; read as a level starts, which is why the page locks it while one is running.
 */
public enum PlayerOne {
    KEYBOARD,
    PAD
}
