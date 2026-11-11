#!/usr/bin/env python3
import copy,importlib.util,json,tempfile,unittest
from pathlib import Path
s=importlib.util.spec_from_file_location('catalogue',Path(__file__).with_name('verify-catalogue.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class Catalogue(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.kinds={'GAME_OBJECT':4,'WALL_OBJECT':5}
  payload={'scenery':[{'placementId':'tree','position':{'x':1,'y':-1},'sceneryId':34,'direction':1}],'boundaries':[{'placementId':'wall','position':{'x':2,'y':-2},'boundaryId':196,'direction':3}]}
  raw=json.dumps(payload).encode();(self.root/'placements.json').write_bytes(raw);(self.root/'manifest.json').write_text(json.dumps({'placementSets':[{'worldSpace':'global','level':0,'path':'placements.json','sha256':m.package.sha(raw)}]}))
  self.proof={'packageFingerprint':'p','manifestSHA256':'m','sectorRoster':[{'worldSpace':'global','level':0,'sectorX':0,'sectorY':-1,'sha256':'terrain'}]}
  world={'plane':0,'cellX':0,'cellY':-1,'sourceSha256':'terrain','resourceId':1000000,'vertexCount':3,'indexCount':3}
  cells=[{'plane':0,'cellX':x,'cellY':y,'resourceId':None,'vertexCount':0,'indexCount':0,'identities':[],'placementCount':0,'canonicalContentKey':'empty'}for x in (0,1)for y in (-2,-1)]
  owned=next(c for c in cells if(c['cellX'],c['cellY'])==(0,-1));owned.update(resourceId=1000001,vertexCount=6,indexCount=6,identities=[4,1,-1,34,1,10,10,5,2,-2,196,3,10,20],placementCount=2,canonicalContentKey='key')
  def resource(cell,role):return {**cell,'id':cell['resourceId'],'role':role,'sourceKind':'world'if role==0 else'objects','cellSize':48 if role==0 else 24,'batchIntCount':5,'bytes':16+cell['vertexCount']*112+cell['indexCount']*4+20}
  self.c={'complete':True,'packageFingerprintSha256':'p','manifestSha256':'m','worldCells':[world],'objectCells':cells,'chunks':[resource(world,0),resource(owned,1)]}
 def tearDown(self):self.tmp.cleanup()
 def check(self):return m.join(self.c,self.proof,self.root,self.kinds)
 def rejected(self):
  with self.assertRaises(ValueError):self.check()
 def owned(self):return next(c for c in self.c['objectCells']if c['placementCount'])
 def test_real_source_multiset_signed_cells(self):
  r=self.check();self.assertEqual((r['worldCells'],r['objectCells'],r['authoredPlacements'],r['resources']),(1,4,2,2))
 def test_reordered_identities_are_same_membership(self):
  w=self.owned()['identities'];self.owned()['identities']=w[7:]+w[:7];self.check()
 def test_elevations_not_claimed_by_roster(self):
  self.owned()['identities'][5]=999;self.check()
 def test_missing_cell_even_if_counts_replaced(self):
  self.c['objectCells'][0]['cellX']=7;self.rejected()
 def test_duplicate_cell(self):
  self.c['objectCells'][0]=copy.deepcopy(self.c['objectCells'][1]);self.rejected()
 def test_wrong_terrain_hash(self):
  self.c['worldCells'][0]['sourceSha256']='other';self.rejected()
 def test_duplicate_terrain_owner(self):
  self.c['worldCells'].append(copy.deepcopy(self.c['worldCells'][0]));self.rejected()
 def test_changed_definition(self):
  self.owned()['identities'][3]+=1;self.rejected()
 def test_changed_direction(self):
  self.owned()['identities'][4]+=1;self.rejected()
 def test_changed_kind(self):
  self.owned()['identities'][0]=5;self.rejected()
 def test_wrong_cell_identity_position(self):
  self.owned()['identities'][1]=25;self.rejected()
 def test_duplicate_replacing_second_placement(self):
  self.owned()['identities'][7:]=self.owned()['identities'][:7];self.rejected()
 def test_noninteger_identity(self):
  self.owned()['identities'][6]=True;self.rejected()
 def test_removed_mandatory_resource(self):
  self.owned().update(resourceId=None,vertexCount=0,indexCount=0);self.rejected()
 def test_missing_resource(self):
  self.c['chunks'].pop();self.rejected()
 def test_unowned_extra_resource(self):
  c=copy.deepcopy(self.c['chunks'][0]);c['id']+=20;self.c['chunks'].append(c);self.rejected()
 def test_duplicate_resource(self):
  self.c['chunks'].append(copy.deepcopy(self.c['chunks'][0]));self.rejected()
 def test_wrong_geometry_owner(self):
  self.c['chunks'][1]['cellX']+=1;self.rejected()
 def test_wrong_resource_content_key(self):
  self.c['chunks'][1]['canonicalContentKey']='other';self.rejected()
 def test_wrong_resource_placement_count(self):
  self.c['chunks'][1]['placementCount']-=1;self.rejected()
 def test_wrong_resource_bytes(self):
  self.c['chunks'][1]['bytes']+=4;self.rejected()
 def test_changed_source_after_source_proof(self):
  (self.root/'placements.json').write_text('{}');self.rejected()
 def test_partial_diagnostic_is_explicit(self):
  self.c['complete']=False
  with self.assertRaises(ValueError):self.check()
  r=m.join(self.c,self.proof,self.root,self.kinds,{(0,0,-1)});self.assertTrue(r['partialDiagnostic'])
 def test_partial_missing_explicit_source(self):
  self.c['complete']=False
  with self.assertRaises(ValueError):m.join(self.c,self.proof,self.root,self.kinds,{(0,0,-1),(1,0,-1)})
if __name__=='__main__':unittest.main()
