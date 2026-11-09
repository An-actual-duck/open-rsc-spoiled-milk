package orsc;

import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Hidden controlled thin-cutout / opaque shadow temporal evidence, not FPS acceptance. */
public final class PicketShadowWaveGpuFixture {
    static final int W=512,H=256;
    static LwjglBindings gl;
    static OpenGLShadowBindings functions;
    static int vbo,ebo,texture,program;
    static boolean alpha;
    static final byte[] gray = new byte[W*H];
    static long signature, shadowContext;
    static final java.security.MessageDigest PIXEL_HASH = pixelHash();
    static java.security.MessageDigest pixelHash(){try{return java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static String pixelDigest(){byte[] digest=PIXEL_HASH.digest(gray);StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format("%02x",b&255));return s.toString();}
    static float fenceZ=6500;
    static boolean nativeFence;
    static float worldOffsetX, worldOffsetZ, casterOffsetX;
    static final float BASE_HEIGHT=Float.parseFloat(System.getProperty("probe.height","0"));
    static float worldOffsetY=BASE_HEIGHT;
    static boolean movingCaster;
    // Native timber10 triangles41388/41389 and railing12 triangles41704/41705
    // share these exact translated positions/UVs (see picket-shadow-wave.txt).
    static final float[] NATIVE_QUAD={64,0,-64,0,0,64,-192,-64,1,0,-64,-192,64,1,1,64,0,-64,0,0,-64,-192,64,1,1,-64,0,64,0,1};
    static FloatBuffer floats(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).flip();return b;}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static int compile(int type,String source)throws Exception{int s=gl.glCreateShader(type);gl.glShaderSource(s,source);gl.glCompileShader(s);require(gl.glGetShaderi(s,gl.GL_COMPILE_STATUS)!=0,gl.glGetShaderInfoLog(s));return s;}
    static final int COPIES=Integer.getInteger("probe.copies",8);
    static void mesh()throws Exception{
        float[] vertices=new float[(6+COPIES*6)*5];
        float[] ground={-8192,0,-8192,0,0,8192,0,-8192,0,0,8192,0,16000,0,0,-8192,0,-8192,0,0,8192,0,16000,0,0,-8192,0,16000,0,0};
        System.arraycopy(ground,0,vertices,0,30);
        for(int q=0;q<COPIES;q++)for(int i=0;i<6;i++){
            int at=30+(q*6+i)*5;for(int axis=0;axis<5;axis++)vertices[at+axis]=NATIVE_QUAD[i*5+axis];
            vertices[at]+=casterOffsetX;vertices[at]-=(q-(COPIES-1)*.5f)*128;vertices[at+2]+=fenceZ+(q-(COPIES-1)*.5f)*128;
        }
        for(int i=0;i<vertices.length;i+=5){vertices[i]+=worldOffsetX;vertices[i+1]+=worldOffsetY;vertices[i+2]+=worldOffsetZ;}
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(vertices),gl.GL_STATIC_DRAW);
        IntBuffer indices=ByteBuffer.allocateDirect((6+COPIES*6)*4).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<6+COPIES*6;i++)indices.put(i);indices.flip();
        gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,indices,gl.GL_STATIC_DRAW);
    }
    static final OpenGLGeometryShadows.Scene SCENE=new OpenGLGeometryShadows.Scene(){
        public long staticSignature(){return signature;}
        public double referenceHeight(){return worldOffsetY;}
        public long historyContext(){return shadowContext;}
        public void drawDepth(OpenGLGeometryShadows pass,DirectionalShadowProjection projection,boolean staticOnly)throws Exception{
            pass.bindChunk(vbo,ebo,20,12,0,0);pass.drawRange(0,false,0,6);if(!staticOnly||!movingCaster)pass.drawRange(texture,alpha,6,COPIES*6);
        }
    };
    static double frame(OpenGLGeometryShadows pass,float centerX,float centerZ,float spanX,float spanZ)throws Exception{
        gl.glUseProgram(program);pass.bindReceiver(program);gl.glUniform1f(gl.glGetUniformLocation(program,"uCenterX"),centerX);gl.glUniform1f(gl.glGetUniformLocation(program,"uCenterZ"),centerZ);gl.glUniform1f(gl.glGetUniformLocation(program,"uSpanX"),spanX);gl.glUniform1f(gl.glGetUniformLocation(program,"uSpanZ"),spanZ);
        gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionX"),RendererRemasterLightSettings.getLightDirectionX());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionY"),RendererRemasterLightSettings.getLightDirectionY());gl.glUniform1f(gl.glGetUniformLocation(program,"uLightDirectionZ"),RendererRemasterLightSettings.getLightDirectionZ());
        functions.bindFramebuffer(OpenGLShadowBindings.FRAMEBUFFER,0);gl.glViewport(0,0,W,H);gl.glDisable(gl.GL_DEPTH_TEST);gl.glDisable(gl.GL_CULL_FACE);gl.glColorMask(true,true,true,true);gl.glClearColor(1,1,1,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT);
        gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,20,0L);gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);
        ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);gl.glReadPixels(0,0,W,H,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);double energy=0;for(int i=0;i<W*H;i++){gray[i]=pixels.get(i*4);energy+=1.0-(gray[i]&255)/255.0;}return energy*spanX*spanZ/(W*H);
    }
    static String[] probeModes(){String c=System.getProperty("probe.cascade","combined");return c.equals("near")?new String[]{"near-fixed-world","near-sun-world","height-sun-world","near-caster-world"}:c.equals("far")?new String[]{"mixed-fixed-world","mixed-sun-world","far-fixed-world","far-sun-world"}:new String[]{"near-fixed-world","near-sun-world","mixed-fixed-world","mixed-sun-world","far-fixed-world","far-sun-world","cascade-motion"};}
    static String isolatedSource(){String source=OpenGLGeometryShadows.FRAGMENT_SOURCE;String cascade=System.getProperty("probe.cascade","combined");if(cascade.equals("near"))return source.replace("return mix(nearVisibility, farVisibility, blend);","return nearVisibility;");if(cascade.equals("far"))return source.replace("return mix(nearVisibility, farVisibility, blend);","return farVisibility;");return source;}
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);System.setProperty("spoiledmilk.remasterLightAzimuth","90");System.setProperty("spoiledmilk.remasterLightElevation",System.getProperty("probe.elevation","45"));
        gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLGeometryShadows pass=null;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(W,H,"Hidden thin-shadow temporal fixture",0,0);require(window!=0,"hidden context");gl.glfwMakeContextCurrent(window);gl.createCapabilities();functions=OpenGLShadowBindings.create();require(functions!=null,"GL3 required");System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));
            vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();texture=gl.glGenTextures();gl.glBindTexture(gl.GL_TEXTURE_2D,texture);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_S,gl.GL_CLAMP_TO_EDGE);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_T,gl.GL_CLAMP_TO_EDGE);
            ByteBuffer cutout=ByteBuffer.allocateDirect(64*64*4);for(int y=0;y<64;y++)for(int x=0;x<64;x++){boolean solid=x%16<4||y%24<4;cutout.put((byte)255).put((byte)255).put((byte)255).put(solid?(byte)255:0);}cutout.flip();int textureSize=64;
            if(args.length>2){
                nativeFence=true;textureSize=128;cutout=ByteBuffer.allocateDirect(128*128*4);boolean[] seen=new boolean[128*128];int loaded=0;
                try(java.io.BufferedReader reader=Files.newBufferedReader(Paths.get(args[1]))){reader.readLine();String row;while((row=reader.readLine())!=null){String[] values=row.split("\\t");if(!values[0].equals("12"))continue;require(values[1].equals("128")&&values[2].equals("128"),"Unexpected native texture12 canvas");int x=Integer.parseInt(values[4]),y=Integer.parseInt(values[5]),at=y*128+x;require(!seen[at],"Duplicate native alpha pixel");seen[at]=true;loaded++;long rgba=Long.parseLong(values[6]);for(int c=0;c<4;c++)cutout.put(at*4+c,(byte)(rgba>>((3-c)*8)));}}
                require(loaded==128*128,"Incomplete native texture12 export");
            }
            if(args.length<=2){
                Properties properties=new Properties();
                try(java.io.InputStream source=Files.newInputStream(Paths.get(args[1]))){properties.load(source);}
                textureSize=Integer.parseInt(properties.getProperty("width"));
                byte[] bits=Base64.getDecoder().decode(properties.getProperty("alphaBits"));require(bits.length==textureSize*textureSize/8,"Native mask size");
                nativeFence=true;cutout=ByteBuffer.allocateDirect(textureSize*textureSize*4);int opaque=0;
                for(int i=0;i<textureSize*textureSize;i++){boolean solid=(bits[i/8]&(1<<(i%8)))!=0;if(solid)opaque++;cutout.put((byte)255).put((byte)255).put((byte)255).put(solid?(byte)255:0);}cutout.flip();
                require(opaque==Integer.parseInt(properties.getProperty("opaquePixels")),"Native mask coverage");
            }
            gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,textureSize,textureSize,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,cutout);
            String vertex="#version 120\nattribute vec3 aPosition;uniform float uCenterX,uCenterZ,uSpanX,uSpanZ;varying vec3 vShadowWorldPosition;varying float vModelKind;void main(){vShadowWorldPosition=aPosition;vModelKind=1.0;gl_Position=vec4((aPosition.x-uCenterX)*2.0/uSpanX,(aPosition.z-uCenterZ)*2.0/uSpanZ,0.0,1.0);}";
            int vs=compile(gl.GL_VERTEX_SHADER,vertex),fs=compile(gl.GL_FRAGMENT_SHADER,"#version 120\nvarying float vModelKind;uniform float uLightDirectionX,uLightDirectionY,uLightDirectionZ;"+isolatedSource()+"void main(){gl_FragColor=vec4(vec3(geometryShadowVisibility()),1.0);}");program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);
            pass=new OpenGLGeometryShadows(gl,functions);RendererShadowSettings.setQuality(Boolean.getBoolean("probe.low")?RendererShadowSettings.Quality.LOW:RendererShadowSettings.Quality.HIGH);
            double nearMean=-1;
            StringBuilder csv=new StringBuilder("case,cutout,frame,azimuth,farRebuilt,shadowArea,pixelSha256\n");
            for(boolean cut:new boolean[]{true})for(String mode:probeModes()){
                worldOffsetX=mode.endsWith("world")?64000:0;worldOffsetZ=mode.endsWith("world")?59000:0;
                movingCaster=mode.equals("near-caster-world");casterOffsetX=0;
                worldOffsetY=BASE_HEIGHT;alpha=cut;signature++;shadowContext++;RendererRemasterLightSettings.adjustElevation(Float.parseFloat(System.getProperty("probe.elevation","45"))-RendererRemasterLightSettings.getElevationDegrees());RendererRemasterLightSettings.adjustAzimuth(90-RendererRemasterLightSettings.getAzimuthDegrees());
                fenceZ=mode.equals("cascade-motion")?Float.parseFloat(System.getProperty("probe.mixedZ","3400")):(mode.startsWith("near")||mode.startsWith("height"))?1000:mode.startsWith("mixed")?Float.parseFloat(System.getProperty("probe.mixedZ","5100")):6500;mesh();
                double total=0,min=Double.POSITIVE_INFINITY,max=0,delta=0,previous=0;int frames=180;byte[] previousPixels=new byte[W*H];double residual=0,maxResidual=0,firstCentroid=0,lastCentroid=0;
                for(int i=0;i<frames;i++){
                    if(movingCaster){casterOffsetX=i*2;mesh();}
                    if(mode.startsWith("height")){worldOffsetY=BASE_HEIGHT+i*.5f;mesh();}
                    if(mode.contains("sun")){RendererRemasterLightSettings.adjustAzimuth(.002f);if(Boolean.getBoolean("probe.bothAngles"))RendererRemasterLightSettings.adjustElevation(.0008f);if(mode.equals("sun-rebuild"))signature++;}
                    float cameraShift=mode.startsWith("camera")?i*.5f:0;
                    float eyeX=mode.equals("cascade-motion")?(float)Math.cos(i*Math.PI/32)*1024:0;
                    float eyeZ=mode.equals("cascade-motion")?(float)Math.sin(i*Math.PI/32)*1024:0;
                    pass.prepare(SCENE,eyeX+worldOffsetX,-256+worldOffsetY,eyeZ+worldOffsetZ);float span=2048;
                    double energy=frame(pass,cameraShift+worldOffsetX,fenceZ-128+worldOffsetZ,span,span);csv.append(mode).append(',').append(cut).append(',').append(i).append(',').append(RendererRemasterLightSettings.getAzimuthDegrees()).append(',').append(pass.farRebuilt).append(',').append(energy).append(',').append(pixelDigest()).append('\n');
                    if(movingCaster) {
                        double weighted=0,weight=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){double w=255-(gray[y*W+x]&255);weighted+=w*x;weight+=w;}
                        double centroid=weighted/Math.max(1,weight)*span/W;if(i==0)firstCentroid=centroid;lastCentroid=centroid;
                    }
                    if(i>0){
                        double shiftPixels=mode.startsWith("camera")?.5*W/span:0,error=0,support=0;
                        for(int y=0;y<H;y++)for(int x=0;x<W-2;x++){
                            double expected=(previousPixels[y*W+x]&255)*(1-shiftPixels)+(previousPixels[y*W+x+1]&255)*shiftPixels;
                            int actual=gray[y*W+x]&255;error+=Math.abs(actual-expected);support+=255-actual;
                        }
                        double relative=error/Math.max(1,support);residual+=relative;maxResidual=Math.max(maxResidual,relative);
                    }
                    if(mode.contains("sun")&&i%6==0){
                        Path dir=output.resolve(mode);Files.createDirectories(dir);byte[] header=("P5\n"+W+" "+H+"\n255\n").getBytes("US-ASCII");byte[] file=new byte[header.length+gray.length];System.arraycopy(header,0,file,0,header.length);System.arraycopy(gray,0,file,header.length,gray.length);Files.write(dir.resolve(String.format("frame-%04d.pgm",i)),file);
                    }
                    System.arraycopy(gray,0,previousPixels,0,gray.length);
                    total+=energy;min=Math.min(min,energy);max=Math.max(max,energy);if(i>0)delta=Math.max(delta,Math.abs(energy-previous));previous=energy;
                }
                double mean=total/frames;
                Files.write(output.resolve("temporal.csv"),csv.toString().getBytes("UTF-8"));
                require(mean>1000,"Shadow was removed instead of stabilized: "+mode);
                if(mode.equals("near-fixed-world")&&!Boolean.getBoolean("probe.low")&&System.getProperty("probe.elevation","45").equals("45")) {
                    require(mean>(textureSize==64?130000:45000)&&mean<(textureSize==64?150000:54000),"Native repeated shadow coverage changed: "+mean);
                }
                if(mode.equals("cascade-motion")) {
                    require(min>max*.70,"Cascade transition loses shadow coverage: "+min+"/"+max);
                    require(maxResidual<.25,"Cascade transition has an abrupt spatial discontinuity: "+maxResidual);
                }
                if(mode.equals("near-sun-world")&&!Boolean.getBoolean("probe.low")&&System.getProperty("probe.elevation","45").equals("45")) {
                    // Includes legitimate solar motion; constrains persistent beat, not zero physical movement.
                    require(residual/(frames-1)<.002,"Repeated near fence beat returned: "+residual/(frames-1));
                    require(maxResidual<.09,"Near cutout single step exceeds recorded bound: "+maxResidual);
                }
                if(mode.contains("fixed"))require(max-min<.01,"Stationary camera/sun shadows are not stable");
                if(movingCaster)require(Math.abs((lastCentroid-firstCentroid)-358)<(Boolean.getBoolean("probe.low")?12:4),"Moving caster shadow was cached or trailed: "+(lastCentroid-firstCentroid));
                System.out.println(mode+" cutout="+cut+" mean="+total/frames+" min="+min+" max="+max+" maxFrameDelta="+delta+" rawChangeMean="+residual/(frames-1)+" rawChangeMax="+maxResidual);
            }
            Files.write(output.resolve("temporal.csv"),csv.toString().getBytes("UTF-8"));
        }finally{if(pass!=null)pass.close();if(program!=0)gl.glDeleteProgram(program);if(texture!=0)gl.glDeleteTextures(texture);if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
