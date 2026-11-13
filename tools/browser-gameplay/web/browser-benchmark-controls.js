// Private same-artifact controls. Ordinary/software sessions never receive overrides.
export function browserPreparationBenchmarkControls(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: null};
  const policies = ['frameprep', 'animated'].map(name => {
    const value = options.get(name) || 'on';
    if (value !== 'on' && value !== 'off') throw new Error(`Invalid private ${name} policy`);
    return value !== 'off';
  });
  return {
    properties: ['spoiledmilk.browserFramePreparation=' + policies[0],
      'spoiledmilk.browserAnimatedBufferReuse=' + policies[1]],
    requested: {framePreparation: policies[0], animatedBufferReuse: policies[1]},
  };
}

/** Verified ordinary WebGL default; only benchmark URLs expose an override. */
export function browserOwnedStateBenchmarkPolicy(mode, options) {
  if (mode !== 'webgl') return false;
  if (!options.has('benchmark')) return true;
  const policy=options.get('ownedstate')||'off';
  if (!['on','off'].includes(policy)) throw new Error('Invalid private ownedstate policy');
  return policy==='on';
}

/** Independent startup-only CPU experiments; no ordinary/software overrides. */
export function browserModelBenchmarkControls(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: null};
  const read=(name,fallback)=>{const value=options.get(name)||fallback;if(value!=='on'&&value!=='off')throw new Error(`Invalid private ${name} policy`);return value==='on';};
  const modelPickBounds=read('modelpickbounds','off'),haloMatchReuse=read('halomatch','on');
  return {properties:['spoiledmilk.browserModelPickBounds='+modelPickBounds,'spoiledmilk.presentationHaloMatchReuse='+haloMatchReuse],requested:{modelPickBounds,haloMatchReuse}};
}

export function browserVertexOnlyPolicy(mode,options){
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],enabled:false};
  const value=options.get('vertexonly')||'off';if(!['on','off'].includes(value))throw new Error('Invalid private vertexonly policy');
  return {properties:['spoiledmilk.browserVertexOnlyUpdates='+(value==='on')],enabled:value==='on'};
}

export function browserFinalModelProfilePolicy(mode,options){
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],enabled:false};
  const value=options.get('finalprofile')||'off';if(!['on','off'].includes(value))throw new Error('Invalid private finalprofile policy');
  return {properties:['spoiledmilk.browserFinalModelProfile='+(value==='on')],enabled:value==='on'};
}
