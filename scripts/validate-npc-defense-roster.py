#!/usr/bin/env python3
"""Validate the defense authoring draft against real definitions; never writes data."""
from pathlib import Path
import csv,hashlib,json,math,re
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'docs/myworld/proposed-work-plans/npc-defense-roster'
STYLES=('melee','ranged','magic')
def main():
 data=json.loads((BASE/'roster.json').read_text())
 for p,h in data['definitionHashes'].items():assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h,'Source changed: '+p
 defs=[]
 for name in ['NpcDefs.json','NpcDefsCustom.json','MonsterSlayerNpcDefs.json','VisualTestNpcDefs.json','MyWorldNpcDefs.json','SlayerMovementPreviewNpcDefs.json','MonsterSlayerTowerNpcDefs.json','GemDragonNpcDefs.json','StirgeNpcDefs.json']:
  for row in json.loads((ROOT/'server/conf/server/defs'/name).read_text())['npcs']:
   assert row['id']==len(defs);defs.append(row)
 for o in json.loads((ROOT/'server/conf/server/defs/NpcDefsMyWorld.json').read_text())['npcs']:
  defs[o['id']]={**defs[o['id']],**o}
 expected={r['id'] for r in defs if r['attackable']==1};rows=data['npcs'];ids=[r['id']for r in rows]
 assert len(ids)==len(set(ids)) and set(ids)==expected
 assert {r['id']for r in rows if r['boss']}=={809,477,844}
 for r in rows:
  d=defs[r['id']];assert r['name']==d['name'] and r['level']==d['combatlvl'] and r['originalDefense']==d['defense']
  current={}
  for s,default in zip(STYLES,(1,.5,.5)):
   direct=d.get(s+'Defense',0)
   multiplier=d.get(s+'DefenseMultiplier',-1);divisor=d.get(s+'DefenseDivisor',-1)
   if multiplier<0:multiplier=1/divisor if divisor>0 else default
   current[s]=direct if direct>0 else max(0,math.floor(d['defense']*multiplier))
  assert current==r['currentDefense'],r['id']
  a=r['assignedDefense'];anchor=r['anchorDefense']
  assert set(a)==set(STYLES) and all(type(v)is int and 0<=v<=anchor for v in a.values())
  assert max(a.values())>=r['originalDefense']
  explicit=all(d.get(s+'Defense',0)>0 for s in STYLES)
  assert anchor==(r['originalDefense']if r['boss']or not explicit else max(r['originalDefense'],max(current.values())))
  if r['boss']:assert r['band']=='Boss'and all(v==r['originalDefense'] for v in a.values())
  else:
   matches=[b for b in data['ordinaryBands']if r['level']>=b['minLevel']and(b['maxLevel']is None or r['level']<=b['maxLevel'])]
   assert len(matches)==1 and matches[0]['name']==r['band']
   if anchor>0:assert min(a.values())<anchor and min(a.values())*100<=anchor*90
   if r['level']<10:assert sum(v>0 for v in a.values())<=1
 byid={r['id']:r for r in rows}
 for i,f in {23:'insects_arachnids',74:'insects_arachnids',867:'beasts',868:'beasts',869:'magical_beasts',864:'magical_birds'}.items():assert byid[i]['family']==f
 assert byid[23]['assignedDefense']=={'melee':0,'ranged':10,'magic':0}
 assert byid[74]['assignedDefense']['magic']==0
 assert byid[263]['assignedDefense']['ranged']>byid[263]['assignedDefense']['magic']>byid[263]['assignedDefense']['melee']
 for r in rows:
  a=r['assignedDefense']
  if r['family']=='corporeal_undead':assert a['ranged']==a['magic']
  if r['family']=='spirits':assert a['melee']>=a['magic']>=a['ranged']and any('provisionally'in n for n in r['notes'])
 csvrows=list(csv.DictReader((BASE/'roster.csv').open(newline='')));assert len(csvrows)==len(rows)
 for c,r in zip(csvrows,rows):
  assert int(c['id'])==r['id'] and c['family']==r['family'] and c['band']==r['band']
  for s in STYLES:
   assert int(c['assigned'+s.title()])==r['assignedDefense'][s]
   assert int(c['current'+s.title()])==r['currentDefense'][s]
 mdids=[int(x)for x in re.findall(r'^\| (\d+) \|', (BASE/'roster.md').read_text(),re.M)]
 assert len(mdids)==len(ids) and set(mdids)==set(ids)
 print(f'PASS {len(rows)} assignments: exact attackable coverage, current-source baselines, original anchors, three bosses, band coverage, zero cases, family checks and JSON/CSV/Markdown agreement. No gameplay code executed or changed.')
if __name__=='__main__':main()
