package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One kind of **worked site** (G0111): what it stands as, how long the work at it takes, how loud starting it is,
 * and where a biome lays it. Solid, immovable and unbreakable whatever its kind - no health, on nobody's side - so
 * nothing here says otherwise.
 *
 * @param radius its body, in world units: what blocks men, shots and blasts, and what arm's length is measured to
 * @param workSeconds how long the action key is held at it, by one man at a time, to take it
 * @param rouseRadius how far each fresh attempt at it is heard (G0016); zero rouses nobody
 * @param chance the chance, per cent, that a biome that could lay it lays it in place of a control point, dealt
 *     from the biome's own stream (G0068, G0111); zero never lays it
 * @param housed whether it stands inside a house, against a wall and clear of its doorways - so only a biome that
 *     lays a house (never a gatehouse) can lay it
 * @param action what the action key offers at it, and so what its prompt says (G0098)
 * @param look what a renderer draws it as
 */
@With
public record WorkedSiteDef(
        float radius, float workSeconds, float rouseRadius, int chance, boolean housed, ActionKind action, Look look) {

    public WorkedSiteDef {
        Require.positive("worked site radius", radius);
        Require.positive("worked site workSeconds", workSeconds);
        Require.notNegative("worked site rouseRadius", rouseRadius);
        Require.percent("worked site chance", chance);
        if (!action.worksASite()) {
            throw new IllegalArgumentException("a worked site is worked, not " + action);
        }
    }
}
