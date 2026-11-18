package pl.mzebrows.pwt.client.hud;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.draw.ActionMark;
import pl.mzebrows.pwt.client.draw.Drawn;
import pl.mzebrows.pwt.client.draw.GraphicConfig;
import pl.mzebrows.pwt.client.draw.Palette;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.ui.hud.ChipCover;
import pl.mzebrows.pwt.ui.hud.ChipFade;
import pl.mzebrows.pwt.ui.hud.PromptKind;
import pl.mzebrows.pwt.ui.hud.Prompts;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.layout.HudMetrics;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * Each local commander's **prompt** (G0098), on the UI canvas above what it is about: the key in his device's words
 * and the verb - {@code [E] OPEN}, {@code HOLD [E] STABILISE}, {@code [C] UPGRADE x2}, filling as the work is done - on a see-through backing
 * that thins while an alien body is under it, so the fight shows through. Leaving a gun is smaller and dim. With more
 * than one player in the game each is framed in its player's colour; several on one thing stack, lowest slot
 * nearest it. Neutral text, never red over the view (section 10).
 *
 * <p>What it says and where is {@link Prompts}'; this only finds the place on screen and draws. A line is built only
 * when what it says changes.
 */
public final class ActionChips {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);

    private static final Family HANDLED = Family.all(Handle.class).get();

    private static final int SLOTS = Players.MAX;

    private final Hud hud;
    private final UiStrings strings;
    private final HudMetrics metrics;
    private final Palette palette;
    private final ActionMark mark;
    private final Color backing;
    private final Color text;
    private final Color dim;
    private final Color fill;
    private final ChipFade fade;

    private final String[] lines = new String[SLOTS];
    private final PromptKind[] saidKind = new PromptKind[SLOTS];
    private final ActionKind[] saidAction = new ActionKind[SLOTS];
    private final String[] saidKey = new String[SLOTS];
    private final int[] saidCount = new int[SLOTS];

    /** Scratch for projecting into the view; overwritten before every read. */
    private final Vector2 point = new Vector2();

    private final Color scratch = new Color();

    private ImmutableArray<Entity> handled = empty();

    /** The world point the last anchor was put at, and how many canvas pixels one world unit is there. */
    private float anchorX;

    private float anchorY;
    private float perUnit = 1f;
    private float tileSize = 1f;

    public ActionChips(Hud hud, UiStrings strings, GraphicConfig graphic, HudMetrics metrics, Palette palette) {
        this.hud = hud;
        this.strings = strings;
        this.metrics = metrics;
        this.palette = palette;
        this.mark = graphic.action();
        this.backing = graphic.hud().color().bar();
        this.text = graphic.hud().color().text();
        this.dim = graphic.hud().color().textDim();
        this.fill = graphic.actionMark();
        ActionMark.Chip chip = mark.chip();
        this.fade = new ChipFade(chip.alpha(), chip.occludedAlpha(), chip.fadeSeconds());
        Arrays.fill(lines, "");
        Arrays.fill(saidKind, PromptKind.NONE);
        Arrays.fill(saidAction, ActionKind.NONE);
        Arrays.fill(saidKey, "");
    }

    /** Points it at a world. Again after a restart - a new simulation is a new engine. */
    public void bind(Simulation world) {
        handled = world.engine().getEntitiesFor(HANDLED);
        tileSize = world.map().tileSize();
    }

    /**
     * Draws every local commander's prompt.
     *
     * @param keys by slot, what that player presses; null for a slot not played here
     * @param viewport the world's, to find where a thing is on the screen
     * @param cover whether a seen alien is under a prompt, which thins its backing
     */
    public void draw(
            ScreenLayout layout,
            Viewport viewport,
            Prompts prompts,
            Readout readout,
            @Nullable PromptKeys[] keys,
            ChipCover cover,
            float alpha,
            float delta) {
        Rect view = layout.view();
        if (view.isEmpty()) {
            return;
        }
        boolean begun = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            PromptKind kind = prompts.kind(slot);
            @Nullable PromptKeys key = keys[slot];
            @Nullable
            Entity anchor =
                    kind == PromptKind.NONE || key == null ? null : Handles.resolve(handled, prompts.anchor(slot));
            if (key == null || anchor == null || !above(anchor, alpha, viewport, layout)) {
                fade.step(slot, false, delta);
                continue;
            }
            String line = line(slot, prompts, key.of(kind));
            boolean minor = prompts.minor(slot);
            int pad = minor ? 0 : 1;
            int width = line.length() * UiFont.ADVANCE + 2 * pad + 1;
            int height = metrics.fontSize() + 2 * pad + (prompts.hold(slot) ? 1 : 0);
            int step = height + mark.chip().lift();
            int x = Math.round(point.x) - width / 2;
            // The whole stack is kept inside the view, so the prompts stacked over one thing never meet.
            int base = Math.round(point.y) + mark.chip().lift();
            base = Math.max(view.y(), Math.min(view.y() + view.height() - (prompts.stack(slot) + 1) * step, base));
            int y = base + prompts.stack(slot) * step;
            x = Math.max(view.x(), Math.min(view.right() - width, x));
            fade.step(
                    slot,
                    cover.alienWithin(
                            anchorX + (x - point.x) / perUnit,
                            anchorY + (y - point.y) / perUnit,
                            anchorX + (x + width - point.x) / perUnit,
                            anchorY + (y + height - point.y) / perUnit),
                    delta);
            if (!begun) {
                hud.beginCanvas();
                begun = true;
            }
            scratch.set(backing);
            scratch.a = fade.alpha(slot);
            hud.fill(x, y, width, height, scratch);
            if (prompts.framed() && !minor) {
                hud.frame(
                        x - 1,
                        y - 1,
                        width + 2,
                        height + 2,
                        palette.colorFor(Look.COMMANDER, readout.player(slot).variant));
            }
            if (prompts.hold(slot)) {
                float done = DOOR.has(anchor) ? DOOR.get(anchor).progress() : prompts.fill(slot);
                hud.fill(x, y, Math.round(width * Math.min(1f, Math.max(0f, done))), 1, fill);
            }
            hud.text(line, x + pad + 1, y + height - pad, minor ? dim : text);
        }
        if (begun) {
            hud.endCanvas();
        }
    }

    /**
     * Puts {@link #point} on the canvas just above the anchor's brackets - a body's top, a doorway's - and says
     * whether that is inside the view at all.
     */
    private boolean above(Entity anchor, float alpha, Viewport viewport, ScreenLayout layout) {
        if (DOOR.has(anchor)) {
            Door door = DOOR.get(anchor);
            point.set((door.col + door.width * 0.5f) * tileSize, (door.row + door.height) * tileSize + mark.gap());
        } else if (BODY.has(anchor) && PREVIOUS.has(anchor)) {
            point.set(
                    Drawn.x(POSITION.get(anchor), PREVIOUS.get(anchor), alpha),
                    Drawn.y(POSITION.get(anchor), PREVIOUS.get(anchor), alpha) + BODY.get(anchor).radius + mark.gap());
        } else {
            return false;
        }
        anchorX = point.x;
        anchorY = point.y;
        point.set(anchorX + 1f, anchorY);
        viewport.project(point);
        float unitRight = point.x;
        point.set(anchorX, anchorY);
        viewport.project(point);
        perUnit = Math.max(1e-3f, (unitRight - point.x) / layout.scale());
        point.set((point.x - layout.offsetX()) / layout.scale(), (point.y - layout.offsetY()) / layout.scale());
        Rect view = layout.view();
        return point.x >= view.x()
                && point.x <= view.right()
                && point.y >= view.y()
                && point.y <= view.y() + view.height();
    }

    /**
     * What this slot's prompt says, built again only when the key, the kind, the action or the drafts it counts have
     * changed.
     */
    private String line(int slot, Prompts prompts, String key) {
        PromptKind kind = prompts.kind(slot);
        ActionKind action = prompts.action(slot);
        int count = prompts.count(slot);
        if (kind != saidKind[slot]
                || action != saidAction[slot]
                || count != saidCount[slot]
                || !key.equals(saidKey[slot])) {
            saidKind[slot] = kind;
            saidAction[slot] = action;
            saidCount[slot] = count;
            saidKey[slot] = key;
            String verb = count > 0 ? strings.format(prompts.verb(slot), count) : strings.get(prompts.verb(slot));
            lines[slot] = strings.format(prompts.hold(slot) ? "ui.prompt.hold" : "ui.prompt.press", key, verb);
        }
        return lines[slot];
    }

    private static ImmutableArray<Entity> empty() {
        return new ImmutableArray<>(new Array<>());
    }
}
