package pl.mzebrows.pwt.ui.hud;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeView;

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
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Respawning> RESPAWNING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);

    /** Once: {@code values()} hands out a new array on every call, and this runs every frame. */
    private static final Objective[] OBJECTIVES = Objective.values();

    private static final Family COMMANDERS =
            Family.all(Commander.class, Appearance.class).get();
    private static final Family RESPAWNS = Family.all(Respawning.class).get();
    private static final Family POINTS = Family.all(ControlPoint.class).get();
    private static final Family NESTS = Family.all(Nest.class).get();
    private static final Family TROOPERS = Family.all(Trooper.class).get();
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
    private ImmutableArray<Entity> points = empty();
    private ImmutableArray<Entity> nests = empty();
    private ImmutableArray<Entity> troopers = empty();
    private ImmutableArray<Entity> turrets = empty();

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
        for (int i = 0; i < points.size(); i++) {
            out.pointsTotal++;
            if (POINT.get(points.get(i)).captured) {
                out.pointsTaken++;
            }
        }
        out.nestsStanding = standing(nests);
        out.troopersStanding = standing(troopers);

        for (int i = 0; i < commanders.size(); i++) {
            commander(commanders.get(i), out);
        }
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
                player.variant = respawning.slot;
                int[] last = lastLife[respawning.slot];
                player.kills = last[0];
                player.goals = last[1];
                player.stabilised = last[2];
            }
        }
    }

    private void commander(Entity entity, Readout out) {
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
        player.action = ACTION.has(entity) ? ACTION.get(entity).available : ActionKind.NONE;
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
        }
        if (VITALITY.has(entity)) {
            // A pip per wound he carries and per one he could (G0053); the appearance says which count.
            player.wounds = appearance.wounds;
            player.maxWounds = VITALITY.get(entity).carries;
        }
        player.shielded = appearance.shield > 0f;
        player.stunned = appearance.state == Appearance.State.STUNNED;
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

    private void bind(Simulation world) {
        bound = world;
        for (int[] last : lastLife) {
            Arrays.fill(last, 0);
        }
        serial++;
        commanders = world.engine().getEntitiesFor(COMMANDERS);
        respawns = world.engine().getEntitiesFor(RESPAWNS);
        points = world.engine().getEntitiesFor(POINTS);
        nests = world.engine().getEntitiesFor(NESTS);
        troopers = world.engine().getEntitiesFor(TROOPERS);
        turrets = world.engine().getEntitiesFor(TURRETS);
    }

    private static int standing(ImmutableArray<Entity> entities) {
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!DYING.has(entity) || !DYING.get(entity).begun) {
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
