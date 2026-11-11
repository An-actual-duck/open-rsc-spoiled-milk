#!/usr/bin/env python3
"""Source-bound offline literal Java oracle; never builds/launches game or browser."""
import argparse,bz2,hashlib,json,struct,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args();root=Path(__file__).resolve().parents[3];out=a.output.resolve()
if out.exists():raise SystemExit('Refuse existing output')
out.mkdir(parents=True);(out/'classes').mkdir()
graphics=root/'Client_Base/src/orsc/graphics/two/GraphicsController.java';text=graphics.read_text()
def method(signature):
 start=text.index(signature);body=text.index('{',start);depth=1;i=body+1
 # Selected source methods contain no string/comment braces; verify via javac.
 while depth:
  depth+=(text[i]=='{')-(text[i]=='}');i+=1
 return text[start:i]
methods=[method(x)for x in ['private void plotCharacter(', 'private void plotLetter(', 'private void plotLetterAntialiased(', 'public final void drawBoxAlpha(']]
archive=root/'Client_Base/Cache/video/library.orsc';raw=archive.read_bytes();u=int.from_bytes(raw[:3],'big');n=int.from_bytes(raw[3:6],'big');data=bz2.decompress(b'BZh1'+raw[6:6+n])if u!=n else raw[6:6+n];assert len(data)==u
count=int.from_bytes(data[:2],'big');offset=2+count*10;fonts=[]
for i in range(count):
 rec=data[2+i*10:12+i*10];key=int.from_bytes(rec[:4],'big');size=int.from_bytes(rec[4:7],'big');packed=int.from_bytes(rec[7:10],'big')
 for name in ['h11p.jf','h12b.jf','h12p.jf','h13b.jf','h14b.jf']:
  h=0
  for c in name.upper():h=(h*61+ord(c)-32)&0xffffffff
  if h==key:
   font=data[offset:offset+packed];font=bz2.decompress(b'BZh1'+font)if packed!=size else font;assert len(font)==size;fonts.append((name,font))
 offset+=packed
assert len(fonts)==5
# Include production font bytes plus threshold-specific AA vectors using the same glyph ABI.
font_sources=[dict(name=name,data=list(font),sha256=hashlib.sha256(font).hexdigest())for name,font in fonts]
synthetic=bytes([0,30,31,127,229,230,255,1,128,254,0,255]);font_sources.append(dict(name='synthetic-aa-thresholds',data=list(synthetic),sha256=hashlib.sha256(synthetic).hexdigest()))
w,h=67,43;cases=[]
def signed(b):return b if b<128 else b-256
def glyph(source,addr,x,y,color,aa):
 f=font_sources[source]['data'];width=signed(f[addr+3]);height=signed(f[addr+4]);left=x+signed(f[addr+5]);top=y-signed(f[addr+6]);sh=(signed(f[addr])<<14)+(signed(f[addr+1])<<7)+signed(f[addr+2]);head=left+top*w;ds=w-width;ss=0
 if top<0:lost=-top;sh+=lost*width;head+=w*lost;height-=lost;top=0
 if top+height>=h:height-=1+top+height-h
 if left<0:lost=-left;ss+=lost;width-=lost;sh+=lost;left=0;ds+=lost;head+=lost
 if w<=width+left:lost=width+left-w+1;ds+=lost;ss+=lost;width-=lost
 if width<=0 or height<=0:return None
 return dict(kind='glyph',head=head,width=width,height=height,rowStride=ds,color=color,source=source,sourceHead=sh,sourceStride=ss,aa=aa,raw=[source,addr,x,y,color,aa])
chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!"£$%^&*()-_=+[{]};:\'@#~,<.>/?\\| '
colors=[0,-1,0x123456,0xabcdef,0x7fffffff,-2147483648]
for f in range(5):
 for aa in [False,True]:
  for pos in [(4,18),(-3,5),(60,42),(0,0)]:
   ops=[]
   for j,ch in enumerate('AgW09!?'):
    q=glyph(f,chars.index(ch)*9,pos[0]+j*4,pos[1]+(j%2),colors[j%len(colors)],aa)
    if q:ops.append(q)
   cases.append(ops)
# Mixed order and all original alpha bounds, including no-op and full-opacity math.
for alpha in [0,1,30,31,127,128,229,230,255,256]:
 ops=[dict(kind='alpha',head=0,width=w,height=h,rowStride=0,color=0xabcdef,alpha=alpha)]
 q=glyph(0,chars.index('A')*9,8,20,-1,False)
 if q:ops.append(q)
 ops.append(dict(kind='alpha',head=3+4*w,width=29,height=19,rowStride=w-29,color=0x01f7c4,alpha=alpha))
 q=glyph(1,chars.index('g')*9,12,21,0x123456,True)
 if q:ops.append(q)
 cases.append(ops)
# Every byte threshold participates in exact AA paths with overlapping opacity spans.
for alpha in range(257):
 cases.append([dict(kind='glyph',head=2+w,width=6,height=2,rowStride=w-6,color=colors[alpha%6],source=5,sourceHead=0,sourceStride=0,aa=bool(alpha&1)),dict(kind='alpha',head=2+w,width=6,height=2,rowStride=w-6,color=colors[(alpha+1)%6],alpha=alpha)])
packets=[]
for i,ops in enumerate(cases):
 pattern=[dict(kind='alpha',head=row*w,width=w,height=1,rowStride=0,color=((row*1234567)^0x75a39c)&0xffffff,alpha=(row*17)%257)for row in range(h)]
 q=[dict(kind='clear')]+pattern+ops
 for j,op in enumerate(q):op['order']=j
 packets.append(dict(schema=1,width=w,height=h,generation=i,sequence=i,ops=q))
corpus=dict(width=w,height=h,sources=font_sources,packets=packets);(out/'corpus.json').write_text(json.dumps(corpus,separators=(',',':')))
# Binary raw calls feed extracted Java literal clipping/raster code independently.
with (out/'input.bin').open('wb')as stream:
 def ints(*values):stream.write(struct.pack('>'+'i'*len(values),*values))
 ints(w,h,len(font_sources))
 for source in font_sources:ints(len(source['data']));stream.write(bytes(source['data']))
 ints(len(packets))
 for packet in packets:
  ints(len(packet['ops'])-1)
  for op in packet['ops'][1:]:
   if op['kind']=='alpha':ints(2,op['head']%w,op['head']//w,op['width'],op['height'],op['color'],op['alpha'])
   elif 'raw'in op:
    source,addr,x,y,color,aa=op['raw'];ints(0,source,addr,x,y,color,int(aa))
   else:ints(1,op['source'],op['sourceHead'],op['head'],op['width'],op['height'],op['rowStride'],op['sourceStride'],op['color'],int(op['aa']))
shim='''package orsc.graphics.two;
import java.io.*; public class LiteralOverlayReference {
int width2,height2,clipLeft=0,clipTop=0,clipRight,clipBottom;boolean interlace=false;int[] pixelData;WorldOverlayCoverage coverage=new WorldOverlayCoverage();
void coverageOpaque(int[]d,int i){coverage.opaque(d,i);}void coverageMultiply(int[]d,int i,int n){coverage.multiply(d,i,n);}
boolean shouldReplaceRenderer2DPrimitive(boolean x){return false;}boolean recordRenderer2DPrimitive(int a,int b,int c,int d,int e,int f){return false;}
static class GenUtil{static RuntimeException makeThrowable(RuntimeException e,String ignored){return e;}}
static class FastMath{static int bitwiseAnd(int a,int b){return a&b;}}
'''+ '\n'.join(methods)+'''
public static void main(String[]args)throws Exception{try(DataInputStream in=new DataInputStream(new FileInputStream(args[0]));DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]))){LiteralOverlayReference r=new LiteralOverlayReference();r.width2=in.readInt();r.height2=in.readInt();r.clipRight=r.width2;r.clipBottom=r.height2;r.pixelData=new int[r.width2*r.height2];byte[][]sources=new byte[in.readInt()][];for(int i=0;i<sources.length;i++){sources[i]=new byte[in.readInt()];in.readFully(sources[i]);}int frames=in.readInt();out.writeInt(frames);out.writeInt(r.pixelData.length);for(int frame=0;frame<frames;frame++){r.coverage.beginFrame();r.coverage.begin(r.pixelData,r.width2,r.height2);int ops=in.readInt();for(int j=0;j<ops;j++){int kind=in.readInt();if(kind==0){int s=in.readInt(),addr=in.readInt(),x=in.readInt(),y=in.readInt(),color=in.readInt();boolean aa=in.readInt()!=0;r.plotCharacter(aa,sources[s],x,color,addr,y);}else if(kind==1){int s=in.readInt(),sourceHead=in.readInt(),head=in.readInt(),w=in.readInt(),h=in.readInt(),stride=in.readInt(),sourceStride=in.readInt(),color=in.readInt();boolean aa=in.readInt()!=0;if(aa)r.plotLetterAntialiased(sources[s],color,w,head,h,sourceStride,stride,r.pixelData,sourceHead);else r.plotLetter(color,r.pixelData,head,stride,h,w,sourceHead,sources[s],sourceStride);}else if(kind==2){r.drawBoxAlpha(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}else throw new IOException("op");}for(int c:r.pixelData)out.writeInt(c);WorldOverlayCoverage.Frame f=r.coverage.borrow(r.pixelData,r.width2,r.height2);for(float t:f.transmittance)out.writeInt(Float.floatToRawIntBits(t));}if(in.available()!=0)throw new IOException("trailing corpus");}}
}'''
(out/'LiteralOverlayReference.java').write_text(shim)
for name in ['WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java']:(out/name).write_bytes((root/'Client_Base/src/orsc/graphics/two'/name).read_bytes())
receipt=dict(sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),graphicsSHA256=hashlib.sha256(graphics.read_bytes()).hexdigest(),archiveSHA256=hashlib.sha256(raw).hexdigest(),methodHashes=[hashlib.sha256(s.encode()).hexdigest()for s in methods],fontNames=[f[0]for f in fonts],packets=len(packets),sourceCopy=True,scope='Extracted literal Java methods with stub capture/error adapters; no game build/browser/GPU; corpus is not real frame capture')
(out/'provenance.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(out)
