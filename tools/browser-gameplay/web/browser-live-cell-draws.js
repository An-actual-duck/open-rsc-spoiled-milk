/** A live frame owns its local draws and ready catalog cells. Pending outer
 * cells add no draw commands until their upload has published. */
export function createLiveCellDraws(catalog, resident, stats) {
  const cells = new Map(catalog.chunks.map(cell => [cell.id, cell]));

  function plan(draws, centerX, centerY, lookahead) {
    const required = [], optional = [];
    for (let at = 0; at < draws.length; at += 8) {
      const cell = cells.get(draws[at]);
      if (!cell) continue;
      const scale = cell.role === 0 ? 1 : 2;
      const x = Math.floor(cell.cellX / scale), y = Math.floor(cell.cellY / scale);
      const protectsInteractiveCoverage = Math.abs(x - centerX) <= 1 && Math.abs(y - centerY) <= 1;
      (protectsInteractiveCoverage ? required : optional).push(cell.id);
    }
    return {required, optional: optional.concat(lookahead)};
  }

  function published(draws) {
    let output = null, retained = 0, missing = 0;
    for (let at = 0; at < draws.length; at += 8) {
      if (cells.has(draws[at]) && !resident.has(draws[at])) {
        if (!output) {
          output = new Int32Array(draws.length);
          output.set(draws.subarray(0, at));
        }
        missing++;
        continue;
      }
      if (output) output.set(draws.subarray(at, at + 8), retained);
      retained += 8;
    }
    stats.liveDraws = retained / 8;
    stats.pendingOuterDraws = missing;
    if (missing) stats.framesWithPendingOuterDraws = (stats.framesWithPendingOuterDraws || 0) + 1;
    return output ? output.subarray(0, retained) : draws;
  }

  return {plan, published};
}
