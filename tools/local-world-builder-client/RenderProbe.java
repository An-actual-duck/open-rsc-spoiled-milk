package orsc;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Hidden synthetic scene: no application main, credentials, world loading or sockets. */
public final class RenderProbe {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW unavailable");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            window = GLFW.glfwCreateWindow(96, 96, "Local editor render probe", 0, 0);
            if (window == 0) throw new IllegalStateException("hidden OpenGL context unavailable");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            LwjglBindings bindings = LwjglBindings.load();
            try (OpenGLShaderProgram projected = OpenGLShaderProgram.createProjectedWorld(bindings);
                 OpenGLShaderProgram resident = OpenGLShaderProgram.createResidentChunkParity(bindings)) {
                FloatBuffer identity = BufferUtils.createFloatBuffer(16);
                identity.put(new float[] {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}).flip();
                GL11.glViewport(0, 0, 96, 96);
                GL11.glClearColor(0, 0, 0, 1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                projected.useWorld(identity, false);
                GL20.glVertexAttrib4f(2, 0.2f, 0.8f, 0.3f, 1);
                GL20.glVertexAttrib1f(3, 32);
                GL20.glVertexAttrib3f(4, 0.2f, 0.8f, 0.3f);
                GL20.glVertexAttrib1f(5, 32);
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex3f(-0.8f, -0.8f, 0);
                GL11.glVertex3f(0.8f, -0.8f, 0);
                GL11.glVertex3f(0, 0.8f, 0);
                GL11.glEnd();
                ByteBuffer pixel = BufferUtils.createByteBuffer(4);
                GL11.glReadPixels(48, 48, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                int green = pixel.get(1) & 255;
                if (green < 20 || GL11.glGetError() != GL11.GL_NO_ERROR)
                    throw new AssertionError("projected shader did not render expected synthetic geometry");
                GL20.glUseProgram(0);
                System.out.println("PASS packaged projected/resident shaders and framebuffer triangle; GPU="
                    + GL11.glGetString(GL11.GL_RENDERER) + "; green=" + green);
            }
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
