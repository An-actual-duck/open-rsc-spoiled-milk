export function createBrowserSurfaceMeshWorker(runtime, javaVersion, scenery = false) {
  const worker = new Worker(new URL('./browser-surface-mesh-worker.js', import.meta.url),
    {name: scenery ? 'Spoiled Milk scenery mesher' : 'Spoiled Milk terrain mesher'});
  const stats = {ready: false, completed: 0, gameplay: 0, bytes: 0, inputBytes: 0, busy: 0, refused: 0, error: null};
  stats.wordReplies = 0;
  if (!scenery) {
    stats.gameplayBytes = 0;
    stats.gameplayWordReplies = 0;
  }
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
      if (result.byteLength === 0) stats.refused++;
      else {
        stats.completed++;
        if (pending.gameplay) {
          stats.gameplay++;
          stats.gameplayBytes += result.byteLength;
          if (pending.words) stats.gameplayWordReplies++;
        }
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
    build(source, args, words = false) {
      if (closed) return Promise.resolve(null);
      if (pending) {
        stats.busy++;
        return Promise.resolve(null);
      }
      const bytes = new Int8Array(source);
      return new Promise(resolve => {
        stats.inputBytes += bytes.byteLength;
        pending = {resolve, gameplay: args[args.length - 1] === true, words};
        try {
          worker.postMessage({bytes, args, words}, [bytes.buffer]);
        } catch (error) {
          close(String(error));
        }
      });
    },
    dispose() { close(null); }
  };
}
