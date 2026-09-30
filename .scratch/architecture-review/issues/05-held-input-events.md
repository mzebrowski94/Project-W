# 05 - One queue for held-back input events

Status: needs-triage

Strength: Strong - likely live bug. Dependency: in-process; `PeerSession` is local-substitutable
(`LoopbackNetwork`, `ManualClock`). Report candidate 6.

## Problem

The `ScenarioPlay` seam is real - two adapters, and `ScenarioScreen` never branches on which it holds. But
each adapter buffers a local player's **draft** picks and **crate** choices its own way:

- offline, by slot: `ScenarioSession:61-74, 132-150, 285-308` - a take and a close both handled;
- online, by seat: `OnlineSession:66-73, 224-240, 250-266` - a take *else* a close.

Online moves the pending events into `local` before `peer.pump`, and `PeerSession.pump` returns early
unless the state is `PLAYING` (`PeerSession:218`): a pick buffered then is silently dropped. Rare, since a
pick needs the draft bar.

Coverage: `ScenarioSessionTest` has 35 tests, 8 on picks, 0 on crates; `OnlineSessionTest` has 9, none on
picks or crates.

## Direction

One module holds a local player's one-off events until a tick takes them; both adapters feed it, with one
set of take and close rules, tested once.

## Constraint

ADR 0012 rejects "a one-off input as a held flag": the module keeps them as **input events**, delivered
once.

## First step

A failing test in `OnlineSessionTest`: a pick made before the session is `PLAYING` still reaches the world.

## Comments
