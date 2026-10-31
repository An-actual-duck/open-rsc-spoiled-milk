local root=app.params['root']
local out=root..'/dev/myworld/blight-sigil-production'
local assets=root..'/dev/myworld/assets/sprites/items/inventory-ground/resources/sigils'
local palette={
 [app.pixelColor.rgba(65,74,86,255)]=app.pixelColor.rgba(55,55,55,255),
 [app.pixelColor.rgba(91,87,98,255)]=app.pixelColor.rgba(75,75,75,255),
 [app.pixelColor.rgba(95,102,112,255)]=app.pixelColor.rgba(95,95,95,255),
 [app.pixelColor.rgba(111,120,131,255)]=app.pixelColor.rgba(115,115,115,255),
 [app.pixelColor.rgba(129,134,145,255)]=app.pixelColor.rgba(190,190,190,255),
 [app.pixelColor.rgba(143,152,163,255)]=app.pixelColor.rgba(215,215,215,255),
 [app.pixelColor.rgba(159,168,179,255)]=app.pixelColor.rgba(235,235,235,255),
 [app.pixelColor.rgba(181,174,195,255)]=app.pixelColor.rgba(255,255,255,255)
}
for _,grade in ipairs({'stone','silver'}) do
 for _,form in ipairs({'carved','infused'}) do
  local s=app.open(root..'/dev/myworld/blight-sigil-candidate/blight-thorn-'..form..'.aseprite')
  if grade=='silver' then
   local cel=s.layers[1]:cel(1)
   local image=Image(cel.image)
   for y=0,image.height-1 do for x=0,image.width-1 do
    local c=image:getPixel(x,y)
    if palette[c] then image:putPixel(x,y,palette[c]) end
   end end
   cel.image=image;s.layers[1].name='Existing eight-tone silver substrate palette'
  end
  local name='blight-'..grade..'-'..(form=='carved' and 'uninfused' or 'infused')
  s:saveAs(out..'/aseprite/'..name..'.aseprite')
  s:saveCopyAs(assets..'/'..name..'.png');s:close()
 end
end
