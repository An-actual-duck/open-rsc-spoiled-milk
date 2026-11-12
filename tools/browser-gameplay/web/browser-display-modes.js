/** Fullscreen belongs to the outer document, never Java or its sandbox iframe. */
export function createDisplayModes({window:win,document:doc,player,dialog,confirm,cancel,message,onChange=()=>{},onReturn=()=>{}}){
  const stats={baseMode:new URLSearchParams(win.location.search).get('view')==='embedded'?'embedded':'fit',fullscreen:false};
  let disposed=false,pending=false,generation=0;
  const supported=!!(doc.fullscreenEnabled&&player.requestFullscreen);
  function update(){
    if(disposed)return;
    stats.fullscreen=doc.fullscreenElement===player;stats.mode=stats.fullscreen?'fullscreen':stats.baseMode;
    doc.body.dataset.view=stats.baseMode;onChange(stats);
  }
  function closeDialog(){if(dialog.open)dialog.close();onReturn();}
  function dismiss(){generation++;pending=false;closeDialog();}
  function offer(reason){
    if(disposed)return;
    message.textContent=reason;
    confirm.disabled=!supported;
    if(!dialog.open)dialog.showModal();
  }
  // Call the API before any await: the current same-origin menu click may still
  // carry activation. Only offer a fresh, real gesture if that attempt fails.
  async function enter(){
    if(disposed||pending)return;
    if(!supported){offer('Fullscreen is unavailable. You can keep playing in this tab.');return;}
    pending=true;const token=++generation;
    try{await player.requestFullscreen();if(disposed||token!==generation){await exit();return;}stats.error=null;closeDialog();}
    catch(error){if(disposed||token!==generation)return;stats.error=String(error);offer('Choose Enter fullscreen to continue, or keep playing in this tab.');}
    finally{if(token===generation)pending=false;update();}
  }
  async function exit(){if(doc.fullscreenElement===player)try{await doc.exitFullscreen();}catch(error){stats.error=String(error);}update();}
  function request(action){
    if(disposed)return;
    if(action===1){stats.baseMode=stats.baseMode==='fit'?'embedded':'fit';update();}
    else if(action===2){if(doc.fullscreenElement===player)void exit();else void enter();}
  }
  function changed(){if(doc.fullscreenElement===player&&dialog.open)closeDialog();update();}
  function confirmed(){void enter();}
  function cancelled(){dismiss();}
  function escaped(event){event.preventDefault();dismiss();}
  confirm.addEventListener('click',confirmed);cancel.addEventListener('click',cancelled);
  dialog.addEventListener('cancel',escaped);doc.addEventListener('fullscreenchange',changed);update();
  return {stats,request,exit,
    dismiss,
    dispose(){generation++;disposed=true;if(dialog.open)dialog.close();confirm.removeEventListener('click',confirmed);cancel.removeEventListener('click',cancelled);dialog.removeEventListener('cancel',escaped);doc.removeEventListener('fullscreenchange',changed);}};
}
