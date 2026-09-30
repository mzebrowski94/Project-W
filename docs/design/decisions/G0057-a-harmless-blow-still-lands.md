# G0057: A harmless blow still lands

**Status.** accepted

**Trigger.** Adding a damage source or anything that writes `Damage`; a blow that owes nothing or harms a
man not at all; proposing that a hit count only when it does damage, or that some hit spare a shield;
a strain whose contact damage is zero.

**Enforced by.** `DamageTest` (how a tick's blows are recorded and combined, one that owes nothing
included), `DamageSystemTest.forgetsWhereAHitThatOwedNothingCameFrom`, `.turnsAManTowardsAHitThatCostHimNothing`,
`.breaksAShieldWithAHitThatCostsHimNothing`, `.provokesAnAlienWithAHitThatOwedNothing`,
`ShieldTest.takesOneHitOfAnyHarmInsteadOfHim`, `AntTest.takesNobodyWhoHasAShieldInFrontOfHim`,
`CollisionSystemTest.landsNothingFromClawsWithNothingInThem`, `StrikeCombatTest.leavesAProtectedBruteFreeWhenTheSweepConnects`.

**Amends G0053**: a fourth harm, **none**, that costs a man no wound and still lands - it breaks his
shield like any other. Read with G0016: being hit provokes because the hit landed, whatever it owed.

**Constraint.**

- **A blow counts because it landed, not because it owes anything.** `Damage` records whatever lands -
  contact, the end of a grip, getting out of one, a blast - through one call, and `DamageSystem` settles
  every tick's landed blows and clears them all, landed or not. An amount of zero is not "nothing
  happened".
- **None is a harm.** Named, like the other three, for what it does to a man: nothing to his wounds.
  Everything else a landed blow does still happens - **it breaks a shield**, it turns a man towards
  where it came from and provokes an alien, and it throws, stuns or slows him if its source says so.
- **Every gun declares its harm** (`sim.weapon.*.harm`). Gunfire wounds; **venom's shot is none** -
  the spatter is the weapon, so the spit costs a shield or a glance towards the frog, never a wound.
- **A grab on a shielded man is a harmless blow.** The shield breaks where every other hit breaks it,
  with the rest of the tick's blows, and the creature simply lands - so **two grabs on one tick both
  bounce off**, as two blows on one tick already did.
- **Getting out of a grip costs what the grip declares** (`sim.unit.*.grip.escapeHarm`): a wound for
  the one strain that grips.
- **Zero is off for claws.** A strain whose contact damage is zero lands no blow at all - not a
  harmless one, which would take a shield. A gun's zero is different: a gun declares its harm, so a
  shot that owes nothing is still a shot.
- **Protection is settled with the blow.** A protected target has its blows recorded and settled to
  nothing by `DamageSystem`. The one thing a blow does on the spot - the hold a locking swing puts on
  its target - is refused to a protected target where it is given.

**Scope.** When a blow counts and what a harmless one does. It says nothing about which harm any source
should declare beyond the ones named here.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A hit that owes nothing does nothing* - the amount as the gate. It is what this replaces: a man
reads no amount (G0053), so every source that should hurt him had to pass one anyway, a test fixture
kept a blow of one hit point for the purpose, and venom's spit left where it came from on a man for
the next real hit to find.

**No:** *Deriving a harm from the amount* - zero damage meaning none. The same coupling again, and
G0020's rejection of a health threshold says why: lethality is the source's to declare.

**No:** *A harmless hit that spares the shield.* A shield takes one hit of any harm (G0053). A spit
that took nothing would make the shield the answer to venom, when the answer is where you stand.

**No:** *A grab that breaks the shield on the spot.* A second creature landing on the same tick would
then find him bare and carry him off, where two blows on one tick are both taken by the shield.

**No:** *A writer that sets `Damage`'s fields itself.* The rules for combining a tick's blows - the
worst harm, the longest stun, the first source, the higher slot - live in `Damage` once.

**Revisit when** a harmless source should leave a shield alone after all - that is a property of the
source to declare, not a reason to gate on the amount again.
