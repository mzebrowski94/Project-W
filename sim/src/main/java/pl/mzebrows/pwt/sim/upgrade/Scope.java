package pl.mzebrows.pwt.sim.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Whether an effect reaches the unit reading a number: what it is (commander, trooper, alien), for a
 * personal upgrade which slot, and for an adaptation narrowed to one strain which strain. Read from components it already carries, so no tag is added for
 * the upgrades' sake. Anything else - a turret, whose gun belongs to the place (G0024) - is reached by
 * nothing.
 */
final class Scope {

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);

    private Scope() {}

    /**
     * Whether {@code target} reaches {@code who}, for an entry held by {@code slot} ({@code
     * Upgrades.SHARED} for the side's).
     */
    static boolean reaches(Target target, Entity who, int slot) {
        return switch (target) {
            case SQUAD -> TROOPER.has(who);
            case COMMANDERS -> COMMANDER.has(who);
            case CHOOSER -> COMMANDER.has(who) && COMMANDER.get(who).slot == slot;
            case TEAM -> TROOPER.has(who) || COMMANDER.has(who);
            case SIDE -> false;
            case ALIENS -> FACTION.has(who) && FACTION.get(who).side == Side.ALIEN;
        };
    }

    /** Whether {@code who} is an alien of this strain now: one that evolved into it is, one that evolved out of it no longer. */
    static boolean reaches(UnitKind strain, Entity who) {
        return reaches(Target.ALIENS, who, Upgrades.SHARED) && STRAIN.has(who) && STRAIN.get(who).kind == strain;
    }
}
