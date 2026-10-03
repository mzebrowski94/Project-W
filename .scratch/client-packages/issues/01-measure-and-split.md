# 01 - Measure the client and choose the split

Type: research

Blocked by: architecture-review 05, 03, 04, 06, 09

## Question

Which layer packages, holding which classes, leave the fewest ports - and what does each port carry?

## Read first

`.scratch/client-packages/map.md`, `docs/adr/0020-systems-live-in-fenced-packages-by-domain.md`,
`.scratch/system-modules/issues/03-measure-system-graph.md` (*Answer* - the same deliverables, for `sim`),
`ClientArchitectureTest`.

## Settled

By layer; package-private by default; ports declared; the review tickets are in.

## Deliver

1. `system-graph.py` taught a second root (`--root core/src/main/java/pl/mzebrows/pwt/client`), not a copy.
2. A split file beside this map, checked by the script; every public class and why.
3. Where each of the client tests goes, and which reach package-private code in two packages.
4. The `Gdx`-free rule by package: which packages may touch `Gdx`, devices or GL.
5. Every hard-coded path: `ClientArchitectureTest`, `docs/architecture.md` (*core (client)*), the
   `unit-tests` skill's exemplars, `lwjgl3`.

## Done when

The split is in `map.md` *Decisions*, approved by Mateusz.
