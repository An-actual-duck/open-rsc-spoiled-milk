/** Private owned-pipeline streaming validation. Allocation/shape changes and
 * post-draw operations must keep their original immediate check, never this gate.
 * Streaming content metadata is tentative until beforeDraw succeeds. On failure
 * the session is poisoned; there is no retry using tentatively committed caches. */
export function createStreamUploadValidation(gl,{stats={},enabled=false,poison=()=>{}}={}) {
  let active=enabled===true,pending=null,failed=false,dirty=false,disposed=false;
  Object.assign(stats,{streamUploadValidationActive:active,streamUploadValidationRequested:active,
    streamUploadChecksDeferred:0,streamUploadValidationChecks:0,streamUploadValidationPassed:0,
    streamUploadValidationFailures:0,streamUploadValidationTransitions:0});
  function assertAlive(){if(disposed||failed)throw new Error('Streaming upload backend unavailable; restart session');}
  function fail(error){if(!failed){failed=true;stats.streamUploadValidationFailures++;stats.backendFailed=true;poison(error);}return error;}
  function deferOrCheck(originalCheck){assertAlive();if(!active)return originalCheck();dirty=true;stats.streamUploadChecksDeferred++;}
  function beforeDraw(){assertAlive();if(!active)return;
    try{stats.streamUploadValidationChecks++;
      if(gl.isContextLost?.())throw new Error('Streaming upload graphics context lost');
      const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('Streaming upload GL error 0x'+error.toString(16));
      dirty=false;stats.streamUploadValidationPassed++;
    }catch(error){throw fail(error);}
  }
  function queue(value){assertAlive();if(typeof value!=='boolean')throw new Error('Invalid streaming upload validation policy');pending=value;stats.streamUploadValidationRequested=value;}
  function frameComplete(){assertAlive();if(dirty)throw fail(new Error('Unvalidated streaming uploads at frame boundary'));
    if(pending!==null){if(active!==pending)stats.streamUploadValidationTransitions++;active=pending;pending=null;stats.streamUploadValidationActive=active;}
  }
  function dispose(){disposed=true;pending=null;dirty=false;}
  return {assertAlive,fail,deferOrCheck,beforeDraw,queue,frameComplete,dispose,get active(){return active;}};
}
/** Adopted ordinary WebGL default; overrides/live controls remain benchmark-only. */
export function browserUploadValidationBenchmarkPolicy(mode,options){
  if(mode!=='webgl')return false;
  if(!options.has('benchmark'))return true;
  const value=options.get('uploadvalidation')||'off';
  if(value!=='on'&&value!=='off')throw new Error('Invalid private uploadvalidation policy');
  return value==='on';
}
