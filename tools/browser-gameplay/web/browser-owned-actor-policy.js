// Private startup-only actor publication. Desktop/ordinary/software defaults stay OFF.
export function browserOwnedActorPixelPolicy(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: false};
  const value = options.get('actorpixels') || 'off';
  if (value !== 'on' && value !== 'off') throw new Error('Invalid private actorpixels policy');
  return {properties: ['spoiledmilk.browserOwnedActorPixels=' + (value === 'on')], requested: value === 'on'};
}
