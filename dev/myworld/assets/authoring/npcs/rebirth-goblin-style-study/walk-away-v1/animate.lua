-- Aseprite-native rear-three-quarter walk toward screen upper right.
-- The head and trunk stay on one continuous plate. Eight walk poses, no idle.
local root=assert(app.params.root);local out=assert(app.params.out);local pc=app.pixelColor
local old=app.open(root..'/walk-away-v1/goblin-a-away-base.aseprite')
local src=Image(128,128,ColorMode.RGB);src:drawImage(old.cels[1].image,old.cels[1].position)
local function image()return Image(128,128,ColorMode.RGB)end
local function round(x)return math.floor(x+.5)end
local function visible(c)return pc.rgbaA(c)>0 end
local function green(c)return pc.rgbaG(c)>=pc.rgbaR(c)and pc.rgbaG(c)>pc.rgbaB(c)end
local function armSide(x,y,c)
 if y>=47 and y<=89 and x<(y<61 and 58 or y<74 and 56 or 54)then return 'near' end
 if y>=51 and y<=73 and x>=73 and green(c)then return 'far' end
end
local function legSide(x,y,c)
 if armSide(x,y,c)or not green(c)then return nil end
 if y>=81 and x>=50 and x<67 then return 'near' end
 if y>=78 and x>=66 then return 'far' end
end
local function part(test)
 local im=image();for y=0,127 do for x=0,127 do local c=src:getPixel(x,y);if visible(c)and test(x,y,c)then im:drawPixel(x,y,c)end end end;return im
end
local arms={near={},far={}};local legs={near={},far={}}
for _,side in ipairs({'near','far'})do
 local elbow,wrist=side=='near'and 65 or 60,side=='near'and 77 or 65
 arms[side]={part(function(x,y,c)return armSide(x,y,c)==side and y<=elbow end),part(function(x,y,c)return armSide(x,y,c)==side and y>=elbow-5 and y<=wrist+3 end),part(function(x,y,c)return armSide(x,y,c)==side and y>=wrist-3 end)}
 local knee,ankle=side=='near'and 98 or 93,side=='near'and 108 or 99
 legs[side]={part(function(x,y,c)return legSide(x,y,c)==side and y<=knee+3 end),part(function(x,y,c)return legSide(x,y,c)==side and y>=knee-4 and y<=ankle+3 end),part(function(x,y,c)return legSide(x,y,c)==side and y>=ankle-3 end)}
end
local function underlay(art,pts)
 local im=image();for y=0,127 do local xs={}
  for i,a in ipairs(pts)do local b=pts[i%#pts+1];if(a[2]<=y and b[2]>y)or(b[2]<=y and a[2]>y)then xs[#xs+1]=a[1]+(y-a[2])*(b[1]-a[1])/(b[2]-a[2])end end
  table.sort(xs);for i=1,#xs-1,2 do for x=math.ceil(xs[i]),math.floor(xs[i+1])do im:drawPixel(x,y,pc.rgba(50,55,22,255))end end
 end;im:drawImage(art);return im
end
legs.near[1]=underlay(legs.near[1],{{55,79},{64,81},{63,91},{59,98},{57,102},{51,101},{50,95},{52,87}})
legs.near[2]=underlay(legs.near[2],{{51,94},{59,95},{58,103},{56,111},{49,111},{50,103}})
legs.near[3]=underlay(legs.near[3],{{50,105},{57,105},{57,111},{52,113},{48,111}})
legs.far[1]=underlay(legs.far[1],{{67,78},{73,78},{75,87},{74,96},{67,96},{66,87}})
legs.far[2]=underlay(legs.far[2],{{67,89},{74,89},{75,99},{68,101},{66,97}})
legs.far[3]=underlay(legs.far[3],{{68,96},{74,96},{75,101},{68,103},{66,100}})
local upper=part(function(x,y,c)return y<=68 and not armSide(x,y,c)end)
local cloth=part(function(x,y,c)return y>=65 and not armSide(x,y,c)and not legSide(x,y,c)end)
-- Inverse bone-space sampling keeps joined native pixels without interpolation colors.
local function bone(dst,source,a,b,c,d,width)
 width=width or 1
 local sx,sy=b[1]-a[1],b[2]-a[2];local sl=math.sqrt(sx*sx+sy*sy);sx,sy=sx/sl,sy/sl
 local tx,ty=d[1]-c[1],d[2]-c[2];local tl=math.sqrt(tx*tx+ty*ty);tx,ty=tx/tl,ty/tl
 assert(sl>0 and tl>0)
 for y=0,127 do for x=0,127 do
  local dx,dy=x-c[1],y-c[2]
  local along=(dx*tx+dy*ty)*sl/tl
  local across=(-dx*ty+dy*tx)/width
  local ox,oy=round(a[1]+sx*along-sy*across),round(a[2]+sy*along+sx*across)
  if ox>=0 and ox<128 and oy>=0 and oy<128 then local color=source:getPixel(ox,oy);if visible(color)then dst:drawPixel(x,y,color)end end
 end end
end
local function rigid(source,px,py,tx,ty,degrees)
 local im=image();local a=math.rad(degrees);local co,si=math.cos(a),math.sin(a)
 for y=0,127 do for x=0,127 do
  local dx,dy=x-tx,y-ty;local xx,yy=round(px+co*dx+si*dy),round(py-si*dx+co*dy)
  if xx>=0 and xx<128 and yy>=0 and yy<128 then im:drawPixel(x,y,source:getPixel(xx,yy))end
 end end
 return im
end

local sourceLeg={near={{59,85},{55,98},{53,108},{53,116}},far={{70,82},{70,93},{70,99},{70,103}}}
local sourceArm={near={{55,51},{50,63},{46,76},{46,88}},far={{76,51},{75,59},{78,65},{78,72}}}
-- The planted foot travels down-left as the goblin moves up-right. The swing
-- foot lifts and returns forward, rather than reusing the front-view motion.
local nearLeg={
 {{59,85},{56,96},{57,104},1},{{59,85},{55,99},{55,106},1},{{59,85},{54,99},{53,108},1},{{59,85},{54,99},{51,109},.94},
 {{59,85},{54,98},{51,109},.88},{{59,85},{55,97},{53,105},.9},{{59,85},{58,94},{57,101},.93},{{59,85},{59,95},{58,102},.98}}
local farLeg={
 {{70,82},{72,94},{73,104},.88},{{70,82},{73,94},{74,101},.8},{{70,82},{75,91},{77,97},.82},{{70,82},{75,90},{78,96},.9},
 {{70,82},{75,90},{77,97},1},{{70,82},{74,92},{76,99},1},{{70,82},{72,93},{74,101},.96},{{70,82},{71,93},{73,103},.9}}
local nearArm={
 {{49,64},{44,79},1},{{49,65},{45,79},1},{{51,63},{48,76},.98},{{53,61},{51,72},.95},
 {{54,60},{52,71},.92},{{53,61},{51,73},.94},{{51,63},{48,76},.98},{{49,63},{45,77},1}}
local farArm={
 {{80,56},{82,60},.85},{{80,57},{82,61},.86},{{78,58},{79,64},.88},{{76,59},{76,68},.9},
 {{75,59},{75,70},.92},{{75,60},{75,69},.9},{{77,59},{77,66},.88},{{79,57},{80,62},.86}}
local frames={};local bobs={0,2,1,-1,0,2,1,-1}
for n=1,8 do
 local bob=bobs[n];local phase=(n-1)*math.pi/4;local sway=math.sin(phase)*.65
 local function leg(side,pose)
  local im=image();local s=sourceLeg[side];local hip={pose[1][1]+sway,pose[1][2]+bob};local knee={pose[2][1]+sway*.5,pose[2][2]+bob*.35};local ankle=pose[3];local scale=pose[4]
  bone(im,legs[side][1],s[1],s[2],hip,knee,scale)
  bone(im,legs[side][2],s[2],s[3],knee,ankle,scale)
  bone(im,legs[side][3],s[3],s[4],ankle,{ankle[1],ankle[2]+(s[4][2]-s[3][2])*scale},scale)
  return im
 end
 local function arm(side,pose)
  local im=image();local s=sourceArm[side];local shoulder={s[1][1]+sway,s[1][2]+bob};local elbow={pose[1][1]+sway,pose[1][2]+bob*.75};local wrist={pose[2][1]+sway,pose[2][2]+bob*.5};local width=pose[3]
  bone(im,arms[side][1],s[1],s[2],shoulder,elbow,width)
  bone(im,arms[side][2],s[2],s[3],elbow,wrist,width)
  bone(im,arms[side][3],s[3],s[4],wrist,{wrist[1]-.5,wrist[2]+(s[4][2]-s[3][2])*width},width)
  return im
 end
 local backArm=arm('far',farArm[n]);local backLeg=leg('far',farLeg[n]);local frontLeg=leg('near',nearLeg[n]);local frontArm=arm('near',nearArm[n]);local body=rigid(upper,64,67,64+sway,67+bob,math.sin(phase)*.55)
 local skirt=image()
 for y=0,127 do for x=0,127 do local dx=sway+math.sin(phase-.4)*math.max(0,y-73)*.045;local xx,yy=round(x-dx),round(y-bob)
  if xx>=0 and xx<128 and yy>=0 and yy<128 then skirt:drawPixel(x,y,cloth:getPixel(xx,yy))end
 end end
 local plates={backArm,backLeg,frontLeg,body,skirt,frontArm};local full=image();for _,im in ipairs(plates)do full:drawImage(im)end
 -- Discard disconnected resampling crumbs, including narrow foot-edge remnants.
 local seen={};local stray={}
 for y=0,127 do for x=0,127 do local key=y*128+x
  if not seen[key]and visible(full:getPixel(x,y))then
   local q={{x,y}};seen[key]=true;local at=1
   while at<=#q do local p=q[at];at=at+1
    for yy=math.max(0,p[2]-1),math.min(127,p[2]+1)do for xx=math.max(0,p[1]-1),math.min(127,p[1]+1)do local k=yy*128+xx
     if not seen[k]and visible(full:getPixel(xx,yy))then seen[k]=true;q[#q+1]={xx,yy}end
    end end
   end
   if #q<20 then for _,p in ipairs(q)do stray[#stray+1]=p end end
  end
 end end
 for _,p in ipairs(stray)do full:drawPixel(p[1],p[2],0);for _,im in ipairs(plates)do im:drawPixel(p[1],p[2],0)end end
 frames[n]={plates=plates,full=full}
end
local sprite=Sprite(128,128,ColorMode.RGB);local layers={}
for i,name in ipairs({'Far arm','Far leg','Near leg','Head and back - continuous','Waistcloth','Near arm'})do local layer=i==1 and sprite.layers[1]or sprite:newLayer();layer.name=name;layers[i]=layer end
for n,f in ipairs(frames)do
 if n>1 then sprite:newEmptyFrame()end;sprite.frames[n].duration=.11
 for i,im in ipairs(f.plates)do sprite:newCel(layers[i],n,im,Point(0,0))end
 local still=Sprite(128,128,ColorMode.RGB);still.cels[1].image=f.full;still:saveAs(out..'/away-'..string.format('%02d',n)..'.png');still:close()
end
local tag=sprite:newTag(1,8);tag.name='Walk diagonal away';tag.aniDir=AniDir.FORWARD
sprite:saveAs(root..'/walk-away-v1/goblin-a-walk-away.aseprite');sprite:saveCopyAs(out..'/goblin-a-walk-away.gif')
local sheet=Sprite(512,256,ColorMode.RGB);local art=Image(512,256,ColorMode.RGB)
for n,f in ipairs(frames)do art:drawImage(f.full,Point(((n-1)%4)*128,math.floor((n-1)/4)*128))end
sheet.cels[1].image=art;sheet:saveAs(out..'/away-contact-sheet.png');app.command.SpriteSize{width=1536,height=768,method='nearest-neighbor'};sheet:saveAs(out..'/away-contact-sheet-3x.png')
print('8 frames; 128x128; 110 ms/frame; 880 ms full loop; six editable layers; no idle; near/far occlusion')
