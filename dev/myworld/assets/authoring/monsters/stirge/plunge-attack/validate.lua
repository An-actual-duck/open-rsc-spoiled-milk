local base=app.params['base'];local a=app.open(base..'/inputs/approved-flight.aseprite');local b=app.open(base..'/stirge-plunge-attack.aseprite');local pc=app.pixelColor
local function full(l,f)local im=Image(210,160,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im end
local checked=0
for _,l in ipairs(a.layers)do local r;for _,n in ipairs(b.layers)do if n.name==l.name then r=n end end;assert(r and l.isVisible==r.isVisible)
 for f=1,15 do local x,y=full(l,f),full(r,f);for yy=0,159 do for xx=0,209 do assert(x:getPixel(xx,yy)==y:getPixel(xx,yy),'Approved flight layer changed')end end;checked=checked+1 end
end
local front,body,head
for _,l in ipairs(b.layers)do if l.name=='Attack near wing - plunge and recovery'then front=l elseif l.name=='Striped abdomen and moth thorax'then body=l elseif l.name=='Mosquito head and proboscis'then head=l end end
assert(front.stackIndex>body.stackIndex and front.stackIndex<head.stackIndex)
local overlap,total=0,0;local x,y=full(front,18),full(body,18)
for yy=0,159 do for xx=0,209 do if pc.rgbaA(y:getPixel(xx,yy))>0 then total=total+1;if pc.rgbaA(x:getPixel(xx,yy))>0 then overlap=overlap+1 end end end end
assert(overlap>=307,'Recovery wing does not cover original body')
assert(#b.frames==18 and #b.tags==6 and #b.layers==10)
print('PASS: '..checked..' unchanged flight layer/frame combinations; recovery wing covers '..overlap..'/'..total..' body pixels, head drawn in front; 18 frames, six tags, ten layers.')
a:close();b:close()

-- Final correction scope: only coil visibility and plunge wing draw order.
local previous=app.open(base..'/inputs/plunge-before-final-fixes.aseprite')
local current=app.open(base..'/stirge-plunge-attack.aseprite')
local function lookup(s,name)for _,l in ipairs(s.layers)do if l.name==name then return l end end end
local function equal(x,y)for yy=0,159 do for xx=0,209 do assert(x:getPixel(xx,yy)==y:getPixel(xx,yy),'Unexpected final-correction change')end end end
for _,name in ipairs({'Striped abdomen and moth thorax','Mosquito head and proboscis'})do
 for f=16,(name=='Mosquito head and proboscis'and 18 or 17)do equal(full(lookup(previous,name),f),full(lookup(current,name),f))end
end
for _,name in ipairs({'Far legs - thorax roots','Near legs - thorax roots'})do for f=17,18 do equal(full(lookup(previous,name),f),full(lookup(current,name),f))end end
local front=lookup(current,'Attack near wing - plunge and recovery');assert(front:cel(17))
local combined=full(lookup(current,'Rust bat wings'),17);combined:drawImage(full(front,17),Point(0,0))
equal(combined,full(lookup(previous,'Rust bat wings'),17))
print('PASS: plunge wing pixels/position unchanged, foreground order corrected; head unchanged in all attack poses; body unchanged in windup/plunge; remaining attack leg poses unchanged.')
previous:close();current:close()

-- Far-wing perspective revision: no changes outside recovery far wing/body bridge.
local previous=app.open(base..'/inputs/recovery-before-far-wing-fix.aseprite')
local current=app.open(base..'/stirge-plunge-attack.aseprite')
local checked=0
for _,l in ipairs(previous.layers)do
 local n=lookup(current,l.name);assert(n)
 for f=1,18 do
  if f~=18 or(l.name~='Rust bat wings'and l.name~='Striped abdomen and moth thorax')then
   equal(full(l,f),full(n,f));checked=checked+1
  end
 end
end
print('PASS: '..checked..' layer/frame combinations match pre-revision master, including near wing, legs, head, and first two attack poses.')
previous:close();current:close()

-- Texture-only finish: all other cels and the far-wing alpha mask are fixed.
local previous=app.open(base..'/inputs/recovery-before-texture-match.aseprite')
local current=app.open(base..'/stirge-plunge-attack.aseprite')
for _,l in ipairs(previous.layers)do local n=lookup(current,l.name)
 for f=1,18 do
  if f~=18 or l.name~='Rust bat wings'then equal(full(l,f),full(n,f))end
 end
end
local a=full(lookup(previous,'Rust bat wings'),18);local b=full(lookup(current,'Rust bat wings'),18)
for y=0,159 do for x=0,209 do
 assert(pc.rgbaA(a:getPixel(x,y))==pc.rgbaA(b:getPixel(x,y)),'Far-wing silhouette changed')
 if a:getPixel(x,y)==pc.rgba(91,45,27,255)then assert(a:getPixel(x,y)==b:getPixel(x,y),'Far-wing rib changed')end
end end
print('PASS: texture-only far-wing revision; silhouette, ribs and other 179 layer/frame combinations unchanged.')
previous:close();current:close()
