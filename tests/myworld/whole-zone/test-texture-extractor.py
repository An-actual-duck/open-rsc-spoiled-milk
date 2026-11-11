#!/usr/bin/env python3
"""Independent malformed OSAR corpus against an explicit source checkout."""
import argparse,gzip,hashlib,importlib.util,json,struct,tempfile,unittest
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,required=True);p.add_argument('--out',type=Path);args,remaining=p.parse_known_args();source=args.source_root/'tools/browser-gameplay/whole-zone/extract-textures.py';spec=importlib.util.spec_from_file_location('extractor',source);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def entry(name=b'0',kind=0,frames=1,w=2,h=2,pixels=None):
 layer=b'\x03'if kind in (1,2,3)else b'';header=struct.pack('>HH',w,h)+bytes(9)
 return name+b'\0'+bytes([kind])+layer+bytes([frames,0])+b'\x12\x34\x56'+(header+(bytes(w*h)if pixels is None else pixels))*frames
def group(name=b'textures',entries=None):
 entries=[entry()]if entries is None else entries
 return name+b'\0'+struct.pack('>H',len(entries))+b''.join(entries)
class Extractor(unittest.TestCase):
 def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.src=self.root/'source.gz';self.dst=self.root/'derived.gz';self.old=(m.MAX_DECODED,m.MAX_FRAME_PIXELS,m.MAX_TEXTURE_PAYLOAD)
 def tearDown(self):m.MAX_DECODED,m.MAX_FRAME_PIXELS,m.MAX_TEXTURE_PAYLOAD=self.old;self.tmp.cleanup()
 def run_data(self,raw):
  self.src.write_bytes(gzip.compress(raw,mtime=0));return m.extract(self.src,self.dst)
 def rejected(self,raw):
  with self.assertRaises((ValueError,EOFError,gzip.BadGzipFile)):self.run_data(raw)
  self.assertFalse(self.dst.exists(),'Validation must fail before output publication')
 def test_exact_selected_encoded_bytes(self):
  e=[entry(),entry(b'1',kind=3)];r=self.run_data(b'\x02'+group(b'ui',[entry(b'x')])+group(entries=e));self.assertEqual(r['entries'],2);self.assertEqual(gzip.decompress(self.dst.read_bytes()),b'\x01'+group(entries=e))
 def test_duplicate_subspace(self):self.rejected(b'\x02'+group()+group())
 def test_duplicate_entry(self):self.rejected(b'\x01'+group(entries=[entry(),entry()]))
 def test_missing_textures(self):self.rejected(b'\x01'+group(b'ui'))
 def test_empty_textures(self):self.rejected(b'\x01'+group(entries=[]))
 def test_nonconsecutive_ids(self):self.rejected(b'\x01'+group(entries=[entry(b'1')]))
 def test_unknown_type(self):self.rejected(b'\x01'+group(entries=[entry(kind=5)]))
 def test_zero_frames(self):self.rejected(b'\x01'+group(entries=[entry(frames=0)]))
 def test_truncated_frame(self):self.rejected((b'\x01'+group())[:-1])
 def test_truncated_palette(self):self.rejected(b'\x01textures\0\0\x010\0\0\x01\x01\x00')
 def test_overlong_name(self):self.rejected(b'\x01'+b'x'*4096)
 def test_decoded_trailing(self):self.rejected(b'\x01'+group()+b'x')
 def test_bad_gzip(self):
  self.src.write_bytes(b'not gzip')
  with self.assertRaises(gzip.BadGzipFile):m.extract(self.src,self.dst)
  self.assertFalse(self.dst.exists())
 def test_pixel_budget(self):m.MAX_FRAME_PIXELS=3;self.rejected(b'\x01'+group())
 def test_decoded_budget(self):m.MAX_DECODED=12;self.rejected(b'\x01'+group())
 def test_aggregate_payload_budget(self):m.MAX_TEXTURE_PAYLOAD=45;self.rejected(b'\x01'+group(entries=[entry(),entry(b'1')]))
 def test_cap_covers_metadata_not_only_pixels(self):m.MAX_TEXTURE_PAYLOAD=20;self.rejected(b'\x01'+group(entries=[entry(w=0,h=0)]))
 def test_existing_derivative_preserved(self):
  self.dst.write_bytes(b'owner');self.src.write_bytes(gzip.compress(b'\x01'+group(),mtime=0))
  with self.assertRaises(ValueError):m.extract(self.src,self.dst)
  self.assertEqual(self.dst.read_bytes(),b'owner')
suite=unittest.defaultTestLoader.loadTestsFromTestCase(Extractor);result=unittest.TextTestRunner().run(suite)
receipt={'passed':result.wasSuccessful(),'checks':result.testsRun,'source':str(source.resolve()),'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'scope':'Synthetic malformed/byte-preservation CPU proof only; production pixel decoder proof is separate. Scaled budget tests exercise exact branches without allocating attack-sized buffers.'}
if args.out:
 if args.out.exists():raise ValueError('Preserve prior result')
 args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(receipt));raise SystemExit(not result.wasSuccessful())
