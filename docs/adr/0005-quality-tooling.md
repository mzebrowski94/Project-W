# ADR 0005: Spotless, Error Prone, NullAway and Lombok

**Constraint.** All four run on every build; versions are in `gradle.properties`. Formatting is
never hand-applied - Spotless rewrites. Every package carries `@NullMarked`, and a NullAway
violation fails the build.

**Scope.** NullAway is deliberately disabled for test compilation - tests exist to catch our bugs,
not to satisfy a null checker. Lombok is used for constructor and accessor boilerplate, but **not**
on ECS components, which use public mutable fields; `docs/conventions.md` explains why.

**Rationale that is not obvious from the build file.** Most code here is written by an AI and
reviewed by one person. Tooling that catches a class of mistake mechanically is worth more in that
arrangement than on a project where every line is typed by its reviewer. Spotless in particular
exists so review sees only real changes.

**Rejected, and likely to be re-proposed.** Checkstyle - describes style rather than fixing it,
which is strictly worse when a machine writes the code.

**Revisit when** any of the four blocks a JDK upgrade. Lombok is the likely candidate and the
easiest to drop; nothing depends on it deeply.
