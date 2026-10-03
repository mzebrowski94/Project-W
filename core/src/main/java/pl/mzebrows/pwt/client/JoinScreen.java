package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.input.KeyHolds;
import pl.mzebrows.pwt.client.input.KeyPresses;
import pl.mzebrows.pwt.client.input.NavInputReader;
import pl.mzebrows.pwt.client.input.PadLabels;
import pl.mzebrows.pwt.client.input.PageControls;
import pl.mzebrows.pwt.client.play.JoinChoices;
import pl.mzebrows.pwt.client.play.OnlineSession;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.BuildId;
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
    private final JoinChoices choices = new JoinChoices(BuildId.current());
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
    private int sessionsShown = -1;
    private Rect panel = Rect.EMPTY;

    public JoinScreen(GameContext context) {
        this.context = context;
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
        ScreenLayout layout = context.hud().layout();
        reader.read(nav, delta, true, false, keys, held, layout);
        modality.update(nav);
        rebuild(heard != null && heard.listening());
        if (picking ? pick() : choose(layout)) {
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
                picking ? pickHint() : hints.selectAndBack(modality),
                0,
                1f);
    }

    /** The list and the picker's normal life. True when the screen is being left. */
    private boolean choose(ScreenLayout layout) {
        int visible = painter.visibleEntries(layout.canvas(), head, lines.size(), foot.size());
        int chosen = page.handle(nav, painter.layout(), panel, MenuPainter.firstEntryLine(head), visible);
        if (chosen == PageControls.BACK) {
            context.queueScreen(new MenuScreen(context, "", UiPage.ONLINE));
            return true;
        }
        if (chosen < 0) {
            return false;
        }
        int codeLine = choices.sessions().size();
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
                picking
                        ? strings.format("ui.join.picking", bracketed(picker.code(), picker.slot()))
                        : strings.format("ui.join.typed", choices.typed() + "_"));
        foot.clear();
        if (sessions.isEmpty()) {
            painter.addWrapped(foot, strings.get("ui.join.nothing"), 2);
        }
        if (!choices.problem().isEmpty()) {
            painter.addWrapped(foot, choices.problem(), 2);
        }
        if (sessions.size() != sessionsShown) {
            // The list grew or shrank as sessions were heard: keep the code line where the player was.
            boolean onCode = sessionsShown >= 0 && page.index() >= sessionsShown;
            page.reset(lines.size(), onCode ? lines.size() - 1 : Math.min(page.index(), lines.size() - 1));
            sessionsShown = sessions.size();
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

    private String pickHint() {
        String confirm = PadLabels.of(context.controlsConfig().ui().pad().confirm(), modality.playStation());
        String back = PadLabels.of(context.controlsConfig().ui().pad().back(), modality.playStation());
        return strings.format("ui.join.pickHint", confirm, back);
    }

    /** {@code A[C]DE}: the code with the slot being changed in brackets. */
    private static String bracketed(String code, int slot) {
        StringBuilder text = new StringBuilder(code.length() + 2);
        for (int i = 0; i < code.length(); i++) {
            if (i == slot) {
                text.append('[').append(code.charAt(i)).append(']');
            } else {
                text.append(' ').append(code.charAt(i)).append(' ');
            }
        }
        return text.toString();
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
    }
}
