"""Same-stage controls; no resource rebinding, startup, or readiness assertion."""
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode

def scene_urls(url,gpu_residency='full',static_scene='off'):
 if gpu_residency not in ('full','bounded')or static_scene not in ('on','off'):raise ValueError('Invalid private scene controls')
 parts=urlsplit(url);base=dict(parse_qsl(parts.query))
 def make(**flags):return urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode({**base,**flags}),parts.fragment))
 common={'wholezone':'on','gpuresidency':gpu_residency,'staticscene':static_scene}
 return {'url':make(**common),'urlFullGpu':make(**{**common,'gpuresidency':'full'}),'urlBoundedGpu':make(**{**common,'gpuresidency':'bounded'}),'urlStaticSceneOn':make(**{**common,'staticscene':'on'}),'urlStaticSceneOff':make(**{**common,'staticscene':'off'}),'sceneControls':{'gpuResidency':gpu_residency,'staticScene':static_scene,'ordinaryDefaultsChanged':False,'switchScope':'Same exact staged artifacts; controls apply on private session startup, not runtime hot switching','readiness':'Staging/control declarations only; runtime CPU/catalogue/GPU coverage receipts required'}}
