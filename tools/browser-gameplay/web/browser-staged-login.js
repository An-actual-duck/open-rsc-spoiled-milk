/** Functional pre-entry gate. No token, account, destination or source payload is exposed. */
export function createStagedLoginGate({enabled=false}={}){
  const stats={enabled:enabled===true,phase:'idle',owner:0,requiredFrame:0,approved:false};
  function begin(owner,submitted){
    if(!enabled||!Number.isSafeInteger(owner)||owner<1||owner<=stats.owner||!Number.isSafeInteger(submitted)||submitted<0)throw Error('Invalid pending preparation owner');
    Object.assign(stats,{owner,requiredFrame:submitted+1,approved:false,phase:'preparing'});
  }
  function ready(owner,{completedFrame=0,worldReady=false,coverageReady=false,cpuReady=false,blocked=true,error=false}={}){
    if(stats.phase!=='preparing'||owner!==stats.owner||error)return false;
    const ok=completedFrame>=stats.requiredFrame&&worldReady&&coverageReady&&cpuReady&&!blocked;
    if(ok)stats.approved=true;return !!ok;
  }
  function finish(owner,committed,submitted){
    if(owner!==stats.owner)return;
    if(committed){if(stats.phase!=='preparing'||!stats.approved||!Number.isSafeInteger(submitted)||submitted<0)throw Error('Unprepared browser commit');stats.phase='committed';stats.requiredFrame=submitted+1;}
    else stats.phase='cancelled';
  }
  function observe(mode,{worldReady=false,completedFrame=0,blocked=true}={}){if(stats.phase==='committed'&&mode==='GAME'&&worldReady===true&&completedFrame>=stats.requiredFrame&&!blocked)stats.phase='entered';}
  function dispose(){stats.phase='disposed';}
  return {stats,begin,ready,finish,observe,dispose,get blocked(){return stats.phase==='preparing'||stats.phase==='committed';}};
}
