-- Shared colour-mask bases. Original reference files remain untouched.
local root=app.params['root'];local out=app.params['out']
local rgba=app.pixelColor.rgba
local function author(relative,name,kind)
 local sprite=app.open(root..'/dev/myworld/assets/references/items/'..relative)
 app.command.ChangePixelFormat{format='rgb'}
 local image=Image(sprite.cels[1].image)
 for pixel in image:pixels()do
  local c=pixel();local r,g,b,a=app.pixelColor.rgbaR(c),app.pixelColor.rgbaG(c),app.pixelColor.rgbaB(c),app.pixelColor.rgbaA(c)
  if a>0 then
   if kind=='plain'then local shade=r==151 and 208 or 255;pixel(rgba(shade,shade,shade,a))
   elseif kind=='orb'then
    if r~=g or g~=b then local shade=r==154 and 208 or 254;pixel(rgba(shade,shade,255,a))end
   elseif kind=='crossbow'then
    -- Keep every string/metal pixel intact; only the three brown wood tones change.
    if r>g and g>b then local shade=r==88 and 140 or(r==129 and 199 or 254);pixel(rgba(shade,shade,255,a))end
   elseif kind=='log'then
    local shade=math.min(255,math.floor(r*255/143));pixel(rgba(shade,shade,shade,a))
   elseif kind=='bolt'or kind=='poison'then
    local shade=r==65 and 145 or 230
    if kind=='poison'and pixel.x+pixel.y>=48 then pixel(rgba(35,165,48,a))
    else pixel(rgba(shade,shade,shade,a))end
   end
  end
 end
 sprite.cels[1].image=image;sprite.layers[1].name='Shared material mask: '..name
 sprite:saveAs(out..'/aseprite/'..name..'.aseprite');sprite:saveCopyAs(out..'/png/'..name..'.png');sprite:close()
end
author('weapons/staves/id-02235.png','staff-wood-base','plain')
author('weapons/staves/id-02241.png','staff-orb-base','orb')
author('weapons/crossbows/id-02199.png','crossbow-wood-base','crossbow')
author('ammunition/bolts/id-02206.png','metal-bolts-base','bolt')
author('ammunition/bolts/id-02206.png','metal-bolts-poison-base','poison')
author('materials/wood/id-02164.png','log-wood-base','log')
