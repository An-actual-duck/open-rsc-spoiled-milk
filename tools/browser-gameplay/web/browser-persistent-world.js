/** Render-only private experiment. The catalogue's cells are spatial indices. */
const TILE = 128, WORLD_CELL = 48, OBJECT_CELL = 24;
const VIEW_RADIUS = 112;

export function persistentWorldPolicy(options, {publicAlpha, renderWorker, wholeZone, wholeRam}) {
  const enabled = options.get('persistentscene') === 'on';
  const flyover = options.get('flyover') === 'on';
  const preparedWalk = options.get('preparedwalk') === 'on';
  const terrainLookahead = options.get('terrainlookahead') === 'on';
  const quietWalk = options.get('quietwalk') === 'on';
  const liveWalk = options.get('livewalk') === 'on';
  const workerReplay = options.get('workerreplay') === 'on';
  const walkBuffer = options.get('walkbuffer') === 'on';
  if (walkBuffer && !workerReplay) throw new Error('Walking resources require worker replay');
  if ((enabled || flyover || preparedWalk || terrainLookahead || quietWalk || liveWalk || workerReplay) && (publicAlpha || !enabled || !renderWorker.enabled
      || !wholeZone.enabled || wholeZone.residency !== 'full' || !wholeRam.enabled)) {
    throw new Error('Persistent scene requires a private full-residency WebGL session');
  }
  if ((preparedWalk || terrainLookahead || quietWalk || liveWalk || workerReplay) && flyover) throw new Error('Prepared walking requires live gameplay');
  return {enabled, flyover, preparedWalk, terrainLookahead, quietWalk, liveWalk, workerReplay, walkBuffer};
}

// The existing Java view is R * translate(-eye), with R an orthonormal basis.
// Input is two column-major matrices, followed by the actor projection.
export function cameraWorldPosition(matrices, originX, originZ) {
  const x = matrices[28], y = matrices[29], z = matrices[30];
  return [
    originX - (matrices[16] * x + matrices[17] * y + matrices[18] * z),
    originZ - (matrices[24] * x + matrices[25] * y + matrices[26] * z)
  ];
}

export function moveWorldCamera(matrices, dx, dz) {
  const out = new Float32Array(matrices);
  for (const start of [0, 16]) for (let row = 0; row < 4; row++) {
    out[start + 12 + row] -= dx * matrices[start + row] + dz * matrices[start + 8 + row];
  }
  return out;
}

/** Move toward a confirmed tile without extrapolating past it. */
export function walkingReplayOffset(anchor, destination, elapsedMs) {
  if (!anchor || !destination) return null;
  const dx = destination[0] - anchor[0], dz = destination[1] - anchor[1];
  const distance = Math.max(Math.abs(dx), Math.abs(dz));
  if (distance < 1 || distance > 4 * TILE) return null;
  const amount = Math.max(0, Math.min(1, elapsedMs / Math.max(100, distance * 2)));
  return [dx * amount, dz * amount];
}

export function persistentCells(cameraX, cameraZ, size) {
  const x = cameraX / TILE, z = cameraZ / TILE;
  return {
    minX: Math.floor((x - VIEW_RADIUS) / size),
    maxX: Math.floor((x + VIEW_RADIUS) / size),
    minZ: Math.floor((z - VIEW_RADIUS) / size),
    maxZ: Math.floor((z + VIEW_RADIUS) / size),
  };
}

export function createOverworldFlyover(stats) {
  let start, anchor, lastCell;
  return function frame(matrices, originX, originZ, now) {
    const camera = cameraWorldPosition(matrices, originX, originZ);
    if (!anchor) {
      anchor = camera;
      start = now;
      stats.flyoverBoundaryCrossings = 0;
    }
    // A continuous out-and-back path across several old region boundaries.
    const elapsed = Math.max(0, now - start);
    const targetX = anchor[0] + Math.sin(elapsed / 13000) * 115 * TILE;
    const targetZ = anchor[1] + (1 - Math.cos(elapsed / 13000)) * 11 * TILE;
    const cell = Math.floor(targetX / (WORLD_CELL * TILE));
    if (lastCell !== undefined && cell !== lastCell) stats.flyoverBoundaryCrossings++;
    lastCell = cell;
    stats.flyoverCameraX = Math.round(targetX / TILE);
    stats.flyoverCameraZ = Math.round(targetZ / TILE);
    stats.flyoverFrames = (stats.flyoverFrames || 0) + 1;
    return moveWorldCamera(matrices, targetX - camera[0], targetZ - camera[1]);
  };
}

export function sceneryCells(cameraX, cameraZ) {
  return persistentCells(cameraX, cameraZ, OBJECT_CELL);
}

export function terrainCells(cameraX, cameraZ) {
  return persistentCells(cameraX, cameraZ, WORLD_CELL);
}
