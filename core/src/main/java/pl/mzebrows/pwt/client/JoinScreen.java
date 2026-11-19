package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.config.BuildVersion;
import pl.mzebrows.pwt.client.hud.MenuPainter;
import pl.mzebrows.pwt.client.hud.PageHints;
import pl.mzebrows.pwt.client.hud.UiStrings;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.NavInputReader;
import pl.mzebrows.pwt.client.input.PageControls;
import pl.mzebrows.pwt.client.play.JoinChoices;
import pl.mzebrows.pwt.client.play.OnlineSession;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.CodePicker;
import pl.mzebrows.pwt.ui.nav.InputModality;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.page.MenuLayout;
import pl.mzebrows.pwt.ui.page.UiPage;

/**
 * Joining a game ({@code UI:main/online/join}, G0038): the sessions heard on this network as they appear,
 * and under them a line for a room code or an address. There is no lobby behind it - confirming drops the
 * player straight into the game being played.
 *
 * <p>A keyboard types into the last line at any time. <b>A pad needs no keyboard</b> (plan Q6): confirming
 * the last line opens a four-slot picker - left and right choose the slot, up and down the letter - and
 * confirming again joins; back closes the picker. What is typed and what would be joined is {@link
 * JoinChoices}, the picker is {@link CodePicker}, both tested; this reads devices and draws.
 */
public final class JoinScreen extends ScreenAdapter {

    private final GameContext context;
    private final UiStrings strings = UiStrings.load();
    private final JoinChoices choices;
    private final CodePicker picker = new CodePicker(RoomCode.ALPHABET, RoomCode.LENGTH);
    private final NavInput nav = new NavInput();
    private final InputModality modality = new InputModality();
    private final PageControls page = new PageControls();
    private final PageHints hints;
    private final MenuPainter painter;
    private final NavInputReader reader;
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);
    private final KeyHolds held = keycode -> Gdx.input.isKeyPressed(keycode);
    private final List<String> head = new ArrayList<>(2);
    private final List<String> lines = new ArrayList<>();
    private final List<String> foot = new ArrayList<>(1);

    private @Nullable LanListener listener;
    private boolean picking;

    /** A room asked of the relay: the page waits for its door's answer before it lets go of the player (G0103). */
    private @Nullable OnlineSession asking;

    /** The session was handed to the scenario screen, which closes it. */
    private boolean handedOver;

    /** What a session is given each frame while it waits at the door: nobody holds anything. */
    private static final PlayerInputs IDLE = new PlayerInputs(Players.MAX);

    private int linesShown = -1;

    /** The room that is being asked for, as the page names it while it waits. */
    private String askedRoom = "";

    private int sessionsShown = -1;
    private Rect panel = Rect.EMPTY;

    public JoinScreen(GameContext context) {
        this.context = context;
        NetConfig.Relay relay = context.netConfig().relay();
        this.choices = new JoinChoices(
                BuildId.current(), relay.set() ? new JoinChoices.Relay(relay.host(), relay.port()) : null);
        this.hints = new PageHints(context.controlsConfig(), strings);
        this.painter = new MenuPainter(
                context.hud(),
                context.graphicConfig(),
                context.graphicConfig().layout().metrics());
        this.reader = new NavInputReader(context.controlsConfig().ui(), context.gamepads(), true);
    }

    @Override
    public void show() {
        listener = new LanListener(context.netConfig().lan(), SystemClock.INSTANCE, NetLog.silent(64));
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
                picking = false;
                choices.type(character);
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        LanListener heard = listener;
        if (heard != null) {
            heard.poll();
            choices.heard(heard.heard());
        }
        if (asking != null && !waitForTheDoor(delta)) {
            return;
        }
        ScreenLayout layout = context.hud().layout();
        reader.read(nav, delta, true, false, keys, held, layout);
        modality.update(nav);
        rebuild(heard != null && heard.listening());
        if (asking != null) {
            // Nothing is chosen while the relay answers; Back gives the attempt up.
            if (nav.back || nav.pointerSecondary) {
                giveUp();
            }
        } else if (picking ? pick() : choose(layout)) {
            return;
        }
        if (modality.showsPointer()) {
            context.hud().showSystemCursor();
        } else {
            context.hud().hideSystemCursor();
        }
        ScreenUtils.clear(context.graphicConfig().hud().color().bar());
        Rect view = layout.canvas();
        panel = painter.page(
                view,
                MenuLayout.Anchor.CENTRED,
                head,
                lines,
                page.enabled(),
                page.shown(modality),
                page.scroll(),
                painter.visibleEntries(view, head, lines.size(), foot.size()),
                foot,
                picking ? hints.picker(modality, "ui.join.pickHint") : hints.selectAndBack(modality),
                0,
                1f);
    }

    /** The list and the picker's normal life. True when the screen is being left. */
    private boolean choose(ScreenLayout layout) {
        int visible = painter.visibleEntries(layout.canvas(), head, lines.size(), foot.size());
        int chosen = page.handle(nav, painter.layout(), panel, MenuPainter.firstEntryLine(head), visible);
        if (chosen == PageControls.BACK) {
            if (choices.asking() != null) {
                // Out of the password line, to the code line again.
                choices.cancelPassword();
                picking = false;
                return false;
            }
            context.queueScreen(new MenuScreen(context, "", UiPage.ONLINE));
            return true;
        }
        if (chosen < 0) {
            return false;
        }
        int codeLine = choices.sessions().size();
        if (choices.asking() != null && chosen > codeLine) {
            // The password line, under the code line.
            if (choices.typedPassword().isEmpty() || modality.padHints()) {
                picking = true;
                choices.replaceTyped(picker.code());
                return false;
            }
            return join();
        }
        if (choices.asking() != null && chosen == codeLine) {
            // Back to the code: the page stops asking.
            choices.cancelPassword();
            return false;
        }
        if (chosen < codeLine) {
            choices.replaceTyped("");
            choices.select(chosen);
            return join();
        }
        if (choices.typed().isEmpty() || modality.padHints()) {
            // Nothing typed, or a pad: pick the code a letter at a time.
            picking = true;
            choices.replaceTyped(picker.code());
            return false;
        }
        return join();
    }

    /** The pad's code picker: left and right choose the slot, up and down the letter. */
    private boolean pick() {
        if (nav.left) {
            picker.left();
        }
        if (nav.right) {
            picker.right();
        }
        if (nav.up) {
            picker.up();
        }
        if (nav.down) {
            picker.down();
        }
        choices.replaceTyped(picker.code());
        if (nav.back || nav.pointerSecondary) {
            picking = false;
            return false;
        }
        return nav.confirm && join();
    }

    private boolean join() {
        JoinChoices.Target target = choices.choose(context.netConfig().port());
        if (target == null) {
            return false;
        }
        Log.info("Joining " + target.description());
        if (target.relayed()) {
            return ask(target);
        }
        context.queueScreen(new ScenarioScreen(
                context,
                OnlineSession.join(
                        context.netConfig(),
                        context.launchOptions(),
                        context.netConfig().latencySteps(),
                        target.host(),
                        target.port(),
                        LocalMachineId.load())));
        return true;
    }

    /** Asks the relay's door into a room: the page waits for the answer. False if the attempt could not be made. */
    private boolean ask(JoinChoices.Target target) {
        try {
            asking = OnlineSession.joinThroughRelay(
                    context.netConfig(),
                    context.launchOptions(),
                    context.netConfig().latencySteps(),
                    target.room(),
                    target.password(),
                    LocalMachineId.load(),
                    BuildVersion.current());
        } catch (RuntimeException e) {
            Log.error("Could not reach the relay", e);
            context.queueScreen(new MenuScreen(context, OnlineSession.UNREACHABLE));
            return true;
        }
        askedRoom = target.room();
        picking = false;
        return false;
    }

    /**
     * One frame of the door's answer. Let in, the player goes on to the session. A refusal for want of a password opens
     * the password line - once: a password that was given and is wrong is the relay's other refusal, as any other
     * refusal - and the player goes back to the menu with what the relay said (G0103). False when the screen is left.
     */
    private boolean waitForTheDoor(float delta) {
        OnlineSession door = asking;
        if (door == null) {
            return true;
        }
        door.advance(delta, IDLE);
        String refusal = door.relayRefusal();
        if (refusal != null) {
            asking = null;
            door.close();
            if (choices.asking() == null
                    && refusal.equals(Door.REFUSAL_PREFIX + "room " + askedRoom + " has a password")) {
                choices.askPassword(askedRoom);
                picking = false;
                return true;
            }
            Log.info(refusal);
            context.queueScreen(new MenuScreen(context, refusal));
            return false;
        }
        if (door.relayUnreachable()) {
            asking = null;
            door.close();
            context.queueScreen(new MenuScreen(context, OnlineSession.UNREACHABLE));
            return false;
        }
        if (door.relayAnswered()) {
            asking = null;
            handedOver = true;
            context.queueScreen(new ScenarioScreen(context, door));
            return false;
        }
        return true;
    }

    private void giveUp() {
        OnlineSession door = asking;
        asking = null;
        if (door != null) {
            door.close();
        }
    }

    /** The page's words: one line per session heard, then the code line; what went wrong under them. */
    private void rebuild(boolean listening) {
        List<LanListener.Heard> sessions = choices.sessions();
        head.clear();
        head.add(strings.get("ui.join.title"));
        int port = context.netConfig().lan().port();
        painter.addWrapped(head, strings.format(listening ? "ui.join.listening" : "ui.join.deaf", port), 2);
        lines.clear();
        for (LanListener.Heard session : sessions) {
            lines.add(describe(session));
        }
        lines.add(
                picking && choices.asking() == null
                        ? strings.format("ui.join.picking", picker.shown(0))
                        : strings.format("ui.join.typed", choices.typed() + "_"));
        if (choices.asking() != null) {
            lines.add(
                    picking
                            ? strings.format("ui.join.passwordPicking", picker.shown(0))
                            : strings.format("ui.join.password", choices.typedPassword() + "_"));
        }
        foot.clear();
        if (choices.asking() != null) {
            painter.addWrapped(foot, strings.format("ui.join.passwordAsked", choices.asking()), 2);
        }
        if (asking != null) {
            painter.addWrapped(foot, strings.format("ui.join.connecting", askedRoom), 2);
        }
        if (sessions.isEmpty()) {
            painter.addWrapped(foot, strings.get("ui.join.nothing"), 2);
        }
        if (!choices.problem().isEmpty()) {
            painter.addWrapped(foot, choices.problem(), 2);
        }
        if (sessions.size() != sessionsShown || lines.size() != linesShown) {
            // The list grew or shrank as sessions were heard, or the password line came or went: keep the player on
            // the code line - or put him on the password line when it is asked for.
            boolean onCode = sessionsShown >= 0 && page.index() >= sessionsShown;
            page.reset(
                    lines.size(),
                    onCode || choices.asking() != null ? lines.size() - 1 : Math.min(page.index(), lines.size() - 1));
            sessionsShown = sessions.size();
            linesShown = lines.size();
        }
    }

    private String describe(LanListener.Heard session) {
        var announcement = session.announcement();
        // The host's address last: on a narrow page it is the part cut, and the warning must not be.
        return announcement.code() + "  " + announcement.scenario() + "  " + announcement.players() + "/"
                + announcement.capacity()
                + (choices.joinable(session) ? "" : "  " + strings.get("ui.join.otherBuild"))
                + "  " + announcement.host();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        context.hud().showSystemCursor();
        LanListener heard = listener;
        if (heard != null) {
            heard.close();
            listener = null;
        }
        if (!handedOver) {
            giveUp();
        }
    }
}
