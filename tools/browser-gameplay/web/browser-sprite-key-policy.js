// Experimental startup-only cache representation. No ordinary/software override.
export function browserSpriteKeyBenchmarkControls(mode,options){
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
  const value=options.get('spritekeylimbs')||'off';
  if(value!=='on'&&value!=='off')throw new Error('Invalid private spritekeylimbs policy');
  return {properties:['spoiledmilk.browserSpriteKeyLimbs='+(value==='on')],requested:value==='on'};
}
