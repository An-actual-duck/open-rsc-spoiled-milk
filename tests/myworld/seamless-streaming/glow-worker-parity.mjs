import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {createBrowserGlowRaster} from '../../../tools/browser-gameplay/web/browser-glow-raster.js';

const raster = createBrowserGlowRaster();
let checks = 0;
for (const path of process.argv.slice(2)) {
  for (const row of readFileSync(path, 'utf8').trim().split('\n')) {
    const [recipeWords, expectedBounds, expected] = row.split('\t');
    const recipe = Int32Array.from(JSON.parse(recipeWords));
    const mask = raster(recipe);
    assert.equal(mask.axis, recipe[0]);
    assert.deepEqual([...new Int32Array(mask.bounds.buffer)], JSON.parse(expectedBounds));
    if (expected === 'empty') {
      assert.equal(mask.visible, false);
      assert.equal(mask.rgba.some(value => value !== 0), false);
    } else {
      assert.equal(mask.visible, true);
      assert.equal(createHash('sha256').update(mask.rgba).digest('hex'), expected);
    }
    assert.equal(raster(recipe.slice()), mask);
    checks++;
  }
}
assert.ok(checks > 0);
assert.throws(() => raster(new Int32Array(9)), /length/);
assert.throws(() => raster(new Int32Array(10)), /bounds/);
console.log(`PASS ${checks} glow masks matched the Java builder's bytes and bounds`);
