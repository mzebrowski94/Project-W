# 02 - Fence and move the client

Type: task

Blocked by: 01

## Question

Move every client class into its layer package behind the fences, one package per stage.

## Read first

`.scratch/client-packages/map.md` (01's split), `.scratch/system-modules/issues/04-pilot-squad.md` and
`05-remaining-packages.md` (*Answer* - what moving broke last time), `ClientArchitectureTest`.

## Steps

1. The fences first, red: `FENCED`, `PORTS`, every class in a declared package, the `Gdx`-free rule by
   package - each `.because` naming the new ADR, failing on zero classes.
2. One package per stage with `.claude/scripts/move-classes.py`; compile; make public what it names;
   `package-info.java`; `verify.sh`; commit.
3. The new ADR (amends 0020's *Scope*) with its row in `docs/adr/README.md` and 0020's status;
   `docs/architecture.md`'s *core (client)* section as a package table.

## Done when

`verify.sh` green, no class directly in `client` but what 01 left in the root, the ADR in `docs/adr/`.
