package orsc;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
/** Read actual maintained production shader constants without creating a GL context. */
public final class BrowserColorShaderOracle {
 static String shader(Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return(String)f.get(null);}
 public static void main(String[] args)throws Exception{
  Path out=Paths.get(args[0]);Files.createDirectories(out);
  String vertex=shader(OpenGLShaderProgram.class,"RESIDENT_CHUNK_PARITY_VERTEX_SHADER");
  Files.write(out.resolve("world.vert"),vertex.getBytes("UTF-8"));
  Files.write(out.resolve("world.frag"),shader(OpenGLShaderProgram.class,"RESIDENT_CHUNK_PARITY_FRAGMENT_SHADER").getBytes("UTF-8"));
  Files.write(out.resolve("depth.vert"),shader(OpenGLGeometryShadows.class,"DEPTH_VERTEX").getBytes("UTF-8"));
  Files.write(out.resolve("depth.frag"),shader(OpenGLGeometryShadows.class,"DEPTH_FRAGMENT").getBytes("UTF-8"));
  StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(vertex.getBytes("UTF-8")))hex.append(String.format("%02x",b&255));
  System.out.println(vertex.length()+":"+hex);
 }
}
