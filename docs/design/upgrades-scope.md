# Upgrades and adaptations — scoped work

**Status: planned 2026-09-28; S0-S8 done 2026-09-29 (S8 awaits its two-copy check); S9 played and passed by Mateusz and S10 built 2026-09-30, so adaptations have shipped and only S11 (blocked) is left; the wounds work in `health-scope.md` (H0-H4) that went ahead of it is done 2026-09-29. D18 amended 2026-09-29: the
draft is shown in the top bar, not the bottom bar.** Read this first in every session of the upgrade work,
and tick the session in §0 when it lands. **This doc expires when adaptations ship (S10).** As each
session lands, what it settles moves where it belongs - invariants and the input map into
`README.md`, words into `glossary.md`, records into `decisions/` and `docs/adr/`, scaffolding into
`alpha-phase.md` - and what is left here at the end is the record of *why* the build order was what it
was.

Written from draft 0 (a technical proposal compared against three external sources), a check of its
claims against `main` at `e7cf245`, and five rounds of questions Mateusz answered the same evening
(§2). Line numbers below are as of `e7cf245`.

## 0. Where it stands

| # | Session | Size | Stops with | Status |
|---|---|---|---|---|
| S0 | Records and words | S | docs only | done 2026-09-29 |
| S1 | Config split and the binder | M | the same game, an identical bound config | done 2026-09-29 |
| S2 | Seams, nothing held | M | the same game, the same hashes | done 2026-09-29 |
| S3 | The draft core | L | drafts earned, dealt, rotated, picked and passed on - headless | done 2026-09-29 |
| S4 | The cheap upgrades and dev keys | M | playable offline with dev keys | done 2026-09-29 |
| S5 | The interface | L | playable offline for real, solo and local co-op | done 2026-09-29 |
| S6 | Firepower: sealed caches and the armoury deck | M | Adventure's caches dealt on reveal | done 2026-09-29; the sealed cache replaced by the crate 2026-09-30 (G0056) - Firepower now stocks every crate with a flamer, and Full Crates joined the catalogue |
| S7 | Explosives: troopers throw | M | troopers grenade packs, never the squad | done 2026-09-29 |
| S8 | Online | L | playable online, drop-in and dropped links included | built 2026-09-29; the two-copy check is Mateusz's |
| S9 | Playtest gate and tuning | S-M | numbers Mateusz has felt | done 2026-09-30: the numbers stand for now |
| S10 | Adaptations, groundwork | M | the swarm adapts in a test | done 2026-09-30; on in both Adventure levels at Mateusz's ask |
| S11 | Carrying upgrades between levels | - | - | blocked on the level lifecycle |

**Order rules.** S1 before S3: the catalogue needs its file. S2 before S3: every stat has its reader
before anything moves it. S3 before everything after it. S5 before S6 and S7, so Firepower and
Explosives are judged through the real interface. S8 depends only on S3 and S5 and may move ahead of
S6 and S7 if online co-op is the next thing to playtest.

## 1. The shape in one screen

- **An upgrade is data, never a new component.** An `UpgradeKind` constant, an `UpgradeDef` and a
  block in `assets/sim/upgrades.properties` (ADR 0008). Its effects are a sealed list of records - the
  `Grant` pattern - so a new *kind of effect* fails the build at every switch that has not decided
  about it.
- **An upgrade changes numbers, never what an entity is made of.** Composition is fixed at spawn (ADR
  0013). Behaviour an upgrade unlocks - troopers throwing grenades - is written once, carried from
  spawn by every unit that could use it, and **off at zero**. The upgrade turns the number up.
- **Numbers are folded where they are read, never written back.** One immutable base (a definition,
  or the copy a component took at spawn), one list of what is held (the **ledger**, on the level
  entity) and one read port (`Modifiers`), called from the one place each number is read. So a unit
  spawned after an upgrade is identical to one that was on the field when it landed, by construction,
  with no on-spawn hook to forget.
- **A draft is state; a pick is an input event.** Objectives earn drafts at a configured rate per
  source. Each draft goes to the next present player in slot order, and its cards are dealt from a
  stream of their own when it is earned. The player opens it when he likes, and his pick crosses
  machines as a numbered input event, like a squad order (ADR 0012). Nothing in the simulation waits.
- **One model for both sides.** The aliens' side-wide upgrades - **adaptations** - use the same
  catalogue, ledger and read port. Only the trigger differs: a schedule instead of a chooser.
- **A fenced package in `sim`.** `sim.upgrade` has four entry points and ArchUnit fences. Its pure
  parts are dependency-free, so lifting them into a module later is a move, not a rewrite (ADR 0017,
  S0).

## 2. Decisions taken (2026-09-28)

| # | Question | Answer | What it means here |
|---|---|---|---|
| D1 | Who benefits from a pick? | **Declared per upgrade** | Every effect names a target: the squad, the commanders, the chooser's commander, the team (squad and commanders), or the side (numbers with no reader entity). An upgrade whose effects all target the chooser is **personal**: its ranks count per slot. Co-op commanders therefore grow distinct kits - a first answer to the open question "four identical commanders?". |
| D2 | Does opening a draft pause? | **Only with one present player** | Solo: opening pauses and closing resumes. With two or more present players, local or online, the game runs on. |
| D3 | How long does an upgrade last? | **The level, for now** | The ledger lives on the level entity. Carrying it on waits for the level lifecycle (S11). |
| D4 | What happens to a draft its chooser does not take? | **Held; passed on if he leaves** | No timer and no auto-pick. A slot that goes away (D19) hands its pending drafts to the next present slot. |
| D5 | Where is a flamer "found"? | **Dealt on reveal** | A level may place **sealed** weapons caches. Each is dealt a cache kind from a weighted **armoury deck** the first time a commander comes near, and shows it from then on. Hand-placed caches keep their gun. Firepower adds flamer cards to the deck. |
| D6 | How does Larger Army's +1..3 work? | **Ranks I-III** | Each rank gives +1 man per crate (3 up to 6) and +2 squad headroom, so the extra men are not wasted. |
| D7 | Who gets Weapon Handling? | **The squad and every commander** | Fire cooldown shortens per rank; heat per shot does not change. |
| D8 | What earns a draft? | **Capturing a control point, clearing a hold wave, killing a boss** | Destroying a nest does not. |
| D9 | Where does the code live? | **A fenced package in `sim`** | `sim.upgrade`, with four entry points and fences in `ArchitectureTest`. |
| D10 | Split `game.properties`? | **Fully, now** | Into `assets/sim/*.properties` by domain, bound as one (S1). |
| D11 | How do the aliens adapt? | **On a schedule, at random; reactive later** | Adaptations are earned by level time and by upgrades the players take (both off at zero), and dealt from the alien pool on a stream of their own. A hook for reactive weights exists but is off. |
| D12 | What can a co-op chooser do while the draft is open? | **Move and shoot** | The draft borrows the squad-order inputs, so he cannot give an order while it is open. |
| D13 | What is the aliens' word? | **Adaptation** | *Evolve* keeps its meaning: one alien changing strain by feeding. |
| D14 | How big is the v1 catalogue? | **The four examples plus about four cheap ones, one of them personal** | See §6.1. |
| D15 | Where do sealed caches appear first? | **About half of each Adventure level's weapons caches** | Tuned in S9. The lair's cache of every gun stays as laid. |
| D16 | How does the draft look in solo? | **The same as in co-op** | One layout (D18); solo also pauses (D2). |
| D17 | Each Adventure level holds about 25 draft sources - how often is a draft earned? | **One per N per source, configurable** | `pointsPerDraft=2`, `wavesPerDraft=4`, `bossesPerDraft=1`. The first of each source earns at once. About ten drafts a level; a scenario may override. |
| D18 | Where is the draft shown? | **The whole top bar turns into it** (Mateusz, amended 2026-09-29; first answer was the bottom bar) | The top bar is already the terminal, where the draft's dispatches land. While a draft is open, its mission, goal, terminal and aside give way to the chooser's portrait and name on the left and his cards across the rest - the same span the bottom bar would have given them. The bottom bar is untouched, so every player's panel and its alarms stay in sight. Nothing is drawn over the view, so the UI spec's rule stands. A machine's top bar shows one draft at a time. |
| D19 | How does the simulation know a player left? | **The host seals away/back** | A dropped link puts an *away* event in the next bundle, and a return puts a *back*. The rotation skips away slots and their drafts pass on. "Solo" means one present player. Amends G0038. |

## 3. Invariant check (`feature` skill, step 2)

### Consistent, no change needed

| Rule | Why it holds |
|---|---|
| Pillar 2: the commander is a unit | The chooser stays on the field; in co-op he keeps fighting. Nothing becomes a god view. |
| Pillar 6: reproducible | Cards are dealt from `RngStream.DRAFT`, sealed caches from `ARMOURY` and adaptations from `ADAPTATION`. Picks and presence are inputs. |
| Pillar 7: online is the same game | Picks and presence are numbered input events. The ledger, drafts, deck and presence live on the level entity, so snapshot, hash, drop-in and heal carry them with no new code. The rotation is keyed by slot and sized from capacity. |
| G0010, ADR 0013 and `onlyTheSpawnerChangesWhatAnEntityIsMadeOf` | No effect adds or removes a component. Capabilities are carried from spawn and switched by a number. |
| "A gun is found and lost, never owned" | Modifiers belong to the side, never to a gun, and no effect hands anyone a gun. |
| "A tally is shown, never spent" (G0044) | Drafts come from objectives only; nothing converts a tally. |
| G0030, do not build: "a reinforcement granted by the capture itself" | No draft, empty or not, ever grants men. |
| G0018 and G0020: a blast is one decisive circle, on nobody's side | Trooper throws follow the safety rules in §6.2. No blast-damage stat exists. |
| G0019: the grenade is a fixed throw | A trooper's throw is the same throw: along a bearing, a fixed range, passing through the living. |
| G0028: a player is a slot | An upgrade belongs to the slot or the side, never to a life. A commander who respawns keeps what his slot chose - the opposite of G0044's tally, stated so nobody "fixes" it. |
| G0039: a chance is dealt, not tossed | Trooper throws go through `Odds`; the armoury deck is a `Bag`. |
| The UI spec's do-not-build: nothing drawn over the view in `UI:play` except modals and the rest | The draft takes the top bar, in the bar and never over the view (D18). |

### Amended

These rows pre-date upgrades. S0 rewrites each one; nothing reinterprets it quietly.

| Rule | Amendment |
|---|---|
| Pillar 1: "a mechanic that makes the player stop and compute belongs in a different game" | One named exception: choosing an upgrade. The player picks the moment, it shows three cards at most with exact numbers, and it pauses only a solo game (G0049). |
| Pillar 3: "no shop, no loadout screen, no economy between levels" | Clarified, not weakened: upgrades are earned on the field, never bought, and last the level. Carrying them on is open (S11). |
| "Squad size never exceeds ... the squad the level started with plus its configured headroom" | "... plus its configured headroom, plus what upgrades add to it." |
| "What one pull of a trigger puts in the air is a property of the gun, not of who is holding it" | Pellets, cone and reach stay the gun's. **How often** and **how hard** may be scaled by the holder's side. |
| `PickupKind` and the glossary's *weapons cache*: "one kind per gun, so which gun is in a crate is legible before he has walked to it" | Still true of every cache anyone can take. A **sealed** cache shows its gun before any commander can reach it (G0050), and a test holds the reveal radius above the cache's reach. |
| G0038: "a dropped connection is an unplugged pad; the commanders stay standing and read as idle" | Still true, and also: the slot is marked **away** until it returns, skipped by the draft rotation, and shown OFFLINE (G0052). |
| `docs/architecture.md`: "systems still all live in `system/`" | Except `UpgradeSystem`, which lives in the fenced `upgrade/` package with the rules it runs (ADR 0017). |
| ADR 0003 and 0015, do not build: "packages plus an ArchUnit rule instead of a module" | Scoped, not reversed. Those records were about making rendering unreachable, and nothing here needs a classpath wall. ADR 0017 says why a package is right for this subsystem. |

### New - the design did not say, and now does

- Every upgrade and adaptation in force is on screen with its rank, and so is who holds a draft
  (pillar 5).
- A card's text is generated from its effects, so tuning a number cannot leave a card lying.
- The relational guarantees `SimConfigTest` holds hold **at every upgrade's and adaptation's
  highest rank**, through the same fold the game uses. Examples: the hand strike deletes a grunt and
  not a brute; the beam runs hottest; the collision cell covers the largest body. Body size is never
  a stat.
- A crewed turret fires the place's gun (G0024), and no v1 target covers it.

## 4. Words (a new *Upgrades* section in `docs/glossary.md`, S0)

| Word | Meaning |
|---|---|
| **upgrade** | A permanent improvement the player side holds for the rest of the level. Kinds are `UpgradeKind`; numbers are `sim.upgrade.<kind>.*`. |
| **adaptation** | An upgrade the aliens take, on a schedule. Not *evolve*, which stays one alien changing strain by feeding. |
| **rank** | How many times one upgrade has been taken (I, II, III). Each rank applies its effects once more. |
| **shared / personal** | Whether an upgrade's ranks belong to the side or to the slot that took it. It is personal when every effect targets the chooser. |
| **effect** | One change an upgrade makes: scale a stat, add to a count, raise a chance, scale one gun, or add cards to the armoury deck. |
| **target** | Whom an effect reaches: the squad, the commanders, the chooser's commander, the team, the side, or the aliens. |
| **stat** | A number an effect may move. It is read in exactly one place; a stat nobody reads is a config key that lies. |
| **ledger** | What each side holds, with ranks. Level state (G0007). |
| **draft** | One offer of up to N upgrades to one player, earned by an objective. He takes one. |
| **card** | One upgrade on a draft. |
| **pick** | Taking a card: an input event naming the draft and the upgrade, never the card's position. |
| **chooser** | The slot a draft belongs to. |
| **rotation** | The order in which drafts go round the present players: slot order, starting after the last chooser. |
| **away** | A slot whose link has dropped. Its commander stands idle, the rotation skips it, and its drafts pass on. |
| **capability** | Behaviour written once and carried from spawn by every unit that could use it, off at zero until an upgrade turns it up. |
| **sealed cache** | A weapons cache whose gun is dealt the first time a commander comes near, and shown from then on. It joins the existing *weapons cache* entry rather than coining a word beside it. |
| **armoury deck** | The weighted cards a sealed cache is dealt from, per level. |

## 5. Architecture

### 5.1 The package and its four entry points

```
sim/src/main/java/pl/mzebrows/pwt/sim/upgrade/
  package-info.java  @NullMarked, and the package's contract in one paragraph
  UpgradeKind        every upgrade and adaptation                          public   definitions
  UpgradeDef         side, weight, ranks, tier, requires, group, effects   public
  Effect             sealed: Scale, Count, Chance, Gun; Deal (S6); Breed (S10)
  Stat               every number an effect may move, with its clamp keys
  Target             SQUAD, COMMANDERS, CHOOSER, TEAM, SIDE, ALIENS
  UpgradeSystem      the only writer; registered by Simulation            public   1. earned
  Picks              Simulation.step hands it the tick's picks            public   2. picked
  Modifiers          one method per stat, handed to systems               public   3. read
  UpgradeView        what the interface may read                          public   4. shown
  Drafting, Dealer, Fold, Rotation, Scope                                 package-private
sim/src/main/java/pl/mzebrows/pwt/sim/component/
  Upgrades, Drafts        on the level entity, added by Spawner.spawnLevel; fenced (fence 1)
  Presence, Armoury (S6)  on the level entity too, but not the package's: written by
                          Simulation.step and by PickupSystem's reveal
```

Every effect names its target (D1), `Gun` included: `gun flamer gun_damage team 25`. The one
exception is `Deal`, whose target is always the side's deck.

**Fences** (in `ArchitectureTest`; each one watched failing against a deliberate probe before it is
trusted - `conventions.md`):

1. Nothing outside `..sim.upgrade..` reads or writes `Upgrades` or `Drafts`, except `WorldCodec`,
   `Components` and the `Spawner` that creates them. Rules ask `Modifiers`. (S3) The interface's
   half - `ui` and `core` read `UpgradeView` and never the components - lives in
   `UiArchitectureTest` and `ClientArchitectureTest`, because `ArchitectureTest` only sees `sim`.
   (S5)
2. `..sim.upgrade..` depends on nothing in `..sim.system..` or `..sim.scenario..`. It is called; it
   does not call. (S2)
3. **Every `Stat` has exactly one reader.** Each `Modifiers` method carries `@Reads(Stat)`. Every
   `Stat` appears on exactly one such method, and each such method has exactly one caller outside the
   package. A stat nobody reads fails the build. (S2)
4. `systemsHoldNoStateAcrossTicks` extends to `..sim.upgrade..`, so a `Modifiers` cache must say
   `@NotState`. (S2)
5. Existing rules cover the rest. `onlyTheSpawnerChangesWhatAnEntityIsMadeOf` rules out component
   toggles. `onlyTheStreamTableStartsARandomStream` keeps new streams in `RngStream`.
   `componentFieldsAreWhatASnapshotCanWrite` admits new lists only by name.

**Why `UpgradeSystem` lives in `upgrade/`.** Mateusz asked for the subsystem to be separable, with
clear entry points. A thin system in `system/` handing everything to package-private rules would
split one concern across two packages, and it would need a fence exception anyway. `SystemOrderTest`
reads `Simulation`'s constructor rather than packages, so the order remains the one source of truth.

### 5.2 State

Everything lives on the level entity (G0007), so snapshot, hash, drop-in and heal carry it with no
new code. Fields are records (ADR 0014). A record cannot hold a list, so each list is a named
collection: one line in `ArchitectureTest.NAMED_COLLECTIONS` and a reason in the review. The codec
already writes any `Array` of records.

| Component | Fields | Written by |
|---|---|---|
| `Upgrades` | `Array<Held> held`, where `record Held(UpgradeKind kind, int slot, int ranks)`: slot is `-1` for a shared upgrade and the taker's slot for a personal one. Also `revision` and `playerPicks`; `adaptations` and `nextAdaptationTick` arrive in S10. | `sim.upgrade` |
| `Drafts` | `Array<Draft> pending`: `record Draft(int serial, int chooser, long earnedTick, int revision)`.<br>`Array<Card> cards`: `record Card(int serial, int position, UpgradeKind kind)`.<br>`Array<Passed> passed`: `record Passed(int slot, UpgradeKind kind)`.<br>`Array<Pick> submitted`: `record Pick(int slot, int serial, UpgradeKind kind)`, emptied every tick.<br>Also `nextSerial`, `lastChooser`, `earned`, `fromPoints`, `fromWaves`, `fromBosses`. | `sim.upgrade` |
| `Armoury` | `drawn` (a `Bag`'s `long`), and `shuffledFor`: the deck's card counts it was shuffled at, packed into a `long` | `PickupSystem`'s reveal (S6). The deck is pickup state; the upgrade package only folds its card counts. |
| `Presence` | `Array<Away> away`: `record Away(int slot)` | `Simulation.step`, from inputs (S3) |

The upgrade package reads three **published facts** (G0004: communicate through components):

- `ControlPoint.captured` already exists.
- `Waves.cleared` is new, written by `WaveSystem`. It is a `long` with bit *w* set once every alien
  wave *w* sent is off its feet, using the test `ObjectiveSystem` already uses (`Dying.begun`). Each
  alien carries the wave that sent it on `Strain.wave`, stamped at spawn through a
  `Spawner.spawnUnit` overload. Waves count from 0 (`Waves.sent`), so the stamp is the wave's number
  plus one, and 0 means placed, not sent. `Enemy` stays a marker. `Strain` survives an evolution, so
  the stamp does too. A level may hold at most 63 waves, checked where waves are added.
- `Level.bossesDown` is new, written by `BarrageSystem` on the tick a boss's throes begin. It is
  latched per boss on `Barrage.fallen`. A boss is a unit whose `BarrageDef` is not off.

### 5.3 One tick

```
PlayerInputs { held, orders, joins, picks, presence }         picks and presence are new
  Simulation.step: joins -> presence -> god mode -> Intent <- held -> Picks.submit
  1-44   systems; the seams ask Modifiers
         (Guns.spend for all four shooters, Guns.volley, Odds, PickupSystem,
          ReinforcementSystem, the throw)
  41     WaveSystem          publishes Waves.cleared                          new
  44     BarrageSystem       publishes Level.bossesDown                       new
  45     ControlPointSystem  ControlPoint.captured
  46 NEW UpgradeSystem       1. apply this tick's picks to the ledger; re-deal cards they made stale
                             2. pass on the drafts of away slots
                             3. earn drafts from the three facts at their rates; deal their cards
                             4. (S10) the adaptations that are due
  47-50  ObjectiveSystem, FacingSystem, AppearanceSystem, ReaperSystem       renumbered from 46-49
  ui     ReadoutWriter <- UpgradeView
```

A pick takes effect from the next tick: one position and one writer, at the cost of 17 ms nobody can
see. S3 adds these rows to `docs/simulation-order.md` and `SystemOrderTest`:

- `UpgradeSystem` (46) runs **after** `ControlPointSystem` (45), `BarrageSystem` (44) and
  `WaveSystem` (41), so a point taken, a boss felled or a wave cleared this tick earns its draft this
  tick.
- `UpgradeSystem` (46) runs **after** every seam that reads `Modifiers`. The ledger changes once per
  tick, at the end, so every reader in a tick sees the same ledger.

### 5.4 The fold

```
scaled stat    = base × (100 + clamp(Σ percent, stat.minPercent, stat.maxPercent)) / 100
                 a duration already in ticks (Weapon.cooldownTicks) is rounded, never below one tick
count          = max(stat.min, base + Σ amount)
chance/second  = max(0, base + Σ perSecond)          base 0: the behaviour is off
Σ runs over the held entries whose effect targets the reader, times their ranks
```

- **Always from the base, on every read.** Nothing is written back, so no running value can drift
  and nothing ever needs undoing. That rules out the float trap source 2 warns about. Percentages are
  whole numbers, so every sum is exact and independent of order, and each read costs one float
  multiply.
- **The base is whatever the reader already has in hand**: a definition, or the copy a component took
  at spawn (`Weapon.cooldownTicks`). An upgrade never writes either, so this needs nothing from
  backlog C4.
- **Who is reading** is `Scope.of(entity)`: side (`Faction`), role (commander, trooper, turret or
  alien), slot (`Commander.slot`) and strain (`Strain.kind`). The targets resolve like this:
  - `SQUAD`: a trooper.
  - `COMMANDERS`: any commander.
  - `CHOOSER`: the commander whose slot holds the entry.
  - `TEAM`: both of the above.
  - `SIDE`: the numbers with no reader entity - men per crate, headroom, the deck.
  - `ALIENS`: the alien side.
  - A turret: nothing covers it (G0024).
- **Cost**: a handful of held entries times a few effects per read, with no allocation. If a profile
  ever shows it, a `@NotState` cache keyed by `Upgrades.revision` is a pure cache, which ADR 0014
  already allows.

### 5.5 The seams: every stat and its one reader

| Stat or effect | The one reader | Change needed | Session |
|---|---|---|---|
| `FIRE_COOLDOWN` (scale, floor -50 %) | `Guns.spend`, for all four shooters | `TrooperCombatSystem.fire` asks `weapon.ready()` and writes `weapon.remainingTicks` itself (line 198). Route it through `Guns.ready` and `Guns.spend` like the other three. **This is a deliberate behaviour change, not an identity.** A trooper can take a weapons cache: `CollisionSystem.claim` asks only for `Taking`, which troopers carry. A trooper holding the laser (self-slow 0.72 for 0.16 s) will now slow himself when he fires, as the commander does. That is what "a property of the gun, not of who is holding it" already says. Heat still never applies to him, because troopers carry no `Heat`. | S2 |
| `GUN_DAMAGE` (scale) | `Guns.volley` | Fold once per volley and hand the number to `spawnProjectile`, which takes it as a parameter instead of copying `weapon.damage` (it has no other caller). | S2 |
| a chance, per `AiChance` | `Odds` | `Odds` asks `Modifiers.chance(unit, kind, base)` and is its only caller, so every `AiChance` becomes an upgradeable number for free. Neither `spend` overload takes an entity today, so both signatures change - the in-hand one, which takes a strain's own override, gains the kind too - and so do their five callers: `TrooperCombatSystem` (two), `TrooperCrew`, `TrooperAid` and `CollisionSystem`. | S2 |
| `MEN_PER_CRATE` (count) | `PickupSystem.reinforce` | none | S2 |
| `SQUAD_HEADROOM` (count) | `ReinforcementSystem`'s cap (line 72) | none | S2 |
| `GRENADE_COOLDOWN` (scale) | one throw helper, `Throws`, shared by `GrenadeSystem` and the troopers | Extract the release from `GrenadeSystem.throwWhateverWasAskedFor`. | S2 |
| `ARMOURY_CARDS` (deal) | the reveal in `PickupSystem` | new (§6.3) | S6 |
| alien stats | the existing `Guns` and `Odds` seams first; a new seam only arrives with its first adaptation | - | S10 |

S2 lands every seam with nothing held. Apart from the trooper change above, which lands first as a
stage of its own with its own test, **the game is byte-identical, and the determinism tests and
hashes are the proof.**

### 5.6 The draft

- **Earned.** `UpgradeSystem` counts the three facts and compares each count with what it has
  already credited to that source. A source yields `ceil(count / perDraft)` drafts: the first capture
  earns at once, then every `pointsPerDraft`-th. Zero switches a source off. When several drafts are
  earned on one tick, they are earned points first, then waves, then bosses.
- **Chooser.** The next present slot after `lastChooser`, in ascending slot order, wrapping round.
  Only slots with a commander count (standing, down or on his way back), and never an away slot.
  Single-player: always slot 0. If nobody is present, the draft goes to the lowest slot with a
  commander and passes on when somebody returns.
- **Dealt** when earned, from `RngStream.DRAFT`: `options` (3) distinct kinds, weighted and without
  replacement, **in deal order** (deal order is card order). A kind is eligible when all of these
  hold:
  - it is on the player side;
  - its ranks are below the maximum - per side for a shared upgrade, per the chooser's slot for a
    personal one;
  - its tier is unlocked (`tierFrom`, counted in drafts earned so far);
  - what it `requires` is held;
  - no other member of its `group` is held;
  - its `weight` is above zero, so a scenario can switch an upgrade off.

  **Anti-streak:** a kind the chooser passed over last time is not dealt to him next time, unless the
  pool would run short. A small pool deals fewer cards. An empty pool earns nothing and says so in a
  dispatch.
- **Pending.** A slot may hold several drafts, oldest first. Nothing expires.
- **Picked.** `pick(slot, serial, kind)` names **the kind, never a card position**. Online, a pick
  lands some ticks after the press; if a card was re-dealt in between, a position would grant a card
  the player never saw. A pick is valid when the draft exists, its chooser is that slot, and the kind
  is on it and still eligible; otherwise nothing happens. One pick per slot per tick.
- **Stale cards.** A pick can make a card on another pending draft ineligible (the last rank, or a
  group). That card is re-dealt in place, excluding the draft's other cards, and the draft's
  `revision` bumps so the interface can show that it changed.
- **Passed on.** While a slot is away, each of its pending drafts moves to the next present slot with
  its cards unchanged, and a dispatch says so.
- **Rerolls.** None. If one is ever wanted, it is an input event, re-dealt from the same stream and
  limited by a count - never by a currency (pillar 3, G0044).

### 5.7 Presence (D19)

- `PlayerInputs.away(slot)` and `.back(slot)` are input events. `Simulation.step` applies them after
  joins, and `Presence` holds the slots that are away.
- **Offline, nothing sends them.** Unplugging a pad pauses the game and is not leaving (G0028).
  **Online, the sequencer seals them** (S8): *away* when a peer's link drops, *back* when a returning
  machine is re-seated.
- The commander stays standing and idle, exactly as G0038 says. Only the rotation and the HUD read
  presence. The UI spec's OFFLINE label notes that "remote disconnection is not published yet"; this
  is the source it was missing.

### 5.8 Capabilities, not components

An upgrade never adds a code path at runtime. Behaviour it unlocks is written once, into the system
that owns it, carried from spawn by every unit that could use it, and off at zero. That is the
codebase's own idiom (`AiChance`, `RangedDef.off()`, `WeaveDef.off()`).

A *tactic* upgrade works the same way. G0010's mind is a field, so a tactic moves numbers the mind
already weighs (`threatBonus`, `sharedTargetPenalty`, `urgency`, `holdSeconds`, the aid chances), or
turns up a dormant behaviour. The trooper AI review's unbuilt proposals - search, bounding advance, a
spot worth standing on - are the natural tactic upgrades after v1.

### 5.9 Config (S1, D10)

**The split.** Files go in `assets/sim/`, one per domain. Each key prefix sits wholly in one file, and
the comments travel with their keys:

| File | Prefixes |
|---|---|
| `world.properties` | `sim.seed`, `sim.tickSeconds`, `sim.maxTicksPerFrame`, `sim.map`, `sim.spawn`, `sim.collision`, `sim.obstacle` |
| `commander.properties` | `sim.commander` |
| `squad.properties` | `sim.trooper` |
| `ai.properties` | `sim.ai.chance` |
| `units.properties` | `sim.unit.*` |
| `weapons.properties` | `sim.weapon.*`, `sim.melee` |
| `pickups.properties` | `sim.pickup.*`, `sim.rabbit` |
| `hazards.properties` | `sim.blast`, `sim.grenade`, `sim.fire`, `sim.barrel`, `sim.mine`, `sim.bloat`, `sim.ground` |
| `level.properties` | `sim.controlPoint`, `sim.nest`, `sim.turret`, `sim.casualty`, `sim.action` |
| `upgrades.properties` | `sim.upgrade.*` (S3), `sim.adaptation.*` (S10) |

- The files are read in name order, concatenated and bound by the one `SimConfig.parse`. **A key in
  two files is an error.** Today `ConfigBinder.parse` quietly keeps the last one; it must report it.
- `generateShippedConfig` reads the directory and writes one constant per file, so its 60 000-character
  guard applies per file and the ceiling is gone. The file list is generated alongside the constants,
  so the loader and the build cannot disagree about which files exist. The generated `ShippedConfig`
  is package-private in `sim`, so `core`'s `SimConfigLoader` reads the list through a public
  accessor (`SimConfig.shippedFiles()`).
- **Every block states every key.** The binder reports each absent record field as missing, and a
  record's `off()` applies only when its whole block is absent. So every stat block carries
  `minPercent`, `maxPercent` and `min`. Every pickup kind that carries an `armoury` block gives both
  its keys (§6.3); kinds that are not weapons caches carry none, and are off.
- **Online is unchanged.** `SimConfigLoader.shipped()` still returns one comment-free text, and the
  host still ships all of it.
- **About "world properties for online init":** every key the simulation reads must be identical on
  every peer, so no smaller "init" subset exists. What could shrink a join is shipping only the keys
  that differ from the built-in defaults. That option was not chosen: at about 26 KB of text, deflated on
  the wire, it is not needed.
- `graphic.properties`, `controls.properties` and `network.properties` stay where they are; they are
  not the simulation's.

**The catalogue** binds like every kind table (`Defs<UpgradeKind, UpgradeDef>`):

```
sim.upgrade.draft.options=3
sim.upgrade.draft.pointsPerDraft=2
sim.upgrade.draft.wavesPerDraft=4
sim.upgrade.draft.bossesPerDraft=1
sim.upgrade.draft.tierFrom=0,3,6
sim.upgrade.stat.fire_cooldown.minPercent=-50
sim.upgrade.stat.fire_cooldown.maxPercent=100
sim.upgrade.larger_army.side=player
sim.upgrade.larger_army.weight=10
sim.upgrade.larger_army.ranks=3
sim.upgrade.larger_army.tier=1
sim.upgrade.larger_army.requires=none
sim.upgrade.larger_army.group=0
sim.upgrade.larger_army.effects=count men_per_crate side 1; count squad_headroom side 2
```

**Binder changes** (S1), each tested in `ConfigBinderTest` like every other reading:

- A sealed record with **N** arguments, such as `count men_per_crate side 1`. Today it takes at most
  one.
- A `List` of sealed records separated by `;`. Today only `List<Integer>` is read.

**Checked at bind time**, in `UpgradeDef`'s constructor and in `SimConfig`. Each of these is an
error:

- a target on the wrong side;
- an upgrade that mixes personal (`CHOOSER`) and shared effects;
- a `SIDE` target on a stat an entity reads, or an entity target on a side number;
- a requirement cycle, or a requirement across sides;
- a group with only one member;
- fewer than one rank;
- a tier with no `tierFrom` entry;
- a `Deal` naming a cache kind that is not in the deck;
- a deck over 64 cards at every upgrade's highest rank (a `Bag` holds 64).

### 5.10 Online (S8, note 6)

- **Typed input events.** Today an event is `(seq, slot, code)`, and the code is always an `Order`.
  It generalises to `(seq, slot, type, payload)`, with `ORDER(code)` and `PICK(serial:int, kind:byte)`.
  Events stay numbered, are resent until acknowledged and are applied once, **one per slot per type
  per tick** - the rules orders already follow. The code that changes:
  - `PeerSession.queueOrders` and `sendInput`, whose room check hard-codes `events * 6`;
  - `Sequencer.input`, `Peer.hold` and `Peer.applyEvents`, which holds back a peer's later events
    while an order waits;
  - `Sequencer.maxBundleBytes`.
- **Presence is the sequencer's own event, not a peer's.** The sequencer knows when a link drops
  (`left(peer, …)`) and when a machine is re-seated, and seals *away* or *back* into the next bundle.
- **`Bundle`** gains two sections, picks `(slot, serial, kind)` and presence `(slot, away)`, in
  `write`, `read`, `skip` and `applyTo`. `Msg.PROTOCOL` goes from 2 to 3.
- **A pause carries a reason.** `PAUSE | on | reason`, and `PAUSED` echoes the reason and the slot.
  Only a solo session ever pauses for a draft (D2). When a *back* ends the solo, the chooser's machine
  resumes.
- **Validation lives in the simulation**, never in the sequencer, which "decides which input applies
  to a tick, never what happened in it". In lockstep, this is what the sources' "server-validated"
  means: every peer validates identically, and any disagreement is a desync the checksum already
  catches.
- **State crosses as a snapshot**, like everything else. A player who drops in mid-level receives the
  ledger, the drafts, the deck and presence.
- **Offline**, picks go through `ScenarioSession.pendingPick[slot]`, spent once per tick like
  `pendingOrder` and remembered while paused.

### 5.11 The interface (S5)

- **The draft bar** (D18). `C`, or `NORTH` on a pad (`Y`/`Triangle`, left free on purpose), opens
  the oldest draft of that seat's slot. The **whole top bar** swaps to it - mission, goal, terminal
  and aside, the dev overlay's panel included - as `` ` `` swaps the bottom bar to the roll call. The
  top bar is the terminal already, the place the game talks to the player, and its span is the
  view's, the same the bottom bar would have given the cards:
  - on the left, the chooser's portrait and `P{n}`;
  - across the rest, his cards. Each shows a name with the rank it will have once taken
    (`LARGER ARMY II`) and up to two effect lines generated from the definition (`+1 MAN PER CRATE`,
    `+2 SQUAD ROOM`);
  - the highlighted card is framed in his colour, and `1/2` shows when he holds more than one draft.
- **A machine's top bar shows one draft at a time.** If another local seat presses `C` while the bar is
  taken, it only shows whose draft it is. Other machines are not affected.
- **Input while the draft is open** comes only from the chooser's seat, and only from the order
  inputs and the draft key:
  - `1`-`3`, or left/right on the d-pad, choose a card;
  - `C`/`Y` takes it;
  - `4`, or down on the d-pad, closes the draft and keeps it.

  His move, aim, fire, strike, grenade and action stay live (D12), but his order inputs give no order
  while it is open. This needs a per-seat input context in `InputReader`, which today has a single
  machine-wide `neutral` flag. *These bindings are proposed rows until S5 settles them.*
- **Taking a card closes the bar.** If another draft is pending, the panel still shows it and `C`
  opens it. So a solo pick always lands on a tick before the next draft is read.
- **Solo pause** (D2). With one present player, opening the bar pauses the game
  (`ScenarioSession.setPaused`; online, `OnlineSession` with the draft reason) and closing it
  resumes. The PAUSED banner does not show over a draft.
- **The bottom bar is untouched while a draft is open**, so every player's panel and its alarms
  (DOWN, KIA, OVERHEAT) stay in sight - the reason D18 moved. What the draft hides is the mission,
  the goal line and the terminal. The goal line comes back as it was when the bar closes. S5 decides
  whether `Terminal` holds its queue while the bar is up, so a dispatch said meanwhile - *P2 TOOK
  LARGER ARMY II* among them - types out after it rather than ageing out unseen
  (`graphic.hud.terminal.maxAgeSeconds`).
- **The pending tell.** Each player's panel shows `UPGRADE ×n` and the key while his slot holds
  drafts, within its 19-column text budget.
- **The aside strip** (`UI:hud/top/aside`, reserved - plan Q30) shows the side's upgrades with rank
  pips, and later the aliens' adaptations. This is a first answer to Q30. The F1 overlay, off by
  default since 2026-09-29, still takes the strip when it is stepped on. While a draft is open the
  strip is hidden with the rest of the bar; each card names the rank it will have, so the chooser
  still sees what he holds of what is offered.
- **Dispatches** in the terminal, narrative only:
  - a draft earned;
  - an upgrade taken (`P2 TOOK LARGER ARMY II`);
  - a draft passed on;
  - nothing left to learn;
  - in S10, the swarm adapting.

  `Terminal.offer(kind, slot, a, b)` carries the kind's ordinal and the rank, and `DispatchWords`
  learns upgrade names.
- **Text** comes from `ui.upgrade.<kind>` names and one template per effect (`ui.effect.<effect>.<stat>`),
  formatted when a draft changes and never per frame. Glyphs are placeholders; no art without a
  record of its own (alpha rule).
- **Spec rows.**
  - In `menu_layout_agent.md` §7: `UI:hud/top/draft` (`HudSlot.DRAFT`), and the tell with a
    `HudSlot` constant of its own. `UiSpecTest` pairs §7 with `HudSlot` (and §3 with `UiPage`), so
    each row lands with its constant.
  - The §6 input rows, which no test checks, so they are kept by hand.
  - The input-map rows in `README.md`.
  - A D-row in `ui_and_graphics_action_plan.md`.

### 5.12 Adaptations: groundwork (S10, D11, D13)

- Adaptations use the same catalogue with `side=alien`. They have their own pool and **no chooser**,
  and are dealt one at a time from `RngStream.ADAPTATION`.
- They are earned on a schedule, with both halves off at zero:
  - one every `sim.adaptation.everySeconds` of level time;
  - one per `sim.adaptation.perPlayerUpgrades` upgrades the players take.

  A level overrides either half through `Scenario.configure`.
- They are read through the same `Modifiers`. An alien's scope is the alien side and its strain. A new
  `Breed(UnitKind strain, Stat stat, int percent)` effect narrows an adaptation to one strain,
  mirroring `Gun`. It is not named `Strain`, so it cannot be confused with the `Strain` component
  that `Scope` reads.
- They are shown as a dispatch (`THE SWARM ADAPTS - SPITTERS SPIT FASTER`) and in the aside strip,
  and later as a tint per adapted strain through `Appearance`.
- **The first adaptation needs no new seam**: the spitters' `FIRE_COOLDOWN` goes through
  `Guns.spend`, which S2 already built.
- **A hook for reactive weights, off.** `UpgradeDef.answers` names a player upgrade the adaptation
  counters, or none, and `sim.adaptation.answerWeight` is a multiplier (0 is off).
- **Before any health adaptation:** changing max health breaks "a unit spawned later is identical to
  one already on the field", unless health is stored as damage taken, with the max folded at the read.
  It is named here so nobody discovers it mid-session.

## 6. Content v1

### 6.1 The catalogue

The numbers are starting points; S9 tunes them.

| Upgrade | Target | Ranks | Tier | Each rank | Read by | Session |
|---|---|---|---|---|---|---|
| **Larger Army** | side | 3 | 1 | +1 man per crate; +2 squad headroom | `PickupSystem`, `ReinforcementSystem` | S3 |
| **Weapon Handling Training** | team | 3 | 1 | fire cooldown -12 % (floor -50 %) | `Guns.spend` | S3 |
| **Firepower** | side (the deck), team (the damage) | 2 | 1 | +2 flamer cards in the armoury deck; flamer damage +25 % | the reveal; `Guns.volley` | S6 |
| **Explosives** | squad | 1 | 2 | chance to throw a grenade +0.3/s of opportunity | `Odds`, in `TrooperCombatSystem` | S7 |
| *Marksmen* (cheap) | squad | 2 | 1 | gun damage +15 % | `Guns.volley` | S4 |
| *Field Medicine* (cheap) | squad | 2 | 1 | aid-a-trooper +0.2/s, aid-the-commander +0.5/s (base 0.4 and 1.5) | `Odds` | S4 |
| *Grenadier* (cheap, **personal**) | chooser | 3 | 1 | grenade cooldown -20 % | `Throws` | S4 |
| *Forced March* (cheap) | squad | 2 | 1 | squad speed +10 % (stat `SPEED`, read by `DriveSystem`; never the commander's) | `DriveSystem` | S4 |
| *First Aid* (cheap) | team | 2 | 1 | stabilise work +25 % a tick, a fifth off the time (stat `STABILISE_SPEED`) | `Aid.work` | S4 |

Weapon Handling shortens the cooldown but never the heat per shot, so a hot gun overheats sooner: it
buys bursts, not sustained fire. Whether "the beam runs hottest" survives it is for the max-rank
relational check to say, because cooling does not scale. It holds at today's numbers.

**Troopers can take weapons caches** (`CollisionSystem.claim` asks only for `Taking`). So Weapon
Handling and Marksmen cover whatever gun a trooper picked up, and Firepower's damage covers a trooper
who picked up a flamer. That is why its damage targets the team and not just the commanders.

At about ten drafts a level against this pool (about 16 shared ranks plus the personal ones), a level
ends with most of the pool taken and a few cards left to say no to.

### 6.2 Explosives: "sparingly", as rules rather than hope (G0051)

Every trooper spawns with a `Thrower`. It uses the commander's flight (`sim.grenade.*`: speed, range
34, radius, blast) with its own *how often* in `sim.trooper.grenade.*`; G0019 leaves how far and how
often to config. A throw is an **opportunity** only while all five of these hold, and only then does
`Odds` spend the chance:

1. He is fighting something (`Trooper.Mode.FIGHT` with a target), nothing holds him, and his hands
   are free.
2. The landing point - along his bearing to the target, at the throw's fixed range - has at least
   `minCluster` aliens standing within the blast.
3. **Nobody on the player's side** - commander, trooper, casualty or crew - stands within the blast
   radius plus `safetyMargin` of that point. The blast is on nobody's side (G0018).
4. The path is clear of rock and of scenery that never moves, and stays inside the arena. The throw
   passes through the living (G0019), so this is what makes the landing point certain.
5. His `Thrower` is ready, and the squad's gap has run out: at most one trooper grenade per
   `squadGapSeconds`, held as level state in `Level.squadThrowReadyTick`.

The checks run in that order, cheapest first. Checks 2 and 3 scan the alien and player-side lists
only for a trooper that has already passed 1, the cheap half of 4, and 5 - one or two a tick at most.
The collision grid is built after the squad block, so no broadphase is used.

The throw's range (34) equals a trooper's sight (34). So troopers grenade packs as they come into
view, and the same distance keeps the blast off them.

### 6.3 Sealed caches and the armoury deck (G0050)

- **`PickupKind.ARMOURY_SEALED`** is a constant, a definition and a block (G0008). It offers nobody
  anything (`playerGrant=none`), so it cannot be taken before it is revealed.
- **The reveal.** On the first tick a commander stands within the cache's reveal radius,
  `PickupSystem` deals it a cache kind from the deck, and `Spawner.applyPickup` recasts it in place:
  its kind, look and grant change, its components stay. This is the same shape as `EvolutionSystem`
  recasting a strain. From then on it is an ordinary weapons cache, legible before anybody reaches it.
  `SimConfigTest` holds the reveal radius above the cache's reach plus the widest commander.
- **The deck.** A nested `ArmouryDef(cards, revealRadius)` on `PickupDef`, with `off()`, so only the
  weapons caches carry a block, and each gives both keys. A gun's cache has `revealRadius` 0; the
  sealed cache has `cards` 0. The starting cards are railgun 3, flamer 3, shotgun 3 and laser 2.
  - The cards are folded through `Modifiers.armouryCards`, the `ARMOURY_CARDS` stat's one reader,
    so Firepower's `deal armoury_flamer 2` adds two.
  - Dealing uses a `Bag` (no streaks) drawn from `RngStream.ARMOURY`.
  - The `Bag`'s state, and the card counts it was shuffled at, live on `Armoury`, written by the
    reveal. A reveal that finds different counts - an upgrade landed - shuffles afresh. So the deck
    never reads the ledger; it only asks the read port for its counts.
- **Adventure** (D15): about half of each level's weapons caches become sealed. The lair's cache of
  every gun stays as laid.

## 7. Sessions

**Every session:**

- read this doc and `docs/design/alpha-phase.md` (`CLAUDE.md` is already in context);
- ask Mateusz for delete permission in the first message;
- write tests first - general, then the implementation, then the specific cases;
- get `.claude/scripts/verify.sh` green before claiming anything;
- commit as `CLAUDE.md` says: on Mateusz's machine nothing, only a suggested message; in a cloud
  session, through `cloud-commit.sh`;
- end with the keys added or moved, values included;
- tick §0.

### S0 - Records and words

**Goal.** Before any code, the design surface says what this doc decided.

**Read first.**

- `docs/design/README.md`: the invariants, the input map, the open questions.
- `docs/adr/README.md` and `docs/design/decisions/README.md`.
- In `docs/glossary.md`, the whole section each new word joins.
- `docs/ui/menu_layout_agent.md` §6-§7.

**Write.**

1. **ADR 0017, *Upgrades are a fenced package reading one ledger*.**
   - *Constraint:* the ledger is on the level entity; values are folded at the one reader; the
     package has four entry points and fences 1-4; `UpgradeSystem` lives in the package.
   - *Scope:* both sides; not the interface.
   - *No:* a per-entity current-stats component; a dispatcher that applies effects to entities; an
     on-spawn hook; adding or removing a component for a tactic or ability; a running total edited in
     place; a stat with two readers; a Gradle module for the subsystem now, with the reason set
     against ADR 0003 and 0015.
   - *Revisit when:* `def/` moves into a module of its own, or a second consumer of the pure half
     appears.
2. **G0049, *An upgrade is earned on the field and drafted in turn*.**
   - *Covers:* D1-D4, D8, D12, D17, and D18's one draft per bar.
   - *No:* a currency, a shop or a paid reroll; a draft that times out or picks for him; pausing a
     game with two players present; a draft earned from a tally or a kill count; a fallback that
     grants men (G0030); an effect that hands out a gun.
3. **G0050, *A sealed cache shows its gun before you reach it*.**
   - *Covers:* D5; amends the rationale on `PickupKind`.
   - *No:* a gun dealt when the cache is taken; re-dealing a cache once it has been revealed.
4. **G0051, *A trooper throws only where the blast is the enemy's*.**
   - *Covers:* §6.2.
   - *No:* trooper grenades on a timer; thrown at the aim point; an ammunition economy.
5. **G0052, *A slot can be away*.**
   - *Covers:* D19; supersedes part of G0038's "a dropped connection is an unplugged pad".
6. **`CLAUDE.md`:** the exception to pillar 1 and the clarification of pillar 3. Each is named in the
   summary, one line each.
7. **`README.md`.**
   - The amended rows from §3.
   - The new invariant rows from §8, as `deferred` until their session.
   - Input-map rows for `C`/`Y` and the in-draft keys, as *proposed*.
   - Open questions: carrying upgrades between levels; reactive adaptations; rerolls; health
     upgrades needing health stored as damage taken.
8. **`glossary.md`:** the *Upgrades* section from §4, and *sealed cache* beside the existing
   *weapons cache*.
9. **`docs/architecture.md` and `.claude/skills/new-system/SKILL.md`:** both say systems live in
   `system/`. Each gains ADR 0017's one exception.

The UI spec rows (`menu_layout_agent.md` §6 and §7) wait for S5. `UiSpecTest` fails whenever those
tables and the `HudSlot` enum disagree, so each row lands together with its constant.

**Done when.** Every row in §3 is written where it belongs.

### S1 - Config split and the binder

**Goal.** The same game, read from `assets/sim/*.properties`, with a binder that can read the
catalogue's shape.

**Read first.**

- `sim/build.gradle` (`generateShippedConfig`, lines 15-51).
- `core/.../SimConfigLoader`.
- `sim/.../ConfigBinder`: `parse`, `sealed` (lines 261-284), `convert`.
- `SimConfig` (`ROOT`, the `@ConfigKey` renames).
- The tests `ShippedConfigTest`, `ConfigDefaultsTest` and `ConfigBinderTest`.
- `docs/packaging.md`.

**Stages** (each ends green):

1. **The binder.** It reads sealed records with N arguments and `List`s of sealed records separated
   by `;`. A key given twice is reported as a problem instead of the last one silently winning. Tests
   come first, in `ConfigBinderTest`.
2. **The split.** Move the blocks into `assets/sim/` as §5.9 lays out, comments included. Before
   moving anything, write a throwaway guard, `parse(split files) == parse(game.properties)`, and keep
   it until `assets/game.properties` is deleted at the end of this stage.
3. **The build.** `generateShippedConfig` runs over the directory and writes one constant per file,
   with the file list generated beside them. `SimConfigLoader` reads that list through
   `SimConfig.shippedFiles()`, because the generated class is package-private in `sim`. `shipped()`
   keeps its meaning.
4. **The tests.** `ShippedConfigTest` covers the files, and its hard-coded path goes. A new test
   checks that no key appears in two files. `ConfigDefaultsTest` is unchanged.
5. **The docs.** Update every line that names `game.properties` (grep): `CLAUDE.md`, `README.md`,
   `workflow.md`, `conventions.md`, `architecture.md`, `glossary.md`, `docs/design/README.md`,
   `alpha-phase.md`, `.claude/skills/feature/SKILL.md` and `docs/ui/*`. ADRs and G-records are
   history and stay as written. Close backlog E7.

**Done when.**

- Verify is green, and the bound `SimConfig` equals the old one.
- `ShippedConfigTest.shipsToPeersWithoutItsCommentsAsTheSameConfig` still passes over the split
  files. That test is the online guard: the net tests build their worlds from `SimConfig.defaults()`
  and never ship text, so `SessionTest` proves nothing here.
- The desktop jar still finds `assets/sim/` (`lwjgl3` adds `assets/` to its resources).

### S2 - Seams, nothing held

**Goal.** Every v1 stat has its one reader wired through `Modifiers`, with nothing held. After one
deliberate, named change (stage 1), the game is byte-identical.

**Read first.** `Guns`, `Odds`, `TrooperCombatSystem`, `GrenadeSystem`, `PickupSystem`,
`ReinforcementSystem`, `Spawner.spawnProjectile` and `ArchitectureTest`.

**Stages:**

1. **Trooper fire goes through `Guns.ready` and `Guns.spend`.** This is the deliberate change from
   §5.5: a trooper holding a gun that slows its holder now slows, like the commander. The test comes
   first - a trooper firing the laser slows himself - and the rest of `TrooperCombatTest` stays green
   unchanged. Name the change in the session summary.
2. **`Throws`.** The release moves out of `GrenadeSystem.throwWhateverWasAskedFor` into one static
   helper in `system/`. `GrenadeTest` stays green unchanged.
3. **The `sim.upgrade` skeleton.** `package-info.java`, `Stat`, `Target`, `Scope` and `Fold`, plus
   `Modifiers` with `@Reads`, returning the base while nothing is held. `Fold` gets full unit tests:
   clamps, ticks never below one, sums that do not depend on order, counts at their floor.
4. **Wire each seam.** `Guns.spend`, the damage in `Guns.volley`, `PickupSystem.reinforce`,
   `ReinforcementSystem`'s cap and `Throws`. Then `Odds`: both `spend` overloads gain the unit, and
   the in-hand one gains the kind. Their five callers change with them: `TrooperCombatSystem` (two),
   `TrooperCrew`, `TrooperAid` and `CollisionSystem`. `Modifiers` is built in `Simulation` and handed
   to systems the way `Spawner` is.
5. **Fences 2-4** (§5.1), each watched failing against a probe first.

**Done when.** Verify is green, and `SimulationDeterminismTest` and `SnapshotRoundTripTest` are
unchanged. A throwaway check - not a golden kept in the repo - compares every scenario's hash after N
ticks before and after stages 2-5, and finds them equal.

**As built (2026-09-29).**

- `Target` and `Scope` moved to S3, stage 3: with nothing held nothing resolves a target, and a
  `Scope` with no caller would be untested code. So did `Fold`'s "sums that do not depend on order":
  the sum is the ledger's, and arrives with it.
- `Stat` has a sixth constant, `CHANCE`, read by `Odds` through `Modifiers.chance(who, kind, base)`.
  Both `Odds.spend` overloads now start `(modifiers, unit, kind, rng, due, chances, ...)`.
- `Guns.spend` and `Guns.volley` take `Modifiers`; `spawnProjectile(..., Weapon, float damage, ...)`
  lost its copy of `weapon.damage` rather than gaining an overload.
- `Modifiers` has a no-argument constructor; S3 gives it the level entity's ledger.
- Fences: `upgradesAreCalledAndDoNotCall` (2), `everyStatIsReadInExactlyOnePlace` (3), and
  `systemsHoldNoStateAcrossTicks` extended to `..sim.upgrade..` (4). Each was watched failing
  against a probe.
- The hash check ran 12 scenarios x 2 seeds x 1200 ticks, recorded after stage 1: equal after
  stages 2-5.

### S3 - The draft core

**Goal.** Drafts are earned, dealt, rotated, picked, re-dealt and passed on - headless, deterministic
and snapshotted - with Larger Army and Weapon Handling as the content that proves the fold end to
end.

**Read first.**

- `Simulation`: the constructor and `step`.
- `Spawner.spawnLevel` and `spawnUnit`.
- The components `Level`, `Waves` and `Barrage`.
- The systems `WaveSystem`, `BarrageSystem` and `ControlPointSystem`.
- `PlayerInputs`, and `WorldCodec` with `NAMED_COLLECTIONS`.
- `SystemOrderTest` and `docs/simulation-order.md`.
- The tests `CoopTest` and `DeterminismFuzzTest`.

**Stages:**

1. **Definitions.** `UpgradeKind`, `UpgradeDef`, `Effect` (`Scale`, `Count`, `Chance`, `Gun`) and the
   bind-time checks. `upgrades.properties` gets the draft block, Larger Army and Weapon Handling.
2. **State.** The components `Upgrades`, `Drafts` and `Presence`; `Components.ALL`; the
   `NAMED_COLLECTIONS` lines; `spawnLevel`. Regenerate `docs/components.md` with
   `.claude/scripts/component-index.py`.
3. **The pure parts, with unit tests only.** `Dealer`: weighted, distinct, in deal order,
   anti-streak, small pool. `Rotation`: present slots, wrapping, nobody present. `Target` and
   `Scope` (moved from S2): which held entries reach a reader, a turret reached by none. The sum
   over held entries, and that it does not depend on their order.
4. **The published facts.** `Strain.wave`, stamped through a `spawnUnit` overload; `Waves.cleared`;
   `Level.bossesDown` with `Barrage.fallen`. Tests: a wave counts as cleared when its last alien's
   throes begin and not before; a boss counts once.
5. **Switching it on.**
   - `UpgradeSystem` at 46, and `RngStream.DRAFT`. The same stage adds the `SystemOrderTest` rows
     and renumbers `simulation-order.md` (46-50), because `SystemOrderTest` fails the moment the
     constructor and the doc disagree.
   - `Picks`.
   - `PlayerInputs.pick`, `.away` and `.back`.
   - `Simulation.step` hands over picks and applies presence.
   - `Modifiers` reads the ledger.
6. **Fence 1**, the `sim` half.

**Tests** (the general ones first):

- **`DraftTest`:**
  - in single-player, a point taken earns a draft for slot 0;
  - drafts go round the present slots in order;
  - a pick applies one rank from the next tick;
  - a pick for a draft that is gone, from the wrong slot, or of a kind not offered changes nothing;
  - a stale card is re-dealt;
  - an away slot's drafts pass on;
  - drafts arrive at the configured rate per source;
  - a commander who respawns keeps his slot's personal ranks.
- **`UpgradeTest`:** a unit spawned after an upgrade is identical to one that was on the field when
  it landed - a trooper reinforced after Larger Army, and a volley's cooldown after Weapon Handling.
- **The existing suites:** `SnapshotRoundTripTest` with a non-empty ledger and drafts;
  `DeterminismFuzzTest` with random picks and presence; `SimulationDeterminismTest` with a scripted
  pick.

**Stops.** Stages 1-3 register nothing and stand alone. After stage 4 the facts are published and
nothing reads them yet. Stages 5-6 turn the draft on.

**As built (2026-09-29).**

- **Config.** Three `SimConfig` components: `@ConfigKey("upgrade.draft") DraftDef draft`,
  `@ConfigKey("upgrade.stat") Defs<Stat, StatDef> stats` and `@ConfigKey("upgrade") Defs<UpgradeKind,
  UpgradeDef> upgrades`, checked together by `UpgradeDef.checkCatalogue`. `Stat` carries its `Form`
  (scale, count, chance) and its `Reader` (gun, unit, side); a stat block states all three keys, and
  the ones its form does not use must be zero. `Effect.Chance` is written `chance <ai_chance>
  <target> <perSecond>`. A tier counts the drafts earned *before* the one being dealt.
- **Dealing.** What the chooser passed over is dealt only after everything else has been, rather
  than left out, so a short pool still fills. A stale card is re-dealt only on a tick with a pick or a
  pass-on; one nothing can replace is dropped, and a draft left with no cards goes. An empty pool
  earns nothing - the source is still credited - and its dispatch waits for S5.
- **Facts.** `Waves.cleared` is worked out before the tick's wave is sent, because Ashley adds what
  a system spawns only after that system has run. Each wave's aliens carry `Strain.wave` through
  `Spawner.spawnUnit(kind, x, y, wave)`; a level holds at most `Waves.MOST` (63) waves.
- **Input.** `PlayerInputs.pick(slot, serial, kind)`, `.away(slot)` and `.back(slot)`, read as
  `pickOf`/`pickSerialOf`, `goesAway` and `comesBack`. `Presence.leave`/`comeBack` are the only
  writes, in `Simulation.step`. Nothing sends them yet: the dev keys are S4, the sequencer S8.
- `Modifiers(engine, config)` reads the level entity's ledger through a family; `Scope` and `Target`
  landed here, as S2 recorded. `UpgradeView` is S5's.
- The fact tests went beside their systems (`DefendTest`, `BarrageTest`) rather than a file of
  their own. `DeterminismFuzzTest` (opt-in) sends random picks and presence; all 12 scenarios green.

### S4 - First catalogue, playable offline

**Goal.** Mateusz can play Adventure offline, earn drafts, see what is offered and take it.

**Stages:**

1. **The cheap upgrades** from §6.1: Marksmen, Field Medicine, Grenadier (which is personal and
   proves the per-slot rank path) and the fourth, picked at the start of the session.
2. **Dev keys.**
   - `ScenarioSession.pendingPick[slot]`, remembered while paused and spent once per tick.
   - `F5`/`F6`/`F7` take card 1/2/3 of the keyboard seat's oldest draft.
   - The offer and every pick go to the console (`Log.info`), the way `ActionPrompts` announces
     actions.
   - A row in the harness table and a scaffolding row in `alpha-phase.md`.
3. **The relational guarantees at the highest rank:** `SimConfigTest`, through `Modifiers`.

**Tests.**

- **`ScenarioSessionTest`:** a pick given on a frame too short to run a tick is still delivered; it
  is spent once when a frame runs several ticks; it is remembered while paused.
- **Each upgrade:** one test that its number moves where it is read. Grenadier's moves only for the
  chooser's commander.

**Done when.** Verify is green and an Adventure run with drafts is playable.

**As built (2026-09-29).** Mateusz chose both candidates for the fourth, so the catalogue is seven:
Forced March (squad speed) and First Aid (quicker stabilise) beside the three above.

- Two new stats. `SPEED` has a reader kind of its own, `DRIVEN`: it is read where `DriveSystem`
  commits a course, so an effect on it may reach the squad or the aliens and never a commander,
  whose pace is his own control's (G0010) - a bind-time refusal, not a quiet no-op.
  `STABILISE_SPEED` scales the work one tick does, read in `Aid.work` for whoever is working, so the
  commander and a trooper stay the same (the `Aid` rule).
- `UpgradeView` (entry point 4) landed early, read-only: the harness needs a slot's oldest draft and
  its cards. `Drafts.revision` moves on any draft change; `UpgradeView.revision()` joins it with the
  ledger's. S5 builds on it.
- Dev keys: `HarnessCommand.TAKE_FIRST_CARD`..`TAKE_THIRD_CARD` on `F5`-`F7`. `ScenarioSession` reads
  the card at press time and holds the pick by slot until a tick spends it. `DraftAnnouncer` says
  each local player's oldest draft and the held list on the console when they change. Online, the
  keys say once that they are offline only.
- Relational checks at the highest rank, through `Modifiers`: the beam still runs hottest, and the
  squad at its fastest is still slower than the commander.
- In the test fixture the five new upgrades have weight zero, so the draft tests keep dealing two
  kinds; each upgrade's test holds it directly.

### S5 - The interface

**Goal.** Playable offline for real, solo and in local co-op: the top-bar draft (D18), the tell,
the aside strip, the dispatches and the solo pause.

**Read first.**

- `docs/ui/menu_layout_agent.md` (all of it), and §7-§8 of `docs/ui/ui_and_graphics_action_plan.md`.
- Readout: `ReadoutWriter`, `Readout`.
- Top bar: `TopBarPainter`, `TopBarLayout`, `Terminal`, `HudMetrics.topLines`.
- Bottom bar, for the tell only: `BottomBarPainter`, `PanelLayout`.
- Input: `InputReader`, `PadControls`, `OrderKeys`, `ControlsConfig`.
- Flow: `PlayMenus`, `UiFlow`.
- Text: `Terminal`, `DispatchKind`, `DispatchText`, `UiStrings`.

**Stages:**

1. **Readout.** `UpgradeView` feeds `ReadoutWriter`, which fills `Readout` with a per-slot draft
   readout and the held list. `copyFrom` and `clear` are updated. Fence 1's interface half goes into
   `UiArchitectureTest` and `ClientArchitectureTest` here.
2. **An input context per seat.** `InputReader` routes a seat's order inputs to the draft while its
   bar is open. Add the bindings (`controls.key.draft=C`, `controls.pad.draft=NORTH`) and settle the
   input-map rows.
3. **The bar.**
   - `HudSlot.DRAFT` and the tell's own constant, each with its §7 row in the same change.
   - The layout arithmetic: portrait and name, then N cards across the rest of the top bar's span,
     each card a name and two effect lines inside the bar's `topLines`, within the §8 text rules
     (including a pseudo-locale 30 % longer).
   - The highlight and the `1/2` count; taking a card closes the bar.
   - One draft per machine's bar.
4. **Text.** The `ui.upgrade.*` and `ui.effect.*` templates, formatted when a draft changes.
5. **The solo pause and its banner.** Closing resumes. A *back* while paused solo also resumes; S8
   completes the online side.
6. **The rest.** The tell in each panel, the aside strip, the dispatches, and whether the terminal
   holds its queue while the bar is up.

**Tests.**

- `UiSpecTest` rows, and readout tests.
- `InputReader` routing per seat: other seats' orders still work, and the chooser's give none.
- Layout arithmetic tests, and `TextFitTest` for the dispatch templates.
- `ScenarioSessionTest` for the solo pause.

**Done when.** Verify is green, and Mateusz plays Adventure solo and in local co-op with the real bar.

**As built (2026-09-29).**

- **Readout.** `ReadoutWriter` reads `UpgradeView` alone: each slot's draft count, his oldest draft's
  serial and up to three cards with the rank each would have (`PlayerReadout`), and the ledger with
  each entry's most ranks (`Readout.held*`). `UpgradeView` gained `cardOf` (the n-th card still on a
  draft, skipping a dropped place), `effects`, `ranksOf` and `dry`, over a new `Drafts.dry` count of
  drafts a source was owed that the pool could not deal. Fence 1's interface half:
  `UiArchitectureTest.theInterfaceReadsUpgradesThroughTheirView`,
  `ClientArchitectureTest.theClientReadsUpgradesThroughTheirView`.
- **Input per seat.** `InputReader.read` takes the seat driving the draft; `DraftKeys` takes that
  seat's order out of the frame and turns it into a draft key by the order's place - keyboard
  `1`-`3` choose, `4` closes; pad right/left move the highlight, down closes, up does nothing. Every
  other seat's orders are untouched. `controls.key.draft=C` and `controls.pad.draft=NORTH` (with
  `draftTrigger=NONE`) open the draft and take the highlighted card.
- **The bar.** `DraftBar` (ui, pure) is the one draft per machine: whose, which serial, which card is
  highlighted; only the seat that opened it drives it, it follows the world once a frame, and taking
  a card closes it and hands back the pick, which goes through the new `ScenarioPlay.pick` (offline;
  online it says once that picks wait for S8). `DraftLayout` is the arithmetic, `TopBarPainter` the
  drawing, `CardText` the words - names `ui.upgrade.*`, one template per effect
  `ui.effect.<form>.<stat>` / `ui.effect.chance.<chance>`, who it reaches `ui.target.*` - rebuilt only
  when the draft, the highlight or the seat changes.
- **Solo pause** (D2): `DraftPause` pauses when a draft opens with one player in the game offline and
  resumes only a pause it made; a menu opening closes the draft and takes the pause over. The paused
  banner and its scrim stay off a draft's pause (`PlayMenus.quietPause`). *A back while paused solo
  also resumes* is not built: offline nobody is ever away, so it waits for S8.
- **The rest.** The tell (`UpgradeTell`, `HudSlot.UPGRADE_TELL`) right-aligned on the panel's first
  row, shortened beside a status and never over it. The aside strip lists the ledger with pips when the
  F1 overlay is not in its panel. `UpgradeReporter` files `DRAFT_EARNED`, `UPGRADE_TAKEN` (the taker
  inferred from whose drafts went down), `DRAFT_PASSED` and `NOTHING_LEFT`. **The terminal holds its
  queue while the bar is up**: `ScenarioScreen` does not advance it, so its clock stops and nothing
  ages out unseen.
- `DraftAnnouncer` is gone with the console offers; `F5`-`F7` stay as harness keys.
- From the playtest: the pause menu shows every upgrade held beside it, docked right
  (`HeldUpgradesPainter`), since the aside has room for four rows. Only with the menu open - a modal -
  so the paused banner still leaves the view clear (the UI spec's do-not-build list).

### S6 - Firepower: sealed caches and the armoury deck

**Stages:**

1. `ArmouryDef` on `PickupDef`; `PickupKind.ARMOURY_SEALED` with its look and block;
   `Spawner.applyPickup`.
2. The `Armoury` component, written by the reveal in `PickupSystem`, and `RngStream.ARMOURY`. The
   `Deal` effect and the `ARMOURY_CARDS` stat, whose one reader is the reveal.
3. Firepower (`deal armoury_flamer 2; gun flamer gun_damage team 25`).
4. Seal about half of the caches in Adventure and Adventure 2.

**Tests.**

- A sealed cache cannot be taken.
- It is revealed on the tick a commander comes within the radius, and not before.
- The deck never deals a card twice before every card has come up.
- Firepower's cards change what the next shuffle deals.
- The reveal radius exceeds the cache's reach (`SimConfigTest`).
- `ScenariosTest` still holds.

**As built (2026-09-29).**

- **Definitions.** `ArmouryDef(cards, revealRadius)` on `PickupDef`, `off()` for everything but a
  weapons cache; a cache is a card or sealed, never both, and a sealed cache must offer nobody anything
  (both refused at bind time). A five-argument `PickupDef` constructor means "no armoury part", so the
  fixture's other pickups did not change. `PickupKind.ARMOURY_SEALED`, `Look.ARMOURY_SEALED`
  (`graphic.color.armoury_sealed=a89c84ff`, drab khaki: the colour of no gun).
- **The reveal** is the tail of `PickupSystem.update`, after the claims: a sealed cache with a commander
  on his feet (`Players.down`) within its radius is dealt a cache kind and recast in place by
  `Spawner.applyPickup` (kind, body radius, look). No new system, so `simulation-order.md` is unchanged
  apart from a note.
- **The deck.** Every kind whose `armoury.cards` is above zero, in kind order, each count folded
  through `Modifiers.armouryCards` (the `ARMOURY_CARDS` stat's one reader) on every reveal. A `Bag`
  over the total, drawn from `RngStream.ARMOURY`. `Armoury` (level entity) holds `drawn` and
  **`shuffledAt`, the deck's size at the last shuffle - not the packed counts §5.2 proposed**: a
  `Deal` only ever adds (its constructor refuses less than one), so any change to the deck changes its
  size, and an int needs no limit on how many caches the deck holds.
- **`Effect.Deal(card, amount)`**, written `deal armoury_flamer 2`; its target is always `SIDE`.
  Catalogue checks (`UpgradeDef.checkDeck`): a deal names a card the deck already holds; a level
  that can seal a cache has a card to deal it; the deck with every deal at its highest rank fits a bag
  (64). Card text `ui.effect.deal.<cache>` ("+2 SEALED FLAMERS").
- **Firepower**: ranks 2, weight 10, tier 1, `deal armoury_flamer 2; gun flamer gun_damage team 25`.
  At two ranks the deck is 7 flamers in 15 cards.
- **Levels.** Adventure: 6 of 15 caches sealed - none in the growth, where the colours are learnt,
  and never the first beam. Adventure 2: 4 of 10 - one of every pair past the anthill; the lair's four
  stay as laid. `AdventureScenarioTest.CACHES` gained the laser (it had been missed) and the sealed
  cache.
- **Tests.** `PickupSystemTest`: nobody takes a sealed cache; revealed on the tick a commander comes
  within the radius and not before; not by a commander who is down; a revealed cache arms whoever
  takes it; eight caches from the fixture's four-card deck are every gun twice. `UpgradeTest`:
  Firepower's cards are dealt, a deck that grows mid-shuffle is shuffled afresh, the damage is the
  flamer's alone. `SimConfigTest`: the reveal radius exceeds the widest cache plus the commander; a
  deal of a card not in the deck and a deck too big for a bag are refused.

### S7 - Explosives: troopers throw

**Stages:**

- `AiChance.THROW_A_GRENADE`, with a base of 0;
- the `sim.trooper.grenade.*` block;
- a `Thrower` on every trooper from spawn;
- `Trooper.throwDue` and `Level.squadThrowReadyTick`;
- the five conditions from §6.2, checked in `TrooperCombatSystem` and thrown through `Throws`;
- Explosives itself, at tier 2.

**Tests.**

- With the chance at zero, no trooper ever throws, and nothing is drawn.
- A trooper throws only with a cluster at the landing point.
- No throw ever lands a blast within the safety radius of anyone on the player's side, a casualty
  or a man on a turret included.
- No throw follows a line that meets rock or scenery.
- At most one throw per squad gap.
- A trooper spawned after Explosives throws like one already on the field.

**As built (2026-09-29).**

- **Config.** `TrooperGrenadeDef(cooldownSeconds, minCluster, safetyMargin, squadGapSeconds)` as
  `TrooperConfig.grenade`, keys `sim.trooper.grenade.*` in `squad.properties` (10 s, 3, 4, 6 s).
  `AiChance.THROW_A_GRENADE`, `sim.ai.chance.throw_a_grenade=0`.
- **State.** Every trooper spawns with a `Thrower`: the commander's flight (`sim.grenade.*`) with the
  trooper's cooldown. `Trooper.throwDue`, `Level.squadThrowReadyTick`. A trooper has no `Intent`, so
  `GrenadeSystem` never throws for him.
- **The throw** is `TrooperCombatSystem.throwAGrenade`, between the swing at a leap and the shot; a
  throw replaces that tick's shot. Order: `Odds.possible` first (new: the chance above zero, drawing
  nothing - so with Explosives not held nothing below it runs); hands free, own throw ready, squad gap
  run out; the path inside the arena and clear of rock (`TileMap.isClearPath`); `minCluster` aliens on
  their feet within the blast of the landing point; nobody of ours (commanders and troopers, down or
  not, crew included) within blast plus `safetyMargin`; no scenery (solid and immovable - what a
  grenade comes off, G0019) across the path. Then `Odds.spend`, then `Throws.release`, and the gap is
  set. The landing point is `Throws.reach` (new: muzzle plus range) along the bearing to his target;
  the checks run to one tick's flight past it, because a grenade goes off on the tick its distance
  runs out.
- `Sight.nearLine` extracted from `clearShot` - the distance of a point from a segment - and used by
  both. `Odds` reads the `CHANCE` stat through one private method, so `possible` and `spend` share it.
- **Explosives**: squad, ranks 1, tier 2, weight 10, `chance throw_a_grenade squad 0.3`.
- **Tests** (`TrooperGrenadeTest`, 8): nothing thrown and nothing dealt at zero; throws with
  Explosives, and the same for a man who arrives after it; not without a cluster; not with the
  commander or a casualty inside the margin; not with scenery across the path; one grenade per squad
  gap between two men. Mutations checked: switching off the friendly, scenery and cluster rules each
  fails its test. **Rock across the path has no test of its own**: rock on the line also blocks his
  sight, so he is not fighting; the check is `TileMap.isClearPath`, tested where it lives.

### S8 - Online

**Stages:**

- typed events in `PeerSession` and `Sequencer`, and the `PICK` event;
- presence, sealed by the sequencer;
- the two new `Bundle` sections, and `Msg.PROTOCOL` 3;
- the reason on `PAUSE`/`PAUSED`;
- in `OnlineSession`: pending picks, the draft pause when solo, and resuming on a *back*.

**Tests.**

- **`BundleTest`:** a round trip with picks and presence.
- **`SequencerTest`:** a pick is sealed once however often it is resent; one per slot per tick; an
  order and a pick from one slot on one tick both land.
- **`SessionTest`**, through `SessionHarness`:
  - a pick made on a joining machine lands on every machine on the same tick;
  - a player who drops in after three picks has the same ledger (the hashes agree);
  - a dropped link passes its drafts on identically everywhere, and a return puts the slot back in
    the rotation;
  - a pick for a draft that is gone changes nothing anywhere.
- **`OnlineSessionTest`:** the draft pause carries its reason.

**Done when.** Verify is green, and drafts work both ways in a two-copy session on one PC (the
latency playtest's method).

**As built (2026-09-29).**

- **Typed input events.** On the wire an event is its number (int), slot (byte), type (byte), a value
  (int) and an extra (byte), `Msg.EVENT_BYTES` = 11 whatever the type: `EVENT_ORDER` (the order's code)
  and `EVENT_PICK` (the draft's serial; the kind's code). `PeerSession.queueEvents` numbers orders and
  picks alike; the sequencer checks the whole event before holding it, and `Peer.applyEvents` seals one
  of each type per slot per tick, holding back everything after an event that has to wait, so events
  land in the order given. `Msg.PROTOCOL` 3.
- **`Bundle`** gains picks `(slot, serial, kind)` and presence `(slot, away 1 | back 0)`, in `write`,
  `read`, `skip` and `applyTo`; `Sequencer.maxBundleBytes` grows by both (a test holds a full tick to
  it).
- **Presence, sealed by the sequencer.** A peer leaving marks each slot it held away - unless that
  slot's join was never sealed, in which case there is no commander to be away. Seating anybody in an
  away slot seals *back*, or, if the away was not sealed yet, seals nothing. A restart forgets who was
  away: a new world has nobody in it who is. A returning machine takes the lowest free slot, as any
  joiner does, so it is *back* in its old slot only if that is the lowest free.
- **The pause carries a reason.** `PAUSE | on | reason` and `PAUSED | on | tick | by | reason`, the
  reason `PAUSE_BY_PLAYER` or `PAUSE_FOR_DRAFT`. **`PAUSED` echoes the asking peer's id, not a slot** -
  a peer can hold several slots, and nothing reads which. **The sequencer lifts a draft pause itself
  when it seats anybody**: nothing is sealed while paused, so a returning friend's *back* could never
  reach the world and end the solo. A player's own pause stays on.
- **Client.** `OnlineSession.pick` holds the card per seat until the next frame hands it to the session
  as an event; `F5`-`F7` work online through `ScenarioPlay.takeCard`, which `ScenarioSession` now uses
  too. `ScenarioPlay.setDraftPaused` / `pausedForDraft`; the screen counts "solo" online as offline, a
  player whose link dropped not counted (`PlayerReadout.away`, from the new `UpgradeView.away`), and
  keeps the paused banner off a draft's pause on every machine. `DraftPause` resumes the moment the game
  stops being solo, the draft still open. The panel's OFFLINE now also shows a dropped remote link.
- **Tests.** `BundleTest`: picks and presence round trip; a full tick fits the history; an unknown
  upgrade code is refused. `SequencerTest`: a pick sealed once however often resent; an order and a pick
  on one tick; one pick per slot per tick; an event of no type refused. `SessionTest` (on a test world
  whose map-wide points take themselves on a timer, `TestWorlds.DRAFTING`, in Thicket - alone in
  Skirmish the host was knocked off his feet too often to take a point): a card taken on a joining
  machine lands everywhere; a drop-in after three cards holds all three; a dropped link is away and
  passed by, and a machine seated in its slot is back; a card from a draft that is gone changes nothing;
  a pause for a draft says so on every machine; a draft pause lifts when a second player sits down, a
  hand's does not. `DraftPauseTest`, `ReadoutWriterTest` for the rest. **`OnlineSessionTest` was not
  written**: the reason is carried and read in `PeerSession`, and `SessionTest` covers it end to end.
- **Not done:** the two-copy check on one PC (Mateusz's); the HUD does not name who is away beyond the
  grey OFFLINE panel.

### S9 - Playtest gate and tuning

- **Play:** Adventure solo, in local co-op with two pads, and online with two copies.
- **Watch:**
  - how often drafts arrive;
  - whether the pool empties before the level does;
  - whether the solo pause intrudes;
  - whether the bar hides something you needed;
  - how close trooper grenades land to your own men;
  - how legible sealed caches are.
- **Turn:**
  - the draft block;
  - each upgrade's numbers;
  - `sim.trooper.grenade.*`;
  - the deck's cards;
  - which Adventure caches are sealed.
- **No scenario unless Mateusz asks for one.**

### S10 - Adaptations: groundwork

**Stages:**

- the alien scope in `Modifiers`, and the `Breed` effect;
- `RngStream.ADAPTATION`, and the schedule (off);
- the first adaptation (the spitters' fire cooldown);
- its dispatch and its place in the aside strip;
- the `answers` hook (off);
- the relational checks at the highest rank, for alien numbers.

**Tests.**

- With both schedule keys at zero, nothing is ever dealt and nothing is drawn.
- One adaptation every `everySeconds`, and one per `perPlayerUpgrades` picks.
- An adaptation moves only its own strain's number.
- A spitter spawned after it is identical to one already on the field.

**Not in it:** content in any scenario until Mateusz asks.

**As built (2026-09-30).** Mateusz chose -15 % a rank, three ranks, and asked for the schedule on in
the Adventure levels.

- **`Effect.Breed(strain, stat, percent)`**, written `breed spitter fire_cooldown -15`: always the
  aliens', a scale only, and never a side's number. `Scope.reaches(strain, who)` is the alien side and
  `Strain.kind` *now* - an alien that evolves into the strain is reached, one that evolves out of it is
  not. One branch in `Modifiers.percent`; no new stat and no new seam, since the spit is charged in
  `Guns.spend`.
- **`UpgradeKind.RAPID_SPIT`**, `side=alien`, weight 10, 3 ranks, tier 1. Spit 1.1 s -> about 0.94,
  0.77, 0.61 s; the -50 floor is never reached by it alone.
- **`UpgradeDef.answers`** (`answers=none` in every block). Only an adaptation may answer, and only a
  players' upgrade (the constructor and the catalogue check). A second constructor without it keeps
  every players' definition as it was. **Weight** of an answer = `weight * (1 + answerWeight * ranks
  the players hold of it)`, whole numbers, off at zero.
- **`AdaptationDef(everySeconds, perPlayerUpgrades, answerWeight)`**, `sim.adaptation.*`, all zero in
  what ships.
- **The schedule is two credit counters on `Upgrades`**, `fromTime` and `fromPicks`, beside
  `adaptations` - the drafts' `fromPoints` shape - **instead of the `nextAdaptationTick` §5.2
  named**: nothing to initialise, and a level that changes the interval cannot strand a tick. Level time
  counts the tick being run, so the first adaptation lands at `everySeconds`, not at once; picks count
  this tick's.
- **`UpgradeSystem` step 4**, after picks and drafts: one card per credit, dealt from
  `RngStream.ADAPTATION` over the aliens' pool and granted at once, shared. `eligible` takes the side;
  **an adaptation's tier counts adaptations taken** against the same `tierFrom`. A dry pool takes the
  credit and draws nothing.
- **Shown.** `UpgradeView.heldAlien` -> `Readout.heldAlien`; `UpgradeReporter` files
  `SWARM_ADAPTS` (`THE SWARM ADAPTS - SPITTERS SPIT FASTER`, from `ui.adapted.<KIND>`) instead of a
  player taking it, and an adaptation in the same frame no longer counts as a card taken. The aside and
  the pause list prefix `SWARM` (`HeldUpgradesPainter.whose`, shared by both). Card text has a `breed`
  template with the strain's name (`ui.strain.<KIND>`). **No tint per strain yet.**
- **`Scenario.adapting`**: both Adventure levels, one every 180 s and one per 3 cards. With one
  adaptation of three ranks the swarm is done after three.
- **Tests.** `AdaptationTest` (7): off draws nothing; one per interval; one per N picks; dry pool
  draws nothing; the answer weight; only its strain; spawned later is identical. `SimConfigTest`:
  `everythingHeld` holds both sides; refusals for an answering upgrade and an answer to the swarm's
  own. `ReadoutWriterTest`, `UpgradeReporterTest` (+2).
- **The relational checks at the highest rank.** No relation `SimConfigTest` holds reads a spitter's
  cooldown, so holding the adaptation there proves nothing new today; it is there so the next
  adaptation is checked the day it lands.

### S11 - Carrying upgrades between levels (blocked)

Blocked on "what happens when a level's objectives are met" - the level lifecycle, an open question
in `README.md`. The ledger is a list of `(kind, slot, ranks)`, so carrying it means copying that list
into the next level's entity, keyed by slot. The questions it will ask:

- Does a slot's personal ledger follow the slot or the man?
- Does an unspent draft carry over?
- Do adaptations reset?

## 8. Guards

These are the invariant rows S0 writes as `deferred`; each turns `enforced` in its session.

| Invariant | Enforced by | Session |
|---|---|---|
| An upgrade changes numbers, never what an entity is made of | `onlyTheSpawnerChangesWhatAnEntityIsMadeOf`, `CompositionTest` (existing) | S3 |
| Every stat has exactly one reader, and a stat nobody reads fails the build | fence 3 | S2 |
| A unit spawned after an upgrade is identical to one on the field when it landed | `UpgradeTest` | S3 |
| The same seed and inputs deal the same drafts, and a pick is an input event | `SimulationDeterminismTest`, `DeterminismFuzzTest` with picks | S3 |
| A draft belongs to one chooser in slot rotation; a pick from anyone else, or for a draft that is gone, changes nothing | `DraftTest` | S3 |
| An away slot is skipped by the rotation, and its drafts pass on | `DraftTest`, `SessionTest` | S3, S8 |
| An upgrade belongs to the slot or the side, never to a life | `DraftTest` | S3 |
| Drafts come from objectives only, at their configured rate | `DraftTest` | S3 |
| The relational guarantees hold at every upgrade's and adaptation's highest rank | `SimConfigTest`, through `Modifiers` | S4, S10 |
| Every upgrade and adaptation in force is on screen with its rank, and so is who holds a draft | readout tests, and judgement | S5 |
| A machine's top bar shows at most one draft | bar test | S5 |
| A sealed cache shows its gun before a commander can reach it | `SimConfigTest`, `PickupSystemTest` | S6 |
| A trooper's grenade never lands where its blast reaches the player's side | `TrooperGrenadeTest` | S7 |

Beyond these, `Dealer`, `Fold` and `Rotation` get pure unit tests: weights, uniqueness, small pools,
clamps, and tick rounding. That covers the sources' "test rounding and precision".

## 9. Risks and what to watch

- **Input per seat is new in `core` (S5).** Today one machine-wide flag neutralises every seat. The
  draft needs one seat's order inputs routed away while the others play on. Test it first.
- **The config split touches the build, packaging and the online join together (S1).** The safety
  net is the throwaway equality guard plus
  `ShippedConfigTest.shipsToPeersWithoutItsCommentsAsTheSameConfig`. `SessionTest` is not part of it:
  its worlds are built from `SimConfig.defaults()` and it never ships text. The desktop jar is the one
  thing no test here runs.
- **Cadence.** Ten drafts a level is a guess against a pool of eight upgrades. S9's first question
  is whether the pool runs dry or drags.
- **Trooper grenades near the squad (S7).** The rules make friendly fire impossible at the moment of
  the throw. Men walk, and a grenade flies for about a second: `safetyMargin` covers that walk, and
  the playtest is where it gets checked.
- **The draft hides the goal and the terminal (S5).** Every panel stays in sight (D18), so nobody
  misses a DOWN while a friend chooses; what a player can miss is a dispatch. Holding the terminal's
  queue while the bar is up is the proposed answer.
- **Snapshot churn.** New streams and components change every hash. Determinism tests compare runs,
  not stored values, so nothing breaks, but a desync dump grows by these fields.

## 10. Not in scope, with a reason each

- **Temporary or consumable upgrades** (source 3's durations). Pickups are already the temporary
  kind; add the enum with its first user.
- **Removing an upgrade** (source 3's removal by source id). No mechanic removes one. If one ever
  does, the ledger entry is the source id.
- **A power budget per upgrade** (source 3). Backlog, once there are fifteen or more upgrades.
- **Rerolls.** Not wanted in v1; see §5.6 for the shape if they ever are.
- **Reactive adaptations.** Only the hook, which is off (§5.12).
- **Carrying upgrades between levels.** S11.
- **Art for cards, pips and the sealed cache.** The alpha rule applies: no art without its own record.
- **Upgrades for turrets.** The gun belongs to the place (G0024).

## 11. Scaffolding this run introduces

| Scaffolding | Replaced by |
|---|---|
| Dev keys `F5`-`F7` take a card (S4) | Replaced by the draft bar in S5; the keys stay as harness keys, and the console offers are gone. |
| Cards, pips and the sealed cache are text and coloured shapes | Art, each with a record of its own |
| The catalogue is eight upgrades | Content, after S9 |
| Every level draws drafts at the same rate | Per-level overrides, as levels are designed |

## Appendix A. The external sources: what we took

The verdicts are Adopt (as proposed), Adapt (the idea, in this codebase's form), Reject (it conflicts
with a recorded decision) and Defer.

| Source item | Verdict | What we do |
|---|---|---|
| 1.1 Tag components to route upgrades | Adapt | Already there: `Commander`, `Trooper`, `Enemy`, `Faction.side`, `Strain.kind`, read by `Scope`. No new tags. |
| 1.2 Base stats plus a current-stats component per entity; 4.3 a recalculation system | Adapt | The principle stays: an immutable base and a derived current value. But no per-entity copy and no recalculation pass - the value is folded at the reader. Per-entity copies need the on-spawn hook and the re-apply pass that source 3 warns about. |
| 1.3 A weapon stats component | Have it | `Weapon`, from `WeaponDef`. |
| 1.4 Tactic tags; 2.2 component toggles; 4.2 command buffers for structural changes | **Reject** | Composition is fixed at spawn (G0010, ADR 0013), and a toggle would desync a drop-in. A tactic is numbers the mind already weighs, or a dormant capability. |
| 1.5 Skill components | Adapt | Capabilities carried from spawn, off at zero (`Thrower` on troopers). |
| 2.1 A definition as a ScriptableObject or JSON, with name, description and icon | Adapt | An enum, a record and a properties block (ADR 0008). The text is i18n and the icon is a look. The simulation never holds text. |
| 2.2 Weapon swap; source 3's "equipment add" | **Reject** | A gun is found and lost, never owned. |
| 2.3 A target enum | Adopt | `Target`, declared per effect (D1). |
| 3.1 Prerequisites, exclusivity and tiers; source 3's tier progression and exclusive sets | Adopt | `requires`, `group`, `tier` (by drafts earned) and `weight`. |
| 3.2 A weighted draw of three unique options | Adopt | On its own stream, with integer weights, in deal order, plus anti-streak. |
| 3.3 An event that pauses logic and awaits the choice | **Reject** | Lockstep cannot await. A pending draft is state and the choice is an input event; only a solo game pauses, and only the interface pauses it (D2). |
| 4.1 A dispatcher applying effects to tagged entities | **Reject** | Nothing is applied to entities. The only writer appends to the ledger; readers fold. |
| Source 2, A: permanent for the run | Adopt | Permanent for the level; the run is S11. |
| Source 2, B: base plus recalculation, never incremental float edits | Adopt | Always from the base, with whole-number percentages. |
| Source 2, C: the UI never touches entities | Have it | ADR 0015; the draft emits an input event. |
| Source 3, scope {Entity, Faction, Player, Global, Squad} | Adapt | Side, squad, commanders, chooser and team. No per-entity scope: one squad is shared (G0028), and an upgrade bound to an entity would die with the man. |
| Source 3, stack policies | Adapt | Additive whole points, a clamp per stat and a rank limit. Multiplicative stacking compounds and depends on order in floats; "replace highest" is what ranks already express. |
| Source 3, "adding an upgrade means adding a class" | Adapt | Adding an upgrade means a constant and a block. Adding a new *kind of effect* means a record in the sealed `Effect`, checked by the compiler. |
| Source 3, a deterministic pipeline, clamps, no zero or negative values, rounding tests | Adopt | §5.4 and the pure tests. |
| Source 3, an on-spawn hook for units spawned after an upgrade | Adopt the goal, **reject the hook** | Reading at the seam makes it true by construction, and `UpgradeTest` enforces it. |
| Source 3, a deterministic, server-validated reroll with a cost | Adapt, deferred | Deterministic, and validated identically by every peer. Never a currency - at most a count. |

## Appendix B. Keys this work adds (proposed values)

```
assets/sim/upgrades.properties
  sim.upgrade.draft.options=3               cards per draft
  sim.upgrade.draft.pointsPerDraft=2        the first capture earns at once, then every 2nd
  sim.upgrade.draft.wavesPerDraft=4         hold waves cleared per draft
  sim.upgrade.draft.bossesPerDraft=1
  sim.upgrade.draft.tierFrom=0,3,6          drafts earned before tier 1, 2 and 3 are dealt
  sim.upgrade.stat.<stat>.minPercent/.maxPercent/.min   every stat block states all three
  sim.upgrade.<kind>.side/weight/ranks/tier/requires/group/effects
  sim.adaptation.everySeconds=0             off
  sim.adaptation.perPlayerUpgrades=0        off
  sim.adaptation.answerWeight=0             off: the reactive hook
assets/sim/squad.properties
  sim.trooper.grenade.cooldownSeconds=10
  sim.trooper.grenade.minCluster=3
  sim.trooper.grenade.safetyMargin=4
  sim.trooper.grenade.squadGapSeconds=6
assets/sim/ai.properties
  sim.ai.chance.throw_a_grenade=0           off until Explosives
assets/sim/pickups.properties
  sim.pickup.armoury_<gun>.armoury.cards=3          railgun 3, flamer 3, shotgun 3, laser 2
  sim.pickup.armoury_<gun>.armoury.revealRadius=0   a gun's cache is never sealed
  sim.pickup.armoury_sealed.armoury.cards=0
  sim.pickup.armoury_sealed.armoury.revealRadius=30
  sim.pickup.armoury_sealed.*               radius 1.2, shine 2.6, look armoury_sealed, grants none
assets/controls.properties
  controls.key.draft=C
  controls.pad.draft=NORTH
  controls.pad.draftTrigger=NONE
assets/graphic.properties
  the sealed cache's colour, the bar's card frame and highlight
```
