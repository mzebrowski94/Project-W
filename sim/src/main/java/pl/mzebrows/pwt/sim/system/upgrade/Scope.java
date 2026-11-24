package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Sentry;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Whether an effect reaches the unit reading a number: what it is (commander, trooper, alien), for a
 * personal upgrade which slot, and for an adaptation narrowed to one strain which strain. Read from components it already carries, so no tag is added for
 * the upgrades' sake. A sentry is reached by its setter's personal upgrades alone, through the slot it carries (G0129).
 * Anything else - a turret, whose gun belongs to the place (G0024) - is reached by nothing.
 */
final class Scope {

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Sentry> SENTRY = ComponentMapper.getFor(Sentry.class);

    /** Nobody in particular: the side's own numbers. */
    private static final int NOBODY = 0;

    /** Anything no target reaches - a turret, a barrel. */
    private static final int OTHER = 1;

    private static final int TROOPER_KIND = 2;

    /** An alien with no strain - a nest. */
    private static final int ALIEN = 3;

    /** The first strain's; strain {@code k} is this plus its ordinal. */
    private static final int FIRST_STRAIN = ALIEN + 1;

    /** How many kinds of reader {@link #kindOf} tells apart. */
    static final int KINDS = FIRST_STRAIN + UnitKind.values().length;

    /**
     * A commander: no kind, since a personal upgrade tells him from every other by his slot. There are only as
     * many as the players, so his reads are folded each time rather than kept. A sentry too, which a personal upgrade
     * tells apart by the slot of the man who set it down (G0129): its reads are a pull of its gun, folded each time.
     */
    static final int COMMANDER_KIND = -1;

    private Scope() {}

    /**
     * Which kind of reader {@code who} is, as far as any effect can tell (ADR 0017): two readers of one kind are
     * reached by exactly the same held effects, so a sum folded for one is the sum for the other. Read off the
     * same components as {@link #reaches}: a trooper, an alien and its strain now - or {@link #COMMANDER_KIND}.
     */
    @SuppressWarnings("EnumOrdinal") // an index into Modifiers' cache, never stored or sent
    static int kindOf(@Nullable Entity who) {
        if (who == null) {
            return NOBODY;
        }
        if (COMMANDER.has(who)) {
            return COMMANDER_KIND;
        }
        if (TROOPER.has(who)) {
            return TROOPER_KIND;
        }
        if (SENTRY.has(who)) {
            return COMMANDER_KIND;
        }
        if (FACTION.has(who) && FACTION.get(who).side == Side.ALIEN) {
            return STRAIN.has(who) ? FIRST_STRAIN + STRAIN.get(who).kind.ordinal() : ALIEN;
        }
        return OTHER;
    }

    /**
     * Whether {@code target} reaches {@code who}, for an entry held by {@code slot} ({@code
     * Upgrades.SHARED} for the side's).
     */
    static boolean reaches(Target target, Entity who, int slot) {
        return switch (target) {
            case SQUAD -> TROOPER.has(who);
            case COMMANDERS -> COMMANDER.has(who);
            case CHOOSER ->
                (COMMANDER.has(who) && COMMANDER.get(who).slot == slot)
                        || (SENTRY.has(who) && SENTRY.get(who).slot == slot);
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
