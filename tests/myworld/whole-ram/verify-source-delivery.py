#!/usr/bin/env python3
"""Independent byte join to original package, plus actual producer refusal tests."""
import argparse,copy,hashlib,importlib.util,json,pathlib,shutil,struct,sys
sys.dont_write_bytecode=True
p=argparse.ArgumentParser();p.add_argument('--module',type=pathlib.Path,required=True);p.add_argument('--delivery',type=pathlib.Path,required=True);p.add_argument('--package',type=pathlib.Path,required=True);p.add_argument('--out',type=pathlib.Path,required=True);a=p.parse_args()
def sha(b):return hashlib.sha256(b).hexdigest()
def check(v,m):
 global checks
 checks+=1
 if not v:raise AssertionError(m)
checks=0
if a.out.exists():raise ValueError('Preserve previous results')
a.out.mkdir(parents=True);spec=importlib.util.spec_from_file_location('producer',a.module);producer=importlib.util.module_from_spec(spec);spec.loader.exec_module(producer)
m=json.loads((a.delivery/'manifest.json').read_text());source=json.loads((a.package/'manifest.json').read_text());data=(a.delivery/'source.bin').read_bytes();check(struct.unpack('<4I',data[:16])==(0x57524131,1,1797,46101718),'Exact wire header')
# Roster is independently derived from the original package, never producer payload_roster.
paths={'manifest.json'}|{x['path']for name in ('terrainSectors','placementSets')for x in source[name]};check(len(paths)==1797,'Original selected full source membership')
rows={x['path']:x for x in m['files']};check(set(rows)==paths and len(m['files'])==len(paths),'No missing or duplicated source files')
position=16;total=0
for name in sorted(paths):
 file=a.package/name;check(not file.is_symlink()and file.is_file(),'Real original source path');raw=file.read_bytes();row=rows[name];check(row['offset']==position and row['bytes']==len(raw),'Exact contiguous source interval');check(data[position:position+len(raw)]==raw and sha(raw)==row['sha256'],'Every original source byte/hash unchanged');position+=len(raw);total+=len(raw)
check(position==len(data)and total==46101718,'No trailing/gap source bytes');check(m.get('complete')is False and m.get('prepared')is None,'Source-only cannot claim prepared readiness')
producer.validate_source(a.delivery)
work=a.out/'mutations';work.mkdir();shutil.copyfile(a.delivery/'source.bin',work/'source.bin');failures=[]
def rejected(name,change):
 bad=copy.deepcopy(m);change(bad);(work/'manifest.json').write_text(json.dumps(bad));
 try:producer.validate_source(work)
 except (ValueError,KeyError,TypeError,OverflowError):failures.append(name);check(True,name);return
 raise AssertionError('Accepted malformed source delivery: '+name)
rejected('duplicate payload path',lambda x:x['files'].__setitem__(1,copy.deepcopy(x['files'][0])))
rejected('source interval overlap',lambda x:x['files'][1].__setitem__('offset',16))
rejected('source interval beyond buffer',lambda x:x['files'][-1].__setitem__('bytes',2**31))
rejected('source path traversal',lambda x:x['files'][1].__setitem__('path','../manifest.json'))
rejected('wrong signed scope',lambda x:x['files'][1].__setitem__('level',10))
rejected('bool signed level',lambda x:x['files'][1].__setitem__('level',True))
rejected('payload digest changed same source manifest',lambda x:x['files'][-1].__setitem__('sha256','0'*64))
rejected('source manifest digest mismatch',lambda x:x.__setitem__('sourceManifestSha256','0'*64))
rejected('incomplete raw file count',lambda x:x['scope'].__setitem__('rawPackageFiles',1796))
rejected('wrong prepared query scope',lambda x:x['scope'].__setitem__('preparedQueryLevels',[0,1,10]))
rejected('wrong external asset order',lambda x:x['externalAssets'].reverse())
rejected('noninteger packing setting',lambda x:x['settings'].__setitem__(0,True))
# Same metadata/manifest, changed byte; restore after refusal so each case is independent.
(work/'manifest.json').write_text(json.dumps(m))
with(work/'source.bin').open('r+b')as f:f.seek(len(data)-1);f.write(bytes([data[-1]^1]))
try:producer.validate_source(work);raise AssertionError('Changed source bytes accepted')
except ValueError:failures.append('changed actual payload same manifest');checks+=1
shutil.copyfile(a.delivery/'source.bin',work/'source.bin')
with(work/'source.bin').open('r+b')as f:f.truncate(len(data)-1)
try:producer.validate_source(work);raise AssertionError('Truncated source accepted')
except ValueError:failures.append('truncated actual source');checks+=1
result={'passed':True,'checks':checks,'files':len(paths),'rawBytes':total,'bundleBytes':len(data),'producerSource':str(a.module),'producerSha256':sha(a.module.read_bytes()),'deliveryManifestSha256':sha((a.delivery/'manifest.json').read_bytes()),'sourceManifestSha256':sha((a.package/'manifest.json').read_bytes()),'refusals':failures,'scope':'Every original selected source byte joined independently, bounded malformed producer gates. Source-only incomplete artifact, not prepared Java products/full RAM/browser acceptance.'};(a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
