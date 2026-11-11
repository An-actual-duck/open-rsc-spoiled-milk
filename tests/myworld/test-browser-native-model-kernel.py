#!/usr/bin/env python3
"""Frozen production RSModel differential. CPU only; no server or browser."""
import argparse, datetime, hashlib, json, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = '8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
MODEL = 'Client_Base/src/orsc/graphics/three/RSModel.java'
HELPER = ROOT / 'Client_Base/src/orsc/graphics/three/BrowserNativeModelKernel.java'
INSERT = '''                if (BrowserNativeModelKernel.enabled && BrowserNativeModelKernel.tryTransform(
                        this.vertHead, this.vertXTransform, this.vertYTransform, this.vertZTransform,
                        this.vertexParam6, this.vertexParam2, this.vertXRot, this.vertYRot, this.vertZRot,
                        xOffset, yOffset, zOffset, vParamSrc, zTop, rotZ, rotY, rotX,
                        xy_xy, xy_yy, xz_xz, xz_xx, yz_zy, yz_zz)) return;

'''

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-jar', type=pathlib.Path, required=True)
    parser.add_argument('--java-home', type=pathlib.Path)
    parser.add_argument('--cases', type=int, default=6000)
    args = parser.parse_args()
    jar = args.baseline_jar.resolve()
    category = pathlib.Path(subprocess.check_output(['python3', str(ROOT/'scripts/workspace-layout.py'), 'path', 'tests', 'browser-native-model-kernel', '--mkdir'], text=True).strip())
    run = category / datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'); run.mkdir()
    java = str(args.java_home/'bin/java') if args.java_home else 'java'
    javac = str(args.java_home/'bin/javac') if args.java_home else 'javac'
    frozen = subprocess.check_output(['git', 'show', BASE+':'+MODEL], cwd=ROOT)
    current = (ROOT/MODEL).read_bytes()
    def rotate_method(data):
        start=data.index(b'final void rotate1024('); opening=data.index(b'{',start); depth=0
        for offset in range(opening,len(data)):
            if data[offset] == ord('{'): depth+=1
            elif data[offset] == ord('}'):
                depth-=1
                if depth==0:return data[start:offset+1]
        raise AssertionError('Unclosed rotate method')
    actual_method=rotate_method(current)
    assert actual_method.count(INSERT.encode()) == 1
    assert actual_method.replace(INSERT.encode(), b'') == rotate_method(frozen), 'Literal RSModel rotation fallback changed'
    old = run/'baseline'; old.mkdir(); source = old/'RSModel.java'; source.write_bytes(frozen)
    classes = run/'current'; classes.mkdir()
    fixtures = [ROOT/'tests/myworld'/n for n in ['NativeModelKernelReferenceFixture.java','NativeModelKernelGuardFixture.java','NativeModelKernelFallbackFixture.java','NativeModelKernelBrowserProbe.java']]
    def execute(name, command):
        with (run/(name+'.log')).open('w') as log:
            subprocess.run(command, cwd=ROOT, check=True, stdout=log, stderr=subprocess.STDOUT)
    execute('compile-baseline',[javac,'-source','8','-target','8','-cp',str(jar),'-d',str(old),str(source)])
    execute('compile-current',[javac,'-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(ROOT/MODEL),str(HELPER),*map(str,fixtures)])
    common=['-Xmx768m','-Djava.awt.headless=true']
    for name, cp, flags in [('baseline',str(old)+':'+str(classes)+':'+str(jar),[]),('current-default',str(classes)+':'+str(jar),[]),('current-property-only',str(classes)+':'+str(jar),['-Dspoiledmilk.browserNativeModelKernel=true'])]:
        execute(name,[java,*common,*flags,'-cp',cp,'orsc.graphics.three.NativeModelKernelReferenceFixture',str(run/(name+'.bin')),str(args.cases)])
    assert (run/'baseline.bin').read_bytes() == (run/'current-default.bin').read_bytes() == (run/'current-property-only.bin').read_bytes()
    execute('js-oracle',['node','--experimental-default-type=module',str(ROOT/'tests/myworld/test-browser-native-model-kernel.mjs'),str(ROOT/'tools/browser-gameplay/web/browser-native-model-kernel.js'),str(run/'baseline.bin')])
    execute('java-guards',[java,*common,'-cp',str(classes)+':'+str(jar),'orsc.graphics.three.NativeModelKernelGuardFixture'])
    for name,cp in [('baseline',str(old)+':'+str(classes)+':'+str(jar)),('current',str(classes)+':'+str(jar))]:
        execute('fallback-'+name,[java,*common,'-cp',cp,'orsc.graphics.three.NativeModelKernelFallbackFixture'])
    baseline_fallback=(run/'fallback-baseline.log').read_text().splitlines()[-1]
    assert baseline_fallback == (run/'fallback-current.log').read_text().splitlines()[-1]
    bootstrap = run/'bootstrap'; bootstrap.mkdir()
    execute('compile-bootstrap',[javac,'-source','8','-target','8','-cp',str(classes)+':'+str(jar),'-d',str(bootstrap),*map(str,sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java')))])
    oracle=json.loads((run/'js-oracle.log').read_text().splitlines()[-1])
    result={'passed':True,'cpuOnly':True,'baselineSource':BASE,'baselineJarSha256':sha(jar),'cases':args.cases,'referenceBytes':(run/'baseline.bin').stat().st_size,'referenceSha256':sha(run/'baseline.bin'),'jsOracle':oracle,'fallbackDigest':baseline_fallback,'javaGuards':(run/'java-guards.log').read_text().strip(),'scope':'Frozen actual RSModel outputs, literal current Java OFF/property-only parity, JS entire-array equality, unchanged malformed-input partial writes/exceptions, private JNI guard and bootstrap compilation. Actual browser writeback and FPS remain manager checks.'}
    (run/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'passed':True,'run':str(run),**result},indent=2))

if __name__ == '__main__': main()
