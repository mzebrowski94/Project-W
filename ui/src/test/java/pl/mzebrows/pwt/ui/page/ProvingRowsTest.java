package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/** The proving ground page's choice rows (G0060): what left and right do to a setup, and what is shown. */
class ProvingRowsTest {

    private final ProvingRows rows = new ProvingRows(SimConfig.defaults());

    @Test
    void stepsTheStrainThroughEveryStrainAndRoundAgain() {
        ProvingSetup setup = ProvingSetup.first();
        List<UnitKind> seen = new ArrayList<>();
        for (int i = 0; i <= UnitKind.values().length; i++) {
            seen.add(setup.strain());
            setup = rows.step(setup, ProvingRows.Row.STRAIN, 1);
        }

        assertThat(seen).startsWith(UnitKind.values()).endsWith(UnitKind.GRUNT);
        UnitKind[] kinds = UnitKind.values();
        assertThat(rows.step(ProvingSetup.first(), ProvingRows.Row.STRAIN, -1).strain())
                .isEqualTo(kinds[kinds.length - 1]);
    }

    @Test
    void stepsTheCountThroughOneThreeFiveTenAndANest() {
        ProvingSetup setup = ProvingSetup.first().withCount(1);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            seen.add(rows.value(setup, ProvingRows.Row.COUNT).text());
            setup = rows.step(setup, ProvingRows.Row.COUNT, 1);
        }

        assertThat(seen).containsExactly("1", "3", "5", "10", "", "1");
        ProvingSetup nest = rows.step(ProvingSetup.first().withCount(10), ProvingRows.Row.COUNT, 1);
        assertThat(nest.nest()).isTrue();
        assertThat(rows.value(nest, ProvingRows.Row.COUNT).key()).isEqualTo("ui.provingGround.nest");
    }

    /** Only what a man can carry: a creature's spit or a turret's gun is not his to practise with. */
    @Test
    void offersEveryGunAManCarriesAndNothingElse() {
        ProvingSetup setup = ProvingSetup.first();
        List<WeaponKind> seen = new ArrayList<>();
        do {
            seen.add(setup.gun());
            setup = rows.step(setup, ProvingRows.Row.GUN, 1);
        } while (setup.gun() != WeaponKind.SIDEARM);

        assertThat(seen)
                .containsExactly(
                        WeaponKind.SIDEARM,
                        WeaponKind.RAILGUN,
                        WeaponKind.FLAMER,
                        WeaponKind.SHOTGUN,
                        WeaponKind.LASER,
                        WeaponKind.BAZOOKA,
                        WeaponKind.PULSE,
                        WeaponKind.DISC_GUN);
        assertThat(rows.value(ProvingSetup.first(), ProvingRows.Row.GUN).key()).isEqualTo("ui.weapon.SIDEARM");
    }

    /** A strain that cannot walk is one, standing (the world's rule): the row says so and will not move. */
    @Test
    void holdsTheCountAtOneForARootedStrain() {
        ProvingSetup boss = ProvingSetup.first().withStrain(UnitKind.BOSS_GRUNT).withCount(5);

        assertThat(rows.locked(boss, ProvingRows.Row.COUNT)).isTrue();
        assertThat(rows.value(boss, ProvingRows.Row.COUNT).text()).isEqualTo("1");
        assertThat(rows.step(boss, ProvingRows.Row.COUNT, 1)).isEqualTo(boss);
        assertThat(rows.locked(ProvingSetup.first(), ProvingRows.Row.COUNT)).isFalse();
    }

    @Test
    void togglesTheSquadAndStepsTheOthers() {
        assertThat(rows.step(ProvingSetup.first(), ProvingRows.Row.SQUAD, 1).squad())
                .isTrue();
        assertThat(rows.step(ProvingSetup.first(), ProvingRows.Row.OTHERS, -1).others())
                .isEqualTo(ProvingSetup.Others.GRENADE_CACHES);
        assertThat(rows.value(ProvingSetup.first(), ProvingRows.Row.OTHERS).key())
                .isEqualTo("ui.provingGround.others.NONE");
    }

    @Test
    void stepsARankFromNoneToItsMostAndRoundAgain() {
        int most = SimConfig.defaults().upgrades().of(UpgradeKind.MARKSMEN).ranks();
        ProvingSetup setup = ProvingSetup.first();
        for (int i = 0; i < most; i++) {
            setup = rows.stepRank(setup, UpgradeKind.MARKSMEN, 1);
        }

        assertThat(setup.rankOf(UpgradeKind.MARKSMEN)).isEqualTo(most);
        assertThat(rows.stepRank(setup, UpgradeKind.MARKSMEN, 1).rankOf(UpgradeKind.MARKSMEN))
                .isZero();
        assertThat(rows.stepRank(ProvingSetup.first(), UpgradeKind.MARKSMEN, -1).rankOf(UpgradeKind.MARKSMEN))
                .isEqualTo(most);
    }

    /** The players' upgrades first, then the swarm's adaptations - the order the aside shows them in. */
    @Test
    void listsThePlayersUpgradesBeforeTheSwarmsAdaptations() {
        List<UpgradeKind> kinds = rows.upgrades();

        int firstSwarm = kinds.indexOf(UpgradeKind.RAPID_SPIT);

        assertThat(kinds).hasSize(UpgradeKind.values().length);
        assertThat(kinds.subList(0, firstSwarm)).noneMatch(rows::swarm).contains(UpgradeKind.MARKSMEN);
        assertThat(kinds.subList(firstSwarm, kinds.size()))
                .allMatch(rows::swarm)
                .contains(UpgradeKind.LARGER_BROOD);
    }

    @Test
    void countsWhatIsHeld() {
        ProvingSetup setup =
                ProvingSetup.first().withRank(UpgradeKind.MARKSMEN, 2).withRank(UpgradeKind.RAPID_SPIT, 1);

        assertThat(rows.held(setup)).isEqualTo(2);
    }
}
