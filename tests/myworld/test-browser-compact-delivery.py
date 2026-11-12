#!/usr/bin/env python3
"""Real derivative admission plus early refusal and same-artifact URL controls.
No Java/game/browser/GPU/service starts; source files and bakes remain untouched.
"""
import importlib.util,json,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/browser-gameplay'))
from compact_geometry_delivery import admit_compact_geometry
from private_scene_urls import scene_urls

def main():
 if len(sys.argv)!=3:raise SystemExit('Explicit canonical whole-zone directory and compact run required')
 canonical=Path(sys.argv[1]).resolve();run=Path(sys.argv[2]).resolve();m=json.loads((canonical/'manifest.json').read_text());r=json.loads((run/'compact-receipt.json').read_text());side=(run/'compact-zone/compact-manifest.json').read_bytes()
 admitted=admit_compact_geometry(run,canonical,m);assert len(admitted['files'])==len(m['chunks'])+1
 base=ROOT/'output/tests/browser-compact-geometry';base.mkdir(parents=True,exist_ok=True);checks=1
 with tempfile.TemporaryDirectory(prefix='refusal-',dir=base)as tmp:
  p=Path(tmp);(p/'compact-zone').mkdir();(p/'compact-zone/compact-manifest.json').write_bytes(side)
  for field,value in [('passed',False),('complete',False),('CPUOnly',False),('canonicalCatalogSha256','0'*64),('manifestSha256','0'*64),('geometryLayout','unknown'),('attributeFloat32Comparisons',0),('driverFiles',{})]:
   bad=dict(r);bad[field]=value;(p/'compact-receipt.json').write_text(json.dumps(bad))
   try:admit_compact_geometry(p,canonical,m)
   except ValueError:checks+=1
   else:raise AssertionError('Accepted invalid '+field)
 urls=scene_urls('http://127.0.0.1:8232/?wholeRAM=on',gpu_residency='bounded',static_scene='on',gpu_budget_mib=512,geometry_format='compact',compact_available=True)
 from urllib.parse import urlsplit,parse_qs
 a=parse_qs(urlsplit(urls['urlGeometryLegacy']).query);b=parse_qs(urlsplit(urls['urlGeometryCompact']).query)
 assert a.pop('geometryformat')==['legacy'];assert b.pop('geometryformat')==['compact'];assert a==b;checks+=1
 assert urls['sceneControls']['ordinaryDefaultsChanged']is False;checks+=1
 for kwargs in [{'geometry_format':'compact'},{'geometry_format':'bogus','compact_available':True}]:
  try:scene_urls('http://127.0.0.1/',**kwargs)
  except ValueError:checks+=1
  else:raise AssertionError('Invalid geometry control accepted')
 print(json.dumps({'passed':True,'checks':checks,'realResourcesValidated':len(m['chunks']),'scope':'Full actual derivative receipt/hash/header admission, early refusal, same artifact startup URLs; no browser/GPU/timing proof'}))
if __name__=='__main__':main()
