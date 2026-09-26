# 0016: An entity is named by its handle

**Trigger.** A component that has to find another entity on a later tick - an owner, a target, a load,
a station; spawning an entity anywhere but `Spawner`; changing `Handles.resolve` or the order families
keep.

**Enforced by.** `HandleTest` - every entity carries a handle, handles rise in spawn order, a reaped
entity resolves to nobody even when its pooled object comes back as something else, a swing whose owner
is gone follows nobody, and **no component declares an `Entity` field**. `DeterminismFuzzTest` and
`SnapshotRoundTripTest` carry the handles and `Level.nextHandle` through restores like any other field.

**Constraint.** Every entity carries a `Handle`: a `long` issued from `Level.nextHandle` when `Spawner`
adds it (`admit`), never reused. A component that refers to another entity holds that entity's handle,
and a system finds it with `Handles.resolve(handled, id)` - null if it is not on the field. Zero,
`Handle.NOBODY`, refers to nobody. Handles rise in the order entities are added, which is the order
every family keeps (ADR 0013), so the family of handled entities is sorted and the lookup is a binary
search: no index, no cache, nothing extra for a snapshot to carry.

This supersedes ADR 0014's allowance of an `Entity`-typed component field. `WorldCodec` still knows how
to write one - as its place in spawn order - but no component has one to write.

**Why.** `PooledEngine` recycles its `Entity` objects. One kept across a tick comes back aimed at
whatever was spawned next: `Strike.owner` did exactly that - a commander reaped mid-swing had his
object reused by the next spawn, and the swing followed it. G0004, G0010 and G0016 each answered this
with a rule of the form "hold a place, not a reference", and two mechanics grew their own id schemes to
get round it: a drag counter on `Level`, and a station counter held in `Spawner` - not in the snapshot,
so a world restored from one could issue a turret an id already in use. One scheme replaces all three.

**Scope.** References that outlive the tick they were made in. A reference used within one tick and
dropped - a local, a family iterated now - is still an `Entity`. It says nothing about *what* a system
should keep a reference to: G0010's argument for steering by a published place rather than a remembered
target is a design choice about behaviour, and stays one; handles only make the other choice safe when a
future mechanic wants it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A generation counter paired with a slot index.* It exists to recycle ids, and a 64-bit counter
issued once per spawn never runs out.

**No:** *An id-to-entity map kept beside the engine.* A second structure to keep in step with every add
and removal, and a hash map in `sim` besides - the sorted family already is the index.

**No:** *Replacing Ashley to get handles.* This is the part of that change worth having, at the cost of
one component.

**Revisit when** entities are added outside `Spawner` or `WorldCodec`, or a family stops being kept in
spawn order - either breaks the sort the lookup relies on.
