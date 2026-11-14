-- Native-size authoring export; source is preserved unchanged alongside this recipe.
local base=app.params['root']..'/dev/myworld/assets/authoring/monsters/stirge/flight-v2/'
local pc=app.pixelColor
local palette={0x23181a,0x341e15,0x4d3019,0x6a471f,0x86632d,0xa47f3f,0xc69b55,0xdbb575,
 0x623136,0x8d4e4e,0xb86c66,0x4f3032,0x6c4242,0x7e4c49,0x945a55,0xa66d63,0xbf8d7c,
 0xb3976d,0xd0b585,0xe0c698,0x311922,0x4b212c,0x68303a,0x843e49,0xa14c56,0xedcf69,0xfff2a0}
local source=app.open(base..'inputs/generated-front-anatomy.png')
local input=Image(source.cels[1].image)
local sprite=Sprite(150,130,ColorMode.RGB)
local result=Image(150,130,ColorMode.RGB)
for y=0,129 do for x=0,149 do
 local sx=math.min(input.width-1,math.floor((x+0.5)*input.width/150))
 local sy=math.min(input.height-1,math.floor((y+0.5)*input.height/130))
 local c=input:getPixel(sx,sy)
 if pc.rgbaA(c)>=220 then
  local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
  local best,dist=0,1e30
  for _,rgb in ipairs(palette)do
   local pr=(rgb>>16)&255;local pg=(rgb>>8)&255;local pb=rgb&255
   local d=(r-pr)^2+(g-pg)^2+(b-pb)^2
   if d<dist then best=rgb;dist=d end
  end
  result:drawPixel(x,y,pc.rgba((best>>16)&255,(best>>8)&255,best&255,255))
 end
end end
sprite.cels[1].image=result
sprite.layers[1].name='Front underside anatomy guide'
sprite:saveAs(base..'front-anatomy-source.aseprite')
sprite:saveCopyAs(base..'front-anatomy-source.png')
sprite:close();source:close()
