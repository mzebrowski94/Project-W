package pl.mzebrows.pwt.sim.scenario;

import java.util.Map;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.system.upgrade.DraftDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeSystem;

/**
 * The proving ground (G0060): a round patch of sand ringed by thicket, with rock outside it and a few
 * rocks on it, his side on the left and the strain he chose on the right - and nothing in it he did not choose.
 *
 * <p>Built from a {@link ProvingSetup} rather than listed in {@code Scenarios.ALL}, so it never appears
 * in the campaign; {@code Scenarios.provingGround} is the way in. Every source of a draft is off, so it earns
 * none, and the reserve starts full so practice does not run out of lives.
 *
 * <p>Numbers here are this arena's, as every scenario's are - chosen to make a fight readable, not to
 * balance one.
 */
final class ProvingGroundScenario implements Scenario {

    /** The sand's radius, in tiles: about two views across, so the whole fight is one pull-back away. */
    private static final int RADIUS = 18;

    /** How deep the thicket ring is, in tiles, inside the rock. */
    private static final int THICKET = 3;

    /** Rock round the disc, in tiles, so the rim is a wall rather than the map's edge. */
    private static final int MARGIN = 1;

    /** How far left of the middle his side stands, in tiles. */
    private static final int OURS = 11;

    /** Where the strain stands: this far right of the middle, in tiles, to {@link #THEIRS_FAR}. */
    private static final int THEIRS_NEAR = 8;

    private static final int THEIRS_FAR = 13;

    /** Half the height of the band anything on the right is scattered into, in tiles. */
    private static final int THEIRS_HALF_HEIGHT = 5;

    /** Where the others stand: this far left of the middle, in tiles, to {@link #OTHERS_FAR}. */
    private static final int OTHERS_NEAR = 3;

    private static final int OTHERS_FAR = 6;

    private static final int OTHERS_HALF_HEIGHT = 5;

    /** How many of the others stand there. */
    private static final int OTHERS = 3;

    /** Width and height of his spawn area, in tiles; also where he comes back. Room for a whole squad. */
    private static final int SPAWN_WIDTH = 4;

    private static final int SPAWN_HEIGHT = 8;

    /** Lone rock tiles, and clumps of {@link #CLUMP_FEWEST} to {@link #CLUMP_MOST} tiles, to fight round. */
    private static final int LONE_ROCKS = 4;

    private static final int CLUMPS = 3;

    private static final int CLUMP_FEWEST = 3;

    private static final int CLUMP_MOST = 6;

    /** How many tries a rock gets at finding ground it may stand on before it is given up. */
    private static final int TRIES = 64;

    /** Gap left between anything placed and anything already there, in world units. */
    private static final float CLEARANCE = 2f;

    private final ProvingSetup setup;

    ProvingGroundScenario(ProvingSetup setup) {
        this.setup = setup;
    }

    @Override
    public String name() {
        return "Proving Ground";
    }

    @Override
    public String description() {
        return "Practice against " + setup.strain() + " with the " + setup.gun() + ".";
    }

    @Override
    public SimConfig configure(SimConfig base) {
        SimConfig config = Scenario.withoutMuster(base);
        DraftDef draft = config.draft();
        int side = 2 * (RADIUS + MARGIN);
        return config.withMap(config.map().withCols(side).withRows(side).withRegions(1))
                .withSpawn(new SimConfig.SpawnConfig(side / 2 - OURS - SPAWN_WIDTH / 2, SPAWN_WIDTH, SPAWN_HEIGHT))
                .withReserve(config.reserve().withStart(config.reserve().max()))
                // Practice earns nothing (G0060): no source of a draft counts here, the boss included.
                .withDraft(new DraftDef(draft.options(), 0, 0, 0, draft.tierFrom()))
                .withCommander(config.commander().withWeapon(setup.gun()))
                .withTrooper(config.trooper()
                        .withSquadSize(setup.squad() ? config.trooper().squadSize() : 0));
    }

    /**
     * Sand inside the disc, thicket in its outer ring, rock outside it - judged at each tile's centre - and
     * a few lone rocks and clumps on the sand, drawn from the map's own stream, so a seed is a layout. Rock
     * keeps a tile of sand from the thicket and a tile from where anything stands, and a pocket it fences
     * off is filled in.
     */
    @Override
    public TileMap map(SimConfig config, Randomness randomness) {
        SimConfig.MapConfig shape = config.map();
        int cols = shape.cols();
        int rows = shape.rows();
        TileMap.Builder builder = TileMap.builder(cols, rows, shape.tileSize());
        boolean[] free = new boolean[cols * rows];
        float middle = cols * 0.5f;
        int inner = RADIUS - THICKET;
        int open = inner - 1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float dx = col + 0.5f - middle;
                float dy = row + 0.5f - middle;
                float squared = dx * dx + dy * dy;
                Terrain ground = squared > RADIUS * RADIUS
                        ? Terrain.ROCK
                        : squared > inner * inner ? Terrain.THICKET : Terrain.SAND;
                builder.set(col, row, ground);
                free[row * cols + col] = squared <= open * open;
            }
        }
        int centre = cols / 2;
        SimConfig.SpawnConfig spawn = config.spawn();
        keepClear(free, cols, new TileRect(spawn.col(), (rows - spawn.height()) / 2, spawn.width(), spawn.height()));
        keepClear(free, cols, band(centre - OTHERS_FAR, centre - OTHERS_NEAR, centre, OTHERS_HALF_HEIGHT));
        keepClear(free, cols, band(centre + THEIRS_NEAR, centre + THEIRS_FAR, centre, THEIRS_HALF_HEIGHT));

        boolean[] rock = new boolean[cols * rows];
        for (int i = 0; i < LONE_ROCKS; i++) {
            int at = loneTile(free, rock, cols, randomness);
            if (at >= 0) {
                rock[at] = true;
            }
        }
        for (int i = 0; i < CLUMPS; i++) {
            int seed = loneTile(free, rock, cols, randomness);
            if (seed >= 0) {
                int size = CLUMP_FEWEST + randomness.nextInt(CLUMP_MOST - CLUMP_FEWEST + 1);
                grow(free, rock, cols, seed, size, randomness);
            }
        }
        for (int i = 0; i < rock.length; i++) {
            if (rock[i]) {
                builder.set(i % cols, i / cols, Terrain.ROCK);
            }
        }
        return Maps.sealUnreachable(builder, spawn.col(), rows / 2, Terrain.ROCK)
                .build();
    }

    /** Marks a rect, and a tile round it, as ground no rock may take. */
    private static void keepClear(boolean[] free, int cols, TileRect area) {
        int rows = free.length / cols;
        for (int row = Math.max(0, area.row() - 1); row <= Math.min(rows - 1, area.endRow()); row++) {
            for (int col = Math.max(0, area.col() - 1); col <= Math.min(cols - 1, area.endCol()); col++) {
                free[row * cols + col] = false;
            }
        }
    }

    /** A free tile with no rock on any of the eight round it, or -1 if none turned up in time. */
    private static int loneTile(boolean[] free, boolean[] rock, int cols, Randomness randomness) {
        int rows = free.length / cols;
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int col = randomness.nextInt(cols);
            int row = randomness.nextInt(rows);
            if (free[row * cols + col] && !rockAround(rock, cols, col, row)) {
                return row * cols + col;
            }
        }
        return -1;
    }

    private static boolean rockAround(boolean[] rock, int cols, int col, int row) {
        int rows = rock.length / cols;
        for (int r = Math.max(0, row - 1); r <= Math.min(rows - 1, row + 1); r++) {
            for (int c = Math.max(0, col - 1); c <= Math.min(cols - 1, col + 1); c++) {
                if (rock[r * cols + c]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Grows a clump from {@code seed}, a side-neighbour of a tile already in it at a time, up to {@code size}. */
    private static void grow(boolean[] free, boolean[] rock, int cols, int seed, int size, Randomness randomness) {
        int rows = rock.length / cols;
        int[] clump = new int[size];
        clump[0] = seed;
        rock[seed] = true;
        int grown = 1;
        for (int attempt = 0; attempt < TRIES && grown < size; attempt++) {
            int from = clump[randomness.nextInt(grown)];
            int direction = randomness.nextInt(4);
            int col = from % cols + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
            int row = from / cols + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
            if (col < 0 || row < 0 || col >= cols || row >= rows) {
                continue;
            }
            int at = row * cols + col;
            if (free[at] && !rock[at]) {
                rock[at] = true;
                clump[grown++] = at;
            }
        }
    }

    @Override
    public void populate(Simulation simulation) {
        deployPlayerSide(simulation);
        TileMap map = simulation.map();
        int middle = map.cols() / 2;
        Scatter scatter = new Scatter(map, simulation.rng(), CLEARANCE, CLEARANCE);
        spawnOthers(simulation, scatter, band(middle - OTHERS_FAR, middle - OTHERS_NEAR, middle, OTHERS_HALF_HEIGHT));
        spawnTheirs(simulation, scatter, band(middle + THEIRS_NEAR, middle + THEIRS_FAR, middle, THEIRS_HALF_HEIGHT));
        for (Map.Entry<UpgradeKind, Integer> held : setup.ranks().entrySet()) {
            UpgradeSystem.hold(simulation.engine(), simulation.config(), held.getKey(), held.getValue());
        }
    }

    private void spawnOthers(Simulation simulation, Scatter scatter, TileRect band) {
        SimConfig config = simulation.config();
        switch (setup.others()) {
            case NONE -> {}
            case TURRETS -> scatter.scatter(band, OTHERS, config.turret().radius(), simulation::spawnTurret);
            case MINES -> scatter.scatter(band, OTHERS, config.mine().radius(), simulation::spawnMine);
            case BARRELS ->
                scatter.scatter(band, OTHERS, config.pickup(PickupKind.BARREL).radius(), simulation::spawnBarrel);
        }
    }

    /** The chosen strain, or a nest of it; a rooted strain is one, standing - it cannot come out of a hole. */
    private void spawnTheirs(Simulation simulation, Scatter scatter, TileRect band) {
        UnitDef def = simulation.config().unit(setup.strain());
        if (setup.nest() && !def.rooted()) {
            scatter.scatter(
                    band, 1, simulation.config().nest().radius(), (x, y) -> simulation.spawnNest(setup.strain(), x, y));
            return;
        }
        int count = def.rooted() ? 1 : setup.count();
        scatter.scatter(band, count, def.radius(), (x, y) -> simulation.spawnUnit(setup.strain(), x, y));
    }

    /** Columns {@code from} to {@code to}, rows centred on {@code middle} - a band to scatter into. */
    private static TileRect band(int from, int to, int middle, int halfHeight) {
        return new TileRect(from, middle - halfHeight, to - from, 2 * halfHeight);
    }
}
