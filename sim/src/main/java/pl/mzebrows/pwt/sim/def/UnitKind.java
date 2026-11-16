package pl.mzebrows.pwt.sim.def;

/**
 * Every strain a unit can be in. One constant per form, and the form an entity currently has is data
 * on its {@code Strain} component rather than which marker components it carries.
 *
 * <p>That distinction is what makes evolution possible at all. Swapping a marker would mean adding or
 * removing a component in the middle of a tick, which mutates the families being iterated and
 * silently skips entities; swapping this enum is a field write that nothing can miss.
 *
 * <p>Adding a strain is one constant here, one {@link UnitDef} in {@code SimConfig.defaults()} and
 * one block under {@code sim.unit.<name>} in {@code assets/sim/*.properties}. Nothing else - the loader and
 * the registry both iterate this enum, and both fail loudly if a definition is missing.
 */
public enum UnitKind {
    /** The basic enemy: fast, fragile, dies to one hand strike. */
    GRUNT,
    /** The heavy enemy: slower, tougher, survives a hand strike and is knocked back instead. */
    BRUTE,
    /**
     * The shooting enemy: it stops at a distance and spits, roughly, rather than closing. Fragile up
     * close, and a different problem from the two above - one you have to go and solve rather than
     * one that comes to you.
     */
    SPITTER,
    /**
     * The fast one: smaller, thinner, and it notices you from much further off than anything else.
     *
     * <p>What makes it a different problem from a grunt is not its speed but its line - it weaves as
     * it closes and steps aside from shots it sees coming, so it is a target you have to lead rather
     * than one you have to out-run.
     */
    RUNNER,
    /**
     * The one that gets about in hops and ruins the ground it is shooting at.
     *
     * <p>A shooter like a spitter and a different problem from one. It holds a middle distance, and
     * what it throws is not a shot to be hit by but a spatter to be standing in - so the thing to
     * read is the ground rather than the creature. It gets about in bursts with a sit between them,
     * which is what makes it worth shooting at the moment it lands.
     *
     * <p>And it gives up easily: its lose range is barely past the range it notices you at, where
     * everything else keeps hunting you half a screen further. Backing off actually works on one,
     * which is the whole of what makes it a creature to leave alone rather than one to clear.
     */
    FROG,
    /**
     * The one in two segments, whose head turns a bullet and whose leap takes a man away (G0045).
     *
     * <p>A different problem from everything above, twice over. Shooting it in the face does
     * nothing, so a squad standing in a line in front of it is a squad doing nothing - somebody has to
     * get round it, or put a fist in its face. And what its leap lands on is not killed but carried
     * off, so the fight moves: a man is being taken somewhere and the clock on him is running.
     */
    ANT,
    /**
     * The one at the end: a grunt grown too big to walk, sat among holes that keep sending more of
     * everything, and a barrage for anybody who will not come close enough to claw (G0048).
     *
     * <p>A different problem from all of the above because it is the one that does not come to you and
     * cannot be got round: it is a place, like a nest, that shoots in patterns - so what to read is
     * the gap in the next salvo, and what to do is walk into it and keep firing.
     */
    BOSS_GRUNT,
    /**
     * A giant that sears (G0071): a brute half as big again and four times as hard to kill, too heavy to leap,
     * that stands and roars before it sprays a cone of short beams that wound and slow. What to read is the
     * roar; what to do is get out of the cone, or stun it out of it.
     */
    SEARER,
    /**
     * A giant that thumps (G0071): the searer's body, and after its roar a slam - a ring of the ground round it
     * that wounds and stuns every man of the squad in it. What to read is the roar; what to do is not be
     * standing next to it when it ends.
     */
    THUMPER,
    /**
     * The ant turned round (G0073): its armour is on its back, so its face - the end it spits from - is the soft
     * end. It stands, tints, and spits a line of globs that wound a man they hit and leave glue that holds him.
     * What to read is the tint and the line; what to do is step off the line and shoot it in the face.
     */
    SLUDGER,
    /**
     * A brute-sized thing with runners on its back, which it slings over the squad's heads (G0074): each lands
     * behind whoever it was slung at, so the fight arrives from both sides. What to read is the tint; what to do is
     * kill it before it has emptied its back - or after, and take what spills.
     */
    SLINGER,
    /**
     * A small thing that never walks (G0083): slung onto a man, it clings to him and, when his time is up, kills him
     * and goes off in venom. Left alone it goes off where it lies. What to read is the clock on him; what to do is
     * shoot it off him - which costs him a wound of its venom rather than his life - and get him away from the squad.
     */
    LEECH,
    /**
     * A slinger whose riders are leeches, slung onto whoever it hunts rather than past him (G0083). The same body and
     * the same answer as the slinger; what it leaves is a man with a clock on him rather than a runner behind him.
     */
    LEECH_SLINGER,
    /**
     * A spitter whose spit goes off where it lands and leaves fire there (G0091, provisional, for review): the same
     * body and the same way of fighting, so what to read is the ring it wears and the ground its shots leave burning;
     * what to do is what a spitter asks - get to it - without walking through what it has already spat.
     */
    MAGMA_SPITTER,
    /**
     * The desert snake (G0095): a head and six parts behind it, going on and on through the desert in wide arcs and
     * through whoever stands in its way - its head wounds every man it passes through - and now and then turning onto
     * a man it picked to go through him. Nothing slows it and it never stops. What to read is where it is bending and
     * whom it has turned onto; what to do is step out of its way, or shoot it down a part at a time, the head last.
     */
    SNAKE,
    /**
     * The desert worm (G0096): a shadow under the ground nothing can hurt, always coming for the nearest man in its own
     * biome, that surfaces under him - a maw closing on where he stood - and wounds whoever is still inside when it
     * snaps. What to read is the ring opening round your feet; what to do is keep walking, then shoot it while it stands
     * in its hole.
     */
    WORM
}
