package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SQUAD_SIZE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * Where the player's side starts, and that it starts together.
 *
 * <p>The spawn area is the first piece of a level that is a <em>place</em> rather than a number, and
 * it is where G0009 is easiest to break by accident: a squad is the first time several things are
 * placed relative to one named patch of grid, and lining them up on tile centres would read as a grid
 * to the player instantly.
 */
class SquadTest {

    private static final int SPAWN_COLS = 2;
    private static final int SPAWN_ROWS = 5;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    @Test
    void putsTheWholeSquadOnTheFieldWithTheCommander() {
        Simulation simulation = deployed(config());

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE);
    }

    /** Squad size belongs to the scenario, not to the build: a level decides how many men you get. */
    @Test
    void spawnsAsManyTroopersAsTheScenarioAsksFor() {
        Simulation simulation = deployed(config().withTrooper(config().trooper().withSquadSize(7)));

        assertThat(count(simulation, Trooper.class)).isEqualTo(7);
    }

    @Test
    void startsTheSquadInTheSpawnArea() {
        Simulation simulation = deployed(config());
        TileMap map = simulation.map();
        TileRect area = simulation.config().spawn().on(map);

        for (Entity trooper : all(simulation, Trooper.class)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(area.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
        }
    }

    @Test
    void startsTheCommanderInTheSameAreaAsHisSquad() {
        Simulation simulation = deployed(config());
        TileMap map = simulation.map();
        TileRect area = simulation.config().spawn().on(map);

        Position at = all(simulation, Commander.class).get(0).getComponent(Position.class);

        assertThat(area.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
    }

    /** G0009. The grid chooses which tiles are used; it never decides where inside one anything stands. */
    @Test
    void standsNobodyAtATileCentre() {
        Simulation simulation = deployed(config());

        for (Entity trooper : all(simulation, Trooper.class)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(offsetInTile(at.x)).isNotCloseTo(TILE * 0.5f, TOLERANCE);
        }
    }

    @Test
    void spreadsTheSquadOutRatherThanStackingItInOnePlace() {
        Simulation simulation = deployed(config());

        ImmutableArray<Entity> squad = all(simulation, Trooper.class);
        for (int i = 0; i < squad.size(); i++) {
            for (int j = i + 1; j < squad.size(); j++) {
                assertThat(gap(squad.get(i), squad.get(j))).isGreaterThan(0f);
            }
        }
    }

    @Test
    void givesEveryTrooperABodySoItCanBeHit() {
        Simulation simulation = deployed(config());

        for (Entity trooper : all(simulation, Trooper.class)) {
            assertThat(trooper.getComponent(Body.class)).isNotNull();
            assertThat(trooper.getComponent(Body.class).radius).isGreaterThan(0f);
        }
    }

    /** A spawn area laid over rock places nobody in it rather than embedding the squad in a wall. */
    @Test
    void putsNoTrooperInsideRock() {
        Simulation simulation =
                deployed(config(), ground().rect(0, 0, 4, 20, Terrain.ROCK).build());

        for (Entity trooper : all(simulation, Trooper.class)) {
            Position at = trooper.getComponent(Position.class);
            assertThat(simulation.map().passableAt(at.x, at.y)).isTrue();
        }
    }

    /**
     * Promoted from a deferred invariant now that there is a squad: a trooper lost during a level
     * stays lost for it. Nothing brings one back, and nothing should - the squad is the resource
     * (pillar 3), and men you can lose without consequence are not a resource.
     */
    @Test
    void keepsATrooperLostDuringALevelLost() {
        Simulation simulation = deployed(config());
        Entity lost = all(simulation, Trooper.class).get(0);

        lost.getComponent(Damage.class).add(TROOPER_HEALTH * 2f, 0);
        steps(simulation, ticksIn(DEATH_THROES) + 30);

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE - 1);
    }

    /** The other promoted row. A level decides how many men you get, and nothing exceeds it. */
    @Test
    void neverPutsMoreTroopersOnTheFieldThanTheSquadAllows() {
        SimConfig base = config();
        Simulation simulation = deployed(base.withTrooper(base.trooper().withSquadSize(2)));

        assertThat(count(simulation, Trooper.class)).isLessThanOrEqualTo(2);
    }

    private static Simulation deployed(SimConfig config) {
        return deployed(config, ground().build());
    }

    private static Simulation deployed(SimConfig config, TileMap map) {
        Simulation simulation =
                new Simulation(config.withSpawn(new SimConfig.SpawnConfig(1, SPAWN_COLS, SPAWN_ROWS)), map);
        new Scenario() {
            @Override
            public String name() {
                return "deployment";
            }

            @Override
            public String description() {
                return "the player's side, put on the field";
            }

            @Override
            public void populate(Simulation world) {
                deployPlayerSide(world);
            }
        }.populate(simulation);
        return simulation;
    }

    private static ImmutableArray<Entity> all(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get());
    }

    private static int count(Simulation simulation, Class<? extends Component> marker) {
        return all(simulation, marker).size();
    }

    private static float offsetInTile(float world) {
        return world - (float) Math.floor(world / TILE) * TILE;
    }

    private static float gap(Entity first, Entity second) {
        Position a = first.getComponent(Position.class);
        Position b = second.getComponent(Position.class);
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy)
                - first.getComponent(Body.class).radius
                - second.getComponent(Body.class).radius;
    }
}
