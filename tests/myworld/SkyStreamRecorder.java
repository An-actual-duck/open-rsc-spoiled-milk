package orsc;
import java.io.*;
import java.security.*;
final class SkyStreamRecorder {
 static final int GL_BLEND=0x0BE2,GL_SRC_ALPHA=0x0302,GL_ONE_MINUS_SRC_ALPHA=0x0303,GL_TRIANGLES=4,GL_QUADS=7;
 final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); final DataOutputStream out=new DataOutputStream(bytes); int vertices;
 void word(int v)throws Exception{out.writeInt(v);} void number(float v)throws Exception{word(Float.floatToRawIntBits(v));}
 void glEnable(int x)throws Exception{word(1);word(x);} void glDisable(int x)throws Exception{word(2);word(x);} void glBlendFunc(int a,int b)throws Exception{word(3);word(a);word(b);}
 void glBegin(int x)throws Exception{word(4);word(x);} void glEnd()throws Exception{word(5);} void glColor4f(float a,float b,float c,float d)throws Exception{word(6);number(a);number(b);number(c);number(d);}
 void glVertex3f(float x,float y,float z)throws Exception{word(7);number(x);number(y);number(z);vertices++;}
 String digest()throws Exception{byte[] sha=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());StringBuilder s=new StringBuilder();for(byte b:sha)s.append(String.format("%02x",b&255));return s.toString();}
}
