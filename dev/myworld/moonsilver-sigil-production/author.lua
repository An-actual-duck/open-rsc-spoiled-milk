local root=app.params['root'] or '/home/justin/Core-Framework'
local out=app.params['out'] or '/home/justin/.local/share/moonsilver-sigil-art-20261101/v2'
local assets=root..'/dev/myworld/assets/sprites/items/inventory-ground/resources/sigils'
local rgba=app.pixelColor.rgba
local stone={{65,74,86},{91,87,98},{95,102,112},{111,120,131},{129,134,145},{143,152,163},{159,168,179},{181,174,195}}
local silver={55,75,95,115,190,215,235,255}
local moon={{20,32,66},{30,48,87},{47,68,111},{70,95,143},{128,155,195},{165,189,219},{204,221,241},{235,244,254}}
local silverMap,moonMap,stoneMoon={},{},{}
for i=1,#silver do
 local s=stone[i];local m=moon[i]
 silverMap[rgba(s[1],s[2],s[3],255)]=rgba(silver[i],silver[i],silver[i],255)
 moonMap[rgba(silver[i],silver[i],silver[i],255)]=rgba(m[1],m[2],m[3],255)
 stoneMoon[rgba(s[1],s[2],s[3],255)]=rgba(m[1],m[2],m[3],255)
end
local function author(source,name,palette)
 local s=app.open(source)
 app.command.ChangePixelFormat{format='rgb'}
 local cel=s.cels[1];local im=Image(cel.image)
 for pixel in im:pixels()do if palette[pixel()]then pixel(palette[pixel()])end end
 cel.image=im;s.layers[1].name=name..' — native shape and placement retained'
 s:saveAs(out..'/aseprite/'..name..'.aseprite');s:saveCopyAs(out..'/png/'..name..'.png');s:close()
end
local barMap={
 [rgba(110,110,110,255)]=rgba(30,48,82,255),
 [rgba(140,140,140,255)]=rgba(80,109,155,255),
 [rgba(163,163,163,255)]=rgba(145,176,216,255),
 [rgba(187,187,187,255)]=rgba(188,211,239,255),
 [rgba(217,217,217,255)]=rgba(228,239,253,255)
}
author(root..'/dev/myworld/reference-library/items/materials/ores-bars-and-rocks/id-02229.png','moonsilver-bar',barMap)
author(assets..'/stone.png','blank-silver-sigil',silverMap)
author(assets..'/stone.png','blank-moonsilver-sigil',stoneMoon)
for _,family in ipairs({'sara','guth','zam','neutral'})do
 for _,state in ipairs({'unblessed','blessed'})do
  author(assets..'/silver-'..state..'-'..family..'-sigil.png','moonsilver-'..state..'-'..family..'-sigil',moonMap)
 end
end
for _,state in ipairs({'uninfused','infused'})do
 author(assets..'/blight-silver-'..state..'.png','moonsilver-'..state..'-blight-sigil',moonMap)
end
-- Native48x32 mould: a small three-recess casting plate following existing mould shading.
local s=Sprite(48,32,ColorMode.RGB);local im=s.cels[1].image
local function rect(x0,y0,x1,y1,c)for y=y0,y1 do for x=x0,x1 do im:putPixel(x,y,c)end end end
rect(8,5,39,25,rgba(107,107,107,255));rect(8,4,37,23,rgba(202,202,202,255));rect(7,6,8,21,rgba(235,235,235,255));rect(9,3,35,4,rgba(229,229,229,255));rect(38,6,39,24,rgba(128,128,128,255));rect(9,24,37,25,rgba(126,126,126,255))
local ring={{-2,-5},{-1,-5},{0,-5},{1,-5},{2,-5},{-4,-4},{-3,-4},{3,-4},{4,-4},{-5,-3},{5,-3},{-5,-2},{5,-2},{-5,-1},{5,-1},{-5,0},{5,0},{-5,1},{5,1},{-5,2},{5,2},{-4,3},{4,3},{-3,4},{-2,4},{-1,4},{0,4},{1,4},{2,4},{3,4}}
for _,pos in ipairs({{17,10},{30,10},{23,18}})do
 for dy=-4,3 do for dx=-4,4 do if dx*dx+dy*dy<20 then im:putPixel(pos[1]+dx,pos[2]+dy,rgba(164,164,164,255))end end end
 for _,pt in ipairs(ring)do
  local dot=pt[1]+pt[2]
  local shade=dot < -3 and rgba(112,112,112,255)or(dot > 3 and rgba(243,243,243,255)or rgba(175,175,175,255))
  im:putPixel(pos[1]+pt[1],pos[2]+pt[2],shade)
 end
end
s.layers[1].name='Reusable three-blank casting mould'
s:saveAs(out..'/aseprite/sigil-mould.aseprite');s:saveCopyAs(out..'/png/sigil-mould.png');s:close()
-- Equal-size native contact sheet; no source/candidate is resized to fill a cell.
local names={'silver-reference-bar','moonsilver-bar','blank-silver-sigil','blank-moonsilver-sigil','sigil-mould'}
for _,state in ipairs({'unblessed','blessed'})do for _,family in ipairs({'sara','guth','zam','neutral','blight'})do
 names[#names+1]=family=='blight'and('moonsilver-'..(state=='unblessed'and'uninfused'or'infused')..'-blight-sigil')or('moonsilver-'..state..'-'..family..'-sigil')
end end
local sheet=Sprite(240,96,ColorMode.RGB);local dst=sheet.cels[1].image
dst:clear(rgba(38,41,47,255))
for i,name in ipairs(names)do
 local image=Image{fromFile=name=='silver-reference-bar'and(root..'/dev/myworld/reference-library/items/materials/ores-bars-and-rocks/id-02229.png')or(out..'/png/'..name..'.png')}
 local ox=((i-1)%5)*48;local oy=math.floor((i-1)/5)*32
 dst:drawImage(image,Point(ox+math.floor((48-image.width)/2),oy+math.floor((32-image.height)/2)))
end
sheet:saveCopyAs(out..'/review-native.png')
app.command.SpriteSize{width=960,height=384,method='nearest-neighbor'}
sheet:saveCopyAs(out..'/review-4x.png');sheet:close()
