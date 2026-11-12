"""Same-stage controls; no resource rebinding, startup, or readiness assertion."""
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode

def scene_urls(url,gpu_residency='full',static_scene='off',gpu_budget_mib=256):
 if gpu_residency not in ('full','bounded')or static_scene not in ('on','off')or gpu_budget_mib not in (256,512):raise ValueError('Invalid private scene controls')
 parts=urlsplit(url);base=dict(parse_qsl(parts.query))
 def make(**flags):return urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode({k:v for k,v in {**base,**flags}.items()if v is not None}),parts.fragment))
 common={'wholezone':'on','gpuresidency':gpu_residency,'gpubudget':str(gpu_budget_mib)if gpu_residency=='bounded'else None,'staticscene':static_scene}
 return {'url':make(**common),'urlFullGpu':make(**{**common,'gpuresidency':'full','gpubudget':None}),'urlBoundedGpu':make(**{**common,'gpuresidency':'bounded','gpubudget':str(gpu_budget_mib)}),'urlBounded256':make(**{**common,'gpuresidency':'bounded','gpubudget':'256'}),'urlBounded512':make(**{**common,'gpuresidency':'bounded','gpubudget':'512'}),'urlStaticSceneOn':make(**{**common,'staticscene':'on'}),'urlStaticSceneOff':make(**{**common,'staticscene':'off'}),'sceneControls':{'gpuResidency':gpu_residency,'boundedGpuBudgetMiB':gpu_budget_mib,'staticScene':static_scene,'ordinaryDefaultsChanged':False,'switchScope':'Same exact staged artifacts; explicit bounded256/512 budget, no runtime expansion. Controls apply on private session startup','readiness':'Staging/control declarations only; runtime CPU/catalogue/GPU coverage receipts required'}}
