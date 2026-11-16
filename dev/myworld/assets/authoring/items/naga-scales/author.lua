-- Small native pixel-art edit of the existing game-owned scale silhouette.
local root=app.params.root
local s=app.open(root..'/references/items/materials/bones-and-monster-parts/id-02296.png')
local pc=app.pixelColor
local source=Image(s.cels[1].image)
local out=Image(48,32)
local palette={ [0x3e48a5]=0x345635,[0x5865bc]=0x4f7946,[0x737ed9]=0x719956,[0x949df8]=0xa0bb7c,[0x272789]=0x243e2a }
-- Preserve the scale; the three orange pixels belong to the reference's stray edge.
for y=0,31 do for x=0,47 do
 local c=source:getPixel(x,y)
 local rgb=(pc.rgbaR(c)<<16)|(pc.rgbaG(c)<<8)|pc.rgbaB(c)
 if pc.rgbaA(c)>0 and palette[rgb] then
  local q=palette[rgb];out:drawPixel(x,y,pc.rgba((q>>16)&255,(q>>8)&255,q&255,255))
 end
end end
s.cels[1].image=out
s:saveAs(root..'/authoring/items/naga-scales/naga-scales.aseprite')
s:saveCopyAs(root..'/runtime/sprites/items/inventory-ground/resources/naga-scales.png')
s:close()
