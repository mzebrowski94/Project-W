/**
 * The aliens' own behaviour (ADR 0020): what they notice, how they attack, and what they grow into.
 *
 * <p>Systems: {@code PerceptionSystem} - who a unit is hunting, and where; {@code EnemyLeapSystem}, {@code
 * EnemyRangedSystem} and {@code GripSystem} - the leap, the shot from a distance, and the jaws that carry a
 * man off; {@code BarrageSystem} - a boss's patterns; {@code EvolutionSystem} - a strain turning into
 * another once it has fed. Ports: none.
 *
 * <p>Not here: how they walk ({@code motion} - hop, weave, wander and the route are steering behaviours),
 * what a blow does once it lands ({@code combat}), or a nest and a wave ({@code level}).
 *
 * <p>Answers to G0011, G0015, G0016, G0026, G0032, G0036, G0045 and G0048.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.creature;

import org.jspecify.annotations.NullMarked;
