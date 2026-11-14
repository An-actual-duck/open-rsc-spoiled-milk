local root=app.params['root'];local pc=app.pixelColor;local base=root..'/dev/myworld/assets/authoring/items/bat-wings/'
local src=Image{fromFile=base..'inputs/generated-wing.png'};local im=Image(48,32,ColorMode.RGB)
local palette={{97,97,97},{124,124,124},{182,182,182},{108,68,18},{146,103,33},{175,132,58}}
for y=0,18 do for x=0,33 do
 local c=src:getPixel(math.floor(162+(x+.5)*1173/34),math.floor(164+(y+.5)*659/19))
 if pc.rgbaA(c)>=230 then
  local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c);local brown=r>b+20 and g>b+8;local best,score=nil,math.huge
  for i,p in ipairs(palette)do if (brown and i>=4)or(not brown and i<=3)then local d=(r-p[1])^2+(g-p[2])^2+(b-p[3])^2;if d<score then best=p;score=d end end end
  im:drawPixel(7+x,6+y,pc.rgba(best[1],best[2],best[3],255))
 end
end end
local s=Sprite(48,32,ColorMode.RGB);s:newCel(s.layers[1],1,im,Point(0,0));s:saveAs(base..'bat-wing.aseprite')
im:saveAs(root..'/dev/myworld/assets/runtime/sprites/items/inventory-ground/bat-wing.png')
im:saveAs(root..'/output/art/bat-wing-drops/bat-wing.png');s:close()

-- Palette-only variants: preserve every native pixel, outline and texture cluster.
local variants={
 {name='stirge-wing',colors={{153,130,95},{193,168,124},{228,208,164},{108,68,18},{146,103,33},{175,132,58}}},
 {name='death-wing-wing',colors={{22,23,27},{43,45,51},{76,79,87},{42,34,27},{65,53,38},{91,76,53}}}
}
for _,variant in ipairs(variants)do
 local map={};for i,p in ipairs(palette)do local c=variant.colors[i];map[pc.rgba(p[1],p[2],p[3],255)]=pc.rgba(c[1],c[2],c[3],255)end
 local image=Image(im)
 for y=0,31 do for x=0,47 do local pixel=image:getPixel(x,y);if pc.rgbaA(pixel)>0 then assert(map[pixel]);image:drawPixel(x,y,map[pixel])end end end
 local master=Sprite(48,32,ColorMode.RGB);master:newCel(master.layers[1],1,image,Point(0,0));master:saveAs(base..variant.name..'.aseprite')
 image:saveAs(root..'/dev/myworld/assets/runtime/sprites/items/inventory-ground/'..variant.name..'.png')
 image:saveAs(root..'/output/art/bat-wing-colors/'..variant.name..'.png');master:close()
end
