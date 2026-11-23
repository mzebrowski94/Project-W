package pl.mzebrows.pwt.map;

import java.util.List;

/**
 * The five first biomes and the redoubt's ground, written out as {@code biomes.properties} and {@code
 * finales.properties} hold them, as this module's own fixtures: {@code map} cannot read the tables. Houses: the
 * quarry 1-2, "a quarry with a base in it", the rest 0-1 so the sweep proves a house survives every coat (missions
 * ticket 12).
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
                    new Houses(0, 1),
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    Crags.off(),
                    new Outcrops(0.006f, 1, 2, 1, 1, 0f, 0f),
                    Clearings.off(),
                    List.of()));

    static final TestBiome QUARRY = new TestBiome(
            "quarry",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.DUST,
                    Terrain.PLAIN,
                    new Houses(1, 2),
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    Crags.off(),
                    new Outcrops(0.10f, 4, 9, 2, 3, 0.7f, 0f),
                    Clearings.off(),
                    List.of()));

    static final TestBiome BRAKE = new TestBiome(
            "brake",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.PLAIN,
                    Terrain.ORGANIC,
                    new Houses(0, 1),
                    List.of(
                            new Patch.Rect(PatchGround.of(Terrain.ORGANIC), 0.035f, 4, 6),
                            new Patch.Blob(PatchGround.of(Terrain.THICKET), 0.18f, 6, 11)),
                    Crags.off(),
                    new Outcrops(0.015f, 2, 4, 1, 2, 0.3f, 0f),
                    Clearings.off(),
                    List.of()));

    static final TestBiome ANTHILL = new TestBiome(
            "anthill",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.ORGANIC,
                    Terrain.DUST,
                    new Houses(0, 1),
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    new Crags(0.05f, 4, Terrain.ROCK),
                    Outcrops.off(),
                    Clearings.off(),
                    List.of()));

    static final TestBiome BADLANDS = new TestBiome(
            "badlands",
            new BiomeGround(
                    50,
                    58,
                    3,
                    Terrain.ASH,
                    Terrain.DUST,
                    new Houses(0, 1),
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                    new Crags(0.13f, 4, Terrain.ROCK),
                    new Outcrops(0.02f, 2, 4, 1, 2, 0.3f, 0f),
                    Clearings.off(),
                    List.of()));

    static final List<TestBiome> FIVE = List.of(PLAIN, QUARRY, BRAKE, ANTHILL, BADLANDS);

    /** The rift (G0115): frost cut by fissures laid as dense crags, and two clearings no fissure is laid in. */
    static final TestBiome RIFT = new TestBiome(
            "rift",
            new BiomeGround(
                    64,
                    72,
                    3,
                    Terrain.FROST,
                    Terrain.DUST,
                    Houses.off(),
                    List.of(new Patch.Rect(PatchGround.neighbour(), 0.03f, 4, 6)),
                    new Crags(0.16f, 2, Terrain.FISSURE),
                    Outcrops.off(),
                    new Clearings(2, 8),
                    List.of()));

    /** The redoubt's ground: a fixed width, no dealt coats, its rock four fixed pieces round the anchor. */
    static final TestBiome REDOUBT = new TestBiome(
            "redoubt",
            new BiomeGround(
                    34,
                    34,
                    3,
                    Terrain.DUST,
                    Terrain.ASH,
                    Houses.off(),
                    List.of(),
                    Crags.off(),
                    Outcrops.off(),
                    Clearings.off(),
                    List.of(
                            new TileRect(-9, -3, 2, 3),
                            new TileRect(8, 1, 2, 3),
                            new TileRect(-1, -10, 3, 1),
                            new TileRect(0, 9, 3, 1))));

    /** How much of the biome its rock coats take together. */
    float rock() {
        return ground.crags().density() + ground.outcrops().density();
    }
}
