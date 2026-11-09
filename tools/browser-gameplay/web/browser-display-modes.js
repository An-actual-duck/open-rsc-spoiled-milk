/** Fullscreen belongs to the outer document, never Java or its sandbox iframe. */
export function createDisplayModes({window:win,document:doc,player,select,button,notice,onChange=()=>{}}){
  const stats={baseMode:new URLSearchParams(win.location.search).get('view')==='embedded'?'embedded':'fit',fullscreen:false};
  function update(){stats.fullscreen=doc.fullscreenElement===player;stats.mode=stats.fullscreen?'fullscreen':stats.baseMode;
    doc.body.dataset.view=stats.baseMode;select.value=stats.baseMode;button.textContent=stats.fullscreen?'Exit fullscreen':'Fullscreen';
    onChange(stats);}
  const supported=!!(doc.fullscreenEnabled&&player.requestFullscreen);button.disabled=!supported;
  if(!supported)notice.textContent='Fullscreen is unavailable in this browser. Fit tab remains available.';
  async function toggle(){
    try{if(doc.fullscreenElement===player)await doc.exitFullscreen();else await player.requestFullscreen();notice.textContent='';}
    catch(error){notice.textContent='Fullscreen could not be opened. Fit tab is still available.';stats.error=String(error);}
    update();
  }
  function changed(){notice.textContent='';update();}
  function selectMode(){stats.baseMode=select.value==='embedded'?'embedded':'fit';update();}
  button.addEventListener('click',toggle);select.addEventListener('change',selectMode);doc.addEventListener('fullscreenchange',changed);update();
  return {stats,exit(){if(doc.fullscreenElement===player)return doc.exitFullscreen().catch(()=>{});},
    dispose(){button.removeEventListener('click',toggle);select.removeEventListener('change',selectMode);doc.removeEventListener('fullscreenchange',changed);}};
}
