local root=app.params['root'];local pc=app.pixelColor
local function export(source,bounds,width,height,name,dest)
 local src=Image{fromFile=source};local s=Sprite(48,32,ColorMode.RGB);local im=Image(48,32,ColorMode.RGB)
 for y=0,height-1 do for x=0,width-1 do
  local sx=math.floor(bounds[1]+(x+.5)*bounds[3]/width);local sy=math.floor(bounds[2]+(y+.5)*bounds[4]/height)
  local c=src:getPixel(sx,sy)
  if pc.rgbaA(c)>=230 then
   -- Small channel quantization and binary alpha remove generated preview haze.
   local function q(v)return math.min(255,math.floor(v/16+.5)*16)end
   im:drawPixel(16+x,3+y,pc.rgba(q(pc.rgbaR(c)),q(pc.rgbaG(c)),q(pc.rgbaB(c)),255))
  end
 end end
 s:newCel(s.layers[1],1,im,Point(0,0));s:saveAs(dest..'/'..name..'.aseprite');im:saveAs(root..'/dev/myworld/assets/runtime/sprites/items/inventory-ground/'..name..'.png');im:saveAs(root..'/output/art/stirge-encounter/'..name..'.png');s:close()
end
export(root..'/dev/myworld/assets/authoring/items/bug-off/inputs/generated-can.png',{616,436,298,427},16,25,'bug-off',root..'/dev/myworld/assets/authoring/items/bug-off')
export(root..'/dev/myworld/assets/authoring/items/sanguinary-gland/inputs/generated-gland.png',{542,262,403,523},16,21,'sanguinary-gland',root..'/dev/myworld/assets/authoring/items/sanguinary-gland')
