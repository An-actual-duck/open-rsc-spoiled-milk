/** Passive, bounded initial-world progress. Never a readiness or timeout policy. */
const LABELS=Object.freeze({1:'Checking world data',2:'Reading map data',3:'Checking map files',4:'Reading world data',5:'Validating world data',6:'Preparing graphics',7:'World preparation failed'});
const ERRORS=Object.freeze({1:'WORLD-MEMORY',2:'WORLD-DATA',3:'WORLD-RENDER'});
export function createWorldPreparationProgress(){
  const stats={version:1,accepted:0,rejected:0,latest:null,failed:false};
  function accept(input){
    const reject=()=>{stats.rejected=Math.min(2147483647,stats.rejected+1);return false;};
    if(!input||input.length!==8)return reject();const v=Array.from(input);
    if(v.some(x=>!Number.isInteger(x)||x<0||x>2147483647)||v[0]!==1||v[1]<1||v[2]<1||!LABELS[v[3]]||v[4]>v[5]||v[5]>536870912||v[7]>3||(v[3]===7)!==(v[7]>0))return reject();
    if([1,5,6,7].includes(v[3])&&(v[4]!==0||v[5]!==0)||v[3]===3&&v[5]!==1797)return reject();
    const old=stats.latest;
    if(stats.failed||old&&(v[1]<old.owner||v[1]===old.owner&&(v[2]<=old.sequence||v[3]<old.stage||v[6]<old.elapsedMillis||v[3]===old.stage&&(v[5]!==old.total||v[4]<old.completed)))||(!old||v[1]>old.owner)&&v[3]!==1)return reject();
    stats.latest=Object.freeze({owner:v[1],sequence:v[2],stage:v[3],label:LABELS[v[3]],completed:v[4],total:v[5],elapsedMillis:v[6],failureCode:ERRORS[v[7]]||null});
    stats.failed=v[3]===7;stats.accepted=Math.min(2147483647,stats.accepted+1);return true;
  }
  return {stats,accept};
}
export function worldPreparationMessage(progress,state){
  if(progress?.failureCode)return 'World preparation failed ('+progress.failureCode+'). Restart the game to retry.';
  if(progress&&progress.stage<6){
    if(progress.stage===3)return progress.label+' ('+progress.completed+' / '+progress.total+')';
    if(progress.total>0)return progress.label+' · '+(progress.completed/1000000).toFixed(1)+' of '+(progress.total/1000000).toFixed(1)+' MB read';
    return progress.label+'…';
  }
  if(state?.required>state?.uploaded)return 'Preparing terrain and objects ('+state.uploaded+' / '+state.required+')';
  if(state?.pendingRepacks>0)return 'Preparing world materials…';
  if(state?.reason==='Warming up world rendering')return 'Warming up graphics…';
  if(state?.reason==='Finishing graphics')return 'Finishing graphics…';
  return progress?.stage===6?'Preparing graphics…':'Receiving area data…';
}
