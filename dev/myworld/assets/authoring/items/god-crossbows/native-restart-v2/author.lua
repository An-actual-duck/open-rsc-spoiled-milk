-- Native 48x32 authoring directly from the ordinary crossbow and god-mace themes.
-- No previous crossbow designs, generated concepts or resampled artwork as inputs.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/items/god-crossbows/native-restart-v2/'
local pc=app.pixelColor
local src=app.open(root..'/dev/myworld/assets/references/items/weapons/crossbows/id-02199.png')
local original=Image(src.cels[1].image)
assert(original.width==48 and original.height==32)
local function rgb(n)return pc.rgba((n>>16)&255,(n>>8)&255,n&255,255)end
local function dot(image,x,y,c)
 assert(x>=0 and x<48 and y>=0 and y<32,'Native canvas overflow')
 image:drawPixel(x,y,rgb(c))
end
local function line(image,x0,y0,x1,y1,c,clip)
 local dx=math.abs(x1-x0);local sx=x0<x1 and 1 or -1
 local dy=-math.abs(y1-y0);local sy=y0<y1 and 1 or -1;local err=dx+dy
 while true do
  local p=original:getPixel(x0,y0)
  if not clip or (pc.rgbaA(p)>0 and pc.rgbaR(p)>pc.rgbaG(p) and pc.rgbaG(p)>pc.rgbaB(p))then dot(image,x0,y0,c)end
  if x0==x1 and y0==y1 then break end
  local e=2*err;if e>=dy then err=err+dy;x0=x0+sx end
  if e<=dx then err=err+dx;y0=y0+sy end
 end
end
local styles={
 {name='zamorak',woodDark=0x6e1600,woodMid=0x9e1f00,woodLight=0xd23b21,limb=0x191919,limbShade=0x000000},
 {name='saradomin',woodDark=0x818181,woodMid=0xbababa,woodLight=0xdfdfdf,limb=0xd2ad35,limbShade=0xa5830a},
 {name='guthix',woodDark=0x644010,woodMid=0x9c7248,woodLight=0xad7f52,limb=0x92be6e,limbShade=0x5e8148}
}
for _,s in ipairs(styles)do
 local sprite=Sprite(48,32,ColorMode.RGB)
 local body=Image(original)
 for pixel in body:pixels()do
  local c=pixel()
  if pc.rgbaA(c)>0 then
   local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
   if r==88 and g==50 and b==29 then pixel(rgb(s.woodDark))
   elseif r==129 and g==83 and b==34 then pixel(rgb(s.woodMid))
   elseif r==184 and g==112 and b==48 then pixel(rgb(s.woodLight))
   elseif r==129 and g==134 and b==145 then
    if s.name=='zamorak'then pixel(0)else pixel(rgb(s.limb))end
   elseif pixel.y>=26 and (r==111 or r==82)then
    if s.name=='zamorak'then pixel(0)else pixel(rgb(s.limbShade))end
   end
  end
 end
 sprite.cels[1].image=body;sprite.layers[1].name='Original framework — stock and limb materials'
 local detail=Image(48,32,ColorMode.RGB)
 local name
 if s.name=='zamorak'then
  name='Continuous tapered horn limbs'
  -- Replace the complete ordinary bow, rather than decorating its ends.
  -- Both horns grow from the front mounting point and curl back to the string.
  local function stockAt(x,y)
   local c=original:getPixel(x,y)
   return pc.rgbaA(c)>0 and pc.rgbaR(c)>pc.rgbaG(c) and pc.rgbaG(c)>pc.rgbaB(c)
  end
  local function horn(points)
   for y=1,30 do for x=1,46 do
    local inside=false;local j=#points
    for i=1,#points do
     local a,b=points[i],points[j]
     if (a[2]>y+0.5)~=(b[2]>y+0.5) and x+0.5<(b[1]-a[1])*(y+0.5-a[2])/(b[2]-a[2])+a[1]then inside=not inside end
     j=i
    end
    if inside and not stockAt(x,y)then dot(detail,x,y,0x000000)end
   end end
  end
  horn({{14,27},{10,25},{7,22},{5,18},{5,14},{6,11},{8,9},{11,9},{12,10},{12,12},{10,14},{10,12},{10,11},{9,11},{8,13},{8,16},{10,20},{12,23},{16,25}})
  horn({{12,25},{17,26},{22,28},{28,29},{33,28},{36,26},{36,24},{35,23},{34,24},{34,25},{32,26},{27,27},{22,26},{17,24},{14,23}})
  -- Restrained grey highlights follow each horn's length, not a separate cap.
  for _,points in ipairs({{{13,25},{10,22},{8,18},{7,15},{8,12},{10,10}},{{15,26},{20,28},{25,29},{30,29},{34,27},{35,25}}})do
   for i=1,#points-1 do
    local a,b=points[i],points[i+1]
    local temp=Image(48,32,ColorMode.RGB);line(temp,a[1],a[2],b[1],b[2],0x323232)
    for pixel in temp:pixels()do
     if pc.rgbaA(pixel())>0 and pc.rgbaA(detail:getPixel(pixel.x,pixel.y))>0 then dot(detail,pixel.x,pixel.y,0x323232)end
    end
   end
  end
 elseif s.name=='saradomin'then
  name='Golden limb tips shaped into mace stars'
  local star={'   H   ',' G H G ','  GHG  ','HHHLHHH','  GHG  ',' G H G ','   H   '}
  local palette={G=0xa5830a,H=0xd2ad35,L=0xfcf9df}
  for _,centre in ipairs({{8,11},{36,27}})do
   for y,row in ipairs(star)do for x=1,#row do
    local c=palette[row:sub(x,x)];if c then dot(detail,centre[1]+x-4,centre[2]+y-4,c)end
   end end
  end
 else
  name='Dark green thorned brambles winding along stock'
  local points={{18,23},{21,23},{22,20},{25,20},{26,18},{29,18},{30,15},{33,15},{34,12},{37,12},{38,9},{41,9},{42,7}}
  for i=1,#points-1 do local a,b=points[i],points[i+1];line(detail,a[1],a[2],b[1],b[2],0x294528,true)end
  -- Short branching spines; keep the trigger, latch and string readable.
  for _,p in ipairs({{23,21,24,22},{27,18,27,20},{31,15,32,17},{35,12,35,14},{39,9,40,11}})do line(detail,p[1],p[2],p[3],p[4],0x294528,true)end
  line(detail,24,18,23,16,0x294528);dot(detail,23,15,0x294528)
  line(detail,32,13,31,11,0x294528);dot(detail,31,10,0x294528)
  dot(detail,26,18,0x415b31);dot(detail,34,12,0x415b31);dot(detail,38,9,0x415b31)
 end
 local layer=sprite:newLayer();layer.name=name;sprite:newCel(layer,1,detail,Point(0,0))
 -- Mechanical pixels are restored on top: same trigger, latch and taut string.
 local mechanical=Image(48,32,ColorMode.RGB)
 for pixel in mechanical:pixels()do
  local c=original:getPixel(pixel.x,pixel.y)
  local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
  if pc.rgbaA(c)>0 and pixel.y<26 and ((r==82 and g==82 and b==82)or(r==111 and g==120 and b==131))then pixel(c)end
 end
 local top=sprite:newLayer();top.name='Original trigger, latch and string';sprite:newCel(top,1,mechanical,Point(0,0))
 sprite:saveAs(base..s.name..'-crossbow-icon.aseprite');sprite:saveCopyAs(base..s.name..'-crossbow-icon.png');sprite:close()
end
src:close()
