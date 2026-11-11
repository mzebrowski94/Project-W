package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The swarm adapts on a schedule, with no chooser (D11): one adaptation every so many seconds of level
 * time and one per so many upgrades the players take, both off at zero. An adaptation is an upgrade on
 * the aliens' side, held in the same ledger and read through the same fold.
 */
class AdaptationTest {

    private static final float MIDDLE = 50f;
    private static final int BASE_TICKS = 40;
    private static final float EVERY_SECONDS = 1f;

    @Test
    void adaptsNothingAndDrawsNothingWhileBothHalvesAreOff() {
        Simulation simulation = new Simulation(scheduled(0f, 0));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        long untouched = simulation.stream(RngStream.ADAPTATION).state();

        AlphaWorlds.steps(simulation, ticksIn(5f));
        pick(simulation, UpgradeKind.WEAPON_HANDLING);
        pick(simulation, UpgradeKind.LARGER_ARMY);

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isZero();
        assertThat(simulation.stream(RngStream.ADAPTATION).state()).isEqualTo(untouched);
    }

    @Test
    void adaptsOnceEveryIntervalOfLevelTime() {
        Simulation simulation = new Simulation(scheduled(EVERY_SECONDS, 0));
        simulation.spawnCommander(MIDDLE, MIDDLE);

        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS) - 2);
        int before = ranks(simulation, UpgradeKind.RAPID_SPIT);
        AlphaWorlds.steps(simulation, 3);
        int once = ranks(simulation, UpgradeKind.RAPID_SPIT);
        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS));

        assertThat(before).isZero();
        assertThat(once).isOne();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isEqualTo(2);
    }

    @Test
    void adaptsOncePerSoManyUpgradesThePlayersTake() {
        Simulation simulation = new Simulation(scheduled(0f, 2));
        simulation.spawnCommander(MIDDLE, MIDDLE);

        pick(simulation, UpgradeKind.WEAPON_HANDLING);
        int afterOne = ranks(simulation, UpgradeKind.RAPID_SPIT);
        pick(simulation, UpgradeKind.LARGER_ARMY);

        assertThat(afterOne).isZero();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /** Every adaptation at its last rank: nothing more is taken, and nothing more is drawn for it. */
    @Test
    void takesNothingMoreAndDrawsNothingOnceThePoolIsDry() {
        Simulation simulation = new Simulation(scheduled(EVERY_SECONDS, 0));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        int last = simulation.config().upgrades().of(UpgradeKind.RAPID_SPIT).ranks();

        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS * last) + 1);
        long dry = simulation.stream(RngStream.ADAPTATION).state();
        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS * 2));

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isEqualTo(last);
        assertThat(DraftTest.ledger(simulation).adaptations).isEqualTo(last);
        assertThat(simulation.stream(RngStream.ADAPTATION).state()).isEqualTo(dry);
    }

    /**
     * The reactive hook (D11): an adaptation that answers an upgrade the players hold is dealt ahead of
     * one that answers nothing, once the answer weight is turned up. Marksmen stands in as a second
     * adaptation, since the catalogue has one.
     */
    @Test
    void dealsTheAnswerToAnUpgradeThePlayersHoldOnceTheAnswerWeightIsUp() {
        SimConfig config = AlphaWorlds.config()
                .withAdaptation(new AdaptationDef(EVERY_SECONDS, 0, 1000))
                .withUpgrades(AlphaWorlds.config()
                        .upgrades()
                        .with(
                                UpgradeKind.MARKSMEN,
                                new UpgradeDef(
                                        Side.ALIEN,
                                        10,
                                        1,
                                        1,
                                        null,
                                        0,
                                        UpgradeKind.WEAPON_HANDLING,
                                        List.of(new Effect.Breed(UnitKind.GRUNT, Stat.FIRE_COOLDOWN, -10)))));
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        pick(simulation, UpgradeKind.WEAPON_HANDLING);

        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS));

        assertThat(ranks(simulation, UpgradeKind.MARKSMEN)).isOne();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isZero();
    }

    /** It narrows to its strain: a grunt with the same gun, the squad and the commander are untouched. */
    @Test
    void movesOnlyItsOwnStrainsNumber() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE + 20f, MIDDLE + 20f);

        DraftTest.ledger(simulation).held.add(new Upgrades.Held(UpgradeKind.RAPID_SPIT, Upgrades.SHARED, 1));

        assertThat(cooldown(simulation, spitter)).isLessThan(BASE_TICKS);
        assertThat(cooldown(simulation, grunt)).isEqualTo(BASE_TICKS);
        assertThat(cooldown(simulation, trooper)).isEqualTo(BASE_TICKS);
        assertThat(cooldown(simulation, commander)).isEqualTo(BASE_TICKS);
    }

    /** The same, asked of one port in turn: what one strain's read found is not handed to another strain. */
    @Test
    void movesOnlyItsOwnStrainsNumberWhicheverStrainIsReadFirst() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE + 20f, MIDDLE + 20f);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());
        DraftTest.ledger(simulation).held.add(new Upgrades.Held(UpgradeKind.RAPID_SPIT, Upgrades.SHARED, 1));

        int first = modifiers.fireCooldownTicks(grunt, WeaponKind.SPIT, BASE_TICKS);

        assertThat(modifiers.fireCooldownTicks(spitter, WeaponKind.SPIT, BASE_TICKS))
                .isLessThan(BASE_TICKS);
        assertThat(first)
                .isEqualTo(modifiers.fireCooldownTicks(grunt, WeaponKind.SPIT, BASE_TICKS))
                .isEqualTo(BASE_TICKS);
    }

    @Test
    void readsTheSameForASpitterOnTheFieldAndOneThatArrivesAfterwards() {
        Simulation simulation = new Simulation(scheduled(EVERY_SECONDS, 0));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity before = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE);

        AlphaWorlds.steps(simulation, ticksIn(EVERY_SECONDS) + 1);
        Entity after = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE + 20f);

        assertThat(cooldown(simulation, before))
                .isEqualTo(cooldown(simulation, after))
                .isLessThan(BASE_TICKS);
    }

    private static SimConfig scheduled(float everySeconds, int perPlayerUpgrades) {
        return AlphaWorlds.config().withAdaptation(new AdaptationDef(everySeconds, perPlayerUpgrades, 0));
    }

    /** Takes one rank through the real path: a point taken earns a draft, and a pick takes the card. */
    private static void pick(Simulation simulation, UpgradeKind kind) {
        simulation.spawnControlPoint(10f, 10f).getComponent(ControlPoint.class).captured = true;
        simulation.step();
        int serial = DraftTest.drafts(simulation).pending.first().serial();
        simulation.step(new PlayerInputs().pick(0, serial, kind));
    }

    private static int ranks(Simulation simulation, UpgradeKind kind) {
        Upgrades ledger = DraftTest.ledger(simulation);
        for (int i = 0; i < ledger.held.size; i++) {
            if (ledger.held.get(i).kind() == kind) {
                return ledger.held.get(i).ranks();
            }
        }
        return 0;
    }

    private static int cooldown(Simulation simulation, Entity who) {
        return new Modifiers(simulation.engine(), simulation.config())
                .fireCooldownTicks(who, WeaponKind.SPIT, BASE_TICKS);
    }
}
