package pl.mzebrows.pwt.sim.scenario;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A mission on one tier (G0065): the config's seed is the mission seed, and the {@link Mission} it builds is what
 * stands on the field - the biomes dealt into a strip, each populated by rules (G0068). Not in the menu registry:
 * reached by its code, as {@code mission <tier>}.
 *
 * <p>Holds the last mission its map was built for, so populating that world does not build it twice; a world
 * built for another seed builds its own.
 */
final class MissionScenario implements Scenario {

    private final TierKind tier;

    private @Nullable Mission built;

    MissionScenario(TierKind tier) {
        this.tier = tier;
    }

    /** What a mission on this tier is called, to a session and to the lobby. */
    static String nameOf(TierKind tier) {
        return "mission " + tier.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String name() {
        return nameOf(tier);
    }

    @Override
    public String description() {
        return "Four biomes dealt from the mission seed, each populated by rules, on "
                + tier.name().toLowerCase(Locale.ROOT) + ".";
    }

    /**
     * The tier folded in (G0063), and every strain put down unaware, as the Adventures put theirs: a march with
     * everything awake from the first tick is one fight that never stops.
     */
    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = base.mission().apply(tier, base);
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        return config;
    }

    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        Mission mission = Mission.build(config, tier, randomness);
        built = mission;
        return mission.laid().map();
    }

    /**
     * Points first and then nests, which are ground rather than things on it, then a shut door in every doorway of
     * every house and gatehouse (G0069); then the squad on the start; then everything else in the order placement
     * put it down.
     */
    @Override
    public void populate(Simulation simulation) {
        Mission mission = built;
        if (mission == null || mission.seed() != simulation.config().seed()) {
            mission = Mission.build(simulation.config(), tier);
        }
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Point) {
                simulation.spawnControlPoint(placed.x(), placed.y());
            }
        }
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Nest nest) {
                simulation.spawnNest(nest.strain(), nest.x(), nest.y());
            }
        }
        for (House house : mission.laid().houses()) {
            for (TileRect doorway : house.doorways()) {
                simulation.spawnDoor(doorway);
            }
        }
        deployPlayerSide(simulation);
        for (Placed placed : mission.placed()) {
            switch (placed) {
                case Placed.Point _, Placed.Nest _ -> {}
                case Placed.Loose loose -> simulation.spawnUnit(loose.strain(), loose.x(), loose.y());
                case Placed.Pickup pickup -> simulation.spawnPickup(pickup.kind(), pickup.x(), pickup.y());
                case Placed.Barrel barrel -> simulation.spawnBarrel(barrel.x(), barrel.y());
                case Placed.Boulder boulder -> simulation.spawnObstacle(boulder.x(), boulder.y());
                case Placed.Rabbit rabbit -> simulation.spawnRabbit(rabbit.x(), rabbit.y());
            }
        }
    }
}
