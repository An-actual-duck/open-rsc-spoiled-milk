local root=app.params['root']
local source=app.params['source']
-- One continuous crooked stem and two downward thorn hooks; no upward horns.
local rows={
 '.......##....',
 '......##.....',
 '.....##......',
 '.....##......',
 '......##.....',
 '......##.....',
 '.....####....',
 '...###..###..',
 '..##......##.',
 '.##........##',
 '.##........##',
 '.##........##',
 '..##......##.',
 '...#......#..'
}
for _,form in ipairs({'carved','infused'}) do
 local s=app.open(source..'/stone.png');s.layers[1].name='Owner original stone - unchanged'
 local layer=s:newLayer();layer.name='Thorn glyph - '..form
 local image=Image(28,25,ColorMode.RGB)
 local color=form=='infused' and app.pixelColor.rgba(96,24,100,255) or app.pixelColor.rgba(160,160,160,255)
 for y,row in ipairs(rows) do
  for x=1,#row do if string.sub(row,x,x)=='#' then image:putPixel(x+6,y+3,color) end end
 end
 s:newCel(layer,1,image,Point(0,0));s:saveAs(root..'/blight-thorn-'..form..'.aseprite');s:saveCopyAs(root..'/blight-thorn-'..form..'-28x25.png')
 local full=Sprite(48,32,ColorMode.RGB);full.layers[1].name='Inventory preview'
 local c=Image(48,32,ColorMode.RGB);c:drawImage(s.cels[1].image,Point(13,4));c:drawImage(image,Point(13,4));full:newCel(full.layers[1],1,c,Point(0,0));full:saveAs(root..'/blight-thorn-'..form..'-48x32.png');full:close();s:close()
end
