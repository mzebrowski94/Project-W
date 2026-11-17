package pl.mzebrows.pwt.sim.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StunnedTest {

    @Test
    void drainsADeclaredStunFromFull() {
        Stunned stunned = new Stunned();

        stunned.daze(40);
        stunned.remainingTicks = 20;

        assertThat(stunned.dazeLeft()).isEqualTo(0.5f);
    }

    @Test
    void drainsAShortStunThatLandsOnALongerStaggerOverTheWholeHold() {
        Stunned stunned = new Stunned();
        stunned.apply(40, 0f);

        stunned.daze(20);

        assertThat(stunned.dazeLeft()).isEqualTo(1f);
        stunned.remainingTicks = 10;
        assertThat(stunned.dazeLeft()).isEqualTo(0.25f);
    }

    @Test
    void forgetsALastStunsLengthWhenANewOneLandsOnAStagger() {
        Stunned stunned = new Stunned();
        stunned.daze(100);
        stunned.remainingTicks = 0;
        stunned.dazed = false;
        stunned.apply(40, 0f);

        stunned.daze(20);

        assertThat(stunned.dazeLeft()).isEqualTo(1f);
    }

    @Test
    void keepsAStunsLengthWhenAShorterOneLandsOnIt() {
        Stunned stunned = new Stunned();
        stunned.daze(40);
        stunned.remainingTicks = 30;

        stunned.daze(10);

        assertThat(stunned.dazeLeft()).isEqualTo(0.75f);
    }
}
