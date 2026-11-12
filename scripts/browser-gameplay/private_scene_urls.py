"""Same-stage controls; no resource rebinding, startup, or readiness assertion."""
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode

def scene_urls(url,gpu_residency='full',static_scene='off',gpu_budget_mib=256,geometry_format='legacy',compact_available=False):
 if geometry_format not in ('legacy','compact')or geometry_format=='compact'and not compact_available:raise ValueError('Compact geometry must be staged explicitly')
 if gpu_residency not in ('full','bounded')or static_scene not in ('on','off')or gpu_budget_mib not in (256,512):raise ValueError('Invalid private scene controls')
 parts=urlsplit(url);base=dict(parse_qsl(parts.query))
 def make(**flags):
  combined={**base,**flags}
  if combined.get('clonetemplates')=='on'and(combined.get('staticscene')!='on'or combined.get('wholeram')!='on'):combined['clonetemplates']='off'
  if combined.get('baselinereuse')=='on'and(combined.get('staticscene')!='on'or combined.get('wholeram')!='on'):combined['baselinereuse']='off'
  return urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode({k:v for k,v in combined.items()if v is not None}),parts.fragment))
 common={'wholezone':'on','gpuresidency':gpu_residency,'gpubudget':str(gpu_budget_mib)if gpu_residency=='bounded'else None,'staticscene':static_scene,**({'geometryformat':geometry_format}if compact_available else{})}
 return {'urlGeometryLegacy':make(**{**common,'geometryformat':'legacy'})if compact_available else make(**common),'urlGeometryCompact':make(**{**common,'geometryformat':'compact'})if compact_available else None,'url':make(**common),'urlFullGpu':make(**{**common,'gpuresidency':'full','gpubudget':None}),'urlBoundedGpu':make(**{**common,'gpuresidency':'bounded','gpubudget':str(gpu_budget_mib)}),'urlBounded256':make(**{**common,'gpuresidency':'bounded','gpubudget':'256'}),'urlBounded512':make(**{**common,'gpuresidency':'bounded','gpubudget':'512'}),'urlStaticSceneOn':make(**{**common,'staticscene':'on'}),'urlStaticSceneOff':make(**{**common,'staticscene':'off'}),'sceneControls':{'gpuResidency':gpu_residency,'boundedGpuBudgetMiB':gpu_budget_mib,'staticScene':static_scene,'geometryFormat':geometry_format,'compactGeometryStaged':compact_available,'ordinaryDefaultsChanged':False,'switchScope':'Same exact staged artifacts; explicit bounded256/512 budget, no runtime expansion. Controls apply on private session startup','readiness':'Staging/control declarations only; runtime CPU/catalogue/GPU coverage receipts required'}}


def baseline_reuse_urls(url):
 """A/B only clone reuse on the same exact RAM/geometry/source stage."""
 parts=urlsplit(url);base=dict(parse_qsl(parts.query))
 def make(value):
  return urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode({**base,'baselinereuse':value,'clonetemplates':'off'}),parts.fragment))
 allowed=base.get('wholeram')=='on'and base.get('staticscene')=='on'
 return {'urlBaselineReuseOn':make('on')if allowed else None,'urlBaselineReuseOff':make('off')}


def clone_template_urls(url):
 """Same-stage exact clone recipe controls; previous baseline trial stays off."""
 parts=urlsplit(url);base=dict(parse_qsl(parts.query))
 def make(value):return urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode({**base,'baselinereuse':'off','clonetemplates':value}),parts.fragment))
 allowed=base.get('wholeram')=='on'and base.get('staticscene')=='on'
 return {'urlCloneTemplatesOn':make('on')if allowed else None,'urlCloneTemplatesOff':make('off')}
