/**
 * Upgrades and adaptations (ADR 0017): the numbers they may move, how a number is folded from its
 * base, and the one read port every such number is asked through.
 *
 * <p>Fenced. It is called and does not call: nothing here depends on a system or a scenario, and
 * outside it a rule asks {@link pl.mzebrows.pwt.sim.upgrade.Modifiers} and nothing else. Its public
 * entry points are {@code Modifiers} now, and {@code UpgradeSystem}, {@code Picks} and {@code
 * UpgradeView} as they land ({@code docs/design/upgrades-scope.md}); everything else is package-private.
 */
@NullMarked
package pl.mzebrows.pwt.sim.upgrade;

import org.jspecify.annotations.NullMarked;
