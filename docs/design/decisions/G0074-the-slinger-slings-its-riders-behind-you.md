# G0074: The slinger slings its riders over you, and they land behind you

**Status.** amended by G0083: a slinger whose riders are leeches, slung onto its target

**Trigger.** Adding a strain that carries, throws or launches another; anything that cannot be hurt for a while; a
body that passes through bodies; reloading a slinger; making a blast decisive against everything again.

**Enforced by.** `SlingerTest`, `SystemOrderTest` (its three rows), `MissionSweepTest`.

**Settled with Mateusz, 2026-10-06**, adding the slinger to the anthill, the badlands and the cut.

**Constraint.**

- **The slinger is a brute's numbers in a bigger body** (radius 2.9, 300 health), brown, carrying **riders** - three
  runners - on its back. Riders are a **count** on its `Sling`, never entities, until one is slung (ADR 0016). They
  are drawn as runner-coloured discs on its back, one fewer for every sling (pillar 5), and they are **never
  reloaded**. It notices as far off as it can sling (32), where a brute notices at 24.
- **A sling** goes at the target its `Perception` names (G0026), once he is 16-32 off. It stands for a 0.5 s tint
  (the winding-up state every leap and barrage shows), then the rider leaves **aimed once**, at a place 5 past him
  along the line. 2 s between slings. **A declared stun breaks the tint off and the rider stays aboard**; a stagger
  does not (G0071's rule).
- **A slung rider is a leap** with `Leap.untouchable` set and its `Collider.throughBodies` set. Nothing lands on it,
  **a blast included**. It passes through every body, a shot included, and touches nothing; scenery (solid and
  immovable: boulders, trunks, bodies in their throes, a rooted boss), rock, shut doors and the arena's edge stop it,
  and it **comes down where it stops**. Drawn over its shadow.
- **Down, it is harmless for 0.3 s** - on the field and killable, its claws sheathed (`ContactDamage.sheathe`) - and
  then an ordinary runner, already hunting, with its own leap's cooldown running.
- **Once its back is empty it leaps like a brute**; while it carries riders it does not leap ("back full", as the ant's
  grip is "jaws full").
- **When it dies, the riders still aboard spill** within 6 of its body, through `Brood` - the nest's last-brood rule:
  nearest ground first, never on its body, never within `sim.nest.hatchKeepOff` of a man - already hunting, with **no
  rouse**. A rider slung or spilled carries the slinger's wave stamp.
- **The blast row is amended**: a blast is decisive on everything standing in it **but a rider in the air**.

**Why a count and not entities aboard.** Carried entities would be bodies off the field - the `Crew` problem for each
of them, every system asked to ignore them, and an `Entity` held across ticks. A count says everything the game
shows: how many, and what they are.

**Why a leap.** A rider in the air has every property a leap has - aimed once, flown at a fixed speed, owns its
velocity, ends - and the leap system already runs it. The new part is two fields; "passes through bodies" is on the
`Collider`, because the solver reads nothing else.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

- *Riders as entities carried on its back* - above.
- *Reloading a slinger* - from a nest, from food, over time. Three is what it has; killing it early is the answer.
- *A rider that can be shot down in the air* - it is a tell to read where it lands, not a clay pigeon. Kill the
  slinger during the tint, or stun it.
- *A sling steered after it leaves* - G0012's argument and every leap's: an attack that follows cannot be dodged.
- *A rouse when the riders spill* - a slinger is not a nest; what spills is already on top of you.

**Revisit when** play says a landing cannot be read in a crowd (a bigger shadow, or a mark where it will land - drawn
only, never state), or that a slinger dies with its back full too often for the sling to matter.

## Amended by G0083

A second slinger, the **leech slinger**, carries leeches rather than runners and slings them onto its target (`landBehind` 0), live the moment they land (`landingSeconds` 0). Everything else here holds for it.
