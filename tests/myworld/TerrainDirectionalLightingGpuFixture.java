package orsc;

import java.nio.*;
import java.nio.file.*;

/** Small normal/lighting regressions running the production terrain fragment GLSL. */
public final class TerrainDirectionalLightingGpuFixture {
    static LwjglBindings gl;
    static OpenGLShadowBindings functions;
    static int program,vertices,indices;
    static int compile(int type,String source)throws Exception{
        return TerrainLightingGpuFixture.shader(type,source);
    }
    static void value(String name,float value)throws Exception{
        gl.glUniform1f(gl.glGetUniformLocation(program,name),value);
    }
    static int sample(float nx,float ny,float nz,int mode,float kind,float legacy,float strength)throws Exception{
        value("uNormalX",nx);value("uNormalY",ny);value("uNormalZ",nz);
        value("uKind",kind);value("uLegacy",legacy);value("uTerrainReliefStrength",strength);
        gl.glUniform1i(gl.glGetUniformLocation(program,"uReplayMode"),mode);
        gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);
        ByteBuffer pixel=ByteBuffer.allocateDirect(4);gl.glReadPixels(32,32,1,1,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixel);
        return pixel.get(0)&255;
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        gl=LwjglBindings.load();TerrainLightingGpuFixture.gl=gl;
        require(gl.glfwInit(),"GLFW init");long window=0;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
            window=gl.glfwCreateWindow(64,64,"Hidden terrain lighting probes",0,0);require(window!=0,"hidden window");
            gl.glfwMakeContextCurrent(window);gl.createCapabilities();functions=OpenGLShadowBindings.create();
            System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));
            String vertex="#version 120\nattribute vec3 aPosition;uniform float uNormalX,uNormalY,uNormalZ,uKind,uLegacy;varying vec3 vNormal;varying float vModelKind;varying float vBaseLegacyLight;varying vec3 vShadowWorldPosition;varying vec2 vTexCoord,vWorldXZ,vTerrainWorldXZ;varying vec4 vMaterialColor;varying vec3 vRawMaterialColor,vTerrainBlendColor;varying float vTerrainBlendStrength,vTerrainVariationMask,vMaterialFamily,vCameraDepth;void main(){vNormal=vec3(uNormalX,uNormalY,uNormalZ);vModelKind=uKind;vBaseLegacyLight=uLegacy;vShadowWorldPosition=aPosition;vTexCoord=vec2(0.5);vWorldXZ=vec2(0.0);vTerrainWorldXZ=vec2(0.0);vMaterialColor=vec4(0.0,0.0,0.0,1.0);vRawMaterialColor=vec3(0.0);vTerrainBlendColor=vec3(0.0);vTerrainBlendStrength=0.0;vTerrainVariationMask=0.0;vMaterialFamily=0.0;vCameraDepth=1.0;gl_Position=vec4(aPosition,1.0);}";
            ClassLoader loader=TerrainDirectionalLightingGpuFixture.class.getClassLoader();
            java.net.URLClassLoader baseline=null;
            if(args.length==1){baseline=new java.net.URLClassLoader(new java.net.URL[]{Paths.get(args[0]).toUri().toURL()},null);loader=baseline;}
            String fragment=TerrainLightingGpuFixture.fragment(loader);
            if(baseline!=null)baseline.close();
            int vs=compile(gl.GL_VERTEX_SHADER,vertex),fs=compile(gl.GL_FRAGMENT_SHADER,fragment);
            program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glLinkProgram(program);
            require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);
            gl.glUseProgram(program);gl.glViewport(0,0,64,64);gl.glDisable(gl.GL_DEPTH_TEST);gl.glDisable(gl.GL_CULL_FACE);
            float[] positions={-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0};vertices=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vertices);
            gl.glBufferData(gl.GL_ARRAY_BUFFER,TerrainLightingGpuFixture.floats(positions),gl.GL_STATIC_DRAW);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,12,0L);
            IntBuffer order=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<6;i++)order.put(i);order.flip();indices=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,indices);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,order,gl.GL_STATIC_DRAW);
            gl.glUniform1i(gl.glGetUniformLocation(program,"uNearGeometryShadow"),3);gl.glUniform1i(gl.glGetUniformLocation(program,"uFarGeometryShadowMap"),4);
            gl.glUniform1i(gl.glGetUniformLocation(program,"uRemasterLightingEnabled"),1);
            value("uLightDirectionX",1);value("uLightDirectionY",1);value("uLightDirectionZ",0);value("uLightAmbient",.35f);value("uLightIntensity",.65f);value("uObjectReliefStrength",8);
            int toward=sample(-.6f,.8f,0,1,1,100,8),away=sample(.6f,.8f,0,1,1,100,8);
            require(toward>away+100,"Physical sun-facing terrain must be brighter than away: "+toward+"/"+away);
            value("uLightDirectionX",-1);
            int opposite=sample(.6f,.8f,0,1,1,100,8);require(Math.abs(opposite-toward)<=1,"Sun reversal should reverse lit slope");
            int flatLow=sample(0,1,0,1,1,100,0),flatHigh=sample(0,1,0,1,1,100,8);
            require(flatLow==flatHigh,"Terrain Shading must preserve flat palette");
            int zeroSlope=sample(.6f,.8f,0,1,1,100,0);require(zeroSlope==flatLow,"Zero Terrain Shading must remove directional slope contrast");
            int terrainDark=sample(0,1,0,3,1,255,8),terrainLight=sample(0,1,0,3,1,0,8);
            require(terrainDark==255 && terrainLight==255,"Directional terrain must not multiply by fixed legacy relief");
            int objectDark=sample(0,1,0,3,4,255,8),objectLight=sample(0,1,0,3,4,0,8);
            require(objectLight>objectDark+40,"Object relief behavior must remain available");
            for(int elevation:new int[]{5,10,45,85,90}){
                value("uLightDirectionX",(float)Math.cos(Math.toRadians(elevation)));value("uLightDirectionY",(float)Math.sin(Math.toRadians(elevation)));
                for(float strength:new float[]{0,2,8,9.5f}){
                    System.out.print("OVERHEAD elevation="+elevation+" strength="+strength+":");
                    for(int slope:new int[]{0,30,45})for(int sign:new int[]{-1,1}){
                        int level=sample(sign*(float)Math.sin(Math.toRadians(slope)),(float)Math.cos(Math.toRadians(slope)),0,2,1,100,strength);
                        System.out.print(" "+slope+"/"+sign+"="+level);
                    }
                    System.out.println();
                }
                int flat=sample(0,1,0,2,1,100,8);
                require(flat==sample(0,1,0,2,1,100,0),"Flat brightness moved with Terrain Shading at sun"+elevation);
                int towardSlope=sample(-.7071068f,.7071068f,0,2,1,100,8);
                int awaySlope=sample(.7071068f,.7071068f,0,2,1,100,8);
                if(elevation>=85){
                    require(flat-towardSlope>=10 && flat-awaySlope>=10,"Overhead slopes lost shape at sun"+elevation);
                    require(sample(.7071068f,.7071068f,0,2,1,100,2)>awaySlope+8,"Terrain Shading has no meaningful overhead slope control");
                }else require(towardSlope>awaySlope+35,"Low/diagonal sunlight lost facing-slope contrast at sun"+elevation);
            }
            value("uLightIntensity",0);
            int nightToward=sample(-.6f,.8f,0,2,1,0,8),nightAway=sample(.6f,.8f,0,2,1,255,8);
            require(nightToward==nightAway && nightToward>0,"Night ambient must remain readable without fixed sun");
            // Full production main(), not only its diagnostic light helpers:
            // black material + known emission must ignore legacy terrain relief.
            int diagnosticProgram=program;
            int glowVertex=compile(gl.GL_VERTEX_SHADER,vertex);
            int glowFragment=compile(gl.GL_FRAGMENT_SHADER,TerrainLightingGpuFixture.residentFragment(TerrainDirectionalLightingGpuFixture.class.getClassLoader()));
            int glowProgram=gl.glCreateProgram();gl.glAttachShader(glowProgram,glowVertex);gl.glAttachShader(glowProgram,glowFragment);gl.glBindAttribLocation(glowProgram,0,"aPosition");gl.glLinkProgram(glowProgram);
            require(gl.glGetProgrami(glowProgram,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(glowProgram));gl.glDeleteShader(glowVertex);gl.glDeleteShader(glowFragment);
            gl.glUseProgram(glowProgram);program=glowProgram;
            gl.glUniform1i(gl.glGetUniformLocation(program,"uNearGeometryShadow"),3);gl.glUniform1i(gl.glGetUniformLocation(program,"uFarGeometryShadowMap"),4);
            gl.glUniform1i(gl.glGetUniformLocation(program,"uRemasterLightingEnabled"),1);gl.glUniform1i(gl.glGetUniformLocation(program,"uGlowMaskEnabled"),1);gl.glUniform1i(gl.glGetUniformLocation(program,"uGlowMask"),2);
            value("uGlowMaskMinX",-1);value("uGlowMaskMinZ",-1);value("uGlowMaskInvSpanX",.5f);value("uGlowMaskInvSpanZ",.5f);
            value("uBrightness",1);value("uContrast",1);value("uGamma",1);value("uSaturation",1);value("uLightDirectionY",1);
            int glowTexture=gl.glGenTextures();gl.glActiveTexture(gl.GL_TEXTURE0+2);gl.glBindTexture(gl.GL_TEXTURE_2D,glowTexture);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);
            ByteBuffer emission=ByteBuffer.allocateDirect(4);emission.put((byte)64).put((byte)32).put((byte)16).put((byte)255).flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,1,1,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,emission);
            int glowDark=sample(0,1,0,0,1,255,8),glowLight=sample(0,1,0,0,1,0,8);
            require(glowDark==64 && glowLight==64,"Terrain legacy relief modulates independent glow: "+glowDark+"/"+glowLight);
            gl.glDeleteTextures(glowTexture);gl.glDeleteProgram(glowProgram);program=diagnosticProgram;
            System.out.println("PASS production terrain lighting: physical normal basis, continuous sun-relative slope control, flat palette, legacy independence, unchanged object relief, night ambient and full-shader independent glow; toward/away="+toward+"/"+away);
        }finally{
            if(program!=0)gl.glDeleteProgram(program);if(vertices!=0)gl.glDeleteBuffers(vertices);if(indices!=0)gl.glDeleteBuffers(indices);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();
        }
    }
}
