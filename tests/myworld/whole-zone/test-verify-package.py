#!/usr/bin/env python3
import copy,hashlib,importlib.util,json,tempfile,unittest
from pathlib import Path
p=Path(__file__).with_name('verify-package.py');s=importlib.util.spec_from_file_location('verify',p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class Package(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.t='terrain/global/l0/x0-y-1.raw';self.p='placements/global/l0.json';(self.root/self.t).parent.mkdir(parents=True);(self.root/self.t).write_bytes(bytes(48*48*10));(self.root/self.p).parent.mkdir(parents=True);(self.root/self.p).write_text(json.dumps({'worldSpace':'global','level':0,'scenery':[{'placementId':'one','position':{'x':1,'y':-1}}]}));self.manifest={'terrainSectors':[{'path':self.t,'sha256':m.sha((self.root/self.t).read_bytes()),'worldSpace':'global','level':0,'sectorX':0,'sectorY':-1,'encoding':'raw-layered-sector-v1'}],'placementSets':[{'path':self.p,'sha256':m.sha((self.root/self.p).read_bytes()),'worldSpace':'global','level':0}]};self.save()
 def tearDown(self):self.tmp.cleanup()
 def save(self):
  (self.root/'manifest.json').write_text(json.dumps(self.manifest));self.manifestSHA=m.sha((self.root/'manifest.json').read_bytes());self.fingerprint=self.tree()
 def tree(self):
  h=hashlib.sha256()
  for p in sorted(p for p in self.root.rglob('*')if p.is_file()):
   for f in [p.relative_to(self.root).as_posix(),p.stat().st_size,m.sha(p.read_bytes())]:h.update(str(f).encode()+b'\0')
  return h.hexdigest()
 def check(self):return m.verify(self.root,self.fingerprint,self.manifestSHA)
 def test_all_payloads_signed_ownership(self):
  a=self.check();self.assertTrue(a['passed']);self.assertEqual(a['files'],3);self.assertEqual(a['sectorCounts'],{'global:0':1});self.assertEqual(a['sectorRoster'][0]['sectorY'],-1);self.assertEqual(a['manifestPlacements'][0]['y'],-1)
 def test_corrupt_payload(self):
  (self.root/self.t).write_bytes(b'x'+bytes(48*48*10-1))
  with self.assertRaises(ValueError):self.check()
 def test_missing_payload(self):
  (self.root/self.t).unlink()
  with self.assertRaises(ValueError):self.check()
 def test_undeclared_file(self):
  (self.root/'extra').write_bytes(b'never ignored')
  with self.assertRaises(ValueError):self.check()
 def test_symlink_is_not_owned_payload(self):
  (self.root/self.t).unlink();(self.root/self.t).symlink_to(self.root/self.p)
  with self.assertRaises(ValueError):self.check()
 def test_duplicate_sector_ownership(self):
  row=copy.deepcopy(self.manifest['terrainSectors'][0]);row['path']='other.raw';(self.root/'other.raw').write_bytes((self.root/self.t).read_bytes());self.manifest['terrainSectors'].append(row);self.save()
  with self.assertRaises(ValueError):self.check()
 def test_duplicate_declared_path(self):
  self.manifest['terrainSectors'].append(self.manifest['terrainSectors'][0]);self.save()
  with self.assertRaises(ValueError):self.check()
 def test_traversal(self):
  self.manifest['terrainSectors'][0]['path']='../escape.raw';self.save()
  with self.assertRaises(ValueError):self.check()
 def test_bad_sector_length_even_with_matching_digest(self):
  (self.root/self.t).write_bytes(b'x');self.manifest['terrainSectors'][0]['sha256']=m.sha(b'x');self.save()
  with self.assertRaises(ValueError):self.check()
 def test_placement_level(self):
  (self.root/self.p).write_text(json.dumps({'worldSpace':'global','level':1}));self.manifest['placementSets'][0]['sha256']=m.sha((self.root/self.p).read_bytes());self.save()
  with self.assertRaises(ValueError):self.check()
 def test_duplicate_json_key(self):
  with self.assertRaises(ValueError):m.load('{"a":1,"a":2}')
 def test_nonfinite_json(self):
  with self.assertRaises(ValueError):m.load('{"a":NaN}')
 def test_wrong_tree_fingerprint(self):
  with self.assertRaises(ValueError):m.verify(self.root,'0'*64,self.manifestSHA)
if __name__=='__main__':unittest.main()
