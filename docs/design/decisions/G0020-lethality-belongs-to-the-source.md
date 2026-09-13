# G0020: Lethality belongs to the source, not to the amount

**Trigger.** Adding a damage source, changing what happens at zero health, or proposing that how a
unit dies should depend on how badly it was hurt.

**Enforced by.** `CasualtyTest.leavesATrooperShotDownOnTheGroundRatherThanDead`,
`.killsATrooperOutrightWhenTheBlowIsOneThatKills`,
`.finishesACasualtyWithTheNextHitThatReachesHimWhateverItIs`.

**Constraint.** Every blow declares, as data, whether it kills outright or leaves a **casualty**:
a man on the ground with a **bleed-out** running, who can be **stabilised** back onto his feet.
Gunfire and blasts leave casualties. Claws, a melee sweep and a leap kill. The flag rides on
`ContactDamage`, travels on the `Damage` owed, and is read in exactly one place - `DamageSystem`, at
the moment health reaches zero. **Fatal wins when hits combine**, as the longest recovery window
does.

A unit that nothing on its side could come and rescue simply does not carry a `Casualty` component
and dies as it always did. That is the whole of why no alien can be one.

**Scope.** What happens at zero health, and nothing else. It says nothing about how much damage a
source does, how often, or to whom - those are still numbers in `game.properties`. It does not touch
the death path: a casualty who runs out of time dies through the same `Deaths.kill` a bullet would
have used, so there is one kind of corpse.

**The rule that costs nothing.** A casualty is at zero health, so the next hit of any kind takes him
below it again and the ordinary death path runs. Being shot while you lie there is fatal, and so is
being eaten, and neither is a special case anybody had to write. That is the property to preserve if
this is ever reworked: it is what makes leaving a man in the open a real risk rather than a timer.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A health threshold - below zero by more than X kills, otherwise a casualty.* The obvious answer, and
the one that will be proposed the first time a sidearm round feels too forgiving. Rejected because it
makes what happens to you depend on arithmetic you cannot see, which pillar 5 refuses: the player
would learn "sometimes he gets up" rather than "bullets knock you down and claws kill you". It also
makes every weapon's damage number a *second* decision about lethality, so retuning a gun silently
changes the rules of rescue.

**No:** *A chance to go down instead of dying.* Cheap to write and it reads as drama. Rejected for pillar 6
as much as pillar 5 - a roll here is a roll in the most consequential moment in the game, and "he
usually gets up" is not a rule anybody can play around.

**No:** *A `Casualty` component added at the moment a man goes down, and removed when he is picked up.* The
natural ECS-shaped answer. Rejected because adding or removing a component mid-tick mutates the
families being iterated and silently skips entities - the same trap `Dying`, `Damage` and `Stunned`
are all carried permanently to avoid. The component is permanent and `begun` is the switch.

**No:** *Letting the commander be a casualty too.* Attractive, and where this is probably going. Rejected for
now because only the commander can stabilise anybody, so nothing on the field could pick him up: the
animation would promise a rescue the game cannot deliver, which is a worse pillar-5 failure than
simply killing him. One line in `Spawner` when co-op or trooper-assisted revival lands.

**No:** *Making a blast wounding for some things and fatal for others.* Rejected because G0018 says a blast is
one circle with one meaning inside it. It is uniformly **decisive** - everything standing in it goes
down - and what it leaves behind is a fact about the victim, not about the blast.

**Revisit when** somebody can pick the commander up: that changes the "no" above from a design
decision into an obsolete restriction. Also revisit if it turns out that troopers spend the whole
game on the ground, which would mean the bleed-out is doing the work a difficulty setting should.
