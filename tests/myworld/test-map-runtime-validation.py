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
public final class RuntimeValidationFixture {
 public static void main(String[] args)throws Exception {
  if(args[0].equals("validate")) {
   NativeLayeredWorldPackageCatalog catalog=NativeLayeredWorldPackageCatalog.loadConfigured(args[2]);
   NativeLayeredWorldRuntimeProfile.fromConfiguration(args[1]).validate(catalog);
   System.out.println("VALID "+args[1]);return;
  }
  System.setProperty("openrsc.worldBuilderTargetRoot",args[2]+"/no-installed-primary");
  System.setProperty("openrsc.layeredPlayerLocationAuthority","true");
  System.setProperty("openrsc.layeredSpatialRuntimeAuthority","true");
  System.setProperty("openrsc.layeredProtocolClientAuthority","true");
  System.setProperty("openrsc.layeredNativeTerrainPackage","true");
  System.setProperty("openrsc.layeredNativeTerrainPackagePath",args[2]);
  System.setProperty("openrsc.layeredNativeWorldRuntimeProfile",args[1]);
  Server server=new Server("fixture.conf");server.getEntityHandler().load();
  World world=server.getWorld();int before=world.getNpcs().size();
  try {world.getRegionManager().populateNativeLayeredPlacements();}
  catch(IllegalStateException refusal) {
   System.out.println("REFUSED "+refusal.getMessage()+" npcBefore="+before+" npcAfter="+world.getNpcs().size());
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

    def invoke(self, mode, profile, package, success=True):
        env = {k: v for k, v in os.environ.items() if not k.startswith("OPENRSC_LAYERED_")}
        result = subprocess.run(["java", "-cp", self.cp, "RuntimeValidationFixture", mode, profile,
                                 str(package)], cwd=self.work, env=env, text=True, capture_output=True, timeout=30)
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

    def test_installed_runtime_accepts_non_product_package(self):
        package = self.package("generic")
        manifest = json.loads((package / "manifest.json").read_text())
        manifest["packageId"] = "independent.custom-world"
        manifest["packageVersion"] = "2.3.4"
        # One arbitrary authored level, with an explicit (empty) placement set.
        sector = manifest["terrainSectors"][1]
        manifest["levels"] = [dict(manifest["levels"][0], level=42)]
        sector["level"] = 42
        sector["sha256"] = hashlib.sha256((package / sector["path"]).read_bytes()).hexdigest()
        manifest["terrainSectors"] = [sector]
        relative = manifest["placementSets"][0]["path"]
        self.change_payload(package, relative, lambda d: d.update(schemaVersion=3,encoding="layered-world-placements-v3",level=42,npcs=[],groundItems=[],scenery=[],boundaries=[]))
        manifest["placementSets"][0]["level"] = 42
        manifest["placementSets"][0]["encoding"] = "layered-world-placements-v3"
        manifest["placementSets"][0]["sha256"] = hashlib.sha256((package / relative).read_bytes()).hexdigest()
        (package / "manifest.json").write_text(json.dumps(manifest))
        self.assertIn("VALID", self.invoke("validate", "world-builder-installed", package))

    def test_missing_late_definition_refuses_before_first_registration(self):
        package = self.package("late-missing-item")
        relative = "placements/deep-l2-entities.json"
        self.change_payload(package, relative, lambda d: d["groundItems"][0].update(itemId=65530))
        self.assertIn("npcAfter=0", self.invoke("populate", "fixture-additive", package))


if __name__ == "__main__":
    unittest.main()
