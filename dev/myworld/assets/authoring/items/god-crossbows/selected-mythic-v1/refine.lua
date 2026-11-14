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
 if god=='guthix'then
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
  -- Match the existing dark red string; only fill its transparent right-side gaps.
  line(cleanup,23,20,36,23,0x51050c,true,original)
  -- Recolour the existing arrow pixels to the same black/charcoal palette as horns.
  -- Keep its original silhouette, with no white redraw or larger replacement head.
  for y=6,20 do for x=1,46 do
   local centre=36-(y-6)*1.3
   local arrow=(y<=9 and x>=32 and x<=36)or math.abs(x-centre)<=1
   local c=original:getPixel(x,y)
   local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
   if arrow and pc.rgbaA(c)>0 and r>g*1.5 and r>b*1.5 then
    dot(cleanup,x,y,r>=200 and 0x45424d or(r>=122 and 0x2c2932 or 0x19171d))
   end
  end end
 end
 local detail=sprite:newLayer();detail.name='Continuous strings and contrast arrow';sprite:newCel(detail,1,cleanup,Point(0,0));app.layer=detail
 sprite:saveAs(base..god..'-crossbow.aseprite');sprite:saveCopyAs(base..god..'-crossbow.png');sprite:close();src:close()
end
