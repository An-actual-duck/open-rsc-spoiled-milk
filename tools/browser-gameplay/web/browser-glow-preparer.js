import {createBrowserGlowRaster} from './browser-glow-raster.js';

const raster = createBrowserGlowRaster();

self.onmessage = ({data: {id, words}}) => {
  try {
    const {rgba, visiblePixels} = raster(words);
    const payload = new Int8Array(4 + rgba.length);
    new DataView(payload.buffer).setInt32(0, visiblePixels);
    payload.set(rgba, 4);
    self.postMessage({id, payload}, [payload.buffer]);
  } catch (error) {
    self.postMessage({id, error: String(error)});
  }
};
