package orsc;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Optional replay of an immutable native terrain export through production GLSL.
 * No server, scene authority shortcut, input bridge, or visible/focused window. */
public final class TerrainLightingGpuFixture {
    static final int W=1920,H=1080;
    static LwjglBindings gl; static OpenGLShadowBindings f;
    static int vbo,ebo,count; static float originX,originZ;
    static FloatBuffer floats(float[] values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values).flip();return b;}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int shader(int type,String source)throws Exception{int s=gl.glCreateShader(type);gl.glShaderSource(s,source);gl.glCompileShader(s);require(gl.glGetShaderi(s,gl.GL_COMPILE_STATUS)!=0,gl.glGetShaderInfoLog(s));return s;}
    static float[] field(String json,String name){Matcher m=Pattern.compile("\\\""+name+"\\\"\\s*:\\s*(\\[[^]]*\\]|[-+0-9.eE]+)").matcher(json);require(m.find(),"Missing field "+name);String[] values=m.group(1).replace("[","").replace("]","").split(",");float[] result=new float[values.length];for(int i=0;i<values.length;i++)result[i]=Float.parseFloat(values[i].trim());return result;}
    static String fragment(ClassLoader loader)throws Exception{Class<?> type=Class.forName("orsc.OpenGLShaderProgram",true,loader);Field source=type.getDeclaredField("RESIDENT_CHUNK_PARITY_FRAGMENT_SHADER");source.setAccessible(true);String s=(String)source.get(null);return s.substring(0,s.lastIndexOf("void main()"))+"uniform int uReplayMode;void main(){float d=remasterDiffuse(normalize(vec3(uLightDirectionX,uLightDirectionY,uLightDirectionZ)));float v=geometryShadowVisibility();float result=uReplayMode==0?v:uReplayMode==1?d:uReplayMode==3?remasterLocalReliefFactor():uReplayMode==4?d*v:remasterClassicShadeFactor(d*v)*remasterLocalReliefFactor();gl_FragColor=vec4(vec3(result),1.0);}";}
    static final OpenGLGeometryShadows.Scene SCENE=new OpenGLGeometryShadows.Scene(){public long staticSignature(){return 1;}public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection projection,boolean staticOnly)throws Exception{pass.bindChunk(vbo,ebo,32,12,originX,originZ);pass.drawRange(0,false,0,count);}};
    static void load(Path file)throws Exception{
        List<float[]> triangles=new ArrayList<float[]>();
        try(BufferedReader r=Files.newBufferedReader(file)){String[] names=r.readLine().split("\\t");Map<String,Integer> columns=new HashMap<String,Integer>();for(int i=0;i<names.length;i++)columns.put(names[i],i);String row;
            while((row=r.readLine())!=null){String[] t=row.split("\\t");if(triangles.isEmpty()){originX=Float.parseFloat(t[columns.get("logicalOffsetX")]);originZ=Float.parseFloat(t[columns.get("logicalOffsetZ")]);}// Native exported vertices are already in the PRIMARY presentation basis,
                // including halo chunks. Their distinct logical origins are residency
                // metadata (GeometryShadowCoordinates contract), not extra translations.
                require(Float.parseFloat(t[columns.get("vertexOffsetX")])==0 && Float.parseFloat(t[columns.get("vertexOffsetZ")])==0,"Replay requires zero presentation rebase offsets");
                require(Integer.parseInt(t[columns.get("plane")])==0,"Replay currently supports receiver plane0 only");
                if(triangles.isEmpty())require(Integer.parseInt(t[columns.get("chunkIndex")])==0,"Primary presentation chunk must be first");
                float[] v=new float[24];for(int i=0;i<3;i++){for(int a=0;a<3;a++)v[i*8+a]=Float.parseFloat(t[columns.get("xyz".charAt(a)+""+i)]);for(int a=0;a<3;a++)v[i*8+5+a]=Float.parseFloat(t[columns.get("n"+"xyz".charAt(a)+i)])/256f;}triangles.add(v);}
        }
        count=triangles.size()*3;float[] data=new float[count*8];int at=0;for(float[] t:triangles){System.arraycopy(t,0,data,at,t.length);at+=t.length;}IntBuffer indices=ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<count;i++)indices.put(i);indices.flip();vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(data),gl.GL_STATIC_DRAW);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,indices,gl.GL_STATIC_DRAW);
    }
    static void uniform(int program,String name,float value)throws Exception{gl.glUniform1f(gl.glGetUniformLocation(program,name),value);}
    static void image(Path file,ByteBuffer p)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int k=(y*W+x)*4;image.setRGB(x,H-y-1,0xff000000|((p.get(k)&255)<<16)|((p.get(k+1)&255)<<8)|(p.get(k+2)&255));}ImageIO.write(image,"png",file.toFile());}
    public static void main(String[] args)throws Exception{
        require(args.length>=2,"Usage: TerrainLightingGpuFixture export-directory output-directory [baseline-jar]");Path input=Paths.get(args[0]),output=Paths.get(args[1]);Files.createDirectories(output);String frame=new String(Files.readAllBytes(input.resolve("terrain-frame.json")),"UTF-8");System.setProperty("spoiledmilk.remasterLightAzimuth",String.valueOf(field(frame,"sunAzimuth")[0]));System.setProperty("spoiledmilk.remasterLightElevation",String.valueOf(field(frame,"sunElevation")[0]));
        gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLGeometryShadows pass=null;
        try{gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(W,H,"Hidden native terrain lighting replay",0,0);require(window!=0,"GLFW window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));f=OpenGLShadowBindings.create();require(f!=null,"GL3 support");load(input.resolve("terrain-triangles.tsv"));pass=new OpenGLGeometryShadows(gl,f);RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);pass.prepare(SCENE,originX+field(frame,"getCameraOffsetX")[0],field(frame,"getCameraOffsetY")[0],originZ+field(frame,"getCameraOffsetZ")[0]);
            float[] view=field(frame,"worldViewMatrix"),projection=field(frame,"projectionMatrix"),camera=new float[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)for(int k=0;k<4;k++)camera[col*4+row]+=projection[row*4+k]*view[k*4+col];
            for(int variant=0;variant<(args.length==3?2:1);variant++){
                ClassLoader loader=TerrainLightingGpuFixture.class.getClassLoader();URLClassLoader baseline=null;if(variant==1){baseline=new URLClassLoader(new java.net.URL[]{Paths.get(args[2]).toUri().toURL()},null);loader=baseline;}
                String vertex="#version 120\nattribute vec3 aPosition;attribute vec3 aNormal;uniform mat4 uCamera;uniform float uOriginX;uniform float uOriginZ;varying vec3 vShadowWorldPosition;varying vec3 vNormal;varying float vModelKind;varying float vBaseLegacyLight;void main(){vShadowWorldPosition=aPosition+vec3(uOriginX,0.0,uOriginZ);vNormal=aNormal;vModelKind=1.0;vBaseLegacyLight=100.0;gl_Position=uCamera*vec4(aPosition,1.0);}";
                int vs=shader(gl.GL_VERTEX_SHADER,vertex),fs=shader(gl.GL_FRAGMENT_SHADER,fragment(loader));int program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glBindAttribLocation(program,2,"aNormal");gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);
                f.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);gl.glViewport(0,0,W,H);gl.glUseProgram(program);pass.bindReceiver(program);gl.glUniformMatrix4fv(gl.glGetUniformLocation(program,"uCamera"),false,floats(camera));uniform(program,"uOriginX",originX);uniform(program,"uOriginZ",originZ);uniform(program,"uLightDirectionX",RendererRemasterLightSettings.getLightDirectionX());uniform(program,"uLightDirectionY",RendererRemasterLightSettings.getLightDirectionY());uniform(program,"uLightDirectionZ",RendererRemasterLightSettings.getLightDirectionZ());uniform(program,"uLightAmbient",.35f);uniform(program,"uLightIntensity",.65f);uniform(program,"uTerrainReliefStrength",8);gl.glUniform1i(gl.glGetUniformLocation(program,"uRemasterLightingEnabled"),1);
                gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_POLYGON_OFFSET_FILL);gl.glEnable(gl.GL_DEPTH_TEST);f.depthDefaults();gl.glDepthMask(true);gl.glColorMask(true,true,true,true);gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,32,0L);gl.glEnableVertexAttribArray(2);gl.glVertexAttribPointer(2,3,gl.GL_FLOAT,false,32,20L);
                for(int mode=0;mode<3;mode++){gl.glUniform1i(gl.glGetUniformLocation(program,"uReplayMode"),mode);gl.glClearColor(1,0,1,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT);gl.glDrawElements(gl.GL_TRIANGLES,count,gl.GL_UNSIGNED_INT,0L);ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);gl.glReadPixels(0,0,W,H,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);image(output.resolve((variant==0?"current":"baseline")+"-"+mode+".png"),pixels);System.out.print((variant==0?"current":"baseline")+" mode"+mode+":");for(int[] p:new int[][]{{1410,420},{1425,438},{1518,455},{1500,445},{1500,610},{1555,615},{1600,650}})System.out.print(" "+(pixels.get(((H-p[1]-1)*W+p[0])*4)&255));System.out.println();
                    if(variant==0 && mode==1){
                        int darkest=255,brightest=0;for(int[] q:new int[][]{{1410,420},{1425,438},{1518,455},{1500,445}}){int value=pixels.get(((H-q[1]-1)*W+q[0])*4)&255;darkest=Math.min(darkest,value);brightest=Math.max(brightest,value);}
                        require(brightest<64,"Away-facing native hillside incorrectly gets strong sunlight: "+brightest);
                        require(brightest-darkest<24,"Native hillside smooth-light discontinuity: "+(brightest-darkest));
                    }
                    if(variant==1 && mode==1)require((pixels.get(((H-420-1)*W+1410)*4)&255)>230,"Baseline no longer reproduces reversed slope light");
                }
                gl.glDeleteProgram(program);if(baseline!=null)baseline.close();
            }
            require(((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null)).intValue()==0,"GL error");System.out.println("PASS native terrain replay completed "+count/3+" triangles (diagnostic values, not presentation timing).");
        }finally{if(pass!=null)pass.close();if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
