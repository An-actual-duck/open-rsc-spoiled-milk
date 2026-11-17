-- Eight native front-walk poses articulated from the approved A prototype.
-- Contact/down/passing/up, then the opposite leg. No idle pose or crossfades.
local root=assert(app.params.root)
local out=assert(app.params.out)
local pc=app.pixelColor
local old=app.open(root..'/v1/goblin-a.aseprite')
local src=Image(128,128,ColorMode.RGB)
src:drawImage(old.cels[1].image,old.cels[1].position)
local function image()return Image(128,128,ColorMode.RGB)end
local function round(n)return math.floor(n+.5)end
local function visible(c)return pc.rgbaA(c)>0 end
local function green(c)return pc.rgbaG(c)>=pc.rgbaR(c) and pc.rgbaG(c)>pc.rgbaB(c) end
local function part(test)
 local im=image()
 for y=0,127 do for x=0,127 do local c=src:getPixel(x,y);if visible(c)and test(x,y,c)then im:drawPixel(x,y,c)end end end
 return im
end
local function armSide(x,y)
 if y<44 or y>93 then return nil end
 if x<(y<60 and 50 or y<74 and 46 or 44)then return 'L' end
 if x>(y<60 and 77 or y<74 and 81 or 83)then return 'R' end
end
local arms,legs={}, {}
for _,side in ipairs({'L','R'})do
 arms[side]={
  part(function(x,y,c)return armSide(x,y)==side and y<=65 end),
  part(function(x,y,c)return armSide(x,y)==side and y>=60 and y<=79 end),
  part(function(x,y,c)return armSide(x,y)==side and y>=74 end)
 }
 local function leg(x,y,c)return not armSide(x,y)and y>=85 and green(c)and(side=='L'and x<62 or side=='R'and x>=68)end
 legs[side]={
  part(function(x,y,c)return leg(x,y,c)and y<=103 end),
  part(function(x,y,c)return leg(x,y,c)and y>=98 and y<=112 end),
  part(function(x,y,c)return leg(x,y,c)and y>=107 end)
 }
end
-- Reconstruct the hidden thigh/knee surfaces under the waistcloth before
-- articulation. The source pose's clothing occlusions must not become holes.
local function underlay(art,points)
 local im=image();local shade=pc.rgba(50,55,22,255)
 for y=0,127 do
  local xs={}
  for i,a in ipairs(points)do local b=points[i%#points+1]
   if(a[2]<=y and b[2]>y)or(b[2]<=y and a[2]>y)then xs[#xs+1]=a[1]+(y-a[2])*(b[1]-a[1])/(b[2]-a[2])end
  end
  table.sort(xs)
  for i=1,#xs-1,2 do for x=math.ceil(xs[i]),math.floor(xs[i+1])do im:drawPixel(x,y,shade)end end
 end
 im:drawImage(art);return im
end
legs.L[1]=underlay(legs.L[1],{{48,84},{57,84},{58,93},{55,100},{54,104},{47,104},{44,98},{45,91}})
legs.R[1]=underlay(legs.R[1],{{70,84},{80,84},{83,92},{82,98},{79,104},{72,104},{70,98}})
legs.L[2]=underlay(legs.L[2],{{47,98},{54,98},{55,105},{55,112},{46,112},{46,105}})
legs.R[2]=underlay(legs.R[2],{{72,98},{79,98},{81,105},{81,112},{71,112},{71,105}})
legs.L[3]=underlay(legs.L[3],{{47,106},{54,106},{55,111},{50,113},{45,111}})
legs.R[3]=underlay(legs.R[3],{{72,106},{79,106},{82,111},{76,113},{71,111}})
local function legPixel(x,y,c)return y>=85 and green(c)and(x<62 or x>=68)end
local torso=part(function(x,y,c)return y>=43 and y<=71 and not armSide(x,y)end)
local head=part(function(x,y,c)return y<=44 end)
local cloth=part(function(x,y,c)return y>=70 and not armSide(x,y)and not legPixel(x,y,c)end)
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
local sourceArm={L={{46,49},{39,63},{35,77},{35,90}},R={{81,49},{88,63},{92,77},{92,90}}}
local sourceLeg={L={{52,88},{50,101},{51,109},{51,117}},R={{76,88},{76,101},{76,109},{76,117}}}
local poses={
 {bob=0,sway=-1,L={{52,88},{49,100},{49,109},1},R={{74,88},{74,97},{76,103},.82},la={{46,49},{40,60},{36,72},.92},ra={{81,49},{87,65},{92,81},1.02}},
 {bob=2,sway=-1,L={{52,90},{48,103},{50,109},1},R={{74,90},{72,99},{75,102},.75},la={{46,51},{40,63},{36,75},.94},ra={{81,51},{87,66},{92,81},1.01}},
 {bob=1,sway=-1,L={{53,89},{51,101},{52,106},.94},R={{74,89},{69,96},{70,101},.86},la={{46,50},{39,64},{35,77},1},ra={{81,50},{88,64},{92,77},1}},
 {bob=-1,sway=0,L={{53,87},{53,98},{54,103},.82},R={{75,87},{71,99},{73,105},.94},la={{46,48},{39,63},{35,79},1.01},ra={{81,48},{88,61},{92,74},.95}}
}
local function mirrorPoint(p)return {128-p[1],p[2]}end
local function mirrorLeg(p)return {mirrorPoint(p[1]),mirrorPoint(p[2]),mirrorPoint(p[3]),p[4]}end
local function mirrorArm(p)return {mirrorPoint(p[1]),mirrorPoint(p[2]),mirrorPoint(p[3]),p[4]}end
local frames={}
for n=1,8 do
 local original=poses[(n-1)%4+1];local second=n>4
 local p=second and{bob=original.bob,sway=-original.sway,L=mirrorLeg(original.R),R=mirrorLeg(original.L),la=mirrorArm(original.ra),ra=mirrorArm(original.la)}or original
 local function drawLeg(side,target)
  local im=image();local s=sourceLeg[side];local scale=target[4]
  bone(im,legs[side][1],s[1],s[2],target[1],target[2],scale)
  bone(im,legs[side][2],s[2],s[3],target[2],target[3],scale)
  bone(im,legs[side][3],s[3],s[4],target[3],{target[3][1],target[3][2]+8*scale},scale)
  return im
 end
 local function drawArm(side,target)
  local im=image();local s=sourceArm[side];local width=target[4]
  bone(im,arms[side][1],s[1],s[2],target[1],target[2],width)
  bone(im,arms[side][2],s[2],s[3],target[2],target[3],width)
  bone(im,arms[side][3],s[3],s[4],target[3],{target[3][1]+(side=='L'and -1 or 1),target[3][2]+13*width},width)
  return im
 end
 local l,r=drawLeg('L',p.L),drawLeg('R',p.R)
 local la,ra=drawArm('L',p.la),drawArm('R',p.ra)
 local tilt=p.sway*.7
 local body=rigid(torso,64,70,64+p.sway*.6,70+p.bob,tilt)
 local face=rigid(head,64,43,64+p.sway*.35,43+p.bob,tilt*.6)
 local skirt=image();local phase=(n-1)*math.pi/4
 for y=0,127 do for x=0,127 do
  local offset=p.sway*.6+math.sin(phase-.5)*math.max(0,y-73)*.055
  local xx,yy=round(x-offset),round(y-p.bob)
  if xx>=0 and xx<128 and yy>=0 and yy<128 then skirt:drawPixel(x,y,cloth:getPixel(xx,yy))end
 end end
 local front=(n<=2 or n==8)and'L'or(n>=4 and n<=6)and'R'or(n==3 and'L'or'R')
 -- The forward leg and opposite arm own the foreground.
 local plates={front=='L'and la or ra,front=='L'and r or l,front=='L'and l or r,body,skirt,face,front=='L'and ra or la}
 local composite=image();for _,im in ipairs(plates)do composite:drawImage(im)end
 -- Discard isolated one-pixel sampling debris, without changing attached details.
 local stray={}
 for yy=1,126 do for xx=1,126 do if visible(composite:getPixel(xx,yy))then
  local joined=false
  for dy=-1,1 do for dx=-1,1 do if(dx~=0 or dy~=0)and visible(composite:getPixel(xx+dx,yy+dy))then joined=true end end end
  if not joined then stray[#stray+1]={xx,yy}end
 end end end
 for _,p in ipairs(stray)do composite:drawPixel(p[1],p[2],0);for _,im in ipairs(plates)do im:drawPixel(p[1],p[2],0)end end
 frames[n]={plates=plates,composite=composite}
end
local sprite=Sprite(128,128,ColorMode.RGB)
local layers={};for i,name in ipairs({'Rear arm','Rear leg','Forward leg','Torso','Waistcloth','Head','Forward arm'})do local l=i==1 and sprite.layers[1]or sprite:newLayer();l.name=name;layers[i]=l end
for n,f in ipairs(frames)do
 if n>1 then sprite:newEmptyFrame()end
 sprite.frames[n].duration=.11
 for i,im in ipairs(f.plates)do sprite:newCel(layers[i],n,im,Point(0,0))end
 local still=Sprite(128,128,ColorMode.RGB);still.cels[1].image=f.composite;still:saveAs(out..'/walk-'..string.format('%02d',n)..'.png');still:close()
end
local tag=sprite:newTag(1,8);tag.name='Walk front';tag.aniDir=AniDir.FORWARD
sprite:saveAs(root..'/walk-front-v1/goblin-a-walk-front.aseprite')
sprite:saveCopyAs(out..'/goblin-a-walk-front.gif')
local sheet=Sprite(512,256,ColorMode.RGB);local sheetImage=Image(512,256,ColorMode.RGB)
for n,f in ipairs(frames)do sheetImage:drawImage(f.composite,Point(((n-1)%4)*128,math.floor((n-1)/4)*128))end
sheet.cels[1].image=sheetImage;sheet:saveAs(out..'/walk-contact-sheet.png')
app.command.SpriteSize{width=1536,height=768,method='nearest-neighbor'};sheet:saveAs(out..'/walk-contact-sheet-3x.png')
print('8 frames; 128x128; 110 ms/frame; 880 ms full cycle; 7 editable layers; forward looping; no idle')
