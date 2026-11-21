const MAX_SECTOR_BYTES = 48 * 48 * 11;

export function createBrowserTerrainWorker() {
  const worker = new Worker(new URL(import.meta.url), {type: 'module', name: 'Spoiled Milk terrain'});
  let pending;
  let failure;
  function fail(error) {
    failure = error;
    pending?.reject(error);
    pending = undefined;
    worker.terminate();
  }
  worker.onmessage = ({data}) => {
    const request = pending;
    pending = undefined;
    if (!request) return;
    if (data.error) request.reject(new Error(data.error));
    else request.resolve(new Int8Array(data.bytes));
  };
  worker.onerror = event => fail(new Error(event.message || 'Terrain worker failed'));
  return {
    inflate(compressed, expectedLength) {
      if (failure) return Promise.reject(failure);
      if (pending) return Promise.reject(new Error('Terrain decode is already in progress'));
      const bytes = new Uint8Array(compressed);
      return new Promise((resolve, reject) => {
        pending = {resolve, reject};
        try {
          worker.postMessage({bytes, expectedLength}, [bytes.buffer]);
        } catch (error) {
          fail(error);
        }
      });
    },
    dispose() { fail(new Error('Terrain worker closed')); }
  };
}

async function inflate(bytes, expectedLength) {
  if (!Number.isSafeInteger(expectedLength) || expectedLength < 1 || expectedLength > MAX_SECTOR_BYTES) {
    throw new Error('Invalid terrain sector size');
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate'));
  const reader = stream.getReader();
  const output = new Uint8Array(expectedLength);
  let offset = 0;
  try {
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      if (offset + value.length > expectedLength) throw new Error('Terrain sector exceeds declared size');
      output.set(value, offset);
      offset += value.length;
    }
  } finally {
    await reader.cancel();
    reader.releaseLock();
  }
  if (offset !== expectedLength) throw new Error('Terrain sector ended before its declared size');
  return output.buffer;
}

if (typeof WorkerGlobalScope !== 'undefined' && globalThis instanceof WorkerGlobalScope) {
  globalThis.onmessage = async ({data}) => {
    try {
      const bytes = await inflate(data.bytes, data.expectedLength);
      globalThis.postMessage({bytes}, [bytes]);
    } catch (error) {
      globalThis.postMessage({error: String(error.message || error)});
    }
  };
}
