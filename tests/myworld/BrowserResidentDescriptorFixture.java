package orsc;

import java.io.*;
import java.util.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;

/** Frozen/current binary descriptor oracle; constructor counts are test-only source instrumentation. */
public final class BrowserResidentDescriptorFixture {
    static long totalConstructors,directConstructors;static int captures,directCaptures;
    static void capture(DataOutputStream out,mudclient client)throws Exception{
        BrowserResidentInputsFixture.field(mudclient.class,"fixtureIdentityConstructions").setLong(null,0);
        int used=(Integer)BrowserResidentInputsFixture.call(client,"captureResidentStaticInputs",new Class<?>[0]);
        long count=BrowserResidentInputsFixture.field(mudclient.class,"fixtureIdentityConstructions").getLong(null);
        boolean direct=false;try{direct=(Boolean)mudclient.class.getMethod("isBrowserResidentDescriptorWritingEnabled").invoke(null);}catch(NoSuchMethodException frozen){}
        if(direct?count!=0:count!=used/8)throw new AssertionError("Unexpected descriptor constructor count: "+count+" for "+used+" ints; direct="+direct);
        if(direct){directCaptures++;directConstructors+=count;}
        totalConstructors+=count;captures++;out.writeInt(used);int[] probe=(int[])BrowserResidentInputsFixture.get(client,"residentStaticInputProbe");for(int n=0;n<used;n++)out.writeInt(probe[n]);
        int animated=(Integer)BrowserResidentInputsFixture.get(client,"residentAnimatedInputCount");int[] indices=(int[])BrowserResidentInputsFixture.get(client,"residentAnimatedInputIndices");out.writeInt(animated);for(int n=0;n<animated;n++)out.writeInt(indices[n]);
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        BrowserResidentInputsFixture.definitions();mudclient client=BrowserResidentInputsFixture.client();
        BrowserResidentInputsFixture.set(client,"residentStaticInputProbe",new int[16000]);BrowserResidentInputsFixture.set(client,"residentAnimatedInputIndices",new int[100]);
        List<SceneBaselineState.Record> scenery=(List<SceneBaselineState.Record>)BrowserResidentInputsFixture.get(client,"staticPresentationSceneryRecords"),walls=(List<SceneBaselineState.Record>)BrowserResidentInputsFixture.get(client,"staticPresentationWallRecords");
        for(int n=0;n<1100;n++)scenery.add(new SceneBaselineState.Record(n%4,n%240-48,(n*7)%240-48,n%8,0));
        for(int n=0;n<80;n++)walls.add(new SceneBaselineState.Record(0,n%144,(n*3)%144,n%8,1));
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){
            capture(out,client);capture(out,client);
            for(int direction=-1;direction<=8;direction++){client.setGameObjectInstanceDir(0,direction);client.setWallObjectInstanceDir(0,direction);capture(out,client);}
            Sector[] sectors=(Sector[])BrowserResidentInputsFixture.get(BrowserResidentInputsFixture.get(client,"world"),"sectors");
            for(int pass=0;pass<3;pass++){for(Sector s:sectors)for(int x=0;x<48;x++)for(int z=0;z<48;z++)s.getTile(x,z).groundElevation=(x*7+z*3+pass*29)&255;capture(out,client);}
            EntityHandler.getObjectDef(0).width=3;EntityHandler.getObjectDef(0).height=5;capture(out,client);
            EntityHandler.getObjectDef(1).modelID=-1;capture(out,client);EntityHandler.getObjectDef(1).modelID=0;
            ArrayList<GameObjectDef> defs=(ArrayList<GameObjectDef>)BrowserResidentInputsFixture.field(EntityHandler.class,"objects").get(null);GameObjectDef old=defs.set(0,null);capture(out,client);defs.set(0,old);
            client.getGameObjectInstanceModel(0).addRotation(0,7,0);client.getGameObjectInstanceModel(2).addRotation(0,11,0);capture(out,client);
            client.setGameObjectInstanceMaterialized(0,false);capture(out,client);client.setGameObjectInstanceMaterialized(0,true);
            client.setGameObjectInstanceModel(1,null);capture(out,client);client.setGameObjectInstanceModel(1,BrowserResidentInputsFixture.model());
            for(int[] base:new int[][]{{48,-48},{Integer.MAX_VALUE,Integer.MIN_VALUE},{-144,144}}){BrowserResidentInputsFixture.set(client,"midRegionBaseX",base[0]);BrowserResidentInputsFixture.set(client,"midRegionBaseZ",base[1]);for(int size:new int[]{128,127,0,Integer.MAX_VALUE}){BrowserResidentInputsFixture.set(client,"tileSize",size);capture(out,client);}}
            BrowserResidentInputsFixture.set(client,"midRegionBaseX",0);BrowserResidentInputsFixture.set(client,"midRegionBaseZ",0);BrowserResidentInputsFixture.set(client,"tileSize",128);
            Object world=BrowserResidentInputsFixture.get(client,"world");
            NativeLayeredTerrainSnapshot nativeTerrain=new NativeLayeredTerrainSnapshot("fixture","1.0.0",String.join("",Collections.nCopies(64,"a")),24,"global",-8,0,0,NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,String.join("",Collections.nCopies(64,"a")),31,1,0,0,0,0,0);
            BrowserResidentInputsFixture.set(world,"nativeLayeredTerrainSnapshot",nativeTerrain);BrowserResidentInputsFixture.set(world,"nativeLayeredTerrainAppliedSectionX",1);BrowserResidentInputsFixture.set(world,"nativeLayeredTerrainAppliedSectionY",1);capture(out,client);
            BrowserResidentInputsFixture.set(world,"nativeLayeredTerrainSnapshot",null);capture(out,client);
            Collections.reverse(scenery);Collections.reverse(walls);capture(out,client);
            client.setGameObjectInstanceCount(0);client.setWallObjectInstanceCount(0);capture(out,client);
            Renderer3DSettings.setBrowserPresentationAvailable(false);capture(out,client);
        }
        System.out.println("PASS "+captures+" exact descriptor captures; test-only canonical constructor entries="+totalConstructors+"; directCaptures="+directCaptures+" directConstructorEntries="+directConstructors+"; no heap/FPS claim");
    }
}
