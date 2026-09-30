package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.Effect;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.upgrade.Stat;
import pl.mzebrows.pwt.sim.upgrade.Target;
import pl.mzebrows.pwt.sim.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * The numbers here that fail silently. Everything else in {@code SimConfig} is validated at
 * construction or is visible the moment it is wrong; the grid's cell size and the clinch carry are
 * neither - one lets units walk through each other, the other leaves a freeze playing across a gap.
 */
class SimConfigTest {

    private static final float HUGE = 40f;

    /** Keeps a rate that never divides by zero from being a rate that divides by zero. */
    private static final float TINY = 1e-6f;

    /** Brings the carry to a halt almost instantly, well short of the sweep's own reach. */
    private static final float HUGE_DECAY = 100_000f;

    /**
     * A strain no scenario spawns is still reachable by evolving, and a collision cell too small for
     * a grown body stops the grid reporting some overlapping pairs at all. Units then walk through
     * each other, with nothing else failing.
     */
    @Test
    void measuresTheLargestBodyEvenWhenOnlyEvolvingCanReachIt() {
        SimConfig config = SimConfig.defaults()
                .withUnit(UnitKind.BRUTE, brute -> brute.withRadius(HUGE).withCount(0));

        assertThat(config.largestRadius()).isEqualTo(HUGE);
    }

    /** Neither a strain nor a pickup, so neither table's loop measures one; both are solid bodies. */
    @Test
    void measuresANestWiderThanAnythingElse() {
        SimConfig config = SimConfig.defaults();

        SimConfig wide = config.withNest(config.nest().withRadius(HUGE));

        assertThat(wide.largestRadius()).isEqualTo(HUGE);
    }

    @Test
    void measuresATurretWiderThanAnythingElse() {
        SimConfig config = SimConfig.defaults();

        SimConfig wide = config.withTurret(config.turret().withRadius(HUGE));

        assertThat(wide.largestRadius()).isEqualTo(HUGE);
    }

    /**
     * <strong>Breaking a frog's aggro has to actually work.</strong> "It gives up easily" is a fact
     * about two numbers and nothing else - how far past its detection range it will keep hunting you
     * - and there is no system anywhere that would notice if somebody widened the gap to match the
     * rest of the table while tuning. The mechanic would simply stop existing.
     */
    @Test
    void letsGoOfHimSoonerThanAnythingElseOnTheFieldDoes() {
        SimConfig config = SimConfig.defaults();

        float frog = leeway(config, UnitKind.FROG);

        for (UnitKind kind : UnitKind.values()) {
            if (kind != UnitKind.FROG) {
                assertThat(frog)
                        .as("a frog must give up sooner than a %s", kind)
                        .isLessThan(leeway(config, kind));
            }
        }
    }

    /**
     * <strong>The hand strike deletes an ordinary enemy and staggers a brute</strong> - the contrast the
     * whole parry is built on (G0013). It is two numbers in two blocks, and it was quietly broken for
     * as long as a grunt's health had been raised past the strike's damage: the sweep landed, the grunt
     * stood, and its claws killed him on the next tick.
     */
    @Test
    void deletesAnOrdinaryEnemyWithOneSwingAndNotABrute() {
        SimConfig config = SimConfig.defaults();

        assertThat(config.melee().damage()).isGreaterThanOrEqualTo(config.healthOf(UnitKind.GRUNT));
        assertThat(config.melee().damage()).isLessThan(config.healthOf(UnitKind.BRUTE));
    }

    /**
     * <strong>The beam has to run out sooner than anything else he can pick up.</strong> Every good
     * thing about the laser is bought out of one bar - the line through a crowd, the slow, and a
     * body inflated until it goes off - so the moment some other gun runs hotter, the laser is
     * simply the gun you hold down and none of those cost anything. That is two numbers on one
     * definition and no system anywhere would notice it being tuned away.
     *
     * <p>Asked of the guns a <em>crate</em> grants, which is the honest definition of a gun a man
     * can carry: a turret's gun runs hot too and nobody carries one away.
     */
    @Test
    void runsHotterThanAnyOtherGunAManCanCarry() {
        SimConfig config = SimConfig.defaults();

        float beam = secondsOfHolding(config, WeaponKind.LASER);

        for (PickupKind kind : PickupKind.values()) {
            if (config.pickup(kind).playerGrant() instanceof Grant.Arm crate && crate.weapon() != WeaponKind.LASER) {
                assertThat(beam)
                        .as("the beam must stop itself sooner than the %s does", crate.weapon())
                        .isLessThan(secondsOfHolding(config, crate.weapon()));
            }
        }
    }

    /**
     * The same, with every upgrade the players can hold at its highest rank, read through the fold the
     * game uses (ADR 0017). Weapon Handling shortens every cooldown and cools nothing, so it is the
     * rounding of cooldowns to whole ticks that could tip it.
     */
    @Test
    void runsHotterThanAnyOtherGunAManCanCarryWithEveryUpgradeAtItsHighestRank() {
        SimConfig config = SimConfig.defaults();
        Simulation world = everythingHeld(config);
        Entity commander = world.spawnCommander(10f, 10f, 0);
        Modifiers modifiers = new Modifiers(world.engine(), config);

        float beam = secondsOfHolding(config, WeaponKind.LASER, modifiers, commander);

        for (PickupKind kind : PickupKind.values()) {
            if (config.pickup(kind).playerGrant() instanceof Grant.Arm crate && crate.weapon() != WeaponKind.LASER) {
                assertThat(beam)
                        .as("the beam must stop itself sooner than the %s does", crate.weapon())
                        .isLessThan(secondsOfHolding(config, crate.weapon(), modifiers, commander));
            }
        }
    }

    /** A deal adds to a card the deck already holds; dealing crates into crates would deal them forever. */
    @Test
    void refusesADealOfACacheTheArmouryDeckDoesNotHold() {
        SimConfig config = AlphaWorlds.config();

        assertThatThrownBy(() -> config.withUpgrades(config.upgrades()
                        .map(
                                UpgradeKind.FIREPOWER,
                                firepower -> new UpgradeDef(
                                        Side.PLAYER, 0, 2, 1, null, 0, List.of(new Effect.Deal(PickupKind.CRATE, 1))))))
                .hasMessageContaining("not a card in the armoury deck");
    }

    /** A stock puts a card the deck holds into every crate; a crate stocked with crates is nonsense (G0056). */
    @Test
    void refusesAStockOfSomethingTheArmouryDeckDoesNotHold() {
        SimConfig config = AlphaWorlds.config();

        assertThatThrownBy(() -> config.withUpgrades(config.upgrades()
                        .map(
                                UpgradeKind.FIREPOWER,
                                firepower -> new UpgradeDef(
                                        Side.PLAYER,
                                        0,
                                        2,
                                        1,
                                        null,
                                        0,
                                        List.of(new Effect.Stock(PickupKind.CRATE, 1))))))
                .hasMessageContaining("not a card in the armoury deck");
    }

    /** The reactive hook weighs an adaptation by what the players hold, so only an adaptation answers (D11). */
    @Test
    void refusesAnUpgradeOfThePlayersThatAnswersSomething() {
        assertThatThrownBy(() -> new UpgradeDef(
                        Side.PLAYER,
                        10,
                        1,
                        1,
                        null,
                        0,
                        UpgradeKind.MARKSMEN,
                        List.of(new Effect.Scale(Stat.GUN_DAMAGE, Target.SQUAD, 10))))
                .hasMessageContaining("only an adaptation answers");
    }

    /** ... and what it answers is something the players can hold, or it is weighted by nothing ever. */
    @Test
    void refusesAnAdaptationThatAnswersOneOfTheSwarms() {
        SimConfig config = AlphaWorlds.config();

        assertThatThrownBy(() -> config.withUpgrades(config.upgrades()
                        .map(
                                UpgradeKind.RAPID_SPIT,
                                spit -> new UpgradeDef(
                                        Side.ALIEN, 10, 3, 1, null, 0, UpgradeKind.RAPID_SPIT, spit.effects()))))
                .hasMessageContaining("not the players'");
    }

    /** A crate holds no more than the cards a top bar shows (G0056). */
    @Test
    void refusesACrateThatHoldsMoreThanATopBarShows() {
        assertThatThrownBy(() -> new SimConfig.CrateConfig(2, SimConfig.CrateConfig.CAPACITY + 1))
                .hasMessageContaining("at most");
    }

    /** The deck with every deal at its highest rank still fits the bag it is dealt from. */
    @Test
    void refusesAnArmouryDeckTooBigForABag() {
        SimConfig config = AlphaWorlds.config();

        assertThatThrownBy(() -> config.withUpgrades(config.upgrades()
                        .map(
                                UpgradeKind.FIREPOWER,
                                firepower -> new UpgradeDef(
                                        Side.PLAYER,
                                        0,
                                        2,
                                        1,
                                        null,
                                        0,
                                        List.of(new Effect.Deal(PickupKind.ARMOURY_FLAMER, Bag.MOST))))))
                .hasMessageContaining("a bag deals at most");
    }

    /**
     * <strong>The squad at its fastest is still slower than the commander.</strong> A squad following
     * him strings out behind rather than crowding him (the trooper block says why), and Forced March
     * must not undo that at its last rank.
     */
    @Test
    void keepsTheSquadSlowerThanTheCommanderAtEveryUpgradesHighestRank() {
        SimConfig config = SimConfig.defaults();
        Simulation world = everythingHeld(config);
        world.spawnCommander(10f, 10f, 0);
        Entity trooper = world.spawnTrooper(20f, 10f);

        float fastest = config.trooper().speed() * new Modifiers(world.engine(), config).pace(trooper, 1f);

        assertThat(fastest).isLessThan(config.commander().speed());
    }

    /**
     * <strong>A thicket has to cost his side more than it costs theirs.</strong> Ground that slowed
     * everybody equally is scenery - both sides arrive at the fight in the order they would have on
     * open ground and the only thing that changed is how long the walk took. The asymmetry is the
     * whole mechanic and it is two numbers in one table, so nothing else in the game would notice
     * somebody evening them up while tuning how thick the undergrowth feels.
     */
    @Test
    void costsThePlayersSideMoreToWadeThroughThanItCostsTheAliens() {
        GroundDef thicket = SimConfig.defaults().ground(Terrain.THICKET);

        assertThat(thicket.playerFactor()).isLessThan(thicket.alienFactor());
        assertThat(thicket.matters()).isTrue();
    }

    /**
     * How long the trigger can be held down before the gun stops itself. A gun cools whether or not
     * it is firing, so what fills the bar is the difference between the two rates rather than either
     * of them; a gun that stays cold is held down for ever.
     */
    private static float secondsOfHolding(SimConfig config, WeaponKind kind) {
        WeaponDef gun = config.weapon(kind);
        if (!gun.overheats()) {
            return Float.POSITIVE_INFINITY;
        }
        float perSecond = gun.heat().perShot() / Math.max(gun.cooldownSeconds(), TINY);
        return 1f / Math.max(perSecond - gun.heat().coolPerSecond(), TINY);
    }

    /** The same, for the cooldown a shooter is charged once every upgrade reaching him has been folded in. */
    private static float secondsOfHolding(SimConfig config, WeaponKind kind, Modifiers modifiers, Entity who) {
        WeaponDef gun = config.weapon(kind);
        if (!gun.overheats()) {
            return Float.POSITIVE_INFINITY;
        }
        int ticks = modifiers.fireCooldownTicks(who, kind, Ticks.of(gun.cooldownSeconds(), config.tickSeconds()));
        float perSecond = gun.heat().perShot() / Math.max(ticks * config.tickSeconds(), TINY);
        return 1f / Math.max(perSecond - gun.heat().coolPerSecond(), TINY);
    }

    /**
     * A world holding every upgrade and every adaptation at its highest rank, personal ones for slot
     * zero - so each relation above is checked against the aliens' numbers moved as far as they go too.
     */
    private static Simulation everythingHeld(SimConfig config) {
        Simulation world = new Simulation(config);
        Upgrades ledger = world.engine()
                .getEntitiesFor(Family.all(Upgrades.class).get())
                .first()
                .getComponent(Upgrades.class);
        for (UpgradeKind kind : UpgradeKind.values()) {
            UpgradeDef def = config.upgrades().of(kind);
            ledger.held.add(new Upgrades.Held(kind, def.personal() ? 0 : Upgrades.SHARED, def.ranks()));
        }
        return world;
    }

    /** How much further than the range it noticed you at a strain will keep hunting you. */
    private static float leeway(SimConfig config, UnitKind kind) {
        PerceptionDef perception = config.unit(kind).perception();
        return perception.loseRange() - perception.detectionRange();
    }

    @Test
    void variesOneStrainWithoutDisturbingTheRest() {
        SimConfig config = SimConfig.defaults();

        SimConfig changed = config.withUnit(UnitKind.GRUNT, grunt -> grunt.withCount(1));

        assertThat(changed.unit(UnitKind.GRUNT).count()).isEqualTo(1);
        assertThat(changed.unit(UnitKind.BRUTE)).isEqualTo(config.unit(UnitKind.BRUTE));
    }

    /**
     * A carry shorter than the stride a sweep can leave means the clinch stops short of the commander
     * and the freeze plays across the gap it exists to close. Nothing downstream can notice: the
     * numbers are legal, the systems all work, and it only ever shows up as the beat looking wrong.
     */
    @Test
    void refusesAClinchCarryThatWouldStopShortOfTheCommander() {
        SimConfig config = SimConfig.defaults();

        assertThatThrownBy(() -> config.withMelee(config.melee().withClinchCarryDecay(HUGE_DECAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsACarryThatIsNeverStoppedAtAll() {
        SimConfig config = SimConfig.defaults();

        assertThat(config.withMelee(config.melee().withClinchCarryDecay(0f))
                        .melee()
                        .carryDistance())
                .isEqualTo(Float.MAX_VALUE);
    }
}
