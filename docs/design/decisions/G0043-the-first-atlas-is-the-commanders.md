# G0043: The first atlas is the commander's

**Status.** accepted

**Trigger.** Adding art for any look; changing a renderer; drawing a sprite, a portrait or a UI skin.

**Enforced by.** Nothing mechanical yet - caught by reading. When the puppet lands, `PuppetLibrary`
validates each look's frames at load and falls back to the shape per look.

**Constraint.** **Amends G0003**: it replaces the *temporary* half of G0003 for looks that have a complete puppet, and for
nothing else.

- The first atlas holds three things: the commander's **puppet** (the layered sprite he is drawn as),
  his **portrait** in the player panel, and the **UI skin** - the frames, fills and icons of the bars and
  pages.
- Every other look stays a shape and a colour until a record of its own says otherwise.
- A look whose puppet is missing or incomplete is drawn as its shape - per look, logged once at load,
  never per frame. Art can only ever be missing; it can never stop the game.
- The permanent half of G0003 is unchanged: drawing reads `Appearance`, a puppet is chosen by `Look`,
  and nothing about a sprite reaches the simulation. Hit areas stay circles (ADR 0007); the shadow is
  drawn at the body's radius so the one part of the sprite that must not lie, does not.
- World animation runs on simulation time (tick and render alpha), never the wall clock, so a paused
  game is a frozen picture and a replay looks the same (pillar 6).

**Why.** The commander is the unit the player *is* (pillar 2) and is on screen every frame; he is also
the one look whose facts - aim independent of movement (G0002), heat, a bleed-out - a circle with a
spoke shows least well. A HUD needs a skin and a portrait anyway. Starting the art with one look keeps
the cost of finding out whether the pipeline works to one set of frames.

**Scope.** Which looks may have art, and what happens when it is missing. Not the art style, not the
pipeline that produces it, not animation for troopers or aliens.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Sprites for other looks without their own decision.* Each is a cost and a style commitment; the
circle is still the fastest way to find out a mechanic is not fun (G0003's reason, which stands for them).

**No:** *Choosing a puppet by class or by a gameplay component* - `if has Commander`. G0003's permanent
half: by `Look`, from `Appearance`.

**No:** *Gameplay reading anything about a sprite* - its size, its frame, where its gun is drawn. If the
art and the simulation disagree about where a shot leaves, the art changes.

**No:** *Animation driven by the wall clock.* It would keep moving in a paused game and differ in a replay.

**Revisit when** a second look is asked to have a puppet - that is a record naming it.
