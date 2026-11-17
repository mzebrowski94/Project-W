# G0098: The action names its subject, and the interface marks it

**Status.** accepted

**Trigger.** Drawing anything on what the action key would act on; proposing to let the player pick among
things in reach; making a touch pickup a key press; putting a mark or a prompt over the view.

**Enforced by.** `ActionTest.namesTheManDownBesideHimAsTheSubject`, `.namesNobodyWhenThereIsNothingToDo`,
`DoorTest.namesTheShutDoorAsTheSubject`, `.namesTheManDownBesideHimRatherThanADoorAsNear`,
`TurretTest.namesTheTurretBesideHimAsTheSubject`, `.namesTheTurretHeIsOnAsTheSubject`,
`CrateTest.namesTheCrateBesideHimAsTheSubject`, `.namesNobodyWhileHeHasTheCrateOpen`, `AidTest.namesTheManHeIsPickingUpAsTheSubject`;
`ReadoutWriterTest.carriesTheSubjectOfHisActionAndHisOwnHandle`, `.carriesAPinAndNotACling`, `PromptsTest` (which
prompt, in which order, over what) and `ScenarioSessionTest.promptsTheHoldOverAManWhoGoesDownWithinReach`. How the
brackets and the prompt look is judgement.

**Asked for** by Mateusz, 2026-11-17: interactable things should be highlighted near the commander, with a prompt
saying the key and what it does. Grilled the same day.

**Amends G0021** - its *nothing is named, only kinds*. The kind is still all the simulation acts on; what the key
would act on is now also published, by handle, for the interface to show.

**Constraint.** `Action.subject` carries the **handle** of the action's **subject** - the casualty, the gun, the
crate, the shut door, or the gun he is on - and `Handle.NOBODY` whenever nothing is on offer. It is written where the
offer is made (`ActionSystem` for a commander, `AidSystem` for a trooper, so the field means the same on both), from
the choice G0021 already makes: nearest first, a tie to the gun, then the crate, the casualty and the door. Nothing in
the simulation reads it.

- **One place chooses.** The interface draws the bracket on the entity the handle resolves to. It never asks
  `Reach` itself: a second copy of the tie order is a second way to disagree about which thing the key means.
- **A handle, never an entity** (ADR 0016). Handles are never reused and `reset()` clears it, so a pooled `Action`
  cannot come back naming whatever replaced the subject - the failure G0021's rule was written against.
- **What the interface does with it**, settled in the same grilling and specified in `docs/ui/menu_layout_agent.md`
  as each stage lands: corner **brackets** on the subject, faint ones on what else is within the **notice
  distance** (`graphic.properties`, its own number - never a multiple of **reach**), and a see-through **prompt**
  above the subject - the key and the verb, for this machine's players only. Touch **pickups** keep being taken by
  touch (G0006) and get smaller ticks of their own colour, only where they are worth something to a commander near
  them (`Grants`). A prompt to take an upgrade carries how many drafts he holds once he holds more than one -
  `UPGRADE x2`, as his panel's tell does.

**Scope.** The commander's action key and what the interface marks for it. Not hauling (`F`, G0021's exception),
whose offer nothing publishes yet; not how a pickup is taken.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Choosing the subject by facing or aim - a dot product with the aim, or nearness to the middle of the
screen.* A second rule to learn on top of nearest, for overlaps that are rare; the bracket already shows which
thing the key means, and a step is the fix (G0021). The middle of a shared screen is nobody's.

**No:** *Taking a medkit or a grenade cache with the action key.* They are taken by touch (G0006), a trooper takes a
medkit by touch too, and the worth check already stops one being spent on a man it does nothing for - so the press
buys nothing and adds a subject to the key's list, which is G0021's revisit trigger.

**No:** *A red icon or red mark over the view* - for a man pinned, or anything else. Alert red is the bars' alone
(`docs/ui/menu_layout_agent.md`, section 10); a pin is said in his panel, in alert, and the prompt over him is
neutral.

**No:** *A ring as the highlight.* A ring already says a charge, a shine, a shield or a grip's clock, and a door has
no body to put one round. Brackets.

**No:** *The interface finding the subject from what it can see.* See *One place chooses*.

**Revisit when** a subject must be marked that is not an entity, or the key's list gains a kind whose subject is
something other than the nearest of its kind.
