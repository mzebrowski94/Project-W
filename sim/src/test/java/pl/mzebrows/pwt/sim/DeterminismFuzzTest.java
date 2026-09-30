package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * Five minutes of every scenario under seeded random input - four players, orders, a late join, god mode
 * switched back and forth - with the world snapshotted at random ticks and a twin restored from it
 * stepping alongside (ADR 0014). Every tick the twin must hash as the original does. Reaches what the
 * scripted circle never does, and what a drop-in or a heal would meet.
 *
 * <p>Opt-in, because it runs for minutes: {@code PWT_FUZZ=1}. Worth a run after any change to a system's
 * fields or a component's, and before a playtest.
 */
@EnabledIfEnvironmentVariable(named = "PWT_FUZZ", matches = "1")
class DeterminismFuzzTest {

    private static final int TICKS = 18_000;
    private static final int SLOTS = 4;
    private static final Order[] ORDERS = Order.values();
    private static final UpgradeKind[] KINDS = UpgradeKind.values();
    private static final PickupKind[] PICKUPS = PickupKind.values();

    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredScenarios")
    void aWorldRestoredAtAnyTickStaysWithTheOriginal(String name, Scenario scenario) {
        long seed = 0x5EED_0000L + name.hashCode();
        Rng dice = new Rng(seed);
        Simulation original = Scenarios.world(scenario, SimConfig.defaults().withSeed(seed));
        Simulation twin = null;
        int nextRestore = 300 + dice.nextInt(900);
        PlayerInputs inputs = new PlayerInputs(original.capacity());
        boolean invulnerable = false;
        for (int tick = 0; tick < TICKS; tick++) {
            inputs.clear();
            for (int slot = 0; slot < SLOTS; slot++) {
                inputs.set(slot, randomHeld(dice));
                if (dice.nextFloat() < 0.01f) {
                    inputs.order(slot, ORDERS[dice.nextInt(ORDERS.length)]);
                }
            }
            if (tick == 1 || tick == 2_000 + (int) (seed & 1023)) {
                // A second machine as often as not, so the leash is reckoned both ways (G0056).
                inputs.join(tick == 1 ? 1 : 2 + dice.nextInt(SLOTS - 2), dice.nextInt(2));
            }
            if (dice.nextFloat() < 0.002f) {
                // A pick is an input event: most name a draft that is gone or a card not offered, and change nothing.
                inputs.pick(dice.nextInt(SLOTS), 1 + dice.nextInt(8), KINDS[dice.nextInt(KINDS.length)]);
            }
            if (dice.nextFloat() < 0.003f) {
                // A crate's events: most name a crate the slot does not have open, or a pickup it does not hold.
                int slot = dice.nextInt(SLOTS);
                if (dice.nextFloat() < 0.5f) {
                    inputs.closeCrate(slot);
                } else {
                    inputs.takeFromCrate(slot, PICKUPS[dice.nextInt(PICKUPS.length)]);
                }
            }
            if (dice.nextFloat() < 0.0005f) {
                int slot = 1 + dice.nextInt(SLOTS - 1);
                if (dice.nextFloat() < 0.5f) {
                    inputs.away(slot);
                } else {
                    inputs.back(slot);
                }
            }
            if (dice.nextFloat() < 0.001f) {
                invulnerable = !invulnerable;
                inputs.switchInvulnerability(invulnerable);
            }
            original.step(inputs);
            if (twin != null) {
                twin.step(inputs);
                long expected = WorldCodec.hash(original);
                if (WorldCodec.hash(twin) != expected) {
                    assertThat(WorldCodec.differences(WorldCodec.snapshot(original), WorldCodec.snapshot(twin), 20))
                            .as("%s parted from its twin at tick %d", name, tick)
                            .isEmpty();
                }
            }
            if (tick == nextRestore) {
                twin = Scenarios.blank(scenario, SimConfig.defaults().withSeed(seed));
                WorldCodec.restore(twin, WorldCodec.snapshot(original));
                nextRestore += 300 + dice.nextInt(2_700);
            }
        }
    }

    private static CommanderInput randomHeld(Rng dice) {
        return new CommanderInput(
                        dice.nextFloat() * 2f - 1f,
                        dice.nextFloat() * 2f - 1f,
                        dice.nextFloat() * 200f,
                        dice.nextFloat() * 200f,
                        dice.nextFloat() < 0.7f,
                        dice.nextFloat() < 0.05f)
                .acting(dice.nextFloat() < 0.05f)
                .dragging(dice.nextFloat() < 0.03f);
    }

    private static Stream<Arguments> registeredScenarios() {
        return Scenarios.all().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
