package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Posture;

/**
 * The dev overlay says of the commander what his panel says, because both read the readout: down from his
 * posture, his gun as the panel names it, his protection.
 */
class DebugOverlayTest {

    @Test
    void saysAStandingCommandersWoundsAndGun() {
        PlayerReadout player = standing();
        player.wounds = 1;
        player.maxWounds = 3;

        assertThat(DebugOverlay.commanderState(player, false)).isEqualTo("wounds 1/3   " + WeaponKind.SIDEARM);
    }

    @Test
    void saysACasualtyOrADyingCommanderIsDown() {
        PlayerReadout casualty = standing();
        casualty.posture = Posture.CASUALTY;
        PlayerReadout dying = standing();
        dying.posture = Posture.DYING;

        assertThat(DebugOverlay.commanderState(casualty, false)).startsWith("down");
        assertThat(DebugOverlay.commanderState(dying, false)).startsWith("down");
    }

    @Test
    void saysTheGunHeIsOnWhileAboard() {
        PlayerReadout player = standing();
        player.posture = Posture.ABOARD;
        player.weapon = WeaponKind.TURRET_GUN;

        assertThat(DebugOverlay.commanderState(player, false)).endsWith("   " + WeaponKind.TURRET_GUN);
    }

    @Test
    void saysThereIsNoCommanderWhileHeWaitsToComeBack() {
        assertThat(DebugOverlay.commanderState(new PlayerReadout(), false)).isEqualTo("dead, respawning");
    }

    @Test
    void namesGodModeOverHisOwnProtection() {
        PlayerReadout player = standing();
        player.invulnerable = true;

        assertThat(DebugOverlay.commanderState(player, true)).endsWith("GOD MODE");
        assertThat(DebugOverlay.commanderState(player, false)).endsWith("invulnerable");
    }

    private static PlayerReadout standing() {
        PlayerReadout player = new PlayerReadout();
        player.posture = Posture.STANDING;
        return player;
    }
}
