#!/usr/bin/env python3
"""Shared inventory identity, strict stable IDs and effective custom selectors."""
import json
import os
import re
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HARNESS = r'''
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
public final class RoofResourceFixture {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Object read(Class<?> type,String text)throws Exception {
  return type.getMethod("read",InputStream.class).invoke(null,text==null?null:new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
 }
 static int scalar(Object catalog,String method,int id)throws Exception{return (Integer)catalog.getClass().getMethod(method,int.class).invoke(catalog,id);}
 static void refuse(Class<?> type,String text)throws Exception {
  try{read(type,text);throw new AssertionError("malformed inventory accepted: "+text);}
  catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"wrong refusal");}
 }
 public static void main(String[] args)throws Exception {
  int[][] golden={{64,6},{64,3},{96,2},{80,33},{80,15},{90,49}};
  for(String name:new String[]{"com.openrsc.server.io.TerrainRoofDefinitionCatalog","com.openrsc.client.entityhandling.TerrainRoofDefinitionCatalog"}){
   Class<?> type=Class.forName(name);Object shared=type.getMethod("shared").invoke(null);
   check((Integer)type.getMethod("getCount").invoke(shared)==6,"default count changed");
   for(int i=0;i<6;i++)check(scalar(shared,"getHeight",i)==golden[i][0]&&scalar(shared,"getFill",i)==golden[i][1],"default scalar changed");
   String base="schemaVersion=1\nids=0,1\nroof.1=65535,-123\nroof.0=64,6\n";
   Object reordered=read(type,base);check(scalar(reordered,"getHeight",1)==65535&&scalar(reordered,"getFill",0)==6,"physical rows renumbered");
   StringBuilder extended=new StringBuilder("schemaVersion=1\nids=0,1,2,3,4,5,6\n");
   for(int i=0;i<7;i++)extended.append("roof.").append(i).append("=80,15\n");
   check((Integer)type.getMethod("getCount").invoke(read(type,extended.toString()))==7,"hardcoded vanilla ceiling");
   refuse(type,null);refuse(type,base.replace("schemaVersion=1","schemaVersion=2"));
   refuse(type,base+"roof.0=64,6\n");refuse(type,base+"extra=1\n");
   refuse(type,base.replace("ids=0,1","ids=1,0"));refuse(type,base.replace("ids=0,1","ids=0,0"));refuse(type,base.replace("ids=0,1","ids=0,2"));
   refuse(type,base.replace("roof.1=65535,-123\n",""));refuse(type,base.replace("65535","65536"));refuse(type,base.replace("65535","-1"));refuse(type,base.replace("-123","no"));
   refuse(type,base+new String(new char[32769]).replace('\0',' '));
   StringBuilder excess=new StringBuilder("schemaVersion=1\nids=");for(int i=0;i<256;i++){if(i>0)excess.append(',');excess.append(i);}refuse(type,excess.toString());
  }
  com.openrsc.client.entityhandling.EntityHandler.load(true);
  for(int i=0;i<6;i++){
   Object roof=com.openrsc.client.entityhandling.EntityHandler.getElevationDef(i);
   check((Integer)roof.getClass().getMethod("getUnknown1").invoke(roof)==golden[i][0],"client effective height changed");
   check((Integer)roof.getClass().getMethod("getUnknown2").invoke(roof)==golden[i][1],"client effective fill changed");
  }
  com.openrsc.server.io.NativeTerrainMaterialPreflight.Definitions custom=new com.openrsc.server.io.NativeTerrainMaterialPreflight.Definitions(){
   public boolean tile(int id){return id==99;}public boolean door(int id){return id==254||id==299;}
   public boolean roof(int id){return id==254;}public boolean scenery(int id){return id==1335;}
  };
  com.openrsc.server.model.world.coordinate.WorldMapSectorId sector=new com.openrsc.server.model.world.coordinate.WorldMapSectorId(
   com.openrsc.server.model.world.coordinate.WorldSpaceId.GLOBAL,-7,10,12);
  for(int diagonal:new int[]{12300,49336})com.openrsc.server.io.NativeTerrainMaterialPreflight.validateTile("custom",sector,8,24,
   new com.openrsc.server.io.NativeLayeredTerrainTile(65535,255,100,255,255,255,diagonal),custom);
  try{
   com.openrsc.server.io.NativeTerrainMaterialPreflight.validateTile("custom",sector,8,24,
    new com.openrsc.server.io.NativeLayeredTerrainTile(0,0,101,0,0,0,0),custom);
   throw new AssertionError("unknown custom material accepted");
  }catch(IllegalStateException expected){String message=expected.getMessage();
   for(String part:new String[]{"package=custom","sector=","tile=488,600","field=overlay","raw=101","definition=100"})check(message.contains(part),"missing diagnostic "+part);
  }
  System.out.println("PASS shared strict readers and effective client roof inventory");
 }
}
'''


class MapRuntimeResourcesTest(unittest.TestCase):
    def test_version_axes_match_maintained_sources_not_numeric_maxima(self):
        contract = json.loads((ROOT / "runtime-contract/map-runtime-v1.json").read_text())
        updater = (ROOT / "server/src/com/openrsc/server/GameStateUpdater.java").read_text()
        axes = contract["axes"]
        mappings = {
            "currentSceneContextWriters": {
                "classic": "LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION", "synthetic": "SYNTHETIC_DEEP_SCENE_CONTEXT_PROTOCOL_VERSION",
                "nativeFull": "NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION", "nativeResident": "RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION",
                "nativeReady": "READY_RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION", "nativeAtomic": "ATOMIC_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION"},
            "currentSceneBaselineWriters": {
                "ordinary": "SCENE_BASELINE_PROTOCOL_VERSION", "layered": "LAYERED_SCENE_BASELINE_PROTOCOL_VERSION",
                "atomicFence": "ATOMIC_SCENE_FENCE_PROTOCOL_VERSION", "presentationPages": "LAYERED_PRESENTATION_SCENE_BASELINE_PROTOCOL_VERSION"},
            "currentMovementSnapshotWriters": {
                "ordinary": "MOVEMENT_SNAPSHOT_PROTOCOL_VERSION", "layered": "LAYERED_MOVEMENT_SNAPSHOT_PROTOCOL_VERSION"}}
        for axis, constants in mappings.items():
            for name, constant in constants.items():
                match = re.search(r"\b" + constant + r"\s*=\s*(\d+)\s*;", updater)
                self.assertIsNotNone(match, constant)
                self.assertEqual(axes[axis][name], int(match.group(1)), constant)
        self.assertEqual(axes["sceneContextReaders"], list(range(1, 9)))
        self.assertEqual(axes["sceneBaselineReaders"], list(range(1, 9)))
        self.assertEqual(axes["terrainProtocols"]["symmetricStructure"], 10)
        self.assertNotIn(10, axes["sceneContextReaders"])
        self.assertEqual(contract["geometry"]["packagePresentationChunkTiles"], 24)
        self.assertEqual(contract["geometry"]["currentActiveWireChunkTiles"], 48)
        self.assertFalse(contract["materialSelectors"]["roof"]["runtimeInventoryHashNegotiation"])

    def test_server_client_resources_identical_to_canonical_source(self):
        for filename in ("roof-definitions-v1.properties", "map-runtime-v1.json"):
            expected = (ROOT / "runtime-contract" / filename).read_bytes()
            for jar in (ROOT / "server/core.jar", ROOT / "Client_Base/Open_RSC_Client.jar"):
                with zipfile.ZipFile(jar) as archive:
                    self.assertEqual(archive.read("map-runtime/" + filename), expected)

    def test_strict_stable_ids_and_exact_effective_roofs(self):
        with tempfile.TemporaryDirectory(prefix="map-roof-inventory-") as directory:
            work = Path(directory)
            source = work / "RoofResourceFixture.java"
            source.write_text(HARNESS)
            cp = os.pathsep.join(map(str, (work, ROOT / "server/core.jar", ROOT / "Client_Base/Open_RSC_Client.jar")))
            subprocess.run(["javac", "-cp", cp, "-d", str(work), str(source)], check=True)
            result = subprocess.run(["java", "-cp", cp, "RoofResourceFixture"], cwd=work, text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS shared strict readers", result.stdout)


if __name__ == "__main__":
    unittest.main()
