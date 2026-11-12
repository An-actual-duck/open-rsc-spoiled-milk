package orsc;

import java.lang.reflect.*;
import java.util.*;
import orsc.graphics.three.*;

/** Actual publication hooks, independent of catalogue admission and browser/GPU work. */
public final class StaticSceneRevisionFixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static Field field(Class<?> type,String name)throws Exception{while(type!=null){try{Field result=type.getDeclaredField(name);result.setAccessible(true);return result;}catch(NoSuchFieldException absent){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private static Object allocate(Class<?> type)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");return unsafe.getMethod("allocateInstance",Class.class).invoke(field(unsafe,"theUnsafe").get(null),type);}
    private static Object input(int x,int role,long key)throws Exception{
        Renderer3DWorldChunkFrame.ChunkMesh anchor=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,x*128,624*128,new int[0],new float[0],new float[0],new int[0],new int[0],new int[0],new int[0],new Renderer3DModelKind[0],0,0,0,true,0L);
        Class<?> type=Class.forName("orsc.mudclient$ResidentObjectChunkInput"),identity=Class.forName("orsc.mudclient$ResidentObjectCanonicalIdentity");
        for(Constructor<?> ctor:type.getDeclaredConstructors())if(ctor.getParameterTypes().length==14){ctor.setAccessible(true);return ctor.newInstance(anchor,0L,x,624,role,new RSModel[0],Array.newInstance(identity,0),0,0L,key,0,0,0,"");}
        throw new AssertionError("Resident input fixture constructor changed");
    }
    private static void capture(mudclient client,Object... inputs)throws Exception{Method capture=mudclient.class.getDeclaredMethod("captureWholeOverworldObjectCells",List.class);capture.setAccessible(true);capture.invoke(client,Arrays.asList(inputs));}
    private static void packet(SceneBaselineState state,int tick,int x){
        // An empty legacy scene page still publishes a real authority footprint.
        state.recordPacket(1,tick,x,624,0,0,0,8,0,0,0,0,0,0,0,Collections.<SceneBaselineState.Record>emptyList());
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){check(BrowserWholeOverworldZone.isStaticSceneReuseEnabled()==Boolean.parseBoolean(args[0]),"private default-off startup gate");System.out.println("PASS static scene gate "+args[0]);return;}
        mudclient client=(mudclient)allocate(mudclient.class);
        check(client.getWholeOverworldObjectCellsRevision()==-1,"uncaptured placement revision is unknown");
        check(client.getWholeZoneObjectAuthorityRevision()==-1,"missing packet owner is unknown");
        Object original=input(96,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,0x123456789abcdef0L);
        capture(client,original);long initial=client.getWholeOverworldObjectCellsRevision();int[] originalRows=client.getWholeOverworldObjectCells();
        check(initial>0&&originalRows.length==6,"first capture publishes known content");
        capture(client,input(96,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,0x123456789abcdef0L));
        check(client.getWholeOverworldObjectCellsRevision()==initial,"fresh descriptor with equal content preserves revision");
        originalRows[3]^=1;check(client.getWholeOverworldObjectCells()[3]!=(originalRows[3]),"published rows cannot be mutated through getter");
        capture(client,input(96,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,0x123456789abcdef1L));
        check(client.getWholeOverworldObjectCellsRevision()>initial,"door/tree replacement changes revision");
        long mutated=client.getWholeOverworldObjectCellsRevision();capture(client);check(client.getWholeOverworldObjectCellsRevision()>mutated&&client.getWholeOverworldObjectCells().length==0,"last removal publishes empty scene");
        long empty=client.getWholeOverworldObjectCellsRevision();capture(client);check(client.getWholeOverworldObjectCellsRevision()==empty,"unchanged empty scene remains stable");
        capture(client,original);check(client.getWholeOverworldObjectCellsRevision()>empty,"restoration publishes new revision");
        int[] restored=client.getWholeOverworldObjectCells();check(restored[3]==(int)0x123456789abcdef0L&&restored[4]==(int)(0x123456789abcdef0L>>>32),"restoration preserves full canonical identity");
        for(boolean reverse:new boolean[]{false,true}){Object animated=input(96,Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,0L);capture(client,reverse?animated:original,reverse?original:animated);int[] mixed=client.getWholeOverworldObjectCells();check(mixed[5]==2&&mixed[3]==restored[3]&&mixed[4]==restored[4],"mixed animated mask sticky in both input orders");}
        long mixedRevision=client.getWholeOverworldObjectCellsRevision();capture(client,original);check(client.getWholeOverworldObjectCellsRevision()>mixedRevision&&client.getWholeOverworldObjectCells()[5]==0,"animated removal restores static mask");
        capture(client,input(120,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,0x123456789abcdef0L));check(client.getWholeOverworldObjectCells()[1]==5,"cell move publishes new world cell");
        SceneBaselineState state=new SceneBaselineState();PacketHandler packets=(PacketHandler)allocate(PacketHandler.class);field(PacketHandler.class,"sceneBaselineState").set(packets,state);client.packetHandler=packets;
        long before=client.getWholeZoneObjectAuthorityRevision();packet(state,1,96);long first=client.getWholeZoneObjectAuthorityRevision();check(first>before,"packet publication increments authority revision");
        packet(state,2,97);check(client.getWholeZoneObjectAuthorityRevision()>first,"same-content moving footprint increments authority revision");
        long moved=client.getWholeZoneObjectAuthorityRevision();state.resetForScopeChange("new-scope");check(client.getWholeZoneObjectAuthorityRevision()>moved,"scope reset invalidates authority");
        field(mudclient.class,"wholeOverworldObjectCellsRevision").setLong(client,Long.MAX_VALUE);capture(client);check(client.getWholeOverworldObjectCellsRevision()<0,"placement wrap becomes uncacheable");
        field(SceneBaselineState.class,"wholeZoneAuthorityRevision").setLong(state,Long.MAX_VALUE);state.resetForScopeChange("overflow");check(client.getWholeZoneObjectAuthorityRevision()<0,"authority wrap becomes uncacheable");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"scope\":\"Actual detached placement capture and packet/reset publication; no catalogue admission, gameplay packet transport, browser/GPU or performance claim.\"}");
    }
}
