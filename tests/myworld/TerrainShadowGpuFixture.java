package orsc;

import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Structurally matching40-triangle heightfield from native Seers diagnosis; no network/account.
 * Source dump e6ba47a69cef1807d40cd82a2f310ac9a6052dcdfd393a5c1afcb7cf0be05126.
 * Vertex depression of90world units gives a1.31degree grazing sunlight footprint.
 * Uses original1920x1080 camera and global offset, rather than a generic plane. */
public final class TerrainShadowGpuFixture {
	static final int WIDTH=1920,HEIGHT=1080;
	static final int[] COORDS=terrainPatch();
	static int[] terrainPatch() {
		int[] result=new int[5*4*6*3];int at=0;
		for(int x=8832;x<9472;x+=128)for(int z=9088;z<9600;z+=128) {
			int[] corners={x+128,z,x,z,x,z+128,x,z+128,x+128,z+128,x+128,z};
			for(int i=0;i<corners.length;i+=2) {
				int vx=corners[i],vz=corners[i+1];result[at++]=vx;
				result[at++]=vx==9088&&vz==9344?-294:-384;result[at++]=vz;
			}
		}
		return result;
	}

	static LwjglBindings gl;static OpenGLShadowBindings f;static int vbo,ebo;
	static FloatBuffer floats(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).flip();return b;}
	static int shader(int type,String source)throws Exception{int s=gl.glCreateShader(type);gl.glShaderSource(s,source);gl.glCompileShader(s);if(gl.glGetShaderi(s,gl.GL_COMPILE_STATUS)==0)throw new AssertionError(gl.glGetShaderInfoLog(s));return s;}
	static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
	static int brightness(ByteBuffer p,int x,int y){return p.get(((HEIGHT-y-1)*WIDTH+x)*4)&255;}
	static final OpenGLGeometryShadows.Scene SCENE=new OpenGLGeometryShadows.Scene(){
		public long staticSignature(){return 1L;}
		public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection p,boolean staticOnly)throws Exception{
			pass.bindChunk(vbo,ebo,20,12,350208,276480);pass.drawRange(0,false,0,COORDS.length/3);
		}
	};
	public static void main(String[] args)throws Exception{
		Path output=Paths.get(args[0]);Files.createDirectories(output);
		System.setProperty("spoiledmilk.remasterLightAzimuth","315");System.setProperty("spoiledmilk.remasterLightElevation","25");
		gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLGeometryShadows pass=null;int program=0;
		try{
			gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
			window=gl.glfwCreateWindow(WIDTH,HEIGHT,"Hidden actual terrain subset",0,0);require(window!=0,"hidden window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();
			System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));f=OpenGLShadowBindings.create();require(f!=null,"GL3 needed");
			float[] data=new float[COORDS.length/3*5];for(int i=0;i<COORDS.length/3;i++){data[i*5]=COORDS[i*3];data[i*5+1]=COORDS[i*3+1];data[i*5+2]=COORDS[i*3+2];}
			IntBuffer indices=ByteBuffer.allocateDirect(COORDS.length/3*4).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<COORDS.length/3;i++)indices.put(i);indices.flip();
			vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(data),gl.GL_STATIC_DRAW);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,indices,gl.GL_STATIC_DRAW);
			String vertex="#version120\nattribute vec3 aPosition;uniform mat4 uCamera;varying vec3 vShadowWorldPosition;varying float vModelKind;void main(){vShadowWorldPosition=aPosition+vec3(350208.0,0.0,276480.0);vModelKind=1.0;gl_Position=uCamera*vec4(aPosition,1.0);}";
			vertex=vertex.replace("#version120","#version 120");
			int vs=shader(gl.GL_VERTEX_SHADER,vertex),fs=shader(gl.GL_FRAGMENT_SHADER,"#version120\nvarying float vModelKind;uniform float uLightDirectionX;uniform float uLightDirectionY;uniform float uLightDirectionZ;\n".replace("#version120","#version 120")+OpenGLGeometryShadows.FRAGMENT_SOURCE+"void main(){gl_FragColor=vec4(vec3(geometryShadowVisibility()),1.0);}");
			program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);
			pass=new OpenGLGeometryShadows(gl,f);RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);pass.prepare(SCENE,350208+9028,-1515,276480+10953);
			// Same row-major matrices as the captured native frame, converted once.
			float[] view={-1,0,0,9028,0,.77301025f,.6343689f,-5777.132f,0,.6343689f,-.77301025f,9427.85f,0,0,0,1};
			float[] projection={1.0666667f,0,0,0,0,-1.8962963f,.02222222f,0,0,0,1.0015117f,-10.007559f,0,0,1,0};
			float[] column=new float[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)for(int k=0;k<4;k++)column[col*4+row]+=projection[row*4+k]*view[k*4+col];
			f.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);gl.glViewport(0,0,WIDTH,HEIGHT);gl.glUseProgram(program);pass.bindReceiver(program);
			gl.glUniformMatrix4fv(gl.glGetUniformLocation(program,"uCamera"),false,floats(column));
			gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionX"),RendererRemasterLightSettings.getLightDirectionX());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionY"),RendererRemasterLightSettings.getLightDirectionY());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionZ"),RendererRemasterLightSettings.getLightDirectionZ());
			gl.glColorMask(true,true,true,true);gl.glDepthMask(true);gl.glClearColor(1,0,1,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT);gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,20,0L);gl.glDrawElements(gl.GL_TRIANGLES,COORDS.length/3,gl.GL_UNSIGNED_INT,0L);
			ByteBuffer pixels=ByteBuffer.allocateDirect(WIDTH*HEIGHT*4);gl.glReadPixels(0,0,WIDTH,HEIGHT,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
			BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int k=(y*WIDTH+x)*4;image.setRGB(x,HEIGHT-y-1,0xff000000|((pixels.get(k)&255)<<16)|((pixels.get(k+1)&255)<<8)|(pixels.get(k+2)&255));}ImageIO.write(image,"png",output.resolve("actual-terrain.png").toFile());
			for(int y=424;y<=472;y+=4){System.out.print("ROW "+y+":");for(int x=872;x<=936;x+=4)System.out.print(" "+brightness(pixels,x,y));System.out.println();}
			require(brightness(pixels,800,455)>240,"unoccluded neighboring road not lit");
			int leaked=0;for(int y=424;y<=452;y+=4)for(int x=872;x<=924;x+=4)leaked=Math.max(leaked,brightness(pixels,x,y));
			require(leaked<=1,"back-facing true-shadow terrain leaks PCF sunlight: "+leaked+"/255");
			System.out.println("PASS matching native terrain subset: no false grazing-surface sunlight; legitimate depression stays shadowed, adjacent road stays lit.");
		}finally{if(pass!=null)pass.close();if(program!=0)gl.glDeleteProgram(program);if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
	}
}
