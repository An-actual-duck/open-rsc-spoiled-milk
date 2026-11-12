/** Experimental clone reuse has no authority/readiness role. Startup-only A/B. */
export function baselineReusePolicy(mode, options) {
  const value = options.get('baselinereuse') || 'off';
  if (!['on', 'off'].includes(value)) throw new Error('Invalid private baseline reuse policy');
  const enabled = value === 'on';
  if (enabled && (mode !== 'webgl' || !options.has('benchmark') || options.get('wholezone') !== 'on'
      || options.get('wholeram') !== 'on' || options.get('staticscene') !== 'on'))
    throw new Error('Baseline reuse requires private whole-RAM/static-scene WebGL admission');
  return {enabled, properties: enabled ? ['spoiledmilk.browserBaselineReuse=true'] : []};
}
