package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * Which one prompt each commander played here is shown (G0098): a strike while he is pinned, else what the
 * action key would do, else his upgrade for a few seconds after he earns one - and where it goes.
 */
class PromptsTest {

    private static final float UPGRADE_SECONDS = 4f;
    private static final float FRAME = 1f / 60f;
    private static final IntPredicate EVERYBODY = PromptsTest::everybody;

    private final Prompts prompts = new Prompts(UPGRADE_SECONDS);
    private final Readout readout = new Readout();

    @Test
    void promptsTheActionOverItsSubject() {
        PlayerReadout player = standing(0, 10L);
        player.action = ActionKind.MAN_TURRET;
        player.subject = 20L;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.ACTION);
        assertThat(prompts.anchor(0)).isEqualTo(20L);
        assertThat(prompts.verb(0)).isEqualTo("ui.action.MAN_TURRET");
        assertThat(prompts.hold(0)).isFalse();
        assertThat(prompts.minor(0)).isFalse();
    }

    @Test
    void aHoldSaysSoAndFillsAsTheWorkIsDone() {
        PlayerReadout player = standing(0, 10L);
        player.action = ActionKind.STABILISE;
        player.subject = 20L;
        player.progress = 0.4f;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.hold(0)).isTrue();
        assertThat(prompts.fill(0)).isEqualTo(0.4f);
    }

    @Test
    void opensADoorOnAHold() {
        PlayerReadout player = standing(0, 10L);
        player.action = ActionKind.OPEN_DOOR;
        player.subject = 20L;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.hold(0)).isTrue();
        assertThat(prompts.verb(0)).isEqualTo("ui.action.OPEN_DOOR");
    }

    @Test
    void promptsTheStrikeOverHimWhilePinned() {
        PlayerReadout player = standing(0, 10L);
        player.pinned = true;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.STRIKE);
        assertThat(prompts.anchor(0)).isEqualTo(10L);
        assertThat(prompts.verb(0)).isEqualTo("ui.action.STRIKE");
    }

    @Test
    void theStrikeComesBeforeTheActionAndTheActionBeforeTheUpgrade() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 1;
        player.action = ActionKind.OPEN_CRATE;
        player.subject = 20L;
        player.pinned = true;

        prompts.update(readout, EVERYBODY, FRAME);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.STRIKE);

        player.pinned = false;
        prompts.update(readout, EVERYBODY, FRAME);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.ACTION);

        player.action = ActionKind.NONE;
        player.subject = Handles.NOBODY;
        prompts.update(readout, EVERYBODY, FRAME);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.UPGRADE);
        assertThat(prompts.anchor(0)).isEqualTo(10L);
        assertThat(prompts.verb(0)).isEqualTo("ui.action.UPGRADE");
    }

    @Test
    void promptsTheUpgradeForItsSecondsAfterADraftIsEarnedAndThenNoMore() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 1;

        prompts.update(readout, EVERYBODY, FRAME);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.UPGRADE);

        advance(UPGRADE_SECONDS - 0.1f);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.UPGRADE);

        advance(0.2f);
        assertThat(prompts.kind(0)).isEqualTo(PromptKind.NONE);
    }

    @Test
    void aSecondDraftPromptsAgain() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 1;
        prompts.update(readout, EVERYBODY, FRAME);
        advance(UPGRADE_SECONDS + 1f);

        player.drafts = 2;
        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.UPGRADE);
    }

    /** Once he holds more than one, the prompt says how many, as his panel's tell does (G0098): UPGRADE x2. */
    @Test
    void countsHisDraftsOnTheUpgradePromptOnceHeHoldsMoreThanOne() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 1;
        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.verb(0)).isEqualTo("ui.action.UPGRADE");
        assertThat(prompts.count(0)).isZero();

        player.drafts = 2;
        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.verb(0)).isEqualTo("ui.action.UPGRADES");
        assertThat(prompts.count(0)).isEqualTo(2);
    }

    @Test
    void countsNothingOnAPromptThatIsNotTheUpgrade() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 2;
        player.action = ActionKind.OPEN_CRATE;
        player.subject = 20L;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.verb(0)).isEqualTo("ui.action.OPEN_CRATE");
        assertThat(prompts.count(0)).isZero();
    }

    @Test
    void stopsPromptingTheUpgradeOnceHeHasTakenIt() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        player.drafts = 1;
        prompts.update(readout, EVERYBODY, FRAME);

        player.drafts = 0;
        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.NONE);
    }

    /** A new world is taken silently, as the reporters take it: drafts it starts with were not just earned. */
    @Test
    void aNewWorldsDraftsWereNotJustEarned() {
        PlayerReadout player = standing(0, 10L);
        prompts.update(readout, EVERYBODY, FRAME);
        readout.worldSerial++;
        player.drafts = 1;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.NONE);
    }

    @Test
    void leavingTheGunIsTheMinorPrompt() {
        PlayerReadout player = standing(0, 10L);
        player.posture = Posture.ABOARD;
        player.action = ActionKind.LEAVE_TURRET;
        player.subject = 20L;

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.ACTION);
        assertThat(prompts.minor(0)).isTrue();
    }

    @Test
    void promptsNobodyWhoIsDownOrNotHere() {
        PlayerReadout down = standing(0, 10L);
        down.posture = Posture.CASUALTY;
        down.pinned = true;
        PlayerReadout away = standing(1, 11L);
        away.action = ActionKind.OPEN_CRATE;
        away.subject = 20L;

        prompts.update(readout, slot -> slot == 0, FRAME);

        assertThat(prompts.kind(0)).isEqualTo(PromptKind.NONE);
        assertThat(prompts.kind(1)).isEqualTo(PromptKind.NONE);
        assertThat(prompts.kind(2)).isEqualTo(PromptKind.NONE);
    }

    @Test
    void stacksThePromptsOnOneSubjectLowestSlotFirst() {
        for (int slot : new int[] {3, 1, 2}) {
            PlayerReadout player = standing(slot, 10L + slot);
            player.action = ActionKind.STABILISE;
            player.subject = slot == 2 ? 30L : 20L;
        }

        prompts.update(readout, EVERYBODY, FRAME);

        assertThat(prompts.stack(1)).isZero();
        assertThat(prompts.stack(3)).isEqualTo(1);
        assertThat(prompts.stack(2)).isZero();
    }

    @Test
    void framesThePromptsOnlyWhenThereIsMoreThanOnePlayer() {
        standing(0, 10L).pinned = true;
        prompts.update(readout, EVERYBODY, FRAME);
        assertThat(prompts.framed()).isFalse();

        standing(5, 15L);
        prompts.update(readout, slot -> slot == 0, FRAME);
        assertThat(prompts.framed()).isTrue();
    }

    @Test
    void noticesOnlyForAManFreeToAct() {
        PlayerReadout player = standing(0, 10L);
        assertThat(Prompts.noticing(player)).isTrue();

        player.pinned = true;
        assertThat(Prompts.noticing(player)).isFalse();
        player.pinned = false;

        player.stunned = true;
        assertThat(Prompts.noticing(player)).isFalse();
        player.stunned = false;

        player.crateCount = 2;
        assertThat(Prompts.noticing(player)).isFalse();
        player.crateCount = 0;

        player.posture = Posture.ABOARD;
        assertThat(Prompts.noticing(player)).isFalse();
    }

    private static boolean everybody(int slot) {
        return slot >= 0;
    }

    private PlayerReadout standing(int slot, long self) {
        PlayerReadout player = readout.player(slot);
        player.posture = Posture.STANDING;
        player.self = self;
        return player;
    }

    private void advance(float seconds) {
        for (float done = 0f; done < seconds; done += FRAME) {
            prompts.update(readout, EVERYBODY, FRAME);
        }
    }
}
