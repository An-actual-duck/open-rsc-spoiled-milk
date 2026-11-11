/** Read-only row borrowing except the deliberate probe writes below.
 * No array/proxy is saved in a closure, result or global state. */
export function createNestedArrayProbe() {
  const stats = {calls: 0, accepted: 0, rejected: 0, primitiveWrites: 0, indexReads: 0};
  function inspect(rows, stage) {
    stats.calls++;
    if (rows == null || !Number.isInteger(stage) || stage < 0 || stage > 4 || rows.length !== 7) {
      stats.rejected++; return false;
    }
    // Access each outer proxy slot only once; the leaf must be the borrowed
    // Int32Array itself, not an async Java method or a copied JS numeric array.
    const a = rows[0], b = rows[1], c = rows[2], d = rows[3], e = rows[4], f = rows[5], g = rows[6];
    stats.indexReads += 7;
    if (stage === 4) {
      const ok = [a,b,c,d,e,f,g].every(x => x === null);
      stats[ok ? 'accepted' : 'rejected']++; return ok;
    }
    const ok = a instanceof Int32Array && a.length === (stage === 0 ? 3 : 2)
      && b instanceof Int32Array && b.length === 3
      && (stage < 2 ? c === null : c instanceof Int32Array && c.length === 1)
      && d instanceof Int32Array && d.length === 3
      && e instanceof Int32Array && e.length === 0
      && f instanceof Int32Array && f.length === 1
      && g instanceof Int32Array && g.length === 64
      && (stage < 2 ? b === d : b !== d);
    if (!ok) { stats.rejected++; return false; }
    // All type/identity/value checks happen before the first write.
    if (stage === 0) {
      if (a[0] !== 1 || a[1] !== 2 || b[0] !== -1 || b[1] !== 2147483647
          || b[2] !== -2147483648 || f[0] !== 42 || g[63] !== 0) { stats.rejected++; return false; }
      a[1] = 0x12345678; b[0] = -123456789; g[63] = -7654321; stats.primitiveWrites += 3;
    } else if (stage === 1) {
      if (a[0] !== 9 || a[1] !== 8 || b[0] !== -123456789) { stats.rejected++; return false; }
      a[0] = 777; stats.primitiveWrites++;
    } else if (stage === 2) {
      if (c[0] !== 7 || b[0] !== 6 || d[0] !== -123456789) { stats.rejected++; return false; }
      c[0] = 888; b[2] = 999; stats.primitiveWrites += 2;
    } else { stats.rejected++; return false; }
    stats.accepted++; return true;
  }
  return {stats, inspect};
}
