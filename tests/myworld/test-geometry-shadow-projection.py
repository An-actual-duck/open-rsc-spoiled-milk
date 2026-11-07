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
  Properties props=new Properties();
  RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);
  RendererShadowSettings.saveToClientSettings(props);
  RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
  RendererShadowSettings.loadFromClientSettings(props);
  require(RendererShadowSettings.getQuality()==RendererShadowSettings.Quality.LOW,"quality persistence");
  require(RendererShadowSettings.cycle()==RendererShadowSettings.Quality.HIGH,"low->high");
  require(RendererShadowSettings.cycle()==RendererShadowSettings.Quality.OFF,"high->off");
  System.out.println("PASS stable projection, sun-ray occlusion, rebase and quality persistence");
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
                        str(ROOT / "Client_Base/src/orsc/RendererShadowSettings.java"), str(fixture)], check=True)
        subprocess.run(["java", "-cp", str(folder), "orsc.GeometryShadowProjectionFixture"], check=True)


if __name__ == "__main__":
    main()
