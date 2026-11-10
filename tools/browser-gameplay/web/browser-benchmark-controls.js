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

/** JS-only same-artifact toggle; not a client preference or Java property. */
export function browserOwnedStateBenchmarkPolicy(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return false;
  const policy=options.get('ownedstate')||'off';
  if (!['on','off'].includes(policy)) throw new Error('Invalid private ownedstate policy');
  return policy==='on';
}
