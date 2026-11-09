package orsc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Geometry-depth sun shadows. The resident renderer owns caster submission. */
final class OpenGLGeometryShadows implements AutoCloseable {
	interface Scene {
		long staticSignature();
		default long historyContext() { return 0; }
        default int receiverPlane() { return 0; }
        default double referenceHeight() { return Double.NaN; }
		default GeometryShadowRoofMask roofMask() { return GeometryShadowRoofMask.EMPTY; }
		void drawDepth(OpenGLGeometryShadows pass, DirectionalShadowProjection projection, boolean staticOnly) throws Exception;
	}
	static final String VERTEX_DECLARATIONS = "varying vec3 vShadowWorldPosition;\nuniform float uGeometryShadowWorldOffsetX;\nuniform float uGeometryShadowWorldOffsetZ;\n";
	static final String VERTEX_ASSIGNMENT =
		"vShadowWorldPosition = worldPosition.xyz + vec3(uGeometryShadowWorldOffsetX, 0.0, uGeometryShadowWorldOffsetZ);\n";
	static final String FRAGMENT_SOURCE = String.join("\n",
		"varying vec3 vShadowWorldPosition;",
		"uniform int uGeometryShadows;",
		"uniform int uGeometryRoofReceivers;",
		"uniform sampler2D uGeometryRoofMask;",
		"uniform float uGeometryRoofMinX;",
		"uniform float uGeometryRoofMinZ;",
		"uniform float uGeometryRoofInvSpanX;",
		"uniform float uGeometryRoofInvSpanZ;",
		"uniform int uFarGeometryShadow;",
        "uniform int uPreviousFarGeometryShadow;",
        "uniform sampler2DShadow uPreviousFarGeometryShadowMap;",
        "uniform mat4 uPreviousFarGeometryLight;",
        "uniform float uFarGeometryBlend;",
		"uniform int uGeometryShadowRadius;",
		"uniform sampler2DShadow uNearGeometryShadow;",
		"uniform sampler2DShadow uFarGeometryShadowMap;",
		"uniform mat4 uNearGeometryLight;",
		"uniform mat4 uFarGeometryLight;",
		"uniform float uNearGeometryTexel;",
		"uniform float uFarGeometryTexel;",
		"uniform float uNearGeometryWorldTexel;",
		"uniform float uFarGeometryWorldTexel;",
		"float geometryShadowSample(sampler2DShadow shadowMap, mat4 lightMatrix, float texel, float worldTexel, vec3 normal) {",
		"  vec3 at = (lightMatrix * vec4(vShadowWorldPosition + normal * worldTexel * 1.5, 1.0)).xyz * 0.5 + 0.5;",
		"  if (min(at.x, at.y) <= 0.002 || max(at.x, at.y) >= 0.998 || at.z <= 0.0 || at.z >= 1.0) return 1.0;",
		"  float value = 0.0; float count = 0.0;",
		"  for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {",
		"    if (x >= -uGeometryShadowRadius && x <= uGeometryShadowRadius && y >= -uGeometryShadowRadius && y <= uGeometryShadowRadius) {",
		"      value += shadow2D(shadowMap, vec3(at.xy + vec2(float(x), float(y)) * texel, at.z - 0.000015)).r;",
		"      count += 1.0;",
		"    }",
		"  }",
		"  return value / max(1.0, count);",
		"}",
		"float geometryShadowVisibility() {",
		"  if (uGeometryShadows == 0) return 1.0;",
		"  if (uGeometryRoofReceivers != 0 && ((vModelKind > 0.5 && vModelKind < 1.5) || vModelKind > 3.5)) {",
		"    vec2 roofUv = (vShadowWorldPosition.xz - vec2(uGeometryRoofMinX, uGeometryRoofMinZ)) * vec2(uGeometryRoofInvSpanX, uGeometryRoofInvSpanZ);",
		"    if (min(roofUv.x, roofUv.y) >= 0.0 && max(roofUv.x, roofUv.y) < 1.0 && texture2D(uGeometryRoofMask, roofUv).r > 0.5) return 0.0;",
		"  }",
		"  vec3 geometricNormal = cross(dFdx(vShadowWorldPosition), dFdy(vShadowWorldPosition));",
		"  float magnitude = length(geometricNormal);",
		"  geometricNormal = magnitude > 0.00001 ? geometricNormal / magnitude : vec3(0.0, -1.0, 0.0);",
		"  vec3 physicalSun = vec3(uLightDirectionX, -uLightDirectionY, uLightDirectionZ);",
		// Native terrain is a heightfield: outward is physical Y-up (negative Y).
		// Keep the bias normal outward. Sun-relative terrain diffuse handles the
		// smooth lighting terminator; a face-wide zero here creates tile silhouettes.
		"  if (vModelKind > 0.5 && vModelKind < 1.5) {",
		"    if (geometricNormal.y > 0.0) geometricNormal = -geometricNormal;",
		"  } else if (dot(geometricNormal, physicalSun) < 0.0) geometricNormal = -geometricNormal;",
		"  float nearVisibility = geometryShadowSample(uNearGeometryShadow, uNearGeometryLight, uNearGeometryTexel, uNearGeometryWorldTexel, geometricNormal);",
		"  vec3 nearAt = (uNearGeometryLight * vec4(vShadowWorldPosition, 1.0)).xyz;",
		"  float nearEdge = max(abs(nearAt.x), abs(nearAt.y));",
		"  float blend = smoothstep(0.78, 0.94, nearEdge);",
		"  if (uFarGeometryShadow == 0) return mix(nearVisibility, 1.0, blend);",
		"  float farVisibility = geometryShadowSample(uFarGeometryShadowMap, uFarGeometryLight, uFarGeometryTexel, uFarGeometryWorldTexel, geometricNormal);",
		"  if (uPreviousFarGeometryShadow != 0) {",
        "    float previousVisibility = geometryShadowSample(uPreviousFarGeometryShadowMap, uPreviousFarGeometryLight, uFarGeometryTexel, uFarGeometryWorldTexel, geometricNormal);",
        "    farVisibility = mix(previousVisibility, farVisibility, uFarGeometryBlend);",
        "  }",
        "  return mix(nearVisibility, farVisibility, blend);",
		"}", "");
	private static final String DEPTH_VERTEX = String.join("\n",
		"#version 120", "attribute vec3 aPosition;", "attribute vec2 aTexCoord;",
		"uniform mat4 uLightMatrix;", "uniform float uGlobalOffsetX;", "uniform float uGlobalOffsetZ;",
		"varying vec2 vTexCoord;", "void main() {",
		"gl_Position = uLightMatrix * vec4(aPosition + vec3(uGlobalOffsetX, 0.0, uGlobalOffsetZ), 1.0);",
		"vTexCoord = aTexCoord;", "}", "");
	private static final String DEPTH_FRAGMENT = String.join("\n",
		"#version 120", "uniform sampler2D uTexture;", "uniform int uAlphaTest;",
		"varying vec2 vTexCoord;", "void main() {",
		"if (uAlphaTest != 0 && texture2D(uTexture, vTexCoord).a <= 0.5) discard;",
		"}", "");
	private static final int FAR_SIZE = 4096;
    static final int FAR_BLEND_FRAMES = 12;
    static int supportedFarSize(int maximumTextureSize) { return maximumTextureSize >= FAR_SIZE ? FAR_SIZE : 2048; }
	private static final float FAR_HALF = 96.0f * 128.0f;
	private static final float FAR_MOVE = 4.0f * 128.0f;
	private static final double FAR_SUN_COSINE = Math.cos(Math.toRadians(0.08));
	private final LwjglBindings gl;
	private final OpenGLShadowBindings functions;
	private final FloatBuffer matrixBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
	private final MapTexture near = new MapTexture();
    private MapTexture far = new MapTexture(), previousFar = new MapTexture();
    private final int farSize;
	private final int program, lightUniform, offsetXUniform, offsetZUniform, alphaUniform;
	private final int vao;
	private final OpenGLGpuTimer nearTimer, farTimer;
	private DirectionalShadowProjection nearProjection, farProjection, previousFarProjection;
	private RendererShadowSettings.Quality quality;
	private int receiverProgram;
	private int roofTexture, roofReceiverUniform, roofMinXUniform, roofMinZUniform, roofInvSpanXUniform, roofInvSpanZUniform;
	private long uploadedRoofSignature;
	private GeometryShadowRoofMask roofMask=GeometryShadowRoofMask.EMPTY;
	int roofUploads;
	private int nearMatrixUniform, farMatrixUniform, enabledUniform, farEnabledUniform, radiusUniform;
	private int nearTexelUniform, farTexelUniform, nearWorldTexelUniform, farWorldTexelUniform;
	private long farSignature, farContext, farRoofSignature;
    private int farPlane;
    private long nearContext;
    private int nearPlane;
    private int farBlendAge;
    private int previousFarEnabledUniform, previousFarMatrixUniform, farBlendUniform;
	private int farAge;
	private boolean active;
	private boolean closed;
	long nearCpuNanos, farCpuNanos;
	int nearDrawCalls, nearTriangles, farDrawCalls, farTriangles;
	private int currentDrawCalls, currentTriangles;
	boolean farRebuilt;
	private final java.util.concurrent.locks.ReentrantLock diagnosticLock = new java.util.concurrent.locks.ReentrantLock();
	private long diagnosticSequence;
	private boolean diagnosticActive, diagnosticFarRebuilt;
    private boolean diagnosticPreviousFar;
    private int diagnosticFarBlendPermille;
	private long diagnosticNearCpu, diagnosticFarCpu;
	private int diagnosticNearDraws, diagnosticNearTriangles, diagnosticFarDraws, diagnosticFarTriangles;

	OpenGLGeometryShadows(LwjglBindings gl, OpenGLShadowBindings functions) throws Exception {
		this.gl = gl; this.functions = functions;
        farSize = supportedFarSize(functions.integer(0x0D33));
		int vertex = 0, fragment = 0, madeProgram = 0, madeVao = 0;
		OpenGLGpuTimer madeNearTimer = null, madeFarTimer = null;
		try {
			vertex = compile(gl.GL_VERTEX_SHADER, DEPTH_VERTEX);
			fragment = compile(gl.GL_FRAGMENT_SHADER, DEPTH_FRAGMENT);
			madeProgram = gl.glCreateProgram();
			gl.glAttachShader(madeProgram, vertex); gl.glAttachShader(madeProgram, fragment);
			gl.glBindAttribLocation(madeProgram, 0, "aPosition"); gl.glBindAttribLocation(madeProgram, 1, "aTexCoord");
			gl.glLinkProgram(madeProgram);
			if (gl.glGetProgrami(madeProgram, gl.GL_LINK_STATUS) == 0) {
				throw new IllegalStateException("Shadow depth program link: " + gl.glGetProgramInfoLog(madeProgram));
			}
			lightUniform = gl.glGetUniformLocation(madeProgram, "uLightMatrix");
			offsetXUniform = gl.glGetUniformLocation(madeProgram, "uGlobalOffsetX");
			offsetZUniform = gl.glGetUniformLocation(madeProgram, "uGlobalOffsetZ");
			alphaUniform = gl.glGetUniformLocation(madeProgram, "uAlphaTest");
			madeVao = functions.genVertexArray();
			boolean timings = Boolean.getBoolean("spoiledmilk.shadowGpuTimings");
			madeNearTimer = new OpenGLGpuTimer(timings ? functions : null, "shadow-near");
			madeFarTimer = new OpenGLGpuTimer(timings ? functions : null, "shadow-far");
			program = madeProgram; vao = madeVao; nearTimer = madeNearTimer; farTimer = madeFarTimer;
		} catch (Exception error) {
			if (madeNearTimer != null) madeNearTimer.close();
			if (madeFarTimer != null) madeFarTimer.close();
			if (madeVao != 0) functions.deleteVertexArray(madeVao);
			if (madeProgram != 0) gl.glDeleteProgram(madeProgram);
			throw error;
		} finally {
			if (vertex != 0) gl.glDeleteShader(vertex);
			if (fragment != 0) gl.glDeleteShader(fragment);
		}
	}

	private int compile(int type, String source) throws Exception {
		int shader = gl.glCreateShader(type);
		try {
			gl.glShaderSource(shader, source); gl.glCompileShader(shader);
			if (gl.glGetShaderi(shader, gl.GL_COMPILE_STATUS) == 0) {
				throw new IllegalStateException("Shadow shader compile: " + gl.glGetShaderInfoLog(shader));
			}
			return shader;
		} catch (Exception error) { gl.glDeleteShader(shader); throw error; }
	}

	boolean prepare(Scene scene, double centerX, double centerY, double centerZ) throws Exception {
		if (closed) throw new IllegalStateException("Shadow resources already closed");
		pollGpuTimers();
		clearPrepared();
        RendererShadowSettings.Quality nextQuality = RendererShadowSettings.getQuality();
        if (quality != nextQuality) { invalidateFarHistory(); farProjection = null; nearProjection = null; }
        quality = nextQuality;
        if (quality == RendererShadowSettings.Quality.OFF) { suspend(); publishDiagnosticFrame(); return false; }
        if (previousFarProjection != null && ++farBlendAge >= FAR_BLEND_FRAMES) previousFarProjection = null;
		float lx = RendererRemasterLightSettings.getLightDirectionX();
		float ly = RendererRemasterLightSettings.getLightDirectionY();
		float lz = RendererRemasterLightSettings.getLightDirectionZ();
        long context = scene.historyContext();
        int plane = scene.receiverPlane();
        DirectionalShadowProjection previousNear = nearProjection;
        if (previousNear != null && (nearContext != context || nearPlane != plane
            || Math.hypot(Math.hypot(centerX-previousNear.centerX,centerZ-previousNear.centerZ),centerY-previousNear.centerY) > quality.nearRadiusTiles*128.0)) previousNear=null;
        int nearSize = quality.nearSize;
        double referenceHeight = scene.referenceHeight();
        nearProjection = quality == RendererShadowSettings.Quality.HIGH
            ? new DirectionalShadowProjection(centerX, centerY, centerZ, lx, ly, lz,
                quality.nearRadiusTiles * 128.0f, nearSize, previousNear,
                Double.isFinite(referenceHeight) ? referenceHeight : centerY)
            : new DirectionalShadowProjection(centerX, centerY, centerZ, lx, ly, lz,
                quality.nearRadiusTiles * 128.0f, nearSize, previousNear);
        nearContext=context; nearPlane=plane;
        long signature = scene.staticSignature();
        roofMask = scene.roofMask();
        farAge++;
		farRebuilt = quality == RendererShadowSettings.Quality.HIGH
			&& (farProjection == null || farSignature != signature || farContext != context || farPlane != plane || farRoofSignature != roofMask.signature || farAge >= 150
				|| Math.hypot(centerX - farProjection.centerX, centerZ - farProjection.centerZ) >= FAR_MOVE
				|| lx * farProjection.lightX - ly * farProjection.lightY + lz * farProjection.lightZ < FAR_SUN_COSINE);
		int oldDrawFbo = functions.integer(OpenGLShadowBindings.DRAW_FRAMEBUFFER_BINDING);
		int oldReadFbo = functions.integer(OpenGLShadowBindings.READ_FRAMEBUFFER_BINDING);
		int oldVao = functions.integer(OpenGLShadowBindings.VERTEX_ARRAY_BINDING);
		int oldArray = functions.integer(OpenGLShadowBindings.ARRAY_BUFFER_BINDING);
		int oldProgram = functions.integer(OpenGLShadowBindings.CURRENT_PROGRAM);
		int oldTexture = functions.integer(OpenGLShadowBindings.ACTIVE_TEXTURE);
		gl.glPushAttrib(gl.GL_ALL_ATTRIB_BITS);
		try {
			functions.bindVertexArray(vao);
			gl.glUseProgram(program);
			gl.glUniform1i(gl.glGetUniformLocation(program, "uTexture"), 0);
			gl.glActiveTexture(gl.GL_TEXTURE0);
			gl.glEnable(gl.GL_DEPTH_TEST); gl.glDepthMask(true); gl.glColorMask(false, false, false, false);
			functions.depthDefaults();
			gl.glDisable(gl.GL_BLEND); gl.glDisable(gl.GL_ALPHA_TEST); gl.glDisable(gl.GL_CULL_FACE);
			gl.glDisable(gl.GL_SCISSOR_TEST); gl.glPolygonMode(gl.GL_FRONT_AND_BACK, gl.GL_FILL);
			gl.glEnable(gl.GL_POLYGON_OFFSET_FILL); gl.glPolygonOffset(1.5f, 2.0f);
			ensureRoofTexture();
			ensureTexture(near, nearSize);
			long start = System.nanoTime();
			nearTimer.begin();
			try { drawMap(scene, near, nearProjection, false); } finally { nearTimer.end(); }
			nearCpuNanos = System.nanoTime() - start; nearDrawCalls = currentDrawCalls; nearTriangles = currentTriangles;
			farCpuNanos = 0;
			if (quality == RendererShadowSettings.Quality.HIGH) {
                ensureTexture(far, farSize);
                if (farRebuilt) {
                    DirectionalShadowProjection next = new DirectionalShadowProjection(centerX, centerY, centerZ, lx, ly, lz, FAR_HALF, farSize);
                    boolean sameScene = farProjection != null && farSignature == signature && farContext == context && farPlane == plane && farRoofSignature == roofMask.signature;
                    boolean poseChanged = sameScene && (centerX != farProjection.centerX || centerY != farProjection.centerY || centerZ != farProjection.centerZ
                        || Math.abs(lx - farProjection.lightX) > 0.000001f || Math.abs(-ly - farProjection.lightY) > 0.000001f || Math.abs(lz - farProjection.lightZ) > 0.000001f);
                    MapTexture target = farProjection == null ? far : previousFar;
                    ensureTexture(target, farSize);
                    start = System.nanoTime(); farTimer.begin();
                    try { drawMap(scene, target, next, true); } finally { farTimer.end(); }
                    farCpuNanos = System.nanoTime() - start; farDrawCalls = currentDrawCalls; farTriangles = currentTriangles;
                    // Commit texture/projection together only after a successful draw.
                    previousFarProjection = poseChanged ? farProjection : null;
                    if (target != far) { previousFar = far; far = target; }
                    farProjection = next; farSignature = signature; farContext = context; farPlane = plane; farRoofSignature = roofMask.signature; farAge = farBlendAge = 0;
                }
			} else {
				destroyTexture(far); invalidateFarHistory(); farProjection = null; farDrawCalls = farTriangles = 0;
			}
			active = true;
		} finally {
			functions.bindFramebuffer(OpenGLShadowBindings.DRAW_FRAMEBUFFER, oldDrawFbo);
			functions.bindFramebuffer(OpenGLShadowBindings.READ_FRAMEBUFFER, oldReadFbo);
			functions.bindVertexArray(oldVao);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER, oldArray);
			gl.glUseProgram(oldProgram);
			gl.glPopAttrib();
			gl.glActiveTexture(oldTexture);
		}
		publishDiagnosticFrame();
		return active;
	}

	private void drawMap(Scene scene, MapTexture map, DirectionalShadowProjection projection, boolean staticOnly) throws Exception {
		functions.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER, map.framebuffer);
		gl.glViewport(0, 0, map.size, map.size);
		gl.glClear(gl.GL_DEPTH_BUFFER_BIT);
		gl.glUniformMatrix4fv(lightUniform, false, projection.put(matrixBuffer));
		currentDrawCalls = currentTriangles = 0;
		scene.drawDepth(this, projection, staticOnly);
	}

	void bindChunk(int vertexBuffer, int indexBuffer, int stride, int uvOffset, float offsetX, float offsetZ) throws Exception {
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER, vertexBuffer); gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
		gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, gl.GL_FLOAT, false, stride, 0L);
		gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 2, gl.GL_FLOAT, false, stride, (long) uvOffset);
		gl.glUniform1f(offsetXUniform, offsetX); gl.glUniform1f(offsetZUniform, offsetZ);
	}
	void drawRange(int texture, boolean alphaTest, int startIndex, int indexCount) throws Exception {
		gl.glBindTexture(gl.GL_TEXTURE_2D, texture);
		gl.glUniform1i(alphaUniform, alphaTest ? 1 : 0);
		gl.glDrawElements(gl.GL_TRIANGLES, indexCount, gl.GL_UNSIGNED_INT, startIndex * 4L);
		currentDrawCalls++; currentTriangles += indexCount / 3;
	}

	void bindReceiver(int receiver) throws Exception {
		if (receiverProgram != receiver) {
			receiverProgram = receiver;
			enabledUniform = gl.glGetUniformLocation(receiver, "uGeometryShadows");
			roofReceiverUniform = gl.glGetUniformLocation(receiver, "uGeometryRoofReceivers");
			roofMinXUniform=gl.glGetUniformLocation(receiver,"uGeometryRoofMinX");roofMinZUniform=gl.glGetUniformLocation(receiver,"uGeometryRoofMinZ");
			roofInvSpanXUniform=gl.glGetUniformLocation(receiver,"uGeometryRoofInvSpanX");roofInvSpanZUniform=gl.glGetUniformLocation(receiver,"uGeometryRoofInvSpanZ");
			gl.glUniform1i(gl.glGetUniformLocation(receiver, "uGeometryRoofMask"), 5);
			farEnabledUniform = gl.glGetUniformLocation(receiver, "uFarGeometryShadow");
            previousFarEnabledUniform = gl.glGetUniformLocation(receiver, "uPreviousFarGeometryShadow");
            previousFarMatrixUniform = gl.glGetUniformLocation(receiver, "uPreviousFarGeometryLight");
            farBlendUniform = gl.glGetUniformLocation(receiver, "uFarGeometryBlend");
            gl.glUniform1i(gl.glGetUniformLocation(receiver, "uPreviousFarGeometryShadowMap"), 6);
			radiusUniform = gl.glGetUniformLocation(receiver, "uGeometryShadowRadius");
			nearMatrixUniform = gl.glGetUniformLocation(receiver, "uNearGeometryLight");
			farMatrixUniform = gl.glGetUniformLocation(receiver, "uFarGeometryLight");
			nearTexelUniform = gl.glGetUniformLocation(receiver, "uNearGeometryTexel");
			farTexelUniform = gl.glGetUniformLocation(receiver, "uFarGeometryTexel");
			nearWorldTexelUniform = gl.glGetUniformLocation(receiver, "uNearGeometryWorldTexel");
			farWorldTexelUniform = gl.glGetUniformLocation(receiver, "uFarGeometryWorldTexel");
			gl.glUniform1i(gl.glGetUniformLocation(receiver, "uNearGeometryShadow"), 3);
			gl.glUniform1i(gl.glGetUniformLocation(receiver, "uFarGeometryShadowMap"), 4);
		}
		gl.glUniform1i(enabledUniform, active ? 1 : 0);
        gl.glUniform1i(previousFarEnabledUniform, active && previousFarProjection != null ? 1 : 0);
		gl.glUniform1i(roofReceiverUniform, 0);
		if (!active) return;
		if(!roofMask.isEmpty()) {
			gl.glUniform1f(roofMinXUniform,roofMask.minX);gl.glUniform1f(roofMinZUniform,roofMask.minZ);
			gl.glUniform1f(roofInvSpanXUniform,1.0f/(roofMask.width*128));gl.glUniform1f(roofInvSpanZUniform,1.0f/(roofMask.height*128));
			gl.glActiveTexture(gl.GL_TEXTURE0+5);gl.glBindTexture(gl.GL_TEXTURE_2D,roofTexture);
		}
		gl.glUniform1i(farEnabledUniform, farProjection != null ? 1 : 0);
		gl.glUniform1i(radiusUniform, quality.filterRadius);
		gl.glUniformMatrix4fv(nearMatrixUniform, false, nearProjection.put(matrixBuffer));
		gl.glUniform1f(nearTexelUniform, 1.0f / near.size);
		gl.glUniform1f(nearWorldTexelUniform, nearProjection.worldTexel);
		gl.glActiveTexture(gl.GL_TEXTURE0 + 3); gl.glBindTexture(gl.GL_TEXTURE_2D, near.texture);
		if (farProjection != null) {
			gl.glUniformMatrix4fv(farMatrixUniform, false, farProjection.put(matrixBuffer));
			gl.glUniform1f(farTexelUniform, 1.0f / far.size);
			gl.glUniform1f(farWorldTexelUniform, farProjection.worldTexel);
		}
		// Shadow sampler types must have their own texture unit even when disabled.
		gl.glActiveTexture(gl.GL_TEXTURE0 + 4); gl.glBindTexture(gl.GL_TEXTURE_2D, farProjection != null ? far.texture : near.texture);
        gl.glActiveTexture(gl.GL_TEXTURE0 + 6); gl.glBindTexture(gl.GL_TEXTURE_2D, previousFarProjection != null ? previousFar.texture : near.texture);
        if (previousFarProjection != null) {
            gl.glUniformMatrix4fv(previousFarMatrixUniform, false, previousFarProjection.put(matrixBuffer));
            float t = Math.min(1.0f, (float) farBlendAge / FAR_BLEND_FRAMES);
            gl.glUniform1f(farBlendUniform, t * t * (3.0f - 2.0f * t));
        }
		gl.glActiveTexture(gl.GL_TEXTURE0);
	}
	void setReceiverPlane(int plane) throws Exception {
		gl.glUniform1i(roofReceiverUniform,active&&!roofMask.isEmpty()&&roofMask.plane==plane?1:0);
	}
	private void ensureRoofTexture() throws Exception {
		if(roofMask.isEmpty()||(roofTexture!=0&&uploadedRoofSignature==roofMask.signature)) return;
		if(roofTexture==0)roofTexture=gl.glGenTextures();
		gl.glActiveTexture(gl.GL_TEXTURE0+5);gl.glBindTexture(gl.GL_TEXTURE_2D,roofTexture);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_S,gl.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_T,gl.GL_CLAMP_TO_EDGE);
		gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,roofMask.width,roofMask.height,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,roofMask.pixels());
		uploadedRoofSignature=roofMask.signature;roofUploads++;gl.glActiveTexture(gl.GL_TEXTURE0);
	}
    private void invalidateFarHistory() throws Exception {
        previousFarProjection = null; farBlendAge = 0; destroyTexture(previousFar);
    }
    void suspend() throws Exception {
        invalidateFarHistory(); farProjection = null; nearProjection = null; farAge = 0;
    }
    void clearPrepared() {
		active = false;
		nearCpuNanos = farCpuNanos = 0;
		nearDrawCalls = nearTriangles = farDrawCalls = farTriangles = 0;
		farRebuilt = false;
	}
	/** Last completed frame, coherent for an observer on another thread.
	 * Returns sequence/active/farRebuilt/nearCpu/farCpu/nearCalls/nearTriangles/
	 * farCalls/farTriangles/farSize/previousFarActive/blendPermille.
	 * Allocation occurs only when the observer asks. */
	long[] diagnosticSnapshot() {
		diagnosticLock.lock();
		try {
			return new long[] {diagnosticSequence, diagnosticActive ? 1L : 0L,
				diagnosticFarRebuilt ? 1L : 0L, diagnosticNearCpu, diagnosticFarCpu,
				diagnosticNearDraws, diagnosticNearTriangles,
				diagnosticFarDraws, diagnosticFarTriangles, farSize, diagnosticPreviousFar ? 1L : 0L, diagnosticFarBlendPermille};
		} finally { diagnosticLock.unlock(); }
	}
	void publishDiagnosticFrame() {
		// A diagnostic observer cannot block the rendering thread. If it owns
		// the tiny copy lock, retain the previous completed frame until next time.
		if (!RendererDiagnosticSession.isEnabled() || !diagnosticLock.tryLock()) return;
		try {
			diagnosticActive = active; diagnosticFarRebuilt = farRebuilt;
			diagnosticNearCpu = nearCpuNanos; diagnosticFarCpu = farCpuNanos;
			diagnosticNearDraws = nearDrawCalls; diagnosticNearTriangles = nearTriangles;
			diagnosticFarDraws = farDrawCalls; diagnosticFarTriangles = farTriangles;
			diagnosticPreviousFar = active && previousFarProjection != null;
            float t = diagnosticPreviousFar ? Math.min(1.0f, (float) farBlendAge / FAR_BLEND_FRAMES) : 1.0f;
            diagnosticFarBlendPermille = (int) (1000 * t * t * (3.0f - 2.0f * t));
            diagnosticSequence++;
		} finally { diagnosticLock.unlock(); }
	}
	boolean isActive() { return active; }
	void pollGpuTimers() throws Exception { farTimer.pollCompleted(); }
	long nearGpuNanos() { return nearTimer.lastNanos(); }
	long farGpuNanos() { return farTimer.lastNanos(); }
	long nearGpuSamples() { return nearTimer.samples(); }
	long farGpuSamples() { return farTimer.samples(); }

	private void ensureTexture(MapTexture map, int size) throws Exception {
		if (map.size == size && map.texture != 0) return;
		destroyTexture(map);
		try {
			map.texture = gl.glGenTextures(); map.framebuffer = functions.genFramebuffer(); map.size = size;
			gl.glBindTexture(gl.GL_TEXTURE_2D, map.texture);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_LINEAR);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, OpenGLShadowBindings.TEXTURE_COMPARE_MODE, OpenGLShadowBindings.COMPARE_REF_TO_TEXTURE);
			gl.glTexParameteri(gl.GL_TEXTURE_2D, OpenGLShadowBindings.TEXTURE_COMPARE_FUNC, OpenGLShadowBindings.LEQUAL);
			gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, OpenGLShadowBindings.DEPTH_COMPONENT24, size, size, 0,
				OpenGLShadowBindings.DEPTH_COMPONENT, gl.GL_FLOAT, null);
			functions.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER, map.framebuffer);
			functions.attachTexture(map.texture); functions.noColorBuffers();
			if (!functions.framebufferComplete()) throw new IllegalStateException("Incomplete shadow depth framebuffer");
		} catch (Exception error) { destroyTexture(map); throw error; }
	}
	private void destroyTexture(MapTexture map) throws Exception {
		if (map.framebuffer != 0) { functions.deleteFramebuffer(map.framebuffer); map.framebuffer = 0; }
		if (map.texture != 0) { gl.glDeleteTextures(map.texture); map.texture = 0; }
		map.size = 0;
	}
	public void close() throws Exception {
		if (closed) return;
		closed = true;
		clearPrepared();
		publishDiagnosticFrame();
		if(roofTexture!=0){gl.glDeleteTextures(roofTexture);roofTexture=0;}
		destroyTexture(near); destroyTexture(far); destroyTexture(previousFar); nearTimer.close(); farTimer.close();
		functions.deleteVertexArray(vao); gl.glDeleteProgram(program);
	}
	private static final class MapTexture { int texture, framebuffer, size; }
}
