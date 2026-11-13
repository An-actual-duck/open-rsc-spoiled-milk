-- Native cleanup of the owner's selected option 1 for each god.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/items/god-crossbows/selected-mythic-v1/'
local pc=app.pixelColor
local function color(n)return pc.rgba((n>>16)&255,(n>>8)&255,n&255,255)end
local function dot(image,x,y,c)assert(x>0 and x<47 and y>0 and y<31);image:drawPixel(x,y,color(c))end
local function line(image,x0,y0,x1,y1,c,onlyEmpty,source)
 local dx=math.abs(x1-x0);local sx=x0<x1 and 1 or -1
 local dy=-math.abs(y1-y0);local sy=y0<y1 and 1 or -1;local err=dx+dy
 while true do
  if not onlyEmpty or pc.rgbaA(source:getPixel(x0,y0))==0 then dot(image,x0,y0,c)end
  if x0==x1 and y0==y1 then break end
  local e=2*err;if e>=dy then err=err+dy;x0=x0+sx end
  if e<=dx then err=err+dx;y0=y0+sy end
 end
end
local function poly(image,points,c)
 for y=1,30 do for x=1,46 do
  local inside=false;local j=#points
  for i=1,#points do
   local a,b=points[i],points[j]
   if (a[2]>y+0.5)~=(b[2]>y+0.5) and x+0.5<(b[1]-a[1])*(y+0.5-a[2])/(b[2]-a[2])+a[1]then inside=not inside end
   j=i
  end
  if inside then dot(image,x,y,c)end
 end end
end
for _,god in ipairs({'saradomin','zamorak','guthix'})do
 local src=app.open(base..'inputs/'..god..'.png');local original=Image(src.cels[1].image)
 assert(original.width==48 and original.height==32)
 local sprite=Sprite(48,32,ColorMode.RGB);local reinforcement=Image(48,32,ColorMode.RGB)
 if god=='zamorak'then
  poly(reinforcement,{{4,27},{5,25},{10,23},{14,20},{18,17},{23,13},{27,11},{30,11},{29,14},{25,16},{21,19},{17,23},{13,25},{9,29},{4,29}},0x7a0912)
 elseif god=='guthix'then
  poly(reinforcement,{{4,17},{12,15},{21,15},{27,16},{30,16},{30,19},{25,19},{20,20},{13,20},{7,21},{4,21}},0x603a23)
 end
 sprite.cels[1].image=reinforcement;sprite.layers[1].name='Stock reinforcement beneath selected design'
 local body=sprite:newLayer();body.name='Selected option 1 — original pixels';sprite:newCel(body,1,original,Point(0,0));app.layer=body
 local cleanup=Image(48,32,ColorMode.RGB)
 if god=='saradomin'then
  -- Only fill transparent gaps on the two existing string paths.
  line(cleanup,42,8,28,17,0xfff7d6,true,original)
  line(cleanup,6,21,22,17,0xfff7d6,true,original)
 elseif god=='zamorak'then
  -- Connect both string segments, distinct from stock and horns.
  line(cleanup,13,11,17,17,0xaaa3ad)
  line(cleanup,24,20,37,23,0xaaa3ad)
  -- A continuous pale-metal arrow stands out against the red body.
  line(cleanup,18,20,35,7,0xd4d3df)
  poly(cleanup,{{36,6},{32,7},{35,10}},0xd4d3df)
  dot(cleanup,36,6,0xf3f1f5);dot(cleanup,35,7,0xf3f1f5)
 end
 local detail=sprite:newLayer();detail.name='Continuous strings and contrast arrow';sprite:newCel(detail,1,cleanup,Point(0,0));app.layer=detail
 sprite:saveAs(base..god..'-crossbow.aseprite');sprite:saveCopyAs(base..god..'-crossbow.png');sprite:close();src:close()
end
