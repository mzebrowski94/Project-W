package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaponType;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;
import pl.mzebrows.pwt.sim.system.upgrade.Target;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The numbers here that fail silently. Everything else in {@code SimConfig} is validated at
 * construction or is visible the moment it is wrong; the grid's cell size and the clinch carry are
 * neither - one lets units walk through each other, the other leaves a freeze playing across a gap.
 *
 * <p>Every relation a player learns holds on every tier's numbers too, folded as a mission folds them (G0063):
 * those tests run once off a mission and once a tier.
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
    @ParameterizedTest
    @MethodSource("everyTier")
    void letsGoOfHimSoonerThanAnythingElseOnTheFieldDoes(SimConfig config) {

        float frog = leeway(config, UnitKind.FROG);

        for (UnitKind kind : UnitKind.values()) {
            // A strain that slithers never hunts, so it has nobody to give up on (G0095).
            if (kind != UnitKind.FROG && !config.unit(kind).slither().enabled()) {
                assertThat(frog)
                        .as("a frog must give up sooner than a %s", kind)
                        .isLessThan(leeway(config, kind));
            }
        }
    }

    /** The same, with the swarm's perception moved as far as every adaptation takes it, through the game's fold. */
    @ParameterizedTest
    @MethodSource("everyTier")
    void letsGoOfHimSoonerThanAnythingElseOnTheFieldDoesWithEveryAdaptationAtItsHighestRank(SimConfig config) {
        Simulation world = everythingHeld(config);
        Modifiers modifiers = new Modifiers(world.engine(), config);

        float frog = leeway(config, UnitKind.FROG, world, modifiers);

        for (UnitKind kind : UnitKind.values()) {
            // A strain that slithers never hunts, so it has nobody to give up on (G0095).
            if (kind != UnitKind.FROG && !config.unit(kind).slither().enabled()) {
                assertThat(frog)
                        .as("a frog must give up sooner than a %s", kind)
                        .isLessThan(leeway(config, kind, world, modifiers));
            }
        }
    }

    /**
     * <strong>A lose range is never shorter than the detection range</strong> (glossary) - or a unit standing
     * between the two would notice him and give up on alternate ticks - at every rank an adaptation takes either.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void neverGivesUpInsideTheRangeItNoticesHimAtWithEveryAdaptationAtItsHighestRank(SimConfig config) {
        Simulation world = everythingHeld(config);
        Modifiers modifiers = new Modifiers(world.engine(), config);

        for (UnitKind kind : UnitKind.values()) {
            assertThat(leeway(config, kind, world, modifiers)).as("%s", kind).isNotNegative();
        }
    }

    /**
     * <strong>The hand strike deletes an ordinary enemy and staggers a brute</strong> - the contrast the
     * whole parry is built on (G0013). It is two numbers in two blocks, and it was quietly broken for
     * as long as a grunt's health had been raised past the strike's damage: the sweep landed, the grunt
     * stood, and its claws killed him on the next tick.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void deletesAnOrdinaryEnemyWithOneSwingAndNotABrute(SimConfig config) {

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
     * can carry: a turret's gun runs hot too and nobody carries one away. And of the guns that do not reload:
     * one whose stop holds until its bar is empty - the bazooka, the disc gun, the shotgun (G0058, G0079) - is
     * reloading, not being held down, and is never the gun anybody holds instead of the beam.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void runsHotterThanAnyOtherGunAManCanCarry(SimConfig config) {

        float beam = secondsOfHolding(config, WeaponKind.LASER);

        for (PickupKind kind : PickupKind.values()) {
            if (config.pickup(kind).playerGrant() instanceof Grant.Arm crate
                    && crate.weapon() != WeaponKind.LASER
                    && !reloads(config, crate.weapon())) {
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
    @ParameterizedTest
    @MethodSource("everyTier")
    void runsHotterThanAnyOtherGunAManCanCarryWithEveryUpgradeAtItsHighestRank(SimConfig config) {
        Simulation world = everythingHeld(config);
        Entity commander = world.spawnCommander(10f, 10f, 0);
        Modifiers modifiers = new Modifiers(world.engine(), config);

        float beam = secondsOfHolding(config, WeaponKind.LASER, modifiers, commander);

        for (PickupKind kind : PickupKind.values()) {
            if (config.pickup(kind).playerGrant() instanceof Grant.Arm crate
                    && crate.weapon() != WeaponKind.LASER
                    && !reloads(config, crate.weapon())) {
                assertThat(beam)
                        .as("the beam must stop itself sooner than the %s does", crate.weapon())
                        .isLessThan(secondsOfHolding(config, crate.weapon(), modifiers, commander));
            }
        }
    }

    /** What the pulse rifle kills in this many bolts or fewer it kills rather than holds: no stun owed (G0095). */
    private static final int KILLED_NOT_HELD = 3;

    /**
     * <strong>The pulse rifle stuns before it kills</strong> - anything it does not kill in three bolts anyway. What
     * the gun is for is holding something still for the squad, so a trigger held on any strain shock builds on - every bolt landing, the
     * shock draining between them as it does in play - fills it before the bolts have emptied its
     * health. Read through the one {@code Shock} the game builds and drains, at the gun's own cadence.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void stunsWithThePulseRifleBeforeItKills(SimConfig config) {
        WeaponDef gun = config.weapon(WeaponKind.PULSE);
        int ticksBetween = Ticks.of(gun.cooldownSeconds(), config.tickSeconds());

        for (UnitKind kind : UnitKind.values()) {
            if (!config.unit(kind).shockable()) {
                continue;
            }
            int boltsToKill = (config.healthOf(kind) + gun.damage() - 1) / gun.damage();
            if (boltsToKill <= KILLED_NOT_HELD) {
                // Dead in three bolts or fewer - the easy tier's runner since 2026-11-15 (G0095): nothing to hold.
                continue;
            }
            float worth = config.shock().perHitAgainst(config.unit(kind).radius(), gun.shockPerHit());
            Shock shock = new Shock();
            int bolts = 1;
            while (!shock.build(worth) && bolts < boltsToKill) {
                shock.drain(config.shock().drainPerSecond(), (ticksBetween - 1) * config.tickSeconds());
                bolts++;
            }

            assertThat(bolts)
                    .as("a held pulse rifle must stun the %s before it kills it", kind)
                    .isLessThan(boltsToKill);
        }
    }

    /**
     * <strong>The pulse rifle stuns by the size of the body</strong>: about two bolts at the smallest, the leech, to
     * about ten at the largest it stuns, the giants - and more for every step up in between.
     */
    @Test
    void stunsASmallBodyInAFewBoltsAndABigOneInManyMore() {
        SimConfig config = SimConfig.defaults();
        WeaponDef gun = config.weapon(WeaponKind.PULSE);

        float leech =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.LEECH).radius(), gun.shockPerHit());
        float grunt =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.GRUNT).radius(), gun.shockPerHit());
        float brute =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.BRUTE).radius(), gun.shockPerHit());
        float searer =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.SEARER).radius(), gun.shockPerHit());

        assertThat(leech).isCloseTo(2f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(grunt).isGreaterThan(leech).isLessThan(brute);
        assertThat(brute).isLessThan(searer);
        assertThat(searer).isCloseTo(10f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void buildsShockOnTheSnakeAsOnAGrunt() {
        SimConfig config = SimConfig.defaults();
        WeaponDef gun = config.weapon(WeaponKind.PULSE);

        float snake =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.SNAKE).radius(), gun.shockPerHit());
        float grunt =
                1f / config.shock().perHitAgainst(config.unit(UnitKind.GRUNT).radius(), gun.shockPerHit());

        assertThat(config.unit(UnitKind.SNAKE).shockable())
                .as("only a declared stun holds the snake (G0095), so it must take the shock that declares one")
                .isTrue();
        assertThat(snake).isCloseTo(grunt, org.assertj.core.data.Offset.offset(0.01f));
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
    @ParameterizedTest
    @MethodSource("everyTier")
    void keepsTheSquadSlowerThanTheCommanderAtEveryUpgradesHighestRank(SimConfig config) {
        Simulation world = everythingHeld(config);
        world.spawnCommander(10f, 10f, 0);
        Entity trooper = world.spawnTrooper(20f, 10f);

        float fastest = config.trooper().speed() * new Modifiers(world.engine(), config).pace(trooper, 1f);

        assertThat(fastest).isLessThan(config.commander().speed());
    }

    /**
     * <strong>No alien stands off to shoot from further than a trooper sees</strong> (ticket 18): his sight came down
     * to bring the fight closer, never so far that a squad holding a point is outranged by what it is there to clear.
     * What walks and shoots - a ranged strain's stand-off, a giant's barrage - starts inside it.
     */
    @Test
    void seesFurtherThanAnyAlienStandsOffToShootFrom() {
        SimConfig config = SimConfig.defaults();
        float sight = config.trooper().perception().detectionRange();
        for (UnitKind kind : UnitKind.values()) {
            UnitDef def = config.units().of(kind);
            if (def.ranged().enabled()) {
                assertThat(def.ranged().range()).as("%s's stand-off", kind).isLessThan(sight);
            }
            if (def.barrage().enabled() && !def.rooted()) {
                assertThat(def.barrage().reach()).as("%s's barrage", kind).isLessThan(sight);
            }
        }
    }

    /**
     * <strong>A thicket has to cost his side more than it costs theirs.</strong> Ground that slowed
     * everybody equally is scenery - both sides arrive at the fight in the order they would have on
     * open ground and the only thing that changed is how long the walk took. The asymmetry is the
     * whole mechanic and it is two numbers in one table, so nothing else in the game would notice
     * somebody evening them up while tuning how thick the undergrowth feels.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void costsThePlayersSideMoreToWadeThroughThanItCostsTheAliens(SimConfig config) {
        GroundDef thicket = config.ground(Terrain.THICKET);

        assertThat(thicket.playerFactor()).isLessThan(thicket.alienFactor());
        assertThat(thicket.matters()).isTrue();
    }

    /**
     * <strong>The disc gun reloads a little sooner than the bazooka.</strong> Both run their bar to the stop
     * with every pull, so the wait for the next is the bar cooling from full to where it comes back.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void reloadsTheDiscGunSoonerThanTheBazooka(SimConfig config) {

        assertThat(stopsAfterOnePull(config, WeaponKind.DISC_GUN)).isTrue();
        assertThat(secondsToReload(config, WeaponKind.DISC_GUN))
                .isLessThan(secondsToReload(config, WeaponKind.BAZOOKA));
    }

    /**
     * <strong>The shotgun's bar is worth four pulls fired close together</strong> (G0079): the fourth stops
     * it and the third never does - at its own cadence, and at the shorter one every upgrade at its highest
     * rank gives it, where less comes off between pulls. Read through the one {@code Heat} the game fills and
     * cools, a tick at a time.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void stopsTheShotgunOnTheFourthPullFiredCloseTogether(SimConfig config) {
        Simulation world = everythingHeld(config);
        Entity commander = world.spawnCommander(10f, 10f, 0);
        Modifiers modifiers = new Modifiers(world.engine(), config);
        int base = Ticks.of(config.weapon(WeaponKind.SHOTGUN).cooldownSeconds(), config.tickSeconds());

        assertThat(pullsBeforeTheStop(config, WeaponKind.SHOTGUN, base)).isEqualTo(4);
        assertThat(pullsBeforeTheStop(
                        config, WeaponKind.SHOTGUN, modifiers.fireCooldownTicks(commander, WeaponKind.SHOTGUN, base)))
                .isEqualTo(4);
    }

    /**
     * <strong>Once stopped, the shotgun reloads</strong> (G0079): the bar must cool all the way down before
     * it fires again, as the bazooka's does, and the pull that hits harder kicks him lightly and shakes
     * nothing. A pull at the muzzle kills a grunt outright.
     */
    @ParameterizedTest
    @MethodSource("everyTier")
    void reloadsTheShotgunFromEmptyWithALightKickAndNoShake(SimConfig config) {
        WeaponDef shotgun = config.weapon(WeaponKind.SHOTGUN);

        assertThat(reloads(config, WeaponKind.SHOTGUN)).isTrue();
        assertThat(shotgun.kicks()).isTrue();
        assertThat(shotgun.recoilSpeed() * shotgun.recoilSeconds())
                .isLessThan(config.weapon(WeaponKind.DISC_GUN).recoilSpeed()
                        * config.weapon(WeaponKind.DISC_GUN).recoilSeconds());
        assertThat(shotgun.shake().strength()).isZero();
        assertThat(shotgun.damage() * shotgun.pellets()).isGreaterThanOrEqualTo(config.healthOf(UnitKind.GRUNT));
    }

    /**
     * Every gun a man can be armed with - the sidearm and whatever a crate holds - has a weapon type, so
     * whatever names a type reaches every gun he can carry; nothing found falls through it as none.
     */
    @Test
    void givesEveryGunAManCanBeArmedWithAWeaponType() {
        SimConfig config = SimConfig.defaults();

        assertThat(config.weapon(WeaponKind.SIDEARM).type()).isNotEqualTo(WeaponType.NONE);
        for (PickupKind kind : PickupKind.values()) {
            if (config.pickup(kind).playerGrant() instanceof Grant.Arm crate) {
                assertThat(config.weapon(crate.weapon()).type())
                        .as("the %s must have a weapon type", crate.weapon())
                        .isNotEqualTo(WeaponType.NONE);
            }
        }
    }

    /** What ships, as every level but a mission plays it, and as a mission plays it on each tier. */
    static Stream<Named<SimConfig>> everyTier() {
        SimConfig shipped = SimConfig.defaults();
        return Stream.concat(
                Stream.of(Named.of("off a mission", shipped)),
                Stream.of(TierKind.values())
                        .map(tier -> Named.of(
                                tier.name().toLowerCase(Locale.ROOT),
                                shipped.mission().apply(tier, shipped))));
    }

    private static float secondsToReload(SimConfig config, WeaponKind kind) {
        WeaponDef gun = config.weapon(kind);
        return Math.max(
                gun.cooldownSeconds(),
                (gun.heat().perShot() - gun.heat().resumeBelow()) / gun.heat().coolPerSecond());
    }

    /** True for a gun one pull runs to the stop: its heat bar is its reload rather than a burst. */
    private static boolean stopsAfterOnePull(SimConfig config, WeaponKind kind) {
        return config.weapon(kind).heat().perShot() >= 1f;
    }

    /**
     * True for a gun whose stop holds until its bar is empty (G0058, G0079): the wait is a reload rather than a
     * gun being held down - the bazooka, the disc gun, the shotgun.
     */
    private static boolean reloads(SimConfig config, WeaponKind kind) {
        WeaponDef gun = config.weapon(kind);
        return gun.overheats() && gun.heat().resumeBelow() <= 0f;
    }

    /**
     * How many pulls a gun fired as soon as it is ready makes before it stops itself, cooling a tick at a time
     * for {@code ticksBetween} ticks after each; a large number for one that never stops.
     */
    private static int pullsBeforeTheStop(SimConfig config, WeaponKind kind, int ticksBetween) {
        WeaponDef gun = config.weapon(kind);
        Heat heat = new Heat();
        int pulls = 0;
        while (!heat.locked && pulls < 1000) {
            heat.spend(gun.heat().perShot());
            pulls++;
            for (int tick = 0; tick < ticksBetween && !heat.locked; tick++) {
                heat.cool(gun.heat().coolPerSecond(), gun.heat().resumeBelow(), config.tickSeconds());
            }
        }
        return pulls;
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

    /** The same for one of the strain standing in {@code world}, both ranges folded as the game reads them. */
    private static float leeway(SimConfig config, UnitKind kind, Simulation world, Modifiers modifiers) {
        PerceptionDef perception = config.unit(kind).perception();
        Entity unit = world.spawnUnit(kind, 10f, 10f);
        return modifiers.loseRange(unit, perception.loseRange())
                - modifiers.detectionRange(unit, perception.detectionRange());
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

    /**
     * <strong>A giant is a brute grown</strong> (G0071): half as big again, four times its health, slower, no
     * leap, and quicker to let you go - but never sooner than a frog, which the test above holds.
     */
    @Test
    void makesEachGiantABruteGrownHalfAsBigAgainAndFourTimesAsHard() {
        SimConfig config = SimConfig.defaults();
        UnitDef brute = config.units().of(UnitKind.BRUTE);
        for (UnitKind giant : List.of(UnitKind.SEARER, UnitKind.THUMPER)) {
            UnitDef def = config.units().of(giant);
            assertThat(def.radius()).as("%s's size", giant).isCloseTo(brute.radius() * 1.5f, Offset.offset(0.01f));
            assertThat(def.health()).as("%s's health", giant).isEqualTo(brute.health() * 4);
            assertThat(def.speed()).as("%s's pace", giant).isLessThan(brute.speed());
            assertThat(def.leap().enabled()).as("%s's leap", giant).isFalse();
            assertThat(leeway(config, giant)).as("%s's leeway", giant).isLessThan(leeway(config, UnitKind.BRUTE));
            assertThat(def.barrage().holdsStill()
                            && def.barrage().stunCancels()
                            && def.barrage().roar().shakes())
                    .as("%s stands, roars and is stunned out of it", giant)
                    .isTrue();
        }
    }

    /** A giant still walks every passage the strip leaves: two tiles of clearance, ten units (G0062, G0071). */
    @Test
    void letsAGiantThroughTheNarrowestPassageAMissionLeaves() {
        SimConfig config = SimConfig.defaults();
        float narrowest = config.mission().ground().clearance() * config.map().tileSize();
        for (UnitKind giant : List.of(UnitKind.SEARER, UnitKind.THUMPER)) {
            assertThat(config.units().of(giant).radius() * 2f)
                    .as("%s across", giant)
                    .isLessThan(narrowest);
        }
    }

    /** "Its range twice its size": the slam's circle has a radius of twice the thumper's width. */
    @Test
    void slamsACircleWhoseRadiusIsTwiceTheThumpersWidthAndStartsOnlyWithSomebodyInIt() {
        SimConfig config = SimConfig.defaults();
        UnitDef thumper = config.units().of(UnitKind.THUMPER);
        WeaponDef slam = config.weapons().of(WeaponKind.SLAM);
        float reach = thumper.radius() + config.muzzleGap() + slam.projectileRadius() * 2f + slam.projectileRange();

        assertThat(reach).isCloseTo(thumper.radius() * 4f, Offset.offset(0.01f));
        assertThat(thumper.barrage().reach()).isLessThan(reach);
        assertThat(slam.harm()).isEqualTo(Harm.WOUND);
        assertThat(slam.stunSeconds()).isEqualTo(2f);
    }

    /**
     * A searer starts its cone only where the cone lands, beam after beam rather than in volleys, each thrown inside
     * a 75-degree cone; every beam wounds and slows.
     */
    @Test
    void searsOnlyWhereItsConeLands() {
        SimConfig config = SimConfig.defaults();
        UnitDef searer = config.units().of(UnitKind.SEARER);
        WeaponDef sear = config.weapons().of(WeaponKind.SEAR);

        assertThat(searer.barrage().reach()).isLessThan(searer.radius() + sear.projectileRange());
        assertThat(sear.harm()).isEqualTo(Harm.WOUND);
        assertThat(sear.slowFactor()).isLessThan(1f);
        assertThat(sear.bloatPerShot()).isZero();
        assertThat(searer.barrage().fan().shots()).isEqualTo(1);
        assertThat(searer.barrage().fan().salvos()).isGreaterThan(1);
        assertThat(sear.halfSpreadRadians() * 2f).isCloseTo((float) Math.toRadians(75.0), Offset.offset(1e-4f));
    }

    /**
     * The sludger (G0073): armoured behind, a locked, stepped line of single globs - a trail of glue from its mouth to
     * whoever it aimed at - that starts only where it lands; a glob
     * wounds the man it hits, and the glue it leaves only slows - no damage, no harm, no shove.
     */
    @Test
    void spitsALockedLineThatWoundsOnTheHitAndLeavesGlueThatOnlySlows() {
        SimConfig config = SimConfig.defaults();
        UnitDef sludger = config.units().of(UnitKind.SLUDGER);
        WeaponDef glob = config.weapons().of(WeaponKind.GLOB);

        assertThat(sludger.armour().centreDegrees()).isEqualTo(180f);
        assertThat(sludger.barrage().stream().locked()).isTrue();
        assertThat(sludger.barrage().stream().stepped()).isTrue();
        // Overlapping circles of glue down the whole reach: a trail, not a dotted line.
        assertThat(sludger.barrage().reach() / sludger.barrage().stream().salvos())
                .isLessThan(glob.slicks().radius() * 2f);
        assertThat(sludger.barrage().stream().shots()).isEqualTo(1);
        assertThat(sludger.barrage().reach()).isLessThan(sludger.radius() + glob.projectileRange());
        assertThat(glob.harm()).isEqualTo(Harm.WOUND);
        assertThat(glob.slicks().damage()).isZero();
        assertThat(glob.slicks().harm()).isEqualTo(Harm.NONE);
        assertThat(glob.slicks().knockbackSpeed()).isZero();
        assertThat(glob.slicks().slowFactor()).isLessThan(1f);
    }

    /**
     * The slinger (G0074): a brute's body grown, carrying runners, that notices as far off as it can sling - one that
     * saw a man only inside its range would never use the far half of it - and lands a rider behind him, harmless a
     * moment, never as long as it takes the rider to reach him again.
     */
    @Test
    void slingsRunnersFromAsFarAsItCanNoticeAndLandsThemBehindHim() {
        SimConfig config = SimConfig.defaults();
        UnitDef slinger = config.units().of(UnitKind.SLINGER);
        UnitDef brute = config.units().of(UnitKind.BRUTE);

        assertThat(slinger.radius()).isGreaterThan(brute.radius());
        assertThat(slinger.health()).isGreaterThan(brute.health());
        assertThat(slinger.sling().rider()).isEqualTo(UnitKind.RUNNER);
        assertThat(slinger.sling().riders()).isPositive();
        assertThat(slinger.perception().detectionRange())
                .isGreaterThanOrEqualTo(slinger.sling().maxRange());
        assertThat(slinger.sling().minRange()).isGreaterThan(slinger.radius() + config.muzzleGap());
        assertThat(slinger.sling().landBehind()).isPositive();
        assertThat(slinger.sling().landingSeconds())
                .isLessThan(slinger.sling().landBehind()
                        / config.units().of(UnitKind.RUNNER).speed());
        assertThat(slinger.leap().enabled()).isTrue();
    }

    /**
     * The leech (G0083): it never walks, clings by touch and never pins, is spent on finishing him, and one that has
     * hold of nobody never goes off by itself - it waits for a man to pass (G0086).
     */
    @Test
    void slingsLeechesOntoHimThatClingAndWaitOnTheGroundForAManToPass() {
        SimConfig config = SimConfig.defaults();
        UnitDef leech = config.units().of(UnitKind.LEECH);
        UnitDef slinger = config.units().of(UnitKind.LEECH_SLINGER);

        assertThat(leech.rooted()).isTrue();
        assertThat(leech.grip().byTouch()).isTrue();
        assertThat(leech.grip().pins()).isFalse();
        assertThat(leech.grip().spentOnFinish()).isTrue();
        assertThat(leech.deathSlicks()).isPositive();
        assertThat(leech.fuse().seconds()).isZero();
        assertThat(leech.leap().enabled()).isTrue();
        assertThat(leech.leap().triggerRange())
                .isLessThanOrEqualTo(leech.perception().detectionRange());
        assertThat(slinger.sling().rider()).isEqualTo(UnitKind.LEECH);
        assertThat(slinger.sling().landBehind()).isZero();
        assertThat(slinger.sling().landingSeconds()).isZero();
    }

    /**
     * The magma spitter (ticket 16, G0091, provisional, for review): a spitter in body and in how it fights, whose spit
     * goes off where it lands in a blast smaller than a charged spit's - so it leaves fire (G0035) and is drawn as
     * carrying a gun that does - a little tougher and slower to fire, so the fire is what it adds, not the numbers.
     */
    @Test
    void makesTheMagmaSpitterASpitterWhoseSpitLeavesFire() {
        SimConfig config = SimConfig.defaults();
        UnitDef spitter = config.units().of(UnitKind.SPITTER);
        UnitDef magma = config.units().of(UnitKind.MAGMA_SPITTER);
        WeaponDef spit = config.weapons().of(spitter.ranged().gun());
        WeaponDef magmaSpit = config.weapons().of(magma.ranged().gun());

        assertThat(magma.radius()).isEqualTo(spitter.radius());
        assertThat(magma.ranged().range()).isEqualTo(spitter.ranged().range());
        assertThat(magma.ranged().closeRange()).isEqualTo(spitter.ranged().closeRange());
        assertThat(magma.ranged().holdsGround()).isTrue();
        assertThat(magma.ranged().haltsToFire()).isTrue();
        assertThat(magma.health()).isGreaterThan(spitter.health());
        assertThat(magmaSpit.blastRadius())
                .isPositive()
                .isLessThan(config.weapons().of(WeaponKind.CHARGED_SPIT).blastRadius());
        assertThat(magmaSpit.cooldownSeconds()).isGreaterThan(spit.cooldownSeconds());
        assertThat(magma.look()).isNotEqualTo(spitter.look());
    }
}
