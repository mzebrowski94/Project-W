# Two trooper profiles: SWAT and RETRO

Type: grilling
Status: open
Blocked by: 06
Section: The squad

## Question

Find a calmer trooper - less SWAT, more exploring the map on the way to the points - by changing numbers, then
offer both as an option: **SWAT** (today's numbers) and **RETRO** (the calmer ones). What moves, where does
the choice live, and who decides it online?

## From Mateusz (translated)

> Test changing parameters so the game is calmer (troopers less SWAT, more exploring the map). Moving towards
> the points but with more exploration. In the options we allow two modes of trooper settings: SWAT
> (current) / RETRO (calmer).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`. `docs/glossary.md` *urgency*, *scan*, *approach*, *forward*, *world config*, *local config*.
G0029, G0040, G0041. ADR 0018 (world config is the host's). `docs/ui/menu_layout_agent.md` (the options
page). `docs/design/alpha-phase.md` (a scenario to judge it by).

## Open

- The numbers: Mateusz judges by feel, so the session builds a scenario and a quick switch first, tunes with
  him, and only then puts the option on a page.
- A profile is world config (every peer must agree): the host's choice online, a block of overrides rather
  than a second copy of every key. Its glossary word.

## Done when

Both profiles in `assets/sim/`, the option on its page, the host's value online, `verify.sh` green.
