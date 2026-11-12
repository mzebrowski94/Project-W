# G0045: The ant is got round, and its leap takes a man away

**Status.** amended by G0083: a grip may cling - by touch, leaving its man free, spent on finishing him

**Trigger.** Adding or changing armour, a creature a shot cannot hurt from one side, a leap that does
anything but bite, or anything that carries a unit off; or proposing that a held man free himself by
anything but landing blows.

**Enforced by.** `AntTest.turnsAShotOffItsHead`, `.takesAShotInTheBack`, `.takesAShotFromTheSide`,
`.takesAFistInTheFace`, `.goesDownToABlastWhicheverWayItFaces`,
`.takesHoldOfAManItLandsOnRatherThanKillingHim`, `.carriesHimOff`, `.showsHowLongHeHasLeft`,
`.finishesHimWhenHisTimeIsUp`, `.letsGoOfHimWhenItDies`, `.leavesAManInItsJawsNoGun`,
`.letsAManInItsJawsSwingAtIt`, `.hasATrooperInItsJawsSwingAtIt`,
`.leavesATrooperWhoNeverStrugglesInItsJaws`; `AdventureTwoScenarioTest.putsAntsInEveryBiomeButTheFirst`.

**Constraint.** Two abilities, each a definition every strain carries and all but one switch off
(`ArmourDef`, `GripDef` - "zero means off"), so the ant is a constant, two definitions and a
`game.properties` block (G0008).

- **The head is armour against gunfire, judged by which side the shot arrives from.** A projectile
  whose flight points back into the arc round the creature's `Facing` lands nothing and is spent. The
  flanks and the back take a shot as anything does. **Only gunfire**: a sweep, a blast and fire land
  wherever they land, so a fist in its face is how its head is hurt. The body is still one circle
  (ADR 0007); the client draws it as two segments along the facing, the head darker, inside that
  circle.
- **Its leap grips instead of biting.** Airborne contact with a man on his feet whom nothing else holds
  takes hold of him and ends the leap - never a blow. Jaws full, it bites nobody. On the ground it bites
  like anything else.
- **A carry is a place and a clock.** `GripSystem` walks it straight away from the nearest other man on
  his side, as a desired velocity so avoidance still bends it, and pulls him along at arm's length by
  velocity so the solver keeps him out of rock. At the end of `holdSeconds` it lands its own blow with
  the lethality its claws declare (G0020). His time is published on him, read backwards like a
  casualty's (pillar 5).
- **Killing it is the only way out, and struggling is how a held man helps.** He is held (`Drivers`):
  no driving, no gun. The one thing he may still do is swing, and a swing lands on it like anybody
  else's. A trooper swings on `AiChance.STRUGGLE`, slow on purpose.
- **The pair is two handles** (`Grip.load`, `Gripped.by`, ADR 0016), and either side letting go of the
  other lets go of both.

**Scope.** The one strain. Nothing about where ants are placed - that is content (Adventure 2).

**Why direction rather than where the shot touches.** The body is one circle and will stay one (ADR
0007); a head that were a second body would need a joint, a second set of contacts and a rule about
which of the two a shot "meant". The side a shot arrives from is one dot product against a facing the
renderer already draws, so the head a player sees is exactly the head that is armoured.

**Why the carry flees the rescuer.** It is what turns a grab into a chase rather than a wait, and it
turns the creature's soft end towards the men coming for him - the one moment the thing is easy to
shoot is the moment it has taken somebody.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *The head as a second entity or a second collider.* See above; one body, and the arc is a
number on it.

**No:** *A struggle meter - so many presses and he is free, the creature unharmed.* A man gets out by
killing it; a meter would be a second health bar on the creature that only its victim can see.

**No:** *Troopers who know about the head and hold fire at a face.* They shoot anyway; getting round it
is the player's job.

**No:** *A grab that kills on landing, or one that ends when the creature is merely hit.* The first is
a grunt with extra steps; the second makes a single stray round a rescue.

**No:** *Armour that turns blasts or fists too.* Then nothing but a flank would ever hurt it, and a
creature you can only hurt from one side in a corridor is a wall.

**Revisit when** a second creature wants armour on a side other than its front, or a carry that goes
somewhere rather than away from somebody (a nest, a drop). The first is an arc centred off the facing;
the second is a destination on the grip, and this record is what it has to argue with.

## Amended by G0083

A grip declares three more things (`GripDef`): whether it **pins** - the ant does; a leech **clings**, leaving its man his legs and his gun, carried on his edge - whether it takes hold **by touch**, on the ground as in the air, and so never bites, and whether finishing him **spends** it. "Killing it is the only way out" holds for both. A man a cling lets go of is thrown clear of nothing: what getting it off costs him is what it leaves when it dies.
