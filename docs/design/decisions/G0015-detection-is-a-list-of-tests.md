# G0015: Detection is a list of tests a strain declares

**Constraint.** What a unit can notice is decided by tests named on its `PerceptionDef` - a range, an
arc, a clear line - which `PerceptionSystem` runs **cheapest first and short-circuits**. A def that
declares range alone behaves exactly as detection did before this record. No system anywhere branches
on what kind of unit it is looking at: giving a strain a new sense means adding a test and declaring
it, not adding a case.

The order is a cost ordering, not a semantic one: squared distance, then a dot product against
facing, then a walk of the tile grid. The expensive test is reached only by candidates that already
passed the cheap ones, so its cost scales with how many fights are happening rather than with how
many units exist.

**Scope.** Noticing, and only noticing. This **narrows G0011 rather than contradicting it**, the same
way G0007 narrowed G0004: G0011's constraint - a unit notices the commander by distance alone, and
rock does not hide him - stands completely unchanged, because everything that hunts him declares
range and nothing else. What this record adds is that *a different unit may declare differently*,
which G0011 already anticipated in its own scope note.

It says nothing about weapons: no shot asks whether it has a clear line, and a projectile still finds
out about rock by flying into it. It says nothing about routing, which is a flow field over terrain.
And **only terrain blocks a line** - obstacles, corpses and other units do not, which is the same
asymmetry the project already accepts for routing ("a route plans around rock and steers around
everything else").

**Rejected, and likely to be re-proposed.**

*A special case in `PerceptionSystem` for the unit that needs the new rule.* The cheap version, and it
will be proposed the moment a third kind of sense is wanted. Rejected because it puts a unit's
identity inside a system - the same mistake G0003 refuses for drawing, for the same reason - and
because one special case is arguable while the second is permanent. A declared test costs a field.

*Line of sight for everything, overturning G0011.* Rejected for the three reasons G0011 gives, all of
which still stand. The one that matters most: it turns every wall into a place where standing still is
safe, which is a stealth mechanic in a game whose first pillar is that fights are decided by reflexes.
A trooper not shooting through rock does not do that, because the trooper is not what the player is
hiding from.

*A `Visibility` component carried only by units that can see.* The natural ECS shape and the one that
reads best. Rejected against G0010's enforced rule that only `Spawner` changes what an entity is made
of, and because an always-present def with an unused field costs nothing and keeps every unit's senses
readable in one table.

*Blocking the line on obstacles and corpses too.* The realistic version, and somebody will notice a
trooper shooting through a dying body. Rejected for now because it needs the collision grid, which is
built *after* the AI has run, so it would mean either a second broadphase or reordering the tick for
an answer the player will rarely be looking for. Named as a known limit instead.

**Revisit when** the tests stop being independent - when one needs another's result, or when the right
order stops being the cheap-to-expensive order and somebody has to think about which runs first. That
is a small evaluator rather than a list, and this record would be a poor description of it. G0011's own
trigger - cover becoming a mechanic the player is asked to use deliberately - is the other one, and it
has not fired.
