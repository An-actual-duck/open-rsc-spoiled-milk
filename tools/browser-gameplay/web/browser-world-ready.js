/** Real source completion plus nonblocking GPU fence and bounded cadence warmup.
 * Only login, authority/area changes and viewport changes start a load epoch.
 * Animated/live setting repacks after a ready frame do not re-cover gameplay. */
export function createWorldReadiness(gl,{stats,clock=()=>performance.now()}={}) {
  const MIN=7,MAX=30,WINDOW=6;
  let current=null,pending=false,key=null,ready=false,fence=null,fenceAt=0,completeFrames=0,last=0,intervals=[],failed=false,everReady=false,transition=false,ordinary=false,transitionStarted=0,heldFrames=0,sourceDeclarations=0,startUploads=0,startBytes=0;
  function removeFence(){if(fence){gl.deleteSync(fence);fence=null;}}
  function reset(){removeFence();ready=false;completeFrames=0;last=0;intervals=[];}
  function set(phase,reason,extra={}){stats.worldReadiness={phase,reason,blocksWorldInput:phase==='loading'||phase==='streaming'||phase==='error',blocksKeyboard:phase==='loading'||phase==='error'||phase==='streaming'&&!ordinary,completeFrames,required:current?.[3]||0,uploaded:current?.[4]||0,pendingRepacks:current?.[5]||0,epoch:current?.[2]||0,...extra};}
  set('login','Waiting for login');
  function waiting(reason){set(everReady?'streaming':'loading',reason);}
  function beginTransition(soft){
    if(failed)return;
    reset();transition=true;ordinary=soft===true;transitionStarted=clock();heldFrames=sourceDeclarations=0;startUploads=stats.uploadedChunks||0;startBytes=stats.uploadedBytes||0;
    waiting('Receiving area data');
  }
  function held(){
    if(!current||!pending)throw new Error('Held browser frame missing world readiness declaration');
    pending=false;if(failed)return;heldFrames++;
    waiting('Preparing area resources');
  }
  function completedTransition(){
    const previouslyReady=everReady;ready=true;everReady=true;transition=false;removeFence();
    if(transitionStarted&&previouslyReady){
      const samples=stats.worldStreamSamples||(stats.worldStreamSamples=[]);
      samples.push({milliseconds:Math.max(0,clock()-transitionStarted),epoch:current[2],required:current[3],uploaded:current[4],ordinary,heldFrames,sourceDeclarations,newChunkUploads:(stats.uploadedChunks||0)-startUploads,newUploadBytes:(stats.uploadedBytes||0)-startBytes});
      if(samples.length>32)samples.shift();transitionStarted=0;
    }
    transitionStarted=0;set('ready','Ready');
  }
  function declare(input){
    const data=new Int32Array(input);
    if(data.length!==12||data[0]!==1||data[1]<0||data[1]>3||data.some(v=>v<0)||data[4]>data[3]||data[7]>data[6]||data[3]>256||data[5]>data[3]||data[6]>1024||data[8]>1||data[9]<1||data[10]<1||data[11]>2048||data[9]*data[10]>2097152)throw new Error('Invalid browser world readiness declaration');
    current=data;pending=true;
    if(failed)return;
    const next=data[2]+':'+data[9]+':'+data[10];
    if(data[1]===0){reset();key=null;everReady=false;transition=false;ordinary=false;transitionStarted=0;set('login','Login');return;}
    if(data[1]===3){set('ui','Fullscreen game interface');return;}
    if(next!==key){if(!transition)beginTransition(false);else reset();key=next;}
    if(transition)sourceDeclarations++;
    if(data[1]===1){reset();waiting('Waiting for area data');return;}
    if(!ready)waiting(data[4]<data[3]?'Preparing world geometry':data[5]>0?'Preparing materials':'Preparing graphics');
    else set('ready','Ready');
  }
  function presented({world,width,height,textures,sprites,lighting}) {
    if(!current||!pending)throw new Error('Browser frame missing world readiness declaration');pending=false;
    if(current[9]!==width||current[10]!==height)throw new Error('World readiness viewport changed during presentation');
    if(failed)return;
    if(current[1]!==2||!world)return;
    if(ready){set('ready','Ready',{liveUpdatesPending:current[4]<current[3]||current[5]>0});return;}
    const complete=current[8]===1&&current[3]===current[4]&&current[5]===0&&current[6]===current[7]&&current[7]===sprites&&current[11]===textures&&lighting;
    if(!complete){reset();waiting('Preparing world resources');return;}
    if(everReady){completedTransition();return;}
    const now=clock();completeFrames++;if(last)intervals.push(Math.max(.01,now-last));last=now;if(intervals.length>WINDOW)intervals.shift();
    if(!fence){fence=gl.fenceSync(gl.SYNC_GPU_COMMANDS_COMPLETE,0);if(!fence)throw new Error('World readiness GPU fence allocation failed');fenceAt=now;gl.flush();}
    const result=gl.clientWaitSync(fence,0,0);
    if(result===gl.WAIT_FAILED)throw new Error('World readiness GPU fence failed');
    const signaled=result===gl.ALREADY_SIGNALED||result===gl.CONDITION_SATISFIED;
    const stable=intervals.length===WINDOW&&Math.max(...intervals)<=Math.min(...intervals)*3;
    const bounded=completeFrames>=MAX||(completeFrames>=3&&now-fenceAt>=2500);
    if(signaled&&((completeFrames>=MIN&&stable)||bounded)){
      completedTransition();set('ready','Ready',{warmupBoundReached:!stable});
    }else if(!signaled&&now-fenceAt>15000){fail('Graphics could not finish loading. Stop and restart to retry.');}
    else waiting(!signaled?'Finishing graphics': 'Warming up world rendering');
  }
  function fail(reason){failed=true;removeFence();set('error',reason);}
  function dispose(){removeFence();}
  return {declare,presented,beginTransition,held,fail,dispose};
}
