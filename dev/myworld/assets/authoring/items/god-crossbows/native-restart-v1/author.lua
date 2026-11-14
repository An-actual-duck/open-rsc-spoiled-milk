-- Fresh native-size designs. No generated concepts or downscaled renders as inputs.
-- Source shape is the unchanged 48x32 standard/Phoenix crossbow reference.
-- Theme colours are taken directly from the existing god mace sprites.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/items/god-crossbows/native-restart-v1/'
local pc=app.pixelColor
local reference=app.open(root..'/dev/myworld/assets/references/items/weapons/crossbows/id-02199.png')
local original=Image(reference.cels[1].image)
assert(original.width==48 and original.height==32)
local function rgb(r,g,b)return pc.rgba(r,g,b,255)end
local styles={
 {name='saradomin', woodDark=rgb(165,85,11),woodMid=rgb(165,131,10),woodLight=rgb(186,186,186),
  limb=rgb(165,131,10), shades={H=rgb(165,131,10),L=rgb(252,249,223),W=rgb(252,249,223)},
  emblem={'   L   ',' H H H ','  HLH  ','LHLWLHL','  HLH  ',' H H H ','   L   '},x=31,y=10},
 {name='zamorak', woodDark=rgb(25,25,25),woodMid=rgb(50,50,50),woodLight=rgb(87,87,87),
  limb=rgb(158,31,0), shades={R=rgb(158,31,0),S=rgb(110,22,0)},
  emblem={' RR RR ','RS R SR','R     R',' SR RS ','  R R  '},x=31,y=11},
 {name='guthix',woodDark=rgb(100,61,16),woodMid=rgb(117,86,55),woodLight=rgb(173,127,82),
  limb=rgb(94,129,72),shades={H=rgb(173,127,82),M=rgb(156,114,72),S=rgb(100,61,16)},
  emblem={'  H  ',' HMH ','HMHMH','MHSHM','HMHMH',' HMH ','  S  '},x=32,y=10}
}
for _,style in ipairs(styles)do
 local sprite=Sprite(48,32,ColorMode.RGB)
 local body=Image(original)
 for pixel in body:pixels()do
  local c=pixel()
  if pc.rgbaA(c)>0 then
   local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
   if r==88 and g==50 and b==29 then pixel(style.woodDark)
   elseif r==129 and g==83 and b==34 then pixel(style.woodMid)
   elseif r==184 and g==112 and b==48 then pixel(style.woodLight)
   elseif r==129 and g==134 and b==145 then pixel(style.limb)
   end
   -- Original gray trigger, stock latch and string shades remain untouched.
  end
 end
 sprite.cels[1].image=body
 sprite.layers[1].name='Original crossbow framework — mace palette'
 local ornament=Image(48,32,ColorMode.RGB)
 for row,line in ipairs(style.emblem)do
  for col=1,#line do
   local value=style.shades[line:sub(col,col)]
   if value then
    local x,y=style.x+col-1,style.y+row-1
    local c=original:getPixel(x,y)
    -- Inset only into original wooden stock. Never cover mechanical metal parts.
    local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
    assert(pc.rgbaA(c)>0 and r>g and g>b,'Emblem must stay inside original stock')
    ornament:drawPixel(x,y,value)
   end
  end
 end
 local layer=sprite:newLayer();layer.name='Inset '..style.name..' emblem'
 sprite:newCel(layer,1,ornament,Point(0,0))
 sprite:saveAs(base..style.name..'-crossbow-icon.aseprite')
 sprite:saveCopyAs(base..style.name..'-crossbow-icon.png')
 sprite:close()
end
reference:close()
