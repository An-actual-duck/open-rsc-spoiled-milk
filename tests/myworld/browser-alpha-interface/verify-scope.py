#!/usr/bin/env python3
"""Closed review of three menu-only mudclient edits; no light-query waiver."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--commit',required=True);a=p.parse_args()
BASE='1ce24dca0bc70fd67bf7d1a16aec7e49408e342c';NAME='Client_Base/src/orsc/mudclient.java'
def get(commit):return subprocess.check_output(['git','-C',str(a.source_root),'show',commit+':'+NAME],text=True)
def sha(s):return hashlib.sha256(s.encode()).hexdigest()
def once(s,old,new):
 assert s.count(old)==1,'Missing or ambiguous reviewed UI boundary'
 return s.replace(old,new,1)
ROW='''\t\tindex = addSettingsRow(index,
\t\t\t"@whi@Middle mouse - " + DesktopMiddleMouseSettings.getMode().label,
\t\t\tRendererSettingsPanel.MIDDLE_MOUSE);

'''
NEW='''\t\t\tcase BROWSER_FIT:
\t\t\t\tBrowserPresentationSettings.request(BrowserPresentationSettings.FIT_TO_TAB);
\t\t\t\tbreak;
\t\t\tcase BROWSER_FULLSCREEN:
\t\t\t\tBrowserPresentationSettings.request(BrowserPresentationSettings.FULLSCREEN);
\t\t\t\tbreak;'''
OLD='''\t\t\tcase MIDDLE_MOUSE:
\t\t\t\tthis.cycleDesktopMiddleMouseMode();
\t\t\t\tbreak;'''
DISPATCH='''\t\tif (settingTab == 1 && settingIndex == RendererSettingsPanel.MIDDLE_MOUSE && this.mouseButtonClick == 1) {
\t\t\tthis.cycleDesktopMiddleMouseMode();
\t\t}

'''
def normalize(s):return once(once(once(s,ROW,''),NEW,OLD),DISPATCH,'')
base=get(BASE);candidate=get(a.commit);assert normalize(candidate)==base,'Non-menu bytes/control flow changed'
old=[x for x in base.splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',x)]
new=[x for x in candidate.splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',x)]
assert old==new and len(new)==3,'Guarded light reader roster changed'
controls=0
for old,new in [('return !isAnimatedResidentObjectChunkModel','return isAnimatedResidentObjectChunkModel'),('model.key = -1 - index;\n\t\tmodel.addRotation','model.key = index;\n\t\tmodel.addRotation'),('settingTab == 1 && settingIndex == RendererSettingsPanel.MIDDLE_MOUSE','settingTab == 0 && settingIndex == RendererSettingsPanel.MIDDLE_MOUSE')]:
 altered=once(candidate,old,new)
 try:assert normalize(altered)==base
 except AssertionError:controls+=1
 else:raise AssertionError('Negative mutation accepted')
print(json.dumps(dict(passed=True,baselineCommit=BASE,candidateCommit=a.commit,baselineSha256=sha(base),candidateSha256=sha(candidate),exactRemainingBytesUnchanged=True,guardedReadersUnchanged=3,negativeMutationControls=controls,scope='Three exact UI-only substitutions; no light-reader, geometry, terrain, collision, picking or source-authority edits.')))
