package pl.mzebrows.pwt.client.play;

import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.StripFaults;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.PlacementRules;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.scenario.Mission;
import pl.mzebrows.pwt.sim.scenario.Placed;

/**
 * What a built mission may never show - its strip's faults ({@link StripFaults}) and placement's (G0068) - each
 * check a list of faults, empty when it holds, so the sweep reports every seed and every fault at once. Distances
 * are crow-flies, the measure placement keeps them by; a distance from a way in is from the nearest point of it.
 */
final class MissionFaults {

    private MissionFaults() {}

    /** Every check. */
    static List<String> all(Mission mission, SimConfig config) {
        List<String> faults = new ArrayList<>();
        faults.addAll(StripFaults.all(
                mission.laid(), mission.grounds(config), config.mission().ground()));
        faults.addAll(mission.shortfalls());
        faults.addAll(reach(mission));
        faults.addAll(kept(mission));
        faults.addAll(houses(mission));
        faults.addAll(start(mission, config.mission().placement()));
        faults.addAll(nests(mission, config.mission().placement()));
        faults.addAll(bands(mission, config));
        faults.addAll(crates(mission, config));
        return faults;
    }

    /** Every point and pickup - a house's stock among them - stands on ground reached from the start. */
    static List<String> reach(Mission mission) {
        TileMap map = mission.laid().map();
        TileRect start = start(mission);
        boolean[] reached = Maps.reachableFrom(map, start.col(), start.row());
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Point || placed instanceof Placed.Pickup) {
                int col = map.colAt(placed.x());
                int row = map.rowAt(placed.y());
                if (!map.inBounds(col, row) || !reached[row * map.cols() + col]) {
                    faults.add(placed + " is not reached from the start");
                }
            }
        }
        return faults;
    }

    /** Nothing on a tile the strip kept - the start, an apron, a doorstep - but a house's stock inside it. */
    static List<String> kept(Mission mission) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            int col = map.colAt(placed.x());
            int row = map.rowAt(placed.y());
            boolean stock = placed instanceof Placed.Pickup && inAHouse(mission, col, row);
            if (mission.laid().kept(col, row) && !stock) {
                faults.add(placed + " stands on kept ground at (" + col + "," + row + ")");
            }
        }
        return faults;
    }

    /** No point, nest or creature in a house: a house is a cache you open. */
    static List<String> houses(Mission mission) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            boolean barred =
                    placed instanceof Placed.Point || placed instanceof Placed.Nest || placed instanceof Placed.Loose;
            if (barred && inAHouse(mission, map.colAt(placed.x()), map.rowAt(placed.y()))) {
                faults.add(placed + " is in a house");
            }
        }
        return faults;
    }

    /** Nothing hostile within {@code startKeepOff} of the start, so nothing wakes on the first tick. */
    static List<String> start(Mission mission, PlacementRules rules) {
        TileRect start = start(mission);
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if ((placed instanceof Placed.Nest || placed instanceof Placed.Loose)
                    && distance(map, start, placed) < rules.startKeepOff()) {
                faults.add(placed + " is " + (int) distance(map, start, placed) + " from the start");
            }
        }
        return faults;
    }

    /** Every nest off the passes into its biome, off every point of it and off every other nest and pickup in it. */
    static List<String> nests(Mission mission, PlacementRules rules) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        List<Placed> placed = mission.placed();
        for (int i = 0; i < placed.size(); i++) {
            Placed nest = placed.get(i);
            if (!(nest instanceof Placed.Nest)) {
                continue;
            }
            if (nest.biome() > 0) {
                for (TileRect entry : mission.laid().entries().get(nest.biome())) {
                    if (distance(map, entry, nest) < rules.nestKeepOff()) {
                        faults.add(nest + " is " + (int) distance(map, entry, nest) + " from the pass at " + entry);
                    }
                }
            }
            for (int j = 0; j < placed.size(); j++) {
                Placed other = placed.get(j);
                if (j == i || other.biome() != nest.biome()) {
                    continue;
                }
                float apart = between(nest, other);
                if (other instanceof Placed.Point && apart < rules.pointKeepOff()) {
                    faults.add(nest + " is " + (int) apart + " from " + other);
                } else if (other instanceof Placed.Nest && apart < rules.nestApart()) {
                    faults.add(nest + " is " + (int) apart + " from " + other);
                } else if (other instanceof Placed.Pickup && apart < rules.pickupKeepOff()) {
                    faults.add(other + " is " + (int) apart + " from " + nest);
                }
            }
        }
        return faults;
    }

    /**
     * One point to each equal band of a biome's columns, so the goal line leads east, two of them {@code pointApart}
     * apart; the last biome's last band is its last {@code approachColumns} (G0064).
     */
    static List<String> bands(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (int p = 0; p < mission.order().size(); p++) {
            List<Placed> points = new ArrayList<>();
            for (Placed placed : mission.placed()) {
                if (placed instanceof Placed.Point && placed.biome() == p) {
                    points.add(placed);
                }
            }
            TileRect rect = mission.laid().biomes().get(p);
            boolean last = p == mission.order().size() - 1;
            int east = last ? Math.min(config.mission().approachColumns(), rect.width()) : 0;
            int even = last ? points.size() - 1 : points.size();
            int span = rect.width() - east;
            List<int[]> bands = new ArrayList<>();
            for (int b = 0; b < even; b++) {
                bands.add(new int[] {rect.col() + span * b / even, rect.col() + span * (b + 1) / even});
            }
            if (last) {
                bands.add(new int[] {rect.endCol() - east, rect.endCol()});
            }
            for (int b = 0; b < bands.size(); b++) {
                int[] band = bands.get(b);
                int in = 0;
                for (Placed point : points) {
                    int col = map.colAt(point.x());
                    in += col >= band[0] && col < band[1] ? 1 : 0;
                }
                if (in != 1) {
                    faults.add("biome " + p + " has " + in + " points in band " + b + ", columns " + band[0] + ".."
                            + (band[1] - 1));
                }
            }
            for (int i = 0; i < points.size(); i++) {
                for (int j = i + 1; j < points.size(); j++) {
                    if (between(points.get(i), points.get(j))
                            < config.mission().placement().pointApart()) {
                        faults.add(points.get(i) + " is " + (int) between(points.get(i), points.get(j)) + " from "
                                + points.get(j));
                    }
                }
            }
        }
        return faults;
    }

    /** Every crate a house did not take within {@code pointReach} of a point of its biome. */
    static List<String> crates(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed crate : mission.placed()) {
            if (!(crate instanceof Placed.Pickup pickup)
                    || !config.pickup(pickup.kind()).armoury().crate()
                    || inAHouse(mission, map.colAt(crate.x()), map.rowAt(crate.y()))) {
                continue;
            }
            float nearest = Float.MAX_VALUE;
            for (Placed point : mission.placed()) {
                if (point instanceof Placed.Point && point.biome() == crate.biome()) {
                    nearest = Math.min(nearest, between(crate, point));
                }
            }
            if (nearest > config.mission().placement().pointReach()) {
                faults.add(crate + " is " + (int) nearest + " from the nearest point");
            }
        }
        return faults;
    }

    private static TileRect start(Mission mission) {
        return mission.laid().entries().getFirst().getFirst();
    }

    private static boolean inAHouse(Mission mission, int col, int row) {
        for (House house : mission.laid().houses()) {
            if (house.inside().contains(col, row)) {
                return true;
            }
        }
        return false;
    }

    /** From a thing to the nearest point of a rect of tiles, in world units. */
    private static float distance(TileMap map, TileRect rect, Placed placed) {
        float tile = map.tileSize();
        float dx = Math.max(0f, Math.max(rect.col() * tile - placed.x(), placed.x() - rect.endCol() * tile));
        float dy = Math.max(0f, Math.max(rect.row() * tile - placed.y(), placed.y() - rect.endRow() * tile));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float between(Placed one, Placed other) {
        float dx = one.x() - other.x();
        float dy = one.y() - other.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
