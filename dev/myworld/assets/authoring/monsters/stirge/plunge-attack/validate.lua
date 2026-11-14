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
assert(overlap/total>.45,'Recovery wing does not cover body')
assert(#b.frames==18 and #b.tags==6 and #b.layers==10)
print('PASS: '..checked..' unchanged flight layer/frame combinations; recovery wing covers '..overlap..'/'..total..' body pixels, head drawn in front; 18 frames, six tags, ten layers.')
a:close();b:close()

-- Final correction scope: only coil visibility and plunge wing draw order.
local previous=app.open(base..'/inputs/plunge-before-final-fixes.aseprite')
local current=app.open(base..'/stirge-plunge-attack.aseprite')
local function lookup(s,name)for _,l in ipairs(s.layers)do if l.name==name then return l end end end
local function equal(x,y)for yy=0,159 do for xx=0,209 do assert(x:getPixel(xx,yy)==y:getPixel(xx,yy),'Unexpected final-correction change')end end end
for _,name in ipairs({'Striped abdomen and moth thorax','Mosquito head and proboscis'})do
 for f=16,18 do equal(full(lookup(previous,name),f),full(lookup(current,name),f))end
end
for _,name in ipairs({'Far legs - thorax roots','Near legs - thorax roots'})do for f=17,18 do equal(full(lookup(previous,name),f),full(lookup(current,name),f))end end
local front=lookup(current,'Attack near wing - plunge and recovery');assert(front:cel(17))
local combined=full(lookup(current,'Rust bat wings'),17);combined:drawImage(full(front,17),Point(0,0))
equal(combined,full(lookup(previous,'Rust bat wings'),17))
print('PASS: plunge wing pixels/position unchanged, foreground order corrected; body/head unchanged in all attack poses; remaining attack leg poses unchanged.')
previous:close();current:close()
