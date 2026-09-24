# G0039: A chance is dealt, not tossed

**Trigger.** Changing how `Odds` decides whether an occasional behaviour happens, adding a new kind of
chance, or proposing a "pity timer", "bad luck protection" or "pseudo-random distribution".

**Enforced by.** `OddsTest.keepsToTheStatedRateHoweverItIsDealt`, `.neverLetsMoreThanTheLongestGapGoBy`,
`.banksNothingFromASpellAtAChanceFarAboveOnePerTick`, `.drawsNothingAtAllForABehaviourThatIsSwitchedOff`.

**Constraint.** Every `AiChance` still states a chance per second of opportunity, and that number is
still the average, exactly. What changed is the shape around the average. A coin tossed every tick has
no memory, so luck runs: a once-every-two-seconds behaviour came twice within half a second one time
in five and failed to come for six seconds one time in twenty. Now each unit carries what it is still
**due** for each behaviour - a budget drawn after every occurrence, averaging one - and each tick of
opportunity spends it down by what that tick is worth. No wait is longer than the **longest gap**
(`sim.ai.chance.longestGap`, 2 by default) times the average.

Wherever the design says a behaviour is "a roll" - a bite (G0022), a trooper going to a man who is
down (G0023) - it now means a dealt chance. Nothing about *when* the chance is taken changed: the aid
decision is still taken once on the way in and never re-taken, and a bite is still only while touching.

The due is hidden, and that is a judgement against pillar 5: it only trims runs of luck the player
would otherwise notice as streaks, and there is nothing in it to learn or track.

**Scope.** The per-second chances in `AiChance`. It says nothing about draws that are not a yes/no
over time - where a wanderer goes, how wide a shot flies, how long a leap hesitates - and nothing about
a dodge, which draws no number at all (G0012). A one-off yes/no per attempt (a critical hit, a drop)
does not exist yet; see the open questions.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Warcraft 3 and Dota 2's pseudo-random distribution, P(N) = C x N per tick.* The constant C has
to be solved numerically for every chance at every tick length, so a strain override or a retuned tick
means solving again; and a chance that changes while a unit is waiting - aid for a trooper and aid for
the commander share one willingness - has no C at all. The due gives the same even deal with the
average exact by construction and no solver.

**No:** *A pity counter on top - "guaranteed after K failures".* The longest gap already is one. A
second rule for the same promise is two numbers that can disagree.

**No:** *A longest gap per behaviour or per strain.* It is not an opinion about a behaviour; it is how
far the game lets luck run before it steps in. One number, on the shared table.

**No:** *Keeping the coin as the default and dealing evenly on request.* Every behaviour on the list
is one a player notices as a streak when it clumps - three sweeps in a second, a barrel nobody bites
for a minute. Zero is still there for a scenario that wants the coin.

**Revisit when** a chance has to be taken per attempt rather than per second (a hit, a drop), or when
rollback lands (ADR 0012) and a draw that depends on how many draws came before it starts to cost more
than a keyed one would.
