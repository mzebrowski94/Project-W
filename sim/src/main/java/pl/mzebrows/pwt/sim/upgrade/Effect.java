package pl.mzebrows.pwt.sim.upgrade;

import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * One change an upgrade makes, applied once per rank. A sealed family, as {@code Grant} is, so a new
 * kind of effect fails the build at every switch that has not decided about it (ADR 0017).
 *
 * <p>Written in config as its name and its arguments: {@code scale fire_cooldown team -12},
 * {@code count men_per_crate side 1}, {@code chance aid_a_trooper squad 0.2},
 * {@code gun flamer gun_damage team 25}, {@code deal armoury_flamer 2}, {@code stock armoury_flamer 1},
 * {@code breed spitter fire_cooldown -15}.
 */
public sealed interface Effect {

    /** Whom it reaches. */
    Target target();

    /** A stat moved by a whole percentage of its base, for every gun if the stat is a gun's. */
    record Scale(Stat stat, Target target, int percent) implements Effect {
        public Scale {
            requireForm(stat, Stat.Form.SCALE);
            requireReach(stat, target);
            requireSome(percent);
        }
    }

    /** A count moved by a whole amount. */
    record Count(Stat stat, Target target, int amount) implements Effect {
        public Count {
            requireForm(stat, Stat.Form.COUNT);
            requireReach(stat, target);
            requireSome(amount);
        }
    }

    /** An occasional behaviour made likelier, or less likely, by a chance per second. */
    record Chance(AiChance kind, Target target, float perSecond) implements Effect {
        public Chance {
            requireReach(Stat.CHANCE, target);
            if (perSecond == 0f) {
                throw new IllegalArgumentException("an effect that changes nothing is a line that lies");
            }
        }
    }

    /** A gun's stat moved by a whole percentage, for that one gun only. */
    record Gun(WeaponKind gun, Stat stat, Target target, int percent) implements Effect {
        public Gun {
            if (stat.reader() != Stat.Reader.GUN) {
                throw new IllegalArgumentException(stat + " is not a gun's, so no gun effect can name it");
            }
            requireForm(stat, Stat.Form.SCALE);
            requireReach(stat, target);
            requireSome(percent);
        }
    }

    /**
     * More of one cache in the armoury deck the sealed caches are dealt from (G0050). The side's own
     * number, so it names no target: nobody in particular reads it.
     *
     * @param card the cache the deck deals more of; one the deck already holds, which the catalogue
     *     check holds to
     * @param amount how many more cards of it; a deal only ever adds
     */
    record Deal(PickupKind card, int amount) implements Effect {
        public Deal {
            if (amount <= 0) {
                throw new IllegalArgumentException("a deal adds cards to the deck, so it adds at least one: " + amount);
            }
        }

        @Override
        public Target target() {
            return Target.SIDE;
        }
    }

    /**
     * Every crate dealt from now on holds at least this many of one card, per rank (G0056): what the deck
     * dealt is swapped for it from the last card back until there are enough. The side's own number, so it
     * names no target; a crate dealt before the pick keeps what it holds.
     *
     * @param card what every crate holds more of; one the deck holds, which the catalogue check holds to
     * @param amount how many of it, at least, per rank
     */
    record Stock(PickupKind card, int amount) implements Effect {
        public Stock {
            if (amount <= 0) {
                throw new IllegalArgumentException("a stock puts something in every crate, so at least one: " + amount);
            }
        }

        @Override
        public Target target() {
            return Target.SIDE;
        }
    }

    /**
     * A stat moved by a whole percentage for one strain of alien only - an adaptation narrowed to the
     * strain it changes, as {@link Gun} narrows an upgrade to one gun. Always the aliens'; not named
     * {@code Strain}, which is the component it is read from.
     *
     * @param strain the only strain it reaches, whatever gun that strain is holding
     * @param stat a unit's number, never the side's
     * @param percent whole percent of the base, per rank
     */
    record Breed(UnitKind strain, Stat stat, int percent) implements Effect {
        public Breed {
            requireForm(stat, Stat.Form.SCALE);
            requireReach(stat, Target.ALIENS);
            requireSome(percent);
        }

        @Override
        public Target target() {
            return Target.ALIENS;
        }
    }

    private static void requireForm(Stat stat, Stat.Form form) {
        if (stat.form() != form) {
            throw new IllegalArgumentException(stat + " is moved by " + stat.form() + ", not " + form);
        }
    }

    /** A side's number is reached only by the side, and a unit's never is; a commander is never driven. */
    private static void requireReach(Stat stat, Target target) {
        if (stat.reader() == Stat.Reader.DRIVEN && target != Target.SQUAD && target != Target.ALIENS) {
            throw new IllegalArgumentException(
                    stat + " is read where a trooper's or an alien's course is committed, so " + target
                            + " would reach a commander it never moves");
        }
        if ((stat.reader() == Stat.Reader.SIDE) != (target == Target.SIDE)) {
            throw new IllegalArgumentException(
                    stat.reader() == Stat.Reader.SIDE
                            ? stat + " is the side's own number, so only side reaches it, not " + target
                            : stat + " is read by a unit, so side cannot reach it");
        }
    }

    private static void requireSome(int amount) {
        if (amount == 0) {
            throw new IllegalArgumentException("an effect that changes nothing is a line that lies");
        }
    }
}
