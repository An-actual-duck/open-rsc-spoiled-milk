-- Static existing crossbow bolt + a few animated trailing pixels, six phases.
local root=app.params.root
local pc=app.pixelColor
local palettes={
 saradomin={dark={184,134,48},light={248,221,151},mote={210,236,255}},
 zamorak={dark={43,35,49},light={100,75,95},mote={208,56,67}},
 guthix={dark={123,84,43},light={190,157,95},mote={125,177,78}}
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
  for i=0,4 do
   local age=(f+i*2)%6
   local jitter=(i%3)-1
   local x=10-math.floor(age*1.5)+jitter
   local y=20+age+jitter
   local alpha=185-age*27
   particles:drawPixel(ox+x,y,rgba(p.mote,alpha))
   if god=='saradomin' and age<2 and i==0 then particles:drawPixel(ox+x+1,y,rgba(p.light,alpha-35))end
   if god=='guthix' and age<2 and i==1 then particles:drawPixel(ox+x,y+1,rgba(p.mote,alpha-35))end
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
