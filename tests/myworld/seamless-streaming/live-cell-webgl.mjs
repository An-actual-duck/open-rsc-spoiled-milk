import {createBoundedResidency} from './browser-bounded-residency.js';
import {createBoundedChunkUpload} from './browser-bounded-upload.js';
import {createLiveCellDraws} from './browser-live-cell-draws.js';
import {createWholeZoneSelection, digestWholeZoneBytes, fetchWholeZoneBytes} from './browser-whole-zone.js';
import {GEOMETRY_ATTRIBUTES} from './browser-geometry-layout.js';

const proof = window.liveCellProof = {done: false, passed: false};
const check = (condition, problem) => { if (!condition) throw new Error(problem); };
const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve));
const cellResponse = await fetch('./terrain-cells.json');
const catalog = await cellResponse.json();
const [west, middle, east] = catalog.chunks;
let releaseEast;
const eastDelay = new Promise(resolve => { releaseEast = resolve; });
let eastRequested = false;
const canvas = document.querySelector('canvas');
const gl = canvas.getContext('webgl2', {antialias: false});
const resident = new Map(), stats = {};
let gpuBytes = 0, pending = 0, error;

function makeProgram() {
  const program = gl.createProgram();
  for (const [type, body] of [[gl.VERTEX_SHADER, `#version 300 es
    in vec3 aPosition;
    uniform vec2 uOffset;
    uniform float uView;
    void main() {
      vec2 p=(aPosition.xz+uOffset)/128.0-vec2(190.0+uView,576.0);
      gl_Position=vec4(p/vec2(74.0,24.0)-1.0,0.0,1.0);
    }`], [gl.FRAGMENT_SHADER, `#version 300 es
    precision highp float;
    uniform vec3 uTint;
    out vec4 color;
    void main() { color=vec4(uTint,1.0); }`]]) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, body);
    gl.compileShader(shader);
    check(gl.getShaderParameter(shader, gl.COMPILE_STATUS), gl.getShaderInfoLog(shader));
    gl.attachShader(program, shader);
    gl.deleteShader(shader);
  }
  gl.linkProgram(program);
  check(gl.getProgramParameter(program, gl.LINK_STATUS), gl.getProgramInfoLog(program));
  return program;
}

function disposeCell(id) {
  const cell = resident.get(id);
  gl.deleteVertexArray(cell.vao);
  gl.deleteBuffer(cell.vbo);
  gl.deleteBuffer(cell.ibo);
  resident.delete(id);
  gpuBytes -= cell.bytes;
}

try {
  check(gl, 'Actual WebGL2 unavailable');
  check(catalog.chunks.length === 3 && [west, middle, east].every((cell, index) =>
    cell.cellX === 4 + index && cell.cellY === 12 && cell.plane === 0 && cell.role === 0), 'Unexpected bake corridor');
  const program = makeProgram(), uniforms = ['uOffset', 'uView', 'uTint'].map(name => gl.getUniformLocation(program, name));
  const verifyGpu = () => check(gl.getError() === gl.NO_ERROR, 'WebGL2 error');
  const residency = createBoundedResidency({catalog, stats, resident, capacityBytes: 8 * 1024 * 1024,
    optionalReserveBytes: 0, retainRelevantJobs: true, totalBytes: () => gpuBytes,
    totalCount: () => resident.size + pending, evict: disposeCell,
    async prepare(cell, signal) {
      if (cell.id === east.id) {
        eastRequested = true;
        await eastDelay;
      }
      const url = new URL(cell.path, location.href);
      const bytes = await fetchWholeZoneBytes(url, cell.bytes, {signal});
      check(await digestWholeZoneBytes(bytes) === cell.sha256, 'Baked source hash mismatch');
      return bytes;
    },
    createUpload(cell, buffer) {
      return createBoundedChunkUpload(gl, {chunk: cell, buffer, program, attributes: GEOMETRY_ATTRIBUTES, check: verifyGpu,
        reserve: bytes => residency.reclaim(bytes, 1),
        allocated: bytes => { gpuBytes += bytes; pending++; },
        released: bytes => { gpuBytes -= bytes; pending--; },
        publish: value => { pending--; resident.set(cell.id, value); }});
    },
    onError: problem => { error = problem; }});
  const selection = createWholeZoneSelection(catalog, {}, {liveCells: true});
  const live = createLiveCellDraws(catalog, resident, stats);
  const none = new Int32Array();
  let sceneRevision = 1, rendered = 0;
  function frame(center, view, drawCenter = center) {
    if (error) throw error;
    selection.frame(Int32Array.from([1, 1, 0, 0, 0, center, 12, 2, 127, 127, 127,
      ...catalog.settings, sceneRevision, 0, 0, 1, drawCenter, 12]), none);
    const draws = selection.merge(none, 0);
    const {required, optional} = live.plan(draws, center, 12, []);
    if (!residency.select(required, optional, selection.generationKey)) return null;
    const published = live.published(draws);
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(program);
    gl.uniform1f(uniforms[1], view);
    const ids = [];
    for (let at = 0; at < published.length; at += 8) {
      const id = published[at], chunk = resident.get(id);
      ids.push(id);
      gl.uniform2f(uniforms[0], published[at + 1], published[at + 2]);
      gl.uniform3f(uniforms[2], id === west.id ? 1 : 0, id === middle.id ? 1 : 0, id === east.id ? 1 : 0);
      gl.bindVertexArray(chunk.vao);
      for (let n = 0; n < chunk.batches.length; n += 5) {
        gl.drawElements(gl.TRIANGLES, chunk.batches[n + 2], gl.UNSIGNED_INT, chunk.batches[n + 1] * 4);
      }
    }
    verifyGpu();
    rendered++;
    return ids;
  }

  function pixel(x, y) {
    const color = new Uint8Array(4);
    gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, color);
    verifyGpu();
    return color;
  }

  let ids;
  for (let i = 0; i < 240; i++) {
    ids = frame(4, 0);
    if (ids && eastRequested) break;
    await nextFrame();
  }
  check(eastRequested && ids?.join() === [west.id, middle.id].join(), 'Protected cells did not publish while east waits');
  const generation = stats.generation, before = rendered;
  sceneRevision++;
  for (let i = 0; i < 8; i++) {
    check(frame(4, i, 5)?.length === 2, 'Outer fetch interrupted a live draw');
    check(pixel(32, 60)[0] > 0 && pixel(120, 60)[1] > 0 && pixel(250, 60)[2] === 0, 'Live draw was blank or showed unpublished geometry');
    await nextFrame();
  }
  check(rendered - before === 8 && stats.generation === generation, 'Terrain revision restarted the active view');
  releaseEast();
  for (let i = 0; i < 240 && !resident.has(east.id); i++) {
    check(frame(4, 0, 5)?.length === 2, 'Current cells vanished during an upload slice');
    await nextFrame();
  }
  check(resident.has(east.id), 'East cell did not become resident');
  check(frame(5, 0)?.join() === [west.id, middle.id, east.id].join(), 'Ready cell did not join the next live draw');
  check(pixel(250, 60)[2] > 0, 'East cell never produced actual pixels');
  check(frame(3, 0)?.join() === [west.id, middle.id].join(), 'Reversal used stale cell selection');
  check(pixel(250, 60)[2] === 0, 'Distant cached cell remained drawable');
  Object.assign(proof, {passed: true, rendered, framesWhileEastDelayed: 8, publications: stats.uploads,
    generation, pending: stats.pendingBytes, source: 'independently baked signed-map ground cells'});
  residency.dispose();
  for (const id of [...resident.keys()]) disposeCell(id);
  gl.deleteProgram(program);
} catch (error) {
  proof.error = error.stack || String(error);
} finally {
  proof.done = true;
}
