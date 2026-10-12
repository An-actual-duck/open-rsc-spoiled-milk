#!/usr/bin/env python3
"""Real decoder and PacketHandler replay, optionally against a frozen client JAR.

MAP_RECOVERY_CLIENT_JAR selects pre-fix artifacts for independent failure proof.
The renderer is replaced by a no-window test double; no game services/accounts.
"""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = Path(os.environ.get("MAP_RECOVERY_CLIENT_JAR", ROOT / "Client_Base/Open_RSC_Client.jar"))
CORE = ROOT / "server/core.jar"
HARNESS = r'''
package orsc;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.Deflater;
import sun.misc.Unsafe;
import com.openrsc.server.net.rsc.NativeLayeredTerrainClientResidency;
import com.openrsc.server.net.rsc.struct.outgoing.SceneBaselineStruct;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.Packet;

public final class NativeRecoveryFixture {
 static final String PACKAGE="test.layered", VERSION="1.0.0", SHA=repeat('a'), SOURCE=repeat('b');
 static String repeat(char c) { char[] chars=new char[64]; Arrays.fill(chars,c); return new String(chars); }
 static void check(boolean v,String why) { if(!v) throw new AssertionError(why); }
 static final class Body {
  byte[] bytes; NativeLayeredTerrainClientResidency.Transaction tx;
  Body(byte[] bytes, NativeLayeredTerrainClientResidency.Transaction tx) {this.bytes=bytes;this.tx=tx;}
 }
 static Body body(NativeLayeredTerrainClientResidency server,int cx,int cy,int radius,String encoding) throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
  line(out,PACKAGE);line(out,VERSION);line(out,SHA);out.writeByte(48);out.writeInt(cx);out.writeInt(cy);
  out.writeByte(radius);out.writeByte((2*radius+1)*(2*radius+1));
  NativeLayeredTerrainClientResidency.Transaction tx=server.begin();
  int width=encoding.equals(NativeLayeredTerrainChunk.VISUAL_ENCODING)?3:
     encoding.equals(NativeLayeredTerrainChunk.STRUCTURAL_ENCODING)?7:10;
  byte[] raw=new byte[48*48*width], packed=new byte[raw.length+100];
  Deflater deflater=new Deflater();deflater.setInput(raw);deflater.finish();int count=deflater.deflate(packed);deflater.end();
  for(int x=cx-radius;x<=cx+radius;x++) for(int y=cy-radius;y<=cy+radius;y++) {
   out.writeInt(x);out.writeInt(y);out.writeByte(1);out.writeInt(x);out.writeInt(y);line(out,encoding);line(out,SOURCE);
   String id=PACKAGE+"@"+VERSION+":"+SHA+":global:0:48:"+x+","+y+":"+x+","+y+":"+encoding+":"+SOURCE;
   boolean payload=tx.requiresPayload(id);out.writeByte(payload?1:0);
   if(payload){out.writeShort(count);out.write(packed,0,count);}
  }
  return new Body(bytes.toByteArray(),tx);
 }
 static void line(DataOutputStream o,String s)throws Exception{o.write(s.getBytes("UTF-8"));o.writeByte(10);}
 static NativeLayeredTerrainSnapshot context(NativeLayeredTerrainClientResidency s,NativeLayeredTerrainResidentCache c,int x,int y)throws Exception{
  Body b=body(s,x,y,1,NativeLayeredTerrainChunk.UNIFORM_ENCODING);
  NativeLayeredTerrainSnapshot result=NativeLayeredTerrainPacketDecoder.decodeV8(b.bytes,"global",0,c);b.tx.commit();return result;
 }
 static void halo(NativeLayeredTerrainClientResidency s,NativeLayeredTerrainResidentCache c,
     NativeLayeredTerrainSnapshot active,int x,int y,boolean prediction,boolean structure)throws Exception{
  Body b=body(s,x,y,2,structure?NativeLayeredTerrainChunk.STRUCTURAL_ENCODING:NativeLayeredTerrainChunk.VISUAL_ENCODING);
  if(prediction){
   if(structure)NativeLayeredTerrainPacketDecoder.decodePredictedSymmetricStructure(b.bytes,"global",0,c,active);
   else NativeLayeredTerrainPacketDecoder.decodePredictedSymmetricHalo(b.bytes,"global",0,c,active);
  }else{
   if(structure)NativeLayeredTerrainPacketDecoder.decodeV10Structure(b.bytes,"global",0,c,active);
   else NativeLayeredTerrainPacketDecoder.decodeV9Halo(b.bytes,"global",0,c,active);
   b.tx.commit();
  }
  check(s.getAccessOrder().equals(c.getAccessOrder()),"predicted decoder changed canonical LRU (or real halo failed parity)");
 }
 static void route()throws Exception{
  NativeLayeredTerrainClientResidency s=new NativeLayeredTerrainClientResidency();
  NativeLayeredTerrainResidentCache c=new NativeLayeredTerrainResidentCache();
  int[][] route={{2,13},{3,13},{4,12},{5,11},{6,11},{5,11},{4,12},{3,13},{2,13},{3,13},{4,12}};
  for(int pass=0;pass<3;pass++) for(int i=0;i<route.length;i++){
   int x=route[i][0],y=route[i][1];NativeLayeredTerrainSnapshot active=context(s,c,x,y);
   halo(s,c,active,x,y,false,false);halo(s,c,active,x,y,false,true);
   if(i+1<route.length){int nx=route[i+1][0],ny=route[i+1][1];
    halo(s,c,active,nx,ny,true,false);halo(s,c,active,nx,ny,true,true);
    halo(s,c,active,nx,ny,true,false); // repeated speculative receipt
   }
   check(s.getAccessOrder().equals(c.getAccessOrder()),"route residency diverged");
  }
  check(c.size()==64,"route never exercised capacity pressure");
  System.out.println("PASS route prediction/structure LRU parity at capacity64");
 }
 public static class RendererDouble extends mudclient {
  public int clears, scopes, movements, presentations;public boolean cover;
  RendererDouble(){super(null);}
  @Override public void clearStaticScenePresentation(){clears++;}
  @Override public void resetLayeredSceneActivationPresentation(){cover=false;}
  @Override public void applyLayeredSceneScope(int plane,boolean changed,boolean deep,NativeLayeredTerrainSnapshot terrain){scopes++;}
  @Override public void beginLayeredSceneActivation(boolean retained){cover=true;}
  @Override public void setLayeredSceneActivationPending(boolean pending){cover=pending;}
  @Override public void applyCustomMovementUpdate(int x,int y,int direction){movements++;}
  @Override public int getPlayerCount(){return 0;}
  @Override public int getNpcCount(){return 0;}
  @Override public void replaceStaticScenePresentation(List<SceneBaselineState.Record> scenery,List<SceneBaselineState.Record> walls){
   check(scenery.size()==1&&walls.size()==1,"server-produced presentation records missing");presentations++;
  }
 }
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static RendererDouble renderer(){return new RendererDouble();}
 static void deliver(PacketHandler handler,int opcode,byte[] bytes){
  handler.getPacketsIncoming().packetEnd=0;handler.getPacketsIncoming().writeBytes(bytes,bytes.length);
  handler.getPacketsIncoming().packetEnd=0;handler.handlePacket(opcode,bytes.length);
 }
 static byte[] contextPacket(int sequence,byte[] body,String space,String projection)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
  o.writeByte(8);o.writeInt(sequence);o.writeInt(100+sequence);line(o,space);line(o,projection);
  o.writeInt(190);o.writeInt(623);o.writeInt(0);o.writeShort(190);o.writeShort(623);o.write(body);return bytes.toByteArray();
 }
 static byte[] baseline(int sequence,boolean malformed)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
  o.writeByte(6);o.writeInt(100);o.writeShort(190);o.writeShort(623);o.writeInt(sequence);
  for(int i=0;i<5;i++)o.writeShort(0);o.writeShort(20);
  for(int i=0;i<5;i++)o.writeInt(0);
  o.writeByte(1);o.writeShort(0);o.writeShort(1);o.writeShort(malformed?1:0);
  return bytes.toByteArray();
 }
 static byte[] movement(int sequence,boolean malformed)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
  o.writeByte(2);o.writeInt(sequence);o.writeInt(100);o.writeInt(1);o.writeShort(190);o.writeShort(623);o.writeByte(0);
  o.writeShort(malformed?1:0);o.writeShort(0);return bytes.toByteArray();
 }
 static byte[] stage(byte[] body)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
  o.writeByte(1);o.writeInt(1);o.writeInt(1);o.writeInt(100);line(o,"global");o.writeInt(0);o.write(body);return bytes.toByteArray();
 }
 static byte[] legacyContext()throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
  o.writeByte(1);o.writeInt(3);o.writeInt(100);line(o,"global");
  o.writeInt(190);o.writeInt(623);o.writeInt(0);o.writeShort(190);o.writeShort(623);return bytes.toByteArray();
 }
 static byte[] serverBaseline(int protocol,int sequence,int category)throws Exception{
  SceneBaselineStruct b=new SceneBaselineStruct();b.setOpcode(OpcodeOut.SEND_SCENE_BASELINE);
  b.protocolVersion=protocol;b.serverTick=100;b.localX=190;b.localY=623;b.locationContextSequence=sequence;
  b.pageCategory=category;b.presentationCenterSectorX=3;b.presentationCenterSectorY=13;
  b.presentationOuterRadius=2;b.presentationInnerRadius=1;
  if(category!=SceneBaselineState.PAGE_ATOMIC_FENCE){
   b.scenery=1;b.walls=1;b.presentationScenery=1;b.presentationWalls=1;
   b.pageTotal=1;boolean wall=category==2||category==SceneBaselineState.PAGE_PRESENTATION_WALLS;
   boolean outer=category==SceneBaselineState.PAGE_PRESENTATION_SCENERY||category==SceneBaselineState.PAGE_PRESENTATION_WALLS;
   b.objectRecords.add(new SceneBaselineStruct.ObjectRecord(wall?2:1,outer?48:190,623,0,wall?1:0));
  }
  Packet p=new PayloadCustomGenerator().generate(b,null);
  check(p!=null&&p.getID()==143,"server scene generator failed");
  byte[] bytes=new byte[p.getBuffer().readableBytes()];p.getBuffer().readBytes(bytes);p.getBuffer().release();return bytes;
 }
 static void serverLoginPages(PacketHandler h,int sequence)throws Exception{
  deliver(h,143,serverBaseline(SceneBaselineState.ATOMIC_FENCE_PROTOCOL_VERSION,sequence,SceneBaselineState.PAGE_ATOMIC_FENCE));
  for(int category:new int[]{1,2,SceneBaselineState.PAGE_PRESENTATION_SCENERY,SceneBaselineState.PAGE_PRESENTATION_WALLS})
   deliver(h,143,serverBaseline(SceneBaselineState.PRESENTATION_PROTOCOL_VERSION,sequence,category));
 }
 static void refuses(PacketHandler h,int opcode,byte[] payload,String why){
  try{deliver(h,opcode,payload);throw new AssertionError(why);}catch(RuntimeException expected){}
 }
 static void recovery()throws Exception{
  Config.DEBUG=false;RendererDouble r=renderer();PacketHandler h=new PacketHandler(r);
  Field unsafe=Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
  // Real outgoing framing, no socket or writer thread: allocate without opening
  // transport; finishPacket only queues bytes in the inherited buffer.
  orsc.net.Network_Socket transport=(orsc.net.Network_Socket)((Unsafe)unsafe.get(null)).allocateInstance(orsc.net.Network_Socket.class);
  transport.bufferBits=new orsc.buffers.RSBuffer_Bits(65536);h.setClientStream(transport);
  NativeLayeredTerrainResidentCache c=(NativeLayeredTerrainResidentCache)field(h,"nativeLayeredTerrainResidentCache");
  NativeLayeredTerrainClientResidency s=new NativeLayeredTerrainClientResidency();
  NativeLayeredTerrainSnapshot old=context(s,c,3,13);
  LayeredSceneContextState state=(LayeredSceneContextState)field(h,"layeredSceneContextState");
  state.acceptNative(8,1,100,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION,190,623,0,190,623,old);
  serverLoginPages(h,1);check(r.presentations==1,"ordinary login rejected server version8 presentation");
  refuses(h,143,serverBaseline(8,1,SceneBaselineState.PAGE_ATOMIC_FENCE),"version8 carried a version7-only atomic fence");
  c.clear(); // lost authoritative references, reproduces old prediction eviction
  Body staged=body(s,4,13,1,NativeLayeredTerrainChunk.UNIFORM_ENCODING);
  deliver(h,154,stage(staged.bytes));int resyncBytes=transport.bufferBits.packetEnd;
  deliver(h,154,stage(staged.bytes));deliver(h,154,stage(staged.bytes));
  check(transport.bufferBits.packetEnd==resyncBytes,"duplicate stage loss flooded resync");
  Body references=body(s,3,13,1,NativeLayeredTerrainChunk.UNIFORM_ENCODING);
  deliver(h,157,contextPacket(2,references.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION));
  check(state.getSequence()==1&&r.scopes==0&&r.clears==0,"failed context changed complete scene");
  deliver(h,143,baseline(2,false)); // PRE-FIX crashes here, real handlePacket entrypoint
  deliver(h,146,movement(2,false));
  serverLoginPages(h,2);check(r.presentations==1,"failed-context server version8 pages were published");
  refuses(h,143,serverBaseline(8,2,SceneBaselineState.PAGE_ATOMIC_FENCE),"quarantine bypassed atomic fence schema");
  check(r.movements==0&&state.getSequence()==1,"failed tail changed retained player");
  deliver(h,146,movement(1,false));
  check(r.movements==1,"valid retained-context movement was swallowed");
  refuses(h,143,baseline(3,false),"unknown future baseline swallowed");
  refuses(h,143,baseline(2,true),"malformed rejected baseline swallowed");
  refuses(h,146,movement(2,true),"malformed rejected movement swallowed");
  byte[] unknownMovement=movement(2,false);unknownMovement[0]=(byte)99;
  refuses(h,146,unknownMovement,"unknown movement schema swallowed");
  refuses(h,143,Arrays.copyOf(baseline(2,false),25),"truncated fixed baseline header swallowed");
  refuses(h,143,Arrays.copyOf(baseline(2,false),45),"truncated nonpaged baseline swallowed");
  byte[] unknownBaseline=baseline(2,false);unknownBaseline[0]=(byte)99;
  refuses(h,143,unknownBaseline,"unknown baseline schema swallowed");
  refuses(h,143,serverBaseline(9,2,SceneBaselineState.PAGE_PRESENTATION_SCENERY),"unknown generated baseline schema swallowed");
  byte[] truncatedPresentation=serverBaseline(8,2,SceneBaselineState.PAGE_PRESENTATION_SCENERY);
  refuses(h,143,Arrays.copyOf(truncatedPresentation,truncatedPresentation.length-1),"malformed version8 quarantined page swallowed");
  refuses(h,157,Arrays.copyOf(contextPacket(3,references.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION),6),"truncated context swallowed");
  refuses(h,157,contextPacket(3,references.bytes,"instance-other",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION),"wrong scope swallowed");
  refuses(h,157,contextPacket(3,references.bytes,"global","wrong-projection"),"wrong projection swallowed");
  refuses(h,157,legacyContext(),"recovery switched to legacy authority");
  check(state.getSequence()==1&&state.getNativeTerrainSnapshot()==old,"invalid recovery mutated retained authority");
  for(int i=3;i<8;i++){
   deliver(h,157,contextPacket(i,references.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION));
   deliver(h,143,baseline(i,false));deliver(h,146,movement(i,false));
  }
  check(transport.bufferBits.packetEnd==resyncBytes,"failed contexts sent false readiness or repeated resets");
  check((Integer)field(h,"quarantinedSceneDeltaCount")==0,"unexpected delta counter");
  // Untagged scene deltas cannot be interpreted in the rejected context.
  deliver(h,141,new byte[]{0,1,0,1,0});check(r.movements==1,"untagged delta crossed scope");
  check((Integer)field(h,"quarantinedSceneDeltaCount")==1,"quarantine counter missing");
  deliver(h,142,new byte[24]); // non-scene clock update still flows
  s.clear();Body fresh=body(s,3,13,1,NativeLayeredTerrainChunk.UNIFORM_ENCODING);
  deliver(h,157,contextPacket(8,fresh.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION));
  check(state.getSequence()==8&&r.scopes==1&&r.cover&&c.size()==9,"verified refresh failed atomic activation");
  check(transport.bufferBits.packetEnd>resyncBytes,"verified refresh had no readiness receipt");
  serverLoginPages(h,8);check(r.presentations==2,"verified recovery rejected server version8 presentation");
  deliver(h,146,movement(8,false));check(r.movements==2&&r.cover,"fresh player receipt released unverified static scene");
  refuses(h,143,baseline(7,false),"old quarantined context survived recovery");
  c.clear();
  for(int i=9;i<=24;i++)deliver(h,157,contextPacket(i,references.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION));
  refuses(h,157,contextPacket(25,references.bytes,"global",LayeredSceneContextState.NATIVE_LAYERED_PROJECTION),"failed context quarantine grew without a bound");
  h.resetLayeredSceneProtocolState();check(!state.hasContext()&&c.size()==0,"reconnect retained stale recovery");
  System.out.println("PASS real PacketHandler missing-context tail quarantine, guards, verified refresh and reconnect");
 }
 public static void main(String[] args)throws Exception{if(args[0].equals("route"))route();else recovery();}
}
'''


class NativeTerrainRecoveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="native-recovery-replay-")
        cls.work = Path(cls.temp.name)
        source = cls.work / "NativeRecoveryFixture.java"
        source.write_text(HARNESS)
        # mudclient is final and owns a live renderer. Shadow only that boundary
        # in this disposable test classpath, never PacketHandler or the decoders.
        renderer = cls.work / "mudclient.java"
        renderer.write_text('''package orsc;
public class mudclient {
 public mudclient(Object ignored) {}
 public void clearStaticScenePresentation() {}
 public void resetLayeredSceneActivationPresentation() {}
 public void applyLayeredSceneScope(int p,boolean c,boolean d,NativeLayeredTerrainSnapshot s) {}
 public void beginLayeredSceneActivation(boolean retained) {}
 public void setLayeredSceneActivationPending(boolean pending) {}
 public void applyCustomMovementUpdate(int x,int y,int d) {}
 public int getPlayerCount(){return 0;}
 public int getNpcCount(){return 0;}
 public ORSCharacter getLocalPlayer(){return null;}
 public int getGameObjectInstanceCount(){return 0;}
 public int getWallObjectInstanceCount(){return 0;}
 public int getMidRegionBaseX(){return 0;}
 public int getMidRegionBaseZ(){return 0;}
 public void replaceStaticScenePresentation(java.util.List<SceneBaselineState.Record> scenery,java.util.List<SceneBaselineState.Record> walls){}
}''')
        cls.cp = os.pathsep.join(map(str, [cls.work, CLIENT, CORE]))
        subprocess.run(["javac", "-cp", cls.cp, "-d", str(cls.work), str(renderer), str(source)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def replay(self, mode):
        result = subprocess.run(["java", "-Djava.awt.headless=true", "-cp", self.cp,
                                 "orsc.NativeRecoveryFixture", mode], cwd=self.work,
                                text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        print(result.stdout)

    def test_route_real_decoder_prediction_and_confirmed_halo_parity(self):
        self.replay("route")

    def test_failed_context_real_packet_tail_and_fresh_recovery(self):
        self.replay("recovery")


if __name__ == "__main__":
    unittest.main()
