/** Private experiment. Ordinary WebGL and all other modes keep post-world RGB upload. */
export function browserUiPreuploadBenchmarkPolicy(mode,options){
  if(mode!=='webgl'||!options.has('benchmark'))return false;
  const value=options.get('ui-preupload')||'off';
  if(value!=='on'&&value!=='off')throw new Error('Invalid private ui-preupload policy');
  return value==='on';
}
