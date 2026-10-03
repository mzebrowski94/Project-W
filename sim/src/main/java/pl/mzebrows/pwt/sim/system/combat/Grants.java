package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * What a grant does to whoever it is handed to, and whether it would do anything for him - the one place
 * both are decided, whether the pickup was walked onto ({@code PickupSystem}) or chosen out of a crate
 * ({@code CrateSystem}, G0056).
 *
 * <p>{@link #worthTo} is public because the interface asks it too, to show which of a crate's cards its
 * opener cannot take.
 *
 * <p><strong>The switch is exhaustive over a sealed type on purpose</strong>: a new grant does not
 * compile until somebody decides what it does. Nothing here allocates - a grant is a value, not a call.
 */
public final class Grants {

    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private final Spawner spawner;
    private final Modifiers modifiers;

    public Grants(Spawner spawner, Modifiers modifiers) {
        this.spawner = spawner;
        this.modifiers = modifiers;
    }

    /**
     * Whether a grant would do anything for <em>this</em> taker (G0006 as amended by G0053). A medkit is
     * worth something only to a man with a wound; everything else is worth taking by anybody its side is
     * offered it. Asked of the grant and the taker, never of a flag on the pickup.
     */
    public static boolean worthTo(Grant grant, Entity taker) {
        return switch (grant) {
            case Grant.Mend _ -> VITALITY.has(taker) && VITALITY.get(taker).wounded();
            case Grant.None _, Grant.Detonate _, Grant.Arm _, Grant.Feed _, Grant.Mutate _, Grant.Reinforce _ -> true;
        };
    }

    /**
     * Hands one grant to one taker.
     *
     * @param levels the level entity, for a grant that belongs to the level rather than to the taker
     */
    public void apply(Entity taker, Grant grant, ImmutableArray<Entity> levels) {
        switch (grant) {
            case Grant.None _ -> {
                // Nothing on offer, so nothing to do.
            }
            case Grant.Detonate _ -> charge(taker);
            case Grant.Arm(WeaponKind gun) -> spawner.applyWeapon(taker, gun);
            case Grant.Feed(int amount) -> feed(taker, amount);
            case Grant.Mutate(UnitKind into) -> mutate(taker, into);
            case Grant.Reinforce(int troopers) -> reinforce(troopers, levels);
            case Grant.Mend _ -> mend(taker);
        }
    }

    /**
     * What is left of a barrel once it has gone off in something's face.
     *
     * <p>Nothing, unless the strain names a {@code chargedGun} (G0022); the blast is the barrel's own
     * death (G0018).
     */
    private void charge(Entity taker) {
        if (!RANGED.has(taker)) {
            return;
        }
        Ranged ranged = RANGED.get(taker);
        if (!ranged.enabled || ranged.chargedGun == null) {
            return;
        }
        spawner.applyWeapon(taker, ranged.chargedGun);
    }

    /** Every wound cleared, through the one method that heals a man (G0053). */
    private static void mend(Entity taker) {
        if (VITALITY.has(taker)) {
            VITALITY.get(taker).recover();
        }
    }

    /** Counts towards whatever the taker's strain evolves into. {@code EvolutionSystem} decides when. */
    private static void feed(Entity taker, int amount) {
        if (FEED.has(taker)) {
            FEED.get(taker).add(amount);
        }
    }

    /** Names the strain the taker is about to become. {@code EvolutionSystem} is what performs it. */
    private static void mutate(Entity taker, UnitKind into) {
        if (FEED.has(taker)) {
            FEED.get(taker).mutateInto(into);
        }
    }

    /**
     * A grant that belongs to the level rather than to whoever picked it up.
     *
     * <p>The level is an ordinary entity, so no message queue (G0004). What a crate of men is worth is
     * read here, where it is spent.
     */
    private void reinforce(int troopers, ImmutableArray<Entity> levels) {
        int men = modifiers.menPerCrate(troopers);
        for (int i = 0; i < levels.size(); i++) {
            if (LEVEL.has(levels.get(i))) {
                LEVEL.get(levels.get(i)).pendingReinforcements += men;
            }
        }
    }
}
