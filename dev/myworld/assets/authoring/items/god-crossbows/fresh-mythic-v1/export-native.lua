-- Native 48x32 exports of nine independent reference-free imagegen designs.
-- No old crossbows, maces, symbols or other image references are inputs.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/items/god-crossbows/fresh-mythic-v1/'
local pc=app.pixelColor
local palettes={
 saradomin={0x211e27,0x45424f,0x696775,0x8b8997,0xb0aebc,0xd4d3df,0xf3f1f5,0x503214,0x79501b,0xa16b1d,0xc4942d,0xe5b84d,0xffd66d,0xffed9d,0xfff7d6},
 zamorak={0x09080b,0x19171d,0x2c2932,0x45424d,0x625d6b,0x807a89,0x2d0308,0x51050c,0x7a0912,0xa40c1a,0xcc1422,0xe82a34,0xff4950,0x8a2733},
 guthix={0x20180f,0x3d2718,0x603a23,0x875332,0xb47749,0xd19a61,0xe4bb85,0x17230c,0x2c4217,0x496423,0x688533,0x8ba848,0xb8ce6b,0xdce599,0x748665,0xa5b58c}
}
for _,god in ipairs({'saradomin','zamorak','guthix'})do for option=1,3 do
 local id=god..'-'..option
 local src=app.open(base..'sources/'..id..'.png');local input=Image(src.cels[1].image)
 local sprite=Sprite(48,32,ColorMode.RGB);local image=Image(48,32,ColorMode.RGB)
 for y=0,31 do for x=0,47 do
  local sx=math.floor((x+0.5)*input.width/48);local sy=math.floor((y+0.5)*input.height/32)
  local c=input:getPixel(sx,sy)
  if x>0 and x<47 and y>0 and y<31 and pc.rgbaA(c)>=220 then
   local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c);local best,dist=0,1e30
   for _,rgb in ipairs(palettes[god])do
    local pr=(rgb>>16)&255;local pg=(rgb>>8)&255;local pb=rgb&255
    local d=(r-pr)^2+(g-pg)^2+(b-pb)^2
    if d<dist then best=rgb;dist=d end
   end
   image:drawPixel(x,y,pc.rgba((best>>16)&255,(best>>8)&255,best&255,255))
  end
 end end
 sprite.cels[1].image=image;sprite.layers[1].name=id..' — native 48x32'
 sprite:saveAs(base..'native/'..id..'.aseprite');sprite:saveCopyAs(base..'native/'..id..'.png')
 sprite:close();src:close()
end end
