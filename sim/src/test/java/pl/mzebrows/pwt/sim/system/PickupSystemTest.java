package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.contesting;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A pickup is the first mechanic where two entities on different sides want the same object, so most
 * of what matters here is about exclusivity and about who gets what - not about the grant itself,
 * which {@code EvolutionSystemTest} covers.
 */
class PickupSystemTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    /** Close enough that both units overlap a pickup between them, far enough not to start inside it. */
    private static final float NEIGHBOUR_GAP = 1f;

    @Test
    void feedsAnEnemyThatEatsSomethingLyingOnTheField() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
    }

    @Test
    void takesAPickupOffTheFieldOnceSomethingHasTakenIt() {
        Simulation simulation = simulation();
        simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isZero();
    }

    /**
     * Two units reach the same mushroom on the same tick. Exactly one eats it, and which one is fixed
     * by the collision grid's order rather than by chance - so a replay of the same run agrees.
     */
    @Test
    void givesOnePickupToExactlyOneTaker() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnUnit(UnitKind.GRUNT, X - NEIGHBOUR_GAP, Y);
        Entity second = simulation.spawnUnit(UnitKind.GRUNT, X + NEIGHBOUR_GAP, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(first.getComponent(Feed.class).count + second.getComponent(Feed.class).count)
                .isEqualTo(1);
    }

    @Test
    void givesAUnitOnlyOnePickupPerTick() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X - NEIGHBOUR_GAP, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X + NEIGHBOUR_GAP, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * The commander walks over alien food and leaves it there. A side a pickup offers nothing does not
     * consume it, which is what stops one unit denying another an item it had no use for itself.
     */
    @Test
    void leavesAPickupStandingForASideItOffersNothing() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * The aliens have no use for a crate of men. Changed 2026-09-18: a supply drop used to be a meal
     * to whatever reached it first, which made it a race; it is now the player's alone and is left
     * standing by anything else that walks over it.
     */
    @Test
    void leavesASupplyDropStandingForAnAlien() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
        assertThat(grunt.getComponent(Feed.class).count).isZero();
    }

    /** And no use for a rack of guns it has no hands for. The same change, for the same reason. */
    @Test
    void leavesAWeaponsCacheStandingForAnAlien() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
        assertThat(grunt.getComponent(Feed.class).count).isZero();
    }

    /**
     * The contested case, and the reason a pickup carries a grant per side rather than a list of who
     * may take it: one object, read differently by each side. Nothing in the shipped table uses both
     * halves any more, so the world this runs in declares one that does - the mechanism is still the
     * design.
     */
    @Test
    void givesTheCommanderAndAnEnemyDifferentThingsForTheSamePickup() {
        Simulation rescued = contesting();
        rescued.spawnCommander(X, Y);
        rescued.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        Simulation eaten = contesting();
        Entity grunt = eaten.spawnUnit(UnitKind.GRUNT, X, Y);
        eaten.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        rescued.step();
        eaten.step();

        assertThat(count(rescued, Trooper.class)).isEqualTo(1);
        assertThat(count(eaten, Trooper.class)).isZero();
        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
    }

    /**
     * A grant that belongs to nobody in particular lands on the level entity, which is how the
     * simulation reaches level-scope state without the message queue G0004 rejected. Nothing reaches
     * the taker at all - {@code ReinforcementSystem} turns the level's count into a man the same
     * tick, which is what {@code ReinforcementSystemTest} is about.
     */
    @Test
    void landsALevelScopeGrantOnTheLevelRatherThanOnTheTaker() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        assertThat(commander.getComponent(Feed.class)).isNull();
        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
    }

    /** A body in its throes is scenery, and scenery does not go shopping. */
    @Test
    void letsNothingInItsDeathThroesTakeAnything() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Dying.class).begin();
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isZero();
        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * A sealed cache offers nobody anything (G0050): a trooper standing on it with no commander near
     * leaves it standing, sealed, and so does an alien. Otherwise it could be taken before it had shown
     * which gun it was.
     */
    @Test
    void leavesASealedCacheStandingForAnybodyWhoTouchesIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X + FAR_OUTSIDE_THE_REVEAL, Y);
        simulation.spawnTrooper(X, Y);
        simulation.spawnUnit(UnitKind.GRUNT, X, Y + NEIGHBOUR_GAP);
        Entity cache = simulation.spawnPickup(PickupKind.ARMOURY_SEALED, X, Y);

        simulation.step();

        assertThat(cache.getComponent(Pickup.class).kind).isEqualTo(PickupKind.ARMOURY_SEALED);
        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * Revealed on the tick a commander comes within the radius, and not the tick before (G0050): it
     * becomes a gun's cache in place, the same entity, looking like the cache it now is.
     */
    @Test
    void dealsASealedCacheItsGunOnTheTickACommanderComesNear() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X - AlphaWorlds.REVEAL_RADIUS - JUST, Y);
        Entity cache = simulation.spawnPickup(PickupKind.ARMOURY_SEALED, X, Y);

        simulation.step();
        PickupKind before = cache.getComponent(Pickup.class).kind;
        commander.getComponent(Position.class).set(X - AlphaWorlds.REVEAL_RADIUS + JUST, Y);
        simulation.step();

        assertThat(before).isEqualTo(PickupKind.ARMOURY_SEALED);
        PickupKind dealt = cache.getComponent(Pickup.class).kind;
        assertThat(simulation.config().pickup(dealt).armoury().inDeck()).isTrue();
        assertThat(cache.getComponent(Appearance.class).look)
                .isEqualTo(simulation.config().pickup(dealt).look());
    }

    /** A commander off his feet sees nothing to reveal: it is the man walking up to it who finds out. */
    @Test
    void keepsACacheSealedWhileTheOnlyCommanderNearIsDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y + AlphaWorlds.REVEAL_RADIUS - JUST);
        commander.getComponent(Vitality.class).begun = true;
        Entity cache = simulation.spawnPickup(PickupKind.ARMOURY_SEALED, X, Y);

        simulation.step();

        assertThat(cache.getComponent(Pickup.class).kind).isEqualTo(PickupKind.ARMOURY_SEALED);
    }

    /** Once dealt it is an ordinary weapons cache: the commander who walks onto it comes away armed. */
    @Test
    void armsTheCommanderWhoTakesARevealedCache() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        Entity cache = simulation.spawnPickup(PickupKind.ARMOURY_SEALED, X, Y);

        simulation.step();
        PickupKind dealt = cache.getComponent(Pickup.class).kind;
        simulation.step();

        Grant.Arm arm = (Grant.Arm) simulation.config().pickup(dealt).playerGrant();
        assertThat(commander.getComponent(Weapon.class).kind).isEqualTo(arm.weapon());
    }

    /**
     * The deck is a bag (G0050, G0039): the fixture holds one card of each gun, so eight caches revealed
     * together are every gun twice - never three of one and one of another, which independent draws
     * would often be.
     */
    @Test
    void dealsNoCardTwiceBeforeEveryCardHasComeUp() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        Entity[] caches = new Entity[EIGHT];
        for (int i = 0; i < EIGHT; i++) {
            caches[i] = simulation.spawnPickup(PickupKind.ARMOURY_SEALED, X + RING * (i - EIGHT / 2), Y + RING);
        }

        simulation.step();

        Map<PickupKind, Integer> dealt = new EnumMap<>(PickupKind.class);
        for (Entity cache : caches) {
            dealt.merge(cache.getComponent(Pickup.class).kind, 1, Integer::sum);
        }
        assertThat(dealt)
                .containsOnlyKeys(
                        PickupKind.ARMOURY_RAILGUN,
                        PickupKind.ARMOURY_FLAMER,
                        PickupKind.ARMOURY_SHOTGUN,
                        PickupKind.ARMOURY_LASER)
                .allSatisfy((kind, times) -> assertThat(times).isEqualTo(2));
    }

    /** Well outside a sealed cache's reveal radius. */
    private static final float FAR_OUTSIDE_THE_REVEAL = AlphaWorlds.REVEAL_RADIUS * 3f;

    /** Less than a tick's walk: either side of an edge. */
    private static final float JUST = 0.25f;

    /** Two full shuffles of the fixture's four-card deck. */
    private static final int EIGHT = 8;

    /** How far apart the caches in a row stand: past a commander's reach, inside the reveal. */
    private static final float RING = 1f;
}
