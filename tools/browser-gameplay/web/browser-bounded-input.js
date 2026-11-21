/** Producer-side boundary: Java source can advance before the Worker ACK arrives.
 * Exact scope/mutation comparisons avoid hash collisions. Old ready ACKs cannot
 * grant clicks against a newer scene while its displayed image is held.
 */
export function createBoundedInputGate({liveCells=false}={}){
  let scope=null,rows=null,blocked=false,afterFrame=0;
  const fields=[1,2,3,4,5,6,11,12,13,14,15,16,17];
  if(liveCells)fields.push(18);
  function declare(metadata,overrides,submittedFrames){
    if(metadata?.length!==(liveCells?21:18)||!overrides||overrides.length%6||overrides.length>1800||!Number.isSafeInteger(submittedFrames)||submittedFrames<0)throw new Error('Bounded input declaration bounds');
    if(liveCells){
      const interactive=[];
      for(let at=0;at<overrides.length;at+=6){
        if(Math.abs(Math.floor(overrides[at+1]/2)-metadata[5])<=1&&Math.abs(Math.floor(overrides[at+2]/2)-metadata[6])<=1){
          for(let n=0;n<6;n++)interactive.push(overrides[at+n]);
        }
      }
      overrides=interactive;
    }
    const next=fields.map(i=>metadata[i]);
    const changed=!scope||next.some((n,i)=>n!==scope[i])||rows.length!==overrides.length||rows.some((n,i)=>n!==overrides[i]);
    if(changed){scope=next;rows=Int32Array.from(overrides);blocked=true;afterFrame=submittedFrames+1;}
    return changed;
  }
  function acknowledge(ack,{coverageReady,worldReady}){
    if(blocked&&ack?.frameId>=afterFrame&&coverageReady===true&&worldReady===true)blocked=false;
    return !blocked;
  }
  return {declare,acknowledge,get blocked(){return blocked;},get afterFrame(){return afterFrame;}};
}
