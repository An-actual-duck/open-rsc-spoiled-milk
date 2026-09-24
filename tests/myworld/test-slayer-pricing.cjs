const fs=require('fs'),assert=require('assert/strict');
const source=JSON.parse(fs.readFileSync('tools/generators/item-overrides/51-slayer-economy.json')).items;
const overrides=new Map(JSON.parse(fs.readFileSync('server/conf/server/defs/ItemDefsMyWorld.json')).items.map(x=>[x.id,x]));
for(const x of source){assert.deepEqual(Object.keys(x).sort(),['basePrice','id']);assert.equal(overrides.get(x.id).basePrice,x.basePrice);assert.ok(x.basePrice>0);}
const prices=new Map(source.map(x=>[x.id,x.basePrice]));
for(const [id,value] of [[3349,40000],[3350,600000],[3351,450000],[3352,300000],[3353,150000],[3354,150000],[3355,100000]])assert.equal(prices.get(id),value);
for(let i=0;i<5;i++){let id=3318+i*3;assert.equal(prices.get(id),prices.get(id+2)*3);assert.equal(prices.get(id+1),prices.get(id+2)*2);}
for(let id=3341;id<=3348;id++)assert.ok(Math.floor(prices.get(id)*.6)<1000,'single component auto-alchemy threshold '+id);
assert.ok(prices.get(3348)+10*prices.get(3347)+50*prices.get(3340)<prices.get(3350));
assert.ok(500*prices.get(3339)<prices.get(3349));
assert.ok(!prices.has(3334),'do not revive retired Banshee hide');
console.log('PASS: server prices, equipment approvals, dose scaling and modest component values');
