package orsc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/** Optional desktop GL3 shadow/measurement functions; no native dependency at class load. */
final class OpenGLShadowBindings {
	static final int FRAMEBUFFER = 0x8D40, DRAW_FRAMEBUFFER = 0x8CA9, READ_FRAMEBUFFER = 0x8CA8;
	static final int DRAW_FRAMEBUFFER_BINDING = 0x8CA6, READ_FRAMEBUFFER_BINDING = 0x8CAA;
	static final int DEPTH_ATTACHMENT = 0x8D00, FRAMEBUFFER_COMPLETE = 0x8CD5;
	static final int DEPTH_COMPONENT = 0x1902, DEPTH_COMPONENT24 = 0x81A6;
	static final int TEXTURE_COMPARE_MODE = 0x884C, COMPARE_REF_TO_TEXTURE = 0x884E;
	static final int TEXTURE_COMPARE_FUNC = 0x884D, LEQUAL = 0x0203;
	static final int VERTEX_ARRAY_BINDING = 0x85B5, CURRENT_PROGRAM = 0x8B8D;
	static final int ARRAY_BUFFER_BINDING = 0x8894, ACTIVE_TEXTURE = 0x84E0;
	static final int TIMESTAMP = 0x8E28, QUERY_RESULT = 0x8866, QUERY_RESULT_AVAILABLE = 0x8867;
	private final MethodHandle genFramebuffer, deleteFramebuffer, bindFramebuffer, attachTexture, framebufferStatus;
	private final MethodHandle genVertexArray, deleteVertexArray, bindVertexArray, getInteger, drawBuffer, readBuffer;
	private final MethodHandle depthFunction, clearDepth, depthRange;
	private final MethodHandle genQuery, deleteQuery, queryCounter, queryAvailable, queryResult;
	final boolean timerSupported;

	static OpenGLShadowBindings create() throws Exception {
		Object caps = Class.forName("org.lwjgl.opengl.GL").getMethod("getCapabilities").invoke(null);
		if (!caps.getClass().getField("OpenGL30").getBoolean(caps)) return null;
		boolean timer = caps.getClass().getField("OpenGL33").getBoolean(caps)
			|| caps.getClass().getField("GL_ARB_timer_query").getBoolean(caps);
		return new OpenGLShadowBindings(timer);
	}

	private OpenGLShadowBindings(boolean timer) throws Exception {
		Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
		Class<?> gl15 = Class.forName("org.lwjgl.opengl.GL15");
		Class<?> gl30 = Class.forName("org.lwjgl.opengl.GL30");
		Class<?> gl33 = Class.forName("org.lwjgl.opengl.GL33");
		genFramebuffer = handle(gl30, "glGenFramebuffers");
		deleteFramebuffer = handle(gl30, "glDeleteFramebuffers", int.class);
		bindFramebuffer = handle(gl30, "glBindFramebuffer", int.class, int.class);
		attachTexture = handle(gl30, "glFramebufferTexture2D", int.class, int.class, int.class, int.class, int.class);
		framebufferStatus = handle(gl30, "glCheckFramebufferStatus", int.class);
		genVertexArray = handle(gl30, "glGenVertexArrays");
		deleteVertexArray = handle(gl30, "glDeleteVertexArrays", int.class);
		bindVertexArray = handle(gl30, "glBindVertexArray", int.class);
		getInteger = handle(gl11, "glGetInteger", int.class);
		drawBuffer = handle(gl11, "glDrawBuffer", int.class);
		readBuffer = handle(gl11, "glReadBuffer", int.class);
		depthFunction = handle(gl11, "glDepthFunc", int.class);
		clearDepth = handle(gl11, "glClearDepth", double.class);
		depthRange = handle(gl11, "glDepthRange", double.class, double.class);
		timerSupported = timer;
		genQuery = timer ? handle(gl15, "glGenQueries") : null;
		deleteQuery = timer ? handle(gl15, "glDeleteQueries", int.class) : null;
		queryCounter = timer ? handle(gl33, "glQueryCounter", int.class, int.class) : null;
		queryAvailable = timer ? handle(gl15, "glGetQueryObjecti", int.class, int.class) : null;
		queryResult = timer ? handle(gl33, "glGetQueryObjecti64", int.class, int.class) : null;
	}
	private static MethodHandle handle(Class<?> owner, String name, Class<?>... args) throws Exception {
		Method method = owner.getMethod(name, args);
		return MethodHandles.publicLookup().unreflect(method);
	}
	private static Exception failure(Throwable error) {
		if (error instanceof Error) throw (Error) error;
		return error instanceof Exception ? (Exception) error : new Exception(error);
	}
	int genFramebuffer() throws Exception { try { return (int) genFramebuffer.invokeExact(); } catch (Throwable e) { throw failure(e); } }
	void deleteFramebuffer(int id) throws Exception { try { deleteFramebuffer.invokeExact(id); } catch (Throwable e) { throw failure(e); } }
	void bindFramebuffer(int target, int id) throws Exception { try { bindFramebuffer.invokeExact(target, id); } catch (Throwable e) { throw failure(e); } }
	void attachTexture(int texture) throws Exception { try { attachTexture.invokeExact(FRAMEBUFFER, DEPTH_ATTACHMENT, 0x0DE1, texture, 0); } catch (Throwable e) { throw failure(e); } }
	boolean framebufferComplete() throws Exception { try { return (int) framebufferStatus.invokeExact(FRAMEBUFFER) == FRAMEBUFFER_COMPLETE; } catch (Throwable e) { throw failure(e); } }
	int genVertexArray() throws Exception { try { return (int) genVertexArray.invokeExact(); } catch (Throwable e) { throw failure(e); } }
	void deleteVertexArray(int id) throws Exception { try { deleteVertexArray.invokeExact(id); } catch (Throwable e) { throw failure(e); } }
	void bindVertexArray(int id) throws Exception { try { bindVertexArray.invokeExact(id); } catch (Throwable e) { throw failure(e); } }
	int integer(int name) throws Exception { try { return (int) getInteger.invokeExact(name); } catch (Throwable e) { throw failure(e); } }
	void noColorBuffers() throws Exception { try { drawBuffer.invokeExact(0); readBuffer.invokeExact(0); } catch (Throwable e) { throw failure(e); } }
	void depthDefaults() throws Exception { try { depthFunction.invokeExact(0x0201); clearDepth.invokeExact(1.0d); depthRange.invokeExact(0.0d, 1.0d); } catch (Throwable e) { throw failure(e); } }
	int genQuery() throws Exception { try { return (int) genQuery.invokeExact(); } catch (Throwable e) { throw failure(e); } }
	void deleteQuery(int id) throws Exception { try { deleteQuery.invokeExact(id); } catch (Throwable e) { throw failure(e); } }
	void timestamp(int id) throws Exception { try { queryCounter.invokeExact(id, TIMESTAMP); } catch (Throwable e) { throw failure(e); } }
	boolean available(int id) throws Exception { try { return (int) queryAvailable.invokeExact(id, QUERY_RESULT_AVAILABLE) != 0; } catch (Throwable e) { throw failure(e); } }
	long result(int id) throws Exception { try { return (long) queryResult.invokeExact(id, QUERY_RESULT); } catch (Throwable e) { throw failure(e); } }
}
