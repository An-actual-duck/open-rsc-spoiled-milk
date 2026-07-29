package orsc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

interface ScrollHandler {
	void handle(long window, double xOffset, double yOffset);
}

interface GlfwKeyHandler {
	void handle(long window, int key, int scanCode, int action, int mods);
}

interface CharHandler {
	void handle(long window, int codepoint);
}

interface WindowFocusHandler {
	void handle(long window, boolean focused);
}

final class LwjglBindings {
	private final Class<?> glfwClass;
	private final Method glfwInit;
	private final Method glfwTerminate;
	private final Method glfwDefaultWindowHints;
	private final Method glfwWindowHint;
	private final Method glfwCreateWindow;
	private final Method glfwMakeContextCurrent;
	private final Method glfwSwapInterval;
	private final Method glfwShowWindow;
	private final Method glfwHideWindow;
	private final Method glfwWindowShouldClose;
	private final Method glfwPollEvents;
	private final Method glfwSwapBuffers;
	private final Method glfwDestroyWindow;
	private final Method glfwSetWindowSize;
	private final Method glfwGetWindowPos;
	private final Method glfwSetWindowPos;
	private final Method glfwSetWindowAttrib;
	private final Method glfwGetPrimaryMonitor;
	private final Method glfwGetMonitorPos;
	private final Method glfwGetVideoMode;
	private final Class<?> glfwScrollCallbackInterface;
	private final Method glfwSetScrollCallback;
	private final Object glfwScrollCallbackCif;
	private final Class<?> glfwKeyCallbackInterface;
	private final Method glfwSetKeyCallback;
	private final Object glfwKeyCallbackCif;
	private final Class<?> glfwCharCallbackInterface;
	private final Method glfwSetCharCallback;
	private final Object glfwCharCallbackCif;
	private final Class<?> glfwWindowFocusCallbackInterface;
	private final Method glfwSetWindowFocusCallback;
	private final Object glfwWindowFocusCallbackCif;
	private final Method callbackCreate;
	private final Method callbackFree;
	private final Method memGetAddress;
	private final Method memGetInt;
	private final Method memGetDouble;
	private final Method glfwGetWindowSize;
	private final Method glfwGetFramebufferSize;
	private final Method glfwGetWindowAttrib;
	private final Method glfwGetCursorPos;
	private final Method glfwGetMouseButton;
	private final Method glfwGetKey;
	private final Method createCapabilities;
	private final Method glClearColor;
	private final Method glClear;
	private final Method glViewport;
	private final MethodHandle glEnable;
	private final MethodHandle glDisable;
	private final Method glScissor;
	private final Method glPushAttrib;
	private final Method glPopAttrib;
	private final Method glPushClientAttrib;
	private final Method glPopClientAttrib;
	private final Method glPolygonOffset;
	private final Method glPolygonMode;
	private final Method glCullFace;
	private final Method glGenTextures;
	private final Method glDeleteTextures;
	private final Method glActiveTexture;
	private final MethodHandle glBindTexture;
	private final MethodHandle glTexParameteri;
	private final Method glTexImage2D;
	private final Method glTexSubImage2D;
	private final Method glReadPixels;
	private final MethodHandle glBlendFunc;
	private final Method glAlphaFunc;
	private final Method glDepthMask;
	private final Method glColorMask;
	private final MethodHandle glColor4f;
	private final Method glFogi;
	private final Method glFogf;
	private final Method glFogfv;
	private final Method glGetString;
	private final Method glMatrixMode;
	private final Method glLoadIdentity;
	private final Method glLoadMatrixf;
	private final Method glOrtho;
	private final Method glGenBuffers;
	private final Method glDeleteBuffers;
	private final MethodHandle glBindBuffer;
	private final MethodHandle glBufferDataFloat;
	private final MethodHandle glBufferDataInt;
	private final MethodHandle glEnableClientState;
	private final MethodHandle glDisableClientState;
	private final MethodHandle glVertexPointer;
	private final MethodHandle glColorPointer;
	private final MethodHandle glTexCoordPointer;
	private final MethodHandle glDrawElements;
	private final Method glBegin;
	private final Method glEnd;
	private final Method glTexCoord2f;
	private final MethodHandle glVertex3f;
	private final Method glLineWidth;
	private final Method glCreateShader;
	private final Method glShaderSource;
	private final Method glCompileShader;
	private final Method glGetShaderi;
	private final Method glGetShaderInfoLog;
	private final Method glDeleteShader;
	private final Method glCreateProgram;
	private final Method glAttachShader;
	private final Method glBindAttribLocation;
	private final Method glLinkProgram;
	private final Method glGetProgrami;
	private final Method glGetProgramInfoLog;
	private final Method glUseProgram;
	private final Method glGetUniformLocation;
	private final Method glUniform1i;
	private final Method glUniform1f;
	private final Method glUniformMatrix4fv;
	private final MethodHandle glEnableVertexAttribArray;
	private final MethodHandle glDisableVertexAttribArray;
	private final MethodHandle glVertexAttribPointer;
	private final Method glDeleteProgram;

	final int GLFW_FALSE;
	final int GLFW_TRUE;
	final int GLFW_RESIZABLE;
	final int GLFW_VISIBLE;
	final int GLFW_FOCUSED;
	final int GLFW_DECORATED;
	final int GLFW_PRESS;
	final int GLFW_RELEASE;
	final int GLFW_REPEAT;
	final int GLFW_MOD_SHIFT;
	final int GLFW_MOD_CONTROL;
	final int GLFW_KEY_LEFT_SHIFT;
	final int GLFW_KEY_RIGHT_SHIFT;
	final int GLFW_KEY_LEFT_CONTROL;
	final int GLFW_KEY_RIGHT_CONTROL;
	final int GLFW_KEY_LEFT_ALT;
	final int GLFW_KEY_RIGHT_ALT;
	final int GLFW_MOUSE_BUTTON_LEFT;
	final int GLFW_MOUSE_BUTTON_RIGHT;
	final int GLFW_MOUSE_BUTTON_MIDDLE;
	final int POINTER_SIZE;
	final int GL_COLOR_BUFFER_BIT;
	final int GL_DEPTH_BUFFER_BIT;
	final int GL_ALL_ATTRIB_BITS;
	final int GL_CLIENT_ALL_ATTRIB_BITS;
	final int GL_TEXTURE_2D;
	final int GL_TEXTURE0;
	final int GL_TEXTURE1;
	final int GL_TEXTURE2;
	final int GL_TEXTURE_MIN_FILTER;
	final int GL_TEXTURE_MAG_FILTER;
	final int GL_TEXTURE_WRAP_S;
	final int GL_TEXTURE_WRAP_T;
	final int GL_NEAREST;
	final int GL_LINEAR;
	final int GL_CLAMP;
	final int GL_CLAMP_TO_EDGE;
	final int GL_RGBA;
	final int GL_UNSIGNED_BYTE;
	final int GL_BLEND;
	final int GL_DEPTH_TEST;
	final int GL_FOG;
	final int GL_FOG_MODE;
	final int GL_FOG_START;
	final int GL_FOG_END;
	final int GL_FOG_COLOR;
	final int GL_SCISSOR_TEST;
	final int GL_POLYGON_OFFSET_FILL;
	final int GL_CULL_FACE;
	final int GL_BACK;
	final int GL_SRC_ALPHA;
	final int GL_ONE_MINUS_SRC_ALPHA;
	final int GL_ALPHA_TEST;
	final int GL_GREATER;
	final int GL_FRONT_AND_BACK;
	final int GL_LINE;
	final int GL_FILL;
	final int GL_LINES;
	final int GL_QUADS;
	final int GL_TRIANGLES;
	final int GL_VERTEX_ARRAY;
	final int GL_COLOR_ARRAY;
	final int GL_TEXTURE_COORD_ARRAY;
	final int GL_FLOAT;
	final int GL_UNSIGNED_INT;
	final int GL_ARRAY_BUFFER;
	final int GL_ELEMENT_ARRAY_BUFFER;
	final int GL_STREAM_DRAW;
	final int GL_STATIC_DRAW;
	final int GL_PROJECTION;
	final int GL_MODELVIEW;
	final int GL_VERTEX_SHADER;
	final int GL_FRAGMENT_SHADER;
	final int GL_COMPILE_STATUS;
	final int GL_LINK_STATUS;
	final int GL_VENDOR;
	final int GL_RENDERER;
	final int GL_VERSION;

	static LwjglBindings load() throws Exception {
		Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
		Class<?> glClass = Class.forName("org.lwjgl.opengl.GL");
		Class<?> gl11Class = Class.forName("org.lwjgl.opengl.GL11");
		Class<?> gl12Class = optionalClass("org.lwjgl.opengl.GL12");
		Class<?> gl13Class = Class.forName("org.lwjgl.opengl.GL13");
		Class<?> gl15Class = Class.forName("org.lwjgl.opengl.GL15");
		Class<?> gl20Class = Class.forName("org.lwjgl.opengl.GL20");
		return new LwjglBindings(glfwClass, glClass, gl11Class, gl12Class, gl13Class, gl15Class, gl20Class);
	}

	private LwjglBindings(
		Class<?> glfwClass,
		Class<?> glClass,
		Class<?> gl11Class,
		Class<?> gl12Class,
		Class<?> gl13Class,
		Class<?> gl15Class,
		Class<?> gl20Class)
		throws Exception {
		Class<?> ffiCifClass = Class.forName("org.lwjgl.system.libffi.FFICIF");
		Class<?> callbackClass = Class.forName("org.lwjgl.system.Callback");
		Class<?> memoryUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
		Class<?> pointerClass = Class.forName("org.lwjgl.system.Pointer");

		this.glfwClass = glfwClass;
		glfwInit = method(glfwClass, "glfwInit");
		glfwTerminate = method(glfwClass, "glfwTerminate");
		glfwDefaultWindowHints = method(glfwClass, "glfwDefaultWindowHints");
		glfwWindowHint = method(glfwClass, "glfwWindowHint", int.class, int.class);
		glfwCreateWindow = method(
			glfwClass,
			"glfwCreateWindow",
			int.class,
			int.class,
			CharSequence.class,
			long.class,
			long.class);
		glfwMakeContextCurrent = method(glfwClass, "glfwMakeContextCurrent", long.class);
		glfwSwapInterval = method(glfwClass, "glfwSwapInterval", int.class);
		glfwShowWindow = method(glfwClass, "glfwShowWindow", long.class);
		glfwHideWindow = method(glfwClass, "glfwHideWindow", long.class);
		glfwWindowShouldClose = method(glfwClass, "glfwWindowShouldClose", long.class);
		glfwPollEvents = method(glfwClass, "glfwPollEvents");
		glfwSwapBuffers = method(glfwClass, "glfwSwapBuffers", long.class);
		glfwDestroyWindow = method(glfwClass, "glfwDestroyWindow", long.class);
		glfwSetWindowSize = method(glfwClass, "glfwSetWindowSize", long.class, int.class, int.class);
		glfwGetWindowPos = method(glfwClass, "glfwGetWindowPos", long.class, int[].class, int[].class);
		glfwSetWindowPos = method(glfwClass, "glfwSetWindowPos", long.class, int.class, int.class);
		glfwSetWindowAttrib = method(glfwClass, "glfwSetWindowAttrib", long.class, int.class, int.class);
		glfwGetPrimaryMonitor = method(glfwClass, "glfwGetPrimaryMonitor");
		glfwGetMonitorPos = method(glfwClass, "glfwGetMonitorPos", long.class, int[].class, int[].class);
		glfwGetVideoMode = method(glfwClass, "glfwGetVideoMode", long.class);
		glfwScrollCallbackInterface = Class.forName("org.lwjgl.glfw.GLFWScrollCallbackI");
		glfwSetScrollCallback =
			method(glfwClass, "glfwSetScrollCallback", long.class, glfwScrollCallbackInterface);
		glfwScrollCallbackCif = fieldValue(glfwScrollCallbackInterface, "CIF");
		glfwKeyCallbackInterface = Class.forName("org.lwjgl.glfw.GLFWKeyCallbackI");
		glfwSetKeyCallback = method(glfwClass, "glfwSetKeyCallback", long.class, glfwKeyCallbackInterface);
		glfwKeyCallbackCif = fieldValue(glfwKeyCallbackInterface, "CIF");
		glfwCharCallbackInterface = Class.forName("org.lwjgl.glfw.GLFWCharCallbackI");
		glfwSetCharCallback = method(glfwClass, "glfwSetCharCallback", long.class, glfwCharCallbackInterface);
		glfwCharCallbackCif = fieldValue(glfwCharCallbackInterface, "CIF");
		glfwWindowFocusCallbackInterface = Class.forName("org.lwjgl.glfw.GLFWWindowFocusCallbackI");
		glfwSetWindowFocusCallback =
			method(glfwClass, "glfwSetWindowFocusCallback", long.class, glfwWindowFocusCallbackInterface);
		glfwWindowFocusCallbackCif = fieldValue(glfwWindowFocusCallbackInterface, "CIF");
		callbackCreate = declaredMethod(callbackClass, "create", ffiCifClass, Object.class);
		callbackFree = method(callbackClass, "free", long.class);
		memGetAddress = method(memoryUtilClass, "memGetAddress", long.class);
		memGetInt = method(memoryUtilClass, "memGetInt", long.class);
		memGetDouble = method(memoryUtilClass, "memGetDouble", long.class);
		glfwGetWindowSize = method(glfwClass, "glfwGetWindowSize", long.class, int[].class, int[].class);
		glfwGetFramebufferSize =
			method(glfwClass, "glfwGetFramebufferSize", long.class, int[].class, int[].class);
		glfwGetWindowAttrib = method(glfwClass, "glfwGetWindowAttrib", long.class, int.class);
		glfwGetCursorPos = method(glfwClass, "glfwGetCursorPos", long.class, double[].class, double[].class);
		glfwGetMouseButton = method(glfwClass, "glfwGetMouseButton", long.class, int.class);
		glfwGetKey = method(glfwClass, "glfwGetKey", long.class, int.class);
		createCapabilities = method(glClass, "createCapabilities");

		glClearColor = method(gl11Class, "glClearColor", float.class, float.class, float.class, float.class);
		glClear = method(gl11Class, "glClear", int.class);
		glViewport = method(gl11Class, "glViewport", int.class, int.class, int.class, int.class);
		glEnable = methodHandle(gl11Class, "glEnable", int.class);
		glDisable = methodHandle(gl11Class, "glDisable", int.class);
		glScissor = method(gl11Class, "glScissor", int.class, int.class, int.class, int.class);
		glPushAttrib = method(gl11Class, "glPushAttrib", int.class);
		glPopAttrib = method(gl11Class, "glPopAttrib");
		glPushClientAttrib = method(gl11Class, "glPushClientAttrib", int.class);
		glPopClientAttrib = method(gl11Class, "glPopClientAttrib");
		glPolygonOffset = method(gl11Class, "glPolygonOffset", float.class, float.class);
		glPolygonMode = method(gl11Class, "glPolygonMode", int.class, int.class);
		glCullFace = method(gl11Class, "glCullFace", int.class);
		glGenTextures = method(gl11Class, "glGenTextures");
		glDeleteTextures = method(gl11Class, "glDeleteTextures", int.class);
		glActiveTexture = method(gl13Class, "glActiveTexture", int.class);
		glBindTexture = methodHandle(gl11Class, "glBindTexture", int.class, int.class);
		glTexParameteri = methodHandle(gl11Class, "glTexParameteri", int.class, int.class, int.class);
		glTexImage2D = method(
			gl11Class,
			"glTexImage2D",
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			ByteBuffer.class);
		glTexSubImage2D = method(
			gl11Class,
			"glTexSubImage2D",
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			ByteBuffer.class);
		glReadPixels = method(
			gl11Class,
			"glReadPixels",
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			ByteBuffer.class);
		glBlendFunc = methodHandle(gl11Class, "glBlendFunc", int.class, int.class);
		glAlphaFunc = method(gl11Class, "glAlphaFunc", int.class, float.class);
		glDepthMask = method(gl11Class, "glDepthMask", boolean.class);
		glColorMask = method(gl11Class, "glColorMask", boolean.class, boolean.class, boolean.class, boolean.class);
		glColor4f =
			methodHandle(gl11Class, "glColor4f", float.class, float.class, float.class, float.class);
		glFogi = method(gl11Class, "glFogi", int.class, int.class);
		glFogf = method(gl11Class, "glFogf", int.class, float.class);
		glFogfv = method(gl11Class, "glFogfv", int.class, FloatBuffer.class);
		glGetString = method(gl11Class, "glGetString", int.class);
		glMatrixMode = method(gl11Class, "glMatrixMode", int.class);
		glLoadIdentity = method(gl11Class, "glLoadIdentity");
		glLoadMatrixf = method(gl11Class, "glLoadMatrixf", FloatBuffer.class);
		glOrtho = method(
			gl11Class,
			"glOrtho",
			double.class,
			double.class,
			double.class,
			double.class,
			double.class,
			double.class);
		glGenBuffers = method(gl15Class, "glGenBuffers");
		glDeleteBuffers = method(gl15Class, "glDeleteBuffers", int.class);
		glBindBuffer = methodHandle(gl15Class, "glBindBuffer", int.class, int.class);
		glBufferDataFloat =
			methodHandle(gl15Class, "glBufferData", int.class, FloatBuffer.class, int.class);
		glBufferDataInt =
			methodHandle(gl15Class, "glBufferData", int.class, IntBuffer.class, int.class);
		glEnableClientState = methodHandle(gl11Class, "glEnableClientState", int.class);
		glDisableClientState = methodHandle(gl11Class, "glDisableClientState", int.class);
		glVertexPointer =
			methodHandle(gl11Class, "glVertexPointer", int.class, int.class, int.class, long.class);
		glColorPointer =
			methodHandle(gl11Class, "glColorPointer", int.class, int.class, int.class, long.class);
		glTexCoordPointer =
			methodHandle(gl11Class, "glTexCoordPointer", int.class, int.class, int.class, long.class);
		glDrawElements =
			methodHandle(gl11Class, "glDrawElements", int.class, int.class, int.class, long.class);
		glBegin = method(gl11Class, "glBegin", int.class);
		glEnd = method(gl11Class, "glEnd");
		glTexCoord2f = method(gl11Class, "glTexCoord2f", float.class, float.class);
		glVertex3f = methodHandle(gl11Class, "glVertex3f", float.class, float.class, float.class);
		glLineWidth = method(gl11Class, "glLineWidth", float.class);
		glCreateShader = method(gl20Class, "glCreateShader", int.class);
		glShaderSource = method(gl20Class, "glShaderSource", int.class, CharSequence.class);
		glCompileShader = method(gl20Class, "glCompileShader", int.class);
		glGetShaderi = method(gl20Class, "glGetShaderi", int.class, int.class);
		glGetShaderInfoLog = method(gl20Class, "glGetShaderInfoLog", int.class);
		glDeleteShader = method(gl20Class, "glDeleteShader", int.class);
		glCreateProgram = method(gl20Class, "glCreateProgram");
		glAttachShader = method(gl20Class, "glAttachShader", int.class, int.class);
		glBindAttribLocation = method(gl20Class, "glBindAttribLocation", int.class, int.class, CharSequence.class);
		glLinkProgram = method(gl20Class, "glLinkProgram", int.class);
		glGetProgrami = method(gl20Class, "glGetProgrami", int.class, int.class);
		glGetProgramInfoLog = method(gl20Class, "glGetProgramInfoLog", int.class);
		glUseProgram = method(gl20Class, "glUseProgram", int.class);
		glGetUniformLocation = method(gl20Class, "glGetUniformLocation", int.class, CharSequence.class);
		glUniform1i = method(gl20Class, "glUniform1i", int.class, int.class);
		glUniform1f = method(gl20Class, "glUniform1f", int.class, float.class);
		glUniformMatrix4fv = method(gl20Class, "glUniformMatrix4fv", int.class, boolean.class, FloatBuffer.class);
		glEnableVertexAttribArray = methodHandle(gl20Class, "glEnableVertexAttribArray", int.class);
		glDisableVertexAttribArray = methodHandle(gl20Class, "glDisableVertexAttribArray", int.class);
		glVertexAttribPointer =
			methodHandle(
				gl20Class,
				"glVertexAttribPointer",
				int.class,
				int.class,
				int.class,
				boolean.class,
				int.class,
				long.class);
		glDeleteProgram = method(gl20Class, "glDeleteProgram", int.class);

		GLFW_FALSE = constant(glfwClass, "GLFW_FALSE");
		GLFW_TRUE = constant(glfwClass, "GLFW_TRUE");
		GLFW_RESIZABLE = constant(glfwClass, "GLFW_RESIZABLE");
		GLFW_VISIBLE = constant(glfwClass, "GLFW_VISIBLE");
		GLFW_FOCUSED = constant(glfwClass, "GLFW_FOCUSED");
		GLFW_DECORATED = constant(glfwClass, "GLFW_DECORATED");
		GLFW_PRESS = constant(glfwClass, "GLFW_PRESS");
		GLFW_RELEASE = constant(glfwClass, "GLFW_RELEASE");
		GLFW_REPEAT = constant(glfwClass, "GLFW_REPEAT");
		GLFW_MOD_SHIFT = constant(glfwClass, "GLFW_MOD_SHIFT");
		GLFW_MOD_CONTROL = constant(glfwClass, "GLFW_MOD_CONTROL");
		GLFW_KEY_LEFT_SHIFT = constant(glfwClass, "GLFW_KEY_LEFT_SHIFT");
		GLFW_KEY_RIGHT_SHIFT = constant(glfwClass, "GLFW_KEY_RIGHT_SHIFT");
		GLFW_KEY_LEFT_CONTROL = constant(glfwClass, "GLFW_KEY_LEFT_CONTROL");
		GLFW_KEY_RIGHT_CONTROL = constant(glfwClass, "GLFW_KEY_RIGHT_CONTROL");
		GLFW_KEY_LEFT_ALT = constant(glfwClass, "GLFW_KEY_LEFT_ALT");
		GLFW_KEY_RIGHT_ALT = constant(glfwClass, "GLFW_KEY_RIGHT_ALT");
		GLFW_MOUSE_BUTTON_LEFT = constant(glfwClass, "GLFW_MOUSE_BUTTON_LEFT");
		GLFW_MOUSE_BUTTON_RIGHT = constant(glfwClass, "GLFW_MOUSE_BUTTON_RIGHT");
		GLFW_MOUSE_BUTTON_MIDDLE = constant(glfwClass, "GLFW_MOUSE_BUTTON_MIDDLE");
		POINTER_SIZE = constant(pointerClass, "POINTER_SIZE");
		GL_COLOR_BUFFER_BIT = constant(gl11Class, "GL_COLOR_BUFFER_BIT");
		GL_DEPTH_BUFFER_BIT = constant(gl11Class, "GL_DEPTH_BUFFER_BIT");
		GL_ALL_ATTRIB_BITS = constant(gl11Class, "GL_ALL_ATTRIB_BITS");
		GL_CLIENT_ALL_ATTRIB_BITS = constant(gl11Class, "GL_CLIENT_ALL_ATTRIB_BITS");
		GL_TEXTURE_2D = constant(gl11Class, "GL_TEXTURE_2D");
		GL_TEXTURE0 = constant(gl13Class, "GL_TEXTURE0");
		GL_TEXTURE1 = constant(gl13Class, "GL_TEXTURE1");
		GL_TEXTURE2 = constant(gl13Class, "GL_TEXTURE2");
		GL_TEXTURE_MIN_FILTER = constant(gl11Class, "GL_TEXTURE_MIN_FILTER");
		GL_TEXTURE_MAG_FILTER = constant(gl11Class, "GL_TEXTURE_MAG_FILTER");
		GL_TEXTURE_WRAP_S = constant(gl11Class, "GL_TEXTURE_WRAP_S");
		GL_TEXTURE_WRAP_T = constant(gl11Class, "GL_TEXTURE_WRAP_T");
		GL_NEAREST = constant(gl11Class, "GL_NEAREST");
		GL_LINEAR = constant(gl11Class, "GL_LINEAR");
		GL_CLAMP = constant(gl11Class, "GL_CLAMP");
		GL_CLAMP_TO_EDGE = optionalConstant(gl12Class, "GL_CLAMP_TO_EDGE", GL_CLAMP);
		GL_RGBA = constant(gl11Class, "GL_RGBA");
		GL_UNSIGNED_BYTE = constant(gl11Class, "GL_UNSIGNED_BYTE");
		GL_BLEND = constant(gl11Class, "GL_BLEND");
		GL_DEPTH_TEST = constant(gl11Class, "GL_DEPTH_TEST");
		GL_FOG = constant(gl11Class, "GL_FOG");
		GL_FOG_MODE = constant(gl11Class, "GL_FOG_MODE");
		GL_FOG_START = constant(gl11Class, "GL_FOG_START");
		GL_FOG_END = constant(gl11Class, "GL_FOG_END");
		GL_FOG_COLOR = constant(gl11Class, "GL_FOG_COLOR");
		GL_SCISSOR_TEST = constant(gl11Class, "GL_SCISSOR_TEST");
		GL_POLYGON_OFFSET_FILL = constant(gl11Class, "GL_POLYGON_OFFSET_FILL");
		GL_CULL_FACE = constant(gl11Class, "GL_CULL_FACE");
		GL_BACK = constant(gl11Class, "GL_BACK");
		GL_SRC_ALPHA = constant(gl11Class, "GL_SRC_ALPHA");
		GL_ONE_MINUS_SRC_ALPHA = constant(gl11Class, "GL_ONE_MINUS_SRC_ALPHA");
		GL_ALPHA_TEST = constant(gl11Class, "GL_ALPHA_TEST");
		GL_GREATER = constant(gl11Class, "GL_GREATER");
		GL_FRONT_AND_BACK = constant(gl11Class, "GL_FRONT_AND_BACK");
		GL_LINE = constant(gl11Class, "GL_LINE");
		GL_FILL = constant(gl11Class, "GL_FILL");
		GL_LINES = constant(gl11Class, "GL_LINES");
		GL_QUADS = constant(gl11Class, "GL_QUADS");
		GL_TRIANGLES = constant(gl11Class, "GL_TRIANGLES");
		GL_VERTEX_ARRAY = constant(gl11Class, "GL_VERTEX_ARRAY");
		GL_COLOR_ARRAY = constant(gl11Class, "GL_COLOR_ARRAY");
		GL_TEXTURE_COORD_ARRAY = constant(gl11Class, "GL_TEXTURE_COORD_ARRAY");
		GL_FLOAT = constant(gl11Class, "GL_FLOAT");
		GL_UNSIGNED_INT = constant(gl11Class, "GL_UNSIGNED_INT");
		GL_ARRAY_BUFFER = constant(gl15Class, "GL_ARRAY_BUFFER");
		GL_ELEMENT_ARRAY_BUFFER = constant(gl15Class, "GL_ELEMENT_ARRAY_BUFFER");
		GL_STREAM_DRAW = constant(gl15Class, "GL_STREAM_DRAW");
		GL_STATIC_DRAW = constant(gl15Class, "GL_STATIC_DRAW");
		GL_PROJECTION = constant(gl11Class, "GL_PROJECTION");
		GL_MODELVIEW = constant(gl11Class, "GL_MODELVIEW");
		GL_VERTEX_SHADER = constant(gl20Class, "GL_VERTEX_SHADER");
		GL_FRAGMENT_SHADER = constant(gl20Class, "GL_FRAGMENT_SHADER");
		GL_COMPILE_STATUS = constant(gl20Class, "GL_COMPILE_STATUS");
		GL_LINK_STATUS = constant(gl20Class, "GL_LINK_STATUS");
		GL_VENDOR = constant(gl11Class, "GL_VENDOR");
		GL_RENDERER = constant(gl11Class, "GL_RENDERER");
		GL_VERSION = constant(gl11Class, "GL_VERSION");
	}

	boolean glfwInit() throws Exception {
		return ((Boolean) invoke(glfwInit)).booleanValue();
	}

	void glfwTerminate() throws Exception {
		invoke(glfwTerminate);
	}

	void glfwDefaultWindowHints() throws Exception {
		invoke(glfwDefaultWindowHints);
	}

	void glfwWindowHint(int hint, int value) throws Exception {
		invoke(glfwWindowHint, hint, value);
	}

	long glfwCreateWindow(int width, int height, String title, long monitor, long share) throws Exception {
		return ((Long) invoke(glfwCreateWindow, width, height, title, monitor, share)).longValue();
	}

	void glfwMakeContextCurrent(long window) throws Exception {
		invoke(glfwMakeContextCurrent, window);
	}

	void glfwSwapInterval(int interval) throws Exception {
		invoke(glfwSwapInterval, interval);
	}

	void glfwShowWindow(long window) throws Exception {
		invoke(glfwShowWindow, window);
	}

	void glfwHideWindow(long window) throws Exception {
		invoke(glfwHideWindow, window);
	}

	boolean glfwWindowShouldClose(long window) throws Exception {
		return ((Boolean) invoke(glfwWindowShouldClose, window)).booleanValue();
	}

	void glfwPollEvents() throws Exception {
		invoke(glfwPollEvents);
	}

	void glfwSwapBuffers(long window) throws Exception {
		invoke(glfwSwapBuffers, window);
	}

	void glfwDestroyWindow(long window) throws Exception {
		invoke(glfwDestroyWindow, window);
	}

	void glfwSetWindowSize(long window, int width, int height) throws Exception {
		invoke(glfwSetWindowSize, window, width, height);
	}

	void glfwGetWindowPos(long window, int[] x, int[] y) throws Exception {
		invoke(glfwGetWindowPos, window, x, y);
	}

	void glfwSetWindowPos(long window, int x, int y) throws Exception {
		invoke(glfwSetWindowPos, window, x, y);
	}

	void glfwSetWindowAttrib(long window, int attribute, int value) throws Exception {
		invoke(glfwSetWindowAttrib, window, attribute, value);
	}

	MonitorMode getPrimaryMonitorMode() throws Exception {
		long monitor = ((Long) invoke(glfwGetPrimaryMonitor)).longValue();
		if (monitor == 0L) {
			return new MonitorMode(0, 0, RenderSurfaceSettings.getWidth(), RenderSurfaceSettings.getHeight());
		}

		int[] x = new int[1];
		int[] y = new int[1];
		glfwGetMonitorPos(monitor, x, y);
		Object videoMode = invoke(glfwGetVideoMode, monitor);
		if (videoMode == null) {
			return new MonitorMode(x[0], y[0], RenderSurfaceSettings.getWidth(), RenderSurfaceSettings.getHeight());
		}

		return new MonitorMode(
			x[0],
			y[0],
			videoModeInt(videoMode, "width"),
			videoModeInt(videoMode, "height"));
	}

	private void glfwGetMonitorPos(long monitor, int[] x, int[] y) throws Exception {
		invoke(glfwGetMonitorPos, monitor, x, y);
	}

	private int videoModeInt(Object videoMode, String methodName) throws Exception {
		Method method = videoMode.getClass().getMethod(methodName);
		method.setAccessible(true);
		return ((Integer) method.invoke(videoMode)).intValue();
	}

	void glfwSetScrollCallback(long window, Object callback) throws Exception {
		invoke(glfwSetScrollCallback, window, callback);
	}

	Object createScrollCallback(ScrollHandler handler) throws Exception {
		ScrollCallbackInvocationHandler invocationHandler =
			new ScrollCallbackInvocationHandler(this, handler);
		return createCallback(glfwScrollCallbackInterface, glfwScrollCallbackCif, invocationHandler);
	}

	void glfwSetKeyCallback(long window, Object callback) throws Exception {
		invoke(glfwSetKeyCallback, window, callback);
	}

	Object createKeyCallback(GlfwKeyHandler handler) throws Exception {
		KeyCallbackInvocationHandler invocationHandler =
			new KeyCallbackInvocationHandler(this, handler);
		return createCallback(glfwKeyCallbackInterface, glfwKeyCallbackCif, invocationHandler);
	}

	void glfwSetCharCallback(long window, Object callback) throws Exception {
		invoke(glfwSetCharCallback, window, callback);
	}

	Object createCharCallback(CharHandler handler) throws Exception {
		CharCallbackInvocationHandler invocationHandler =
			new CharCallbackInvocationHandler(this, handler);
		return createCallback(glfwCharCallbackInterface, glfwCharCallbackCif, invocationHandler);
	}

	void glfwSetWindowFocusCallback(long window, Object callback) throws Exception {
		invoke(glfwSetWindowFocusCallback, window, callback);
	}

	Object createWindowFocusCallback(WindowFocusHandler handler) throws Exception {
		WindowFocusCallbackInvocationHandler invocationHandler =
			new WindowFocusCallbackInvocationHandler(this, handler);
		return createCallback(
			glfwWindowFocusCallbackInterface,
			glfwWindowFocusCallbackCif,
			invocationHandler);
	}

	private Object createCallback(
		Class<?> callbackInterface,
		Object callbackCif,
		CallbackInvocationHandler invocationHandler) throws Exception {
		Object callback = Proxy.newProxyInstance(
			callbackInterface.getClassLoader(),
			new Class[] {callbackInterface},
			invocationHandler);
		long callbackAddress = ((Long) invoke(callbackCreate, callbackCif, callback)).longValue();
		invocationHandler.setCallbackAddress(callbackAddress);
		return callback;
	}

	void freeCallback(Object callback) throws Exception {
		if (callback == null) {
			return;
		}
		long callbackAddress = ((Long) callback.getClass().getMethod("address").invoke(callback)).longValue();
		if (callbackAddress != 0L) {
			invoke(callbackFree, callbackAddress);
		}
	}

	private long memGetAddress(long address) throws Exception {
		return ((Long) invoke(memGetAddress, address)).longValue();
	}

	private int memGetInt(long address) throws Exception {
		return ((Integer) invoke(memGetInt, address)).intValue();
	}

	private double memGetDouble(long address) throws Exception {
		return ((Double) invoke(memGetDouble, address)).doubleValue();
	}

	void glfwGetWindowSize(long window, int[] width, int[] height) throws Exception {
		invoke(glfwGetWindowSize, window, width, height);
	}

	void glfwGetFramebufferSize(long window, int[] width, int[] height) throws Exception {
		invoke(glfwGetFramebufferSize, window, width, height);
	}

	int glfwGetWindowAttrib(long window, int attribute) throws Exception {
		return ((Integer) invoke(glfwGetWindowAttrib, window, attribute)).intValue();
	}

	void glfwGetCursorPos(long window, double[] x, double[] y) throws Exception {
		invoke(glfwGetCursorPos, window, x, y);
	}

	int glfwGetMouseButton(long window, int button) throws Exception {
		return ((Integer) invoke(glfwGetMouseButton, window, button)).intValue();
	}

	int glfwGetKey(long window, int key) throws Exception {
		return ((Integer) invoke(glfwGetKey, window, key)).intValue();
	}

	int glfwConstant(String name) throws Exception {
		return constant(glfwClass, name);
	}

	void createCapabilities() throws Exception {
		invoke(createCapabilities);
	}

	void glClearColor(float red, float green, float blue, float alpha) throws Exception {
		invoke(glClearColor, red, green, blue, alpha);
	}

	void glClear(int mask) throws Exception {
		invoke(glClear, mask);
	}

	void glViewport(int x, int y, int width, int height) throws Exception {
		invoke(glViewport, x, y, width, height);
	}

	void glEnable(int capability) throws Exception {
		try {
			glEnable.invokeExact(capability);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glDisable(int capability) throws Exception {
		try {
			glDisable.invokeExact(capability);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glScissor(int x, int y, int width, int height) throws Exception {
		invoke(glScissor, x, y, width, height);
	}

	void glPushAttrib(int mask) throws Exception {
		invoke(glPushAttrib, mask);
	}

	void glPopAttrib() throws Exception {
		invoke(glPopAttrib);
	}

	void glPushClientAttrib(int mask) throws Exception {
		invoke(glPushClientAttrib, mask);
	}

	void glPopClientAttrib() throws Exception {
		invoke(glPopClientAttrib);
	}

	void glPolygonOffset(float factor, float units) throws Exception {
		invoke(glPolygonOffset, factor, units);
	}

	void glPolygonMode(int face, int mode) throws Exception {
		invoke(glPolygonMode, face, mode);
	}

	void glCullFace(int mode) throws Exception {
		invoke(glCullFace, mode);
	}

	int glGenTextures() throws Exception {
		return ((Integer) invoke(glGenTextures)).intValue();
	}

	void glDeleteTextures(int texture) throws Exception {
		if (texture != 0) {
			invoke(glDeleteTextures, texture);
		}
	}

	void glActiveTexture(int textureUnit) throws Exception {
		invoke(glActiveTexture, textureUnit);
	}

	void glBindTexture(int target, int texture) throws Exception {
		try {
			glBindTexture.invokeExact(target, texture);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glTexParameteri(int target, int name, int value) throws Exception {
		try {
			glTexParameteri.invokeExact(target, name, value);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glTexImage2D(
		int target,
		int level,
		int internalFormat,
		int width,
		int height,
		int border,
		int format,
		int type,
		ByteBuffer pixels) throws Exception {
		invoke(glTexImage2D, target, level, internalFormat, width, height, border, format, type, pixels);
	}

	void glTexSubImage2D(
		int target,
		int level,
		int xOffset,
		int yOffset,
		int width,
		int height,
		int format,
		int type,
		ByteBuffer pixels) throws Exception {
		invoke(glTexSubImage2D, target, level, xOffset, yOffset, width, height, format, type, pixels);
	}

	void glReadPixels(
		int x,
		int y,
		int width,
		int height,
		int format,
		int type,
		ByteBuffer pixels) throws Exception {
		invoke(glReadPixels, x, y, width, height, format, type, pixels);
	}

	void glBlendFunc(int sourceFactor, int destinationFactor) throws Exception {
		try {
			glBlendFunc.invokeExact(sourceFactor, destinationFactor);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glAlphaFunc(int function, float reference) throws Exception {
		invoke(glAlphaFunc, function, reference);
	}

	void glDepthMask(boolean flag) throws Exception {
		invoke(glDepthMask, flag);
	}

	void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) throws Exception {
		invoke(glColorMask, red, green, blue, alpha);
	}

	void glColor4f(float red, float green, float blue, float alpha) throws Exception {
		try {
			glColor4f.invokeExact(red, green, blue, alpha);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glFogi(int name, int value) throws Exception {
		invoke(glFogi, name, value);
	}

	void glFogf(int name, float value) throws Exception {
		invoke(glFogf, name, value);
	}

	void glFogfv(int name, FloatBuffer values) throws Exception {
		invoke(glFogfv, name, values);
	}

	String glGetString(int name) throws Exception {
		return (String) invoke(glGetString, name);
	}

	void glMatrixMode(int mode) throws Exception {
		invoke(glMatrixMode, mode);
	}

	void glLoadIdentity() throws Exception {
		invoke(glLoadIdentity);
	}

	void glLoadMatrixf(FloatBuffer matrix) throws Exception {
		invoke(glLoadMatrixf, matrix);
	}

	void glOrtho(double left, double right, double bottom, double top, double near, double far)
		throws Exception {
		invoke(glOrtho, left, right, bottom, top, near, far);
	}

	int glGenBuffers() throws Exception {
		return ((Integer) invoke(glGenBuffers)).intValue();
	}

	void glDeleteBuffers(int buffer) throws Exception {
		if (buffer != 0) {
			invoke(glDeleteBuffers, buffer);
		}
	}

	void glBindBuffer(int target, int buffer) throws Exception {
		try {
			glBindBuffer.invokeExact(target, buffer);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glBufferData(int target, FloatBuffer data, int usage) throws Exception {
		try {
			glBufferDataFloat.invokeExact(target, data, usage);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glBufferData(int target, IntBuffer data, int usage) throws Exception {
		try {
			glBufferDataInt.invokeExact(target, data, usage);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glEnableClientState(int array) throws Exception {
		try {
			glEnableClientState.invokeExact(array);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glDisableClientState(int array) throws Exception {
		try {
			glDisableClientState.invokeExact(array);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glVertexPointer(int size, int type, int stride, long pointer) throws Exception {
		try {
			glVertexPointer.invokeExact(size, type, stride, pointer);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glColorPointer(int size, int type, int stride, long pointer) throws Exception {
		try {
			glColorPointer.invokeExact(size, type, stride, pointer);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glTexCoordPointer(int size, int type, int stride, long pointer) throws Exception {
		try {
			glTexCoordPointer.invokeExact(size, type, stride, pointer);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glDrawElements(int mode, int count, int type, long indices) throws Exception {
		try {
			glDrawElements.invokeExact(mode, count, type, indices);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glBegin(int mode) throws Exception {
		invoke(glBegin, mode);
	}

	void glEnd() throws Exception {
		invoke(glEnd);
	}

	void glTexCoord2f(float s, float t) throws Exception {
		invoke(glTexCoord2f, s, t);
	}

	void glVertex3f(float x, float y, float z) throws Exception {
		try {
			glVertex3f.invokeExact(x, y, z);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glLineWidth(float width) throws Exception {
		invoke(glLineWidth, width);
	}

	int glCreateShader(int type) throws Exception {
		return ((Integer) invoke(glCreateShader, type)).intValue();
	}

	void glShaderSource(int shader, CharSequence source) throws Exception {
		invoke(glShaderSource, shader, source);
	}

	void glCompileShader(int shader) throws Exception {
		invoke(glCompileShader, shader);
	}

	int glGetShaderi(int shader, int pname) throws Exception {
		return ((Integer) invoke(glGetShaderi, shader, pname)).intValue();
	}

	String glGetShaderInfoLog(int shader) throws Exception {
		return (String) invoke(glGetShaderInfoLog, shader);
	}

	void glDeleteShader(int shader) throws Exception {
		if (shader != 0) {
			invoke(glDeleteShader, shader);
		}
	}

	int glCreateProgram() throws Exception {
		return ((Integer) invoke(glCreateProgram)).intValue();
	}

	void glAttachShader(int program, int shader) throws Exception {
		invoke(glAttachShader, program, shader);
	}

	void glBindAttribLocation(int program, int index, CharSequence name) throws Exception {
		invoke(glBindAttribLocation, program, index, name);
	}

	void glLinkProgram(int program) throws Exception {
		invoke(glLinkProgram, program);
	}

	int glGetProgrami(int program, int pname) throws Exception {
		return ((Integer) invoke(glGetProgrami, program, pname)).intValue();
	}

	String glGetProgramInfoLog(int program) throws Exception {
		return (String) invoke(glGetProgramInfoLog, program);
	}

	void glUseProgram(int program) throws Exception {
		invoke(glUseProgram, program);
	}

	int glGetUniformLocation(int program, CharSequence name) throws Exception {
		return ((Integer) invoke(glGetUniformLocation, program, name)).intValue();
	}

	void glUniform1i(int location, int value) throws Exception {
		invoke(glUniform1i, location, value);
	}

	void glUniform1f(int location, float value) throws Exception {
		invoke(glUniform1f, location, value);
	}

	void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) throws Exception {
		invoke(glUniformMatrix4fv, location, transpose, value);
	}

	void glEnableVertexAttribArray(int index) throws Exception {
		try {
			glEnableVertexAttribArray.invokeExact(index);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glDisableVertexAttribArray(int index) throws Exception {
		try {
			glDisableVertexAttribArray.invokeExact(index);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glVertexAttribPointer(
		int index,
		int size,
		int type,
		boolean normalized,
		int stride,
		long pointer) throws Exception {
		try {
			glVertexAttribPointer.invokeExact(
				index,
				size,
				type,
				normalized,
				stride,
				pointer);
		} catch (Throwable cause) {
			rethrow(cause);
		}
	}

	void glDeleteProgram(int program) throws Exception {
		if (program != 0) {
			invoke(glDeleteProgram, program);
		}
	}

	private abstract static class CallbackInvocationHandler implements java.lang.reflect.InvocationHandler {
		private final LwjglBindings gl;
		private final Object callbackCif;
		private long callbackAddress;

		private CallbackInvocationHandler(LwjglBindings gl, Object callbackCif) {
			this.gl = gl;
			this.callbackCif = callbackCif;
		}

		private void setCallbackAddress(long callbackAddress) {
			this.callbackAddress = callbackAddress;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = method.getName();
			if ("address".equals(methodName)) {
				return Long.valueOf(callbackAddress);
			}
			if ("getCallInterface".equals(methodName)) {
				return callbackCif;
			}
			if ("callback".equals(methodName) && args != null && args.length == 2) {
				dispatchNativeCallback(((Long) args[1]).longValue());
				return null;
			}
			if ("invoke".equals(methodName)) {
				return dispatchDirectCallback(args);
			}
			if ("hashCode".equals(methodName)) {
				return Integer.valueOf(System.identityHashCode(proxy));
			}
			if ("equals".equals(methodName)) {
				return Boolean.valueOf(proxy == args[0]);
			}
			if ("toString".equals(methodName)) {
				return description();
			}
			return null;
		}

		protected abstract Object dispatchDirectCallback(Object[] args) throws Exception;

		protected abstract void dispatchNativeCallback(long argsAddress) throws Exception;

		protected abstract String description();
	}

	private static final class ScrollCallbackInvocationHandler extends CallbackInvocationHandler {
		private final LwjglBindings gl;
		private final ScrollHandler handler;

		private ScrollCallbackInvocationHandler(LwjglBindings gl, ScrollHandler handler) {
			super(gl, gl.glfwScrollCallbackCif);
			this.gl = gl;
			this.handler = handler;
		}

		@Override
		protected Object dispatchDirectCallback(Object[] args) {
			if (args != null && args.length == 3) {
				handler.handle(
					((Long) args[0]).longValue(),
					((Double) args[1]).doubleValue(),
					((Double) args[2]).doubleValue());
			}
			return null;
		}

		@Override
		protected void dispatchNativeCallback(long argsAddress) throws Exception {
			long window = gl.memGetAddress(gl.memGetAddress(argsAddress));
			double xOffset = gl.memGetDouble(gl.memGetAddress(argsAddress + gl.POINTER_SIZE));
			double yOffset = gl.memGetDouble(gl.memGetAddress(argsAddress + 2L * gl.POINTER_SIZE));
			handler.handle(window, xOffset, yOffset);
		}

		@Override
		protected String description() {
			return "Spoiled Milk GLFW scroll callback";
		}
	}

	private static final class KeyCallbackInvocationHandler extends CallbackInvocationHandler {
		private final LwjglBindings gl;
		private final GlfwKeyHandler handler;

		private KeyCallbackInvocationHandler(LwjglBindings gl, GlfwKeyHandler handler) {
			super(gl, gl.glfwKeyCallbackCif);
			this.gl = gl;
			this.handler = handler;
		}

		@Override
		protected Object dispatchDirectCallback(Object[] args) {
			if (args != null && args.length == 5) {
				handler.handle(
					((Long) args[0]).longValue(),
					((Integer) args[1]).intValue(),
					((Integer) args[2]).intValue(),
					((Integer) args[3]).intValue(),
					((Integer) args[4]).intValue());
			}
			return null;
		}

		@Override
		protected void dispatchNativeCallback(long argsAddress) throws Exception {
			handler.handle(
				gl.memGetAddress(gl.memGetAddress(argsAddress)),
				gl.memGetInt(gl.memGetAddress(argsAddress + gl.POINTER_SIZE)),
				gl.memGetInt(gl.memGetAddress(argsAddress + 2L * gl.POINTER_SIZE)),
				gl.memGetInt(gl.memGetAddress(argsAddress + 3L * gl.POINTER_SIZE)),
				gl.memGetInt(gl.memGetAddress(argsAddress + 4L * gl.POINTER_SIZE)));
		}

		@Override
		protected String description() {
			return "Spoiled Milk GLFW key callback";
		}
	}

	private static final class CharCallbackInvocationHandler extends CallbackInvocationHandler {
		private final LwjglBindings gl;
		private final CharHandler handler;

		private CharCallbackInvocationHandler(LwjglBindings gl, CharHandler handler) {
			super(gl, gl.glfwCharCallbackCif);
			this.gl = gl;
			this.handler = handler;
		}

		@Override
		protected Object dispatchDirectCallback(Object[] args) {
			if (args != null && args.length == 2) {
				handler.handle(((Long) args[0]).longValue(), ((Integer) args[1]).intValue());
			}
			return null;
		}

		@Override
		protected void dispatchNativeCallback(long argsAddress) throws Exception {
			handler.handle(
				gl.memGetAddress(gl.memGetAddress(argsAddress)),
				gl.memGetInt(gl.memGetAddress(argsAddress + gl.POINTER_SIZE)));
		}

		@Override
		protected String description() {
			return "Spoiled Milk GLFW char callback";
		}
	}

	private static final class WindowFocusCallbackInvocationHandler extends CallbackInvocationHandler {
		private final LwjglBindings gl;
		private final WindowFocusHandler handler;

		private WindowFocusCallbackInvocationHandler(LwjglBindings gl, WindowFocusHandler handler) {
			super(gl, gl.glfwWindowFocusCallbackCif);
			this.gl = gl;
			this.handler = handler;
		}

		@Override
		protected Object dispatchDirectCallback(Object[] args) {
			if (args != null && args.length == 2) {
				handler.handle(((Long) args[0]).longValue(), ((Boolean) args[1]).booleanValue());
			}
			return null;
		}

		@Override
		protected void dispatchNativeCallback(long argsAddress) throws Exception {
			handler.handle(
				gl.memGetAddress(gl.memGetAddress(argsAddress)),
				gl.memGetInt(gl.memGetAddress(argsAddress + gl.POINTER_SIZE)) != 0);
		}

		@Override
		protected String description() {
			return "Spoiled Milk GLFW window focus callback";
		}
	}

	private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
		Method method = type.getMethod(name, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes)
		throws Exception {
		Method method = type.getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	private static MethodHandle methodHandle(
		Class<?> type,
		String name,
		Class<?>... parameterTypes) throws Exception {
		return MethodHandles.publicLookup().unreflect(method(type, name, parameterTypes));
	}

	private static int constant(Class<?> type, String name) throws Exception {
		Field field = type.getField(name);
		field.setAccessible(true);
		return ((Integer) field.get(null)).intValue();
	}

	private static int optionalConstant(Class<?> type, String name, int fallback) throws Exception {
		return type == null ? fallback : constant(type, name);
	}

	private static Class<?> optionalClass(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	private static Object fieldValue(Class<?> type, String name) throws Exception {
		Field field = type.getField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static Object invoke(Method method, Object... arguments) throws Exception {
		try {
			return method.invoke(null, arguments);
		} catch (InvocationTargetException e) {
			rethrow(e.getCause());
			return null;
		}
	}

	private static void rethrow(Throwable cause) throws Exception {
		if (cause instanceof Exception) {
			throw (Exception) cause;
		}
		if (cause instanceof Error) {
			throw (Error) cause;
		}
		throw new RuntimeException(cause);
	}
}
