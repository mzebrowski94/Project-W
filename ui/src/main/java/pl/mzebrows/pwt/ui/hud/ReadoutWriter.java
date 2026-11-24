package pl.mzebrows.pwt.ui.hud;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.combat.Grants;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeView;

/**
 * The one place the interface reads the simulation. Once a frame, after however many ticks ran, it
 * copies the facts the bars show into a {@link Readout} - and it only reads: nothing here writes a
 * component or steps the world (ADR 0015, {@code UiArchitectureTest}).
 *
 * <p>Families are cached per world, because building one allocates. A new world - a restart, a heal, the
 * first world online - is noticed by identity and moves {@link Readout#worldSerial} on, which is how
 * everything downstream knows to start again.
 */
public final class ReadoutWriter {

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Respawning> RESPAWNING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);
    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);

    /** Once: {@code values()} hands out a new array on every call, and this runs every frame. */
    private static final Objective[] OBJECTIVES = Objective.values();

    private static final Family COMMANDERS =
            Family.all(Commander.class, Appearance.class).get();
    private static final Family RESPAWNS = Family.all(Respawning.class).get();
    private static final Family SITES = Family.all(Site.class).get();
    private static final Family NESTS = Family.all(Nest.class).get();
    private static final Family TROOPERS = Family.all(Trooper.class).get();
    private static final Family CRATES = Family.all(Crate.class).get();
    private static final Family TURRETS =
            Family.all(Turret.class, Appearance.class).get();

    /**
     * Each slot's tally as it last stood, so a player waiting to respawn still sees the life that just
     * ended until the new one starts (plan 7.2). Forgotten with the world.
     */
    private final int[][] lastLife = new int[Players.MAX][3];

    private @Nullable Simulation bound;
    private long serial;
    private ImmutableArray<Entity> commanders = empty();
    private ImmutableArray<Entity> respawns = empty();
    private ImmutableArray<Entity> sites = empty();
    private ImmutableArray<Entity> nests = empty();
    private ImmutableArray<Entity> troopers = empty();
    private ImmutableArray<Entity> turrets = empty();
    private ImmutableArray<Entity> crates = empty();

    /**
     * Copies what the world says now into {@code out}.
     *
     * @param mission the scenario's name, which the world does not know
     * @param planet where it is, or empty
     */
    public void fill(Simulation world, String mission, String planet, Readout out) {
        if (bound != world) {
            bind(world);
        }
        out.clear();
        out.worldSerial = serial;
        out.mission = mission;
        out.planet = planet;

        Level level = world.level();
        for (Objective objective : OBJECTIVES) {
            if (level.requires(objective)) {
                out.objectivesRequired |= Readout.bit(objective);
            }
            if (level.isMet(objective)) {
                out.objectivesMet |= Readout.bit(objective);
            }
        }
        Waves waves = world.waves();
        out.wavesSent = waves.sent();
        out.wavesTotal = waves.total();
        out.wavesArmed = waves.armed();
        // Every site, whatever its kind (G0110): the goal line's points are its sites.
        for (int i = 0; i < sites.size(); i++) {
            out.pointsTotal++;
            if (SITE.get(sites.get(i)).taken) {
                out.pointsTaken++;
            }
        }
        out.nestsStanding = standing(nests);
        out.troopersStanding = standing(troopers);
        army(world, level, out);

        for (int i = 0; i < commanders.size(); i++) {
            commander(world, commanders.get(i), out);
        }
        crates(world, out);
        upgrades(world.upgradeView(), out);
        for (int i = 0; i < respawns.size(); i++) {
            Respawning respawning = RESPAWNING.get(respawns.get(i));
            if (!inRange(respawning.slot)) {
                continue;
            }
            PlayerReadout player = out.player(respawning.slot);
            if (player.posture == Posture.ABSENT) {
                player.posture = Posture.RESPAWNING;
                player.respawnSeconds =
                        Math.max(0, respawning.remainingTicks) * world.config().tickSeconds();
                player.respawnWaiting = !respawning.paid;
                player.respawnRecalled = respawning.recalled;
                player.variant = respawning.slot;
                int[] last = lastLife[respawning.slot];
                player.kills = last[0];
                player.goals = last[1];
                player.stabilised = last[2];
            }
        }
    }

    /** The men on the field against its most, and the reserve (G0055). */
    private void army(Simulation world, Level level, Readout out) {
        out.squadOnField = world.menOnField();
        out.squadMost = world.squadMost();
        out.reserve = level.reserve;
        out.failed = level.failed;
        out.won = level.won;
        out.outcomeTick = level.outcomeTick;
        out.tickSeconds = world.config().tickSeconds();
        out.sinceOutcomeSeconds = level.decided()
                ? (level.tick - level.outcomeTick) * world.config().tickSeconds()
                : 0f;
        Lair lair = world.lair();
        out.regroupPresent = lair.waiting() ? lair.present() : 0;
        out.regroupNeeded = lair.waiting() ? lair.needed() : 0;
    }

    private void commander(Simulation world, Entity entity, Readout out) {
        int slot = COMMANDER.get(entity).slot;
        if (!inRange(slot)) {
            return;
        }
        PlayerReadout player = out.player(slot);
        Appearance appearance = APPEARANCE.get(entity);
        player.variant = appearance.variant;
        player.weapon = WEAPON.has(entity) ? WEAPON.get(entity).kind : WeaponKind.SIDEARM;
        player.heat = appearance.heat;
        player.overheated = appearance.overheated;
        player.detonating = appearance.detonating;
        player.self = Handles.of(entity);
        if (ACTION.has(entity)) {
            Action action = ACTION.get(entity);
            player.action = action.available;
            player.subject = action.subject;
            player.progress = action.progress;
        }
        player.pinned = Drivers.gripped(entity);
        if (TALLY.has(entity)) {
            Tally tally = TALLY.get(entity);
            player.kills = tally.kills;
            player.goals = tally.goals;
            player.stabilised = tally.stabilised;
            int[] last = lastLife[slot];
            last[0] = tally.kills;
            last[1] = tally.goals;
            last[2] = tally.stabilised;
        }
        player.posture = switch (appearance.state) {
            case CASUALTY -> Posture.CASUALTY;
            case DYING -> Posture.DYING;
            case ABOARD -> Posture.ABOARD;
            default -> Posture.STANDING;
        };
        if (player.posture == Posture.CASUALTY && VITALITY.has(entity)) {
            player.bleedOut = VITALITY.get(entity).fraction();
            // Whether he may redeploy now (G0109), as the simulation published it - never worked out here.
            player.redeployOpen = appearance.redeployOpen;
        }
        if (VITALITY.has(entity)) {
            // A pip per wound he carries and per one he could (G0053); the appearance says which count.
            player.wounds = appearance.wounds;
            player.maxWounds = world.maxWounds(entity);
        }
        // A pip per grenade he carries and per one he could (G0077), through the throw's own fold.
        player.grenades = world.grenades(entity);
        player.grenadeMost = world.grenadeMost(entity);
        // Under Engineer the same pips are sentries (G0129), through the throw's own fold.
        player.sentries = world.setsSentries(entity);
        // A pip per dash he holds and per one he could (G0107), through the dash's own fold.
        player.dashes = world.dashes(entity);
        player.dashMost = world.dashMost(entity);
        player.shielded = appearance.shield > 0f;
        player.invulnerable =
                INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active();
        // A declared stun only, never a stagger (G0053), and read from its own fraction rather than the state:
        // a leech's cling outranks the stun there and holds nothing still.
        player.stunLeft = appearance.daze;
        player.stunned = appearance.daze > 0f;
        if (player.posture == Posture.ABOARD && CREW.has(entity)) {
            aboard(CREW.get(entity).station, player);
        }
    }

    /**
     * Each slot's drafts and the cards of his oldest, and what the side holds (G0049) - through {@link
     * UpgradeView}, never the ledger itself (ADR 0017).
     */
    private static void upgrades(UpgradeView view, Readout out) {
        out.upgradeRevision = view.revision();
        out.draftsDry = view.dry();
        for (int slot = 0; slot < Players.MAX; slot++) {
            PlayerReadout player = out.player(slot);
            player.away = view.away(slot);
            player.drafts = view.draftsOf(slot);
            int serial = view.oldestDraftOf(slot);
            player.draftSerial = serial;
            int count = 0;
            for (UpgradeKind kind = serial == 0 ? null : view.cardOf(serial, 0);
                    kind != null && count < PlayerReadout.CARDS;
                    kind = view.cardOf(serial, count)) {
                player.cards[count] = kind;
                player.cardRanks[count] = view.rankOnceTaken(kind, slot);
                count++;
            }
            player.cardCount = count;
        }
        int held = Math.min(view.heldCount(), Readout.HELD);
        for (int i = 0; i < held; i++) {
            UpgradeKind kind = view.heldKind(i);
            out.heldKind[i] = kind;
            out.heldSlot[i] = view.heldSlot(i);
            out.heldRanks[i] = view.heldRanks(i);
            out.heldMaxRanks[i] = view.ranksOf(kind);
            out.heldAlien[i] = view.heldAlien(i);
        }
        out.heldCount = held;
    }

    /** Heat belongs to the gun (G0025): aboard, the panel shows the turret's gun and its heat. */
    private void aboard(long station, PlayerReadout player) {
        for (int i = 0; i < turrets.size(); i++) {
            Entity turret = turrets.get(i);
            if (Handles.of(turret) == station) {
                Appearance appearance = APPEARANCE.get(turret);
                player.weapon = WEAPON.has(turret) ? WEAPON.get(turret).kind : WeaponKind.TURRET_GUN;
                player.heat = appearance.heat;
                player.overheated = appearance.overheated;
                return;
            }
        }
    }

    /**
     * Every open crate's cards, on its opener (G0056), and which of them he would be turned down for -
     * asked of the grant and of him in the one place the simulation asks it.
     */
    private void crates(Simulation world, Readout out) {
        for (int i = 0; i < crates.size(); i++) {
            Crate crate = CRATE.get(crates.get(i));
            if (!crate.open() || !crate.dealt || !inRange(crate.openedBy)) {
                continue;
            }
            @Nullable Entity opener = Players.inSlot(commanders, crate.openedBy);
            PlayerReadout player = out.player(crate.openedBy);
            player.crateCount = Math.min(crate.count, PlayerReadout.CRATE_CARDS);
            for (int c = 0; c < player.crateCount; c++) {
                PickupKind kind = crate.get(c);
                player.crate[c] = kind;
                Grant grant = world.config().pickup(kind).grantFor(Side.PLAYER);
                player.crateRefused[c] = opener == null || !Grant.isSomething(grant) || !Grants.worthTo(grant, opener);
            }
        }
    }

    private void bind(Simulation world) {
        bound = world;
        for (int[] last : lastLife) {
            Arrays.fill(last, 0);
        }
        serial++;
        commanders = world.engine().getEntitiesFor(COMMANDERS);
        respawns = world.engine().getEntitiesFor(RESPAWNS);
        sites = world.engine().getEntitiesFor(SITES);
        nests = world.engine().getEntitiesFor(NESTS);
        troopers = world.engine().getEntitiesFor(TROOPERS);
        turrets = world.engine().getEntitiesFor(TURRETS);
        crates = world.engine().getEntitiesFor(CRATES);
    }

    private static int standing(ImmutableArray<Entity> entities) {
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!Life.dying(entity)) {
                count++;
            }
        }
        return count;
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < Players.MAX;
    }

    private static ImmutableArray<Entity> empty() {
        return new ImmutableArray<>(new com.badlogic.gdx.utils.Array<>());
    }
}
