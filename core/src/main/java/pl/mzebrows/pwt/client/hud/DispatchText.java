package pl.mzebrows.pwt.client.hud;

import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.Dispatch;
import pl.mzebrows.pwt.ui.hud.DispatchWords;
import pl.mzebrows.pwt.ui.hud.RankText;

/**
 * A terminal line's words, from {@code ui.dispatch.<KIND>} in {@code ui.properties}. Every template gets
 * the same arguments: {@code {0}} the player it is about ({@code "P2 "}, or nothing while he is the only
 * one), {@code {1}} and {@code {2}} its numbers, {@code {3}} the name it carries - the gun's, or the
 * upgrade's with its rank ({@code LARGER ARMY II}).
 */
public final class DispatchText implements DispatchWords {

    /** Once: {@code values()} hands out a new array on every call. */
    private static final WeaponKind[] WEAPONS = WeaponKind.values();

    private static final UpgradeKind[] UPGRADES = UpgradeKind.values();

    private final UiStrings strings;

    public DispatchText(UiStrings strings) {
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
                    case HOST_CHANGED -> dispatch.slot() < 0 ? hostName(strings, -1, dispatch.a()) + " " : "";
                    default -> "";
                };
        return strings.format(dispatch.kind().key(), player, dispatch.a(), dispatch.b(), name);
    }

    /** A host as the players read him: his slot, {@code P3}, or when he holds none his peer number, {@code PEER 2} (G0104). */
    public static String hostName(UiStrings strings, int slot, int peer) {
        return slot >= 0 ? strings.format("ui.hud.player", slot + 1) : strings.format("ui.hud.peer", peer);
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(length - 1, index));
    }
}
