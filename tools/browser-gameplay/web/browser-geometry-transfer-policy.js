/** Startup-only experiment; ordinary/software sessions keep the verified path. */
export function browserGeometryTransferPolicy(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: null};
  const value=options.get('geometrytransfer')||'off';
  if(value!=='on'&&value!=='off')throw new Error('Invalid private geometrytransfer policy');
  const enabled=value==='on';
  return {properties:['spoiledmilk.browserGeometryTransferReuse='+enabled],requested:enabled};
}
