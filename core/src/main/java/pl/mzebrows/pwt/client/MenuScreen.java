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

    public MenuScreen(GameContext context) {
        this.context = context;
        this.hud = context.hud();
    }

    @Override
    public void render(float delta) {
        handleInput();
        ScreenUtils.clear(context.graphicConfig().backgroundColor());

        hud.begin();
        hud.line(0, "PROJECT-W  -  alpha phase");
        hud.line(1, "Pick a scenario. Each one exists to show a system working.");
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            String marker = i == selection.index() ? ">" : " ";
            hud.line(FIRST_SCENARIO_ROW + i * 2, marker + " " + (i + 1) + ". " + scenario.name());
            hud.indentedLine(FIRST_SCENARIO_ROW + i * 2 + 1, scenario.description());
        }
        hud.line(hud.rows() - 1, "up/down or 1-9 to choose,  enter to start,  esc to quit");
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
        if (keys.justPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    private void start() {
        Scenario scenario = scenarios.get(selection.index());
        Log.info("Starting scenario " + scenario.name());
        context.queueScreen(new ScenarioScreen(context, scenario));
    }
}
