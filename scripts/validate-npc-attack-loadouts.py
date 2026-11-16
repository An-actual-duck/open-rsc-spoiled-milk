#!/usr/bin/env python3
"""Validate authoring loadouts/tags without executing gameplay or changing files."""
from pathlib import Path
import json,hashlib,csv,re,collections
R=Path(__file__).resolve().parents[1];B=R/'docs/myworld/proposed-work-plans/npc-attack-loadouts'
def main():
 d=json.loads((B/'loadouts.json').read_text());v=json.loads((B/'tag-vocabulary.json').read_text());rows=d['rows'];defense=json.loads((R/'docs/myworld/proposed-work-plans/npc-defense-roster/roster.json').read_text())['npcs'];audit={x['id']:x for x in json.loads((R/'docs/myworld/proposed-work-plans/npc-offense-style-audit/audit.json').read_text())['rows']}
 assert d['runtimeLoadable']is False
 for p,h in d['sourceHashes'].items():assert hashlib.sha256((R/p).read_bytes()).hexdigest()==h,'Changed input: '+p
 ids=[x['id']for x in rows];assert len(ids)==len(set(ids))==298 and set(ids)=={r['id']for r in defense}
 vocab=set(v['tags']);modes=collections.Counter();primary=collections.Counter();byid={x['id']:x for x in rows}
 for r in rows:
  modes[r['mode']]+=1;primary[r['primaryStyle']]+=1;s=r['enabledStyles'];assert s and len(s)==len(set(s)) and set(s)<={'melee','ranged','magic'} and s[0]==r['primaryStyle']
  assert r['secondaryStyle']==(s[1]if len(s)>1 else None)and r['tertiaryStyle']==(s[2]if len(s)>2 else None)
  assert {a['style']for a in r['attacks']}==set(s)
  assert set(r['creatureTags'])<=vocab and set(r['creatureAffinities'])<=set(v['elements'])
  if r['mode']in ['protected_minigame','scripted_preserve']:assert not r['genericDispatchEligible']and r['selectionPolicy']=='preserve_existing'
  else:assert r['genericDispatchEligible']
  if r['questReferencePaths']and r['mode']!='protected_minigame':assert 'melee'in s
  if r['mode']=='protected_minigame':
   old=audit[r['id']]['generalProfile'];expected=['melee']if old=='MELEE'else['magic']if old=='PURE_MAGIC'else['ranged']if old=='PURE_RANGED'else['ranged','melee']if old=='MELEE_RANGED'else['magic','melee']if old=='MELEE_FREQUENT_MAGIC'else['melee','magic'];assert s==expected
  for a in r['attacks']:
   assert set(a['tags'])<=vocab and set(a['elementOptions'])<=set(v['elements'])
   assert a['godLine']is None or a['godLine']in v['godLines']
   if a['godLine']:assert a['style']=='magic'and'holy'in a['tags']and'god.'+a['godLine']in a['tags']
   if a['matchingGodReductionPercent']:assert a['matchingGodReductionPercent']==20 and a['godLine']and r['mode']!='protected_minigame'
   else:assert a['godLine']is None or r['mode']=='protected_minigame'
  for a in r['specialAttacks']:assert set(a['tags'])<=vocab and a['dispatch']=='existing_special_owner'
 for i in [139,140,293,567,57,60,79,81]:assert byid[i]['enabledStyles']==['magic']
 for i in [182,658,254,364,798,766,767,772]:assert byid[i]['enabledStyles']==['magic','melee']
 for i in [178,615]:assert byid[i]['enabledStyles']==['magic','melee']
 assert byid[844]['enabledStyles']==['magic','melee','ranged']and byid[844]['mode']=='scripted_preserve'
 assert all(byid[i]['mode']=='protected_minigame'for i in [713,757,758,759,760,789,790,791,595,597,598,599,600,602,603,604,605,606,607,608])
 assert byid[865]['mode']=='scripted_preserve'and all(a['godLine']is None for a in byid[865]['attacks'])
 assert byid[869]['mode']=='scripted_preserve'and byid[869]['primaryStyle']=='magic'
 assert 'undead'not in byid[319]['creatureTags']
 assert len(byid[57]['attacks'][0]['elementOptions'])==2 and not byid[57]['creatureAffinities']
 cs=list(csv.DictReader((B/'loadouts.csv').open(newline='')));assert len(cs)==len(rows)
 for c,r in zip(cs,rows):
  assert int(c['id'])==r['id']and c['primaryStyle']==r['primaryStyle']and c['mode']==r['mode']
  assert c['enabledStyles']==' / '.join(r['enabledStyles'])and c['creatureTags']=='; '.join(r['creatureTags'])
 mdids=[int(x)for x in re.findall(r'^\| (\d+) \|',(B/'loadouts.md').read_text(),re.M)];assert len(mdids)==298 and set(mdids)==set(ids)
 assert modes=={'exclusive':190,'mixed':82,'protected_minigame':22,'scripted_preserve':4}
 assert primary=={'melee':213,'ranged':36,'magic':49}
 print('PASS 298 loadouts: current-source bindings, full roster, ordered styles, quest/minigame safeguards, special owners, god/element/tag semantics and JSON/CSV/Markdown agreement. No runtime combat test or gameplay change.')
if __name__=='__main__':main()
