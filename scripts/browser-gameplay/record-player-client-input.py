#!/usr/bin/env python3
"""Record the freshly built official player JAR as the exact browser bake input."""
from pathlib import Path
import argparse,hashlib,json,re,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,sort_keys=True,indent=2)+'\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',required=True);a=p.parse_args()
 if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-alpha\.[0-9]+)?',a.version):p.error('Exact release version required')
 head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():raise SystemExit('Clean published manager source required')
 if subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()!='main' or head!=subprocess.check_output(['git','rev-parse','spoiled-milk/main'],cwd=ROOT,text=True).strip():raise SystemExit('Published main required')
 archive=ROOT/'output/releases'/a.version/f'spoiled-milk-{a.version}-java.zip'
 jar=ROOT/'Client_Base/Open_RSC_Client.jar'
 with zipfile.ZipFile(archive) as z:
  sources=[n for n in z.namelist() if n.endswith('/game-files/SOURCE-COMMIT.txt')];jars=[n for n in z.namelist() if n.endswith('/game-files/Open_RSC_Client.jar')]
  if len(sources)!=1 or z.read(sources[0]).decode().strip()!=head or len(jars)!=1 or hashlib.sha256(z.read(jars[0])).hexdigest()!=sha(jar):raise SystemExit('Official release archive source/JAR differs')
 out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','builds','browser-release-input','--mkdir'],cwd=ROOT,text=True).strip())/a.version;out.mkdir()
 shutil.copyfile(jar,out/'client.jar')
 tracked=subprocess.check_output(['git','ls-files','Client_Base/src','PC_Client/src','Client_Base/build.xml','runtime-contract'],cwd=ROOT,text=True).splitlines()
 sources={name:sha(ROOT/name) for name in tracked if (ROOT/name).is_file()}
 with zipfile.ZipFile(jar) as z:
  if len(z.namelist())!=len(set(z.namelist())):raise SystemExit('Duplicate JAR entry')
  classes={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.endswith('.class')}
  assets={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith(('myworld-assets/','map-runtime/')) and not n.endswith('/')}
  if any(n.startswith(('myworld-assets/authoring/','myworld-assets/references/')) for n in z.namelist()):raise SystemExit('Authoring/reference assets packaged')
 for name,data in [('source-manifest.json',sources),('compiled-classes.json',classes),('asset-manifest.json',assets)]:write(out/name,data)
 receipt={'singleProductionCommit':True,'clientSourceCommit':head,'jar':str(out/'client.jar'),'jarSha256':sha(jar),'sourceManifestSha256':sha(out/'source-manifest.json'),'compiledClassesManifestSha256':sha(out/'compiled-classes.json'),'assetManifestSha256':sha(out/'asset-manifest.json'),'cleanCheckoutAtBuild':True,'buildSucceeded':True,'compiledClasses':len(classes),'packagedOwnAssets':len(assets),'classesTransplanted':False,'freshBuildAuthority':'scripts/ai-manager.sh release (fresh builds mandatory)','playerArchive':str(archive),'playerArchiveSha256':sha(archive),'gameServerOrGpuStarted':False}
 write(out/'receipt.json',receipt);print(out/'receipt.json')
if __name__=='__main__':main()
