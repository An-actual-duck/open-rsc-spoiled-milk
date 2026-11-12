/** Private startup A/B; no model, scene or readiness authority. */
export function cloneTemplatePolicy(mode,options){
 const value=options.get('clonetemplates')||'off';
 if(!['on','off'].includes(value))throw Error('Invalid private clone-template policy');
 const enabled=value==='on';
 if(enabled&&(mode!=='webgl'||!options.has('benchmark')||options.get('wholezone')!=='on'||options.get('wholeram')!=='on'||options.get('staticscene')!=='on'))throw Error('Clone templates require private whole-RAM/static-scene WebGL admission');
 return {enabled,properties:enabled?['spoiledmilk.browserCloneTemplates=true']:[]};
}
