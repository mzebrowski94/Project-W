package pl.mzebrows.pwt.sim;

import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WaveRole;

/**
 * One kind of finale as data (G0064, missions ticket 04): a fixed <strong>skeleton</strong> - its post, watch, mouths
 * and, in a keep, its lair's nests and boss - every position a tile offset from the anchor ({@link Strip#anchor}),
 * flipped north-south or not, and {@link FinaleRules} that place everything else round it from the finale's own stream
 * (G0120). The redoubt is a definition with no lair; the keep the same with one. Every finale is won by holding its
 * post alone. Read from {@code sim.finale.<name>} in {@code finales.properties}.
 *
 * @param ground its width - fixed - and base, laid by {@code Strip} with no dealt coat; its rock is its {@code pieces}:
 *     none as read, the cover its rules deal once {@link #covered}
 * @param hold the point that holds afterwards, its watch and its mouths
 * @param rules how its cover, guns, mines, barrels and pickups are placed round the skeleton
 * @param waves the schedule held back until the post is taken, in order, each from mouths the hold names, each naming
 *     a role
 * @param roles each role's members (G0120), {@code roles.fast=runner 8 10; frog 5 7}: one dealt to each wave that
 *     names it; a role no wave names may be left out
 * @param lair what rises inside the watch, at fixed spots - empty but for a keep
 * @param pool the strains its lair's holes are dealt from, and how many holes one strain may have
 */
public record FinaleDef(
        BiomeGround ground,
        Hold hold,
        FinaleRules rules,
        List<WaveLine> waves,
        Defs<WaveRole, List<WaveLine.Member>> roles,
        List<LairLine> lair,
        Pool pool) {

    /**
     * The strains a lair's holes are dealt from (G0120), written {@code pool.strains=grunt; spitter; ...} and
     * {@code pool.most=2}: each hole a strain of the pool, uniformly among those dealt fewer than {@code most} so far.
     *
     * @param strains the strains, each once
     * @param most how many holes one strain may have
     */
    public record Pool(List<Pooled> strains, int most) {

        public Pool {
            strains = List.copyOf(strains);
            if (most < 0 || (!strains.isEmpty() && most < 1)) {
                throw new IllegalArgumentException("a pool lets a strain have at least one hole, was " + most);
            }
            if (strains.stream().map(Pooled::strain).distinct().count() != strains.size()) {
                throw new IllegalArgumentException("a pool names each strain once, was " + strains);
            }
        }

        /** No pool: a finale whose lair has no holes to deal. */
        public static Pool off() {
            return new Pool(List.of(), 0);
        }
    }

    /** A strain of a pool: {@code grunt}. */
    public record Pooled(UnitKind strain) {}

    public FinaleDef {
        waves = List.copyOf(waves);
        lair = List.copyOf(lair);
        if (ground.widthMin() != ground.widthMax()) {
            throw new IllegalArgumentException("a finale's width is fixed, was " + ground.widthMin() + ".."
                    + ground.widthMax() + ": its skeleton is offsets from its middle");
        }
        if (ground.houses().max() > 0
                || !ground.patches().isEmpty()
                || ground.crags().density() > 0f
                || ground.corridors().on()
                || ground.outcrops().density() > 0f
                || ground.clearings().count() > 0
                || ground.ice().share() > 0f) {
            throw new IllegalArgumentException("a finale's ground has no dealt coat: its rock is its pieces");
        }
        for (WaveLine wave : waves) {
            for (String mouth : wave.mouthNames()) {
                if (!hold.hasMouth(mouth)) {
                    throw new IllegalArgumentException(
                            "a " + wave.role() + " wave comes from " + mouth + ", a mouth the hold lacks");
                }
            }
            if (roles.of(wave.role()).isEmpty()) {
                throw new IllegalArgumentException("a " + wave.role() + " wave names a role with no members");
            }
        }
        long holes = lair.stream().filter(line -> line instanceof LairLine.Hole).count();
        if (holes > (long) pool.strains().size() * pool.most()) {
            throw new IllegalArgumentException("a lair of " + holes + " holes needs a pool that can deal them, had "
                    + pool.strains().size() + " strains, " + pool.most() + " holes each");
        }
        if (waves.size() > Waves.MOST) {
            throw new IllegalArgumentException("a finale sends at most " + Waves.MOST + " waves, had " + waves.size());
        }
    }

    /** The same finale with this cover dealt (G0120): its pieces the ground's and these, offsets from the anchor. */
    public FinaleDef covered(List<TileRect> cover) {
        return new FinaleDef(withPieces(ground, joined(ground.pieces(), cover)), hold, rules, waves, roles, lair, pool);
    }

    /**
     * The same skeleton flipped north-south - its pieces, its hold and its lair - never east-west. Its rules are
     * symmetric about the anchor's row, so they place the same either way.
     */
    public FinaleDef flipped() {
        return new FinaleDef(
                withPieces(ground, ground.pieces().stream().map(Offset::flipped).toList()),
                hold.flipped(),
                rules,
                waves,
                roles,
                lair.stream().map(LairLine::flipped).toList(),
                pool);
    }

    private static BiomeGround withPieces(BiomeGround g, List<TileRect> pieces) {
        return new BiomeGround(
                g.widthMin(),
                g.widthMax(),
                g.regions(),
                g.base(),
                g.fallback(),
                g.houses(),
                g.patches(),
                g.crags(),
                g.corridors(),
                g.outcrops(),
                g.clearings(),
                g.ice(),
                pieces);
    }

    private static <T> List<T> joined(List<T> shared, List<T> own) {
        List<T> all = new ArrayList<>(shared);
        all.addAll(own);
        return List.copyOf(all);
    }
}
