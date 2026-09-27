package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.Dispatch;
import pl.mzebrows.pwt.ui.hud.DispatchKind;
import pl.mzebrows.pwt.ui.hud.DispatchWords;

/**
 * A terminal line's words, from {@code ui.dispatch.<KIND>} in {@code ui.properties}. Every template gets
 * the same arguments: {@code {0}} the player it is about ({@code "P2 "}, or nothing while he is the only
 * one), {@code {1}} and {@code {2}} its numbers, {@code {3}} the gun's name.
 */
final class DispatchText implements DispatchWords {

    /** Once: {@code values()} hands out a new array on every call. */
    private static final WeaponKind[] WEAPONS = WeaponKind.values();

    private final UiStrings strings;

    DispatchText(UiStrings strings) {
        this.strings = strings;
    }

    @Override
    public String words(Dispatch dispatch) {
        String player = dispatch.slot() < 0 ? "" : strings.format("ui.hud.player", dispatch.slot() + 1) + " ";
        String gun = dispatch.kind() == DispatchKind.WEAPON_ACQUIRED
                ? strings.get("ui.weapon." + WEAPONS[Math.max(0, Math.min(WEAPONS.length - 1, dispatch.a()))].name())
                : "";
        return strings.format(dispatch.kind().key(), player, dispatch.a(), dispatch.b(), gun);
    }
}
