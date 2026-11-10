/** Conservative color-only bounds. Never changes residency or shadow-caster input. */
const FLOAT_EPSILON = 2 ** -23, FLOAT_MAX = 3.4028234663852886e38;
const SCREEN_PADDING = 128;

// Exact allowlist from the maintained production shader. Source-oracle tests guard drift.
export const RESIDENT_COLOR_VERTEX_SHA256 = '7af2917e332f05cc614aa50c40fba1509546e820dceb54900f8f0e56bbdfb3df';
export async function hasResidentPositionContract(source) {
  if (typeof source !== 'string' || source.length !== 1897 || !globalThis.crypto?.subtle) return false;
  try {
    const bytes = new Uint8Array(await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(source)));
    return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('') === RESIDENT_COLOR_VERTEX_SHA256;
  } catch { return false; } // Unknown shader/crypto capability must retain every color draw.
}

/** Owned 48-byte metadata only; includes unused vertices for conservative bounds. */
export function buildChunkColorBounds(vertices) {
  if (!(vertices instanceof Float32Array) || !vertices.length || vertices.length % 28) return null;
  const b = new Float64Array([Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity]);
  for (let at = 0; at < vertices.length; at += 28) {
    for (let axis = 0; axis < 3; axis++) {
      const value = vertices[at + axis];
      if (!Number.isFinite(value)) return null;
      if (value < b[axis]) b[axis] = value;
      if (value > b[axis + 3]) b[axis + 3] = value;
    }
  }
  return b;
}

/** True ONLY when the complete padded AABB is proved outside one side plane.
 * Matrix is the actual column-major world->clip matrix uploaded to the shader.
 * No perspective division, center-point test, far/fog cutoff or near-plane cull.
 */
export function isChunkOutsideColorFrustum(bounds, matrix, width, height, drawX, drawZ, scratch = new Float64Array(18)) {
  if (!bounds || bounds.length !== 6 || !matrix || matrix.length < 16
      || !Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0
      || !scratch || scratch.length < 18) return false;
  for (let n = 0; n < 6; n++) if (!Number.isFinite(bounds[n])) return false;
  for (let n = 0; n < 16; n++) if (!Number.isFinite(matrix[n])) return false;
  const ox = Math.fround(drawX), oz = Math.fround(drawZ);
  if (!Number.isFinite(ox) || !Number.isFinite(oz)) return false;
  const b = scratch;
  b[0] = Math.fround(bounds[0] + ox); b[1] = bounds[1]; b[2] = Math.fround(bounds[2] + oz);
  b[3] = Math.fround(bounds[3] + ox); b[4] = bounds[4]; b[5] = Math.fround(bounds[5] + oz);
  for (let n = 0; n < 6; n++) if (!Number.isFinite(b[n])) return false;
  if (b[0] > b[3] || b[1] > b[4] || b[2] > b[5]) return false;
  // Include offset-add rounding/reassociation, including cancellation at large coordinates.
  const ex = 4 * FLOAT_EPSILON * (Math.max(Math.abs(bounds[0]), Math.abs(bounds[3])) + Math.abs(ox)) + 1e-6;
  const ez = 4 * FLOAT_EPSILON * (Math.max(Math.abs(bounds[2]), Math.abs(bounds[5])) + Math.abs(oz)) + 1e-6;
  b[0] -= ex; b[3] += ex; b[2] -= ez; b[5] += ez;
  for (let row = 0; row < 4; row++) {
    let lo = matrix[12 + row], hi = lo, magnitude = Math.abs(lo);
    for (let axis = 0; axis < 3; axis++) {
      const coefficient = matrix[axis * 4 + row], a = coefficient * b[axis], z = coefficient * b[axis + 3];
      lo += Math.min(a, z); hi += Math.max(a, z);
      magnitude += Math.max(Math.abs(a), Math.abs(z));
    }
    // Overflow/subnormal uncertainty must retain geometry, never incorrectly drop it.
    if (!Number.isFinite(lo) || !Number.isFinite(hi) || magnitude > FLOAT_MAX / 4) return false;
    b[6 + row * 3] = lo; b[7 + row * 3] = hi; b[8 + row * 3] = 128 * FLOAT_EPSILON * magnitude + 1e-6;
  }
  // Retain any camera/near-plane crossing, even if a side test might also reject it.
  if (b[15] <= b[17] || b[12] + b[15] <= b[14] + b[17]) return false;
  const px = 1 + 2 * SCREEN_PADDING / width, py = 1 + 2 * SCREEN_PADDING / height;
  const errorX = b[8] + px * b[17], errorY = b[11] + py * b[17];
  return b[7] + px * b[16] < -errorX || -b[6] + px * b[16] < -errorX
    || b[10] + py * b[16] < -errorY || -b[9] + py * b[16] < -errorY;
}
