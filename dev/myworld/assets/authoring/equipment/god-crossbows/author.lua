-- Native held crossbow palette studies; preserve every pixel's alpha and position.
local root=app.params.root
local sets={
 saradomin={stock={190,200,213},limb={235,183,68},string={164,164,151},bolt={230,239,246}},
 zamorak={stock={176,35,43},limb={47,40,51},string={154,37,44},bolt={30,26,34}},
 guthix={stock={128,82,39},limb={132,174,85},string={91,120,57},bolt={192,175,117}}
}
for god,p in pairs(sets) do
 for frame=0,14 do
  local spr=app.open(root..'/inputs/'..string.format('%02d',frame)..'.png')
  local im=spr.cels[1].image
  for y=0,im.height-1 do for x=0,im.width-1 do
   local v=im:getPixel(x,y);local a=app.pixelColor.rgbaA(v)
   if a>0 then
    local r,g,b=app.pixelColor.rgbaR(v),app.pixelColor.rgbaG(v),app.pixelColor.rgbaB(v)
    local palette,shade
    if r==g and g==b then
     palette=r<100 and p.string or p.limb;shade=r/155
    elseif r>220 and g<180 then palette=p.bolt;shade=.8+g/700
    else palette=p.stock;shade=math.max(.35,math.min(1.15,r/140))
     if god=='guthix' and (x+y*2)%9==0 then palette={49,86,37};shade=1 end
    end
    local function channel(c)return math.max(1,math.min(255,math.floor(c*shade+.5)))end
    im:drawPixel(x,y,app.pixelColor.rgba(channel(palette[1]),channel(palette[2]),channel(palette[3]),a))
   end
  end end
  spr:saveAs(root..'/'..god..'/'..string.format('%02d',frame)..'.aseprite')
  spr:saveCopyAs(root..'/'..god..'/'..string.format('%02d',frame)..'.png')
  spr:close()
 end
end
