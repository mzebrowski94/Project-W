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
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

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
        float tick = simulation.config().tickSeconds();

        hold(simulation, new Upgrades.Held(UpgradeKind.FIRST_AID, Upgrades.SHARED, 1));

        assertThat(modifiers.stabiliseWork(commander, tick)).isCloseTo(tick * 1.25f, CLOSE);
        assertThat(modifiers.stabiliseWork(trooper, tick)).isCloseTo(tick * 1.25f, CLOSE);
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
        man.getComponent(Damage.class).add(AlphaWorlds.A_BLOW, 0, Harm.WOUND);
        AlphaWorlds.steps(simulation, AlphaWorlds.ticksIn(AlphaWorlds.WOUND_GRACE) + 2);
    }

    /**
     * Firepower stacks the armoury deck (G0050): with one rank the fixture's deck is one of each gun
     * and three flamers, so six caches revealed together are three flamers - read through the one port.
     */
    @Test
    void dealsMoreFlamersToSealedCachesWithFirepower() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        hold(simulation, new Upgrades.Held(UpgradeKind.FIREPOWER, Upgrades.SHARED, 1));

        List<PickupKind> dealt = reveal(simulation, 6);

        assertThat(dealt)
                .filteredOn(kind -> kind == PickupKind.ARMOURY_FLAMER)
                .hasSize(AlphaWorlds.ARMOURY_CARDS + AlphaWorlds.FIREPOWER_FLAMERS);
    }

    /**
     * A deck that grows mid-shuffle is shuffled afresh: two cards out of the plain deck, then Firepower
     * lands, and the next six are the whole stacked deck - three flamers, whatever the first two were.
     */
    @Test
    void shufflesTheArmouryDeckAfreshWhenFirepowerLands() {
        Simulation simulation = new Simulation(AlphaWorlds.config());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        reveal(simulation, 2);
        hold(simulation, new Upgrades.Held(UpgradeKind.FIREPOWER, Upgrades.SHARED, 1));

        List<PickupKind> dealt = reveal(simulation, 6);

        assertThat(dealt)
                .filteredOn(kind -> kind == PickupKind.ARMOURY_FLAMER)
                .hasSize(AlphaWorlds.ARMOURY_CARDS + AlphaWorlds.FIREPOWER_FLAMERS);
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

    /** Lays this many sealed caches beside the commander, steps once, and says what each was dealt. */
    private static List<PickupKind> reveal(Simulation simulation, int caches) {
        List<Entity> laid = new ArrayList<>();
        for (int i = 0; i < caches; i++) {
            laid.add(simulation.spawnPickup(PickupKind.ARMOURY_SEALED, MIDDLE + i - caches / 2f, MIDDLE + 3f));
        }
        simulation.step();
        List<PickupKind> dealt = new ArrayList<>();
        for (Entity cache : laid) {
            dealt.add(cache.getComponent(Pickup.class).kind);
        }
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
