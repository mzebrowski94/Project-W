package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.BuildId;

/**
 * Joining a game (G0038): the sessions heard on this network, as they appear, and a line to type a room
 * code or an address into. There is no lobby behind it - Enter drops the player straight into the game
 * being played.
 *
 * <p>What is typed, what is highlighted and what Enter would join is {@link JoinChoices}, which is
 * tested; this only reads keys and draws. Arrow keys move the highlight, because letters are typed.
 */
public final class JoinScreen extends ScreenAdapter {

    private static final int FIRST_ROW = 3;

    private final GameContext context;
    private final Hud hud;
    private final JoinChoices choices = new JoinChoices(BuildId.current());
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);

    private @Nullable LanListener listener;

    public JoinScreen(GameContext context) {
        this.context = context;
        this.hud = context.hud();
    }

    @Override
    public void show() {
        listener = new LanListener(context.netConfig().lan(), SystemClock.INSTANCE, NetLog.silent(64));
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char character) {
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
        if (keys.justPressed(Input.Keys.ESCAPE)) {
            context.queueScreen(new MenuScreen(context));
            return;
        }
        if (keys.justPressed(Input.Keys.UP)) {
            choices.up();
        }
        if (keys.justPressed(Input.Keys.DOWN)) {
            choices.down();
        }
        if (keys.justPressed(Input.Keys.ENTER) || keys.justPressed(Input.Keys.NUMPAD_ENTER)) {
            join();
        }
        draw(heard != null && heard.listening());
    }

    private void join() {
        JoinChoices.Target target = choices.choose(context.netConfig().port());
        if (target == null) {
            return;
        }
        Log.info("Joining " + target.description());
        context.queueScreen(new ScenarioScreen(
                context,
                OnlineSession.join(
                        context.netConfig(),
                        context.launchOptions(),
                        NetConfigLoader.latencySteps(),
                        target.host(),
                        target.port())));
    }

    private void draw(boolean listening) {
        ScreenUtils.clear(context.graphicConfig().backgroundColor());
        hud.begin();
        hud.line(0, "JOIN A GAME");
        hud.line(
                1,
                listening
                        ? "Games on this network (up/down, enter). Port "
                                + context.netConfig().lan().port() + "."
                        : "Not listening for games on this network - another program has port "
                                + context.netConfig().lan().port() + ". Type an address instead.");
        List<LanListener.Heard> sessions = choices.sessions();
        int row = FIRST_ROW;
        if (sessions.isEmpty()) {
            hud.indentedLine(row++, "nothing heard yet");
        }
        for (int i = 0; i < sessions.size() && row < hud.rows() - 5; i++) {
            LanListener.Heard session = sessions.get(i);
            hud.line(row++, (i == choices.selected() ? "> " : "  ") + describe(session));
        }
        row++;
        hud.line(row++, "Or type a room code, or an address (host or host:port):");
        hud.line(row++, "> " + choices.typed() + "_");
        hud.line(row, choices.problem());
        hud.line(hud.rows() - 1, "enter to join,  esc back to the menu");
        hud.end();
    }

    private String describe(LanListener.Heard session) {
        var announcement = session.announcement();
        return announcement.code() + "   " + announcement.scenario() + "   " + announcement.players() + "/"
                + announcement.capacity() + " players   " + announcement.host() + " at " + session.address()
                + (choices.joinable(session) ? "" : "   (another build)");
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        LanListener heard = listener;
        if (heard != null) {
            heard.close();
            listener = null;
        }
    }
}
