#!/usr/bin/env python3
"""Exact approved Cleric art, embedded bytes, native sprites/fonts and optional wire mapping.

Build the client first. Run Java work under the maintainer heavy-build lock.
Use --wire for the real schema-5 twenty-spell catalog after gameplay integration.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'dev/myworld/assets/runtime/ui/cleric'
PREFIX = 'myworld-assets/ui/cleric/'

HARNESS = r'''
package orsc;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import com.openrsc.client.model.Sprite;
import com.openrsc.data.DataFileDecrypter;
import com.openrsc.data.DataOperations;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import orsc.graphics.two.*;
public final class ClericIconArtFixture {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static int u24(byte[] b,int i){return (b[i]&255)<<16|(b[i+1]&255)<<8|(b[i+2]&255);}
 static void fonts(Path root)throws Exception{
  byte[] f=Files.readAllBytes(root.resolve("Client_Base/Cache/video/library.orsc"));
  int n=u24(f,0),compressed=u24(f,3);byte[] library=new byte[n];
  if(n==compressed)System.arraycopy(f,6,library,0,n);else DataFileDecrypter.unpackData(library,n,f,compressed,6);
  for(String name:new String[]{"h11p.jf","h12b.jf","h12p.jf","h13b.jf","h14b.jf","h16b.jf","h20b.jf","h24b.jf"})
   Fonts.addFont(DataOperations.loadData(name,0,library));
 }
 static void set(Object target,String name,Object value)throws Exception{
  Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
 }
 static Object emptyClient()throws Exception{
  Class<?> c=Class.forName("sun.misc.Unsafe");Field f=c.getDeclaredField("theUnsafe");f.setAccessible(true);
  Object unsafe=f.get(null);return c.getMethod("allocateInstance",Class.class).invoke(unsafe,mudclient.class);
 }
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),out=Paths.get(args[1]),empty=Paths.get(args[2]);Files.createDirectories(out);
  Map<Integer,String> expected=new LinkedHashMap<Integer,String>();
  for(String line:Files.readAllLines(Paths.get(args[3]))){String[] v=line.split("\t");expected.put(Integer.parseInt(v[0]),v[1]);}
  boolean sourceCatalog=args.length>5&&"source-catalog".equals(args[5]);
  ClientExternalAssetLoader dev=new ClientExternalAssetLoader(root,ClericIconArtFixture.class);
  ClientExternalAssetLoader packed=new ClientExternalAssetLoader(sourceCatalog?root:empty,ClericIconArtFixture.class);
  Map<Integer,Sprite> sprites=new LinkedHashMap<Integer,Sprite>();
  for(Map.Entry<Integer,String> entry:expected.entrySet()){
   String key=entry.getValue();String[] folders={"dev/myworld/assets/runtime/ui/cleric"};
   Sprite a=dev.loadExternalWorldSprite(dev.findFirstFile(folders,key+".png"),56);
   Sprite b=packed.loadExternalWorldSprite(packed.findFirstFile(folders,key+".png"),56);
   check(a!=null&&b!=null,"native source and embedded icon "+key);
   check(a.getWidth()==64&&a.getHeight()==64&&b.getWidth()==64&&b.getHeight()==64,"existing native presentation canvas "+key);
   check(Arrays.equals(a.getPixels(),b.getPixels()),"native source/embedded pixel parity "+key);
   int visible=0;for(int p:b.getPixels())if(p!=0)visible++;check(visible>0,"visible native icon "+key);
   sprites.put(entry.getKey(),b);
  }
  if(!"-".equals(args[4])){
   List<ClericSpellDef> defs=ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[4])));
   check(defs.size()==20,"actual twenty-spell wire catalog");Set<String> keys=new HashSet<String>();
   for(ClericSpellDef d:defs){check(expected.containsKey(d.getStableCode()),"unexpected spell code "+d.getStableCode());
    check(d.getIconAssetKey().equals(expected.get(d.getStableCode())),"exact code/icon mapping "+d.getStableCode());
    check(keys.add(d.getIconAssetKey()),"distinct icon key per spell");}
   ClericSpellbookCatalog book=new ClericSpellbookCatalog();book.replace(ClericSpellbookCatalog.SCHEMA_VERSION,defs);
   Object mc=emptyClient();Map<Integer,Sprite> loaded=new HashMap<Integer,Sprite>();
   set(mc,"clericSpellbook",book);set(mc,"clericIconSprites",loaded);set(mc,"externalAssetLoader",packed);
   Method load=mudclient.class.getDeclaredMethod("loadExternalClericIconSprites");load.setAccessible(true);load.invoke(mc);
   check(loaded.size()==20,"actual mudclient cache has every spell icon");
   for(Map.Entry<Integer,Sprite> e:sprites.entrySet())check(Arrays.equals(e.getValue().getPixels(),loaded.get(e.getKey()).getPixels()),"actual cached native icon "+e.getKey());
   System.out.println("PASS actual 20-spell wire mapping and mudclient "+(sourceCatalog?"source":"embedded")+" icon cache");
  }
  fonts(root);MudClientGraphics surface=new MudClientGraphics(1000,400,1);Arrays.fill(surface.pixelData,0x252930);
  int i=0;for(Map.Entry<Integer,String> e:expected.entrySet()){
   int x=(i%4)*250,y=(i/4)*80;surface.drawSprite(sprites.get(e.getKey()),x+5,y+6,64,64,5924);
   surface.drawSprite(sprites.get(e.getKey()),x+74,y+7,20,20,5924);
   surface.drawString(e.getKey()+" "+e.getValue().replace('_',' '),x+74,y+44,0xffffff,0);i++;
  }
  BufferedImage image=new BufferedImage(surface.width2,surface.height2,BufferedImage.TYPE_INT_RGB);
  image.setRGB(0,0,surface.width2,surface.height2,surface.pixelData,0,surface.width2);
  ImageIO.write(image,"png",out.resolve(sourceCatalog?"native-font-icons-catalog.png":"native-font-icons.png").toFile());
  System.out.println("PASS native "+(sourceCatalog?"source catalog sprites":"source/embedded sprites")+" and native-font render; checks="+checks);
 }
}
'''


def verify_sources():
    receipt = json.loads((ART / 'PROVENANCE.json').read_text())
    assert receipt['status'] == 'complete' and not receipt['pendingSourceFiles'], 'owner icon intake remains incomplete'
    entries = receipt['entries']
    keys = {e['iconKey'] for e in entries}
    assert len(entries) == len(keys) == 20 and keys == set(receipt['expectedIconKeys'])
    legacy = {Path(e['filename']).stem: e for e in receipt['temporaryCompatibilityAssets']}
    actual = {p.stem for p in ART.glob('*.png')}
    assert keys <= actual <= keys | set(legacy), 'missing canonical or unexpected source icon'
    for key in actual - keys:
        assert hashlib.sha256((ART / (key + '.png')).read_bytes()).hexdigest() == legacy[key]['sha256'], 'temporary legacy bytes changed'
    assert len({e['stableCode'] for e in entries}) == 20
    for entry in entries:
        path = ROOT / entry['productionPath']
        assert path == ART / (entry['iconKey'] + '.png'), 'noncanonical icon path'
        data = path.read_bytes()
        assert hashlib.sha256(data).hexdigest() == entry['sha256'], path
        assert len(data) == entry['pngBytes'] and entry['sourceBytesUnchanged'] is True
        image = Image.open(path)
        assert image.format == 'PNG' and image.mode == entry['mode'] and image.size == (16, 16)
        rgba = image.convert('RGBA')
        assert hashlib.sha256(rgba.tobytes()).hexdigest() == entry['rgbaSha256']
        alpha = rgba.getchannel('A'); histogram = alpha.histogram()
        assert list(alpha.getbbox()) == entry['visibleBounds']
        for prior_path in entry.get('knownVersionedByteIdenticalPaths', []):
            prior = subprocess.check_output(['git', 'show', receipt['legacySourceCommit'] + ':' + prior_path], cwd=ROOT)
            assert hashlib.sha256(prior).hexdigest() == entry['sha256'], 'incorrect existing-byte provenance'
        assert [histogram[0], sum(histogram[1:255]), histogram[255]] == [entry['transparentPixels'], entry['partialAlphaPixels'], entry['opaquePixels']]
    return entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-only', action='store_true', help='Check exact intake receipts without a client build')
    parser.add_argument('--client-jar', type=Path, default=ROOT / 'Client_Base/Open_RSC_Client.jar')
    parser.add_argument('--wire', type=Path, help='Fresh authoritative twenty-spell PayloadCustomGenerator packet')
    parser.add_argument('--catalog-client-jar', type=Path, help='Optional separate actual gameplay client build for source-backed wire/cache checks before the art-first merge')
    parser.add_argument('--render', type=Path, default=ROOT / 'output/reviews/cleric-spell-icons/native-render')
    parser.add_argument('--owner-source', type=Path, help='Optional read-only comparison with original owner filenames')
    args = parser.parse_args()
    entries = verify_sources()
    if args.owner_source:
        for entry in entries:
            assert (args.owner_source / entry['sourceFilename']).read_bytes() == (ROOT / entry['productionPath']).read_bytes(), 'owner source changed: ' + entry['sourceFilename']
    if args.source_only:
        print('PASS 20 exact owner PNG receipts,16x16RGBA canvases, alpha/bounds and canonical source inventory')
        return
    jar = args.client_jar.resolve()
    with zipfile.ZipFile(jar) as archive:
        actual = {name for name in archive.namelist() if name.startswith(PREFIX) and name.endswith('.png')}
        canonical = {PREFIX + entry['iconKey'] + '.png' for entry in entries}
        source_names = {PREFIX + p.name for p in ART.glob('*.png')}
        assert canonical <= actual == source_names, 'missing canonical or unexpected embedded icon'
        for name in actual - canonical:
            assert archive.read(name) == (ART / Path(name).name).read_bytes(), 'temporary legacy embedded bytes changed'
        for entry in entries:
            assert archive.read(PREFIX + entry['iconKey'] + '.png') == (ROOT / entry['productionPath']).read_bytes(), 'embedded PNGbytes differ from approved source'
    java = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
    with tempfile.TemporaryDirectory(prefix='cleric-icon-art-') as folder:
        temp = Path(folder);empty = temp / 'empty';empty.mkdir()
        source = temp / 'ClericIconArtFixture.java';source.write_text(HARNESS)
        keys = temp / 'keys.tsv';keys.write_text(''.join(str(e['stableCode'])+'\t'+e['iconKey']+'\n' for e in entries))
        subprocess.run([str(java/'javac'), '-J-Xmx128m', '-cp', str(jar), '-d', str(temp), str(source)], check=True)
        wire = args.wire
        if wire is None:
            fixture = ROOT / 'tests/myworld/fixtures/cleric-schema5-payload.hex'
            if fixture.exists():
                wire = temp / 'schema5.bin'
                wire.write_bytes(bytes.fromhex(''.join(line for line in fixture.read_text().splitlines() if not line.startswith('#'))))
        catalog_jar = args.catalog_client_jar.resolve() if args.catalog_client_jar else jar
        primary_wire = wire if catalog_jar == jar else None
        def render(client_jar, packet, mode):
            subprocess.run([str(java/'java'), '-Xmx256m', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-cp',
                            str(temp)+os.pathsep+str(client_jar), 'orsc.ClericIconArtFixture', str(ROOT), str(args.render.resolve()),
                            str(empty), str(keys), str(packet.resolve()) if packet else '-', mode], cwd=empty, check=True)
        render(jar, primary_wire, 'embedded')
        if wire is not None and catalog_jar != jar:
            subprocess.run([str(java/'javac'), '-J-Xmx128m', '-cp', str(catalog_jar), '-d', str(temp), str(source)], check=True)
            render(catalog_jar, wire, 'source-catalog')
            print('Separate actual gameplay client proved 20 wire/source-icon bindings; unified final JAR follows the art-first merge.')
        if wire is None:
            print('Catalog mapping unrun: provide --wire with the fresh 20-spell packet after gameplay integration.')
    print('PASS 20 approved exact source/JAR PNGs and native sprite/font rendering')


if __name__ == '__main__':
    main()
