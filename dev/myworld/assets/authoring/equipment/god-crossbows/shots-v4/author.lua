-- Canonical +X bolt, stable pivot, six broad trailing-particle phases.
local root=app.params.root
local pc=app.pixelColor
local p={saradomin={dark={205,161,34},light={255,246,128},mote={255,219,48}},zamorak={dark={32,24,37},light={86,65,83},mote={232,38,63}},guthix={dark={69,42,23},light={131,89,44},mote={104,185,51}}}
local src=app.open(root..'/inputs/bolt-basic-original.png');local original=Image(src.cels[1].image);src:close()
for _,god in ipairs({'basic','saradomin','guthix','zamorak'})do
 local count=god=='basic' and 1 or 6
 local spr=Sprite(96*count,96,ColorMode.RGB)
 local particles=spr.cels[1].image;spr.layers[1].name='Enveloping wisps and long tapered trail'
 local layer=spr:newLayer();layer.name='Horizontal original bolt - fixed pivot 48,48'
 local bolt=Image(96*count,96)
 local function rgba(c,a)return pc.rgba(c[1],c[2],c[3],a)end
 for f=0,count-1 do
  local ox=f*96
  if god~='basic' then
   local pal=p[god]
   -- Separate strands and drifting clumps, tapering backward from the bolt.
   for x=4,59 do
    local life=(x-4)/55
    for strand=-2,2 do
     local y=48+strand*(1+3*life)+math.sin(x*.31+f*1.1+strand)*2
     local a=math.floor(65+life*150)
     if (x+f+strand)%7~=0 then
      particles:drawPixel(ox+x,math.floor(y+.5),rgba(pal.mote,a))
      if life>.42 then particles:drawPixel(ox+x,math.floor(y+.5)+1,rgba(pal.mote,a-30))end
     end
    end
   end
   for i=0,23 do
    local age=(i+f*3)%24;local x=7+age*2;local y=48+math.sin(i*2.1+f*.8)*(3+age*.25)
    local a=110+age*5
    for dy=0,(i%3==0 and 2 or 0)do for dx=0,(i%2)do
     particles:drawPixel(ox+x+dx,math.floor(y)+dy,rgba(i%4==0 and pal.light or pal.mote,a))
    end end
   end
  end
  -- Inverse nearest-neighbour rotation of the original diagonal bolt to +X.
  for y=42,53 do for x=35,61 do
   local u=x-48;local v=y-48
   local sx=math.floor(9+u*.77+v*.64+.5);local sy=math.floor(7.5-u*.64+v*.77+.5)
   if sx>=0 and sx<19 and sy>=0 and sy<16 then
    local c=original:getPixel(sx,sy)
    if pc.rgbaA(c)>0 then
     if god~='basic' then c=rgba(pc.rgbaR(c)>90 and p[god].light or p[god].dark,255)end
     bolt:drawPixel(ox+x,y,c)
    end
   end
  end end
 end
 spr:newCel(layer,1,bolt,Point(0,0))
 spr:saveAs(root..'/'..god..'/bolt.aseprite');spr:saveCopyAs(root..'/'..god..'/bolt.png');spr:close()
end
