#!/usr/bin/env python3
"""Old-entrypoint map lookup parity; public definitions and synthetic terrain only.

MAP_FACADE_BASELINE_CORE_JAR optionally compares the same real Server/RegionManager
calls against a frozen pre-facade maintained build. No listener or accounts.
"""
import hashlib
import json
import os
import runpy
import subprocess
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUPPORT = runpy.run_path(str(ROOT / "tests/myworld/test-map-runtime-validation.py"))
HARNESS = r'''
import com.openrsc.server.Server;
import com.openrsc.server.io.*;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.model.world.region.*;
import com.openrsc.server.model.Point;
public final class MapAuthorityFixture {
 static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
 static void refused(Runnable operation){try{operation.run();throw new AssertionError("missing refusal");}catch(IllegalArgumentException|UnsupportedOperationException expected){}}
 static String error(Runnable operation){try{operation.run();throw new AssertionError("missing error");}catch(RuntimeException expected){return expected.getClass().getName()+":"+expected.getMessage();}}
 public static void main(String[] args)throws Exception {
  boolean classic=args[0].startsWith("classic")||args[0].equals("synthetic");
  System.setProperty("openrsc.worldBuilderTargetRoot",args[2]+"/absent-primary");
  System.setProperty("openrsc.layeredPlayerLocationAuthority","true");
  System.setProperty("openrsc.layeredSpatialRuntimeAuthority","true");
  System.setProperty("openrsc.layeredProtocolClientAuthority","true");
  System.setProperty("openrsc.layeredNativeTerrainPackage",classic?"false":"true");
  System.setProperty("openrsc.layeredNativeWorldRuntimeProfile",args[1]);
  if(!classic)System.setProperty("openrsc.layeredNativeTerrainPackagePath",args[2]);
  if(args[0].equals("synthetic"))System.setProperty("openrsc.layeredSyntheticDeepFixture","true");
  if(args.length>3&&!args[3].isEmpty())System.setProperty("openrsc.layeredNativeTerrainManifestSha256",args[3]);
  Server server=new Server("fixture.conf");server.getEntityHandler().load();
  World world=server.getWorld();RegionManager regions=world.getRegionManager();
  if(args[0].equals("classic-rollback")){
   if(server.getConfig().mapSourceActivationPlan().replacesLegacyBasePopulation()||regions.replacesLegacyBasePopulation())throw new AssertionError("rollback did not resolve classic");
   server.getConfig().WANT_CUSTOM_LANDSCAPE=true;regions.load();
   TileValue loaded=regions.getTile(at(450,600,0));
   if((loaded.elevation&255)!=23||loaded.overlay!=0||(loaded.traversalMask&64)!=0)throw new AssertionError("classic archive was skipped "+loaded);
   System.err.println("SNAP rollback="+loaded);return;
  }
  if(args[0].equals("synthetic")){
   if((regions.getTile(at(450,600,-2)).traversalMask&64)!=0)throw new AssertionError("synthetic fixture changed");
   refused(()->regions.getMutableTile(at(450,600,-2)));
   System.err.println("SNAP synthetic="+regions.getTile(at(450,600,-2)));return;
  }
  System.err.println("SNAP null="+error(()->regions.getTile((WorldLocation)null)));
  System.err.println("SNAP overflow="+error(()->regions.getTile(at(Integer.MAX_VALUE,600,43))));
  TileValue packed=regions.getMutableTile(450,600);packed.overlay=3;packed.elevation=(byte)137;
  packed.initializeTerrainCollision();packed.addDynamicCollision(4);packed.addCombatProjectileCollision(2);
  if(classic||args[0].equals("additive")){
   if(regions.getTile(at(450,600,0))!=packed)throw new AssertionError("legacy identity changed");
   System.err.println("SNAP classic="+packed);
   if(regions.hasNativeLayeredTerrain(at(450,600,0)))throw new AssertionError("classic mistaken native");
   if(regions.getMutableTile(at(450,600,0))!=packed)throw new AssertionError("mutable legacy identity changed");
   refused(()->regions.getTile(at(450,600,42)));
  }
  if(classic)return;
  int level=args[0].equals("additive")?-2:42;
  WorldLocation owned=at(args[0].equals("additive")?452:488,600,level);
  NativeLayeredWorldPackage owner=regions.findNativeLayeredWorldPackage(owned).get();
  NativeLayeredTerrainTile raw=owner.findTile(owned).get();TileValue tile=regions.getTile(owned);
  if((tile.elevation&255)!=(raw.getElevation()&255)||tile.overlay!=(byte)raw.getOverlay())throw new AssertionError("native field projection changed");
  if(!regions.hasNativeLayeredTerrain(owned)||regions.getTile(owned).equals(packed))throw new AssertionError("native authority collapsed into packed carrier");
  if(!regions.findNativeLayeredWorldPackage(owner.getPackageId()).get().equals(owner))throw new AssertionError("owner identity changed");
  refused(()->regions.getMutableTile(owned));
  System.err.println("SNAP native="+tile);
  if(args[0].equals("draft")){
   Object sessions=server.getWorldEditorSessions();
   Class<?> keyClass=Class.forName("com.openrsc.server.content.worldedit.WorldEditorSessionManager$NativeTileKey");
   java.lang.reflect.Constructor<?> keyConstructor=keyClass.getDeclaredConstructor(WorldLocation.class);keyConstructor.setAccessible(true);
   java.lang.reflect.Field overlays=sessions.getClass().getDeclaredField("nativeTerrainOverlay");overlays.setAccessible(true);
   java.lang.reflect.Field growth=sessions.getClass().getDeclaredField("nativeTerrainGrowth");growth.setAccessible(true);
   WorldLocation draft=at(600,600,42);
   ((java.util.Map)overlays.get(sessions)).put(keyConstructor.newInstance(draft),new NativeLayeredTerrainTile(65535,2,255,0,1,2,12001));
   ((java.util.Set)growth.get(sessions)).add(WorldMapSectorId.from(draft));
   if(regions.hasNativeLayeredTerrain(draft))throw new AssertionError("disabled authoring leaked into runtime");
   server.getConfig().WORLD_BUILDER_MODE=true;
   if(!regions.hasNativeLayeredTerrain(draft)||regions.findNativeLayeredWorldPackage(draft).get()!=owner)throw new AssertionError("explicit authoring owner changed");
   TileValue drafted=regions.getTile(draft);
   if((drafted.elevation&255)!=255||!drafted.isTerrainBlocked())throw new AssertionError("draft conversion changed");
   refused(()->regions.getMutableTile(draft));System.err.println("SNAP draft="+drafted);
   server.getConfig().WORLD_BUILDER_MODE=false;
   if(regions.hasNativeLayeredTerrain(draft))throw new AssertionError("draft persisted as saved authority");return;
  }
  if(args[0].equals("additive")){
   regions.populateNativeLayeredPlacements();
   System.err.println("SNAP populated="+regions.getTile(at(446,604,-2)));
   if(world.getNpcs().size()!=1||regions.getNativeLayeredSceneryCount()!=2||regions.getNativeLayeredBoundaryCount()!=2)throw new AssertionError("population changed");
  }else{
   WorldLocation absent=at(600,600,42);
   if(regions.hasNativeLayeredTerrain(absent)||regions.findNativeLayeredWorldPackage(absent).isPresent())throw new AssertionError("absent gained owner");
   refused(()->regions.getTile(absent));
   TileValue missingLegacy=regions.getTile(at(600,600,0));
   if((missingLegacy.traversalMask&64)==0)throw new AssertionError("blank void became walkable");
   System.err.println("SNAP absent="+missingLegacy);
   System.err.println("SNAP border="+regions.getTile(at(527,600,42)));
   if(args[0].equals("border"))System.err.println("SNAP neighbor-owner="+regions.getTile(at(528,600,42)));
  }
 }
}
'''


class MapAuthorityFacadeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.support_class=SUPPORT["MapRuntimeValidationTest"]
        cls.support_class.setUpClass()
        cls.work=cls.support_class.work
        cls.support=cls.support_class("test_installed_runtime_accepts_non_product_package")
        cls.core=Path(os.environ.get("MAP_FACADE_CORE_JAR",ROOT/"server/core.jar"))
        cls.cp=os.pathsep.join(map(str,[cls.work,cls.core]))
        source=cls.work/"MapAuthorityFixture.java"
        source.write_text(HARNESS)
        subprocess.run(["javac","-cp",cls.cp,"-d",str(cls.work),str(source)],check=True)
        data=cls.work/"conf/server/data";data.mkdir(exist_ok=True)
        with zipfile.ZipFile(data/"Custom_Landscape.orsc","w") as archive:
            archive.writestr("h0x57y49",bytes([23,2,0,0,0,0,0,0,0,0])*2304)

    @classmethod
    def tearDownClass(cls):
        cls.support_class.tearDownClass()

    def invoke(self,mode,profile,package,manifest="",core=None):
        cp=self.cp if core is None else os.pathsep.join(map(str,[self.work,core]))
        env={k:v for k,v in os.environ.items() if not k.startswith("OPENRSC_LAYERED_")}
        result=subprocess.run(["java","-cp",cp,"MapAuthorityFixture",mode,profile,str(package),manifest],
                              cwd=self.work,env=env,text=True,capture_output=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        snapshots=[line.split("SNAP ",1)[1] for line in (result.stdout+result.stderr).splitlines() if "SNAP " in line]
        self.assertTrue(snapshots,result.stdout+result.stderr)
        baseline=os.environ.get("MAP_FACADE_BASELINE_CORE_JAR")
        if baseline and core is None and mode!="classic-rollback":
            self.assertEqual(snapshots,self.invoke(mode,profile,package,manifest,core=Path(baseline)))
        return snapshots

    def test_classic_typed_packed_identity_and_nonrepresentable_refusal(self):
        self.invoke("classic","native-additive",self.work)

    def test_classic_rollback_with_retained_replacement_profile_loads_real_archive(self):
        self.invoke("classic-rollback","native-replacement",self.work)

    def test_explicit_synthetic_fixture_mutable_refusal(self):
        self.invoke("synthetic","native-additive",self.work)

    def test_explicit_authoring_bridge_retains_runtime_conditional_draft_behavior(self):
        package=self.support.generic("authority-draft")
        self.invoke("draft","native-replacement",package,hashlib.sha256((package/"manifest.json").read_bytes()).hexdigest())

    def test_additive_native_signed_scope_registered_collision_and_classic_fallback(self):
        package=self.support.package("authority-additive")
        self.invoke("additive","native-additive",package)

    def test_replacement_absence_and_frozen_field_projection(self):
        package=self.support.generic("authority-replacement")
        self.invoke("replacement","native-replacement",package,hashlib.sha256((package/"manifest.json").read_bytes()).hexdigest())

    def test_collision_neighbors_remain_within_the_original_owner(self):
        first=self.support.generic("owner-left")
        second=self.support.generic("owner-right",sector_x=11)
        manifest=json.loads((second/"manifest.json").read_text());manifest["packageId"]="other.owner"
        (second/"manifest.json").write_text(json.dumps(manifest))
        self.support.change_payload(second,"terrain/deep-l2-x10-y12.json",lambda d:d["tile"].update(horizontalWall=1))
        single=self.invoke("replacement","native-replacement",first,hashlib.sha256((first/"manifest.json").read_bytes()).hexdigest())
        composed=self.invoke("border","native-replacement",os.pathsep.join(map(str,[first,second])),hashlib.sha256((first/"manifest.json").read_bytes()).hexdigest())
        self.assertEqual(next(x for x in single if x.startswith("border=")),next(x for x in composed if x.startswith("border=")))


if __name__=="__main__":
    unittest.main()
