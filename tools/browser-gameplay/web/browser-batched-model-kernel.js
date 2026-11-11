/** Internal arithmetic salvage: integer loop authored for Core commit
 * 85c632c4a054fd7a103ad69559129e0cd9e2dac2, browser-native-model-kernel.js.
 * This protocol borrows a transient Java int[][] proxy and primitive rows;
 * it copies only O(models) references/counts, never vertex data. */
export function batchedModelKernelPolicy(mode, options) {
  if (mode !== 'webgl' || !options.has('benchmark')) return {properties: [], requested: false};
  const value = options.get('modelbatch') || 'off';
  if (!['on', 'off'].includes(value)) throw new Error('Invalid private modelbatch policy');
  return {properties: ['spoiledmilk.browserBatchedModelKernel=' + (value === 'on')], requested: value === 'on'};
}

function transformRow(count, sx, sy, sz, px, py, rx, ry, rz, camera, trig) {
  const [ox,oy,oz,shift,near,rotZ,rotY,rotX] = camera;
  const [sinZ,cosZ,sinY,cosY,sinX,cosX] = trig;
  for (let n = 0; n < count; n++) {
    let x = (sx[n] - ox) | 0, y = (sy[n] - oy) | 0, z = (sz[n] - oz) | 0, tmp;
    if (rotZ !== 0) {
      tmp = ((Math.imul(y, sinZ) + Math.imul(cosZ, x)) | 0) >> 15;
      y = ((Math.imul(y, cosZ) - Math.imul(x, sinZ)) | 0) >> 15; x = tmp;
    }
    if (rotY !== 0) {
      tmp = ((Math.imul(cosY, x) + Math.imul(z, sinY)) | 0) >> 15;
      z = ((Math.imul(cosY, z) - Math.imul(x, sinY)) | 0) >> 15; x = tmp;
    }
    if (rotX !== 0) {
      tmp = ((Math.imul(y, cosX) - Math.imul(sinX, z)) | 0) >> 15;
      z = ((Math.imul(sinX, y) + Math.imul(cosX, z)) | 0) >> 15; y = tmp;
    }
    px[n] = z < near ? x << shift : ((x << shift) / z) | 0;
    py[n] = z < near ? y << shift : ((y << shift) / z) | 0;
    rx[n] = x; ry[n] = y; rz[n] = z;
  }
}

/** Validate the WHOLE packet before any write. Buffer bounds are conservative:
 * bounding unions can reject gaps, but never permit an overlapping writer. */
export function transformModelBatch(size, rows, counts, camera, trig, trigSource = null, stats = null) {
  if (!Number.isInteger(size) || size < 1 || size > 128 || rows == null || rows.length < size * 8
      || !(counts instanceof Int32Array) || counts.length < size
      || !(camera instanceof Int32Array) || camera.length !== 8 || camera[4] <= 0
      || !(trig instanceof Int32Array) || trig.length !== 6
      || trigSource !== null && !(trigSource instanceof Int32Array)) return false;
  const borrowed = new Array(size * 8), lengths = new Int32Array(size), buffers = new Map();
  function touch(a, writer) {
    const lo = a.byteOffset, hi = lo + a.byteLength;
    let state = buffers.get(a.buffer);
    if (!state) { state = {readLo: Infinity,readHi: -Infinity,writeLo: Infinity,writeHi: -Infinity}; buffers.set(a.buffer,state); }
    if (lo < state.writeHi && state.writeLo < hi || writer && lo < state.readHi && state.readLo < hi) return false;
    if (writer) { state.writeLo = Math.min(lo,state.writeLo); state.writeHi = Math.max(hi,state.writeHi); }
    else { state.readLo = Math.min(lo,state.readLo); state.readHi = Math.max(hi,state.readHi); }
    return true;
  }
  touch(counts,false); touch(camera,false); touch(trig,false); if (trigSource !== null) touch(trigSource,false);
  let numeric = 0, vertices = 0;
  for (let row = 0; row < size; row++) {
    const count = counts[row];
    if (count < 0) return false;
    lengths[row] = count;
    if (count === 0) continue;
    numeric++; vertices += count;
    const offset = row * 8;
    for (let n = 0; n < 8; n++) {
      const a = rows[offset + n]; // one synchronous proxy read per active leaf
      if (!(a instanceof Int32Array) || a.length < count || !touch(a,n >= 3)) return false;
      borrowed[offset + n] = a;
    }
  }
  for (let row = 0; row < size; row++) {
    const count = lengths[row]; if (count === 0) continue;
    const at = row * 8;
    transformRow(count,borrowed[at],borrowed[at+1],borrowed[at+2],borrowed[at+3],borrowed[at+4],
      borrowed[at+5],borrowed[at+6],borrowed[at+7],camera,trig);
  }
  if (stats) {
    stats.packets++; stats.models += numeric; stats.vertices += vertices; stats.metadata += size - numeric;
    stats.maxModelsPerPacket = Math.max(stats.maxModelsPerPacket,size);
  }
  return true;
}

export function createBatchedModelKernel() {
  const stats = {attempts: 0,packets: 0,models: 0,vertices: 0,metadata: 0,rejections: 0,maxModelsPerPacket: 0};
  return {stats, native(lib,size,rows,counts,camera,trig,trigSource) {
    stats.attempts++;
    const used = transformModelBatch(size,rows,counts,camera,trig,trigSource,stats);
    if (!used) stats.rejections++;
    return used;
  }};
}
