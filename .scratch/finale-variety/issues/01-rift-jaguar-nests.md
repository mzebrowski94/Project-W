# The rift's missing jaguar nests

Type: task
Status: open
Label: ready-for-agent
Blocked by: -
Skills: `/mattpocock-skills:diagnosing-bugs` with `docs/workflow.md`'s failure protocol

## What

`main` is red. `.claude/scripts/verify.sh` fails in `MissionSweepTest.holdsEveryMissionToTheRules`:

```
EHJD-YK39: biome 5 (RIFT): 0 of 1 nests of JAGUAR
MHJD-YK3G: biome 5 (RIFT): 0 of 2 nests of JAGUAR
HHJD-YK3D: biome 5 (RIFT): 0 of 2 nests of JAGUAR
3 faults in 88 seeds
```

Every later ticket needs a green verify, so this goes first. Seen on 2026-11-24 after commit `b9bb537` ("carved nooks for
the rift's nests"); not yet diagnosed.

## Settled

- The sweep's rule stands: a biome lays every nest its block asks for, or is placed again (G0070). Fix the generator or
  the rift's block, never the sweep's rule.
- A fix that changes what a rift looks like or holds (fewer jaguar nests, nests outside houses) is a gameplay change:
  write it down - an `## Amended by` on G0115 or G0112, or a spare G-record (G0123 first) - **provisional, for review**.

## Open for the implementer

- The cause. Suspects, cheapest first: the nooks carved for the rift's nests in `b9bb537` taking the room the jaguar's
  nests need; the jaguar's house rule (G0112) meeting the rift's fissure maze (G0115); `Scatter` running out of
  candidates in a `MissionConfig` tier with more nests.
- Added by author: You can change rift nest settings to nest 0 1 if it will fix things

## Read first

- `CLAUDE.md`; `docs/workflow.md` (ladder, failure protocol).
- G0068, G0070, G0112, G0115.
- `assets/sim/biomes.properties` (the rift and the jaguar's blocks), `Mission`, `Scatter`, `MissionFaults`,
  `MissionSweepTest`.

## Done when

- The three codes above lay their jaguar nests, a test reproducing one of them is green, and `verify.sh` is green.
- `## Answer` names the cause, and a mission code with a rift to look at.
