#!/usr/bin/env python3
"""Executable startup characterization; not private-game acceptance.

Requires freshly source-built server/core.jar and Client_Base/Open_RSC_Client.jar.
No server launch, database or account access. All descriptors live in temp dirs.
"""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "server/core.jar"
CLIENT = ROOT / "Client_Base/Open_RSC_Client.jar"
HARNESS = r"""
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.io.*;
import java.lang.reflect.Method;
public final class MapStartupHarness {
 public static void main(String[] args) throws Exception {
  System.setProperty("openrsc.worldBuilderTargetRoot", args[0]);
  for (int i=2; i<args.length; i++) {
   String[] pair=args[i].split("=", 2); System.setProperty(pair[0], pair[1]);
  }
  if (args[1].equals("evidence")) {
   System.out.println(WorldBuilderInstalledMapActivation.inspect().getStatus()); return;
  }
  if (args[1].equals("client")) {
   System.out.println(orsc.WorldBuilderTerrainBootstrap.isNativeOnly());
   System.out.println(orsc.WorldBuilderTerrainBootstrap.report()); return;
  }
  if (args[1].equals("alias")) {
   NativeLayeredWorldRuntimeProfile profile = NativeLayeredWorldRuntimeProfile.fromConfiguration("world-builder-installed");
   if (profile != NativeLayeredWorldRuntimeProfile.SPOILED_MILK_EDITOR_INSTALLED
      || !profile.requiresConfiguredManifestSha256() || !profile.skipsLegacyTerrainArchive())
     throw new AssertionError("alias changed validation/ownership");
   System.out.println(profile.getId()); return;
  }
  if (args[1].equals("immutability")) {
   boolean[] gates=new boolean[9]; gates[3]=true;
   MapSourceActivationPlan plan=MapSourceActivationPlan.resolve(gates,new boolean[9],true,
       "external", "hash", "fixture-additive", "test", "absent", "", "");
   gates[3]=false;
   if (!plan.gate(3)) throw new AssertionError("mutable gates");
   System.out.println("immutable"); return;
  }
  ServerConfiguration config=new ServerConfiguration();
  if (args[1].equals("init")) {
   config.initConfig(args[0]+"/fixture.conf");
   System.out.println(config.mapSourceActivationPlan().report()); return;
  }
  // Values already resolved by the existing system > env > config readers.
  // Isolate startup selection itself without loading connections/accounts.
  String[] suffix={"PLAYER_LOCATION_AUTHORITY","SPATIAL_RUNTIME_AUTHORITY","PROTOCOL_CLIENT_AUTHORITY",
     "NATIVE_TERRAIN_PACKAGE","NATIVE_TERRAIN_RESIDENCY","NATIVE_TERRAIN_READINESS",
     "NATIVE_TERRAIN_PREDICTION","NATIVE_TERRAIN_SYMMETRIC_RESIDENCY","NATIVE_TERRAIN_ATOMIC_ACTIVATION"};
  String[] names={"PlayerLocationAuthority","SpatialRuntimeAuthority","ProtocolClientAuthority",
     "NativeTerrainPackage","NativeTerrainResidency","NativeTerrainReadiness","NativeTerrainPrediction",
     "NativeTerrainSymmetricResidency","NativeTerrainAtomicActivation"};
  for (int i=0;i<names.length;i++) {
   String raw=System.getProperty("openrsc.layered"+names[i], System.getenv("OPENRSC_LAYERED_"+suffix[i]));
   config.getClass().getField("WANT_LAYERED_"+suffix[i]).setBoolean(config,
       raw==null ? args[1].equals("configured") : Boolean.parseBoolean(raw));
  }
  config.LAYERED_NATIVE_TERRAIN_PACKAGE_PATH=System.getProperty("openrsc.layeredNativeTerrainPackagePath",
      System.getenv().getOrDefault("OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH", "configured/package"));
  config.LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256="configured-hash";
  config.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE=System.getProperty("openrsc.layeredNativeWorldRuntimeProfile",
      System.getenv().getOrDefault("OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE", "world-builder-installed"));
  Method apply=config.getClass().getDeclaredMethod("applyInstalledWorldBuilderMap");
  apply.setAccessible(true); apply.invoke(config);
  System.out.println(config.mapSourceActivationPlan().report());
 }
}
"""


class MapSourceActivationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not CORE.is_file() or not CLIENT.is_file():
            raise RuntimeError("Build maintained server and client before this suite")
        cls.temp = tempfile.TemporaryDirectory(prefix="map-startup-")
        cls.work = Path(cls.temp.name)
        # initConfig reads this file but does not open any database/service.
        (cls.work / "connections.conf").write_text("db_type: sqlite\ndb_name: unused_test_only\n")
        (cls.work / "MapStartupHarness.java").write_text(HARNESS)
        cls.classpath = os.pathsep.join(map(str, [cls.work, CORE, CLIENT]))
        subprocess.run(["javac", "-cp", cls.classpath, "-d", str(cls.work),
                        str(cls.work / "MapStartupHarness.java")], check=True)
        cls.primary = json.loads((ROOT / "server/world-builder-configs/primary.json").read_text())
        cls.installed = cls.work / "installed"
        for key in ["serverMapRelativePath", "clientMapRelativePath"]:
            shutil.copytree(ROOT / cls.primary[key], cls.installed / cls.primary[key])
        cls.write_primary(cls.installed, cls.primary)
        cls.client_profile = cls.installed / "Client_Base/world-builder-configs/installed-client.json"
        cls.client_profile.parent.mkdir(parents=True)
        shutil.copyfile(ROOT / "Client_Base/world-builder-configs/installed-client.json", cls.client_profile)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @staticmethod
    def write_primary(root, document):
        path = root / "server/world-builder-configs/primary.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document))

    def run_harness(self, root, mode="configured", properties=(), env=None, success=True):
        environment = {k: v for k, v in os.environ.items()
                       if not k.startswith("OPENRSC_LAYERED_")}
        environment.update(env or {})
        result = subprocess.run(["java", "-cp", self.classpath, "MapStartupHarness",
                                 str(root), mode, *properties], cwd=self.work,
                                env=environment, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result.stdout + result.stderr

    def test_absent_and_packed_remain_distinct_and_inert(self):
        absent = self.work / "absent"
        self.assertIn("absent", self.run_harness(absent, "evidence"))
        self.assertIn("map-source=configuration", self.run_harness(absent))
        packed = self.work / "packed"
        self.write_primary(packed, dict(self.primary, representation="packed"))
        self.assertIn("packed-inert", self.run_harness(packed, "evidence"))
        self.assertIn("primary=packed-inert", self.run_harness(packed))

    def test_verified_primary_overrides_configured_pin_but_not_launch(self):
        automatic = self.run_harness(self.installed)
        self.assertIn("map-source=installed-primary", automatic)
        self.assertNotIn("package=configured/package", automatic)
        explicit = self.run_harness(self.installed, properties=[
            "openrsc.layeredNativeTerrainPackagePath=external/package"])
        self.assertIn("map-source=explicit-launch", explicit)
        self.assertIn("package=external/package", explicit)
        self.assertIn("installed primary not selected", explicit)

    def test_system_over_environment_and_explicit_disabled_gate_conflict(self):
        result = self.run_harness(self.installed, properties=[
            "openrsc.layeredNativeTerrainPackagePath=system/package"], env={
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH": "environment/package"})
        self.assertIn("package=system/package", result)
        self.assertIn("NativeTerrainPackagePath:system", result)
        self.assertIn("explicitly disabled authority gate", self.run_harness(self.installed,
            properties=["openrsc.layeredSpatialRuntimeAuthority=false"], success=False))
        self.assertIn("Invalid map authority boolean", self.run_harness(self.installed,
            properties=["openrsc.layeredNativeTerrainPackage=perhaps"], success=False))
        self.assertIn("package path conflicts", self.run_harness(self.installed,
            properties=["openrsc.layeredNativeTerrainPackage=false",
                        "openrsc.layeredNativeTerrainPackagePath=external/package"], success=False))

    def test_explicit_legacy_rollback_does_not_reenable_installed(self):
        result = self.run_harness(self.installed, "classic", env={
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE": "false",
            "OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE": "fixture-additive"})
        self.assertIn("representation=classic", result)
        self.assertIn("package=inactive", result)
        self.assertIn("profile=fixture-additive", result)

    def test_malformed_primary_never_silently_falls_back(self):
        root = self.work / "malformed"
        self.write_primary(root, {"representation": "layered"})
        for properties in [[], ["openrsc.layeredNativeTerrainPackage=false"]]:
            self.assertIn("configuration identity is invalid", self.run_harness(
                root, properties=properties, success=False))
        path = root / "server/world-builder-configs/primary.json"
        path.unlink()
        path.symlink_to("missing.json")
        self.assertIn("Unsafe World Builder file", self.run_harness(root, success=False))

    def test_profile_alias_and_immutable_snapshot(self):
        self.assertIn("spoiled-milk-editor-installed", self.run_harness(self.work, "alias"))
        self.assertIn("immutable", self.run_harness(self.work, "immutability"))

    def test_client_classic_absence_and_malformed_profile(self):
        self.assertIn("client-map-source=classic", self.run_harness(self.work, "client",
            properties=["openrsc.worldBuilderInstalledClientProfile=" + str(self.work / "missing.json")]))
        broken = self.work / "broken-client.json"
        broken.write_text("{}")
        self.assertIn("Installed World Builder client profile is invalid", self.run_harness(
            self.work, "client", properties=["openrsc.worldBuilderInstalledClientProfile=" + str(broken)],
            success=False))

    def test_matching_installed_client_identity_and_rejected_manifest_tamper(self):
        result = self.run_harness(self.installed, "client", properties=[
            "openrsc.worldBuilderInstalledClientProfile=" + str(self.client_profile)])
        self.assertIn("client-map-source=installed-client", result)
        self.assertIn(json.loads(self.client_profile.read_text())["manifestSha256"], result)
        descriptor = json.loads(self.client_profile.read_text())
        descriptor["manifestSha256"] = "0" * 64
        wrong = self.client_profile.with_name("tampered.json")
        wrong.write_text(json.dumps(descriptor))
        self.assertIn("SHA-256 does not match", self.run_harness(self.installed, "client",
            properties=["openrsc.worldBuilderInstalledClientProfile=" + str(wrong)], success=False))

    def test_client_explicit_adaptive_precedence_and_single_snapshot(self):
        # Unit doubles cover competing valid identities without inventing an
        # editor login/credential/session. Actual installed parsing is tested above.
        work = self.work / "client-precedence"
        sources = work / "orsc"
        sources.mkdir(parents=True)
        (sources / "WorldBuilderClientProfile.java").write_text('''package orsc;
public final class WorldBuilderClientProfile {
 public static WorldBuilderClientProfile current() { return new WorldBuilderClientProfile(); }
 public boolean isStrictAdaptiveTerrain() { return true; }
 public String strictAdaptiveMapIdentity() { return System.getProperty("test.identity", "explicit"); }
}''')
        (sources / "WorldBuilderInstalledClientProfile.java").write_text('''package orsc;
public final class WorldBuilderInstalledClientProfile {
 public static WorldBuilderInstalledClientProfile current() { return new WorldBuilderInstalledClientProfile(); }
 public boolean isEnabled() { return true; }
 public String mapIdentity() { return "installed"; }
}''')
        (sources / "BootstrapFixture.java").write_text('''package orsc;
public final class BootstrapFixture {
 public static void main(String[] args) {
  if (!WorldBuilderTerrainBootstrap.isNativeOnly()) throw new AssertionError();
  if (!"explicit".equals(WorldBuilderTerrainBootstrap.mapIdentity())) throw new AssertionError();
  System.setProperty("test.identity", "changed");
  if (!"explicit".equals(WorldBuilderTerrainBootstrap.mapIdentity())) throw new AssertionError("mutable bootstrap");
  System.out.println(WorldBuilderTerrainBootstrap.report());
 }
}''')
        subprocess.run(["javac", "-d", str(work), *map(str, sources.glob("*.java")),
            str(ROOT / "Client_Base/src/orsc/WorldBuilderTerrainBootstrap.java")], check=True)
        result = subprocess.run(["java", "-cp", str(work), "orsc.BootstrapFixture"],
            check=True, capture_output=True, text=True)
        self.assertIn("strict-adaptive (installed profile not selected)", result.stdout)

    def test_actual_config_parser_launch_precedence_and_rollback(self):
        (self.installed / "fixture.conf").write_text(
            "want_layered_native_terrain_package: true\n"
            "layered_native_terrain_package_path: configured/package\n"
            "layered_native_terrain_manifest_sha256: configured-hash\n"
            "layered_native_world_runtime_profile: world-builder-installed\n")
        selected = self.run_harness(self.installed, "init", properties=[
            "openrsc.layeredNativeTerrainPackagePath=system/package"], env={
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH": "environment/package"})
        self.assertIn("package=system/package", selected)
        self.assertIn("profile=spoiled-milk-editor-installed", selected)
        environment = self.run_harness(self.installed, "init", env={
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH": "environment/package"})
        self.assertIn("package=environment/package", environment)
        automatic = self.run_harness(self.installed, "init")
        self.assertIn("map-source=installed-primary", automatic)
        rollback = self.run_harness(self.installed, "init", env={
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE": "false",
            "OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE": "fixture-additive"})
        self.assertIn("representation=classic", rollback)
        self.assertIn("package=inactive", rollback)


if __name__ == "__main__":
    unittest.main()
