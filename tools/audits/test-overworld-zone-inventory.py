#!/usr/bin/env python3
"""Independent known-layout checks for raw inventory normalization and ownership."""
import importlib.util,struct,sys,unittest
from pathlib import Path
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('audit',Path(__file__).with_name('overworld-zone-inventory.py'));audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
class InventoryTest(unittest.TestCase):
 def test_v1_v2_same_tiles(self):
  values=[(n%256,(n*7)%256,(n*11)%256,n%32,n%17,n%13,(n*2654435761)&0xffffffff)for n in range(2304)]
  old=b''.join(struct.pack('>6BI',*v)for v in values);new=b''.join(struct.pack('>H5BI',*v)for v in values)
  self.assertEqual(audit.canonical(old,'raw-layered-sector-v1'),new)
  self.assertEqual(list(struct.iter_unpack('>H5BI',audit.canonical(old,'raw-layered-sector-v1'))),values)
 def test_u16_extremes(self):
  values=[(65535,255,250,255,255,255,0xffffffff)]*2304;raw=b''.join(struct.pack('>H5BI',*v)for v in values)
  self.assertEqual(audit.canonical(raw,'raw-layered-sector-v2-u16'),raw)
  self.assertEqual(next(struct.iter_unpack('>H5BI',raw)),values[0])
 def test_refuse_wrong_layout(self):
  for encoding,size in [('raw-layered-sector-v1',23039),('raw-layered-sector-v2-u16',25343),('unknown',23040)]:
   with self.assertRaises(AssertionError):audit.canonical(bytes(size),encoding)
 def test_signed_ownership(self):
  for x in [-1057,-49,-48,-47,-1,0,1,47,48,1055]:
   for y in [-49,-1,0,48,1007]:
    sectorx,sectory=x//48,y//48;localx,localy=x%48,y%48;n=localx*48+localy
    self.assertEqual((sectorx*48+n//48,sectory*48+n%48),(x,y));self.assertTrue(0<=n<2304)
if __name__=='__main__':unittest.main()
