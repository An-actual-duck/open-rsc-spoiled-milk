#!/usr/bin/env python3
"""Independently prove approved installation adds records without rewriting existing archives."""
import subprocess,zipfile,io,struct,hashlib,sys,gzip,importlib.util
from pathlib import Path
root=Path(__file__).resolve().parents[1];baseline=sys.argv[1] if len(sys.argv)>1 else '2c1f38cfb57136e10298685ef9acaaaa1b90abdd';zpath='Client_Base/Cache/video/Authentic_Sprites.orsc';before=subprocess.check_output(['git','show',baseline+':'+zpath],cwd=root);after=(root/zpath).read_bytes();a=zipfile.ZipFile(io.BytesIO(before));b=zipfile.ZipFile(io.BytesIO(after));count=0
for i in a.infolist():
 j=b.getinfo(i.filename);assert a.read(i)==b.read(j);assert all(getattr(i,k)==getattr(j,k) for k in i.__slots__),i.filename
 # Local headers, optional descriptors and compressed bytes remain at same offset.
 off=i.header_offset;head=struct.unpack_from('<IHHHHHIIIHH',before,off);end=off+30+head[-2]+head[-1]+i.compress_size
 if i.flag_bits&8:end+=16 if before[end:end+4]==b'PK\x07\x08' else 12
 assert before[off:end]==after[off:end],i.filename;count+=1
assert set(b.namelist())-set(a.namelist())==set(map(str,range(4100,4118)))
s=importlib.util.spec_from_file_location('gen',root/'scripts/install-cleric-well-assets.py');g=importlib.util.module_from_spec(s);s.loader.exec_module(g)
x='Client_Base/Cache/video/Custom_Sprites.osar';old=g.g.archive(gzip.decompress(subprocess.check_output(['git','show',baseline+':'+x],cwd=root)));new=g.g.archive(gzip.decompress((root/x).read_bytes()));old={(n,k):v for n,es in old for k,v in es};new={(n,k):v for n,es in new for k,v in es};assert all(v==new[k] for k,v in old.items());assert set(new)-set(old)=={('textures',str(i)) for i in range(67,73)}
print('PASS',count,'original ZIP records preserve payload/ZipInfo/localheader/compressedbytes;',len(old),'original OSAR serialized entries exact; additions18+6 only')
# Old XML row prefixes remain structurally identical, including all optional metadata.
import xml.etree.ElementTree as ET
for name,added in [('TileDef.xml',2),('DoorDef.xml',8)]:
 path='server/conf/server/defs/'+name
 old=list(ET.fromstring(subprocess.check_output(['git','show',baseline+':'+path],cwd=root)))
 new=list(ET.parse(root/path).getroot())
 def row(e):return (e.tag,tuple(sorted(e.attrib.items())),(e.text or '').strip(),tuple(row(c) for c in e))
 assert len(new)==len(old)+added and [row(e) for e in new[:len(old)]]==[row(e) for e in old],name
 print('PASS',name,'preserved original',len(old),'definition rows and metadata; appended',added)
