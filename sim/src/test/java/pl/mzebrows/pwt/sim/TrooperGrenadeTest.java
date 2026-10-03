package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * A trooper throws only where the blast is the enemy's (G0051). Every trooper carries the throw from
 * spawn and it is off at zero; Explosives turns it up, and then five rules decide the moment - a pack
 * where it lands, nobody of ours near it, a clear path, his own throw ready and the squad's gap run out.
 *
 * <p>The pack barely moves (grunts at a crawl - not rooted, which would make them scenery a grenade comes
 * off), so where a throw would land is where they are.
 */
class TrooperGrenadeTest {

    private static final float Y = 50f;

    /** Where the man stands, facing the pack. */
    private static final float MAN_X = 30f;

    /** Where his throw comes down: his body, the grenade's, the gap it leaves the hand by, and its range. */
    private static final float LANDING_X =
            MAN_X + AlphaWorlds.TROOPER_RADIUS + AlphaWorlds.GRENADE_RADIUS + 0.05f + AlphaWorlds.GRENADE_RANGE;

    /** Well back from the fight, far outside the blast and its margin. */
    private static final float COMMANDER_X = 15f;

    /** How long a test gives a man to see the pack and throw: well inside his own cooldown and the gap. */
    private static final int TICKS = 30;

    @Test
    void neverThrowsWithTheChanceAtZeroAndDealsNothing() {
        Simulation simulation = world();
        Entity man = simulation.spawnTrooper(MAN_X, Y);
        pack(simulation, 3);

        boolean threw = watch(simulation);

        assertThat(threw).isFalse();
        assertThat(man.getComponent(Trooper.class).throwDue).isZero();
    }

    @Test
    void throwsIntoAPackOnceExplosivesIsHeld() {
        Simulation simulation = world();
        simulation.spawnTrooper(MAN_X, Y);
        pack(simulation, 3);
        explosives(simulation);

        assertThat(watch(simulation)).isTrue();
    }

    /** Read at the seam, so a man who lands after the upgrade throws like one who was there (ADR 0017). */
    @Test
    void throwsTheSameWhenHeArrivesAfterExplosives() {
        Simulation simulation = world();
        explosives(simulation);
        simulation.step();
        simulation.spawnTrooper(MAN_X, Y);
        pack(simulation, 3);

        assertThat(watch(simulation)).isTrue();
    }

    @Test
    void holdsHisGrenadeWithoutAClusterWhereItWouldLand() {
        Simulation simulation = world();
        simulation.spawnTrooper(MAN_X, Y);
        pack(simulation, AlphaWorlds.MIN_CLUSTER - 1);
        explosives(simulation);

        assertThat(watch(simulation)).isFalse();
    }

    /** The commander standing where the blast plus its margin reaches: no throw, however good the pack. */
    @Test
    void holdsHisGrenadeWhenTheCommanderIsNearWhereItWouldLand() {
        Simulation simulation = new Simulation(config());
        simulation.spawnCommander(LANDING_X, Y + NEAR_THE_BLAST);
        simulation.spawnTrooper(MAN_X, Y);
        pack(simulation, 3);
        explosives(simulation);

        assertThat(watch(simulation)).isFalse();
    }

    /** A man down on the ground there counts as much as one standing (G0051: a casualty included). */
    @Test
    void holdsHisGrenadeWhenACasualtyLiesNearWhereItWouldLand() {
        Simulation simulation = world();
        simulation.spawnTrooper(MAN_X, Y);
        Entity casualty = simulation.spawnTrooper(LANDING_X, Y - NEAR_THE_BLAST);
        casualty.getComponent(Vitality.class).begun = true;
        pack(simulation, 3);
        explosives(simulation);

        assertThat(watch(simulation)).isFalse();
    }

    /**
     * Scenery across the throw would turn it somewhere nobody chose (G0019): no throw. A boulder just off
     * the line he shoots along - clear of his shot, so he still fights, but in the way of a grenade made
     * wider than a bullet. On the line itself it would stop him fighting at all, and prove nothing.
     */
    @Test
    void holdsHisGrenadeWhenScenerySitsAcrossItsPath() {
        SimConfig config = config();
        config = config.withGrenade(config.grenade().withRadius(WIDE_GRENADE));
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(COMMANDER_X, Y);
        simulation.spawnTrooper(MAN_X, Y);
        simulation.spawnObstacle((MAN_X + LANDING_X) / 2f, Y + JUST_OFF_THE_LINE);
        pack(simulation, 3);
        explosives(simulation);

        assertThat(watch(simulation)).isFalse();
    }

    /** A grenade a whole unit wider than a shot. */
    private static final float WIDE_GRENADE = AlphaWorlds.PROJECTILE_RADIUS + 1f;

    /** Between the shot's clearance of a boulder and the wide grenade's. */
    private static final float JUST_OFF_THE_LINE = AlphaWorlds.OBSTACLE_RADIUS + AlphaWorlds.PROJECTILE_RADIUS + 0.5f;

    /** Two men see the same pack; one grenade goes, and the other waits out the squad's gap. */
    @Test
    void throwsAtMostOneGrenadePerSquadGap() {
        Simulation simulation = world();
        simulation.spawnTrooper(MAN_X, Y - 1f);
        simulation.spawnTrooper(MAN_X, Y + 1f);
        pack(simulation, 3);
        explosives(simulation);

        int thrown = 0;
        for (int i = 0; i < AlphaWorlds.ticksIn(AlphaWorlds.SQUAD_GAP_SECONDS) - 1; i++) {
            int before = count(simulation, Thrown.class);
            simulation.step();
            thrown += Math.max(0, count(simulation, Thrown.class) - before);
        }

        assertThat(thrown).isEqualTo(1);
    }

    /**
     * Past the blast but inside its margin, off the line it flies - with room to spare for the throw's
     * bearing, which is at whichever of the pack he is shooting.
     */
    private static final float NEAR_THE_BLAST = AlphaWorlds.GRENADE_BLAST_RADIUS + 1f;

    /** Grunts that stay about where they are put, and are still on their legs. */
    private static SimConfig config() {
        SimConfig config = AlphaWorlds.config();
        return config.withUnit(UnitKind.GRUNT, grunt -> grunt.withSpeed(CRAWL));
    }

    /** Slow enough that a pack is where it was put for the whole of a test. */
    private static final float CRAWL = 0.01f;

    /** The commander well back, so the squad has somebody to belong to and he is nowhere near the pack. */
    private static Simulation world() {
        Simulation simulation = new Simulation(config());
        simulation.spawnCommander(COMMANDER_X, Y);
        return simulation;
    }

    /**
     * A pack of this many, in a row along the line of the throw and round where it comes down, so
     * whichever of them he shoots at, he shoots along that line.
     */
    private static void pack(Simulation simulation, int size) {
        for (int i = 0; i < size; i++) {
            simulation.spawnUnit(UnitKind.GRUNT, LANDING_X + (i - size / 2) * PACK_SPACING, Y);
        }
    }

    /** Apart, and all within the blast of the middle one. */
    private static final float PACK_SPACING = 2.5f;

    private static void explosives(Simulation simulation) {
        DraftTest.ledger(simulation).held.add(new Upgrades.Held(UpgradeKind.EXPLOSIVES, Upgrades.SHARED, 1));
    }

    /** Steps a while and says whether any grenade was ever in the air. */
    private static boolean watch(Simulation simulation) {
        for (int i = 0; i < TICKS; i++) {
            simulation.step();
            if (count(simulation, Thrown.class) > 0) {
                return true;
            }
        }
        return false;
    }
}
