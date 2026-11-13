package orsc;

import java.nio.FloatBuffer;
import orsc.graphics.three.Renderer3DFrame;

/** Geometry/roof policy checks use the production mesh and shared roof cache. */
public final class NpcGroundShadowFixture {
    static int checks;
    static void require(boolean condition,String reason) {checks++;if(!condition)throw new AssertionError(reason);}
    static double area(FloatBuffer b) {
        double total=0;
        for(int i=0;i<b.limit();i+=21)total+=Math.abs((b.get(i+7)-b.get(i))*(b.get(i+16)-b.get(i+2))-(b.get(i+9)-b.get(i+2))*(b.get(i+14)-b.get(i)))*.5;
        return total;
    }
    public static void main(String[] args) {
        CharacterGroundShadowMesh mesh=new CharacterGroundShadowMesh();mesh.clear();
        mesh.append(64,-80,64,32,.25f,-.5f,.28f,GeometryShadowRoofMask.EMPTY,0,0);
        FloatBuffer data=mesh.data();double fullArea=area(data);
        require(mesh.circles==1&&mesh.vertexCount()==48,"One circle must have sixteen triangles");
        require(Math.abs(fullArea-8*32*32*Math.sin(Math.PI/8))<.01,"Circle fan area changed");
        for(int i=0;i<data.limit();i+=7) {
            float x=data.get(i),y=data.get(i+1),z=data.get(i+2),alpha=data.get(i+6);
            require(Math.abs(y-(-81.5f+.25f*(x-64)-.5f*(z-64)))<.001,"Shadow detached from sampled terrain tangent");
            require(alpha>=0&&alpha<=.28f,"Unbounded circle opacity");
            require(Math.hypot(x-64,z-64)<=32.001,"Circle radius escaped footprint");
        }
        GeometryShadowRoofMask.Cache cache=new GeometryShadowRoofMask.Cache();
        GeometryShadowRoofMask hidden=cache.get(GeometryShadowRoofMaskFixture.frame(1L<<2,false,101),0);
        mesh.clear();mesh.append(120,0,64,32,0,0,.28f,hidden,0,0);data=mesh.data();
        require(area(data)>0&&area(data)<fullArea,"Roof edge did not clip footprint");
        for(int i=0;i<data.limit();i+=21) {
            float x=(data.get(i)+data.get(i+7)+data.get(i+14))/3;
            float z=(data.get(i+2)+data.get(i+9)+data.get(i+16))/3;
            require(!hidden.covered((int)x,(int)z),"Shadow triangle entered covered tile");
        }
        double clippedArea=area(data);
        GeometryShadowRoofMask shown=cache.get(GeometryShadowRoofMaskFixture.frame(1L<<2,true,102),0);
        mesh.clear();mesh.append(120,0,64,32,0,0,.28f,shown,0,0);
        require(Math.abs(area(mesh.data())-clippedArea)<.001,"Hide Roofs changed shadow suppression");
        mesh.clear();mesh.append(140,0,64,32,0,0,.28f,hidden,0,0);
        require(mesh.vertexCount()==0&&mesh.roofSuppressed==1,"Indoor NPC retained sunlight circle");
        mesh.clear();mesh.append(-136,0,64,32,0,0,.28f,hidden,256,0);
        require(Math.abs(area(mesh.data())-clippedArea)<.01,"Rebase/negative coordinates changed roof clipping");
        mesh.clear();mesh.append(Float.NaN,0,0,32,0,0,.28f,hidden,0,0);
        mesh.append(Float.MAX_VALUE,0,0,32,0,0,.28f,hidden,0,0);
        require(mesh.vertexCount()==0,"Invalid coordinates admitted");
        for(int i=0;i<10000;i++)mesh.append(64,0,64,32,0,0,.28f,GeometryShadowRoofMask.EMPTY,0,0);
        require(mesh.vertexCount()>0&&mesh.vertexCount()<=CharacterGroundShadowMesh.MAX_VERTICES,"Mesh staging bound exceeded");
        mesh.clear();require(mesh.vertexCount()==0&&mesh.circles==0,"Scene reuse retained old circles");
        require(Renderer3DFrame.CharacterGroundShadow.radiusForWidth(0)==14&&Renderer3DFrame.CharacterGroundShadow.radiusForWidth(128)==32
            &&Renderer3DFrame.CharacterGroundShadow.radiusForWidth(Integer.MAX_VALUE)==64,"Size clamp changed");
        System.out.println("PASS "+checks+" circle, terrain tangent, soft alpha, roof edge/hidden roofs, rebase, invalid/bounded data and reset checks");
    }
}
