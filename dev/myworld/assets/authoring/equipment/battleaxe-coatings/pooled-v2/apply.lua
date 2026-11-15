local root=app.params['root'];local base=root..'/dev/myworld/assets/authoring/equipment/battleaxe-coatings/pooled-v2';local pc=app.pixelColor
local approved=app.open(base..'/approved-style-b.aseprite');local approvedOverlay=approved.layers[2]:cel(1).image:clone();approved:close()
local dark=pc.rgba(18,79,72,255);local mid=pc.rgba(32,128,115,255);local light=pc.rgba(74,174,153,255)
local function metal(raw,x,y)
 if x<0 or y<0 or x>=raw.width or y>=raw.height then return false end
 local c=raw:getPixel(x,y);return pc.rgbaA(c)>0 and pc.rgbaR(c)==pc.rgbaG(c)and pc.rgbaG(c)==pc.rgbaB(c)and pc.rgbaR(c)>24
end
local function paint(raw,wet,x,y,color)
 if x<0 or y<0 or x>=raw.width or y>=raw.height then return end
 if metal(raw,x,y)then wet:drawPixel(x,y,color);return end
 local c=raw:getPixel(x,y)
 if pc.rgbaA(c)==0 and (metal(raw,x-1,y)or metal(raw,x+1,y)or metal(raw,x,y-1)or metal(raw,x,y+1))then wet:drawPixel(x,y,color)end
end
local function pool(raw,wet,x,y)
 for dy=-2,2 do for dx=-2,2 do
  if dx*dx+dy*dy<=4 then
   local color=dx*dx+dy*dy>=3 and dark or mid
   if dx==-1 and dy==-1 then color=light end
   paint(raw,wet,x+dx,y+dy,color)
  end
 end end
end
local function save(raw,wet,path)
 local image=Image(raw);image:drawImage(wet,Point(0,0));local changed=0;local added=0
 for y=0,raw.height-1 do for x=0,raw.width-1 do
  local a,b=raw:getPixel(x,y),image:getPixel(x,y)
  if a~=b then
   changed=changed+1
   if pc.rgbaA(a)==0 then added=added+1;assert(metal(raw,x-1,y)or metal(raw,x+1,y)or metal(raw,x,y-1)or metal(raw,x,y+1),'Floating slime')end
  end
 end end
 assert(changed>0);image:saveAs(path..'.png')
 local s=Sprite(raw.width,raw.height,ColorMode.RGB);s.layers[1].name='Uncoated original';s:newCel(s.layers[1],1,raw,Point(0,0));local layer=s:newLayer();layer.name='Approved B - pooled rim';s:newCel(layer,1,wet,Point(0,0));s:saveAs(path..'.aseprite');s:close()
 print(path..': '..changed..' coating pixels, '..added..' attached rim pixels')
end
-- Shared inventory form: exact approved overlay, neutral metal preserved for runtime tint.
local raw=Image{fromFile=base..'/inputs/inventory/84.png'};save(raw,approvedOverlay,base..'/inventory/standard')
-- Dragon inventory keeps its separate red blade/hilt artwork. Transpose the
-- approved edge pools onto its existing cutting rim (never its gold shaft).
raw=Image{fromFile=base..'/inputs/inventory/272.png'}
local wet=Image(raw.width,raw.height,ColorMode.RGB)
local function dragonMetal(x,y)
 if x<0 or y<0 or x>=raw.width or y>=raw.height then return false end
 local c=raw:getPixel(x,y);return pc.rgbaA(c)>0 and pc.rgbaR(c)>pc.rgbaG(c)*3 and pc.rgbaR(c)>pc.rgbaB(c)*3 and x>=20 and y<=20
end
local edge={}
for y=0,20 do for x=20,raw.width-1 do
 if dragonMetal(x,y)and(not dragonMetal(x-1,y)or not dragonMetal(x+1,y)or not dragonMetal(x,y-1)or not dragonMetal(x,y+1))then wet:drawPixel(x,y,mid);table.insert(edge,{x,y})end
end end
for _,t in ipairs({.15,.37,.65,.86})do
 local p=edge[math.max(1,math.floor(#edge*t))]
 for dy=-2,2 do for dx=-2,2 do local x,y=p[1]+dx,p[2]+dy
  if dx*dx+dy*dy<=4 and x>=0 and y>=0 and x<raw.width and y<raw.height then
   local original=raw:getPixel(x,y);local attached=pc.rgbaA(original)==0 and (dragonMetal(x-1,y)or dragonMetal(x+1,y)or dragonMetal(x,y-1)or dragonMetal(x,y+1))
   if dragonMetal(x,y)or attached then wet:drawPixel(x,y,dx==-1 and dy==-1 and light or dx*dx+dy*dy>=3 and dark or mid)end
  end
 end end
end
-- Dragon uses its own material guard; ensure the handle is untouched explicitly.
local result=Image(raw);result:drawImage(wet,Point(0,0));result:saveAs(base..'/inventory/dragon.png')
local ds=Sprite(raw.width,raw.height,ColorMode.RGB);ds.layers[1].name='Original dragon axe';ds:newCel(ds.layers[1],1,raw,Point(0,0));local dl=ds:newLayer();dl.name='Pooled rim';ds:newCel(dl,1,wet,Point(0,0));ds:saveAs(base..'/inventory/dragon.aseprite');ds:close()
for n=0,17 do
 local name=string.format('%02d',n);raw=Image{fromFile=base..'/inputs/held/'..name..'.png'};wet=Image(raw.width,raw.height,ColorMode.RGB);edge={}
 for y=0,raw.height-1 do for x=0,raw.width-1 do
  if metal(raw,x,y)then
   local c=raw:getPixel(x,y);local v=pc.rgbaR(c)
   -- Bright native metal pixels identify the cutting rims, avoiding the socket.
   if v>=160 then wet:drawPixel(x,y,mid);table.insert(edge,{x,y})end
  end
 end end
 if #edge==0 then
  for y=0,raw.height-1 do for x=0,raw.width-1 do
   if metal(raw,x,y)and(not metal(raw,x-1,y)or not metal(raw,x+1,y)or not metal(raw,x,y-1)or not metal(raw,x,y+1))then wet:drawPixel(x,y,mid);table.insert(edge,{x,y})end
  end end
 end
 assert(#edge>0)
 for _,t in ipairs({.25,.75})do local p=edge[math.max(1,math.floor(#edge*t))];pool(raw,wet,p[1],p[2])end
 save(raw,wet,base..'/held/'..name)
end
