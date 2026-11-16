-- Static existing crossbow bolt + a few animated trailing pixels, six phases.
local root=app.params.root
local pc=app.pixelColor
local palettes={
 saradomin={dark={226,190,39},light={255,243,116},mote={255,229,92}},
 zamorak={dark={43,35,49},light={100,75,95},mote={230,58,71}},
 guthix={dark={78,47,26},light={137,91,45},mote={126,200,70}}
}
local source=app.open(root..'/inputs/bolt-basic.png');local original=Image(source.cels[1].image);source:close()
assert(original.width==19 and original.height==16)
for _,god in ipairs({'saradomin','zamorak','guthix'})do
 local p=palettes[god];local sprite=Sprite(192,32,ColorMode.RGB)
 local particles=sprite.cels[1].image;sprite.layers[1].name='Small fading trail - six phases'
 local boltLayer=sprite:newLayer();boltLayer.name='Existing bolt - fixed geometry across every phase'
 local bolts=Image(192,32,ColorMode.RGB)
 local function rgba(c,a)return pc.rgba(c[1],c[2],c[3],a)end
 for f=0,5 do
  local ox=f*32
  for i=0,9 do
   local age=(f+i)%6
   local jitter=(i%5)-2
   local x=10-math.floor(age*1.7)+jitter
   local y=20+age+(i%2)
   local alpha=235-age*23
   particles:drawPixel(ox+x,y,rgba(p.mote,alpha))
   if i%3==0 then particles:drawPixel(ox+x+1,y,rgba(p.mote,alpha-25))end
   if god=='saradomin' and age<2 and i==0 then particles:drawPixel(ox+x,y+1,rgba(p.light,alpha-25))end
  end
  for y=0,15 do for x=0,18 do
   local c=original:getPixel(x,y);local a=pc.rgbaA(c)
   if a>0 then
    local palette=pc.rgbaR(c)>90 and p.light or p.dark
    bolts:drawPixel(ox+10+x,4+y,rgba(palette,a))
   end
  end end
 end
 sprite:newCel(boltLayer,1,bolts,Point(0,0))
 sprite:saveAs(root..'/'..god..'/bolt.aseprite');sprite:saveCopyAs(root..'/'..god..'/bolt.png');sprite:close()
end
