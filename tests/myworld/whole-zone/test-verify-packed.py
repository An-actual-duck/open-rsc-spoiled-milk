#!/usr/bin/env python3
import copy,importlib.util,struct,unittest
from pathlib import Path
s=importlib.util.spec_from_file_location('packed',Path(__file__).with_name('verify-packed.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class Packed(unittest.TestCase):
 def setUp(self):
  self.c={'vertexCount':3,'indexCount':3,'batchIntCount':5,'originX':128,'originZ':-256,'bbox':[128,0,-256,129,0,-255]};self.v=[0.0]*84
  self.v[28]=1.;self.v[58]=1.;self.i=[0,1,2];self.b=[5,0,3,0,0];self.save()
 def save(self):
  self.raw=struct.pack('<4I',0x575a4331,len(self.v)//28,len(self.i),len(self.b))+struct.pack('<'+'f'*len(self.v),*self.v)+struct.pack('<'+'i'*len(self.i),*self.i)+struct.pack('<'+'i'*len(self.b),*self.b);self.c.update(bytes=len(self.raw),sha256=m.package.sha(self.raw),vertexCount=len(self.v)//28,indexCount=len(self.i),batchIntCount=len(self.b))
 def check(self):return m.check_chunk(self.raw,self.c,{5})
 def rejected(self):
  with self.assertRaises(ValueError):self.check()
 def test_exact_binary(self):self.assertEqual(self.check()['drawnIndices'],3)
 def test_sha(self):self.c['sha256']='0'*64;self.rejected()
 def test_header_with_matching_sha(self):
  self.raw=b'abcd'+self.raw[4:];self.c['sha256']=m.package.sha(self.raw);self.rejected()
 def test_count_with_matching_sha(self):
  self.c['vertexCount']=2;self.rejected()
 def test_nonfinite_position(self):self.v[0]=float('nan');self.save();self.rejected()
 def test_nonfinite_nonposition_field(self):self.v[27]=float('inf');self.save();self.rejected()
 def test_negative_index(self):self.i[0]=-1;self.save();self.rejected()
 def test_late_index(self):self.i[-1]=3;self.save();self.rejected()
 def test_material_absent(self):self.b[0]=6;self.save();self.rejected()
 def test_material_range(self):self.b[2]=6;self.save();self.rejected()
 def test_material_alignment(self):self.b[1]=1;self.b[2]=2;self.save();self.rejected()
 def test_material_alpha(self):self.b[4]=2;self.save();self.rejected()
 def test_material_overlap(self):self.b+=self.b;self.save();self.rejected()
 def test_bounds(self):self.c['bbox'][3]+=1;self.rejected()
 def test_undrawn_zero_tail_preserved(self):self.i += [0,0,0];self.save();self.assertEqual(self.check()['drawnIndices'],3)
 def test_explicit_empty_resource(self):
  self.v=[];self.i=[];self.b=[];self.c['bbox']=[0.,0.,0.,0.,0.,0.];self.save();self.assertEqual(self.check()['bytes'],16)
if __name__=='__main__':unittest.main()
