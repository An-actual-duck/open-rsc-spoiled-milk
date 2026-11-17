-- Original AI concept sources; deterministic native-size preparation inside Aseprite.
-- No game reference artwork was supplied to generation or sampled here.
local root=assert(app.params.root)
local out=assert(app.params.out)
local pc=app.pixelColor
for _,v in ipairs({{'a','Rough clusters',20},{'b','Balanced shading',28},{'c','Scruffy outlines',24}}) do
 local original=app.open(root..'/sources/'..v[1]..'-generated.png')
 local im=original.cels[1].image
 local x0,y0,x1,y1=im.width,im.height,-1,-1
 for it in im:pixels() do
  if pc.rgbaA(it())>=128 then
   x0=math.min(x0,it.x);y0=math.min(y0,it.y);x1=math.max(x1,it.x);y1=math.max(y1,it.y)
  end
 end
 assert(x1>x0 and y1>y0)
 local h=104
 local w=math.floor((x1-x0+1)*h/(y1-y0+1)+0.5)
 assert(w<=110)
 local ox,oy=math.floor((128-w)/2),118-h
 local image=Image(128,128,ColorMode.RGB)
 for y=0,h-1 do for x=0,w-1 do
  local sx=x0+math.min(x1-x0,math.floor((x+0.5)*(x1-x0+1)/w))
  local sy=y0+math.min(y1-y0,math.floor((y+0.5)*(y1-y0+1)/h))
  local c=im:getPixel(sx,sy)
  if pc.rgbaA(c)>=128 then image:drawPixel(ox+x,oy+y,pc.rgba(pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c),255)) end
 end end
 original:close()
 local sprite=Sprite(128,128,ColorMode.RGB)
 sprite.cels[1].image=image
 sprite.layers[1].name=v[1]:upper()..' - '..v[2]
 app.command.ColorQuantization{ui=false,maxColors=v[3],withAlpha=true}
 app.command.ChangePixelFormat{format='indexed',dithering='none'}
 app.command.ChangePixelFormat{format='rgb'}
 sprite:saveAs(root..'/goblin-'..v[1]..'.aseprite')
 sprite:saveCopyAs(out..'/goblin-'..v[1]..'.png')
 local colors={};local solid,partial=0,0
 for it in sprite.cels[1].image:pixels() do
  local c=it();local alpha=pc.rgbaA(c)
  if alpha>0 then colors[c]=true;solid=solid+1;if alpha<255 then partial=partial+1 end end
 end
 local count=0;for _ in pairs(colors)do count=count+1 end
 print(v[1]..': 128x128; native colors='..count..'; opaque pixels='..solid..'; partial alpha='..partial)
 sprite:close()
end
