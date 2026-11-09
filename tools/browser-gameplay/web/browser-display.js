/** Presentation-only CSS fit; native framebuffer dimensions remain unchanged. */
export function fitRect(width,height,availableWidth,availableHeight) {
  const scale=Math.min(availableWidth/width,availableHeight/height);
  return {scale,left:(availableWidth-width*scale)/2,top:(availableHeight-height*scale)/2};
}
export function createBrowserDisplay({window:win,document:doc,display,viewport}) {
  let width=960,height=540,desktop,disposed=false;
  const listeners=[],stats={internalWidth:width,internalHeight:height,scale:1,left:0,top:0,cssWidth:width,cssHeight:height};
  function layout(){
    if(disposed)return;
    const r=viewport.getBoundingClientRect();if(!(r.width>0&&r.height>0))return;
    const fit=fitRect(width,height,r.width,r.height),changed=fit.scale!==stats.scale||fit.left!==stats.left||fit.top!==stats.top;
    Object.assign(stats,fit,{internalWidth:width,internalHeight:height,cssWidth:width*fit.scale,cssHeight:height*fit.scale,devicePixelRatio:win.devicePixelRatio||1});
    Object.assign(display.style,{position:'absolute',transformOrigin:'0 0',left:fit.left+'px',top:fit.top+'px',width:width+'px',height:height+'px',transform:`scale(${fit.scale})`});
    if(desktop){desktop.style.width=width+'px';desktop.style.height=height+'px';}
    if(changed)win.dispatchEvent(new Event('browser-display-change'));
  }
  function setSize(w,h){if(!Number.isInteger(w)||!Number.isInteger(h)||w<1||h<1||w*h>2097152)return;
    if(w!==width||h!==height){width=w;height=h;layout();}}
  // CheerpJ5 consumes clientX/Y minus its desktop rect, without CSS unscaling.
  // Run on the display CAPTURE path: document's loading gate and window's
  // middle relay see original coordinates first. No synthetic duplicate event.
  function normalize(event){
    if((event.buttons&4)||event.button===1)return;
    const r=display.getBoundingClientRect(),s=stats.scale;
    if(!(s>0)||s===1)return;
    const x=r.left+(event.clientX-r.left)/s,y=r.top+(event.clientY-r.top)/s;
    try{Object.defineProperties(event,{clientX:{value:x,configurable:true},clientY:{value:y,configurable:true}});}
    catch(error){stats.inputError='Browser cannot map scaled pointer coordinates: '+error;event.preventDefault();event.stopImmediatePropagation();}
  }
  for(const name of ['pointerdown','pointermove','pointerup','pointerleave','wheel','contextmenu']){
    display.addEventListener(name,normalize,{capture:true,passive:false});listeners.push(name);
  }
  const resize=typeof ResizeObserver!=='undefined'?new ResizeObserver(layout):null;resize?.observe(viewport);
  win.addEventListener('resize',layout);layout();
  return {stats,get scale(){return stats.scale;},setSize,layout,
    attachDesktop(element){desktop=element;layout();},
    dispose(){if(disposed)return;disposed=true;resize?.disconnect();win.removeEventListener('resize',layout);for(const n of listeners)display.removeEventListener(n,normalize,true);},
  };
}
