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
		int[] result=new int[5*4*6*3+18];int at=0;
		for(int x=8832;x<9472;x+=128)for(int z=9088;z<9600;z+=128) {
			int[] corners={x+128,z,x,z,x,z+128,x,z+128,x+128,z+128,x+128,z};
			for(int i=0;i<corners.length;i+=2) {
				int vx=corners[i],vz=corners[i+1];result[at++]=vx;
				result[at++]=vx==9088&&vz==9344?-294:-384;result[at++]=vz;
			}
		}
		// Separate opaque card shadows a flat, sun-facing road receiver. This
		// distinguishes actual cast occlusion from the grazing lighting terminator.
		int[] card={9050,-384,9140,9050,-650,9140,9050,-650,9278,9050,-384,9140,9050,-650,9278,9050,-384,9278};
		System.arraycopy(card,0,result,at,card.length);
		return result;
	}

	static LwjglBindings gl;static OpenGLShadowBindings f;static int vbo,ebo;
	static FloatBuffer floats(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).flip();return b;}
	static int shader(int type,String source)throws Exception{int s=gl.glCreateShader(type);gl.glShaderSource(s,source);gl.glCompileShader(s);if(gl.glGetShaderi(s,gl.GL_COMPILE_STATUS)==0)throw new AssertionError(gl.glGetShaderInfoLog(s));return s;}
	static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
	static int brightness(ByteBuffer p,int x,int y){return p.get(((HEIGHT-y-1)*WIDTH+x)*4)&255;}
	static final OpenGLGeometryShadows.Scene SCENE=new OpenGLGeometryShadows.Scene(){
		public long staticSignature(){return 1L;}
        public double referenceHeight(){return -384;}
		public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection p,boolean staticOnly)throws Exception{
			pass.bindChunk(vbo,ebo,32,12,350208,276480);pass.drawRange(0,false,0,COORDS.length/3);
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
			float[] data=new float[COORDS.length/3*8];
            java.util.Map<String,double[]> normals=new java.util.HashMap<String,double[]>();
            for(int i=0;i<COORDS.length;i+=9){
                double ax=COORDS[i+3]-COORDS[i],ay=COORDS[i+4]-COORDS[i+1],az=COORDS[i+5]-COORDS[i+2];
                double bx=COORDS[i+6]-COORDS[i],by=COORDS[i+7]-COORDS[i+1],bz=COORDS[i+8]-COORDS[i+2];
                double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                if(ny<0){nx=-nx;ny=-ny;nz=-nz;}
                for(int c=0;c<9;c+=3){String key=COORDS[i+c]+","+COORDS[i+c+1]+","+COORDS[i+c+2];double[] n=normals.get(key);if(n==null){n=new double[3];normals.put(key,n);}n[0]+=nx;n[1]+=ny;n[2]+=nz;}
            }
            for(int i=0;i<COORDS.length/3;i++){
                for(int a=0;a<3;a++)data[i*8+a]=COORDS[i*3+a];
                double[] n=normals.get(COORDS[i*3]+","+COORDS[i*3+1]+","+COORDS[i*3+2]);double length=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
                for(int a=0;a<3;a++)data[i*8+5+a]=(float)(n[a]/Math.max(1e-9,length));
            }
			IntBuffer indices=ByteBuffer.allocateDirect(COORDS.length/3*4).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<COORDS.length/3;i++)indices.put(i);indices.flip();
			vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(data),gl.GL_STATIC_DRAW);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,indices,gl.GL_STATIC_DRAW);
			String vertex="#version120\nattribute vec3 aPosition;attribute vec3 aNormal;uniform mat4 uCamera;varying vec3 vShadowWorldPosition;varying vec3 vNormal;varying float vModelKind;varying float vBaseLegacyLight;void main(){vShadowWorldPosition=aPosition+vec3(350208.0,0.0,276480.0);vModelKind=1.0;vNormal=aNormal;vBaseLegacyLight=100.0;gl_Position=uCamera*vec4(aPosition,1.0);}";
			vertex=vertex.replace("#version120","#version 120");
			String fragment=TerrainLightingGpuFixture.fragment(TerrainShadowGpuFixture.class.getClassLoader());
            int vs=shader(gl.GL_VERTEX_SHADER,vertex),fs=shader(gl.GL_FRAGMENT_SHADER,fragment);
			program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glBindAttribLocation(program,2,"aNormal");gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);
			pass=new OpenGLGeometryShadows(gl,f);RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);pass.prepare(SCENE,350208+9028,-1515,276480+10953);
			// Same row-major matrices as the captured native frame, converted once.
			float[] view={-1,0,0,9028,0,.77301025f,.6343689f,-5777.132f,0,.6343689f,-.77301025f,9427.85f,0,0,0,1};
			float[] projection={1.0666667f,0,0,0,0,-1.8962963f,.02222222f,0,0,0,1.0015117f,-10.007559f,0,0,1,0};
			float[] column=new float[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)for(int k=0;k<4;k++)column[col*4+row]+=projection[row*4+k]*view[k*4+col];
			f.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);gl.glViewport(0,0,WIDTH,HEIGHT);gl.glUseProgram(program);pass.bindReceiver(program);
			gl.glUniformMatrix4fv(gl.glGetUniformLocation(program,"uCamera"),false,floats(column));
			gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionX"),RendererRemasterLightSettings.getLightDirectionX());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionY"),RendererRemasterLightSettings.getLightDirectionY());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionZ"),RendererRemasterLightSettings.getLightDirectionZ());
			gl.glUniform1f(gl.glGetUniformLocation(program,"uTerrainReliefStrength"),8);
            gl.glUniform1i(gl.glGetUniformLocation(program,"uRemasterLightingEnabled"),1);
            gl.glColorMask(true,true,true,true);gl.glDepthMask(true);gl.glClearColor(1,0,1,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT);gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);gl.glDisable(gl.GL_DEPTH_TEST);
			gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,32,0L);gl.glEnableVertexAttribArray(2);gl.glVertexAttribPointer(2,3,gl.GL_FLOAT,false,32,20L);gl.glDrawElements(gl.GL_TRIANGLES,COORDS.length/3,gl.GL_UNSIGNED_INT,0L);
			ByteBuffer pixels=ByteBuffer.allocateDirect(WIDTH*HEIGHT*4);gl.glReadPixels(0,0,WIDTH,HEIGHT,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
			BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int k=(y*WIDTH+x)*4;image.setRGB(x,HEIGHT-y-1,0xff000000|((pixels.get(k)&255)<<16)|((pixels.get(k+1)&255)<<8)|(pixels.get(k+2)&255));}ImageIO.write(image,"png",output.resolve("actual-terrain.png").toFile());
			// A flat, positive-diffuse road receiver behind the opaque card must
            // still be genuinely occluded; no geometric-normal zero shortcut.
            require(brightness(pixels,1025,439)<=1,"opaque card does not shadow sun-facing road");
            require(brightness(pixels,800,455)>240,"unoccluded neighboring road not lit");
            gl.glUniform1i(gl.glGetUniformLocation(program,"uReplayMode"),4);
            // Read the actual production lighting response separately: raw PCF
            // visibility alone is not irradiance on a grazing/back-facing face.
            gl.glDrawElements(gl.GL_TRIANGLES,COORDS.length/3,gl.GL_UNSIGNED_INT,0L);
            gl.glReadPixels(0,0,WIDTH,HEIGHT,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
            int road=brightness(pixels,800,455),maximum=0;
            for(int y=424;y<=452;y+=4)for(int x=872;x<=924;x+=4)maximum=Math.max(maximum,brightness(pixels,x,y));
            require(road>80,"sun-facing road lost direct sunlight");
            require(maximum<=road-10,"grazing depression must receive less direct sunlight than exposed road: "+maximum+"/"+road);
            System.out.println("PASS production grazing terrain: valid opaque-card cast occlusion retained; depression direct-light maximum="+maximum+", exposed road="+road+".");
		}finally{if(pass!=null)pass.close();if(program!=0)gl.glDeleteProgram(program);if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
	}
}
