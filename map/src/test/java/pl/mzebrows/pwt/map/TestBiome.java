package pl.mzebrows.pwt.map;

import java.util.List;

/**
 * The five first biomes and a bare finale, written out as {@code biomes.properties} and {@code finales.properties}
 * will hold them, for this module's tests until {@code sim} reads the tables.
 */
record TestBiome(String name, BiomeGround ground) {

    static final TestBiome PLAIN = new TestBiome(
            "plain",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.PLAIN,
                    Terrain.DUST,
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    Crags.off(),
                    new Outcrops(0.006f, 1, 2, 1, 1, 0f)));

    static final TestBiome QUARRY = new TestBiome(
            "quarry",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.DUST,
                    Terrain.PLAIN,
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    Crags.off(),
                    new Outcrops(0.10f, 4, 9, 2, 3, 0.7f)));

    static final TestBiome BRAKE = new TestBiome(
            "brake",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.PLAIN,
                    Terrain.ORGANIC,
                    List.of(
                            new Patch.Rect(PatchGround.of(Terrain.ORGANIC), 0.035f, 4, 6),
                            new Patch.Blob(PatchGround.of(Terrain.THICKET), 0.18f, 6, 11)),
                    Crags.off(),
                    new Outcrops(0.015f, 2, 4, 1, 2, 0.3f)));

    static final TestBiome ANTHILL = new TestBiome(
            "anthill",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.ORGANIC,
                    Terrain.DUST,
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    new Crags(0.05f, 4),
                    Outcrops.off()));

    static final TestBiome BADLANDS = new TestBiome(
            "badlands",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.ASH,
                    Terrain.DUST,
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    new Crags(0.13f, 4),
                    new Outcrops(0.02f, 2, 4, 1, 2, 0.3f)));

    static final List<TestBiome> FIVE = List.of(PLAIN, QUARRY, BRAKE, ANTHILL, BADLANDS);

    /** The redoubt's ground without its pieces: a fixed width, no dealt coats. */
    static final TestBiome REDOUBT = new TestBiome(
            "redoubt", new BiomeGround(34, 34, 3, Terrain.DUST, Terrain.ASH, List.of(), Crags.off(), Outcrops.off()));

    /** How much of the biome its rock coats take together. */
    float rock() {
        return ground.crags().density() + ground.outcrops().density();
    }
}
