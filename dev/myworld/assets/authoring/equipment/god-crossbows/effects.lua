local root=app.params.root
local palettes={saradomin={{242,208,105},{220,240,255}},zamorak={{152,32,50},{245,78,69}},guthix={{63,102,38},{151,197,91}}}
for god,p in pairs(palettes) do
 local function rgba(rgb,a)return app.pixelColor.rgba(rgb[1],rgb[2],rgb[3],a or 255)end
 local spr=Sprite(128,16,ColorMode.RGB);local im=spr.cels[1].image
 for f=0,3 do
  local x=f*32
  for i=8,24 do im:drawPixel(x+i,8,rgba(p[1]))end
  for i=0,3 do im:drawPixel(x+24-i,8-i,rgba(p[2]));im:drawPixel(x+24-i,8+i,rgba(p[2]))end
  for i=0,2 do im:drawPixel(x+8+i,7-i,rgba(p[1]));im:drawPixel(x+8+i,9+i,rgba(p[1]))end
  for i=0,3 do im:drawPixel(x+4+i*4,5+(i+f)%3,rgba(p[2],150))end
 end
 spr:saveAs(root..'/'..god..'/bolt.aseprite');spr:saveCopyAs(root..'/'..god..'/bolt.png');spr:close()
 local hit=Sprite(192,32,ColorMode.RGB);im=hit.cels[1].image
 for f=0,5 do
  local ox=f*32;local radius=2+f*2;local alpha=255-f*35
  for spoke=0,7 do
   local angle=spoke*math.pi/4
   for d=math.max(0,radius-3),radius do
    local xx=16+math.floor(math.cos(angle)*d+.5);local yy=16+math.floor(math.sin(angle)*d+.5)
    im:drawPixel(ox+xx,yy,rgba(p[spoke%2+1],alpha))
    if god=='guthix' and d==radius-1 then im:drawPixel(ox+xx+1,yy+1,rgba(p[1],alpha))end
    if god=='zamorak' and spoke%2==0 then im:drawPixel(ox+xx,yy+1,rgba(p[1],alpha))end
   end
  end
 end
 hit:saveAs(root..'/'..god..'/impact.aseprite');hit:saveCopyAs(root..'/'..god..'/impact.png');hit:close()
end
