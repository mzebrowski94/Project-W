package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.ArmourDef;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The sludger (G0073): the ant's armour moved to its back, so its face - the end it spits from - is the soft end.
 *
 * <p>The same armour every armoured strain has, centred behind it rather than ahead: a shot from behind glances
 * off, one in the face lands, and the flanks are open. What it asks of the player is the opposite of the ant -
 * stand in its line to hurt it.
 */
class SludgerTest {

    private static final float MIDDLE = 50f;

    /** Far enough off that it takes a long walk to reach him, so a test about a shot is only about that. */
    private static final float FAR_OFF = 40f;

    private static final float ARC = 140f;

    private static final float BEHIND = 180f;

    /** Close enough to its face or its back that a shot arrives before it has walked anywhere. */
    private static final float POINT_BLANK = 6f;

    private static final int UNTIL_THE_SHOT_ARRIVES = 12;

    /** It walks west towards him, so its back is east. */
    @Test
    void turnsAShotOffItsBack() {
        Simulation simulation = new Simulation(armouredBehind());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity sludger = simulation.spawnUnit(UnitKind.SLUDGER, MIDDLE, MIDDLE);
        steps(simulation, 2);

        simulation.spawnProjectile(at(sludger).x + POINT_BLANK, MIDDLE, -1f, 0f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(sludger.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void takesAShotInTheFace() {
        Simulation simulation = new Simulation(armouredBehind());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity sludger = simulation.spawnUnit(UnitKind.SLUDGER, MIDDLE, MIDDLE);
        steps(simulation, 2);

        simulation.spawnProjectile(at(sludger).x - POINT_BLANK, MIDDLE, 1f, 0f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(sludger.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** The arc leaves the flanks open, behind as in front. */
    @Test
    void takesAShotFromTheSide() {
        Simulation simulation = new Simulation(armouredBehind());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity sludger = simulation.spawnUnit(UnitKind.SLUDGER, MIDDLE, MIDDLE);
        steps(simulation, 2);

        // Where it will have walked to by the time the shot arrives: it is on its way west.
        simulation.spawnProjectile(at(sludger).x - POINT_BLANK * 0.5f, MIDDLE + POINT_BLANK, 0f, -1f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(sludger.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** Its armour alone: no claws, and no barrage firing back while the shots are on their way. */
    private static SimConfig armouredBehind() {
        return config().withUnit(
                        UnitKind.SLUDGER,
                        sludger -> sludger.withContactDamage(0)
                                .withHealth(ENEMY_HEALTH)
                                .withBarrage(BarrageDef.off())
                                .withArmour(new ArmourDef(true, ARC, BEHIND)));
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }
}
