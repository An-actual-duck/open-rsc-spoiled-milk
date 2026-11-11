/** Numeric camera kernel only. Borrowed Java arrays are consumed inline and
 * never retained. No pixels, camera rules, culling or rendering are replaced. */
export function nativeModelKernelPolicy(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: false};
  const value = options.get('modelkernel') || 'off';
  if (!['on', 'off'].includes(value)) throw new Error('Invalid private modelkernel policy');
  return {properties: ['spoiledmilk.browserNativeModelKernel=' + (value === 'on')], requested: value === 'on'};
}

function overlaps(a, b) {
  return a.buffer === b.buffer && a.byteOffset < b.byteOffset + b.byteLength
    && b.byteOffset < a.byteOffset + a.byteLength;
}

/** Return false without writes for unsupported storage or parameters. */
export function transformModelVertices(count, sx, sy, sz, px, py, rx, ry, rz,
    ox, oy, oz, shift, near, rotZ, rotY, rotX, sinZ, cosZ, sinY, cosY, sinX, cosX) {
  if (!Number.isInteger(count) || count <= 0 || count > 0x7fffffff
      || !Number.isInteger(near) || near <= 0 || near > 0x7fffffff) return false;
  const arrays = [sx, sy, sz, px, py, rx, ry, rz];
  if (arrays.some(a => !(a instanceof Int32Array) || a.length < count)) return false;
  const scalars = [ox, oy, oz, shift, rotZ, rotY, rotX, sinZ, cosZ, sinY, cosY, sinX, cosX];
  if (scalars.some(v => !Number.isInteger(v) || (v | 0) !== v)) return false;
  for (let n = 3; n < arrays.length; n++)
    for (let m = 0; m < n; m++) if (overlaps(arrays[n], arrays[m])) return false;
  for (let n = 0; n < count; n++) {
    let x = (sx[n] - ox) | 0, y = (sy[n] - oy) | 0, z = (sz[n] - oz) | 0, tmp;
    if (rotZ !== 0) {
      tmp = ((Math.imul(y, sinZ) + Math.imul(cosZ, x)) | 0) >> 15;
      y = ((Math.imul(y, cosZ) - Math.imul(x, sinZ)) | 0) >> 15;
      x = tmp;
    }
    if (rotY !== 0) {
      tmp = ((Math.imul(cosY, x) + Math.imul(z, sinY)) | 0) >> 15;
      z = ((Math.imul(cosY, z) - Math.imul(x, sinY)) | 0) >> 15;
      x = tmp;
    }
    if (rotX !== 0) {
      tmp = ((Math.imul(y, cosX) - Math.imul(sinX, z)) | 0) >> 15;
      z = ((Math.imul(sinX, y) + Math.imul(cosX, z)) | 0) >> 15;
      y = tmp;
    }
    // near>0 ensures the division branch cannot divide by zero. |0 gives
    // Java's truncation toward zero, including MIN_VALUE / -1 overflow.
    px[n] = z < near ? x << shift : ((x << shift) / z) | 0;
    py[n] = z < near ? y << shift : ((y << shift) / z) | 0;
    rx[n] = x; ry[n] = y; rz[n] = z;
  }
  return true;
}

export function createNativeModelKernel() {
  const stats = {attempts: 0, calls: 0, vertices: 0, rejections: 0};
  return {stats, async native(lib, ...args) {
    stats.attempts++;
    const used = transformModelVertices(...args);
    if (used) { stats.calls++; stats.vertices += args[0]; } else stats.rejections++;
    return used;
  }};
}
