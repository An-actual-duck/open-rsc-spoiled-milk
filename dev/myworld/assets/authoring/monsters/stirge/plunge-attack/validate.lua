local base=app.params['base'];local old=app.open(base..'/inputs/before-bat-wing-palette.aseprite');local current=app.open(base..'/stirge-plunge-attack.aseprite');local pc=app.pixelColor
local function C(r,g,b)return pc.rgba(r,g,b,255)end
local map={[C(153,61,28)]=C(97,97,97),[C(193,85,32)]=C(124,124,124),[C(218,113,42)]=C(182,182,182),[C(91,45,27)]=C(108,68,18),[C(112,48,31)]=C(70,70,70),[C(167,105,51)]=C(146,103,33)}
local wingNames={['Rust bat wings']=true,['Near wing - mid/upstroke behind body']=true,['Near wing - downstroke in front of body']=true,['Attack near wing - plunge and recovery']=true}
local function full(l,f)local im=Image(210,160,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im end
assert(#old.layers==#current.layers and #current.frames==18 and #current.tags==6)
local changed=0
for n,l in ipairs(old.layers)do local r=current.layers[n];assert(l.name==r.name and l.isVisible==r.isVisible)
 for f=1,18 do
  assert(old.frames[f].duration==current.frames[f].duration)
  local a,b=full(l,f),full(r,f)
  for y=0,159 do for x=0,209 do local aa,bb=a:getPixel(x,y),b:getPixel(x,y)
   assert(pc.rgbaA(aa)==pc.rgbaA(bb),'Silhouette changed')
   local expected=wingNames[l.name]and(map[aa]or aa)or aa
   assert(bb==expected,'Unexpected change to '..l.name)
   if aa~=bb then changed=changed+1 end
  end end
 end
end
print('PASS: '..changed..' wing pixels recolored by exact bat-palette mapping; all non-wing pixels, silhouettes, layering, frame timing and poses unchanged.')
old:close();current:close()
