package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Site;
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
 * The swarm adapts on the drafts' schedule, with no chooser (D11, G0118): its count is the side's - the sites taken
 * and the furthest biome any commander has entered - its first waits a gap, and each source is worth more for every
 * player seated. An adaptation is an upgrade on the aliens' side, held in the same ledger and read through the same
 * fold. Here a level plans the swarm one adaptation a source ({@code AlphaWorlds}: one source a draft's reference
 * length, a full curve), and the pool is Rapid Spit's three ranks.
 */
class AdaptationTest {

    private static final float MIDDLE = 50f;
    private static final int BASE_TICKS = 40;

    /** Points enough for every site a test here takes. */
    private static final int SITES = 3;

    /** The west edge of each ridge's core, in tiles; the next biome starts two columns on, past its pass's middle. */
    private static final int FIRST_RIDGE = 8;

    private static final int SECOND_RIDGE = 14;
    private static final int INTO_BIOME = 2;
    private static final float WEST = 10f;

    @Test
    void adaptsNothingAndDrawsNothingWhileAdaptationsAreOff() {
        Simulation simulation = new Simulation(adapting(0));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, SITES);
        long untouched = simulation.stream(RngStream.ADAPTATION).state();

        take(simulation, SITES);

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isZero();
        assertThat(simulation.stream(RngStream.ADAPTATION).state()).isEqualTo(untouched);
    }

    @Test
    void adaptsOnceForEverySiteTheSideTakes() {
        Simulation simulation = new Simulation(adapting(1));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, SITES);
        simulation.step();
        int before = ranks(simulation, UpgradeKind.RAPID_SPIT);

        take(simulation, 1);
        int once = ranks(simulation, UpgradeKind.RAPID_SPIT);
        take(simulation, 1);

        assertThat(before).isZero();
        assertThat(once).isOne();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isEqualTo(2);
    }

    /**
     * Where a draft comes on the first source, the swarm's first waits its gap: two planned over four sites fall on the
     * second and the fourth.
     */
    @Test
    void waitsAGapBeforeItsFirstAdaptationWhereADraftComesAtOnce() {
        Simulation simulation = new Simulation(adapting(2).withAdaptation(flat(2)));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, 4);

        take(simulation, 1);
        int afterOne = ranks(simulation, UpgradeKind.RAPID_SPIT);
        take(simulation, 1);

        assertThat(DraftTest.drafts(simulation).pending.size).isPositive();
        assertThat(afterOne).isZero();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /** The drafts' crossing is a commander in the ridge's pass; the swarm's is a commander in the biome beyond it. */
    @Test
    void adaptsWhenACommanderFirstEntersTheBiomeBeyondARidgeAndNotBefore() {
        Simulation simulation = new Simulation(adapting(1), biomes());
        Entity commander = simulation.spawnCommander(WEST, MIDDLE, 0);
        simulation.step();

        standAt(commander, FIRST_RIDGE);
        simulation.step();
        int inThePass = ranks(simulation, UpgradeKind.RAPID_SPIT);
        standAt(commander, FIRST_RIDGE + INTO_BIOME);
        simulation.step();

        assertThat(simulation.upgradeView().draftsOf(0)).isOne();
        assertThat(inThePass).isZero();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /** The side's furthest biome: the second commander to enter it adds nothing, and going back takes nothing away. */
    @Test
    void countsEachBiomeOnceForTheWholeSide() {
        Simulation simulation = new Simulation(adapting(1), biomes());
        Entity first = simulation.spawnCommander(WEST, MIDDLE, 0);
        Entity second = simulation.spawnCommander(WEST, MIDDLE + 5f, 1);
        simulation.step();

        standAt(first, FIRST_RIDGE + INTO_BIOME);
        simulation.step();
        standAt(second, FIRST_RIDGE + INTO_BIOME);
        standAt(first, FIRST_RIDGE - 2);
        simulation.step();

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /** A commander who comes on two biomes east has entered both. */
    @Test
    void catchesUpEveryBiomeWestOfWhereACommanderComesOn() {
        Simulation simulation = new Simulation(adapting(1), biomes());
        simulation.spawnCommander((SECOND_RIDGE + INTO_BIOME + 1) * TILE, MIDDLE, 0);

        simulation.step();

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isEqualTo(2);
    }

    /** With two seated and a source worth twice as much, the first site reaches the first of two over four. */
    @Test
    void makesASourceWorthMoreForEveryPlayerSeatedPastTheFirst() {
        Simulation simulation = new Simulation(adapting(2).withAdaptation(flat(2).withPerPlayerPercent(100)));
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE, MIDDLE + 5f, 1);
        sites(simulation, 4);

        take(simulation, 1);

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /** A third of three ranks alone; with company the share rises, but stops at the ceiling's two of three. */
    @Test
    void holdsNoMoreThanItsShareOfThePoolAndNeverPastTheCeilingInCompany() {
        AdaptationDef shared = adapting(1)
                .adaptation()
                .withMostPercent(34)
                .withCeilingPercent(67)
                .withPerPlayerPercent(100);
        Simulation alone = new Simulation(adapting(1).withAdaptation(shared));
        alone.spawnCommander(MIDDLE, MIDDLE, 0);
        sites(alone, SITES);
        Simulation four = new Simulation(adapting(1).withAdaptation(shared), 4);
        for (int slot = 0; slot < 4; slot++) {
            four.spawnCommander(MIDDLE, MIDDLE + 5f * slot, slot);
        }
        sites(four, SITES);

        take(alone, SITES);
        take(four, SITES);

        assertThat(ranks(alone, UpgradeKind.RAPID_SPIT)).isOne();
        assertThat(ranks(four, UpgradeKind.RAPID_SPIT)).isEqualTo(2);
    }

    /** Drawn once when the level is planned, from the swarm's stream: never more than the spread either way. */
    @Test
    void movesThePlanByNoMoreThanItsSpreadEitherWay() {
        Set<Integer> plans = new TreeSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            Simulation simulation = new Simulation(adapting(2).withSeed(seed).withAdaptation(flat(2).withSpread(1)));
            simulation.spawnCommander(MIDDLE, MIDDLE);
            sites(simulation, 4);
            simulation.step();
            plans.add(DraftTest.ledger(simulation).adaptationsPlanned);
        }

        assertThat(plans).containsExactly(1, 2, 3);
    }

    /**
     * Its tiers open by share of its own plan: two planned, the tier from half of it waits for the first adaptation.
     * Marksmen stands in as a tier-one adaptation of one rank, and Rapid Spit is moved to tier two.
     */
    @Test
    void opensAnAdaptationsTierByShareOfTheSwarmsOwnPlan() {
        SimConfig base = adapting(1);
        SimConfig config = base.withUpgrades(base.upgrades()
                .with(UpgradeKind.MARKSMEN, alien(1, null))
                .with(
                        UpgradeKind.RAPID_SPIT,
                        new UpgradeDef(
                                Side.ALIEN,
                                10,
                                3,
                                2,
                                null,
                                0,
                                List.of(new Effect.Breed(
                                        UnitKind.SPITTER, Stat.FIRE_COOLDOWN, AlphaWorlds.SPIT_PERCENT)))));
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, 2);

        take(simulation, 1);
        int spitFirst = ranks(simulation, UpgradeKind.RAPID_SPIT);
        take(simulation, 1);

        assertThat(spitFirst).isZero();
        assertThat(ranks(simulation, UpgradeKind.MARKSMEN)).isOne();
        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isOne();
    }

    /**
     * Every adaptation at its last rank - all but one held from the first tick, as the proving ground holds them: an
     * adaptation still owed takes nothing more, and draws nothing for it.
     */
    @Test
    void takesNothingMoreAndDrawsNothingOnceThePoolIsDry() {
        Simulation simulation = new Simulation(adapting(1));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        int last = simulation.config().upgrades().of(UpgradeKind.RAPID_SPIT).ranks();
        AlphaWorlds.hold(simulation, UpgradeKind.RAPID_SPIT, last - 1);
        sites(simulation, SITES);

        take(simulation, 1);
        long dry = simulation.stream(RngStream.ADAPTATION).state();
        take(simulation, 2);

        assertThat(ranks(simulation, UpgradeKind.RAPID_SPIT)).isEqualTo(last);
        assertThat(DraftTest.ledger(simulation).credited).isEqualTo(SITES);
        assertThat(simulation.stream(RngStream.ADAPTATION).state()).isEqualTo(dry);
    }

    /**
     * The reactive hook (D11): an adaptation that answers an upgrade the players hold is dealt ahead of
     * one that answers nothing, once the answer weight is turned up. Marksmen stands in as a second
     * adaptation, since the catalogue has one.
     */
    @Test
    void dealsTheAnswerToAnUpgradeThePlayersHoldOnceTheAnswerWeightIsUp() {
        SimConfig base = adapting(1);
        SimConfig config = base.withAdaptation(base.adaptation().withAnswerWeight(1000))
                .withUpgrades(base.upgrades().with(UpgradeKind.MARKSMEN, alien(1, UpgradeKind.WEAPON_HANDLING)));
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, SITES);
        AlphaWorlds.hold(simulation, UpgradeKind.WEAPON_HANDLING, 1);

        take(simulation, 1);

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
        Simulation simulation = new Simulation(adapting(1));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        sites(simulation, SITES);
        Entity before = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE);

        take(simulation, 1);
        Entity after = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE + 20f, MIDDLE + 20f);

        assertThat(cooldown(simulation, before))
                .isEqualTo(cooldown(simulation, after))
                .isLessThan(BASE_TICKS);
    }

    /** The fixture's config, the swarm planned this many adaptations at the drafts' reference length of one source. */
    private static SimConfig adapting(int adaptations) {
        SimConfig config = AlphaWorlds.config();
        return config.withAdaptation(config.adaptation().withAdaptations(adaptations));
    }

    /** This many adaptations whatever the level's length: a curve of none. */
    private static AdaptationDef flat(int adaptations) {
        return AlphaWorlds.config().adaptation().withAdaptations(adaptations).withCurvePercent(0);
    }

    /** One rank of a tier-one adaptation that moves a grunt's spit, answering {@code answers} or nothing. */
    private static UpgradeDef alien(int ranks, UpgradeKind answers) {
        return new UpgradeDef(
                Side.ALIEN,
                10,
                ranks,
                1,
                null,
                0,
                answers,
                List.of(new Effect.Breed(UnitKind.GRUNT, Stat.FIRE_COOLDOWN, -10)));
    }

    /** Puts down sites, before the level's first tick plans its schedule on them (G0118). */
    private static void sites(Simulation simulation, int count) {
        for (int i = 0; i < count; i++) {
            simulation.spawnControlPoint(10f + 5f * i, 10f);
        }
    }

    /** Takes this many more sites, a tick apart. */
    private static void take(Simulation simulation, int count) {
        ImmutableArray<Entity> sites =
                simulation.engine().getEntitiesFor(Family.all(Site.class).get());
        for (int taken = 0; taken < count; taken++) {
            for (int i = 0; i < sites.size(); i++) {
                Site site = sites.get(i).getComponent(Site.class);
                if (!site.taken) {
                    site.taken = true;
                    break;
                }
            }
            simulation.step();
        }
    }

    /**
     * The arena in three biomes with a ridge marked between each two: a ridge's west half is the biome it leaves, and
     * the next biome starts {@link #INTO_BIOME} columns into it, as a strip lays them.
     */
    private static TileMap biomes() {
        TileMap.Builder builder = AlphaWorlds.ground().ridge(FIRST_RIDGE).ridge(SECOND_RIDGE);
        for (int col = 0; col < builder.cols(); col++) {
            int biome = col < FIRST_RIDGE + INTO_BIOME ? 0 : col < SECOND_RIDGE + INTO_BIOME ? 1 : 2;
            for (int row = 0; row < builder.rows(); row++) {
                builder.region(col, row, biome);
            }
        }
        return builder.regionInBiome(1, 1).regionInBiome(2, 2).build();
    }

    /** Puts him in the middle of a column, as the solver would leave him there. */
    private static void standAt(Entity commander, int col) {
        Position at = commander.getComponent(Position.class);
        at.x = (col + 0.5f) * TILE;
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
