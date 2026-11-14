local base=app.params['base']
local a=app.open(base..'/inputs/previous-gap-repair.aseprite')
local b=app.open(base..'/stirge-near-wing-corrected.aseprite')
local function full(l,f)
 local im=Image(150,130,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im
end
local checked=0
for _,l in ipairs(a.layers)do
 if l.name~='Rust bat wings'then
  local other=nil;for _,n in ipairs(b.layers)do if n.name==l.name then other=n end end;assert(other)
  assert(l.isVisible==other.isVisible)
  for f=1,18 do
   local x,y=full(l,f),full(other,f)
   for row=0,129 do for col=0,149 do assert(x:getPixel(col,row)==y:getPixel(col,row),'Layer changed: '..l.name)end end
   checked=checked+1
  end
 end
end
assert(#b.layers==9 and #b.frames==18 and #b.tags==6)
local foreground,background,body
for _,l in ipairs(b.layers)do
 if l.name=='Near wing - downstroke in front of body'then foreground=l end
 if l.name=='Near wing - mid/upstroke behind body'then background=l end
 if l.name=='Striped abdomen and moth thorax'then body=l end
end
assert(background.stackIndex<body.stackIndex and foreground.stackIndex>body.stackIndex)
for f=1,18 do
 assert((foreground:cel(f)~=nil)==(f==4))
 assert((background:cel(f)~=nil)==(f==5 or f==6))
end
print('PASS: '..checked..' unchanged original nonwing layer/frame combinations; nine layers, eighteen frames, six tags; phase layering correct')
a:close();b:close()
