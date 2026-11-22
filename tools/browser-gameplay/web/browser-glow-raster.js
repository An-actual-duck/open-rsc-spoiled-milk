const HEADER_WORDS = 5;
const EMITTER_WORDS = 5;
const MAX_EMITTERS = 512;
const CACHE_ENTRIES = 4;
const f = Math.fround;

function colorByte(value) {
  return Math.min(255, Math.max(0, Math.round(f(value * 255))));
}

export function createBrowserGlowRaster() {
  const cache = new Map();
  let red, green, blue;

  return function raster(recipe) {
    if (!(recipe instanceof Int32Array) || recipe.length <= HEADER_WORDS
        || recipe.length > HEADER_WORDS + EMITTER_WORDS * MAX_EMITTERS
        || (recipe.length - HEADER_WORDS) % EMITTER_WORDS) throw new Error('Invalid glow recipe length');

    const axis = recipe[0];
    const [minX, maxX, minZ, maxZ] = new Float32Array(recipe.buffer, recipe.byteOffset + 4, 4);
    if (axis < 128 || axis > 512 || ![minX, maxX, minZ, maxZ].every(Number.isFinite)
        || minX > maxX || minZ > maxZ) throw new Error('Invalid glow recipe bounds');

    const key = recipe.join(',');
    const saved = cache.get(key);
    if (saved) {
      cache.delete(key);
      cache.set(key, saved);
      return saved;
    }

    const area = axis * axis;
    if (red?.length !== area) {
      red = new Float32Array(area);
      green = new Float32Array(area);
      blue = new Float32Array(area);
    } else {
      red.fill(0);
      green.fill(0);
      blue.fill(0);
    }
    const spanX = Math.max(1, f(maxX - minX));
    const spanZ = Math.max(1, f(maxZ - minZ));
    const invX = f(1 / spanX), invZ = f(1 / spanZ);
    const pixelWidth = f(spanX / axis), pixelHeight = f(spanZ / axis);
    const clip = pixel => Math.max(0, Math.min(axis - 1, pixel));

    // Keep each float operation in the order of the Java mask builder.
    for (let at = HEADER_WORDS; at < recipe.length; at += EMITTER_WORDS) {
      const x = f(recipe[at]), z = f(recipe[at + 1]), radius = f(recipe[at + 2]);
      const color = recipe[at + 3], intensity = recipe[at + 4];
      if (radius < 1 || color < 0 || color > 0xffffff || intensity < 1 || intensity > 255) {
        throw new Error('Invalid glow recipe emitter');
      }
      const lowX = clip(Math.floor(f(f(f(f(x - radius) - minX) * invX) * axis)));
      const highX = clip(Math.ceil(f(f(f(f(x + radius) - minX) * invX) * axis)));
      const lowZ = clip(Math.floor(f(f(f(f(z - radius) - minZ) * invZ) * axis)));
      const highZ = clip(Math.ceil(f(f(f(f(z + radius) - minZ) * invZ) * axis)));
      const sourceRed = f(((color >> 16) & 255) / 255);
      const sourceGreen = f(((color >> 8) & 255) / 255);
      const sourceBlue = f((color & 255) / 255);
      const scale = f(f(intensity / 255) * f(0.34));
      for (let row = lowZ; row <= highZ; row++) {
        const worldZ = f(minZ + f((row + 0.5) * pixelHeight));
        const dz = f(f(worldZ - z) / radius);
        for (let column = lowX; column <= highX; column++) {
          const worldX = f(minX + f((column + 0.5) * pixelWidth));
          const dx = f(f(worldX - x) / radius);
          const distance = f(Math.sqrt(f(f(dx * dx) + f(dz * dz))));
          if (distance >= 1) continue;
          const t = f(1 - distance);
          const falloff = f(f(t * t) * f(3 - f(2 * t)));
          const strength = f(falloff * scale);
          const pixel = row * axis + column;
          red[pixel] += f(sourceRed * strength);
          green[pixel] += f(sourceGreen * strength);
          blue[pixel] += f(sourceBlue * strength);
        }
      }
    }

    const rgba = new Uint8Array(area * 4);
    let visiblePixels = 0;
    for (let pixel = 0; pixel < area; pixel++) {
      const r = colorByte(red[pixel]), g = colorByte(green[pixel]), b = colorByte(blue[pixel]);
      const alpha = Math.max(r, g, b), at = pixel * 4;
      rgba[at] = r;
      rgba[at + 1] = g;
      rgba[at + 2] = b;
      rgba[at + 3] = alpha;
      if (alpha !== 0) visiblePixels++;
    }
    const mask = {axis, rgba, visible: visiblePixels !== 0, visiblePixels, bounds: new Float32Array([minX, minZ, invX, invZ])};
    cache.set(key, mask);
    if (cache.size > CACHE_ENTRIES) cache.delete(cache.keys().next().value);
    return mask;
  };
}
