#!/usr/bin/env python3
"""Failure-first effective material validation over real maintained startup APIs.

MAP_MATERIAL_CORE_JAR may select a frozen pre-change server to reproduce partial
population/NPE or silent acceptance. No listener, game account or live config.
"""
import os
import runpy
import subprocess
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SUPPORT=runpy.run_path(str(ROOT/"tests/myworld/test-map-runtime-validation.py"))
HARNESS=r'''
import com.openrsc.server.Server;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.*;
public final class MaterialPreflightFixture {
 public static void main(String[] args)throws Exception {
  System.setProperty("openrsc.worldBuilderTargetRoot",args[0]+"/absent-primary");
  System.setProperty("openrsc.layeredPlayerLocationAuthority","true");
  System.setProperty("openrsc.layeredSpatialRuntimeAuthority","true");
  System.setProperty("openrsc.layeredProtocolClientAuthority","true");
  System.setProperty("openrsc.layeredNativeTerrainPackage","true");
  System.setProperty("openrsc.layeredNativeTerrainPackagePath",args[0]);
  System.setProperty("openrsc.layeredNativeWorldRuntimeProfile","native-additive");
  Server server=new Server("fixture.conf");server.getEntityHandler().load();World world=server.getWorld();
  int before=world.getNpcs().size();
  try {
   world.getRegionManager().populateNativeLayeredPlacements();
   world.getTile(WorldLocation.global(new WorldCoordinate(488,600,-2)));
  }catch(RuntimeException failure){
   System.err.println("MATERIAL_REFUSED "+failure.getClass().getName()+":"+failure.getMessage()+" before="+before+" after="+world.getNpcs().size());
   if(!args[1].equals("invalid"))throw failure;
   if(!(failure instanceof IllegalStateException)||!failure.getMessage().contains("material")||world.getNpcs().size()!=before)
    throw new AssertionError("invalid terrain material was not safely refused before population",failure);
   return;
  }
  if(args[1].equals("invalid"))throw new AssertionError("invalid terrain material silently accepted after population="+world.getNpcs().size());
  System.err.println("MATERIAL_VALID npcs="+world.getNpcs().size());
 }
}
'''


class MapMaterialPreflightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.support_class=SUPPORT["MapRuntimeValidationTest"];cls.support_class.setUpClass()
        cls.work=cls.support_class.work;cls.support=cls.support_class("test_installed_runtime_accepts_non_product_package")
        cls.core=Path(os.environ.get("MAP_MATERIAL_CORE_JAR",ROOT/"server/core.jar"))
        cls.cp=os.pathsep.join(map(str,[cls.work,cls.core]))
        source=cls.work/"MaterialPreflightFixture.java";source.write_text(HARNESS)
        subprocess.run(["javac","-cp",cls.cp,"-d",str(cls.work),str(source)],check=True)

    @classmethod
    def tearDownClass(cls):cls.support_class.tearDownClass()

    def invoke(self,name,fields,invalid=True):
        package=self.support.package(name)
        self.support.change_payload(package,"terrain/deep-l2-x10-y12.json",lambda d:d["tile"].update(fields))
        env={k:v for k,v in os.environ.items() if not k.startswith("OPENRSC_LAYERED_")}
        result=subprocess.run(["java","-cp",self.cp,"MaterialPreflightFixture",str(package),"invalid" if invalid else "valid"],
                              cwd=self.work,env=env,text=True,capture_output=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        return result.stdout+result.stderr

    def test_missing_overlay_refuses_before_any_placement_registration(self):
        self.invoke("missing-overlay",dict(overlay=254))

    def test_missing_wall_refuses_instead_of_silent_collision_loss(self):
        self.invoke("missing-wall",dict(verticalWall=255))

    def test_missing_roof_effective_inventory_refuses_before_registration(self):
        self.invoke("missing-roof",dict(roof=50))

    def test_embedded_scenery_selector_requires_its_effective_definition(self):
        self.invoke("missing-embedded-scenery",dict(diagonalWall=59999))

    def test_existing_controls_and_non_selector_raw_diagonal_bits_are_not_ids(self):
        for raw in [0,12000,24000,48000,60000,0xffffffff,0x07112233]:
            self.invoke("control-diagonal-"+str(raw),dict(diagonalWall=raw,overlay=255),invalid=False)
        self.invoke("alias-overlay",dict(overlay=250),invalid=False)


if __name__=="__main__":unittest.main()
