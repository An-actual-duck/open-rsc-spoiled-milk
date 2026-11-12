/** Freeze actual browser module bytes and relative dependencies for Node oracles.
 * Adds no alternate implementation; dependency hashes accompany source receipts. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
export function frozenBrowserModules(directory){
  const root=path.resolve(directory),urls=new Map(),hashes={};
  function url(name){
    if(!/^[A-Za-z0-9_-]+\.js$/.test(name))throw new Error('Unsafe frozen browser dependency');
    if(urls.has(name))return urls.get(name);
    const source=fs.readFileSync(path.join(root,name),'utf8');hashes[name]=crypto.createHash('sha256').update(source).digest('hex');
    const rewritten=source.replace(/from\s+(['"])(\.\/[^'"]+)\1/g,(_,q,relative)=>'from '+q+url(relative.slice(2))+q);
    const result='data:text/javascript;base64,'+Buffer.from(rewritten).toString('base64');urls.set(name,result);return result;
  }
  return {url,hashes};
}
