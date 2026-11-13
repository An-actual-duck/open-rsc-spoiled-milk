-- Export approved, imagegen-refined designs onto the actual RSC inventory grid.
-- Nearest source cell sampling, binary alpha, hand-picked limited palette.
-- The source references and generated masters remain untouched.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/items/god-crossbows/'
local pc=app.pixelColor
local palettes={
 saradomin={0x33221a,0x613711,0x90531f,0xb97939,0xd39a52,0x78601b,0xc69b24,0xffdc60,0x60616d,0x9697a4,0xcdced4,0xf8f1cc},
 zamorak={0x17151b,0x302033,0x4f3555,0x79547d,0xa81d22,0xd42e32,0xf04a45,0x45434e,0x767581,0xa6a7ae,0xd1d1d4,0x21191f},
 guthix={0x332319,0x68401e,0x93602b,0xbe8748,0x293e1e,0x496b31,0x7da44e,0x8a632d,0xc69d55,0xf0d38b,0x777b80,0xc1c5ca}
}
for _,god in ipairs({'saradomin','zamorak','guthix'})do
 local src=app.open(base..'native-inputs/'..god..'.png')
 local input=Image(src.cels[1].image)
 local out=Sprite(48,32,ColorMode.RGB)
 local image=Image(48,32,ColorMode.RGB)
 for y=0,31 do for x=0,47 do
  -- Shared full-canvas sampling preserves relative scale; no independent autofit.
  local sx=math.floor((x-2+0.5)*input.width/48)
  local sy=math.floor((y+0.5)*input.height/32)
  if sx>=0 and sx<input.width and sy>=0 and sy<input.height then
   local c=input:getPixel(sx,sy)
   if pc.rgbaA(c)>=220 then
    local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
    local best,dist=0,1e30
    for _,rgb in ipairs(palettes[god])do
     local pr=(rgb>>16)&255;local pg=(rgb>>8)&255;local pb=rgb&255
     local d=(r-pr)^2+(g-pg)^2+(b-pb)^2
     if d<dist then best=rgb;dist=d end
    end
    image:drawPixel(x,y,pc.rgba((best>>16)&255,(best>>8)&255,best&255,255))
   end
  end
 end end
 -- Native-grid cleanup: continuous one-pixel string and readable trigger.
 local function dot(x,y,rgb) image:drawPixel(x,y,pc.rgba((rgb>>16)&255,(rgb>>8)&255,rgb&255,255))end
 local function line(x0,y0,x1,y1,rgb)
  local dx=math.abs(x1-x0);local sx=x0<x1 and 1 or -1
  local dy=-math.abs(y1-y0);local sy=y0<y1 and 1 or -1;local err=dx+dy
  while true do
   dot(x0,y0,rgb);if x0==x1 and y0==y1 then break end
   local e=2*err;if e>=dy then err=err+dy;x0=x0+sx end
   if e<=dx then err=err+dx;y0=y0+sy end
  end
 end
 if god=='saradomin'then
  line(10,11,28,26,0xcdced4)
  line(32,17,32,19,0x9697a4);dot(31,19,0x9697a4)
  -- Five-pixel-span star retains the holy pendant's four long points.
  for _,v in ipairs({{39,9},{39,10},{37,11},{38,11},{40,11},{41,11},{39,12},{39,13}})do dot(v[1],v[2],0xffdc60)end
  dot(39,11,0xf8f1cc)
 elseif god=='zamorak'then
  line(12,11,32,25,0xa6a7ae)
  line(34,16,34,18,0xa6a7ae);dot(33,18,0xa6a7ae)
 else
  line(10,12,30,27,0xc1c5ca)
  line(32,17,32,19,0xc1c5ca);dot(31,19,0xc1c5ca)
 end
 out.cels[1].image=image
 out.layers[1].name=god..' crossbow — native 48x32'
 local palette=Palette(#palettes[god]+1)
 palette:setColor(0,Color{r=0,g=0,b=0,a=0})
 for i,rgb in ipairs(palettes[god])do palette:setColor(i,Color{r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255,a=255})end
 out:setPalette(palette)
 out:saveAs(base..'native-v1/'..god..'-crossbow-icon.aseprite')
 out:saveCopyAs(base..'native-v1/'..god..'-crossbow-icon.png')
 out:close();src:close()
end
