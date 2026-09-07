---
description: Format, compile, lint and test Project-W
---

Run the project's verify loop in the shell that has this repository mounted (in a Cowork session
that is the device shell, not the cloud container):

```
.claude/scripts/verify.sh $ARGUMENTS
```

No arguments runs `spotlessApply build`, covering compilation, Error Prone, NullAway, ArchUnit and
every test. Pass Gradle task names to narrow it, e.g. `:sim:test`.

Report compactly. On pass: say so and mention any Error Prone warnings; do not paste the test list
unless asked. On fail: the failing file and line, what the checker or assertion objected to, and
your read of the cause - not the whole log, which is at `ai/logs/verify.log`.

Never claim a change works without having run this.
