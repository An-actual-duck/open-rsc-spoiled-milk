-- Authoring only: preserve the approved pose and match the bat's frame anchors.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/monsters/stirge/flight-v1/'
local out=root..'/output/art/stirge-flight-v1/'
local frames=dofile(base..'frame-map.lua')
local alignment=dofile(base..'body-alignment.lua')
local pc=app.pixelColor
local palette={0x23181a,0x341e15,0x4d3019,0x6a471f,0x86632d,0xa47f3f,0xc69b55,0xdbb575,
 0x623136,0x8d4e4e,0xb86c66,0x4f3032,0x6c4242,0x7e4c49,0x945a55,0xa66d63,0xbf8d7c,
 0xb3976d,0xd0b585,0xe0c698,0x311922,0x4b212c,0x68303a,0x843e49,0xa14c56,0xedcf69,0xfff2a0}
local source=app.open(base..'inputs/generated-sheet.png')
local raw=Image(source.cels[1].image)
local front=app.open(root..'/dev/myworld/assets/authoring/monsters/stirge/front-v1/stirge-front-v1.png')
local approved=Image(front.cels[1].image)
local native={}
local function quantize(c)
 local r,g,b=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c)
 local best,dist=0,1e30
 for _,rgb in ipairs(palette)do
  local pr=(rgb>>16)&255;local pg=(rgb>>8)&255;local pb=rgb&255
  local d=(r-pr)^2+(g-pg)^2+(b-pb)^2
  if d<dist then best=rgb;dist=d end
 end
 return pc.rgba((best>>16)&255,(best>>8)&255,best&255,255)
end
for n,f in ipairs(frames)do
 local image=Image(150,130,ColorMode.RGB)
 local sw,sh=f[3]-f[1],f[4]-f[2]
 local scale=f[7]/sw
 local w,h=f[7],math.floor(sh*scale+0.5)
 local dx=f[5]
 local dy=math.max(0,math.floor(f[6]+(f[8]-h)/2+0.5))
 for y=0,h-1 do for x=0,w-1 do
  local sx=math.min(f[3]-1,f[1]+math.floor((x+0.5)*sw/w))
  local sy=math.min(f[4]-1,f[2]+math.floor((y+0.5)*sh/h))
  local c=raw:getPixel(sx,sy)
  if pc.rgbaA(c)>=220 and dx+x<150 and dy+y<130 then image:drawPixel(dx+x,dy+y,quantize(c))end
 end end
 if n==1 then image=Image(approved)
 elseif n==2 or n==3 then
  -- Keep the approved frontal head, gland-bearing body and proboscis consistent.
  local bob=n==2 and -1 or 0
  for y=0,75 do for x=64,85 do image:drawPixel(x,y,0)end end
  for y=0,74 do for x=64,85 do
   local c=approved:getPixel(x,y)
   if pc.rgbaA(c)>0 and y+bob>=0 then image:drawPixel(x,y+bob,c)end
  end end
 end
 local width=n>=16 and 97 or 150
 local aligned=Image(width,130,ColorMode.RGB)
 aligned:drawImage(image,Point(alignment[n][1],alignment[n][2]))
 image=aligned
 native[n]=Image(image)
 local s=Sprite(width,130,ColorMode.RGB);s.cels[1].image=Image(image)
 s:saveCopyAs(base..string.format('stirge-%02d.png',n-1))
 s:saveCopyAs(out..string.format('stirge-%02d.png',n-1));s:close()
end
local sprite=Sprite(150,130,ColorMode.RGB)
sprite.layers[1].name='Native Stirge directional and attack frames'
for n,image in ipairs(native)do
 if n>1 then sprite:newEmptyFrame()end
 sprite:newCel(sprite.layers[1],n,Image(image),Point(0,0));sprite.frames[n].duration=0.15
end
for row,name in ipairs({'front','front-right','right','back-right','back','attack-right'})do
 local tag=sprite:newTag((row-1)*3+1,row*3);tag.name=name;tag.aniDir=AniDir.PING_PONG
end
sprite:saveAs(base..'stirge-flight-v1.aseprite')
local sheet=Sprite(450,780,ColorMode.RGB);local grid=Image(450,780,ColorMode.RGB)
for n,image in ipairs(native)do grid:drawImage(image,Point(((n-1)%3)*150,math.floor((n-1)/3)*130))end
sheet.cels[1].image=grid;sheet:saveCopyAs(base..'stirge-flight-v1.png');sheet:saveCopyAs(out..'stirge-flight-v1.png');sheet:close()
local function mirrored(image)
 local r=Image(image.width,130,ColorMode.RGB)
 for y=0,129 do for x=0,image.width-1 do r:drawPixel(image.width-1-x,y,image:getPixel(x,y))end end
 return r
end
local directions={{'front',0,false},{'front-right',3,false},{'right',6,false},{'back-right',9,false},{'back',12,false},
 {'back-left',9,true},{'left',6,true},{'front-left',3,true},{'attack-right',15,false},{'attack-left',15,true}}
local function append(s,image)
 if image.width~=150 then
  local padded=Image(150,130,ColorMode.RGB)
  padded:drawImage(image,Point(math.floor((150-image.width)/2),0));image=padded
 end
 local number=#s.frames
 if s.cels[1]~=nil then s:newEmptyFrame();number=number+1 end
 s:newCel(s.layers[1],number,Image(image),Point(0,0));s.frames[number].duration=0.15
end
local master=Sprite(150,130,ColorMode.RGB);master:deleteCel(master.cels[1])
for _,d in ipairs(directions)do
 local loop=Sprite(150,130,ColorMode.RGB);loop:deleteCel(loop.cels[1])
 for repeatN=1,3 do for _,offset in ipairs({0,1,2,1})do
  local im=native[d[2]+offset+1];if d[3]then im=mirrored(im)end
  append(loop,im);append(master,im)
 end end
 loop:saveCopyAs(out..d[1]..'.gif');loop:close()
end
master:saveCopyAs(out..'stirge-directions-and-attacks.gif');master:close()
sprite:close();front:close();source:close()
