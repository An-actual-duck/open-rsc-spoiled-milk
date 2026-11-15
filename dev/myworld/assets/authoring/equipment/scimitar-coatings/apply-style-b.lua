local root=app.params['root'];local base=root..'/dev/myworld/assets/authoring/equipment/scimitar-coatings';local pc=app.pixelColor
local approved=app.open(base..'/approved-style-b.aseprite');local wet=approved.layers[2]:cel(1).image:clone();approved:close()
local function save(raw,overlay,png,master)
 local result=Image(raw);result:drawImage(overlay,Point(0,0));local changes=0
 for y=0,raw.height-1 do for x=0,raw.width-1 do
  local a,b=raw:getPixel(x,y),result:getPixel(x,y)
  assert(pc.rgbaA(a)==pc.rgbaA(b),'Coating changed silhouette')
  if a~=b then changes=changes+1 end
 end end
 assert(changes>0,'Empty coating overlay');result:saveAs(png)
 local s=Sprite(raw.width,raw.height,ColorMode.RGB);s.layers[1].name='Uncoated original';s:newCel(s.layers[1],1,raw,Point(0,0))
 local layer=s:newLayer();layer.name='Approved dripping anti-coagulant';s:newCel(layer,1,overlay,Point(0,0));s:saveAs(master);s:close()
end
for _,id in ipairs({1999,2010,82,83,84,427,2155,3117,85,2021,86,2032,398,3265})do
 local raw=Image{fromFile=base..'/inputs/inventory/'..id..'.png'}
 save(raw,wet,root..'/dev/myworld/assets/runtime/sprites/items/inventory-ground/anticoagulant-scimitar-'..id..'.png',base..'/inventory-'..id..'.aseprite')
end
for n=0,17 do
 local raw=Image{fromFile=base..'/inputs/held/'..string.format('%02d',n)..'.png'};local w,h=raw.width,raw.height;local overlay=Image(w,h,ColorMode.RGB)
 local function metal(x,y)
  if x<0 or y<0 or x>=w or y>=h then return false end
  local c=raw:getPixel(x,y);local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
  return pc.rgbaA(c)>0 and r==g and g==b and r>50
 end
 local xmin,ymin,xmax,ymax=w,h,-1,-1
 for y=0,h-1 do for x=0,w-1 do if metal(x,y)then xmin=math.min(xmin,x);xmax=math.max(xmax,x);ymin=math.min(ymin,y);ymax=math.max(ymax,y)end end end
 local horizontal=(xmax-xmin)>(ymax-ymin);local first=horizontal and xmin or ymin;local last=horizontal and xmax or ymax
 local function paint(x,y)
  if not metal(x,y)then return end
  local v=pc.rgbaR(raw:getPixel(x,y));local color=v>210 and pc.rgba(167,57,77,255)or v>120 and pc.rgba(139,36,56,255)or pc.rgba(88,24,40,255)
  overlay:drawPixel(x,y,color)
 end
 for t=first,last do
  local edge=-1
  if horizontal then for y=h-1,0,-1 do if metal(t,y)then edge=y;break end end
  else for x=w-1,0,-1 do if metal(x,t)then edge=x;break end end end
  if edge>=0 then
   local thickness=(t-first)%5==1 and 2 or 1
   for d=0,thickness-1 do if horizontal then paint(t,edge-d)else paint(edge-d,t)end end
   -- Short attached rivulets run inward across the metal, never into the air.
   if (t-first)%9==4 then for d=1,3 do
    if horizontal then paint(t-math.floor(d/2),edge-d)else paint(edge-d,t-math.floor(d/2))end
   end end
  end
 end
 save(raw,overlay,base..'/held/'..string.format('%02d',n)..'.png',base..'/held/'..string.format('%02d',n)..'.aseprite')
end
