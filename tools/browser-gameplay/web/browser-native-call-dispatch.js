/** Startup-only interop experiment. Ordinary/software retain Promise dispatch. */
export function browserNativeCallPolicy(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return 'async';
  const value=options.get('nativecalls')||'async';
  if(value!=='async'&&value!=='sync')throw new Error('Invalid private nativecalls policy');
  return value;
}

/** Only for an explicitly audited synchronous native body. True async natives
 * keep their original Promise implementation. No catch-and-forget work may
 * escape this boundary: an unexpected thenable poisons the owning backend. */
export function createSynchronousNativeCall(call,{name='native',before=()=>{},failure=()=>{}}={}) {
  if(typeof call!=='function')throw new TypeError('Native body must be a function');
  return function(...args) {
    try {
      before();
      const result=call(...args);
      if(result!==null&&(typeof result==='object'||typeof result==='function')&&typeof result.then==='function') {
        // A future source change must not leave an unhandled rejection after
        // the synchronous contract error has already stopped the session.
        Promise.resolve(result).catch(()=>{});
        throw new Error('Unexpected asynchronous result from synchronous '+name);
      }
      return result;
    } catch(error) {
      failure(error);
      throw error;
    }
  };
}
