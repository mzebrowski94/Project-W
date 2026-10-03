package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.writers.WriterFixtures;

final class ComponentWritersTest {

    @Test
    void creditsASystemThatSetsAFieldOfTheComponent() {
        assertThat(writers().get("Velocity")).contains("Steers");
    }

    @Test
    void creditsASystemThatCallsAMethodWhichChangesTheComponent() {
        assertThat(writers().get("Damage")).containsExactly("Hits");
    }

    @Test
    void creditsNothingThatOnlyReads() {
        assertThat(writers().values()).allSatisfy(writers -> assertThat(writers).doesNotContain("Watches"));
    }

    @Test
    void creditsAHelpersWriteToTheSystemThatCallsIt() {
        assertThat(writers().get("Velocity")).contains("Ricochets").doesNotContain("Deflect");
    }

    @Test
    void creditsAWriteNoSystemReachesToTheClassThatMakesIt() {
        assertThat(writers().get("Velocity")).containsExactly("Orders", "Rearms", "Ricochets", "Steers");
    }

    @Test
    void creditsAChangeTheSpawnerMakesForASystemToThatSystem() {
        assertThat(writers().get("Velocity")).contains("Rearms").doesNotContain("Spawner");
    }

    @Test
    void creditsAChangeToACollectionTheComponentHolds() {
        assertThat(writers().get("Drafts")).containsExactly("Clears");
    }

    @Test
    void creditsWritesInsideLambdasToTheSystemTheyRunFor() {
        assertThat(writers().get("Projectile")).containsExactly("Defers", "Delegates");
    }

    @Test
    void followsACallThroughAnInterfaceToWhatImplementsIt() {
        assertThat(writers().get("Position")).contains("Pushes").doesNotContain("Shove");
    }

    @Test
    void followsHelpersThatCallEachOtherInARing() {
        assertThat(writers().get("Position")).containsExactly("Circles", "Pushes");
    }

    @Test
    void findsNothingWrittenByAClassThatOnlyReads() {
        assertThat(writtenBy("Gauge")).isEmpty();
    }

    @Test
    void findsWhatAClassWritesItself() {
        assertThat(writtenBy("Deflect")).containsExactly("Velocity");
    }

    @Test
    void findsWhatAClassWritesThroughWhatItCalls() {
        assertThat(writtenBy("Relay")).containsExactly("Velocity");
    }

    private static Map<String, Set<String>> writers() {
        return ComponentWriters.of(fixtures());
    }

    /** What one fixture writes, measured over the same import it was found in. */
    private static Set<String> writtenBy(String fixture) {
        JavaClasses classes = fixtures();
        return ComponentWriters.writtenBy(classes, classes.get(WriterFixtures.class.getName() + "$" + fixture));
    }

    private static JavaClasses fixtures() {
        Set<Location> locations = new LinkedHashSet<>(Locations.ofPackage(WriterFixtures.class.getPackageName()));
        for (Class<?> component :
                List.of(Velocity.class, Damage.class, Position.class, Projectile.class, Drafts.class)) {
            locations.addAll(Locations.ofClass(component));
        }
        return new ClassFileImporter().importLocations(locations);
    }
}
