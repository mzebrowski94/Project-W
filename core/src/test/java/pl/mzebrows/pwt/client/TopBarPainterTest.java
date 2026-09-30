package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;

/** The crate's cards are rebuilt only when their key changes, so two different crates need two keys. */
class TopBarPainterTest {

    private static final PickupKind[] KINDS = PickupKind.values();

    @Test
    void givesEveryCrateItCanShowAKeyOfItsOwn() {
        Set<Long> keys = new HashSet<>();
        int crates = 0;
        PlayerReadout player = new PlayerReadout();
        for (int shown = 0; shown <= PlayerReadout.CRATE_CARDS; shown++) {
            int values = (int) Math.pow(2 * KINDS.length, shown);
            for (int crate = 0; crate < values; crate++) {
                int rest = crate;
                for (int card = 0; card < shown; card++) {
                    player.crate[card] = KINDS[rest % KINDS.length];
                    player.crateRefused[card] = rest / KINDS.length % 2 == 1;
                    rest /= 2 * KINDS.length;
                }
                keys.add(TopBarPainter.crateKey(player, shown));
                crates++;
            }
        }

        assertThat(keys).hasSize(crates);
    }
}
