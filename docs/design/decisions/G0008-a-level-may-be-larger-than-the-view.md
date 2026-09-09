# G0008: A level may be larger than the view

**Constraint.** Pillar 4 is amended. It used to read:

> **One screen, one fight.** A level reads at a glance. No fog of war, no off-screen management.

It now reads:

> **One fight, and you can always see it.** No fog of war, no off-screen management, no second front
> to check on. A level may run wider than the view - the camera follows you, and one key pulls it
> back for a look - but the fight is always the one you are standing in.

What the old wording was protecting is kept in full: nothing is hidden from the player, nothing is
managed out of sight, and there is never a second place that needs attention while you are fighting
here. What it is no longer doing is fixing a level's size to the size of a window.

**Scope.** Level extent and the camera. It permits a map larger than the view; it does not permit
anything the old pillar forbade. Concretely, still ruled out: fog of war, an off-screen base or
second objective, resources or units to manage somewhere you cannot see, and any mechanic that
requires the player to remember what is happening off screen rather than react to what is in front of
them. A level that needs a minimap to be playable has broken this pillar even though it never breaks
the letter of it.

The permission is bounded by what the camera and the zoom actually do, which is a level a few screens
across - not an open world, and not a level with a far half you travel to and fight in separately.

**Rejected, and likely to be re-proposed.**

*Keeping the pillar and capping every level to the view.* Rejected deliberately rather than by
drift. Tiles exist to make generated map fragments cheap, and a generator whose output can only ever
be one screen wide has almost no room to produce anything the player would recognise as different
places. The cost is real and is accepted: a level is no longer legible in one glance, and the zoom
key is what pays for that.

*A per-level-type exception, with pillar 4 standing as the default.* Rejected because it needs a
rule about which levels are which, and because the exception becomes the default by drift - at which
point the pillar says one thing and every level does another, which is worse than amending it
honestly.

*A minimap.* Rejected, and worth naming because it is the obvious answer to "the level is bigger than
the screen". A minimap is off-screen management with a nicer name: it puts information the player
needs somewhere other than the field they are fighting on. The zoom is the answer instead, precisely
because it makes you stop looking at the fight to use it.

**Revisit when** playtesting says a map a few screens across cannot be read even with the zoom. That
is a signal the amendment went too far, and the answer is smaller maps, not a minimap.
