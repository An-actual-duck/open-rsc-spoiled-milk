/** Startup-only private experiment. Ordinary/software URLs cannot override Java. */
export function browserProjectionBenchmarkControls(mode,options) {
 if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
 const value=options.get('projectionreuse');
 if(value!==null&&value!=='on'&&value!=='off')throw new Error('projectionreuse must be on or off');
 const requested=value==='on';
 return {properties:['spoiledmilk.browserProjectionReuse='+requested],requested};
}
