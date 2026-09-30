package pl.mzebrows.pwt.sim.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * What the interface may read of the upgrades (ADR 0017): the drafts waiting for each slot and their
 * cards, and what each side holds. Read-only, and allocation-free, so it can be asked every frame; the ledger
 * and the drafts themselves stay the package's.
 */
public final class UpgradeView {

    private static final ComponentMapper<Upgrades> UPGRADES = ComponentMapper.getFor(Upgrades.class);
    private static final ComponentMapper<Drafts> DRAFTS = ComponentMapper.getFor(Drafts.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);

    /** The slot a shared upgrade is held by: the side's, not any one player's. */
    public static final int SHARED = Upgrades.SHARED;

    private final ImmutableArray<Entity> levels;
    private final Defs<UpgradeKind, UpgradeDef> catalogue;

    public UpgradeView(Engine engine, SimConfig config) {
        this.levels =
                engine.getEntitiesFor(Family.all(Upgrades.class, Drafts.class).get());
        this.catalogue = config.upgrades();
    }

    /** Moves whenever a draft or the ledger changes, so whoever shows them rebuilds only then. */
    public long revision() {
        if (levels.size() == 0) {
            return 0L;
        }
        Entity level = levels.first();
        return ((long) DRAFTS.get(level).revision << 32) | (UPGRADES.get(level).revision & 0xFFFF_FFFFL);
    }

    /**
     * Whether this slot's link has dropped and nobody has taken it since (G0052): its commander stands idle
     * and the draft rotation passes it by. Offline never.
     */
    public boolean away(int slot) {
        if (levels.size() == 0) {
            return false;
        }
        Entity level = levels.first();
        return PRESENCE.has(level) && PRESENCE.get(level).isAway(slot);
    }

    /** How many drafts this slot is holding. */
    public int draftsOf(int slot) {
        if (levels.size() == 0) {
            return 0;
        }
        Drafts drafts = DRAFTS.get(levels.first());
        int count = 0;
        for (int i = 0; i < drafts.pending.size; i++) {
            if (drafts.pending.get(i).chooser() == slot) {
                count++;
            }
        }
        return count;
    }

    /** The serial of the oldest draft this slot holds, the one it opens first; zero if it holds none. */
    public int oldestDraftOf(int slot) {
        if (levels.size() == 0) {
            return 0;
        }
        Drafts drafts = DRAFTS.get(levels.first());
        for (int i = 0; i < drafts.pending.size; i++) {
            if (drafts.pending.get(i).chooser() == slot) {
                return drafts.pending.get(i).serial();
            }
        }
        return 0;
    }

    /** How many cards that draft has. */
    public int cardsOn(int serial) {
        if (levels.size() == 0) {
            return 0;
        }
        Drafts drafts = DRAFTS.get(levels.first());
        int count = 0;
        for (int i = 0; i < drafts.cards.size; i++) {
            if (drafts.cards.get(i).serial() == serial) {
                count++;
            }
        }
        return count;
    }

    /** The card at this place on that draft, in deal order, or null if there is none. */
    public @Nullable UpgradeKind card(int serial, int position) {
        if (levels.size() == 0) {
            return null;
        }
        Drafts drafts = DRAFTS.get(levels.first());
        for (int i = 0; i < drafts.cards.size; i++) {
            Drafts.Card card = drafts.cards.get(i);
            if (card.serial() == serial && card.position() == position) {
                return card.kind();
            }
        }
        return null;
    }

    /**
     * The {@code index}-th card still on that draft, in deal order, or null past the last. Unlike {@link
     * #card}, it skips the place of a card a pick made stale and nothing could replace.
     */
    public @Nullable UpgradeKind cardOf(int serial, int index) {
        if (levels.size() == 0) {
            return null;
        }
        Drafts drafts = DRAFTS.get(levels.first());
        int seen = 0;
        for (int i = 0; i < drafts.cards.size; i++) {
            Drafts.Card card = drafts.cards.get(i);
            if (card.serial() == serial && seen++ == index) {
                return card.kind();
            }
        }
        return null;
    }

    /** How many drafts a source was owed that the pool could not deal: nothing left to learn. */
    public int dry() {
        return levels.size() == 0 ? 0 : DRAFTS.get(levels.first()).dry;
    }

    /** What taking this kind does, as its definition says - a card's text is generated from these. */
    public List<Effect> effects(UpgradeKind kind) {
        return catalogue.of(kind).effects();
    }

    /** How many ranks this kind has at most. */
    public int ranksOf(UpgradeKind kind) {
        return catalogue.of(kind).ranks();
    }

    /** The rank this kind would have for this slot once taken: the side's, or his own if it is personal. */
    public int rankOnceTaken(UpgradeKind kind, int slot) {
        int holder = catalogue.of(kind).personal() ? slot : Upgrades.SHARED;
        return ranks(kind, holder) + 1;
    }

    /** How many entries the ledger holds. */
    public int heldCount() {
        return levels.size() == 0 ? 0 : UPGRADES.get(levels.first()).held.size;
    }

    /** What the held entry at this index is, in the order entries were first taken. */
    public UpgradeKind heldKind(int index) {
        return held(index).kind();
    }

    /** Whose it is: a slot for a personal upgrade, {@link #SHARED} for the side's. */
    public int heldSlot(int index) {
        return held(index).slot();
    }

    /** How many times it has been taken. */
    public int heldRanks(int index) {
        return held(index).ranks();
    }

    /** Whether it is the swarm's adaptation (D11) rather than the players' upgrade. */
    public boolean heldAlien(int index) {
        return catalogue.of(held(index).kind()).side() == Side.ALIEN;
    }

    private Upgrades.Held held(int index) {
        return UPGRADES.get(levels.first()).held.get(index);
    }

    private int ranks(UpgradeKind kind, int slot) {
        for (int i = 0; i < heldCount(); i++) {
            Upgrades.Held entry = held(i);
            if (entry.kind() == kind && entry.slot() == slot) {
                return entry.ranks();
            }
        }
        return 0;
    }
}
