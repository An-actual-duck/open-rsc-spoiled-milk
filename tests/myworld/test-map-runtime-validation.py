#!/usr/bin/env python3
"""Runtime policy and before-mutation characterization over maintained Core.

Freshly build server/core.jar first. Inputs and definition copies live only in
temporary directories; no listening server, account data or live settings.
"""
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = Path(os.environ.get("MAP_VALIDATION_CORE_JAR", ROOT / "server/core.jar"))
HARNESS = r'''
import com.openrsc.server.Server;
import com.openrsc.server.io.*;
import com.openrsc.server.model.world.World;
import com.openrsc.server.external.*;
public final class RuntimeValidationFixture {
 public static void main(String[] args)throws Exception {
  if(args[0].equals("validate")) {
   NativeLayeredWorldPackageCatalog catalog=NativeLayeredWorldPackageCatalog.loadConfigured(args[2]);
   NativeLayeredWorldRuntimeProfile.fromConfiguration(args[1]).validate(catalog);
   System.out.println("VALID "+args[1]);return;
  }
  if(args[0].equals("manifest")) {
   NativeLayeredWorldPackageCatalog catalog=NativeLayeredWorldPackageCatalog.loadConfigured(args[2]);
   NativeLayeredWorldRuntimeProfile profile=NativeLayeredWorldRuntimeProfile.fromConfiguration(args[1]);
   profile.validate(catalog);profile.validateConfiguredManifest(catalog,args[3]);
   System.out.println("MANIFEST VALID");return;
  }
  if(args[0].equals("preflight")) {
   NativeLayeredWorldPackageCatalog catalog=NativeLayeredWorldPackageCatalog.loadConfigured(args[2]);
   NativeLayeredWorldPlacementPreflight.validate(catalog,new NativeLayeredWorldPlacementPreflight.Definitions() {
    public boolean npc(int id){return true;} public boolean item(int id){return true;}
    public GameObjectDef scenery(int id){GameObjectDef d=new GameObjectDef();d.name="fixture";d.type=1;d.width=2;d.height=1;return d;}
    public DoorDef boundary(int id){DoorDef d=new DoorDef();d.name="fixture";d.doorType=1;return d;}
   });System.out.println("PREFLIGHT VALID");return;
  }
  System.setProperty("openrsc.worldBuilderTargetRoot",args[2]+"/no-installed-primary");
  System.setProperty("openrsc.layeredPlayerLocationAuthority","true");
  System.setProperty("openrsc.layeredSpatialRuntimeAuthority","true");
  System.setProperty("openrsc.layeredProtocolClientAuthority","true");
  System.setProperty("openrsc.layeredNativeTerrainPackage","true");
  System.setProperty("openrsc.layeredNativeTerrainPackagePath",args[2]);
  System.setProperty("openrsc.layeredNativeWorldRuntimeProfile",args[1]);
  Server server=new Server("fixture.conf");server.getEntityHandler().load();
  if(args.length>3)server.getConfig().RESTRICT_ITEM_ID=Integer.parseInt(args[3]);
  World world=server.getWorld();int before=world.getNpcs().size();
  try {world.getRegionManager().populateNativeLayeredPlacements();}
  catch(IllegalStateException refusal) {
   System.err.println("REFUSED "+refusal.getMessage()+" npcBefore="+before+" npcAfter="+world.getNpcs().size());
   if(world.getNpcs().size()!=before)throw new AssertionError("invalid later placement caused partial native population npcBefore="+before+" npcAfter="+world.getNpcs().size());
   return;
  }
  throw new AssertionError("invalid placement unexpectedly populated");
 }
}
'''


class MapRuntimeValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="map-runtime-validation-")
        cls.work = Path(cls.temp.name)
        (cls.work / "RuntimeValidationFixture.java").write_text(HARNESS)
        cls.cp = os.pathsep.join(map(str, [cls.work, CORE]))
        subprocess.run(["javac", "-cp", cls.cp, "-d", str(cls.work),
                        str(cls.work / "RuntimeValidationFixture.java")], check=True)
        (cls.work / "connections.conf").write_text("db_type: sqlite\ndb_name: unused-fixture\n")
        (cls.work / "fixture.conf").write_text("server_name: Runtime validation fixture\navatar_generator: false\n")
        shutil.copytree(ROOT / "server/conf/server/defs", cls.work / "conf/server/defs")
        shutil.copyfile(ROOT / "server/conf/server/aliases.xml", cls.work / "conf/server/aliases.xml")
        shutil.copytree(ROOT / "server/conf/server/languages", cls.work / "conf/server/languages")
        for queries in (ROOT / "server/database").glob("*/queries"):
            shutil.copytree(queries, cls.work / "database" / queries.parent.name / "queries")

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def package(self, name):
        target = self.work / name
        shutil.copytree(ROOT / "tools/layered-maps/fixtures/native-package-v1", target)
        return target

    def invoke(self, mode, profile, package, success=True, extra=()):
        env = {k: v for k, v in os.environ.items() if not k.startswith("OPENRSC_LAYERED_")}
        result = subprocess.run(["java", "-cp", self.cp, "RuntimeValidationFixture", mode, profile,
                                 str(package), *extra], cwd=self.work, env=env, text=True, capture_output=True, timeout=30)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result.stdout + result.stderr

    @staticmethod
    def change_payload(package, relative, mutate):
        payload = package / relative
        document = json.loads(payload.read_text())
        mutate(document)
        payload.write_text(json.dumps(document))
        manifest = json.loads((package / "manifest.json").read_text())
        for section in ["placementSets", "terrainSectors"]:
            for value in manifest[section]:
                if value["path"] == relative:
                    value["sha256"] = hashlib.sha256(payload.read_bytes()).hexdigest()
        (package / "manifest.json").write_text(json.dumps(manifest))

    def generic(self, name, level=42, empty=True, sector_x=10):
        package = self.package(name)
        manifest = json.loads((package / "manifest.json").read_text())
        manifest["packageId"] = "independent.custom-world"
        manifest["packageVersion"] = "2.3.4"
        # One arbitrary authored level, with an explicit (empty) placement set.
        sector = manifest["terrainSectors"][1]
        manifest["levels"] = [dict(manifest["levels"][0], level=level)]
        sector["level"] = level
        sector["sectorX"] = sector_x
        sector["sha256"] = hashlib.sha256((package / sector["path"]).read_bytes()).hexdigest()
        manifest["terrainSectors"] = [sector]
        relative = manifest["placementSets"][0]["path"]
        def payload(d):
            d.update(schemaVersion=3,encoding="layered-world-placements-v3",level=level)
            if empty:
                d.update(npcs=[],groundItems=[],scenery=[],boundaries=[])
            else:
                d["npcs"][0].update(npcId=866,start=dict(x=sector_x*48+8,y=600))
                d["npcs"][0].pop("roamRadius")
                d["npcs"][0]["roamBounds"]=dict(minimum=dict(x=sector_x*48+6,y=598),maximum=dict(x=sector_x*48+10,y=602))
                d["groundItems"][0].update(itemId=3300,position=dict(x=sector_x*48+12,y=600))
                d["scenery"]= [dict(placementId="custom-scenery",sceneryId=1300,position=dict(x=sector_x*48+16,y=604),direction=0)]
                d["boundaries"]= [dict(placementId="custom-boundary",boundaryId=250,position=dict(x=sector_x*48+20,y=604),direction=0)]
        self.change_payload(package, relative, payload)
        manifest["placementSets"][0]["level"] = level
        manifest["placementSets"][0]["encoding"] = "layered-world-placements-v3"
        manifest["placementSets"][0]["sha256"] = hashlib.sha256((package / relative).read_bytes()).hexdigest()
        (package / "manifest.json").write_text(json.dumps(manifest))
        return package

    def test_installed_runtime_accepts_non_product_package(self):
        package = self.generic("generic")
        self.assertIn("VALID", self.invoke("validate", "world-builder-installed", package))

    def test_signed_authored_levels_custom_ids_and_explicit_product_refusal(self):
        for level in [-17,42]:
            package=self.generic("custom-"+str(level),level,empty=False)
            self.assertIn("VALID",self.invoke("validate","native-replacement",package))
            self.assertIn("PREFLIGHT VALID",self.invoke("preflight","native-replacement",package))
            self.invoke("validate","spoiled-milk-installed-review",package,success=False)
            self.invoke("validate","preservation-r64-replacement",package,success=False)

    def test_default_additive_preserves_old_fixture_and_replacement_requires_coverage(self):
        package=self.package("additive")
        self.invoke("validate","fixture-additive",package)
        self.invoke("validate","native-additive",package)
        self.invoke("validate","native-replacement",package,success=False)

    def test_replacement_manifest_required_and_additive_supplied_hash_verified(self):
        package=self.generic("identity")
        actual=hashlib.sha256((package/"manifest.json").read_bytes()).hexdigest()
        for profile in ["native-replacement","world-builder-installed","native-additive"]:
            self.invoke("manifest",profile,package,extra=[actual])
            self.invoke("manifest",profile,package,success=False,extra=["0"*64])
        self.invoke("manifest","native-replacement",package,success=False,extra=[""])
        self.invoke("manifest","native-additive",package,extra=[""])

    def test_unsupported_instance_capability_is_not_product_policy(self):
        package=self.generic("instance")
        manifest=json.loads((package/"manifest.json").read_text())
        manifest["worldSpaces"][0]["kind"]="instance-template"
        (package/"manifest.json").write_text(json.dumps(manifest))
        self.assertIn("Unsupported runtime capability",self.invoke("validate","native-additive",package,success=False))

    def test_authored_placement_footprint_must_remain_in_owner_terrain(self):
        package=self.generic("edge",empty=False)
        self.change_payload(package,"placements/deep-l2-entities.json",lambda d:d["scenery"][0]["position"].update(x=527))
        self.assertIn("leaves its package terrain",self.invoke("preflight","native-replacement",package,success=False))

    def test_existing_collision_world_bounds_not_silently_widened(self):
        package=self.generic("outside-collision-world",empty=False,sector_x=21)
        self.assertIn("OUT_OF_WORLD_EFFECT",self.invoke("preflight","native-replacement",package,success=False))

    def test_payload_hash_mismatch_still_refused(self):
        package=self.generic("bad-hash")
        with (package/"placements/deep-l2-entities.json").open("a") as output: output.write(" ")
        self.invoke("validate","native-additive",package,success=False)

    def test_missing_late_definition_refuses_before_first_registration(self):
        package = self.package("late-missing-item")
        relative = "placements/deep-l2-entities.json"
        self.change_payload(package, relative, lambda d: d["groundItems"][0].update(itemId=65530))
        self.assertIn("npcAfter=0", self.invoke("populate", "fixture-additive", package))

    def test_each_definition_family_preflight_is_atomic(self):
        for family, key in [("npcs","npcId"),("scenery","sceneryId"),("boundaries","boundaryId")]:
            package=self.package("missing-"+family)
            self.change_payload(package,"placements/deep-l2-entities.json",lambda d:d[family][-1].update({key:65530}))
            self.assertIn("npcAfter=0",self.invoke("populate","fixture-additive",package))

    def test_configured_item_restriction_refuses_before_npc_mutation(self):
        package=self.package("restricted-effective-item")
        self.assertIn("npcAfter=0",self.invoke("populate","fixture-additive",package,extra=["9"]))


if __name__ == "__main__":
    unittest.main()
