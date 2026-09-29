/**
 * Upgrades and adaptations (ADR 0017): the catalogue, the ledger's rules, and the one read port every
 * upgradeable number is asked through.
 *
 * <p>Fenced. It is called and does not call: nothing here depends on a system or a scenario, and
 * outside it a rule asks {@link pl.mzebrows.pwt.sim.upgrade.Modifiers} and nothing else. Four entry
 * points: {@link pl.mzebrows.pwt.sim.upgrade.UpgradeSystem} earns and writes, {@link
 * pl.mzebrows.pwt.sim.upgrade.Picks} takes a tick's picks, {@code Modifiers} is read, and {@code
 * UpgradeView} (S5) is what the interface is shown. The definitions are public; everything else is
 * package-private.
 */
@NullMarked
package pl.mzebrows.pwt.sim.upgrade;

import org.jspecify.annotations.NullMarked;
