"""Read-only reviewed native-dispatch inputs and deterministic staged outputs.

This owns provenance/staging only. The allowlisted transformation stays in the
separately reviewed native_dispatch.py dependency; no runtime body is duplicated.
"""
import hashlib
import importlib.util
import re
import subprocess
from pathlib import Path

FILES=(
    'tools/browser-gameplay/web/webgl.js',
    'tools/browser-gameplay/web/browser-native-call-dispatch.js',
    'scripts/browser-gameplay/native_dispatch.py',
)
MARKER="export const BROWSER_NATIVE_CALL_DISPATCH = 'async';"
def digest(value):return hashlib.sha256(value).hexdigest()
def stage_native_dispatch(root,run,web,native_source=None,native_commit=None):
    root=Path(root).resolve();run=Path(run).resolve();web=Path(web).resolve()
    if not web.is_relative_to(run):raise ValueError('Native outputs must be inside the new stage')
    explicit=native_source is not None
    repository=Path(native_source).resolve()if explicit else root
    if explicit and not native_commit:raise ValueError('Review native source requires its exact commit')
    canonical=(web/'webgl.js').read_bytes()
    if not explicit and MARKER not in canonical.decode('utf8'):
        return {'available':False,'canonicalSha256':digest(canonical),'reason':'Canonical backend has no native-dispatch capability marker'}
    commit=native_commit or subprocess.check_output(['git','rev-parse','HEAD'],cwd=repository,text=True).strip()
    if not re.fullmatch('[0-9a-f]{40}',commit):raise ValueError('Native source commit must be exact40hex')
    resolved=subprocess.check_output(['git','rev-parse',commit+'^{commit}'],cwd=repository,text=True).strip()
    if resolved!=commit:raise ValueError('Native source commit did not resolve exactly')
    captured={}
    for relative in FILES:
        entry=subprocess.check_output(['git','ls-tree',commit,'--',relative],cwd=repository,text=True).strip()
        if not entry.startswith(('100644 blob ','100755 blob ')):raise ValueError('Native source must be a regular reviewed file: '+relative)
        value=subprocess.check_output(['git','show',commit+':'+relative],cwd=repository)
        if not explicit and value!=(repository/relative).read_bytes():raise ValueError('Native inputs differ from committed source: '+relative)
        captured[relative]=value
    canonical=captured[FILES[0]]
    if canonical.decode('utf8').count(MARKER)!=1:raise ValueError('Review canonical source lacks its exact async capability marker')
    snapshot=run/'native-dispatch-source';snapshot.mkdir(exist_ok=False)
    for relative,value in captured.items():(snapshot/Path(relative).name).write_bytes(value)
    generator=snapshot/'native_dispatch.py'
    spec=importlib.util.spec_from_file_location('reviewed_native_dispatch',generator);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    generated=module.synchronous_module(canonical.decode('utf8')).encode('utf8')
    (web/'webgl.js').write_bytes(canonical)
    (web/'browser-native-call-dispatch.js').write_bytes(captured[FILES[1]])
    (web/'webgl-sync.js').write_bytes(generated)
    receipt={'available':True,'reviewDependency':explicit,'sourceRepository':str(repository),'sourceCommit':commit,'canonicalSha256':digest(canonical),'generatedSha256':digest(generated),'generatorSha256':digest(captured[FILES[2]]),'inputs':{relative:{'basename':Path(relative).name,'sha256':digest(value)}for relative,value in captured.items()},'runtimeEval':False,'snapshot':str(snapshot)}
    return receipt
