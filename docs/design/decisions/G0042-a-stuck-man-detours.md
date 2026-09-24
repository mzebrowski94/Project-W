# G0042: A man who has stopped getting anywhere detours; he does not plan again

**Trigger.** A unit stuck against terrain or an entity; proposing re-pathing, a nudge, or putting
entities into the routes.

**Enforced by.** `TrooperDetourTest.backsOutOfASlotItHasWalkedIntoAndGoesRoundInstead`,
`.takesNoDetourOnAnOrdinaryWalkOrWhileItStandsStillOnPurpose`,
`ScatterTest.keepsSomethingThatNeverMovesOffRockByTheMarginItIsGiven`.

**Constraint.** Two halves, one for the cause and one for everything else.

- **Nothing that never moves is laid nearly flush against rock.** A scatter placing boulders keeps them
  `keepingOffRock(margin)` off impassable ground, exactly as it keeps them off the map edge.
- **A trooper who is walking and making less than `blockDistance` of ground in `blockSeconds` takes a
  detour**: for `detourSeconds` his way is turned a hundred and twenty degrees to one side - back off and
  across. The side is his own (the sign of his seeded `Approach.bearing`) and alternates with every
  detour. Standing still on purpose resets the clock, so a man who stopped is never a man who is stuck.

**Why.** A playtest of the Adventure had troopers wedged for minutes. A headless probe found the shape: a
boulder a unit or two off rock, leaving a slot narrower than a man. The route knows rock and not entities;
avoidance knows entities and not rock; in the slot both are blind together and nothing measured progress.
5 of the Adventure's 105 boulders made one.

**Why not plan again.** The route is a pure function of terrain, so asking again returns the same route
into the same slot. A recovery has to change something: where the man is standing (a detour), or what the
route knows (entities stamped into it - deferred below).

**Scope.** Troopers walking. Aliens are not given it: a provoked or hunting alien that mills about behind a
wall is an accepted limit already, and a crowd of them is what the solver's own known limit describes.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Re-pathing on a block.* Same terrain, same route, same slot.

**No:** *Teleporting or nudging the stuck man.* A body is moved by its velocity and the solver, never
placed; and a man who jumps is a bug the player can see.

**No:** *Rolling the side from the `Rng`.* G0012's argument: nothing a player could learn is gained by
dice, and a seeded side replays.

**No:** *A rock margin on every scatter.* Something that moves is shoved out of a slot by the solver, and a
man hatched or put down in a corridor has to fit there - a margin would refuse him.

Stamping immovable entities into the routes (flow-field "cost stamps", *Supreme Commander 2*) is
**deferred, not rejected**: the first thing to try if detours turn out to fire often.

**Revisit when** a probe or a playtest shows a man detouring more than once in the same place, or when
Duel, Expanse and Skirmish - which scatter obstacles and units from one scatter, still without a rock
margin - show the same slot.
