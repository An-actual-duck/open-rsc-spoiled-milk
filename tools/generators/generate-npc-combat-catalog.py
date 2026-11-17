#!/usr/bin/env python3
"""Reproduce the initial approved migration; not a production build dependency.

NpcCombatMyWorld.json is the gameplay authority after migration. Regenerating
from frozen proposals will overwrite subsequent balancing, so review its diff.
"""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[2]
L=json.load(open(R/'docs/myworld/proposed-work-plans/npc-attack-loadouts/loadouts.json'));ls={r['id']:r for r in L['rows']};ps={r['id']:r for r in json.load(open(R/'docs/myworld/proposed-work-plans/npc-offensive-powers/powers.json'))['rows']};ds={r['id']:r for r in json.load(open(R/'docs/myworld/proposed-work-plans/npc-defense-roster/roster.json'))['npcs']}
files=['NpcDefs.json','NpcDefsCustom.json','MonsterSlayerNpcDefs.json','VisualTestNpcDefs.json','MyWorldNpcDefs.json','SlayerMovementPreviewNpcDefs.json','MonsterSlayerTowerNpcDefs.json','GemDragonNpcDefs.json','StirgeNpcDefs.json'];defs=[]
for name in files:defs+=json.load(open(R/'server/conf/server/defs'/name))['npcs']
for o in json.load(open(R/'server/conf/server/defs/NpcDefsMyWorld.json'))['npcs']:defs[o['id']]={**defs[o['id']],**o}
rows=[]
for d in defs:
 i=d['id'];l=ls.get(i);p=ps.get(i);r={'id':i,'name':d['name']}
 if l is None:
  assert not d.get('attackable'), f'Attackable NPC {i} lacks approved combat authoring'
  r.update(policy='noncombat_compatibility',reason='Nonattackable definition; no generic combat migration or summon-instance changes.');rows.append(r);continue
 if p['assignmentPolicy']in ['protected_no_change','role_review_hold']:r.update(policy=p['assignmentPolicy'],reason=' '.join(p['notes']));rows.append(r);continue
 magic=next((a for a in l['attacks']if a['style']=='magic'),None)
 specials=[]
 for a in l['specialAttacks']:
  a=dict(a);a['damageKind']='true_damage'if a['key']=='dragon_breath'else'dot'if a['key']in ['burn','bleeding_wound']else'standard'
  if a['key']=='dragon_breath':a['notes']='True damage bypasses ordinary style defenses; retain existing species, target-HP, shield, roll and settlement branches. Not Magic Power.'
  if a['key']=='burn':a['notes']='Damage over time remains outside ordinary style defense and Power rolls; retain existing encounter burn rules.'
  specials.append(a)
 r.update(policy='canonical',power=p['assignedPower'],defense=ds[i]['assignedDefense'],holyPower=0,boss=l['boss'],primaryStyle=l['primaryStyle'],styles=l['enabledStyles'],dispatch='generic'if l['genericDispatchEligible']else'special_preserved',weights=p['selectionWeights'],projectileRange=d.get('projectileRange',5),magicElements=magic['elementOptions']if magic else[],magicGod=magic['godLine']if magic else None,rangedVisual= 'bolt'if i==574 else'dart'if i==421 else'naga_scimitar'if i==866 else'acid_drop'if i==863 else'throwing_knife'if i in [21,89,64,351,342,352,137,264,232,234]else'arrow',creatureTags=l['creatureTags'],creatureAffinities=l['creatureAffinities'],attackTags={a['key']:a['tags']for a in l['attacks']},specialAttacks=specials)
 if r['projectileRange']<=0:r['projectileRange']=5
 rows.append(r)
path=R/'server/conf/server/defs/NpcCombatMyWorld.json';payload=json.dumps({'schemaVersion':1,'npcs':rows},indent=2)+'\n'
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
if args.check:
 assert path.read_text()==payload, 'NPC combat catalog differs from approved authoring; regenerate and review'
 print('PASS complete NPC combat catalog matches approved authoring:',len(rows),'records')
else:
 path.write_text(payload);print(path,len(rows))
