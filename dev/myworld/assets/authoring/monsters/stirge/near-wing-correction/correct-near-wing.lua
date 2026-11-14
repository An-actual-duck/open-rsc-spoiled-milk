-- Separate shoulder mounts and phase-correct overlap for the front/right view.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/monsters/stirge/near-wing-correction/'
local out=root..'/output/art/stirge-near-wing-correction/'
local s=app.open(base..'inputs/previous-gap-repair.aseprite')
local old=app.open(base..'inputs/dot-candidate-3.aseprite')
local pc=app.pixelColor
local function C(r,g,b)return pc.rgba(r,g,b,255)end
local rust0,rust1,rust2,rib=C(153,61,28),C(193,85,32),C(218,113,42),C(91,45,27)
local wing,body,oldWing
for _,l in ipairs(s.layers)do if l.name=='Rust bat wings'then wing=l elseif l.name=='Striped abdomen and moth thorax'then body=l end end
for _,l in ipairs(old.layers)do if l.name=='Rust bat wings'then oldWing=l end end
assert(wing and body and oldWing)
local raised=s:newLayer();raised.name='Near wing - mid/upstroke behind body';raised.stackIndex=body.stackIndex;raised.isVisible=true
local lowered=s:newLayer();lowered.name='Near wing - downstroke in front of body';lowered.stackIndex=body.stackIndex+1;lowered.isVisible=true
local specs={
 {frame=3,sourceRoot={75,39},nearRoot={72,31},farRoot={78,31},scaleY=1,wrist={52,29},rootEdge={75,35},corner={70,35},hull={{68,34},{75,34},{77,39},{74,43},{70,44}}},
 {frame=4,sourceRoot={74,40},nearRoot={71,32},farRoot={77,32},scaleY=1,wrist={44,13},rootEdge={74,34},corner={70,32},hull={{66,30},{72,33},{76,39},{74,43},{69,42}}},
 {frame=5,sourceRoot={74,41},nearRoot={71,33},farRoot={77,33},scaleY=.90,wrist={44,6},rootEdge={74,34},corner={70,31},hull={{66,29},{72,33},{76,39},{74,42},{69,42}}}
}
local function full(layer,frame)
 local image=Image(150,130,ColorMode.RGB);local cel=layer:cel(frame+1);if cel then image:drawImage(cel.image,cel.position)end;return image
end
local function inside(x,y,poly)
 local result=false;local j=#poly
 for i=1,#poly do local a,b=poly[i],poly[j]
  if(a[2]>y)~=(b[2]>y)and x<(b[1]-a[1])*(y-a[2])/(b[2]-a[2])+a[1]then result=not result end
  j=i
 end
 return result
end
local function line(im,a,b,c)
 local x,y=a[1],a[2];local dx=math.abs(b[1]-x);local sx=x<b[1]and 1 or -1
 local dy=-math.abs(b[2]-y);local sy=y<b[2]and 1 or -1;local err=dx+dy
 while true do
  if x>=0 and x<150 and y>=0 and y<130 then im:drawPixel(x,y,c)end
  if x==b[1]and y==b[2]then break end
  local e=2*err;if e>=dy then err=err+dy;x=x+sx end;if e<=dx then err=err+dx;y=y+sy end
 end
end
for _,spec in ipairs(specs)do
 local before=full(wing,spec.frame);local original=full(oldWing,spec.frame)
 local far=Image(150,130,ColorMode.RGB)
 -- Keep the repaired opposite wing fixed. Remove the old shared central bridge.
 for y=0,129 do for x=0,149 do
  local edge=spec.farRoot[1]+math.max(0,spec.farRoot[2]-y)*.8
  if x+.5>=edge then far:drawPixel(x,y,before:getPixel(x,y))end
 end end
 local nearSource=Image(150,130,ColorMode.RGB)
 -- Separate the near wing along its own wrist-to-shoulder leading contour.
 -- A straight sheet-wide split would mistakenly include the far wing's folded tip.
 local function nearEdge(y)
  if y<=spec.wrist[2]then return spec.wrist[1]+2 end
  if y>=spec.rootEdge[2]then return spec.rootEdge[1]+2 end
  return spec.wrist[1]+(spec.rootEdge[1]-spec.wrist[1])*(y-spec.wrist[2])/(spec.rootEdge[2]-spec.wrist[2])+2
 end
 for y=0,129 do for x=0,149 do if x<=nearEdge(y)then nearSource:drawPixel(x,y,original:getPixel(x,y))end end end
 for y=0,129 do for x=0,149 do
  if pc.rgbaA(nearSource:getPixel(x,y))==0 and x<=nearEdge(y)and inside(x+.5,y+.5,spec.hull)then
   local donor=x>=20 and original:getPixel(x-20,math.max(0,y-4))or 0
   if donor~=rust0 and donor~=rust1 and donor~=rust2 then donor=rust1 end
   nearSource:drawPixel(x,y,donor)
  end
 end end
 line(nearSource,spec.sourceRoot,spec.corner,rib)
 assert(pc.rgbaA(nearSource:getPixel(spec.sourceRoot[1],spec.sourceRoot[2]))>0,'Missing near wing hinge')
 local near=Image(150,130,ColorMode.RGB)
 -- Minimal span compression keeps the repositioned wing tips inside bat bounds.
 -- The raised pose also foreshortens vertically; use exact nearest native pixels.
 for y=0,129 do for x=0,149 do
  local sx=math.floor(spec.sourceRoot[1]+(x-spec.nearRoot[1])/.96+.5)
  local sy=math.floor(spec.sourceRoot[2]+(y-spec.nearRoot[2])/spec.scaleY+.5)
  if sx>=0 and sx<150 and sy>=0 and sy<130 then near:drawPixel(x,y,nearSource:getPixel(sx,sy))end
 end end
 s:newCel(wing,spec.frame+1,Image(far),Point(0,0))
 local layer=spec.frame==3 and lowered or raised
 s:newCel(layer,spec.frame+1,Image(near),Point(0,0))
 -- Separate native wing plates are retained for anatomical/overlap inspection.
 far:saveAs(out..string.format('far-wing-%02d.png',spec.frame));near:saveAs(out..string.format('near-wing-%02d.png',spec.frame))
 local torso=full(body,spec.frame)
 assert(pc.rgbaA(torso:getPixel(spec.nearRoot[1],spec.nearRoot[2]))>0,'Near hinge is not on upper body')
 assert(pc.rgbaA(torso:getPixel(spec.farRoot[1],spec.farRoot[2]))>0,'Far hinge is not on upper body')
end
s:saveAs(base..'stirge-near-wing-corrected.aseprite')
local images={}
for frame=0,17 do
 local width=frame>=15 and 97 or 150
 local image=Image(width,130,ColorMode.RGB);image:drawSprite(s,frame+1);images[frame+1]=Image(image)
 image:saveAs(base..string.format('frame-%02d.png',frame));image:saveAs(out..string.format('frame-%02d.png',frame))
end
local sheet=Image(450,780,ColorMode.RGB)
for n,image in ipairs(images)do sheet:drawImage(image,Point(((n-1)%3)*150,math.floor((n-1)/3)*130))end
sheet:saveAs(base..'stirge-near-wing-corrected-sheet.png');sheet:saveAs(out..'stirge-near-wing-corrected-sheet.png')
local function append(sprite,image)
 if image.width~=150 then local p=Image(150,130,ColorMode.RGB);p:drawImage(image,Point(math.floor((150-image.width)/2),0));image=p end
 local n=#sprite.frames;if sprite.cels[1]then sprite:newEmptyFrame();n=n+1 end
 sprite:newCel(sprite.layers[1],n,Image(image),Point(0,0));sprite.frames[n].duration=.15
end
for group,name in ipairs({'front','front-right','right-profile','rear-right','rear','attack-right'})do
 local gif=Sprite(150,130,ColorMode.RGB);gif:deleteCel(gif.cels[1])
 for _,phase in ipairs({0,1,2,1})do append(gif,images[(group-1)*3+phase+1])end
 gif:saveCopyAs(out..name..'.gif');gif:close()
end
local f=io.open(base..'wing-mounts.json','w');f:write(json.encode(specs));f:close()
s:close();old:close()
