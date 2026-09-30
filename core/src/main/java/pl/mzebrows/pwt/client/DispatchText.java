package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.Dispatch;
import pl.mzebrows.pwt.ui.hud.DispatchWords;
import pl.mzebrows.pwt.ui.hud.RankText;

/**
 * A terminal line's words, from {@code ui.dispatch.<KIND>} in {@code ui.properties}. Every template gets
 * the same arguments: {@code {0}} the player it is about ({@code "P2 "}, or nothing while he is the only
 * one), {@code {1}} and {@code {2}} its numbers, {@code {3}} the name it carries - the gun's, or the
 * upgrade's with its rank ({@code LARGER ARMY II}).
 */
final class DispatchText implements DispatchWords {

    /** Once: {@code values()} hands out a new array on every call. */
    private static final WeaponKind[] WEAPONS = WeaponKind.values();

    private static final UpgradeKind[] UPGRADES = UpgradeKind.values();

    private final UiStrings strings;

    DispatchText(UiStrings strings) {
        this.strings = strings;
    }

    @Override
    public String words(Dispatch dispatch) {
        String player = dispatch.slot() < 0 ? "" : strings.format("ui.hud.player", dispatch.slot() + 1) + " ";
        String name =
                switch (dispatch.kind()) {
                    case WEAPON_ACQUIRED ->
                        strings.get("ui.weapon." + WEAPONS[clamp(dispatch.a(), WEAPONS.length)].name());
                    case UPGRADE_TAKEN ->
                        strings.get("ui.upgrade." + UPGRADES[clamp(dispatch.a(), UPGRADES.length)].name()) + " "
                                + RankText.roman(dispatch.b());
                    case SWARM_ADAPTS ->
                        strings.get("ui.adapted." + UPGRADES[clamp(dispatch.a(), UPGRADES.length)].name());
                    default -> "";
                };
        return strings.format(dispatch.kind().key(), player, dispatch.a(), dispatch.b(), name);
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(length - 1, index));
    }
}
