-- Final stripe finish: all other approved cels must remain byte-for-byte identical.
local base=app.params['base'];local old=app.open(base..'/inputs/before-rear-stripes.aseprite');local current=app.open(base..'/stirge-plunge-attack.aseprite');local pc=app.pixelColor
local function lookup(s,name)for _,l in ipairs(s.layers)do if l.name==name then return l end end end
local function full(l,f)local im=Image(210,160,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im end
assert(#current.layers==10 and #current.frames==18 and #current.tags==6)
local checked,striped=0,0
for _,l in ipairs(old.layers)do local n=lookup(current,l.name);assert(n and l.isVisible==n.isVisible)
 for f=1,18 do
  local isStripe=l.name=='Striped abdomen and moth thorax'and f>=10 and f<=15
  local a,b=full(l,f),full(n,f);local differences=0
  for y=0,159 do for x=0,209 do
   local aa,bb=a:getPixel(x,y),b:getPixel(x,y)
   assert(pc.rgbaA(aa)==pc.rgbaA(bb),'Silhouette changed')
   if aa~=bb then differences=differences+1;assert(isStripe,'Unrelated cel changed')end
  end end
  if isStripe then assert(differences>0);striped=striped+1 else checked=checked+1 end
 end
end
local front=lookup(current,'Attack near wing - plunge and recovery');local body=lookup(current,'Striped abdomen and moth thorax');local head=lookup(current,'Mosquito head and proboscis')
assert(front.stackIndex>body.stackIndex and front.stackIndex<head.stackIndex)
print('PASS: '..striped..' rear abdomen cels striped; '..checked..' other layer/frame combinations unchanged; all silhouettes and all attack frames unchanged; 18 frames, six tags, ten layers.')
old:close();current:close()
