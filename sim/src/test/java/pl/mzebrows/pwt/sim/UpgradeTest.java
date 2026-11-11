package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARMY_HEADROOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARMY_MEN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADIER_PERCENT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MARCH_PERCENT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MARKSMEN_PERCENT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MEDICINE_PER_SECOND;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SELF_TREATMENT_SECONDS;

import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * A number is folded where it is read and never written back (ADR 0017), so a unit spawned after an
 * upgrade is identical to one that was on the field when it landed - by construction, with no hook.
 */
class UpgradeTest {

    private static final float MIDDLE = 50f;
    private static final Offset<Float> CLOSE = Offset.offset(1e-5f);

    /** The same number whichever of the two a test asks about, and not the number before the pick. */
    @Test
    void readsTheSameCooldownForATrooperOnTheFieldAndOneWhoArrivesAfterwards() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity before = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());
        int base = before.getComponent(Weapon.class).cooldownTicks;

        take(simulation, UpgradeKind.WEAPON_HANDLING);
        Entity after = simulation.spawnTrooper(MIDDLE - 15f, MIDDLE);

        int onTheField = modifiers.fireCooldownTicks(before, before.getComponent(Weapon.class).kind, base);
        int arrived = modifiers.fireCooldownTicks(
                after, after.getComponent(Weapon.class).kind, after.getComponent(Weapon.class).cooldownTicks);
        assertThat(onTheField).isEqualTo(arrived).isLessThan(base);
    }

    /** Through the one seam every shooter goes through: the commander's next pull is charged the shorter wait. */
    @Test
    void chargesAPullTheShorterWaitFromTheTickAfterThePick() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        int base = commander.getComponent(Weapon.class).cooldownTicks;

        take(simulation, UpgradeKind.WEAPON_HANDLING);
        simulation.step(new CommanderInput(0f, 0f, MIDDLE + 20f, MIDDLE, true, false));

        int charged = commander.getComponent(Weapon.class).remainingTicks;
        assertThat(charged).isLessThan(base);
    }

    @Test
    void landsMoreMenPerCrateAndMakesRoomForThem() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        take(simulation, UpgradeKind.LARGER_ARMY);

        assertThat(modifiers.menPerCrate(3)).isEqualTo(3 + ARMY_MEN);
        assertThat(modifiers.squadHeadroom(4)).isEqualTo(4 + ARMY_HEADROOM);
    }

    /** Whole percentages, summed: the order they were taken in cannot matter. */
    @Test
    void foldsTheSameWhateverOrderTheLedgerHoldsThingsIn() {
        Simulation first = new Simulation(AlphaWorlds.config());
        Simulation second = new Simulation(AlphaWorlds.config());
        Entity a = first.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity b = second.spawnCommander(MIDDLE, MIDDLE, 0);

        hold(
                first,
                new Upgrades.Held(UpgradeKind.WEAPON_HANDLING, Upgrades.SHARED, 2),
                new Upgrades.Held(UpgradeKind.LARGER_ARMY, Upgrades.SHARED, 1));
        hold(
                second,
                new Upgrades.Held(UpgradeKind.LARGER_ARMY, Upgrades.SHARED, 1),
                new Upgrades.Held(UpgradeKind.WEAPON_HANDLING, Upgrades.SHARED, 2));

        assertThat(cooldown(first, a, 37)).isEqualTo(cooldown(second, b, 37)).isLessThan(37);
    }

    /** A gun on a mount is the place's (G0024): nothing the squad or the commanders hold reaches it. */
    @Test
    void reachesNoTurret() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + 20f, MIDDLE);

        hold(simulation, new Upgrades.Held(UpgradeKind.WEAPON_HANDLING, Upgrades.SHARED, 3));

        assertThat(cooldown(simulation, turret, 40)).isEqualTo(40);
    }

    /** Marksmen reaches the squad's shots and not the commander's. */
    @Test
    void makesTheSquadsShotsHitHarderAndNotTheCommanders() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        hold(simulation, new Upgrades.Held(UpgradeKind.MARKSMEN, Upgrades.SHARED, 2));

        assertThat(modifiers.gunDamage(trooper, WeaponKind.SIDEARM, 10))
                .isEqualTo(Whole.percentOf(10, 100 + 2 * MARKSMEN_PERCENT));
        assertThat(modifiers.gunDamage(commander, WeaponKind.SIDEARM, 10)).isEqualTo(10);
    }

    /** Field Medicine makes a trooper readier to fetch a man, the commander above all. */
    @Test
    void makesATrooperReadierToGoForAManWhoIsDown() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        hold(simulation, new Upgrades.Held(UpgradeKind.FIELD_MEDICINE, Upgrades.SHARED, 2));

        assertThat(modifiers.chance(trooper, AiChance.AID_A_TROOPER, 0.4f))
                .isCloseTo(0.4f + 2 * MEDICINE_PER_SECOND, CLOSE);
        assertThat(modifiers.chance(trooper, AiChance.SWING_AT_A_LEAP, 0.5f)).isEqualTo(0.5f);
    }

    /** Grenadier is personal: the commander of the slot that took it throws sooner, and nobody else does. */
    @Test
    void shortensTheWaitBetweenGrenadesForTheChoosersCommanderAlone() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity chooser = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity other = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        hold(simulation, new Upgrades.Held(UpgradeKind.GRENADIER, 0, 1));

        assertThat(modifiers.grenadeCooldownTicks(chooser, 100)).isEqualTo(100 + GRENADIER_PERCENT);
        assertThat(modifiers.grenadeCooldownTicks(other, 100)).isEqualTo(100);
    }

    /**
     * A read remembers nothing past the ledger it was folded from: the same port, asked before a pick and after,
     * answers for what is held each time - through the real pick, which is what moves the ledger in play.
     */
    @Test
    void foldsAfreshOnceTheLedgerChangesBetweenTwoReads() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());
        int before = modifiers.fireCooldownTicks(trooper, WeaponKind.SIDEARM, 100);

        take(simulation, UpgradeKind.WEAPON_HANDLING);

        assertThat(before).isEqualTo(100);
        assertThat(modifiers.fireCooldownTicks(trooper, WeaponKind.SIDEARM, 100))
                .isLessThan(100);
    }

    /** One port reading two commanders in turn, and the first again: a personal upgrade stays his slot's. */
    @Test
    void tellsTheChooserFromTheOtherCommanderWhicheverIsReadFirst() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity other = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        Entity chooser = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());
        hold(simulation, new Upgrades.Held(UpgradeKind.GRENADIER, 0, 1));

        int first = modifiers.grenadeCooldownTicks(other, 100);
        int chosen = modifiers.grenadeCooldownTicks(chooser, 100);

        assertThat(first).isEqualTo(modifiers.grenadeCooldownTicks(other, 100)).isEqualTo(100);
        assertThat(chosen).isEqualTo(100 + GRENADIER_PERCENT);
    }

    /** Forced March speeds the squad up, and nothing else DriveSystem moves. */
    @Test
    void drivesTheSquadFasterAndNoAlien() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Entity grunt = simulation.spawnEnemy(MIDDLE + 30f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        hold(simulation, new Upgrades.Held(UpgradeKind.FORCED_MARCH, Upgrades.SHARED, 1));

        assertThat(modifiers.pace(trooper, 1f)).isCloseTo(1f + MARCH_PERCENT / 100f, CLOSE);
        assertThat(modifiers.pace(grunt, 1f)).isEqualTo(1f);
    }

    /** First Aid makes a tick of work on a casualty worth more, whoever is doing it. */
    @Test
    void makesATickOfWorkOnACasualtyCountForMore() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        hold(simulation, new Upgrades.Held(UpgradeKind.FIRST_AID, Upgrades.SHARED, 1));

        assertThat(modifiers.stabiliseWork(commander, Vitality.TICK)).isEqualTo(125);
        assertThat(modifiers.stabiliseWork(trooper, Vitality.TICK)).isEqualTo(125);
    }

    /** Thick Skin: one more wound on his feet per rank, for his men and every commander (G0053). */
    @Test
    void carriesAnotherWoundOnHisFeetWithThickSkin() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.THICK_SKIN, Upgrades.SHARED, 1));

        wound(simulation, trooper);
        wound(simulation, trooper);

        assertThat(trooper.getComponent(Vitality.class).wounds).isEqualTo(2);
        assertThat(trooper.getComponent(Vitality.class).begun).isFalse();
    }

    /** Self-Treatment: the chooser's wounds clear on their own after a spell with no new one - his alone. */
    @Test
    void mendsTheChoosersWoundsOnTheirOwnAndNobodyElses() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.SELF_TREATMENT, 0, 1));
        wound(simulation, commander);
        wound(simulation, trooper);

        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(SELF_TREATMENT_SECONDS) + 2);

        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
        assertThat(trooper.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** The clock is from his LAST wound, so it rewards getting out of the fight rather than standing in it. */
    @Test
    void startsTheMendAgainWithEveryNewWound() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        hold(simulation, new Upgrades.Held(UpgradeKind.SELF_TREATMENT, 0, 1));
        hold(simulation, new Upgrades.Held(UpgradeKind.THICK_SKIN, Upgrades.SHARED, 1));
        wound(simulation, commander);
        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(SELF_TREATMENT_SECONDS * 0.6f));

        wound(simulation, commander);
        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(SELF_TREATMENT_SECONDS * 0.6f));

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(2);
    }

    @Test
    void mendsNothingWithoutIt() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        wound(simulation, commander);

        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(SELF_TREATMENT_SECONDS * 5f));

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    private static void wound(Simulation simulation, Entity man) {
        man.getComponent(Damage.class).land(0, Harm.WOUND);
        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(AlphaWorlds.WOUND_GRACE) + 2);
    }

    /**
     * Firepower stocks every crate (G0056): with one rank every crate opened after the pick holds a flamer,
     * whatever the deck dealt - read through the one port.
     */
    @Test
    void stocksEveryCrateWithAFlamerUnderFirepower() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.FIREPOWER, Upgrades.SHARED, 1));

        for (int crate = 0; crate < 4; crate++) {
            assertThat(open(simulation, crate)).contains(PickupKind.ARMOURY_FLAMER);
        }
    }

    /** Full Crates fills every crate opened after the pick to the most a crate holds (G0056). */
    @Test
    void fillsEveryCrateUnderFullCrates() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.FULL_CRATES, Upgrades.SHARED, 1));

        for (int crate = 0; crate < 3; crate++) {
            assertThat(open(simulation, crate)).hasSize(SimConfig.CrateConfig.CAPACITY);
        }
    }

    /**
     * A deal stacks the deck the crates are dealt from, and a deck that grows mid-shuffle is shuffled
     * afresh: one crate out of the plain deck, then two more flamers land, and the next seven cards are the
     * whole stacked deck - three flamers, whatever the first crate held.
     */
    @Test
    void shufflesTheArmouryDeckAfreshWhenADealStacksIt() {
        SimConfig config = AlphaWorlds.config();
        config = config.withUpgrades(config.upgrades()
                .map(
                        UpgradeKind.FIREPOWER,
                        firepower -> new UpgradeDef(
                                Side.PLAYER,
                                0,
                                2,
                                1,
                                null,
                                0,
                                List.of(new Effect.Deal(PickupKind.ARMOURY_FLAMER, 2)))));
        Simulation simulation = new Simulation(config);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        open(simulation, 0);
        hold(simulation, new Upgrades.Held(UpgradeKind.FIREPOWER, Upgrades.SHARED, 1));

        List<PickupKind> dealt = new ArrayList<>();
        for (int crate = 1; dealt.size() < 7; crate++) {
            dealt.addAll(open(simulation, crate));
        }

        assertThat(dealt.subList(0, 7))
                .filteredOn(kind -> kind == PickupKind.ARMOURY_FLAMER)
                .hasSize(AlphaWorlds.ARMOURY_CARDS + 2);
    }

    /** Its damage is the flamer's alone, and reaches whoever holds one - a trooper who took a crate too. */
    @Test
    void hitsHarderWithAFlamerAndWithNoOtherGunUnderFirepower() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE - 10f, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.FIREPOWER, Upgrades.SHARED, 2));
        Modifiers modifiers = new Modifiers(simulation.engine(), simulation.config());

        assertThat(modifiers.gunDamage(trooper, WeaponKind.FLAMER, 100))
                .isEqualTo(100 + 2 * AlphaWorlds.FIREPOWER_PERCENT);
        assertThat(modifiers.gunDamage(trooper, WeaponKind.RAILGUN, 100)).isEqualTo(100);
    }

    /**
     * Lays a crate beside the commander, opens it, says what it was dealt, and shuts it and puts it out of
     * reach so the next can be opened. {@code nth} keeps the crates apart.
     */
    private static List<PickupKind> open(Simulation simulation, int nth) {
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + 2f, MIDDLE);
        simulation.step();
        simulation.step(CommanderInput.IDLE.acting(true));
        Crate held = crate.getComponent(Crate.class);
        List<PickupKind> dealt = new ArrayList<>();
        for (int i = 0; i < held.count; i++) {
            dealt.add(held.get(i));
        }
        simulation.step(
                new PlayerInputs().set(0, CommanderInput.IDLE.acting(true)).closeCrate(0));
        crate.getComponent(Position.class).set(MIDDLE + 10f * (nth + 1), MIDDLE + 30f);
        return dealt;
    }

    /** Takes one rank through the real path: a point taken earns a draft, and a pick takes the card. */
    private static void take(Simulation simulation, UpgradeKind kind) {
        simulation.spawnControlPoint(10f, 10f).getComponent(ControlPoint.class).captured = true;
        simulation.step();
        int serial = DraftTest.drafts(simulation).pending.first().serial();
        simulation.step(new PlayerInputs().pick(0, serial, kind));
    }

    private static void hold(Simulation simulation, Upgrades.Held... held) {
        for (Upgrades.Held entry : held) {
            DraftTest.ledger(simulation).held.add(entry);
        }
    }

    private static int cooldown(Simulation simulation, Entity who, int base) {
        return new Modifiers(simulation.engine(), simulation.config()).fireCooldownTicks(who, WeaponKind.SIDEARM, base);
    }
}
