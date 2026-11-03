local root=app.params["root"]
local names={"ichor-water","ichor-stone-seep","ichor-dirt-seep","divine-water-water","divine-water-stone-seep","divine-water-dirt-seep"}
for _,name in ipairs(names) do
 local material=string.find(name,"stone") and "stone-2" or (string.find(name,"dirt") and "dirt-29" or "water-1")
 local source=app.open(root.."/source/"..material..".png")
 source.layers[1].name="Native source (unchanged)"
 local candidate=app.open(root.."/candidates/"..name..".png")
 local image=Image(candidate.cels[1].image)
 app.activeSprite=source
 local layer=source:newLayer();layer.name="Controlled recolor / seep pixels"
 local mask=app.open(root.."/masks/"..name..".png")
 local maskImage=mask.cels[1].image
 for y=0,image.height-1 do for x=0,image.width-1 do
  local p=maskImage:getPixel(x,y)
  if app.pixelColor.rgbaR(p)==0 then image:putPixel(x,y,app.pixelColor.rgba(0,0,0,0)) end
 end end
 source:newCel(layer,1,image,Point(0,0))
 source:saveAs(root.."/aseprite/"..name..".aseprite")
 source:close();candidate:close();mask:close()
end
