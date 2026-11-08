package orsc;
import java.nio.*;
/** Hidden probes exercise production relief/field GLSL and unchanged full-shader emission. */
public final class TerrainDefinitionGpuFixture {
    static LwjglBindings gl; static int program,vbo,ebo,field,glow;
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void f(String n,float v)throws Exception{gl.glUniform1f(gl.glGetUniformLocation(program,n),v);}
    static void i(String n,int v)throws Exception{gl.glUniform1i(gl.glGetUniformLocation(program,n),v);}
    static int sample(float nx,float ny,float legacy,float kind,int mode,float strength)throws Exception {
        f("uNormalX",nx);f("uNormalY",ny);f("uLegacy",legacy);f("uKind",kind);i("uTerrainDefinitionMode",mode);f("uTerrainReliefStrength",strength);
        gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);ByteBuffer p=ByteBuffer.allocateDirect(4);gl.glReadPixels(32,32,1,1,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,p);return p.get(0)&255;
    }
    static void pixel(int unit,int tex,int r,int g,int b,int a)throws Exception {
        gl.glActiveTexture(gl.GL_TEXTURE0+unit);gl.glBindTexture(gl.GL_TEXTURE_2D,tex);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_LINEAR);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_LINEAR);
        ByteBuffer p=ByteBuffer.allocateDirect(4);p.put((byte)r).put((byte)g).put((byte)b).put((byte)a).flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,1,1,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,p);gl.glActiveTexture(gl.GL_TEXTURE0);
    }
    static void link(boolean full)throws Exception {
        if(program!=0)gl.glDeleteProgram(program);
        String vertex="#version 120\nattribute vec3 aPosition;uniform float uNormalX,uNormalY,uKind,uLegacy,uBaseColor;varying vec3 vNormal,vShadowWorldPosition,vRawMaterialColor,vTerrainBlendColor;varying vec4 vMaterialColor;varying vec2 vTexCoord,vWorldXZ,vTerrainWorldXZ;varying float vModelKind,vBaseLegacyLight,vTerrainBlendStrength,vTerrainVariationMask,vMaterialFamily,vCameraDepth;void main(){vNormal=vec3(uNormalX,uNormalY,0);vModelKind=uKind;vBaseLegacyLight=uLegacy;vShadowWorldPosition=aPosition;vTexCoord=vec2(0.5);vWorldXZ=vec2(0);vTerrainWorldXZ=vec2(0);vMaterialColor=vec4(vec3(uBaseColor),1);vRawMaterialColor=vec3(0);vTerrainBlendColor=vec3(0);vTerrainBlendStrength=0;vTerrainVariationMask=0;vMaterialFamily=0;vCameraDepth=1;gl_Position=vec4(aPosition,1);}";
        String fragment=TerrainLightingGpuFixture.residentFragment(TerrainDefinitionGpuFixture.class.getClassLoader());if(!full)fragment=fragment.substring(0,fragment.lastIndexOf("void main()"))+"void main(){gl_FragColor=vec4(vec3(terrainDefinitionFactor()*0.5),1.0);}";
        int vs=TerrainLightingGpuFixture.shader(gl.GL_VERTEX_SHADER,vertex),fs=TerrainLightingGpuFixture.shader(gl.GL_FRAGMENT_SHADER,fragment);program=gl.glCreateProgram();gl.glAttachShader(program,vs);gl.glAttachShader(program,fs);gl.glBindAttribLocation(program,0,"aPosition");gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,gl.glGetProgramInfoLog(program));gl.glDeleteShader(vs);gl.glDeleteShader(fs);gl.glUseProgram(program);
        i("uTexture",0);i("uShadowMask",1);i("uGlowMask",2);i("uNearGeometryShadow",3);i("uFarGeometryShadowMap",4);i("uPreviousFarGeometryShadowMap",6);i("uTerrainDefinitionField",7);f("uTerrainDefinitionInvWidth",1);f("uTerrainDefinitionInvHeight",1);
    }
    public static void main(String[] args)throws Exception {
        gl=LwjglBindings.load();TerrainLightingGpuFixture.gl=gl;require(gl.glfwInit(),"GLFW init");long window=0;
        try {
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(64,64,"Hidden terrain definition probes",0,0);require(window!=0,"Window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));
            vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,TerrainLightingGpuFixture.floats(new float[]{-1,-1,0,1,-1,0,1,1,0,-1,1,0}),gl.GL_STATIC_DRAW);IntBuffer ids=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();ids.put(new int[]{0,1,2,0,2,3}).flip();gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,ids,gl.GL_STATIC_DRAW);gl.glEnableVertexAttribArray(0);gl.glVertexAttribPointer(0,3,gl.GL_FLOAT,false,12,0L);gl.glViewport(0,0,64,64);gl.glDisable(gl.GL_DEPTH_TEST);gl.glDisable(gl.GL_CULL_FACE);
            field=gl.glGenTextures();glow=gl.glGenTextures();link(false);
            for(int mode=0;mode<4;mode++)for(float strength:new float[]{0,2,8})require(sample(0,1,255,1,mode,strength)==128,"Flat palette moved");
            require(sample(.6f,.8f,40,1,0,8)==128&&sample(.6f,.8f,255,1,0,8)==128,"SUN_ONLY includes fixed relief");
            int lit=sample(.6f,.8f,40,1,1,2),dark=sample(.6f,.8f,255,1,1,2);require(lit>dark+30,"Classic relief missing at default strength: "+lit+"/"+dark);
            require(sample(.6f,.8f,255,1,1,0)==128,"Zero strength includes relief");require(sample(.6f,.8f,255,4,3,8)==128,"Terrain relief leaked onto object");
            i("uTerrainDefinitionFieldEnabled",1);pixel(7,field,255,128,0,255);int ao=sample(0,1,72,1,2,2);require(ao>=98&&ao<=101,"AO factor outside bounded effect "+ao);
            pixel(7,field,0,255,0,255);int valley=sample(0,1,72,1,3,2);pixel(7,field,0,0,0,255);int ridge=sample(0,1,72,1,3,2);require(valley>=111&&valley<=113&&ridge>=142&&ridge<=144,"Signed curvature unavailable "+valley+"/"+ridge);
            pixel(7,field,255,255,0,0);require(sample(0,1,72,1,2,8)==128,"Unknown field alpha not neutral");
            link(true);i("uRemasterLightingEnabled",1);i("uGlowMaskEnabled",1);i("uTerrainDefinitionFieldEnabled",1);f("uGlowMaskInvSpanX",1);f("uGlowMaskInvSpanZ",1);f("uBrightness",1);f("uContrast",1);f("uGamma",1);f("uSaturation",1);pixel(2,glow,64,64,64,255);pixel(7,field,255,255,0,255);
            for(int mode=1;mode<4;mode++)for(float legacy:new float[]{0,255})require(sample(.6f,.8f,legacy,1,mode,8)==64,"Independent glow was modulated by terrain definition");
            require(gl.glGetInteger(0x0B40)!=0,"Context state unavailable");System.out.println("PASS production GLSL flat palette, default Classic contrast, SUN_ONLY/zero/object neutrality, bounded AO/curvature/unknowns and full-shader independent glow; legacy lit/dark="+lit+"/"+dark);
        }finally {if(program!=0)gl.glDeleteProgram(program);if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(field!=0)gl.glDeleteTextures(field);if(glow!=0)gl.glDeleteTextures(glow);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
