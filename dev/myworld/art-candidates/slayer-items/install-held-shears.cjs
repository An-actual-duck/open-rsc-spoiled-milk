// Install approved-for-testing candidate exactly; preserve the previous cropped set.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const art=path.join(__dirname,'held-shears'),dest=path.resolve(__dirname,'../../assets/sprites/equipment/shears/numbered'),backup=path.join(art,'previous-numbered');
fs.mkdirSync(backup,{recursive:true});
for(let n=0;n<15;n++){
 const name=String(n).padStart(2,'0')+'.png',old=path.join(dest,name),saved=path.join(backup,name),src=path.join(art,'full-canvas/frame-'+name);
 const data=fs.readFileSync(src);assert.equal(data.readUInt32BE(16),64);assert.equal(data.readUInt32BE(20),102);
 if(!fs.existsSync(saved))fs.copyFileSync(old,saved);
 fs.copyFileSync(src,old);
}
console.log('PASS: 15 full-canvas shears installed without altering artwork; previous set preserved');
