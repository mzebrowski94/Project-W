package pl.mzebrows.pwt.sim;

/**
 * A commander walking in a circle, firing most ticks and swinging, acting and dragging every so often.
 *
 * <p>Enough to exercise every system in every scenario - the seeded wind-up before a leap among them -
 * and shared by the tests that replay whole scenarios, so they all drive the world the same way.
 */
final class ScriptedPlay {

    private ScriptedPlay() {}

    static CommanderInput input(int step) {
        double angle = step * 0.05;
        return new CommanderInput(
                        (float) StrictMath.cos(angle),
                        (float) StrictMath.sin(angle),
                        80f,
                        45f,
                        step % 7 != 0,
                        step % 11 == 0)
                .acting(step % 13 == 0)
                .dragging(step % 17 == 0);
    }
}
