package pl.mzebrows.pwt.client.play;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.Clearing;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.StripFaults;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.FinaleRules;
import pl.mzebrows.pwt.sim.Furniture;
import pl.mzebrows.pwt.sim.Offset;
import pl.mzebrows.pwt.sim.PlacementRules;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Wave;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;
import pl.mzebrows.pwt.sim.scenario.Mission;
import pl.mzebrows.pwt.sim.scenario.Placed;

/**
 * What a built mission may never show - its strip's faults ({@link StripFaults}), placement's (G0068) and its
 * finale's (G0064) - each
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
        faults.addAll(sites(mission, config));
        faults.addAll(relics(mission, config));
        faults.addAll(clearings(mission, config));
        faults.addAll(ice(mission, config));
        faults.addAll(crates(mission, config));
        faults.addAll(trees(mission, config));
        faults.addAll(geysers(mission, config.mission().placement()));
        faults.addAll(emplacements(mission, config));
        faults.addAll(finale(mission));
        faults.addAll(finaleRules(mission, config));
        return faults;
    }

    /**
     * The finale's set piece where it can be played (G0064): everything it stands, and every tile of every mouth a wave
     * comes from, on open ground reached from the start - and nothing hostile of the last biome inside its watch, so
     * holding its post never waits on a hole or a sleeper behind the ridge. What its lair raises stands there too, off
     * kept ground, and inside the watch, so holding the post means dealing with it (G0048). And no two things of the
     * set piece or its lair on one tile - a mine under a gun.
     */
    static List<String> finale(Mission mission) {
        TileMap map = mission.laid().map();
        TileRect start = start(mission);
        boolean[] reached = Maps.reachableFrom(map, start.col(), start.row());
        int finale = mission.order().size();
        List<String> faults = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        Placed.Hold hold = null;
        for (Placed placed : mission.placed()) {
            if (placed.biome() != finale) {
                continue;
            }
            if (placed instanceof Placed.Hold point) {
                hold = point;
            }
            int col = map.colAt(placed.x());
            int row = map.rowAt(placed.y());
            if (!taken.add(row * map.cols() + col)) {
                faults.add("the finale's " + placed + " shares its tile (" + col + "," + row + ") with another");
            }
            if (!map.inBounds(col, row) || !reached[row * map.cols() + col]) {
                faults.add("the finale's " + placed + " is not on open ground reached from the start");
            }
        }
        if (hold == null) {
            faults.add("the finale has no point that holds afterwards");
            return faults;
        }
        for (Wave wave : mission.finale().waves()) {
            for (Wave.Lot lot : wave.lots()) {
                TileRect mouth = lot.where();
                for (int row = mouth.row(); row < mouth.endRow(); row++) {
                    for (int col = mouth.col(); col < mouth.endCol(); col++) {
                        if (!map.inBounds(col, row) || !reached[row * map.cols() + col]) {
                            faults.add("the finale's mouth " + mouth + " is shut at (" + col + "," + row + ")");
                        }
                    }
                }
            }
        }
        for (Arrival arrival : mission.finale().lair()) {
            int col = map.colAt(arrival.x());
            int row = map.rowAt(arrival.y());
            if (!taken.add(row * map.cols() + col)) {
                faults.add("the lair's " + arrival + " shares its tile (" + col + "," + row + ") with another");
            }
            if (!map.inBounds(col, row)
                    || !reached[row * map.cols() + col]
                    || mission.laid().kept(col, row)) {
                faults.add("the lair's " + arrival + " is not on open ground reached from the start");
            }
            float dx = arrival.x() - hold.x();
            float dy = arrival.y() - hold.y();
            if (dx * dx + dy * dy > hold.watch() * hold.watch()) {
                faults.add("the lair's " + arrival + " is outside the finale's watch");
            }
        }
        for (Placed placed : mission.placed()) {
            if (hostile(placed) && placed.biome() == finale - 1 && between(placed, hold) <= hold.watch()) {
                faults.add(placed + " is " + (int) between(placed, hold) + " from the finale's post, inside its watch");
            }
        }
        return faults;
    }

    /**
     * What a finale's rules promise (G0120): each lot's count within its range - the guns fixed, one of each pickup
     * named - everything round the post and every cache of the lair in mirrored north-south pairs about the anchor's
     * row, and so every piece of cover; the ring of tiles round every piece open ground reached from the start, so its
     * lee is; the straight way from every mouth to the post, and from the post to whoever the lair raises, never rock;
     * and in a finale whose rules say so, its guns, caches and cover inside the boss's barrage.
     */
    static List<String> finaleRules(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        TileRect anchor = Strip.anchor(mission.laid().biomes().getLast());
        TileRect start = start(mission);
        boolean[] reached = Maps.reachableFrom(map, start.col(), start.row());
        FinaleRules rules = mission.finale().def(config).rules();
        int position = mission.order().size();
        float row = map.tileCentreY(anchor.row());
        List<String> faults = new ArrayList<>();

        List<float[]> paired = new ArrayList<>();
        int[] counted = new int[4];
        for (Placed placed : mission.placed()) {
            if (placed.biome() != position || placed instanceof Placed.Hold) {
                continue;
            }
            paired.add(new float[] {placed.x(), placed.y()});
            switch (placed) {
                case Placed.Turret turret -> counted[0]++;
                case Placed.Mine mine -> counted[1]++;
                case Placed.Barrel barrel -> counted[2]++;
                case Placed.Pickup pickup -> counted[3]++;
                default -> faults.add("the finale's rules put down " + placed + ", which they never place");
            }
        }
        List<Arrival> caches = new ArrayList<>();
        Arrival boss = null;
        for (Arrival arrival : mission.finale().lair()) {
            if (arrival.form() == Arrival.Form.PICKUP) {
                caches.add(arrival);
                paired.add(new float[] {arrival.x(), arrival.y()});
            } else if (arrival.form() == Arrival.Form.UNIT) {
                boss = arrival;
            }
        }
        within(faults, "turrets", counted[0], rules.turrets());
        within(faults, "mines", counted[1], rules.mines());
        within(faults, "barrels", counted[2], rules.barrels());
        within(faults, "pickups", counted[3], rules.pickups());
        within(faults, "lair's caches", caches.size(), rules.lair());

        for (float[] one : paired) {
            boolean mirrored = false;
            for (float[] other : paired) {
                mirrored |= Math.abs(other[0] - one[0]) < 1e-3f && Math.abs((other[1] - row) + (one[1] - row)) < 1e-3f;
            }
            if (!mirrored) {
                faults.add("the finale's thing at " + one[0] + "," + one[1] + " has no mirror north-south");
            }
        }
        List<TileRect> cover = mission.finale().cover();
        for (TileRect piece : cover) {
            if (!cover.contains(Offset.flipped(piece))) {
                faults.add("the finale's cover " + piece + " has no mirror north-south");
            }
            TileRect at =
                    new TileRect(anchor.col() + piece.col(), anchor.row() + piece.row(), piece.width(), piece.height());
            for (int r = at.row() - 1; r <= at.endRow(); r++) {
                for (int c = at.col() - 1; c <= at.endCol(); c++) {
                    if (at.contains(c, r) || armOf(cover, anchor, at, c, r)) {
                        continue;
                    }
                    if (!map.inBounds(c, r) || !reached[r * map.cols() + c]) {
                        faults.add("the lee of the finale's cover " + piece + " is shut at (" + c + "," + r + ")");
                    }
                }
            }
        }

        Placed.Hold hold = null;
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Hold point) {
                hold = point;
            }
        }
        if (hold == null) {
            return faults;
        }
        for (Wave wave : mission.finale().waves()) {
            for (Wave.Lot lot : wave.lots()) {
                way(
                        faults,
                        map,
                        "from the mouth " + lot.where(),
                        lot.where().centreX(map),
                        lot.where().centreY(map),
                        hold);
            }
        }
        if (boss != null) {
            way(faults, map, "to the lair's " + boss.strain(), boss.x(), boss.y(), hold);
        }

        if (rules.inReach() && boss != null) {
            float reach = reach(config, boss);
            for (Placed placed : mission.placed()) {
                if (placed instanceof Placed.Turret
                        && placed.biome() == position
                        && apart(placed.x(), placed.y(), boss) > reach) {
                    faults.add("the finale's " + placed + " is outside the boss's barrage");
                }
            }
            for (Arrival cache : caches) {
                if (apart(cache.x(), cache.y(), boss) > reach) {
                    faults.add("the lair's " + cache + " is outside the boss's barrage");
                }
            }
            for (TileRect piece : cover) {
                float x = map.tileCentreX(anchor.col() + piece.col());
                float y = map.tileCentreY(anchor.row() + piece.row());
                if (apart(x, y, boss) > reach) {
                    faults.add("the finale's cover " + piece + " is outside the boss's barrage");
                }
            }
        }
        return faults;
    }

    private static void within(List<String> faults, String what, int count, FinaleRules.Lot lot) {
        if (count < lot.min() || count > lot.max()) {
            faults.add("the finale has " + count + " " + what + ", not " + lot.min() + ".." + lot.max());
        }
    }

    /** Whether a tile is in another rect of the cover that shares an edge with this one: the other arm of an L. */
    private static boolean armOf(List<TileRect> cover, TileRect anchor, TileRect at, int col, int row) {
        for (TileRect piece : cover) {
            TileRect other =
                    new TileRect(anchor.col() + piece.col(), anchor.row() + piece.row(), piece.width(), piece.height());
            if (other.equals(at) || !other.contains(col, row)) {
                continue;
            }
            boolean besideAcross = other.endCol() == at.col() || at.endCol() == other.col();
            boolean besideUp = other.endRow() == at.row() || at.endRow() == other.row();
            boolean overlapsUp = other.row() < at.endRow() && at.row() < other.endRow();
            boolean overlapsAcross = other.col() < at.endCol() && at.col() < other.endCol();
            if ((besideAcross && overlapsUp) || (besideUp && overlapsAcross)) {
                return true;
            }
        }
        return false;
    }

    /** The straight way from here to the post, every half tile of it, on ground that is not rock. */
    private static void way(List<String> faults, TileMap map, String what, float x, float y, Placed.Hold hold) {
        float dx = hold.x() - x;
        float dy = hold.y() - y;
        int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy) / (map.tileSize() * 0.5f)));
        for (int i = 0; i <= steps; i++) {
            int col = map.colAt(x + dx * i / steps);
            int r = map.rowAt(y + dy * i / steps);
            if (!map.passableAtTile(col, r)) {
                faults.add("the finale's way " + what + " to its post is shut by rock at (" + col + "," + r + ")");
                return;
            }
        }
    }

    /** How far the boss's barrage reaches: the shortest range of a gun its patterns fire, a shot that flies on aside. */
    private static float reach(SimConfig config, Arrival boss) {
        BarrageDef barrage = config.unit(Objects.requireNonNull(boss.strain())).barrage();
        float reach = Float.MAX_VALUE;
        for (PatternDef pattern : List.of(barrage.ring(), barrage.fan(), barrage.spiral(), barrage.stream())) {
            float range = config.weapon(pattern.gun()).projectileRange();
            if (pattern.enabled() && range > 0f) {
                reach = Math.min(reach, range);
            }
        }
        return reach;
    }

    private static float apart(float x, float y, Arrival other) {
        float dx = x - other.x();
        float dy = y - other.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Every point and pickup - a house's stock among them - stands on ground reached from the start. */
    static List<String> reach(Mission mission) {
        TileMap map = mission.laid().map();
        TileRect start = start(mission);
        boolean[] reached = Maps.reachableFrom(map, start.col(), start.row());
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (site(placed) || placed instanceof Placed.Pickup) {
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
            boolean stock = (placed instanceof Placed.Pickup
                            || placed instanceof Placed.Worked
                            || placed instanceof Placed.Housed)
                    && inAHouse(mission, col, row);
            if (mission.laid().kept(col, row) && !stock) {
                faults.add(placed + " stands on kept ground at (" + col + "," + row + ")");
            }
        }
        return faults;
    }

    /**
     * No point, nest or loose creature in a house: a house is a cache you open - and what it holds that is hostile is a
     * house pack, laid inside one of its own biome's houses and never a gatehouse (G0112).
     */
    static List<String> houses(Mission mission) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Housed housed) {
                House house = houseAt(mission, map.colAt(housed.x()), map.rowAt(housed.y()));
                TileRect rect = housed.biome() < mission.order().size()
                        ? mission.laid().biomes().get(housed.biome())
                        : null;
                if (house == null
                        || rect == null
                        || !rect.contains(house.walls().col(), house.walls().row())
                        || !rect.contains(
                                house.walls().endCol() - 1, house.walls().endRow() - 1)) {
                    faults.add(housed + " is not inside a house of its biome");
                }
                continue;
            }
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
            if (hostile(placed) && distance(map, start, placed) < rules.startKeepOff()) {
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
                if (site(other) && apart < rules.pointKeepOff()) {
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
                if (site(placed) && placed.biome() == p) {
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

    /**
     * Every biome has as many sites as its furniture deals - control points and worked sites together - and at most one
     * worked site of each kind; every worked site that stands in a house is inside one of this biome's houses, never a
     * gatehouse, and its body a man's width clear of every doorway, so it never blocks a way in (G0111).
     */
    static List<String> sites(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        float man = config.trooper().radius() * 2f;
        for (int p = 0; p < mission.order().size(); p++) {
            int sites = 0;
            int open = 0;
            Set<WorkedSiteKind> kinds = new HashSet<>();
            for (Placed placed : mission.placed()) {
                if (placed.biome() != p || !site(placed)) {
                    continue;
                }
                sites++;
                if (!(placed instanceof Placed.Worked worked)) {
                    continue;
                }
                if (!config.workedSite(worked.kind()).housed()) {
                    // In the open, a relic: as many as its biome's furniture counts (G0114).
                    open++;
                    continue;
                }
                if (!kinds.add(worked.kind())) {
                    faults.add("biome " + p + " has a second " + worked.kind());
                }
                House house = houseAt(mission, map.colAt(worked.x()), map.rowAt(worked.y()));
                TileRect rect = mission.laid().biomes().get(p);
                if (house == null
                        || !rect.contains(house.walls().col(), house.walls().row())
                        || !rect.contains(
                                house.walls().endCol() - 1, house.walls().endRow() - 1)) {
                    faults.add(worked + " is not in a house of its biome");
                    continue;
                }
                float clear = config.workedSite(worked.kind()).radius() + man;
                for (TileRect doorway : house.doorways()) {
                    if (distance(map, doorway, worked) < clear) {
                        faults.add(worked + " is " + (int) distance(map, doorway, worked) + " from the doorway at "
                                + doorway);
                    }
                }
            }
            Furniture furniture = config.biomes().of(mission.order().get(p)).furniture();
            if (sites < furniture.sitesMin() || sites > furniture.sitesMax()) {
                faults.add("biome " + p + " has " + sites + " sites, not " + furniture.sitesMin() + ".."
                        + furniture.sitesMax());
            }
            if (open < furniture.relics().min() || open > furniture.relics().max()) {
                faults.add("biome " + p + " has " + open + " relics, not "
                        + furniture.relics().min() + ".." + furniture.relics().max());
            }
        }
        return faults;
    }

    /**
     * Every run of ice (ADR 0025) stands across a way - solid at both ends, at most the clearance and one long - on open
     * ground, never in a clearing nor within the clearance of a kept tile, so no way in is iced; and nothing placed
     * stands in it.
     */
    static List<String> ice(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        int clearance = config.mission().ground().clearance();
        List<String> faults = new ArrayList<>();
        for (TileRect run : mission.laid().ice()) {
            boolean lying = run.height() == 1;
            int length = lying ? run.width() : run.height();
            boolean firstEnd = map.passableAtTile(lying ? run.col() - 1 : run.col(), lying ? run.row() : run.row() - 1);
            boolean lastEnd = map.passableAtTile(lying ? run.endCol() : run.col(), lying ? run.row() : run.endRow());
            if (length > clearance + 1 || firstEnd || lastEnd) {
                faults.add("ice " + run + " does not stand across a narrow way");
            }
            for (int row = run.row(); row < run.endRow(); row++) {
                for (int col = run.col(); col < run.endCol(); col++) {
                    if (!map.passableAtTile(col, row)) {
                        faults.add("ice " + run + " stands on " + map.terrainAtTile(col, row));
                    }
                    for (Clearing clearing : mission.laid().clearings()) {
                        if (clearing.contains(col, row)) {
                            faults.add("ice " + run + " stands in " + clearing);
                        }
                    }
                    if (keptNear(mission, col, row, clearance)) {
                        faults.add("ice " + run + " stands by kept ground at " + col + "," + row);
                    }
                }
            }
            for (Placed placed : mission.placed()) {
                if (distance(map, run, placed) < radius(placed, config)) {
                    faults.add(placed + " stands in the ice " + run);
                }
            }
        }
        return faults;
    }

    /**
     * Every clearing of a biome whose sites are in its clearings holds a control point on its centre tile (G0115).
     */
    static List<String> clearings(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (int p = 0; p < mission.order().size(); p++) {
            if (!config.biomes().of(mission.order().get(p)).furniture().sitesInClearings()) {
                continue;
            }
            TileRect rect = mission.laid().biomes().get(p);
            for (Clearing clearing : mission.laid().clearings()) {
                if (!rect.contains(clearing.col(), clearing.row())) {
                    continue;
                }
                boolean held = false;
                for (Placed placed : mission.placed()) {
                    held |= placed instanceof Placed.Point
                            && map.colAt(placed.x()) == clearing.col()
                            && map.rowAt(placed.y()) == clearing.row();
                }
                if (!held) {
                    faults.add("biome " + p + "'s " + clearing + " holds no control point at its centre");
                }
            }
        }
        return faults;
    }

    /**
     * Every worked site in the open - a relic (G0114) - stands in its room: every tile within it its biome's base and
     * not kept, and nothing else placed within it. And its plinth is reachable from inside its wardens: a commander fits
     * square on each of its four compass points, touching the plinth - clear of rock and of every warden's body.
     */
    static List<String> relics(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        float tile = map.tileSize();
        float man = config.commander().radius();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (!(placed instanceof Placed.Worked worked)
                    || config.workedSite(worked.kind()).housed()) {
                continue;
            }
            var site = config.workedSite(worked.kind());
            float room = site.clearing();
            Terrain base = config.biomes()
                    .of(mission.order().get(placed.biome()))
                    .ground()
                    .base();
            int span = (int) Math.ceil(room / tile);
            for (int row = map.rowAt(worked.y()) - span; row <= map.rowAt(worked.y()) + span; row++) {
                for (int col = map.colAt(worked.x()) - span; col <= map.colAt(worked.x()) + span; col++) {
                    float dx = Math.max(Math.max(col * tile - worked.x(), 0f), worked.x() - (col + 1) * tile);
                    float dy = Math.max(Math.max(row * tile - worked.y(), 0f), worked.y() - (row + 1) * tile);
                    if (dx * dx + dy * dy > room * room) {
                        continue;
                    }
                    if (!map.inBounds(col, row)
                            || mission.laid().kept(col, row)
                            || map.terrainAtTile(col, row) != base) {
                        faults.add(
                                worked + " has ground other than open " + base + " in its room at " + col + "," + row);
                    }
                }
            }
            for (Placed other : mission.placed()) {
                if (!other.equals(placed) && between(placed, other) < room + radius(other, config)) {
                    faults.add(other + " stands in the room of " + worked);
                }
            }
            if (!site.guards().on()) {
                continue;
            }
            float offset = site.guards().offset();
            float guard = config.emplacement(site.guards().kind()).radius();
            float diagonal = offset * (float) Math.sqrt(0.5);
            float out = site.radius() + man;
            float[][] spots = {{0f, out}, {out, 0f}, {0f, -out}, {-out, 0f}};
            for (float[] spot : spots) {
                float x = worked.x() + spot[0];
                float y = worked.y() + spot[1];
                boolean clear = map.isClear(x, y, man);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        float gx = worked.x() + sx * diagonal - x;
                        float gy = worked.y() + sy * diagonal - y;
                        clear &= gx * gx + gy * gy >= (guard + man) * (guard + man);
                    }
                }
                if (!clear) {
                    faults.add(worked + "'s plinth cannot be reached from inside its wardens at " + x + "," + y);
                }
            }
        }
        return faults;
    }

    /**
     * Every turret within {@code pointReach} of a point of its biome (G0082), and every crate a biome lays in a house
     * (G0110): a crate on the open field is what a taken site leaves, never furniture. A finale's are laid.
     */
    static List<String> crates(Mission mission, SimConfig config) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed.biome() == mission.order().size()) {
                continue;
            }
            if (placed instanceof Placed.Pickup pickup
                    && config.pickup(pickup.kind()).armoury().crate()
                    && !inAHouse(mission, map.colAt(placed.x()), map.rowAt(placed.y()))) {
                faults.add(placed + " is a crate outside every house");
            }
            if (!(placed instanceof Placed.Turret)) {
                continue;
            }
            float nearest = Float.MAX_VALUE;
            for (Placed point : mission.placed()) {
                if (point instanceof Placed.Point && point.biome() == placed.biome()) {
                    nearest = Math.min(nearest, between(placed, point));
                }
            }
            if (nearest > config.mission().placement().pointReach()) {
                faults.add(placed + " is " + (int) nearest + " from the nearest point");
            }
        }
        return faults;
    }

    /**
     * Every nest of a biome that stands trees by its nests has at least its fewest within {@code treeReach}, and every
     * tree stands {@code rockMargin} off everything else in its biome, as a boulder does (G0072, G0042).
     */
    static List<String> trees(Mission mission, SimConfig config) {
        PlacementRules rules = config.mission().placement();
        List<String> faults = new ArrayList<>();
        List<Placed> all = mission.placed();
        for (int i = 0; i < all.size(); i++) {
            Placed placed = all.get(i);
            if (placed instanceof Placed.Nest nest
                    && nest.biome() < mission.order().size()) {
                int wanted = config.biomes()
                        .of(mission.order().get(nest.biome()))
                        .furniture()
                        .treesByNestMin();
                int beside = 0;
                for (Placed other : mission.placed()) {
                    beside += other instanceof Placed.Tree && between(nest, other) <= rules.treeReach() ? 1 : 0;
                }
                if (beside < wanted) {
                    faults.add(nest + " has " + beside + " trees within " + rules.treeReach() + ", wants " + wanted);
                }
            }
            if (placed instanceof Placed.Tree tree) {
                for (int j = 0; j < all.size(); j++) {
                    Placed other = all.get(j);
                    if (j == i || other.biome() != tree.biome() || other instanceof Placed.Hold) {
                        continue;
                    }
                    float gap = between(tree, other) - tree.trunk() - radius(other, config);
                    if (gap < rules.rockMargin() - 0.001f) {
                        faults.add(tree + " is " + gap + " off " + other);
                    }
                }
            }
        }
        return faults;
    }

    /**
     * Every geyser {@code geyserKeepOff} off every point and nest of its biome and every way into it, the start among
     * them (G0089): its fire is nobody's, so it never erupts where the squad must stand or comes in.
     */
    static List<String> geysers(Mission mission, PlacementRules rules) {
        TileMap map = mission.laid().map();
        List<String> faults = new ArrayList<>();
        for (Placed geyser : mission.placed()) {
            if (!(geyser instanceof Placed.Geyser)) {
                continue;
            }
            for (TileRect entry : mission.laid().entries().get(geyser.biome())) {
                if (distance(map, entry, geyser) < rules.geyserKeepOff()) {
                    faults.add(geyser + " is " + (int) distance(map, entry, geyser) + " from the way in at " + entry);
                }
            }
            for (Placed other : mission.placed()) {
                boolean kept = site(other) || other instanceof Placed.Nest;
                if (kept && other.biome() == geyser.biome() && between(geyser, other) < rules.geyserKeepOff()) {
                    faults.add(geyser + " is " + (int) between(geyser, other) + " from " + other);
                }
            }
        }
        return faults;
    }

    /**
     * A biome's emplacements (G0113): between its fewest and most spires, never on a thicket's tile, and each {@code
     * emplacementKeepOff} off every way in (the start's own distance in the first biome), every nest of its biome, every
     * site's edge and the finale's watch.
     */
    static List<String> emplacements(Mission mission, SimConfig config) {
        PlacementRules rules = config.mission().placement();
        TileMap map = mission.laid().map();
        float keepOff = rules.emplacementKeepOff();
        int finale = mission.order().size();
        @Nullable Placed hold = null;
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Hold) {
                hold = placed;
            }
        }
        List<String> faults = new ArrayList<>();
        int[] counted = new int[finale];
        for (Placed emplacement : mission.placed()) {
            if (!(emplacement instanceof Placed.Emplacement)) {
                continue;
            }
            int biome = emplacement.biome();
            counted[biome]++;
            if (map.terrainAt(emplacement.x(), emplacement.y()) == Terrain.THICKET) {
                faults.add(emplacement + " stands in a thicket");
            }
            float fromAWayIn = biome == 0 ? Math.max(rules.startKeepOff(), keepOff) : keepOff;
            for (TileRect entry : mission.laid().entries().get(biome)) {
                if (distance(map, entry, emplacement) < fromAWayIn) {
                    faults.add(emplacement + " is " + (int) distance(map, entry, emplacement) + " from the way in at "
                            + entry);
                }
            }
            for (Placed other : mission.placed()) {
                if (other.biome() != biome) {
                    continue;
                }
                float least =
                        site(other) ? radius(other, config) + keepOff : other instanceof Placed.Nest ? keepOff : 0f;
                if (between(emplacement, other) < least) {
                    faults.add(emplacement + " is " + (int) between(emplacement, other) + " from " + other);
                }
            }
            if (hold instanceof Placed.Hold watched
                    && biome == finale - 1
                    && between(emplacement, hold) < watched.watch() + keepOff) {
                faults.add(emplacement + " is " + (int) between(emplacement, hold) + " from the finale's post");
            }
        }
        for (int biome = 0; biome < finale; biome++) {
            Furniture.Count spires =
                    config.biomes().of(mission.order().get(biome)).furniture().spires();
            if (counted[biome] < spires.min() || counted[biome] > spires.max()) {
                faults.add("biome " + biome + " has " + counted[biome] + " spires, not " + spires.min() + "-"
                        + spires.max());
            }
        }
        return faults;
    }

    /** What a thing standing takes up, as placement reserves it. */
    private static float radius(Placed thing, SimConfig config) {
        return switch (thing) {
            case Placed.Point _, Placed.Hold _ -> config.controlPoint().radius();
            case Placed.Nest _ -> config.nest().radius();
            case Placed.Loose loose -> config.unit(loose.strain()).radius();
            case Placed.Housed housed -> config.unit(housed.strain()).radius();
            case Placed.Pickup pickup -> config.pickup(pickup.kind()).radius();
            case Placed.Barrel _ -> config.pickup(PickupKind.BARREL).radius();
            case Placed.Boulder _ -> config.obstacle().radius();
            case Placed.Tree tree -> tree.trunk();
            case Placed.Geyser geyser -> geyser.radius();
            case Placed.Stone stone -> stone.radius();
            case Placed.Rabbit _ -> config.pickup(PickupKind.RABBIT).radius();
            case Placed.Turret _ -> config.turret().radius();
            case Placed.Mine _ -> config.mine().radius();
            case Placed.Worked worked -> config.workedSite(worked.kind()).radius();
            case Placed.Emplacement emplacement ->
                config.emplacement(emplacement.kind()).radius();
        };
    }

    /** Whether generation kept a tile within {@code reach} tiles of this one. */
    private static boolean keptNear(Mission mission, int col, int row, int reach) {
        for (int r = row - reach; r <= row + reach; r++) {
            for (int c = col - reach; c <= col + reach; c++) {
                if (mission.laid().kept(c, r)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Something hostile a biome put down: a nest, a loose creature, or one of a house pack (G0112). */
    private static boolean hostile(Placed placed) {
        return placed instanceof Placed.Nest || placed instanceof Placed.Loose || placed instanceof Placed.Housed;
    }

    /** A site of a biome, whatever its kind (G0110): a control point or a worked site in a point's place. */
    private static boolean site(Placed placed) {
        return placed instanceof Placed.Point || placed instanceof Placed.Worked;
    }

    private static TileRect start(Mission mission) {
        return mission.laid().entries().getFirst().getFirst();
    }

    private static boolean inAHouse(Mission mission, int col, int row) {
        return houseAt(mission, col, row) != null;
    }

    private static @Nullable House houseAt(Mission mission, int col, int row) {
        for (House house : mission.laid().houses()) {
            if (house.inside().contains(col, row)) {
                return house;
            }
        }
        return null;
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
