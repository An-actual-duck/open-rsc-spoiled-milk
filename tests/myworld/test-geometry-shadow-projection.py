#!/usr/bin/env python3
"""Light-space stability, Y convention, occlusion ordering and saved quality."""
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = r'''
package orsc;
import java.util.Properties;
public final class GeometryShadowProjectionFixture {
 static float at(DirectionalShadowProjection p,int row,double x,double y,double z) {
  return (float)(p.matrix[row]*x+p.matrix[row+4]*y+p.matrix[row+8]*z+p.matrix[row+12]);
 }
 static void require(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
 static void reject(float half,float sunX) {
  try {new DirectionalShadowProjection(0,0,0,sunX,1,0,half,2048);throw new AssertionError("invalid accepted");}
  catch(IllegalArgumentException expected){}
 }
 public static void main(String[] args) {
  reject(Float.NaN,1);reject(Float.POSITIVE_INFINITY,1);reject(0,1);reject(100,Float.NaN);
  for(float elevation:new float[]{5,45,85,90}) {
   float x=(float)Math.cos(Math.toRadians(elevation)), y=(float)Math.sin(Math.toRadians(elevation));
   DirectionalShadowProjection a=new DirectionalShadowProjection(0,0,0,x,y,0,4096,2048);
   DirectionalShadowProjection b=new DirectionalShadowProjection(.00001,0,.00001,x,y,0,4096,2048);
   for(int row=0;row<2;row++) for(int col=0;col<4;col++) require(a.matrix[col*4+row]==b.matrix[col*4+row],"subtexel XY drift");
   require(a.intersects(-100,-100,-100,100,100,100),"near caster culled");
   require(!a.intersects(100000,100000,100000,100010,100010,100010),"distant light-outside caster retained");
   // A caster 128 world-units above ground lies on the same sun ray as
   // this ground receiver. In Y-down coordinates its depth must be nearer.
   double groundX=-128.0*x/y;
   require(Math.abs(at(a,0,0,-128,0)-at(a,0,groundX,0,0))<.0001,"sun-ray horizontal projection");
   require(Math.abs(at(a,1,0,-128,0)-at(a,1,groundX,0,0))<.0001,"sun-ray vertical projection");
   require(at(a,2,0,-128,0)<at(a,2,groundX,0,0),"Y-down depth ordering reversed");
  }
  DirectionalShadowProjection before=new DirectionalShadowProjection(100+100000, -800, 200+200000,1,1,0,4096,2048);
  DirectionalShadowProjection after=new DirectionalShadowProjection(-6044+106144, -800, -5944+206144,1,1,0,4096,2048);
  for(int i=0;i<16;i++)require(before.matrix[i]==after.matrix[i],"native origin rebase moved shadow grid");

  // Far maps retain their established absolute grid. Near maps track phase in
  // world space: real map coordinates must not introduce solar quantization.
  DirectionalShadowProjection origin=null, world=null;
  for(int frame=0;frame<20000;frame++) {
   double angle=Math.toRadians(90+frame*.002);
   float sx=(float)(Math.cos(angle)*.70710678), sy=.70710678f, sz=(float)(Math.sin(angle)*.70710678);
   double move=frame<10000?0:(frame-10000)*.5;
   origin=new DirectionalShadowProjection(move,-256,0,sx,sy,sz,4096,2048,origin);
   world=new DirectionalShadowProjection(64000+move,-256,59000,sx,sy,sz,4096,2048,world);
   for(int row=0;row<3;row++)
    require(Math.abs(at(origin,row,move+100,0,1000)-at(world,row,64100+move,0,60000))<.00001,"map-coordinate phase divergence");
   require(Math.hypot(Math.hypot(world.gridCenterX-(64000+move),world.gridCenterZ-59000),world.gridCenterY+256)<3.0,"tracking grid stopped following camera");
  }
  DirectionalShadowProjection held=new DirectionalShadowProjection(64000,-256,59000,.37f,.67f,.64f,4096,2048,null);
  float[] heldMatrix=held.matrix.clone(); double hx=held.gridCenterX,hy=held.gridCenterY,hz=held.gridCenterZ;
  for(int frame=0;frame<100000;frame++) {
   held=new DirectionalShadowProjection(64000,-256,59000,.37f,.67f,.64f,4096,2048,held);
   require(java.util.Arrays.equals(heldMatrix,held.matrix),"fixed-sun tracking matrix drift");
   require(Math.hypot(Math.hypot(held.gridCenterX-hx,held.gridCenterZ-hz),held.gridCenterY-hy)<.0000001,"fixed-sun tracking anchor drift");
  }
  DirectionalShadowProjection tiny=new DirectionalShadowProjection(64000.01,-256,59000.01,.37f,.67f,.64f,4096,2048,held);
  for(int row=0;row<2;row++)for(int col=0;col<4;col++)require(held.matrix[col*4+row]==tiny.matrix[col*4+row],"tracking subtexel XY drift");

  // Ground-aligned near rows remain constant along sun rays, including upper
  // floors and moving terrain references. Far constructors above stay unchanged.
  for(float elevation:new float[]{5,10,45,85,90}) {
   DirectionalShadowProjection tracked=null;
   for(int frame=0;frame<2000;frame++) {
    double az=Math.toRadians(34+frame*.002),el=Math.toRadians(elevation+frame*.00002);
    float sx=(float)(Math.cos(az)*Math.cos(el)),sy=(float)Math.sin(el),sz=(float)(Math.sin(az)*Math.cos(el));
    double ground=-1000+Math.sin(frame*.01)*300, x=64000+frame*.3,z=59000+frame*.2;
    tracked=new DirectionalShadowProjection(x,ground-2000,z,sx,sy,sz,4096,2048,tracked,ground);
    require(Math.abs(tracked.gridCenterX-x)<=2.001&&Math.abs(tracked.gridCenterZ-z)<=2.001,"height/sun tracking escaped half texel");
    require(Math.abs(at(tracked,0,x,ground,z))<.001&&Math.abs(at(tracked,1,x,ground,z))<.001,"elevated focal point outside near center");
    double t=128/sy;
    for(int row=0;row<2;row++)require(Math.abs(at(tracked,row,x,ground-128,z)-at(tracked,row,x-sx*t,ground,z-sz*t))<.00001,"sheared sun ray not invariant");
    require(tracked.intersects((float)x-16,(float)ground-192,(float)z-16,(float)x+16,(float)ground,(float)z+16),"elevated focal caster culled");
   }
  }
  DirectionalShadowProjection h0=new DirectionalShadowProjection(64000,-2256,59000,.37f,.67f,.64f,4096,2048,null,-384);
  DirectionalShadowProjection h1=new DirectionalShadowProjection(64000,-2256,59000,.37f,.67f,.64f,4096,2048,h0,-1000);
  // Reference-height changes move the footprint by whole texels; they cannot
  // introduce a fractional sampling phase at a stationary sun.
  for(int row=0;row<2;row++) {
   double pixels=(h1.matrix[12+row]-h0.matrix[12+row])*2048/2;
   require(Math.abs(pixels-Math.rint(pixels))<.002,"height transition changed subtexel phase");
  }
  Properties props=new Properties();
  RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);
  RendererShadowSettings.saveToClientSettings(props);
  RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
  RendererShadowSettings.loadFromClientSettings(props);
  require(RendererShadowSettings.getQuality()==RendererShadowSettings.Quality.LOW,"quality persistence");
  require(RendererShadowSettings.cycle()==RendererShadowSettings.Quality.HIGH,"low->high");
  require(RendererShadowSettings.cycle()==RendererShadowSettings.Quality.OFF,"high->off");
  Properties oldClassic=new Properties();oldClassic.setProperty(RendererProfileSettings.PROFILE_PROPERTY_KEY,"classic");
  RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);RendererShadowSettings.loadFromClientSettings(oldClassic);
  require(RendererShadowSettings.getQuality()==RendererShadowSettings.Quality.OFF,"old Classic setting migration");
  System.out.println("PASS stable projection, world-relative solar phase, 100000-frame drift, camera tracking, sun-ray occlusion, rebase and quality persistence");
 }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix="geometry-shadow-projection-") as temp:
        folder = Path(temp)
        fixture = folder / "GeometryShadowProjectionFixture.java"
        fixture.write_text(SOURCE)
        subprocess.run(["javac", "-source", "8", "-target", "8", "-d", str(folder),
                        str(ROOT / "PC_Client/src/orsc/DirectionalShadowProjection.java"),
                        str(ROOT / "Client_Base/src/orsc/RendererShadowSettings.java"),
                        str(ROOT / "Client_Base/src/orsc/RendererProfileSettings.java"), str(fixture)], check=True)
        subprocess.run(["java", "-cp", str(folder), "orsc.GeometryShadowProjectionFixture"], check=True)


if __name__ == "__main__":
    main()
