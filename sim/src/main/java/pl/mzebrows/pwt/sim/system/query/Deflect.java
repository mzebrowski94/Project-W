package pl.mzebrows.pwt.sim.system.query;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.RngStream;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * Whether a trooper deflects a leap (G0086): meets it with his hand strike as it lands, so it never takes hold of him.
 *
 * <p>In one place because two packages ask it and must get one answer: the squad's, which swings as the leap comes
 * into his reach, and the contact rules', which refuse the grip on landing - a swing that has not landed yet must not
 * leave the creature holding him.
 *
 * <p><strong>A chance the strain's leap declares, keyed once per leap and man</strong> (G0076's shape): the same
 * answer every tick of that leap, on every machine, without advancing a stream - so a no is a no for that leap, and
 * the next leap asks again. <strong>Only inside his arc</strong>, judged by the facing he has turned to: from behind,
 * never - that is what his turn speed is for. <strong>Only a leap coming at him</strong>: its flight, fixed at launch,
 * meets his body - one aimed at a mate beside him is the mate's. <strong>Only with his strike in hand</strong>: ready,
 * or already swung at this very leap ({@code Trooper.metLeaper}, {@code metLeap}) - a strike still coming back from
 * another swing meets nothing. A man, not the commander, who has his own parry; and not a slung rider, which nothing
 * touches in the air.
 */
public final class Deflect {

    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Melee> MELEE = ComponentMapper.getFor(Melee.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private Deflect() {}

    /** Whether this man deflects this creature's leap in the air now, keyed from the level's seed. */
    public static boolean deflects(long levelSeed, Entity leaper, Entity man) {
        if (!LEAP.has(leaper) || !TROOPER.has(man) || !MELEE.has(man) || !POSITION.has(leaper) || !POSITION.has(man)) {
            return false;
        }
        Leap leap = LEAP.get(leaper);
        if (leap.deflectChance <= 0f || !leap.airborne() || leap.slung()) {
            return false;
        }
        if (!HANDLE.has(leaper) || !HANDLE.has(man) || !BODY.has(leaper) || !BODY.has(man)) {
            return false;
        }
        Trooper trooper = TROOPER.get(man);
        long leaperId = Handles.of(leaper);
        boolean swungAtIt = trooper.metLeaper == leaperId && trooper.metLeap == leap.leaps;
        if (!MELEE.get(man).ready() && !swungAtIt) {
            return false;
        }
        Position at = POSITION.get(man);
        Position coming = POSITION.get(leaper);
        if (!Sight.withinArc(trooper.lookX, trooper.lookY, at.x, at.y, coming.x, coming.y, trooper.arcCosine)
                || !comesAt(leap, coming, at, BODY.get(leaper).radius + BODY.get(man).radius)) {
            return false;
        }
        return Rng.keyed(levelSeed, RngStream.LEAP, leaperId, Handles.of(man), leap.leaps) < leap.deflectChance;
    }

    /** Whether a leap from here, on its fixed flight, meets a body there: the ray passes within the two bodies. */
    private static boolean comesAt(Leap leap, Position from, Position body, float touch) {
        float toX = body.x - from.x;
        float toY = body.y - from.y;
        float along = Math.max(0f, toX * leap.directionX + toY * leap.directionY);
        return Sight.withinRange(
                from.x + leap.directionX * along, from.y + leap.directionY * along, body.x, body.y, touch);
    }
}
