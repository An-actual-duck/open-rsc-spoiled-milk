#!/usr/bin/env python3
"""Copy the encoded textures subspace into a bounded derived OSAR, byte-for-byte."""
import argparse,gzip,hashlib,json,struct
from pathlib import Path
MAX_DECODED=512*1024*1024
MAX_FRAME_PIXELS=16*1024*1024
MAX_TEXTURE_PAYLOAD=16*1024*1024

def digest(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as stream:
  for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
 return h.hexdigest()

class Reader:
 def __init__(self,stream):self.stream=stream;self.count=0
 def take(self,n):
  if not isinstance(n,int)or n<0 or self.count+n>MAX_DECODED:raise ValueError('OSAR decoded byte cap')
  b=self.stream.read(n)
  if len(b)!=n:raise ValueError('Truncated OSAR')
  self.count+=n;return b
 def string(self):
  b=bytearray()
  for _ in range(4096):
   c=self.take(1);b.extend(c)
   if c==b'\0':return bytes(b)
  raise ValueError('OSAR name cap')
 def skip(self,n):
  while n:
   size=min(n,65536);self.take(size);n-=size

def extract(source,destination):
 source,destination=Path(source),Path(destination)
 if destination.exists():raise ValueError('Preserve existing texture derivative; choose a fresh path')
 encoded=[];entry_hashes={};subspace_found=False;selected_bytes=0
 with gzip.open(source,'rb')as stream:
  reader=Reader(stream);groups=reader.take(1)[0]
  for _ in range(groups):
   name=reader.string();count=struct.unpack('>H',reader.take(2))[0];selected=name==b'textures\0'
   if selected:
    if subspace_found:raise ValueError('Duplicate textures subspace')
    subspace_found=True
   for _ in range(count):
    label=reader.string();kind=reader.take(1);entry=bytearray(label+kind)if selected else None
    if kind[0]>4:raise ValueError('Unknown OSAR Entry.TYPE')
    layer=reader.take(1)if kind[0]in(1,2,3)else b'';frames=reader.take(1);palette_size=reader.take(1);palette=reader.take((palette_size[0]+1)*3)
    if selected:entry.extend(layer+frames+palette_size+palette)
    for _ in range(frames[0]):
     header=reader.take(13);w,h=struct.unpack('>HH',header[:4]);pixels=w*h
     if pixels>MAX_FRAME_PIXELS:raise ValueError('OSAR frame pixel cap')
     if selected:
      if selected_bytes+len(entry)+13+pixels>MAX_TEXTURE_PAYLOAD:raise ValueError('Texture derivative byte cap')
      entry.extend(header);entry.extend(reader.take(pixels))
     else:reader.skip(pixels)
    if selected:
     key=label[:-1].decode('latin1')
     if key in entry_hashes:raise ValueError('Duplicate texture entry')
     entry_hashes[key]=hashlib.sha256(entry).hexdigest();encoded.append(bytes(entry));selected_bytes+=len(entry)
  if stream.read(1):raise ValueError('OSAR trailing decoded bytes')
 if not subspace_found or not encoded:raise ValueError('Missing texture subspace')
 if set(entry_hashes)!={str(n)for n in range(len(encoded))}:raise ValueError('Production textures require consecutive numeric IDs')
 destination.parent.mkdir(parents=True,exist_ok=True)
 with destination.open('xb')as output,gzip.GzipFile(fileobj=output,mode='wb',mtime=0,filename='')as compressed:
  compressed.write(b'\x01textures\0'+struct.pack('>H',len(encoded)))
  for entry in encoded:compressed.write(entry)
 return {'source':str(source.resolve()),'sourceSha256':digest(source),'sourceDecodedBytes':reader.count,'destination':str(destination.resolve()),'destinationSha256':digest(destination),'destinationBytes':destination.stat().st_size,'encodedTexturesBytes':selected_bytes,'entries':len(encoded),'entryEncodedSha256':entry_hashes,'scope':'Exact original encoded entry bytes; only non-texture subspaces omitted. No palette/pixel/frame alteration.'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--destination',type=Path,required=True);a=p.parse_args();r=extract(a.source,a.destination);a.destination.with_suffix('.receipt.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps({k:v for k,v in r.items()if k!='entryEncodedSha256'}))
if __name__=='__main__':main()
