package orsc;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Hidden hardware pixel/state fixture, not a gameplay performance benchmark. */
public final class GeometryShadowGpuFixture {
	private static LwjglBindings gl;
	private static OpenGLShadowBindings functions;
	private static int vertices, indices, alphaTexture;
	private static boolean alpha;
	private static boolean slopeReceiver, wallReceiver, contactCard;
	private static int receiverPlane;
	private static float receiverKind=1;
	private static GeometryShadowRoofMask roofMask=GeometryShadowRoofMask.EMPTY;
	private static long signature;
	private static final Set<String> gpuSamples = new HashSet<String>();
	private static final int SIZE = 512;
	private static FloatBuffer floats(float... values) {
		FloatBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		b.put(values).flip(); return b;
	}
	private static IntBuffer ints(int... values) {
		IntBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
		b.put(values).flip(); return b;
	}
	private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
	private static int textureId(OpenGLGeometryShadows pass,String name)throws Exception {
		java.lang.reflect.Field field=OpenGLGeometryShadows.class.getDeclaredField(name);field.setAccessible(true);Object map=field.get(pass);
		java.lang.reflect.Field texture=map.getClass().getDeclaredField("texture");texture.setAccessible(true);return texture.getInt(map);
	}
	private static int compile(int type, String source) throws Exception {
		int shader = gl.glCreateShader(type); gl.glShaderSource(shader, source); gl.glCompileShader(shader);
		require(gl.glGetShaderi(shader, gl.GL_COMPILE_STATUS) != 0, gl.glGetShaderInfoLog(shader)); return shader;
	}
	private static int receiver() throws Exception {
		int v = compile(gl.GL_VERTEX_SHADER, "#version 120\nattribute vec3 aPosition; uniform int uWallReceiver;uniform float uReceiverKind; varying vec3 vShadowWorldPosition;varying float vModelKind; void main(){vShadowWorldPosition=aPosition;vModelKind=uWallReceiver!=0?2.0:uReceiverKind; gl_Position=uWallReceiver!=0?vec4(aPosition.z/320.0,-aPosition.y/320.0,0.0,1.0):vec4(aPosition.x/320.0,aPosition.z/320.0,0.0,1.0);}");
		int f = compile(gl.GL_FRAGMENT_SHADER, "#version 120\nvarying float vModelKind;uniform float uLightDirectionX;uniform float uLightDirectionY;uniform float uLightDirectionZ;\n"
			+ OpenGLGeometryShadows.FRAGMENT_SOURCE + "void main(){gl_FragColor=vec4(vec3(geometryShadowVisibility()),1.0);}");
		int p = gl.glCreateProgram(); gl.glAttachShader(p,v); gl.glAttachShader(p,f); gl.glBindAttribLocation(p,0,"aPosition"); gl.glLinkProgram(p);
		require(gl.glGetProgrami(p,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(p));gl.glDeleteShader(v);gl.glDeleteShader(f);return p;
	}
	private static void mesh() throws Exception {
		if(vertices==0)vertices=gl.glGenBuffers();if(indices==0)indices=gl.glGenBuffers();
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vertices);
		// Floor plus vertical two-triangle card. The card's left UV half is
		// transparent in the second case; its projected shadow must preserve it.
		float[] geometry = {
			-320,0,-320,0,0, 320,0,-320,0,0, 320,0,320,0,0,
			-320,0,-320,0,0, 320,0,320,0,0, -320,0,320,0,0,
			0,-128,-64,0,0, 0,-128,64,1,0, 0,-256,64,1,1,
			0,-128,-64,0,0, 0,-256,64,1,1, 0,-256,-64,0,1};
		for(int i=0;i<6;i++) {
			if(slopeReceiver)geometry[i*5+1]=geometry[i*5]*-.25f;
			if(wallReceiver){geometry[i*5+1]=geometry[i*5];geometry[i*5]=-300;}
		}
		if(contactCard)for(int i=6;i<12;i++)geometry[i*5+1]+=128;
		gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(geometry), gl.GL_STATIC_DRAW);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,indices);
		gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,ints(0,1,2,3,4,5,6,7,8,9,10,11),gl.GL_STATIC_DRAW);
		if(alphaTexture==0)alphaTexture=gl.glGenTextures();gl.glBindTexture(gl.GL_TEXTURE_2D,alphaTexture);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);
		ByteBuffer pixels=ByteBuffer.allocateDirect(16);
		for(int i=0;i<4;i++)pixels.put((byte)255).put((byte)255).put((byte)255).put(i%2==0?(byte)0:(byte)255);
		pixels.flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,2,2,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
	}
	private static final OpenGLGeometryShadows.Scene SCENE = new OpenGLGeometryShadows.Scene() {
		public long staticSignature(){return signature;}
		public GeometryShadowRoofMask roofMask(){return roofMask;}
		public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection projection,boolean staticOnly)throws Exception {
			pass.bindChunk(vertices,indices,20,12,0,0);
			pass.drawRange(0,false,0,6); pass.drawRange(alphaTexture,alpha,6,6);
		}
	};
	private static ByteBuffer draw(OpenGLGeometryShadows pass,int program,Path png)throws Exception {
		functions.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);
		gl.glViewport(0,0,SIZE,SIZE);gl.glColorMask(true,true,true,true);
		gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);
		gl.glClearColor(.7f,.2f,.2f,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glUseProgram(program);pass.bindReceiver(program);pass.setReceiverPlane(receiverPlane);
		gl.glUniform1i(gl.glGetUniformLocation(program,"uWallReceiver"),wallReceiver?1:0);
		gl.glUniform1f(gl.glGetUniformLocation(program,"uReceiverKind"),receiverKind);
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionX"),RendererRemasterLightSettings.getLightDirectionX());
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionY"),RendererRemasterLightSettings.getLightDirectionY());
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionZ"),RendererRemasterLightSettings.getLightDirectionZ());
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vertices);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,indices);
		gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,20,0L);
		gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);
		ByteBuffer pixels=ByteBuffer.allocateDirect(SIZE*SIZE*4);
		gl.glReadPixels(0,0,SIZE,SIZE,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
		if(png!=null){BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
			for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int k=(y*SIZE+x)*4;image.setRGB(x,SIZE-y-1,0xff000000|((pixels.get(k)&255)<<16)|((pixels.get(k+1)&255)<<8)|(pixels.get(k+2)&255));}
			ImageIO.write(image,"png",png.toFile());}
		return pixels;
	}
	private static int brightness(ByteBuffer pixels,int x,int z) {
		int px=(int)((x/320.0+1)*.5*SIZE),py=(int)((z/320.0+1)*.5*SIZE),sum=0;
		for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)sum+=pixels.get(((py+dy)*SIZE+px+dx)*4)&255;
		return sum/9;
	}
	public static void main(String[] args)throws Exception {
		Path output=Paths.get(args[0]).toAbsolutePath();Files.createDirectories(output);
		System.setProperty("spoiledmilk.rendererDiagnostics","true");
		System.setProperty("spoiledmilk.remasterLightAzimuth","0");System.setProperty("spoiledmilk.remasterLightElevation","45");
		gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLGeometryShadows pass=null;OpenGLGpuTimer frameTimer=null;int program=0,controlVao=0,drawFbo=0,readFbo=0;
		try {
			gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
			window=gl.glfwCreateWindow(SIZE,SIZE,"Hidden geometry shadow fixture",0,0);require(window!=0,"hidden window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();
			System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));functions=OpenGLShadowBindings.create();
			if(args.length>1&&args[1].equals("unsupported")){require(functions==null,"GL2 capability fallback");System.out.println("PASS unsupported context detected");return;}
			require(functions!=null,"GL3 needed");mesh();program=receiver();
			System.setProperty("spoiledmilk.shadowGpuTimings","true");
			OpenGLGpuTimer.setDiagnosticSink((label,sequence,nanos)->require(nanos>=0&&gpuSamples.add(label+":"+sequence),"duplicate/invalid GPU sample"));
			frameTimer=new OpenGLGpuTimer(functions,"test-frame");
			pass=new OpenGLGeometryShadows(gl,functions);
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
			controlVao=functions.genVertexArray();functions.bindVertexArray(controlVao);gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vertices);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,indices);
			drawFbo=functions.genFramebuffer();readFbo=functions.genFramebuffer();functions.bindFramebuffer(OpenGLShadowBindings.DRAW_FRAMEBUFFER,drawFbo);functions.bindFramebuffer(OpenGLShadowBindings.READ_FRAMEBUFFER,readFbo);functions.noColorBuffers();
			gl.glUseProgram(program);gl.glActiveTexture(gl.GL_TEXTURE0+5);gl.glBindTexture(gl.GL_TEXTURE_2D,alphaTexture);
			gl.glViewport(7,8,400,401);
			Class<?> gl11=Class.forName("org.lwjgl.opengl.GL11");Method depthFunc=gl11.getMethod("glDepthFunc",int.class);depthFunc.invoke(null,0x0204);
			gl.glDepthMask(false);gl.glColorMask(false,true,false,true);gl.glEnable(gl.GL_CULL_FACE);gl.glCullFace(0x0404);gl.glEnable(gl.GL_POLYGON_OFFSET_FILL);gl.glPolygonOffset(3.5f,7);
			gl11.getMethod("glClearDepth",double.class).invoke(null,.25);
			gl11.getMethod("glDepthRange",double.class,double.class).invoke(null,.2,.7);
			frameTimer.begin();
			pass.prepare(SCENE,0,0,0);
			require(pass.farRebuilt&&pass.nearDrawCalls>0,"initial maps not built");
			long[] completed=pass.diagnosticSnapshot();require(completed[0]>0&&completed[1]==1&&completed[5]>0,"completed diagnostic snapshot missing");
			pass.clearPrepared();require(pass.diagnosticSnapshot()[1]==1,"transient prepare state leaked to observer");
			pass.prepare(SCENE,0,0,0);
			require(functions.integer(OpenGLShadowBindings.CURRENT_PROGRAM)==program,"program state leaked");
			require(functions.integer(OpenGLShadowBindings.ACTIVE_TEXTURE)==gl.GL_TEXTURE0+5,"active texture leaked");
			require(functions.integer(0x8069)==alphaTexture,"texture binding leaked");
			require(functions.integer(0x0B74)==0x0204,"depth function leaked");
			require(functions.integer(OpenGLShadowBindings.DRAW_FRAMEBUFFER_BINDING)==drawFbo&&functions.integer(OpenGLShadowBindings.READ_FRAMEBUFFER_BINDING)==readFbo,"framebuffer binding leaked");
			require(functions.integer(OpenGLShadowBindings.VERTEX_ARRAY_BINDING)==controlVao,"VAO leaked");
			require(functions.integer(OpenGLShadowBindings.ARRAY_BUFFER_BINDING)==vertices&&functions.integer(0x8895)==indices,"buffer bindings leaked");
			ByteBuffer masks=ByteBuffer.allocateDirect(4);gl11.getMethod("glGetBooleanv",int.class,ByteBuffer.class).invoke(null,0x0C23,masks);
			require(masks.get(0)==0&&masks.get(1)!=0&&masks.get(2)==0&&masks.get(3)!=0,"color mask leaked");
			require(!(Boolean)gl11.getMethod("glGetBoolean",int.class).invoke(null,0x0B72),"depth write mask leaked");
			DoubleBuffer depthRange=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asDoubleBuffer();gl11.getMethod("glGetDoublev",int.class,DoubleBuffer.class).invoke(null,0x0B70,depthRange);
			require(Math.abs(depthRange.get(0)-.2)<.000001&&Math.abs(depthRange.get(1)-.7)<.000001,"depth range leaked");
			require((Double)gl11.getMethod("glGetDouble",int.class).invoke(null,0x0B73)==.25,"clear depth leaked");
			require((Boolean)gl11.getMethod("glIsEnabled",int.class).invoke(null,gl.GL_CULL_FACE),"cull enable leaked");
			require(functions.integer(0x0B45)==0x0404,"cull face leaked");
			require((Boolean)gl11.getMethod("glIsEnabled",int.class).invoke(null,gl.GL_POLYGON_OFFSET_FILL),"polygon offset enable leaked");
			require((Float)gl11.getMethod("glGetFloat",int.class).invoke(null,0x8038)==3.5f,"polygon offset factor leaked");
			require((Float)gl11.getMethod("glGetFloat",int.class).invoke(null,0x2A00)==7.0f,"polygon offset units leaked");
			IntBuffer viewport=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();gl11.getMethod("glGetIntegerv",int.class,IntBuffer.class).invoke(null,0x0BA2,viewport);
			require(viewport.get(0)==7&&viewport.get(1)==8&&viewport.get(2)==400&&viewport.get(3)==401,"viewport leaked");
			ByteBuffer opaque=draw(pass,program,output.resolve("opaque-card.png"));
			frameTimer.end();frameTimer.begin();frameTimer.end();
			require(brightness(opaque,-180,-40)<100,"card shadow missing");require(brightness(opaque,180,0)>240,"unoccluded ground/self bias wrong");
			pass.prepare(SCENE,0,0,0);require(!pass.farRebuilt&&pass.farDrawCalls==0&&pass.nearDrawCalls>0,"steady far cache or counters wrong");
			pass.prepare(SCENE,600,0,0);require(pass.farRebuilt,"four-tile movement did not refresh far map");
			alpha=true;signature++;pass.prepare(SCENE,0,0,0);
			require(pass.farRebuilt,"changed alpha/geometry signature did not refresh far map");
			ByteBuffer cutout=draw(pass,program,output.resolve("alpha-card.png"));
			require(brightness(cutout,-180,-40)>240,"transparent foliage casts solid shadow");
			require(brightness(cutout,-180,40)<100,"opaque foliage shadow missing");
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);pass.prepare(SCENE,0,0,0);
			ByteBuffer low=draw(pass,program,output.resolve("low-card.png"));require(brightness(low,-180,40)<100,"low shadow missing");
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);pass.prepare(SCENE,0,0,0);require(pass.farRebuilt,"Low->High did not restore far map");
			slopeReceiver=true;alpha=false;signature++;mesh();pass.prepare(SCENE,0,0,0);
			ByteBuffer slope=draw(pass,program,output.resolve("slope-card.png"));require(brightness(slope,-240,40)<100,"sloped receiver shadow missing");require(brightness(slope,180,0)>240,"sloped receiver self-shadow");
			slopeReceiver=false;wallReceiver=true;signature++;mesh();pass.prepare(SCENE,0,0,0);
			ByteBuffer wall=draw(pass,program,output.resolve("wall-card.png"));require(brightness(wall,40,-100)<100,"wall receiver shadow missing");require(brightness(wall,0,100)>240,"wall receiver self-shadow");
			wallReceiver=false;slopeReceiver=false;signature++;mesh();
			for(boolean sloped:new boolean[]{false,true})for(int elevation:new int[]{25,45,85}) {
				slopeReceiver=sloped;signature++;mesh();
				RendererRemasterLightSettings.adjustElevation(elevation-RendererRemasterLightSettings.getElevationDegrees());
				pass.prepare(SCENE,0,0,0);
				ByteBuffer grazing=draw(pass,program,output.resolve("sun-"+elevation+(sloped?"-slope":"-flat")+".png"));
				for(int x=80;x<=240;x+=10)for(int z=-240;z<=240;z+=10)
					require(brightness(grazing,x,z)>240,"PCF ground self-shadow at sun "+elevation+" x="+x+" z="+z);
			}
			for(int elevation:new int[]{5,10}) {
				slopeReceiver=false;signature++;mesh();RendererRemasterLightSettings.adjustElevation(elevation-RendererRemasterLightSettings.getElevationDegrees());
				pass.prepare(SCENE,0,0,0);ByteBuffer grazing=draw(pass,program,output.resolve("sun-"+elevation+"-flat.png"));
				for(int x=80;x<=240;x+=10)for(int z=-240;z<=240;z+=10)
					require(brightness(grazing,x,z)>240,"grazing ground self-shadow at sun "+elevation);
			}
			slopeReceiver=false;contactCard=true;signature++;mesh();RendererRemasterLightSettings.adjustElevation(45-RendererRemasterLightSettings.getElevationDegrees());
			pass.prepare(SCENE,0,0,0);ByteBuffer contact=draw(pass,program,output.resolve("contact-card.png"));
			require(brightness(contact,-12,0)<100,"shadow detached more than0.1tile from contact");
			require(brightness(contact,20,0)>240,"contact bias spread onto lit side");
			require(brightness(contact,-96,0)<100&&brightness(contact,-180,0)>240,"contact silhouette extent changed");
			GeometryShadowRoofMask.Cache roofCache=new GeometryShadowRoofMask.Cache();
			roofMask=roofCache.get(GeometryShadowRoofMaskFixture.frame(1L<<2,false,101),0);
			gl.glActiveTexture(gl.GL_TEXTURE0+5);gl.glBindTexture(gl.GL_TEXTURE_2D,alphaTexture);pass.prepare(SCENE,0,0,0);
			require(functions.integer(OpenGLShadowBindings.ACTIVE_TEXTURE)==gl.GL_TEXTURE0+5&&functions.integer(0x8069)==alphaTexture,"roof upload leaked texture state");
			require(functions.integer(OpenGLShadowBindings.CURRENT_PROGRAM)==program,"roof upload leaked program");
			int uploads=pass.roofUploads;require(uploads>0,"roof mask not uploaded");
			ByteBuffer indoor=draw(pass,program,output.resolve("roof-covered-floor.png"));
			require(brightness(indoor,180,40)==0,"roofed floor still receives patterned direct sun");
			require(brightness(indoor,180,-100)>240,"roof mask spread onto outdoor tile");
			pass.prepare(SCENE,0,0,0);require(pass.roofUploads==uploads,"unchanged roof metadata uploaded again");
			for(float kind:new float[]{4,5}){receiverKind=kind;require(brightness(draw(pass,program,null),180,40)==0,"covered prop retains direct sun");}
			for(float kind:new float[]{2,3}){receiverKind=kind;require(brightness(draw(pass,program,null),180,40)>240,"facade/roof top was treated as interior");}
			receiverKind=1;receiverPlane=1;require(brightness(draw(pass,program,null),180,40)>240,"roof mask leaked onto another plane");receiverPlane=0;
			roofMask=roofCache.get(GeometryShadowRoofMaskFixture.frame(0,false,102),0);pass.prepare(SCENE,0,0,0);
			require(brightness(draw(pass,program,null),180,40)>240,"unknown/outdoor coverage inferred indoor shadow");
			java.lang.reflect.Field roofField=OpenGLGeometryShadows.class.getDeclaredField("roofTexture");roofField.setAccessible(true);int roofTexture=roofField.getInt(pass);
			signature++;pass.prepare(SCENE,0,0,0);draw(pass,program,null);
			long farSamplesBefore=pass.farGpuSamples();pass.pollGpuTimers();
			require(pass.farGpuSamples()>farSamplesBefore,"completed far query waited for another rebuild");
			long farSamplesAfter=pass.farGpuSamples();pass.pollGpuTimers();
			require(pass.farGpuSamples()==farSamplesAfter,"poll repeated an already completed far sample");
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.OFF);require(!pass.prepare(SCENE,0,0,0),"Off still prepared depth maps");
			require(pass.nearDrawCalls==0&&pass.farDrawCalls==0&&pass.nearCpuNanos==0&&!pass.farRebuilt,"Off retains stale per-frame diagnostics");
			require(pass.diagnosticSnapshot()[1]==0&&pass.diagnosticSnapshot()[5]==0,"Off snapshot retains stale active/draw state");
			ByteBuffer off=draw(pass,program,output.resolve("off.png"));require(brightness(off,40,-100)>240,"disabled shadow persisted");
			frameTimer.begin();frameTimer.end();require(gpuSamples.size()>2,"completed GPU samples missing");
			int error=(Integer)gl11.getMethod("glGetError").invoke(null);require(error==0,"GL error "+error);
			int nearTexture=textureId(pass,"near"),farTexture=textureId(pass,"far");
			require(nearTexture!=0&&farTexture!=0,"Off bounded texture retention contract");
			pass.close();pass.close();pass=null;
			require(!(Boolean)gl11.getMethod("glIsTexture",int.class).invoke(null,nearTexture)
				&&!(Boolean)gl11.getMethod("glIsTexture",int.class).invoke(null,farTexture)
				&&!(Boolean)gl11.getMethod("glIsTexture",int.class).invoke(null,roofTexture),"shadow textures leaked on close");
			System.out.println("PASS depth silhouette, cutout alpha, floor/slope/wall bias, cache invalidation, High/Low/Off, cleanup and GL state restoration");
		} finally {
			OpenGLGpuTimer.setDiagnosticSink(null);if(frameTimer!=null)frameTimer.close();
			if(pass!=null)pass.close();if(program!=0)gl.glDeleteProgram(program);
			if(alphaTexture!=0)gl.glDeleteTextures(alphaTexture);
			if(vertices!=0)gl.glDeleteBuffers(vertices);if(indices!=0)gl.glDeleteBuffers(indices);
			if(functions!=null){if(controlVao!=0)functions.deleteVertexArray(controlVao);if(drawFbo!=0)functions.deleteFramebuffer(drawFbo);if(readFbo!=0)functions.deleteFramebuffer(readFbo);}
			if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();
		}
	}
}
