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
