#!/usr/bin/env python3
"""Maintained server packet generation → client decoding → actual CPU mesh path.

No game window/listener/account. The World constructor is bypassed only to avoid
opening a legacy archive/renderer; its real tile application, geometry input and
presentation elevation methods are invoked without replacement implementations.
"""
import hashlib
import json
import os
import runpy
import struct
import subprocess
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SUPPORT=runpy.run_path(str(ROOT/"tests/myworld/test-map-runtime-validation.py"))
HARNESS=r'''
import com.openrsc.server.io.*;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.net.rsc.NativeLayeredTerrainWireCache;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.rsc.struct.outgoing.*;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.Packet;
import com.openrsc.client.model.Sector;
import orsc.NativeLayeredTerrainPacketDecoder;
import orsc.NativeLayeredTerrainSnapshot;
import orsc.NativeLayeredTerrainResidentCache;
import orsc.buffers.RSBuffer;
import java.lang.reflect.*;
import java.util.Arrays;
import sun.misc.Unsafe;
public final class WideHeightPathFixture {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void put(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 public static void main(String[] args)throws Exception {
  NativeLayeredWorldPackage source=NativeLayeredWorldPackage.load(java.nio.file.Paths.get(args[0]));
  int protocol=Integer.parseInt(args[1]);
  check(source.getPresentationChunkSize()==24,"package presentation metadata changed");
  LayeredSceneContextStruct context=new LayeredSceneContextStruct();context.setOpcode(OpcodeOut.SEND_LAYERED_SCENE_CONTEXT);
  context.protocolVersion=protocol;context.sequence=1;context.serverTick=1;context.worldSpace="global";
  context.projectionId="native-layered-package-v1";context.logicalX=488;context.logicalY=600;context.logicalLevel=42;
  context.legacyX=488;context.legacyY=600;context.nativePackageId=source.getPackageId();context.nativePackageVersion=source.getPackageVersion();
  context.nativeManifestSha256=source.getManifestSha256();context.nativePresentationChunkSize=48;context.nativeCurrentChunkX=10;context.nativeCurrentChunkY=12;context.nativeChunkRadius=1;
  NativeLayeredTerrainWireCache cache=new NativeLayeredTerrainWireCache();
  for(NativeLayeredTerrainSector sector:source.getTerrainSectors().values()){
   LayeredSceneTerrainChunkStruct chunk=new LayeredSceneTerrainChunkStruct();
   chunk.chunkX=chunk.sourceSectorX=sector.getIdentity().getSectorX();chunk.chunkY=chunk.sourceSectorY=sector.getIdentity().getSectorY();
   chunk.available=true;chunk.payloadPresent=true;chunk.sourceEncoding=sector.getSourceEncoding();chunk.sourcePayloadSha256=sector.getSourceSha256();
   chunk.tileBytes=cache.getOrCompress(sector.getIdentity().toString(),sector.getSourceSha256(),48*48*11,sector::copyWireBytes).getCompressedBytes();
   context.nativeChunks.add(chunk);
  }
  Packet packet=new PayloadCustomGenerator().generate(context,null);check(packet.getID()==157,"wrong context opcode");
  byte[] bytes=new byte[packet.getBuffer().readableBytes()];packet.getBuffer().readBytes(bytes);packet.getBuffer().release();
  RSBuffer header=new RSBuffer(bytes);check(header.getUnsignedByte()==protocol,"wrong wire axis");header.get32();header.get32();
  check(header.readString().equals("global"),"world space lost");header.readString();header.get32();header.get32();check(header.get32()==42,"signed level lost");header.getShort();header.getShort();
  byte[] terrain=Arrays.copyOfRange(bytes,header.packetEnd,bytes.length);
  NativeLayeredTerrainResidentCache resident=new NativeLayeredTerrainResidentCache();
  NativeLayeredTerrainSnapshot snapshot=protocol==5
   ? NativeLayeredTerrainPacketDecoder.decodeV5(terrain,"global",42)
   : NativeLayeredTerrainPacketDecoder.decodeV8(terrain,"global",42,resident);
  check(snapshot.getAvailableChunkCount()==9,"wide neighborhood incomplete");
  if(protocol==8)check(resident.size()==9&&resident.getLastPayloads()==9,"current full context did not populate exact resident inventory");
  Sector[] window=new Sector[9];for(int i=0;i<window.length;i++)window[i]=Sector.blankLoaded();
  Class<?> worldClass=orsc.graphics.three.World.class;
  Method apply=worldClass.getDeclaredMethod("applyNativeLayeredTerrain",Sector[].class,int.class,int.class,NativeLayeredTerrainSnapshot.class,int.class,int.class);apply.setAccessible(true);
  apply.invoke(null,window,10,12,snapshot,0,0);
  Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
  Object world=((Unsafe)uf.get(null)).allocateInstance(worldClass);
  put(world,"colorToResource",new int[256]);put(world,"sectors",window);put(world,"nativeLayeredTerrainSnapshot",snapshot);
  put(world,"nativeLayeredTerrainAppliedSectionX",10);put(world,"nativeLayeredTerrainAppliedSectionY",12);
  Method build=worldClass.getDeclaredMethod("buildTerrainModelInput",int.class,int.class,int.class,Sector[].class);build.setAccessible(true);
  Object model=build.invoke(world,0,10,12,window);Object[] vertices=(Object[])field(model,"vertices");
  int side=(int)Math.sqrt(vertices.length);int[] heights={255,256,1024,65535};
  for(int x=0;x<4;x++){
   int localX=48+x,localZ=48+24;int expected=heights[x];
   check(source.findTile(WorldLocation.global(new WorldCoordinate(480+x,600,42))).get().getElevation()==expected,"package height narrowed");
   check(snapshot.createTile(480+x,600).groundElevation==expected,"decoder height narrowed");
   check(window[4].getTile(x,24).groundElevation==expected,"World materialization height narrowed");
   check((Integer)field(vertices[localZ+localX*side],"y")==-expected*3,"CPU terrain vertex height narrowed");
   check(((orsc.graphics.three.World)world).getPresentationTerrainElevation(localX*128,localZ*128)==expected*3,"presentation sampling height narrowed");
  }
  System.out.println("PASS package/maintained generator/decoder/World materialization/CPU mesh/sample heights255,256,1024,65535");
 }
}
'''


class MapWideHeightPathTest(unittest.TestCase):
    def test_maintained_wide_height_path(self):
        support_class=SUPPORT["MapRuntimeValidationTest"];support_class.setUpClass()
        try:
            support=support_class("test_installed_runtime_accepts_non_product_package");work=support_class.work
            package=support.generic("wide-height")
            raw=b"".join(struct.pack(">HBBBBBI",[255,256,1024,65535][x%4],2,0,0,0,0,0) for x in range(48) for y in range(48))
            manifest=json.loads((package/"manifest.json").read_text());manifest["terrainSectors"]=[]
            for x in range(9,12):
                for y in range(11,14):
                    relative=f"terrain/wide-{x}-{y}.raw";(package/relative).write_bytes(raw)
                    manifest["terrainSectors"].append(dict(worldSpace="global",level=42,sectorX=x,sectorY=y,
                        encoding="raw-layered-sector-v2-u16",path=relative,sha256=hashlib.sha256(raw).hexdigest()))
            (package/"manifest.json").write_text(json.dumps(manifest))
            cp=os.pathsep.join(map(str,[work,ROOT/"server/core.jar",ROOT/"Client_Base/Open_RSC_Client.jar"]))
            source=work/"WideHeightPathFixture.java";source.write_text(HARNESS)
            subprocess.run(["javac","-cp",cp,"-d",str(work),str(source)],check=True)
            # v5 is compatibility coverage; v8 is the maintained resident/atomic
            # activation path. Both carry 48-tile chunks, distinct from the
            # package's 24-tile presentation metadata.
            for protocol in (5,8):
                with self.subTest(protocol=protocol):
                    result=subprocess.run(["java","-cp",cp,"WideHeightPathFixture",str(package),str(protocol)],cwd=work,text=True,capture_output=True,timeout=30)
                    self.assertEqual(result.returncode,0,result.stdout+result.stderr)
                    self.assertIn("PASS package/maintained generator",result.stdout)
        finally:support_class.tearDownClass()


if __name__=="__main__":unittest.main()
