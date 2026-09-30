# 0011: A gamepad reaches the game through a port of our own

**Status.** accepted

**Trigger.** Changing the controller library, adding a device, or proposing to call gdx-controllers
from anywhere that decides what a button means.

**Enforced by.** Nothing mechanical - caught by reading. `GdxGamepads` is the only file in the
repository that imports `com.badlogic.gdx.controllers`; the check is a grep, and it is worth one.

**Constraint.** Gamepads reach the game through `GamepadSource`, `Gamepad`, `GamepadAxis` and
`GamepadButton`, all of which are plain Java in `core`. No type from any controller library crosses
that line. Swapping the library is writing one new implementation of two small interfaces and
changing nothing that decides what an input means.

**gdx-controllers 2.2.4 is what sits behind it today.** It was already declared - `core` had
`gdx-controllers-core` and the desktop launcher `gdx-controllers-desktop`, both from the gdx-setup
template, both unused - so switching pads on cost no new dependency. On the desktop it is SDL
underneath, by way of Jamepad. It gives normalised axes, a canonical button mapping and a stable id
per pad, which is everything the port asks for.

**The port exists because the mapping is the doubtful part.** gdx-controllers' own documentation says
its mapping is guaranteed on some platforms and merely usually right on others, and the pads it has
to be right about are Mateusz's, not a test matrix's. The port is what makes "it is wrong on this pad"
a one-file change rather than a rewrite.

**Buttons are named by position, never by letter.** `SOUTH` is A on an Xbox pad and Cross on a
PlayStation one; `WEST` is X and Square. One binding table serves both, and `PadLabels` turns a
position into whatever is printed on the pad the player is holding. A second binding table is how a
game ends up with the PlayStation map one patch behind the Xbox one.

**Scope.** Input devices. It says nothing about who is sitting in which slot - that is `PlayerSeats`
and G0028 - and nothing about what a stick means, which is `PadControls` and is unit-tested against
pads made of plain values.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Calling `Controllers.getControllers()` from wherever a button is needed.* That is the version
of this with no port at all, and it makes the library's uncertainty about mappings a problem in every
file that reads a button instead of in one.

**No:** *An event-stream port - `onConnected`, `onButtonDown`.* Every library has its own idea of which
thread callbacks arrive on and whether they fire at all on a given platform, and a port shaped like
the widest of them is no port. Asking "who is plugged in right now" is something all of them can
answer, and `PlayerSeats` works out arrivals and departures by comparing frames.

Not rejected, deferred: *input4j* - the better second implementation if the first one
disappoints: pure Foreign Function and Memory API, so no native artifacts at all, XInput and
DirectInput on Windows, evdev on Linux, IOKit on macOS, and Java 22+ which this project already
exceeds. Against it: one maintainer, a small user base, no libGDX integration and a polling model of
its own. It is a bet, and the port is what makes taking it cheap later rather than now.

Not rejected, deferred: *GLFW's own gamepad API* - the cheapest fallback of the three: the
desktop launcher already ships GLFW, `glfwGetGamepadState` uses the SDL controller database, and the
canonical layout is fixed by GLFW rather than guessed. The cost is that the implementation has to
live in `lwjgl3` and be injected, because `core` cannot see the backend - which is desktop-only, and
this game is desktop-only.

**No:** *Jamepad directly, or an SDL3 binding.* Rewriting what gdx-controllers already wraps, for a
newer controller database and nothing else.

**Revisit when** a pad Mateusz owns reports the wrong buttons, or a platform other than the desktop
matters. The first is what the port is for; the second is the only thing that would rule GLFW out.
