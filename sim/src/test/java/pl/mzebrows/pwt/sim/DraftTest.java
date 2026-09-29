package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.A_BLOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.upgrade.DraftDef;
import pl.mzebrows.pwt.sim.upgrade.Effect;
import pl.mzebrows.pwt.sim.upgrade.Stat;
import pl.mzebrows.pwt.sim.upgrade.Target;
import pl.mzebrows.pwt.sim.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * An upgrade is earned on the field and drafted in turn (G0049): objectives earn drafts at their rate,
 * each goes to the next present slot, a pick is an input event naming the draft and the kind, and it
 * counts from the next tick - or not at all, if anything about it is wrong.
 */
class DraftTest {

    private static final float MIDDLE = 50f;

    @Test
    void earnsADraftForSlotZeroWhenAPointIsTakenInSinglePlayer() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        take(simulation.spawnControlPoint(20f, 20f));

        simulation.step();

        assertThat(drafts(simulation).pending).singleElement().satisfies(draft -> {
            assertThat(draft.chooser()).isZero();
            assertThat(cards(simulation, draft.serial()))
                    .containsExactlyInAnyOrder(UpgradeKind.LARGER_ARMY, UpgradeKind.WEAPON_HANDLING);
        });
    }

    @Test
    void earnsNothingWhileNothingIsTaken() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnControlPoint(20f, 20f);

        AlphaWorlds.steps(simulation, 30);

        assertThat(drafts(simulation).pending).isEmpty();
    }

    @Test
    void goesRoundThePresentSlotsInOrder() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 2);
        List<Entity> points = points(simulation, 3);

        for (Entity point : points) {
            take(point);
            simulation.step();
        }

        assertThat(drafts(simulation).pending).extracting(Drafts.Draft::chooser).containsExactly(0, 2, 0);
    }

    @Test
    void appliesAPickOneRankAtATime() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        take(simulation.spawnControlPoint(20f, 20f));
        simulation.step();
        int serial = drafts(simulation).pending.first().serial();

        simulation.step(new PlayerInputs().pick(0, serial, UpgradeKind.LARGER_ARMY));

        assertThat(ledger(simulation).held)
                .containsExactly(new Upgrades.Held(UpgradeKind.LARGER_ARMY, Upgrades.SHARED, 1));
        assertThat(drafts(simulation).pending).isEmpty();
        assertThat(drafts(simulation).cards).isEmpty();
    }

    @Test
    void changesNothingForAPickThatIsWrongInAnyWay() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        take(simulation.spawnControlPoint(20f, 20f));
        simulation.step();
        int serial = drafts(simulation).pending.first().serial();

        simulation.step(new PlayerInputs().pick(0, serial + 1, UpgradeKind.LARGER_ARMY));
        simulation.step(new PlayerInputs().pick(1, serial, UpgradeKind.LARGER_ARMY));

        assertThat(ledger(simulation).held).isEmpty();
        assertThat(drafts(simulation).pending).hasSize(1);
    }

    @Test
    void changesNothingForAKindTheDraftDoesNotOffer() {
        Simulation simulation = new Simulation(onlyArmy(AlphaWorlds.config()));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        take(simulation.spawnControlPoint(20f, 20f));
        simulation.step();
        int serial = drafts(simulation).pending.first().serial();

        simulation.step(new PlayerInputs().pick(0, serial, UpgradeKind.WEAPON_HANDLING));

        assertThat(ledger(simulation).held).isEmpty();
        assertThat(drafts(simulation).pending).hasSize(1);
    }

    /** A card whose last rank has just been taken cannot be picked, so it is re-dealt in its place. */
    @Test
    void reDealsACardAPickMadeStale() {
        Simulation simulation = new Simulation(armyOfOneRank(AlphaWorlds.config()));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        List<Entity> points = points(simulation, 2);
        take(points.get(0));
        take(points.get(1));
        simulation.step();
        int first = drafts(simulation).pending.get(0).serial();
        int second = drafts(simulation).pending.get(1).serial();

        simulation.step(new PlayerInputs().pick(0, first, UpgradeKind.LARGER_ARMY));

        assertThat(cards(simulation, second)).containsExactly(UpgradeKind.WEAPON_HANDLING);
        assertThat(drafts(simulation).pending.first().revision()).isEqualTo(1);
    }

    @Test
    void passesTheDraftsOfAnAwaySlotOnAndTakesItBackIntoTheRotation() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        List<Entity> points = points(simulation, 3);
        take(points.get(0));
        simulation.step();

        simulation.step(new PlayerInputs().away(0));
        assertThat(drafts(simulation).pending.first().chooser()).isEqualTo(1);

        take(points.get(1));
        simulation.step();
        simulation.step(new PlayerInputs().back(0));
        take(points.get(2));
        simulation.step();

        assertThat(drafts(simulation).pending).extracting(Drafts.Draft::chooser).containsExactly(1, 1, 0);
    }

    @Test
    void earnsAtTheConfiguredRateFromEachSource() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(
                config.withDraft(new DraftDef(3, 2, 0, 0, config.draft().tierFrom())));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        List<Entity> points = points(simulation, 3);
        List<Integer> earned = new ArrayList<>();

        for (Entity point : points) {
            take(point);
            simulation.step();
            earned.add(drafts(simulation).earned);
        }

        assertThat(earned).containsExactly(1, 1, 2);
    }

    /** A draft the pool cannot fill is not earned, and is counted so the interface can say so. */
    @Test
    void countsADraftNothingWasLeftToDeal() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(off(off(config, UpgradeKind.LARGER_ARMY), UpgradeKind.WEAPON_HANDLING));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        take(simulation.spawnControlPoint(20f, 20f));

        simulation.step();

        assertThat(drafts(simulation).pending).isEmpty();
        assertThat(drafts(simulation).fromPoints).isEqualTo(1);
        assertThat(simulation.upgradeView().dry()).isEqualTo(1);
    }

    /** An upgrade belongs to the slot, never to the life: the opposite of the tally (G0044). */
    @Test
    void keepsHisSlotsPersonalRanksWhenHeComesBack() {
        Simulation simulation = new Simulation(personalHandling(AlphaWorlds.config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        take(simulation.spawnControlPoint(20f, 20f));
        simulation.step();
        simulation.step(
                new PlayerInputs().pick(0, drafts(simulation).pending.first().serial(), UpgradeKind.WEAPON_HANDLING));

        commander.getComponent(Damage.class).add(A_BLOW, 0);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        AlphaWorlds.steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 2);

        assertThat(Players.inSlot(
                        simulation
                                .engine()
                                .getEntitiesFor(Family.all(Commander.class).get()),
                        0))
                .isNotNull();
        assertThat(ledger(simulation).held).containsExactly(new Upgrades.Held(UpgradeKind.WEAPON_HANDLING, 0, 1));
    }

    /** The ledger and the drafts are level state: a snapshot carries them like anything else. */
    @Test
    void carriesTheLedgerAndTheDraftsThroughASnapshot() {
        SimConfig config = AlphaWorlds.config();
        Simulation original = new Simulation(config);
        original.spawnCommander(MIDDLE, MIDDLE);
        List<Entity> points = points(original, 2);
        take(points.get(0));
        take(points.get(1));
        original.step();
        original.step(new PlayerInputs()
                .pick(0, drafts(original).pending.first().serial(), UpgradeKind.LARGER_ARMY)
                .away(1));

        Simulation restored = new Simulation(config);
        WorldCodec.restore(restored, WorldCodec.snapshot(original));

        assertThat(WorldCodec.snapshot(restored)).isEqualTo(WorldCodec.snapshot(original));
        assertThat(ledger(restored).held).isNotEmpty();
        assertThat(drafts(restored).pending).isNotEmpty();
    }

    @Test
    void dealsTheSameDraftsAndAppliesTheSamePicksOnEveryRun() {
        assertThat(pickedRun()).isEqualTo(pickedRun());
    }

    private static long pickedRun() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        for (Entity point : points(simulation, 4)) {
            take(point);
            simulation.step();
        }
        Drafts.Draft first = drafts(simulation).pending.first();
        simulation.step(new PlayerInputs()
                .pick(
                        first.chooser(),
                        first.serial(),
                        cards(simulation, first.serial()).getFirst()));
        AlphaWorlds.steps(simulation, 10);
        return WorldCodec.hash(simulation);
    }

    private static void take(Entity point) {
        point.getComponent(ControlPoint.class).captured = true;
    }

    private static List<Entity> points(Simulation simulation, int count) {
        List<Entity> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(simulation.spawnControlPoint(10f + 12f * i, 15f));
        }
        return points;
    }

    static Drafts drafts(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Drafts.class).get())
                .first()
                .getComponent(Drafts.class);
    }

    static Upgrades ledger(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Upgrades.class).get())
                .first()
                .getComponent(Upgrades.class);
    }

    private static List<UpgradeKind> cards(Simulation simulation, int serial) {
        List<UpgradeKind> kinds = new ArrayList<>();
        for (Drafts.Card card : drafts(simulation).cards) {
            if (card.serial() == serial) {
                kinds.add(card.kind());
            }
        }
        return kinds;
    }

    /** Weapon Handling switched off, so every draft offers Larger Army alone. */
    private static SimConfig onlyArmy(SimConfig config) {
        UpgradeDef handling = config.upgrades().of(UpgradeKind.WEAPON_HANDLING);
        return config.withUpgrades(config.upgrades()
                .with(
                        UpgradeKind.WEAPON_HANDLING,
                        new UpgradeDef(
                                handling.side(),
                                0,
                                handling.ranks(),
                                handling.tier(),
                                handling.requires(),
                                handling.group(),
                                handling.effects())));
    }

    /** Larger Army can be taken once, so a second draft offering it goes stale the moment the first is taken. */
    private static SimConfig armyOfOneRank(SimConfig config) {
        UpgradeDef army = config.upgrades().of(UpgradeKind.LARGER_ARMY);
        return config.withUpgrades(config.upgrades()
                .with(
                        UpgradeKind.LARGER_ARMY,
                        new UpgradeDef(army.side(), army.weight(), 1, army.tier(), null, 0, army.effects())));
    }

    /** Weapon Handling as a personal upgrade, reaching only the commander of the slot that took it. */
    private static SimConfig off(SimConfig config, UpgradeKind kind) {
        UpgradeDef def = config.upgrades().of(kind);
        return config.withUpgrades(config.upgrades()
                .with(
                        kind,
                        new UpgradeDef(
                                def.side(), 0, def.ranks(), def.tier(), def.requires(), def.group(), def.effects())));
    }

    private static SimConfig personalHandling(SimConfig config) {
        return config.withUpgrades(config.upgrades()
                .with(
                        UpgradeKind.WEAPON_HANDLING,
                        new UpgradeDef(
                                Side.PLAYER,
                                10,
                                3,
                                1,
                                null,
                                0,
                                List.of(new Effect.Scale(Stat.FIRE_COOLDOWN, Target.CHOOSER, -25)))));
    }
}
