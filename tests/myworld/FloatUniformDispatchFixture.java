package orsc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Behavioral dispatch, optional-classpath and actual shader-state probes. */
public final class FloatUniformDispatchFixture {
    private static volatile int observedLocation;
    private static volatile float observedValue;
    private static final Exception CHECKED = new Exception("dispatch checked sentinel");
    private static final Error ERROR = new AssertionError("dispatch error sentinel");
    private static final Throwable OTHER = new Throwable("dispatch throwable sentinel");
    public static void accept(int location, float value) { observedLocation=location; observedValue=value; }
    public static void checked(int location, float value) throws Exception { throw CHECKED; }
    public static void error(int location, float value) { throw ERROR; }
    public static void other(int location, float value) throws Throwable { throw OTHER; }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static void route(LwjglBindings bindings,String name) throws Exception {
        Field field=LwjglBindings.class.getDeclaredField("glUniform1f");field.setAccessible(true);
        Method method=FloatUniformDispatchFixture.class.getMethod(name,int.class,float.class);
        // The same fixture can measure the frozen reflective predecessor.
        field.set(bindings,field.getType()==MethodHandle.class?MethodHandles.publicLookup().unreflect(method):method);
    }
    private static void contract() throws Exception {
        LwjglBindings bindings=LwjglBindings.load();
        Field field=LwjglBindings.class.getDeclaredField("glUniform1f");field.setAccessible(true);
        require(field.getType()==MethodHandle.class,"Float uniform did not load as an optional typed handle");
        require(((MethodHandle)field.get(bindings)).type().equals(MethodType.methodType(void.class,int.class,float.class)),"Incorrect real LWJGL int,float -> void signature");
        route(bindings,"accept");
        for(float value:new float[]{-.375f,0f,.125f,1f}) {
            bindings.glUniform1f(-7,value);require(observedLocation==-7&&Float.floatToIntBits(observedValue)==Float.floatToIntBits(value),"Argument order/type/value changed");
        }
        route(bindings,"checked");try{bindings.glUniform1f(1,.5f);throw new AssertionError("Missing checked exception");}catch(Exception expected){require(expected==CHECKED,"Checked failure identity was wrapped");}
        route(bindings,"error");try{bindings.glUniform1f(1,.5f);throw new AssertionError("Missing error");}catch(Error expected){require(expected==ERROR,"Error identity was wrapped");}
        route(bindings,"other");try{bindings.glUniform1f(1,.5f);throw new AssertionError("Missing throwable wrapper");}catch(RuntimeException expected){require(expected.getCause()==OTHER,"Non-Exception throwable wrapper changed");}
        System.out.println("PASS float uniform exact runtime signature, ordered fractional arguments, original checked/Error identity and Throwable wrapping");
    }
    private static void allocation() throws Exception {
        LwjglBindings bindings=LwjglBindings.load();route(bindings,"accept");
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        require(bean.isThreadAllocatedMemorySupported(),"Thread allocation measurement unsupported");bean.setThreadAllocatedMemoryEnabled(true);
        final int warm=500000,count=2000000;long id=Thread.currentThread().getId();
        for(int i=0;i<warm;i++)bindings.glUniform1f(i,i*.03125f);
        long before=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
        for(int i=0;i<count;i++)bindings.glUniform1f(i,i*.03125f);
        long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-before;
        require(observedLocation==count-1&&observedValue==(count-1)*.03125f,"Measured dispatch was not consumed");
        System.out.println("{\"calls\":"+count+",\"bytes\":"+bytes+",\"bytesPerCall\":"+((double)bytes/count)+",\"nanosPerCall\":"+((double)nanos/count)+",\"javaVersion\":\""+System.getProperty("java.version")+"\"}");
    }
    private static int program(LwjglBindings gl) throws Exception {
        int vertex=gl.glCreateShader(gl.GL_VERTEX_SHADER),fragment=gl.glCreateShader(gl.GL_FRAGMENT_SHADER),program=0;
        try {
            gl.glShaderSource(vertex,"#version 120\nvoid main(){gl_Position=gl_Vertex;}");gl.glCompileShader(vertex);
            gl.glShaderSource(fragment,"#version 120\nuniform float amount;void main(){gl_FragColor=vec4(amount+0.5,0.25,0.75,1.0);}");gl.glCompileShader(fragment);
            require(gl.glGetShaderi(vertex,gl.GL_COMPILE_STATUS)!=0&&gl.glGetShaderi(fragment,gl.GL_COMPILE_STATUS)!=0,"Shader compile: "+gl.glGetShaderInfoLog(fragment));
            program=gl.glCreateProgram();gl.glAttachShader(program,vertex);gl.glAttachShader(program,fragment);gl.glLinkProgram(program);require(gl.glGetProgrami(program,gl.GL_LINK_STATUS)!=0,"Shader link: "+gl.glGetProgramInfoLog(program));return program;
        } finally {gl.glDeleteShader(vertex);gl.glDeleteShader(fragment);}
    }
    private static float uniform(int program,int location) throws Exception {
        FloatBuffer data=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        Class.forName("org.lwjgl.opengl.GL20").getMethod("glGetUniformfv",int.class,int.class,FloatBuffer.class).invoke(null,program,location,data);return data.get(0);
    }
    private static void gpu() throws Exception {
        LwjglBindings gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;int first=0,second=0;boolean ready=false;
        try {
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(64,64,"Hidden float uniform parity",0,0);require(window!=0,"Hidden GL window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();ready=true;
            System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));first=program(gl);second=program(gl);int a=gl.glGetUniformLocation(first,"amount"),b=gl.glGetUniformLocation(second,"amount");require(a>=0&&b>=0,"Active uniform locations");
            gl.glUseProgram(first);gl.glUniform1f(a,-.25f);require(uniform(first,a)==-.25f,"Negative fractional uniform readback");
            gl.glUniform1f(-1,.875f);require(uniform(first,a)==-.25f,"Location -1 altered shader state");
            gl.glUseProgram(second);gl.glUniform1f(b,.125f);require(uniform(first,a)==-.25f&&uniform(second,b)==.125f,"Uniform updated an inactive program");
            gl.glUseProgram(first);gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glBegin(7);gl.glVertex3f(-1,-1,0);gl.glVertex3f(1,-1,0);gl.glVertex3f(1,1,0);gl.glVertex3f(-1,1,0);gl.glEnd();
            ByteBuffer pixel=ByteBuffer.allocateDirect(4);Class<?> gl11=Class.forName("org.lwjgl.opengl.GL11");gl11.getMethod("glReadPixels",int.class,int.class,int.class,int.class,int.class,int.class,ByteBuffer.class).invoke(null,32,32,1,1,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixel);
            int[] expected={64,64,191,255};for(int i=0;i<4;i++)require(Math.abs((pixel.get(i)&255)-expected[i])<=1,"Actual shader pixel channel "+i);
            require(((Integer)gl11.getMethod("glGetError").invoke(null))==0,"Unexpected GL error including location -1");
            System.out.println("PASS actual GPU negative/fraction uniform readback, location -1 no-op, selected program state, rendered RGBA and GL error parity");
        } finally {if(ready){gl.glUseProgram(0);if(first!=0)gl.glDeleteProgram(first);if(second!=0)gl.glDeleteProgram(second);}if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=0&&args[0].equals("--allocation")){allocation();return;}
        if(args.length!=0&&args[0].equals("--gpu")){gpu();return;}
        if(args.length!=0&&args[0].equals("--without-lwjgl")) {
            try {LwjglBindings.load();throw new AssertionError("Absent LWJGL unexpectedly loaded");}catch(ClassNotFoundException expected){require(expected.getMessage().startsWith("org.lwjgl."),"Unexpected optional missing dependency");}
            System.out.println("PASS bindings load without static LWJGL linkage; checked optional dependency failure permits presenter fallback");return;
        }
        contract();
    }
}
