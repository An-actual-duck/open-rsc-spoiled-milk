package orsc;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/** Hidden hardware pixel/state fixture, not a gameplay performance benchmark. */
public final class GeometryShadowGpuFixture {
	private static LwjglBindings gl;
	private static OpenGLShadowBindings functions;
	private static int vertices, indices, alphaTexture;
	private static boolean alpha;
	private static long signature;
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
	private static int compile(int type, String source) throws Exception {
		int shader = gl.glCreateShader(type); gl.glShaderSource(shader, source); gl.glCompileShader(shader);
		require(gl.glGetShaderi(shader, gl.GL_COMPILE_STATUS) != 0, gl.glGetShaderInfoLog(shader)); return shader;
	}
	private static int receiver() throws Exception {
		int v = compile(gl.GL_VERTEX_SHADER, "#version 120\nattribute vec3 aPosition; varying vec3 vShadowWorldPosition; void main(){vShadowWorldPosition=aPosition; gl_Position=vec4(aPosition.x/320.0,aPosition.z/320.0,0.0,1.0);}");
		int f = compile(gl.GL_FRAGMENT_SHADER, "#version 120\nuniform float uLightDirectionX;uniform float uLightDirectionY;uniform float uLightDirectionZ;\n"
			+ OpenGLGeometryShadows.FRAGMENT_SOURCE + "void main(){gl_FragColor=vec4(vec3(geometryShadowVisibility()),1.0);}");
		int p = gl.glCreateProgram(); gl.glAttachShader(p,v); gl.glAttachShader(p,f); gl.glBindAttribLocation(p,0,"aPosition"); gl.glLinkProgram(p);
		require(gl.glGetProgrami(p,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(p));gl.glDeleteShader(v);gl.glDeleteShader(f);return p;
	}
	private static void mesh() throws Exception {
		vertices=gl.glGenBuffers();indices=gl.glGenBuffers();
		gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vertices);
		// Floor plus vertical two-triangle card. The card's left UV half is
		// transparent in the second case; its projected shadow must preserve it.
		gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(
			-320,0,-320,0,0, 320,0,-320,0,0, 320,0,320,0,0,
			-320,0,-320,0,0, 320,0,320,0,0, -320,0,320,0,0,
			0,-128,-64,0,0, 0,-128,64,1,0, 0,-256,64,1,1,
			0,-128,-64,0,0, 0,-256,64,1,1, 0,-256,-64,0,1), gl.GL_STATIC_DRAW);
		gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,indices);
		gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,ints(0,1,2,3,4,5,6,7,8,9,10,11),gl.GL_STATIC_DRAW);
		alphaTexture=gl.glGenTextures();gl.glBindTexture(gl.GL_TEXTURE_2D,alphaTexture);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);
		ByteBuffer pixels=ByteBuffer.allocateDirect(16);
		for(int i=0;i<4;i++)pixels.put((byte)255).put((byte)255).put((byte)255).put(i%2==0?(byte)0:(byte)255);
		pixels.flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,2,2,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
	}
	private static final OpenGLGeometryShadows.Scene SCENE = new OpenGLGeometryShadows.Scene() {
		public long staticSignature(){return signature;}
		public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection projection,boolean staticOnly)throws Exception {
			pass.bindChunk(vertices,indices,20,12,0,0);
			pass.drawRange(0,false,0,6); pass.drawRange(alphaTexture,alpha,6,6);
		}
	};
	private static ByteBuffer draw(OpenGLGeometryShadows pass,int program,Path png)throws Exception {
		functions.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);
		gl.glViewport(0,0,SIZE,SIZE);gl.glColorMask(true,true,true,true);
		gl.glClearColor(.7f,.2f,.2f,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDisable(gl.GL_DEPTH_TEST);
		gl.glUseProgram(program);pass.bindReceiver(program);
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionX"),.70710677f);
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionY"),.70710677f);
		gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionZ"),0);
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
		System.setProperty("spoiledmilk.remasterLightAzimuth","0");System.setProperty("spoiledmilk.remasterLightElevation","45");
		gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLGeometryShadows pass=null;int program=0;
		try {
			gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
			window=gl.glfwCreateWindow(SIZE,SIZE,"Hidden geometry shadow fixture",0,0);require(window!=0,"hidden window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();
			System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));functions=OpenGLShadowBindings.create();
			if(args.length>1&&args[1].equals("unsupported")){require(functions==null,"GL2 capability fallback");System.out.println("PASS unsupported context detected");return;}
			require(functions!=null,"GL3 needed");mesh();program=receiver();pass=new OpenGLGeometryShadows(gl,functions);
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
			gl.glUseProgram(program);gl.glActiveTexture(gl.GL_TEXTURE0+5);gl.glBindTexture(gl.GL_TEXTURE_2D,alphaTexture);
			gl.glViewport(7,8,400,401);
			Class<?> gl11=Class.forName("org.lwjgl.opengl.GL11");Method depthFunc=gl11.getMethod("glDepthFunc",int.class);depthFunc.invoke(null,0x0204);
			pass.prepare(SCENE,0,0,0);
			require(functions.integer(OpenGLShadowBindings.CURRENT_PROGRAM)==program,"program state leaked");
			require(functions.integer(OpenGLShadowBindings.ACTIVE_TEXTURE)==gl.GL_TEXTURE0+5,"active texture leaked");
			require(functions.integer(0x8069)==alphaTexture,"texture binding leaked");
			require(functions.integer(0x0B74)==0x0204,"depth function leaked");
			IntBuffer viewport=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();gl11.getMethod("glGetIntegerv",int.class,IntBuffer.class).invoke(null,0x0BA2,viewport);
			require(viewport.get(0)==7&&viewport.get(1)==8&&viewport.get(2)==400&&viewport.get(3)==401,"viewport leaked");
			ByteBuffer opaque=draw(pass,program,output.resolve("opaque-card.png"));
			require(brightness(opaque,-180,-40)<100,"card shadow missing");require(brightness(opaque,180,0)>240,"unoccluded ground/self bias wrong");
			alpha=true;signature++;pass.prepare(SCENE,0,0,0);
			ByteBuffer cutout=draw(pass,program,output.resolve("alpha-card.png"));
			require(brightness(cutout,-180,-40)>240,"transparent foliage casts solid shadow");
			require(brightness(cutout,-180,40)<100,"opaque foliage shadow missing");
			RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);pass.prepare(SCENE,0,0,0);
			ByteBuffer low=draw(pass,program,output.resolve("low-card.png"));require(brightness(low,-180,40)<100,"low shadow missing");
			pass.clearPrepared();ByteBuffer off=draw(pass,program,output.resolve("off.png"));require(brightness(off,-180,40)>240,"disabled shadow persisted");
			int error=(Integer)gl11.getMethod("glGetError").invoke(null);require(error==0,"GL error "+error);
			System.out.println("PASS depth silhouette, cutout alpha, lit/self-shadow bias, High/Low/Off and GL state restoration");
		} finally {
			if(pass!=null)pass.close();if(program!=0)gl.glDeleteProgram(program);
			if(alphaTexture!=0)gl.glDeleteTextures(alphaTexture);
			if(vertices!=0)gl.glDeleteBuffers(vertices);if(indices!=0)gl.glDeleteBuffers(indices);
			if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();
		}
	}
}
