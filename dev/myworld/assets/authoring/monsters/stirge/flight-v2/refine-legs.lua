-- Focused native anatomy edit. V1 wings/head/proboscis are retained.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/monsters/stirge/flight-v2/'
local out=root..'/output/art/stirge-leg-consistency-v2/'
local source=app.open(root..'/dev/myworld/assets/authoring/monsters/stirge/flight-v1/stirge-flight-v1.aseprite')
local guideSprite=app.open(base..'front-anatomy-source.png')
local guide=Image(guideSprite.cels[1].image)
local landmarks=dofile(base..'landmarks.lua')
local pc=app.pixelColor
local function rgba(rgb)return pc.rgba((rgb>>16)&255,(rgb>>8)&255,rgb&255,255)end
local thorax={[0x4d3019]=true,[0x6a471f]=true,[0x86632d]=true,[0xa47f3f]=true,[0xc69b55]=true,[0xdbb575]=true}
local function rgb(c)return (pc.rgbaR(c)<<16)|(pc.rgbaG(c)<<8)|pc.rgbaB(c)end
local function round(n)return math.floor(n+0.5)end
-- Four legs: fore-left, fore-right, hind-left, hind-right. The final flag is
-- foreground visibility. All four roots are attached to the thorax, not abdomen.
local rigs={
 { {{80,37},{91,47},{87,57},{90,58},true}, {{69,37},{58,47},{62,57},{59,58},true},
   {{79,40},{84,52},{81,65},{84,65},true}, {{70,40},{65,52},{68,65},{65,65},true} },
 { {{84,40},{94,46},{97,55},{95,57},false}, {{79,42},{87,49},{86,60},{89,61},true},
   {{81,43},{83,52},{78,64},{80,65},false}, {{75,45},{75,54},{68,64},{71,65},true} },
 { {{89,41},{98,47},{101,56},{98,58},false}, {{87,43},{91,51},{87,61},{90,62},true},
   {{83,42},{84,51},{81,62},{83,64},false}, {{80,44},{79,54},{71,62},{74,63},true} },
 { {{77,31},{88,39},{87,51},{90,52},false}, {{73,32},{78,42},{76,57},{79,58},true},
   {{74,34},{68,43},{65,58},{68,60},false}, {{71,35},{61,44},{59,55},{62,57},true} },
 { {{81,34},{90,45},{89,57},{92,58},false}, {{69,34},{60,45},{61,57},{58,58},false},
   {{79,38},{85,50},{82,64},{85,65},false}, {{71,38},{65,50},{68,64},{65,65},false} },
 { {{51,33},{62,39},{66,48},{63,50},false}, {{48,35},{55,43},{53,53},{56,54},true},
   {{46,34},{46,43},{41,51},{44,52},false}, {{43,36},{37,44},{34,54},{37,55},true} }
}
local function line(im,a,b,color,mask)
 local x,y=a[1],a[2];local dx=math.abs(b[1]-x);local sx=x<b[1]and 1 or -1
 local dy=-math.abs(b[2]-y);local sy=y<b[2]and 1 or -1;local err=dx+dy
 while true do
  if x>=0 and y>=0 and x<im.width and y<im.height then im:drawPixel(x,y,rgba(color));if mask then mask:drawPixel(x,y,rgba(0xffffff))end end
  if x==b[1]and y==b[2]then break end
  local e=2*err;if e>=dy then err=err+dy;x=x+sx end;if e<=dx then err=err+dx;y=y+sy end
 end
end
local native,bodies,farImages,nearImages,masks={},{},{},{},{}
local records={}
for n=0,17 do
 local width=n>=15 and 97 or 150
 local image=Image(width,130,ColorMode.RGB)
 local c=source.layers[1]:cel(n+1);image:drawImage(c.image,c.position)
 local originalImage=Image(image)
 local editMask=Image(width,130,ColorMode.RGB)
 local mark=landmarks[n+1]
 if n<3 then
  local bob=n==1 and -1 or 0
  -- Replace the round underside only; preserve the existing upper thorax and wings.
  local abdomen={{67,40},{83,40},{84,44},{82,53},{79,60},{76,66},{73,66},{70,61},{67,54},{65,45}}
  local function inside(px,py)
   local result=false;local j=#abdomen
   for i=1,#abdomen do
    local a,b=abdomen[i],abdomen[j]
    if(a[2]>py)~=(b[2]>py)and px<(b[1]-a[1])*(py-a[2])/(b[2]-a[2])+a[1]then result=not result end
    j=i
   end
   return result
  end
  -- Import only the segmented abdomen silhouette, never the generated legs.
  for y=40,69 do for x=64,85 do
   local ty=y+bob
   image:drawPixel(x,ty,0);editMask:drawPixel(x,ty,rgba(0xffffff))
   local value=guide:getPixel(x,y)
   if inside(x+.5,y+.5)and pc.rgbaA(value)>0 then image:drawPixel(x,ty,value)end
  end end
  -- Preserve the existing narrow feeding proboscis across the body correction.
  for y=29,47 do for x=74,76 do
   local ty=y+bob;image:drawPixel(x,ty,originalImage:getPixel(x,ty))
  end end
 else
  -- Remove the old short feet beneath the red abdomen. Keep its shell colors.
  for y=mark[8],math.min(129,mark[8]+9)do for x=math.max(0,mark[5]-4),math.min(width-1,mark[7]+3)do
   local value=image:getPixel(x,y);local color=rgb(value)
   if pc.rgbaA(value)>0 and(thorax[color]or color==0x23181a or color==0x341e15)then
    image:drawPixel(x,y,0);editMask:drawPixel(x,y,rgba(0xffffff))
   end
  end end
 end
 local far=Image(width,130,ColorMode.RGB);local near=Image(width,130,ColorMode.RGB)
 local row=math.floor(n/3)+1;local original=landmarks[(row-1)*3+1]
 local a0=math.atan(original[2]-original[4],original[1]-original[3])
 local a=math.atan(mark[2]-mark[4],mark[1]-mark[3])-a0
 if a>math.pi then a=a-2*math.pi elseif a< -math.pi then a=a+2*math.pi end
 a=math.max(-0.20,math.min(0.20,a));if row==1 then a=0 end
 local cos,sin=math.cos(a),math.sin(a)
 local function transform(point)
  if row==1 then return {point[1],point[2]+(n==1 and -1 or 0)}end
  local x,y=point[1]-original[1],point[2]-original[2]
  return {round(mark[1]+x*cos-y*sin),round(mark[2]+x*sin+y*cos)}
 end
 local roots={}
 for legIndex,leg in ipairs(rigs[row])do
  local points={};for j=1,4 do points[j]=transform(leg[j])end
  -- Snap each attachment to a visible thorax pixel rather than to a wing or empty space.
  local ax,ay=points[1][1],points[1][2];local best,dist=nil,1e9
  for y=math.max(0,ay-4),math.min(129,ay+4)do for x=math.max(0,ax-4),math.min(width-1,ax+4)do
   local value=image:getPixel(x,y)
   if pc.rgbaA(value)>0 and thorax[rgb(value)]then
    local d=(x-ax)^2+(y-ay)^2;if d<dist then best={x,y};dist=d end
   end
  end end
  assert(best,'Missing thorax attachment frame '..n..' leg '..legIndex)
  local dx,dy=best[1]-ax,best[2]-ay
  for j=1,4 do points[j][1]=points[j][1]+dx;points[j][2]=points[j][2]+dy end
  local phase=n%3
  if phase==1 then for j=2,4 do points[j][1]=points[j][1]+1 end
  elseif phase==2 then for j=2,4 do points[j][2]=points[j][2]-1 end end
  roots[legIndex]=points[1]
  local target=leg[5]and near or far
  for j=1,3 do
   line(target,{points[j][1]+1,points[j][2]}, {points[j+1][1]+1,points[j+1][2]},0x23181a,editMask)
   line(target,points[j],points[j+1],leg[5]and 0x6a471f or 0x4d3019,editMask)
  end
  local knee=points[2];if leg[5]then line(target,knee,knee,0x86632d,editMask)end
 end
 local flat=Image(width,130,ColorMode.RGB);flat:drawImage(far);flat:drawImage(image);flat:drawImage(near)
 native[n+1]=Image(flat);bodies[n+1]=Image(image);farImages[n+1]=Image(far);nearImages[n+1]=Image(near);masks[n+1]=Image(editMask)
 records[#records+1]=string.format(' {"frame":%d,"roots":[[%d,%d],[%d,%d],[%d,%d],[%d,%d]]}',n,roots[1][1],roots[1][2],roots[2][1],roots[2][2],roots[3][1],roots[3][2],roots[4][1],roots[4][2])
 for _,entry in ipairs({{'stirge-',flat},{'edit-mask-',editMask}})do
  local s=Sprite(width,130,ColorMode.RGB);s.cels[1].image=Image(entry[2]);s:saveCopyAs(base..entry[1]..string.format('%02d.png',n));s:close()
 end
 local s=Sprite(width,130,ColorMode.RGB);s.cels[1].image=Image(flat);s:saveCopyAs(out..string.format('stirge-%02d.png',n));s:close()
end
local file=io.open(base..'leg-attachments.json','w');file:write('[\n'..table.concat(records,',\n')..'\n]\n');file:close()
local sprite=Sprite(150,130,ColorMode.RGB)
local farLayer=sprite.layers[1];farLayer.name='Far legs — attached to thorax, occluded by body'
local bodyLayer=sprite:newLayer();bodyLayer.name='Existing wings/head + corrected front underside'
local nearLayer=sprite:newLayer();nearLayer.name='Near legs — foreground thorax attachments'
for n=1,18 do
 if n>1 then sprite:newEmptyFrame()end
 sprite:newCel(farLayer,n,Image(farImages[n]),Point(0,0));sprite:newCel(bodyLayer,n,Image(bodies[n]),Point(0,0));sprite:newCel(nearLayer,n,Image(nearImages[n]),Point(0,0));sprite.frames[n].duration=.15
end
for row,name in ipairs({'front','front-right','right','back-right','back','attack-right'})do local tag=sprite:newTag((row-1)*3+1,row*3);tag.name=name;tag.aniDir=AniDir.PING_PONG end
sprite:saveAs(base..'stirge-flight-v2.aseprite')
local sheet=Sprite(450,780,ColorMode.RGB);local grid=Image(450,780,ColorMode.RGB)
for n,im in ipairs(native)do grid:drawImage(im,Point(((n-1)%3)*150,math.floor((n-1)/3)*130))end
sheet.cels[1].image=grid;sheet:saveCopyAs(base..'stirge-flight-v2.png');sheet:saveCopyAs(out..'stirge-flight-v2.png');sheet:close()
-- Standard review loops, retaining the existing flap timing and direction order.
local function mirror(im)local r=Image(im.width,130,ColorMode.RGB);for y=0,129 do for x=0,im.width-1 do r:drawPixel(im.width-1-x,y,im:getPixel(x,y))end end;return r end
local function append(s,im)
 if im.width~=150 then local p=Image(150,130,ColorMode.RGB);p:drawImage(im,Point(math.floor((150-im.width)/2),0));im=p end
 local frame=#s.frames;if s.cels[1]then s:newEmptyFrame();frame=frame+1 end
 s:newCel(s.layers[1],frame,Image(im),Point(0,0));s.frames[frame].duration=.15
end
local master=Sprite(150,130,ColorMode.RGB);master:deleteCel(master.cels[1])
for _,d in ipairs({{'front',0,false},{'front-right',3,false},{'right',6,false},{'back-right',9,false},{'back',12,false},{'back-left',9,true},{'left',6,true},{'front-left',3,true},{'attack-right',15,false},{'attack-left',15,true}})do
 local loop=Sprite(150,130,ColorMode.RGB);loop:deleteCel(loop.cels[1])
 for repeatN=1,3 do for _,offset in ipairs({0,1,2,1})do local im=native[d[2]+offset+1];if d[3]then im=mirror(im)end;append(loop,im);append(master,im)end end
 loop:saveCopyAs(out..d[1]..'.gif');loop:close()
end
master:saveCopyAs(out..'stirge-directions-and-attacks.gif');master:close();sprite:close();source:close();guideSprite:close()
