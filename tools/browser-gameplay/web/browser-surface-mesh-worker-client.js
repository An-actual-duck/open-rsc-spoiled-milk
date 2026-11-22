export function createBrowserSurfaceMeshWorker(runtime, javaVersion, scenery = false, queueSceneryDraw) {
  const worker = new Worker(new URL('./browser-surface-mesh-worker.js', import.meta.url),
    {name: scenery ? 'Spoiled Milk scenery mesher' : 'Spoiled Milk terrain mesher'});
  const stats = {ready: false, completed: 0, gameplay: 0, bytes: 0, inputBytes: 0, busy: 0, refused: 0, error: null};
  if (scenery) stats.wordReplies = 0;
  let pending;
  let closed = false;

  function close(error) {
    if (closed) return;
    closed = true;
    stats.error = error;
    worker.terminate();
    pending?.resolve(null);
    pending = undefined;
  }

  worker.onmessage = ({data}) => {
    if (data.error) {
      close(data.error);
    } else if (data.ready) {
      stats.ready = true;
    } else if (pending) {
      const result = pending.words ? new Int32Array(data.result) : new Int8Array(data.result);
      if (pending.words && result.length >= 19 && result[18] > 0) {
        const draw = data.draw && new Int32Array(data.draw);
        if (!draw || draw[0] !== result[18] || draw[1] !== result[5] || draw[2] !== result[6]
            || draw[3] !== result[12] || draw[4] !== result[13] || !queueSceneryDraw?.(draw)) result[18] = 0;
      }
      if (result.byteLength === 0) stats.refused++;
      else {
        stats.completed++;
        if (pending.gameplay) stats.gameplay++;
        if (pending.words) stats.wordReplies++;
      }
      stats.bytes += result.byteLength;
      const request = pending;
      pending = undefined;
      request.resolve(result);
    }
  };
  worker.onerror = event => close(event.message || 'Surface mesh worker failed');
  worker.postMessage({runtime, javaVersion, scenery});

  return {
    stats,
    build(source, args, packing) {
      if (closed) return Promise.resolve(null);
      if (pending) {
        stats.busy++;
        return Promise.resolve(null);
      }
      const bytes = new Int8Array(source);
      const context = packing === undefined ? null : new Int32Array(packing);
      return new Promise(resolve => {
        stats.inputBytes += bytes.byteLength;
        pending = {resolve, gameplay: args[args.length - 1], words: context !== null};
        try {
          worker.postMessage({bytes, args, words: pending.words, packing: context}, context ? [bytes.buffer, context.buffer] : [bytes.buffer]);
        } catch (error) {
          close(String(error));
        }
      });
    },
    dispose() { close(null); }
  };
}
