package pl.mzebrows.pwt.sim.def;

import lombok.With;
import pl.mzebrows.pwt.map.Randomness;

/**
 * One kind of **worked site** (G0111): what it stands as, how long the work at it takes, how loud starting it is,
 * where a biome lays it, and what stands round it and comes of taking it (G0114). Solid, immovable and unbreakable
 * whatever its kind - no health, on nobody's side - so nothing here says otherwise.
 *
 * @param radius its body, in world units: what blocks men, shots and blasts, and what arm's length is measured to
 * @param workSeconds how long the action key is held at it, by one man at a time, to take it
 * @param rouseRadius how far each fresh attempt at it is heard (G0016); zero rouses nobody
 * @param chance the chance, per cent, that a biome that could lay a housed one lays it in place of a control point,
 *     dealt from the biome's own stream (G0068, G0111); zero never lays it. One laid in the open is counted by its
 *     biome's furniture instead (G0114), and declares zero
 * @param housed whether it stands inside a house, against a wall and clear of its doorways - so only a biome that
 *     lays a house (never a gatehouse) can lay it; otherwise it stands in the open, in its own room
 * @param action what the action key offers at it, and so what its prompt says (G0098)
 * @param look what a renderer draws it as
 * @param room how far round its middle the ground must be open where it is laid in the open - no rock, no other
 *     ground than its biome's base, nothing kept and nothing else laid (G0114); and what it leaves and hatches when
 *     taken lies outside it. Zero is its body alone
 * @param guards the emplacements put down round it with it, woken by its first attempt and silenced by its taking
 *     (G0114); off for a kind with none
 * @param hatch what comes up round it the tick it is taken (G0114); off for a kind that hatches nothing
 * @param troopersWork whether a trooper makes it his objective - walks to it, stands round it and works it - as he does
 *     a control point (G0111); false leaves it to the commanders, the squad passing it over for the next site. A site
 *     with guards is stood round inside their lanes (G0114)
 */
@With
public record WorkedSiteDef(
        float radius,
        float workSeconds,
        float rouseRadius,
        int chance,
        boolean housed,
        ActionKind action,
        Look look,
        float room,
        Guards guards,
        Hatch hatch,
        boolean troopersWork) {

    public WorkedSiteDef {
        Require.positive("worked site radius", radius);
        Require.positive("worked site workSeconds", workSeconds);
        Require.notNegative("worked site rouseRadius", rouseRadius);
        Require.percent("worked site chance", chance);
        Require.notNegative("worked site room", room);
        if (!action.worksASite()) {
            throw new IllegalArgumentException("a worked site is worked, not " + action);
        }
        if (!housed && chance != 0) {
            throw new IllegalArgumentException(
                    "a worked site in the open is counted by its biome's furniture, so its chance is 0, was " + chance);
        }
    }

    /**
     * Refuses guards that could not stand round a site: each kind's guards fire a fixed volley, since nothing deals a
     * cycle when a site is put down, and stand inside its room, bodies and all. Once, when the config is built.
     */
    public static void checkGuards(
            Defs<WorkedSiteKind, WorkedSiteDef> sites, Defs<EmplacementKind, EmplacementDef> emplacements) {
        for (WorkedSiteKind kind : WorkedSiteKind.values()) {
            WorkedSiteDef site = sites.of(kind);
            if (!site.guards().on()) {
                continue;
            }
            EmplacementDef guard = emplacements.of(site.guards().kind());
            if (!(guard.cycle() instanceof CycleDef.Volley)) {
                throw new IllegalArgumentException(kind + "'s guards fire a fixed volley, not " + guard.cycle());
            }
            float reach = site.guards().offset() + guard.radius();
            if (site.room() < reach) {
                throw new IllegalArgumentException(
                        kind + "'s room holds its guards, so it is at least " + reach + ", was " + site.room());
            }
        }
    }

    /** How far round its middle nothing else is laid: its room, or its body when it has none. */
    public float clearing() {
        return Math.max(radius, room);
    }

    /**
     * The emplacements standing round a worked site (G0114): one of {@code kind} on each of the four diagonals -
     * north-east, south-east, south-west, north-west - {@code offset} from its middle. They fire a fixed volley, never a
     * dealt cycle, since nothing deals one when a site is put down.
     *
     * @param kind what stands there
     * @param offset how far from the site's middle, along the diagonal; zero is none
     */
    public record Guards(EmplacementKind kind, float offset) {

        public Guards {
            Require.notNegative("worked site guards offset", offset);
        }

        /** None: what every site but the relic declares. */
        public static Guards off() {
            return new Guards(EmplacementKind.SPIRE, 0f);
        }

        /** Whether any stand there. */
        public boolean on() {
            return offset > 0f;
        }

        /**
         * How far off the site's middle, north or south and east or west, each guard stands - and so where the lanes
         * of a guard firing along the compass lines run (G0114): the diagonal offset's share of each.
         */
        public float lane() {
            return offset * EmplacementDef.bearingX(1);
        }
    }

    /**
     * What comes up round a worked site the tick it is taken (G0114), as a nest's brood comes out of it (G0074): from
     * {@code min} to {@code max} of {@code strain}, within {@code radius} of its middle, outside its room and off the
     * men.
     *
     * @param strain what comes up
     * @param min the fewest
     * @param max the most; zero is none
     * @param radius how far from its middle the farthest may come up
     */
    public record Hatch(UnitKind strain, int min, int max, float radius) {

        public Hatch {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("a hatch runs from at least 0 up, was " + min + ".." + max);
            }
            Require.notNegative("worked site hatch radius", radius);
        }

        /** Nothing: what every site but the relic declares. */
        public static Hatch off() {
            return new Hatch(UnitKind.GRUNT, 0, 0, 0f);
        }

        /** How many come up, drawn. */
        public int count(Randomness rng) {
            return min + rng.nextInt(max - min + 1);
        }
    }
}
