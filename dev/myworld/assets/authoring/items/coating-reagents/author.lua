local root=app.params.root or '.'
local out=app.params.out or (root..'/dev/myworld/assets/authoring/items/coating-reagents/art')
local rgba=app.pixelColor.rgba
local function save(s,name)
 s:saveAs(out..'/aseprite/'..name..'.aseprite')
 s:saveCopyAs(out..'/png/'..name..'.png')
 s:close()
end
-- Original classic potion canvas, stopper, neck, bottle contour and lighting.
local s=app.open(root..'/dev/myworld/assets/references/items/consumables/potions-and-bottles/id-02198.png')
app.command.ChangePixelFormat{format='rgb'}
local cel=s.cels[1];local im=Image(cel.image)
for p in im:pixels()do
 local col=p();local r=app.pixelColor.rgbaR(col);local g=app.pixelColor.rgbaG(col);local b=app.pixelColor.rgbaB(col);local a=app.pixelColor.rgbaA(col)
 local y=p.y+cel.position.y;local x=p.x+cel.position.x
 if a>0 and y>=14 then
  local v=(r+g+b)/3
  local cloud=((x+2*y)%9<3 and y>19)and -14 or 0
  local rim=(r>=240 and g>=240 and b>=240)and 32 or 0
  p(rgba(math.max(12,math.floor(v*.24+cloud+rim)),math.max(32,math.floor(v*.49+cloud+rim)),math.max(34,math.floor(v*.46+cloud+rim)),a))
 end
end
cel.image=im;s.layers[1].name='Classic glass vial — opaque murky teal coating'
save(s,'viscous-coating')
-- Rounded organic sack with an asymmetric surface, slime sheeting and drips;
-- no iris, pupil or concentric eye shading.
local pal={
 ['o']={40,48,24},['d']={64,75,32},['b']={101,110,48},['l']={143,150,66},
 ['s']={48,91,45},['g']={79,133,55},['h']={143,185,83},['w']={197,219,134}
}
local rows={
 '      ooooo      ',
 '    oodllbdoo    ',
 '   odlllhhlbdo   ',
 '  odllhwwhglbdo  ',
 ' odllhhhgsglbdo  ',
 ' odlhhggssglbdo  ',
 'odlhgsssggglbddo ',
 'odlggsgghhglbddo ',
 'odggssghwwhgbddo ',
 'osggssghhhgbdddo ',
 'oshgssggggbdddo  ',
 'oshhggssgbdddo   ',
 ' oshhgssgdddo    ',
 ' ogshgssgdoo     ',
 '  oggsosgo       ',
 '  ogs  sgo       ',
 ' oohgo osggo     ',
 ' ooooo ooooo     '
}
s=Sprite(48,32,ColorMode.RGB);im=s.cels[1].image
for y,row in ipairs(rows)do for x=1,#row do
 local c=pal[row:sub(x,x)];if c then im:putPixel(x+14,y+10,rgba(c[1],c[2],c[3],255))end
end end
s.layers[1].name='Round gland — saliva coating and hanging slime'
save(s,'sticky-saliva-gland')
local sheet=Sprite(192,64,ColorMode.RGB);local dst=sheet.cels[1].image
dst:clear(rgba(42,45,50,255))
local files={
 root..'/dev/myworld/assets/authoring/items/coating-reagents/art/reference/viscous-coating-before.png',out..'/png/viscous-coating.png',
 root..'/dev/myworld/assets/references/items/materials/bones-and-monster-parts/id-02266.png',out..'/png/sticky-saliva-gland.png'}
for i,path in ipairs(files)do local src=Image{fromFile=path};dst:drawImage(src,Point((i-1)*48,16))end
sheet:saveCopyAs(out..'/review-native.png')
app.command.SpriteSize{width=1152,height=384,method='nearest-neighbor'}
sheet:saveCopyAs(out..'/review-6x.png');sheet:close()
