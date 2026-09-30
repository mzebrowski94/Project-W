package pl.mzebrows.pwt.sim.upgrade;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * One upgrade: who may take it, how often it is dealt, how many times it can be taken, and what each
 * rank does. Numbers only - an upgrade never adds or removes what an entity is made of (ADR 0017).
 *
 * @param side the players' upgrade or the aliens' adaptation
 * @param weight how likely it is to be dealt against the others; zero switches it off
 * @param ranks how many times it can be taken
 * @param tier which tier it belongs to, from one; a tier is dealt once enough drafts have been earned
 * @param requires an upgrade that must be held before this one is dealt, or none
 * @param group upgrades sharing a group above zero exclude one another; zero is no group
 * @param effects what one rank does
 */
public record UpgradeDef(
        Side side,
        int weight,
        int ranks,
        int tier,
        @Nullable UpgradeKind requires,
        int group,
        List<Effect> effects) {

    public UpgradeDef {
        if (side == Side.NEUTRAL) {
            throw new IllegalArgumentException("an upgrade belongs to the players or the aliens, not to nobody");
        }
        Require.notNegative("upgrade weight", weight);
        Require.atLeastOne("upgrade ranks", ranks);
        Require.atLeastOne("upgrade tier", tier);
        Require.notNegative("upgrade group", group);
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("an upgrade with no effects is a card that lies");
        }
        boolean personal = effects.getFirst().target() == Target.CHOOSER;
        for (Effect effect : effects) {
            if (effect.target().side() != side) {
                throw new IllegalArgumentException(
                        "a " + side + " upgrade cannot reach " + effect.target() + ", which is the other side's");
            }
            if ((effect.target() == Target.CHOOSER) != personal) {
                throw new IllegalArgumentException(
                        "an upgrade is personal or shared, never both: every effect reaches the chooser or none does");
            }
        }
        effects = List.copyOf(effects);
    }

    /** Whether its ranks belong to the slot that took it rather than to the side (D1). */
    public boolean personal() {
        return effects.getFirst().target() == Target.CHOOSER;
    }

    /**
     * What can only be checked of the whole catalogue: a requirement that goes round in a circle or
     * crosses sides, a group of one, a tier nobody unlocks, a stat block whose unused keys are set, and
     * an armoury deck a deal cannot add to or a bag cannot hold.
     *
     * @throws IllegalArgumentException naming the first of those it finds
     */
    public static void checkCatalogue(
            DraftDef draft,
            Defs<Stat, StatDef> stats,
            Defs<UpgradeKind, UpgradeDef> upgrades,
            Defs<PickupKind, PickupDef> pickups) {
        UpgradeKind[] kinds = UpgradeKind.values();
        for (UpgradeKind kind : kinds) {
            UpgradeDef def = upgrades.of(kind);
            if (def.tier() > draft.tierFrom().size()) {
                throw new IllegalArgumentException(
                        kind + " is tier " + def.tier() + ", which draft tierFrom never unlocks");
            }
            UpgradeKind needed = def.requires();
            for (int step = 0; needed != null; step++) {
                if (needed == kind || step > kinds.length) {
                    throw new IllegalArgumentException(kind + " requires itself, round a circle");
                }
                if (upgrades.of(needed).side() != def.side()) {
                    throw new IllegalArgumentException(kind + " requires " + needed + ", which is the other side's");
                }
                needed = upgrades.of(needed).requires();
            }
            if (def.group() > 0) {
                int members = 0;
                for (UpgradeKind other : kinds) {
                    if (upgrades.of(other).group() == def.group()) {
                        members++;
                    }
                }
                if (members < 2) {
                    throw new IllegalArgumentException(
                            kind + " is alone in group " + def.group() + ", which excludes nothing");
                }
            }
        }
        checkDeck(upgrades, pickups);
        for (Stat stat : Stat.values()) {
            StatDef def = stats.of(stat);
            boolean scale = stat.form() == Stat.Form.SCALE;
            boolean count = stat.form() == Stat.Form.COUNT;
            if ((!scale && (def.minPercent() != 0 || def.maxPercent() != 0)) || (!count && def.min() != 0)) {
                throw new IllegalArgumentException(
                        stat + " is moved by " + stat.form() + ", so its other keys must be zero, or they lie");
            }
        }
    }

    /**
     * The armoury deck (G0056): a deal or a stock names a card the deck already holds, a level with crates
     * has a deck to deal them from, and the deck with every deal at its highest rank still fits a bag.
     */
    private static void checkDeck(Defs<UpgradeKind, UpgradeDef> upgrades, Defs<PickupKind, PickupDef> pickups) {
        int cards = 0;
        boolean crates = false;
        for (PickupDef pickup : pickups.values()) {
            cards += pickup.armoury().cards();
            crates |= pickup.armoury().crate();
        }
        if (crates && cards == 0) {
            throw new IllegalArgumentException("there are crates, but the armoury deck has no card to fill them");
        }
        for (UpgradeKind kind : UpgradeKind.values()) {
            UpgradeDef def = upgrades.of(kind);
            for (Effect effect : def.effects()) {
                if (effect instanceof Effect.Deal deal) {
                    if (!pickups.of(deal.card()).armoury().inDeck()) {
                        throw new IllegalArgumentException(
                                kind + " deals " + deal.card() + ", which is not a card in the armoury deck");
                    }
                    cards += deal.amount() * def.ranks();
                }
                if (effect instanceof Effect.Stock stock
                        && !pickups.of(stock.card()).armoury().inDeck()) {
                    throw new IllegalArgumentException(
                            kind + " stocks crates with " + stock.card() + ", which is not a card in the armoury deck");
                }
            }
        }
        if (cards > Bag.MOST) {
            throw new IllegalArgumentException("the armoury deck holds " + cards
                    + " cards with every deal at its highest rank, and a bag deals at most " + Bag.MOST);
        }
    }
}
