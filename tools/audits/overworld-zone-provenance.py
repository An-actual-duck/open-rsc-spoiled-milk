#!/usr/bin/env python3
"""Read-only selected map provenance and local release inventory verification."""
import argparse,hashlib,importlib.util,json,subprocess,sys,zipfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
def sha(b):return hashlib.sha256(b).hexdigest()
def filehash(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def main():
 a=argparse.ArgumentParser(description=__doc__);a.add_argument('--root',type=Path,default=ROOT);a.add_argument('--release',type=Path,required=True);o=a.parse_args();root=o.root.resolve()
 spec=importlib.util.spec_from_file_location('selection',root/'scripts/installed-map-selection.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);selection=m.select(root);package=Path(selection['serverPackage']);entries,fingerprint=m.inventory(package)
 release=o.release.resolve();assert release.is_file();archive_entries=[]
 with zipfile.ZipFile(release) as z:
  names=z.namelist();assert len(names)==len(set(names));prefix=next(n[:-len('PACKAGE-FINGERPRINT.txt')] for n in names if n.endswith('/PACKAGE-FINGERPRINT.txt'))
  assert z.read(prefix+'PACKAGE-FINGERPRINT.txt').decode().strip()==fingerprint
  source=z.read(prefix+'SOURCE-COMMIT.txt').decode().strip();generation=json.loads(z.read(prefix+'GENERATION-REPORT.json'))
  for n in names:
   if n.startswith(prefix+'package/')and not n.endswith('/'):
    info=z.getinfo(n);assert info.file_size<=m.MAX_FILE
    b=z.read(n);archive_entries.append((n[len(prefix+'package/'):],len(b),sha(b)))
  assert sorted(archive_entries)==entries
 assets={}
 primary=json.loads((root/m.PRIMARY).read_text())
 for key in ['serverRuntimeRelativePath','clientRuntimeRelativePath','serverDefinitionCatalogRelativePath','clientDefinitionCatalogRelativePath']:
  p=root/primary[key];assets[key]={'path':primary[key],'bytes':p.stat().st_size,'sha256':filehash(p)}
 for row in primary['assets']:
  for key in ['serverRelativePath','clientRelativePath']:
   p=root/row[key];assets[key]={'path':row[key],'bytes':p.stat().st_size,'sha256':filehash(p)}
 for p in [root/'Client_Base/Cache/video/models.orsc',root/'Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java']:
  assets[str(p.relative_to(root))]={'bytes':p.stat().st_size,'sha256':filehash(p)}
 evidence=root/'docs/myworld/evidence/ichor-bone-spawn-repair.json';e=json.loads(evidence.read_text());assert e['fingerprint']==fingerprint and e['manifestSha256']==selection['manifestSha256']
 checkpoint=Path(e['correctedCheckpoint'])/'project.json';author={'path':str(checkpoint),'exists':checkpoint.is_file()}
 if checkpoint.is_file():author.update(bytes=checkpoint.stat().st_size,sha256=filehash(checkpoint))
 report={'auditOnly':True,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'selection':selection,'packageBytes':sum(n for _,n,_ in entries),'localRelease':{'path':str(release),'bytes':release.stat().st_size,'sha256':filehash(release),'sourceCommit':source,'packageInventoryExact':True,'fileCount':len(archive_entries),'manifestSha256':generation['manifestSha256']},'assetsAndDescriptors':assets,'authoringEvidence':{'path':str(evidence.relative_to(root)),'sha256':filehash(evidence),'selectedCheckpoint':author,'generation':e['correctedCheckpointGeneration'],'originalOwnerSavePreserved':e['originalOwnerSaveUnchanged']},'limits':['Local selected source/release bytes verified. No public Oracle files or published download endpoint inspected.','Known checkpoint chain matches selected package provenance; not a claim that no later unsaved/unimported editor project exists.','Browser prepare.py copies profile+manifest only; full selected local package is not automatically browser resident.','Manifest placements do not alone prove absence of dynamic/plugin runtime additions.']}
 out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','diagnostics','overworld-zone-inventory','--mkdir'],text=True).strip());(out/'provenance.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({'passed':True,'packageBytes':report['packageBytes'],'releaseExact':True,'releaseSha256':report['localRelease']['sha256']}))
if __name__=='__main__':main()
