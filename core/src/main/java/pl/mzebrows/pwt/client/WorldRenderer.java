package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import lombok.RequiredArgsConstructor;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Draws the simulation as coloured circles.
 *
 * <p>This is the placeholder renderer: until real art exists every entity is a shape, so gameplay
 * can be built and looked at without a single texture. Once the atlas lands, a sprite renderer
 * replaces this class and the convention stays - the simulation is never asked how to draw itself.
 */
@RequiredArgsConstructor
public final class WorldRenderer implements Disposable {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final Family DRAWABLE =
            Family.all(Position.class, Body.class).get();
    private static final Color PLACEHOLDER = new Color(0.45f, 0.85f, 0.95f, 1f);
    private static final int CIRCLE_SEGMENTS = 12;

    private final Simulation simulation;
    private final Viewport viewport;

    private final ShapeRenderer shapes = new ShapeRenderer();

    /** Cached once: Ashley keeps this view live, so it must not be re-queried per frame. */
    private ImmutableArray<Entity> drawable = new ImmutableArray<>(new com.badlogic.gdx.utils.Array<>());

    /** Resolves the entity view; call once after the simulation is built. */
    public void start() {
        drawable = simulation.engine().getEntitiesFor(DRAWABLE);
    }

    /** Draws one frame. {@code alpha} is the fraction of a step elapsed, for interpolation. */
    public void render(float alpha) {
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PLACEHOLDER);
        for (int i = 0; i < drawable.size(); i++) {
            Entity entity = drawable.get(i);
            Position position = POSITION.get(entity);
            Body body = BODY.get(entity);
            shapes.circle(position.x, position.y, body.radius, CIRCLE_SEGMENTS);
        }
        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
