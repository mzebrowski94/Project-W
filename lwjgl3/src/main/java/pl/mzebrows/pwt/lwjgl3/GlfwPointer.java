package pl.mzebrows.pwt.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import org.lwjgl.glfw.GLFW;
import pl.mzebrows.pwt.client.SystemPointer;

/**
 * GLFW's own hidden pointer: gone over the window, still absolute, so the mouse aims exactly as before.
 * Replaces a blank cursor image, which left a small black box beside the reticle on Windows.
 */
final class GlfwPointer implements SystemPointer {

    @Override
    public void hide() {
        mode(GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void show() {
        mode(GLFW.GLFW_CURSOR_NORMAL);
    }

    private static void mode(int mode) {
        if (Gdx.graphics instanceof Lwjgl3Graphics graphics) {
            GLFW.glfwSetInputMode(graphics.getWindow().getWindowHandle(), GLFW.GLFW_CURSOR, mode);
        }
    }
}
