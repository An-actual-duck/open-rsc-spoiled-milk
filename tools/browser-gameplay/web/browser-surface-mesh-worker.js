let loading, scenery;
const clientRoot = '/app' + new URL('./', self.location.href).pathname;

self.onmessage = async ({data}) => {
  try {
    if (!loading) {
      const {runtime, javaVersion} = data;
      scenery = data.scenery === true;
      if (!['4.3', '5.0'].includes(runtime) || ![8, 11, 17].includes(javaVersion)) throw new Error('Unsupported mesher runtime');
      loading = (async () => {
        importScripts(`https://cjrtnc.leaningtech.com/${runtime}/loader.js`);
        await cheerpjInit({version: javaVersion, javaProperties: [
          'openrsc.worldBuilderInstalledClientProfile=' + clientRoot + 'world-builder-configs/installed-client.json'
        ]});
        const library = await cheerpjRunLibrary(clientRoot + 'browser-spike.jar:' + clientRoot + 'client.jar');
        return await library.orsc.browser.BrowserSurfaceMeshWorker;
      })();
      await loading;
      self.postMessage({ready: true});
      return;
    }
    const builder = await loading;
    if (scenery && data.words) {
      const result = new Int32Array(await builder.buildSceneryWords(clientRoot + 'Cache', data.bytes, data.packing));
      if (result.length >= 19 && result[18] > 0) {
        const draw = new Int32Array(await builder.takeSceneryDraw());
        self.postMessage({result: result.buffer, draw: draw.buffer}, [result.buffer, draw.buffer]);
      } else {
        self.postMessage({result: result.buffer}, [result.buffer]);
      }
      return;
    }
    const result = new Int8Array(scenery
      ? await builder.buildScenery(clientRoot + 'Cache', data.bytes)
      : await builder.build(clientRoot + 'Cache', data.bytes, ...data.args));
    self.postMessage({result: result.buffer}, [result.buffer]);
  } catch (error) {
    self.postMessage({error: String(error.stack || error)});
  }
};
