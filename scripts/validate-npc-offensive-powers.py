#!/usr/bin/env python3
"""Check the power-authoring data, source bindings and independent damage bounds."""
from pathlib import Path
import json,hashlib,csv,re,math
R=Path(__file__).resolve().parents[1];B=R/'docs/myworld/proposed-work-plans/npc-offensive-powers';S=('melee','ranged','magic')
def main():
 import argparse,subprocess
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--historical-source',action='store_true',help='Validate the frozen authoring baseline at its recorded sourceCommit, rather than current runtime source');args=parser.parse_args()
 def source_bytes(path,data):
  return subprocess.check_output(['git','show',data['sourceCommit']+':'+path],cwd=R)if args.historical_source else(R/path).read_bytes()

 data=json.loads((B/'powers.json').read_text());rows=data['rows'];L=R/'docs/myworld/proposed-work-plans/npc-attack-loadouts/loadouts.json'
 assert data['runtimeLoadable']is False and hashlib.sha256(L.read_bytes()).hexdigest()==data['loadoutSha256']
 for p,h in data['sourceHashes'].items():assert hashlib.sha256(source_bytes(p,data)).hexdigest()==h,p
 load={x['id']:x for x in json.loads(L.read_text())['rows']};audit={x['id']:x for x in json.loads((R/'docs/myworld/proposed-work-plans/npc-offense-style-audit/audit.json').read_text())['rows']}
 assert len(rows)==len({r['id']for r in rows})==298 and {r['id']for r in rows}==set(load)
 byid={r['id']:r for r in rows};ratios={'Low':[100,40,10],'Mid Low':[100,50,20],'Mid':[100,60,30],'Mid High':[100,70,40],'High':[100,80,50],'Very High':[100,90,60]}
 for r in rows:
  i=r['id'];l=load[i];a=audit[i];p=r['assignedPower'];assert set(p)==set(S)and all(type(v)is int and 0<=v<2147483647 for v in p.values())
  assert r['currentPower']==a['baselinePower'] and r['legacyStrength']==a['legacyStrength']and r['legacyAttack']==a['legacyAttack']
  assert r['primaryStyle']==l['primaryStyle']and r['enabledStyles']==l['enabledStyles']
  policy=r['assignmentPolicy']
  if policy in ['protected_no_change','role_review_hold']:assert p==r['currentPower']and r['selectionWeights']is None
  else:
   for st in S:
    if st not in r['enabledStyles']:assert p[st]==0
  if policy=='band_reference':
   assert r['bandPercentReferences']==ratios[r['band']]
   ref=r['powerReference'];assert ref is not None and ref>=0
   if r['referenceBasis']=='legacy_strength':assert ref==r['legacyStrength']
   for st,percent in zip(r['enabledStyles'],ratios[r['band']]):assert p[st]==(max(1,ref*percent//100)if ref>0 else 0)
   assert p[r['primaryStyle']]==ref
  if policy=='protected_no_change':assert l['mode']=='protected_minigame'
  if policy=='preserve_authored_encounter':assert i in [844,865,869]and all(p[st]==r['currentPower'][st]for st in r['enabledStyles'])
  if r['selectionWeights']is not None:
   weights=r['selectionWeights'];assert set(weights)==set(r['enabledStyles'])and sum(weights.values())==100 and all(type(x)is int and x>0 for x in weights.values())
   if len(weights)>1:assert weights[r['primaryStyle']]>max(v for k,v in weights.items()if k!=r['primaryStyle'])
  if l['mode']=='scripted_preserve':assert r['selectionWeights']is None
  for powers,enabled,reported in [(r['currentPower'],r['currentAttackStyles'],r['currentRawCeilings']),(p,r['currentAttackStyles']if policy in ['protected_no_change','role_review_hold']else r['enabledStyles'],r['assignedRawCeilings'])]:
   for st in S:
    expected=None
    if st in enabled:expected=math.ceil(max(1,powers[st]/12))+max(0,math.floor(powers[st]/7))if st=='magic'else max(1,1+math.floor(powers[st]/7))
    assert expected==reported[st],(i,st,expected,reported[st])
 assert sum(r['assignmentPolicy']=='protected_no_change'for r in rows)==22
 assert byid[254]['assignedPower']=={'melee':104,'ranged':0,'magic':104}
 assert byid[809]['assignedPower']=={'melee':250,'ranged':0,'magic':250}and byid[809]['assignedRawCeilings']['magic']==56
 assert byid[477]['assignedPower']=={'melee':224,'ranged':0,'magic':201}
 assert byid[844]['assignedPower']=={'melee':250,'ranged':235,'magic':270}
 assert byid[865]['assignedPower']=={'melee':55,'ranged':0,'magic':55}
 assert byid[869]['assignedPower']=={'melee':110,'ranged':0,'magic':210}
 assert byid[863]['assignedPower']=={'melee':0,'ranged':30,'magic':0}
 assert byid[882]['assignedPower']['melee']==55 and byid[866]['assignedPower']=={'melee':39,'ranged':65,'magic':0}
 assert byid[550]['assignmentPolicy']=='role_review_hold'
 for i in [66,108,189,102,836,401]:assert byid[i]['selectionWeights']=={'melee':90,'magic':10}
 cs=list(csv.DictReader((B/'powers.csv').open(newline='')));assert len(cs)==len(rows)
 for c,r in zip(cs,rows):
  assert int(c['id'])==r['id']and c['assignmentPolicy']==r['assignmentPolicy']
  for st in S:
   assert int(c['assigned'+st.title()])==r['assignedPower'][st]and int(c['current'+st.title()])==r['currentPower'][st]
   for prefix,key in [('old','currentRawCeilings'),('new','assignedRawCeilings')]:assert c[prefix+st.title()+'Ceiling']==(''if r[key][st]is None else str(r[key][st]))
 ids=[int(x)for x in re.findall(r'^\| (\d+) \|',(B/'powers.md').read_text(),re.M)];assert len(ids)==298 and set(ids)==set(byid)
 print('Historical source snapshot; runtime parity is checked by test-canonical-npc-combat.py.'if args.historical_source else'Current source bindings checked.')
 print('PASS298 power assignments: source/loadout bindings, Strength/band rules, disabled zeros, authored references, boss/Ice Queen/script/minigame exceptions, weights, independent raw ceilings and JSON/CSV/Markdown parity. No runtime gameplay tests or changes.')
if __name__=='__main__':main()
