package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.List;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * The scenario list. Every scenario registered in {@link Scenarios} appears here automatically -
 * there is no separate menu definition to keep in sync.
 */
public final class MenuScreen extends ScreenAdapter {

    private static final int FIRST_SCENARIO_ROW = 3;
    private static final int MAX_NUMBER_KEYS = 9;

    private final GameContext context;
    private final Hud hud;
    private final List<Scenario> scenarios = Scenarios.all();
    private final MenuSelection selection = new MenuSelection(Scenarios.all().size());
    private final KeyPresses keys = keycode -> Gdx.input.isKeyJustPressed(keycode);

    /** Why the player is back here, if the game he was in ended without him asking; empty otherwise. */
    private final String notice;

    public MenuScreen(GameContext context) {
        this(context, "");
    }

    /** The menu, saying why the player is back - an online game that ended under him. */
    public MenuScreen(GameContext context, String notice) {
        this.context = context;
        this.hud = context.hud();
        this.notice = notice;
    }

    @Override
    public void render(float delta) {
        handleInput();
        ScreenUtils.clear(context.graphicConfig().backgroundColor());

        hud.begin();
        hud.line(0, "PROJECT-W  -  alpha phase");
        hud.line(1, "Pick a scenario. Each one exists to show a system working.");
        hud.line(2, notice);
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            String marker = i == selection.index() ? ">" : " ";
            hud.line(FIRST_SCENARIO_ROW + i * 2, marker + " " + (i + 1) + ". " + scenario.name());
            hud.indentedLine(FIRST_SCENARIO_ROW + i * 2 + 1, scenario.description());
        }
        hud.line(
                hud.rows() - 1,
                "up/down or 1-9 to choose,  enter to play,  H to host it online,  J to join a game,  esc to quit");
        hud.end();
    }

    private void handleInput() {
        if (keys.justPressed(Input.Keys.UP) || keys.justPressed(Input.Keys.W)) {
            selection.previous();
        }
        if (keys.justPressed(Input.Keys.DOWN) || keys.justPressed(Input.Keys.S)) {
            selection.next();
        }
        for (int i = 0; i < scenarios.size() && i < MAX_NUMBER_KEYS; i++) {
            if (keys.justPressed(Input.Keys.NUM_1 + i) && selection.select(i)) {
                start();
                return;
            }
        }
        if (keys.justPressed(Input.Keys.ENTER) || keys.justPressed(Input.Keys.SPACE)) {
            start();
            return;
        }
        if (keys.justPressed(Input.Keys.H)) {
            host();
            return;
        }
        if (keys.justPressed(Input.Keys.J)) {
            join();
            return;
        }
        if (keys.justPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    private void start() {
        Scenario scenario = scenarios.get(selection.index());
        Log.info("Starting scenario " + scenario.name());
        context.queueScreen(new ScenarioScreen(context, new ScenarioSession(scenario, context.simConfig())));
    }

    /**
     * Hosts the chosen scenario online. Friends join from their join screen, by the room code the
     * overlay shows, or by one of its addresses (G0038).
     */
    private void host() {
        Scenario scenario = scenarios.get(selection.index());
        try {
            context.queueScreen(new ScenarioScreen(
                    context,
                    OnlineSession.host(scenario, context.simConfig(), context.netConfig(), context.launchOptions())));
        } catch (RuntimeException e) {
            Log.error("Could not host " + scenario.name(), e);
        }
    }

    /** Opens the join screen: games heard on this network, or a room code or an address typed in. */
    private void join() {
        context.queueScreen(new JoinScreen(context));
    }
}
