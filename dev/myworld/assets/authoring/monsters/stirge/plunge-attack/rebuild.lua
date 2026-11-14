-- Aseprite-native profile attack. Translation drives the stab; needle pitch stays fixed.
local root=app.params['root'];local base=root..'/dev/myworld/assets/authoring/monsters/stirge/plunge-attack/';local out=root..'/output/art/stirge-plunge-attack/'
local old=app.open(base..'inputs/approved-flight.aseprite');local pc=app.pixelColor
local W,H=210,160
local function C(r,g,b)return pc.rgba(r,g,b,255)end
local P={ink=C(42,27,24),leg0=C(112,88,56),leg1=C(149,121,74),leg2=C(194,164,102),tan=C(175,139,77)}
local function rnd(n)return math.floor(n+.5)end
local function line(im,x,y,xx,yy,color,width)
 x,y,xx,yy=rnd(x),rnd(y),rnd(xx),rnd(yy);local dx,dy=math.abs(xx-x),-math.abs(yy-y);local sx=x<xx and 1 or -1;local sy=y<yy and 1 or -1;local e=dx+dy
 while true do
  if x>=0 and x<im.width and y>=0 and y<im.height then im:drawPixel(x,y,color);if width==2 and x+1<im.width then im:drawPixel(x+1,y,color)end end
  if x==xx and y==yy then break end;local ee=2*e;if ee>=dy then e=e+dy;x=x+sx end;if ee<=dx then e=e+dx;y=y+sy end
 end
end
local function path(im,pts,color,width)for i=1,#pts-1 do line(im,pts[i][1],pts[i][2],pts[i+1][1],pts[i+1][2],color,width)end end
local function full(l,f)local im=Image(150,130,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im end
local function rotate(x,y,degrees)local a=math.rad(degrees);return x*math.cos(a)-y*math.sin(a),x*math.sin(a)+y*math.cos(a)end
local function transform(src,px,py,tx,ty,angle,sx,sy)
 local im=Image(W,H,ColorMode.RGB)
 -- Bound audit checks source pixels before inverse sampling can hide clipping.
 for yy=0,src.height-1 do for xx=0,src.width-1 do if pc.rgbaA(src:getPixel(xx,yy))>0 then local x,y=rotate((xx-px)*sx,(yy-py)*sy,angle);assert(rnd(tx+x)>=0 and rnd(tx+x)<W and rnd(ty+y)>=0 and rnd(ty+y)<H,'Articulation clips source pixels')end end end
 for y=0,H-1 do for x=0,W-1 do local xx,yy=rotate(x-tx,y-ty,-angle);xx=rnd(px+xx/sx);yy=rnd(py+yy/sy);if xx>=0 and xx<src.width and yy>=0 and yy<src.height then im:drawPixel(x,y,src:getPixel(xx,yy))end end end;return im
end
local spr=Sprite(W,H,ColorMode.RGB);local layers={};local source={}
for n,l in ipairs(old.layers)do local dst=n==1 and spr.layers[1]or spr:newLayer();dst.name=l.name;dst.isVisible=l.isVisible;layers[l.name]=dst;source[l.name]={};for f=7,9 do source[l.name][f]=full(l,f)end end
-- Existing flight artwork is copied verbatim into the enlarged document.
for f=1,18 do if f>1 then spr:newEmptyFrame()end;spr.frames[f].duration=old.frames[f].duration
 for _,l in ipairs(old.layers)do local c=l:cel(f);if c then spr:newCel(layers[l.name],f,Image(c.image),Point(c.position.x,c.position.y))end end
end
for _,t in ipairs(old.tags)do local tag=spr:newTag(t.fromFrame.frameNumber,t.toFrame.frameNumber);tag.name=t.name;tag.aniDir=t.aniDir end
local near=spr:newLayer();near.name='Attack near wing - plunge and recovery';near.stackIndex=layers['Striped abdomen and moth thorax'].stackIndex+1
local f=io.open(base..'inputs/profile-anatomy.json');local anatomy=json.decode(f:read('*a'));f:close()
local specs={
 {name='Coil / pull back',cx=65,cy=65,body=-8,sx=.90,sy=1.04,hx=-5,hy=1,headAngle=-2,headX=.94,headY=1,wingFrame=9,wingAngle=12,wingX=1,wingY=1,duration=.24},
 {name='Snap / forward plunge',cx=110,cy=78,body=29,sx=1.13,sy=.97,hx=3,hy=0,headAngle=19,headX=1.14,headY=.94,wingFrame=9,wingAngle=-35,wingX=1,wingY=1,duration=.12},
 {name='Catch / downward wing stroke',cx=88,cy=65,body=0,sx=1,sy=1,hx=0,hy=0,headAngle=0,headX=1,headY=1,wingFrame=8,wingAngle=-64,wingX=1,wingY=1,duration=.24}
}
local anchors={}
for i,sp in ipairs(specs)do
 local frame=15+i
 local function bodyPoint(x,y)local xx,yy=rotate((x-83)*sp.sx,(y-40)*sp.sy,sp.body);return {sp.cx+xx,sp.cy+yy}end
 local mount=bodyPoint(89,38);mount[1]=mount[1]+sp.hx;mount[2]=mount[2]+sp.hy
 local torso=transform(source['Striped abdomen and moth thorax'][8],83,40,sp.cx,sp.cy,sp.body,sp.sx,sp.sy)
 spr:newCel(layers['Striped abdomen and moth thorax'],frame,torso,Point(0,0))
 -- Skull compresses into the collar, then extends forward; the needle never lifts to create the stab.
 local skull=Image(source['Mosquito head and proboscis'][8]);local mx,my=105.64,37.004
 line(skull,mx,my,mx+37*.38,my+28*.32,0);line(skull,mx+37*.38,my+28*.32,mx+37,my+28,0)
 local head=transform(skull,89,38,mount[1],mount[2],sp.headAngle,sp.headX,sp.headY)
 local ox,oy=rotate((mx-89)*sp.headX,(my-38)*sp.headY,sp.headAngle)
 local mouth={mount[1]+ox,mount[2]+oy};local tip={mouth[1]+45*math.cos(math.rad(14)),mouth[2]+45*math.sin(math.rad(14))}
 line(head,mouth[1],mouth[2],tip[1],tip[2],P.leg1);line(head,mouth[1],mouth[2],mouth[1]+(tip[1]-mouth[1])*.35,mouth[2]+(tip[2]-mouth[2])*.35,P.tan);head:drawPixel(rnd(tip[1]),rnd(tip[2]),P.ink)
 spr:newCel(layers['Mosquito head and proboscis'],frame,head,Point(0,0))
 local legsNear,legsFar=Image(W,H,ColorMode.RGB),Image(W,H,ColorMode.RGB);local legRecords={}
 for j,leg in ipairs(anatomy.legs)do
  local r=bodyPoint(leg.root[1],leg.root[2]);local far=leg.side==-1;local rear=leg.pair==2;local knee,ankle,toe
  if i==1 then -- A tight angular coil under each original thoracic station.
   local dx=rear and -6 or 5;local dy=far and 0 or 3
   knee={r[1]+dx,r[2]+5+dy};ankle={r[1]-dx*.40,r[2]+10+dy};toe={r[1]+dx*.45,r[2]+9+dy}
   if far then
    -- Expose each far-side coil past a different side of the silhouette;
    -- roots remain at their original thoracic stations behind the body.
    local side=rear and -1 or 1
    knee={r[1]+side*(rear and 26 or 16),r[2]+9}
    ankle={r[1]+side*(rear and 25 or 13),r[2]+17}
    toe={r[1]+side*(rear and 19 or 8),r[2]+14}
   end
  elseif i==2 then -- Straight, swept-back legs run alongside the body during the thrust.
   local dy=far and -1 or 3;local back=rear and 28 or 21
   knee={r[1]-back*.40,r[2]+dy+1};ankle={r[1]-back,r[2]+dy-2};toe={ankle[1]-4,ankle[2]-1}
  else knee=bodyPoint(leg.knee[1],leg.knee[2]);ankle=bodyPoint(leg.ankle[1],leg.ankle[2]);toe=bodyPoint(leg.toe[1],leg.toe[2])end
  local im=far and legsFar or legsNear;local points={r,knee,ankle,toe};path(im,points,P.ink,2);path(im,points,far and P.leg0 or P.leg1,1)
  if not far then line(im,knee[1],knee[2],ankle[1],ankle[2],P.leg2)end
  legRecords[#legRecords+1]={id=leg.id,root=r,knee=knee,ankle=ankle,toe=toe}
 end
 spr:newCel(layers['Far legs - thorax roots'],frame,legsFar,Point(0,0));spr:newCel(layers['Near legs - thorax roots'],frame,legsNear,Point(0,0))
 local wingSource=Image(source['Rust bat wings'][sp.wingFrame])
 -- Old bat-leg stencil remnants are not wing membrane. They sat below the profile shoulder.
 for y=42,129 do for x=0,149 do wingSource:drawPixel(x,y,0)end end
 if i==2 or i==3 then
  if i==2 then wingSource=Image(source['Rust bat wings'][8]);for y=42,129 do for x=0,149 do wingSource:drawPixel(x,y,0)end end end
  -- Profile midstroke near membrane separated from the overlapping far leading edge.
  local plate=Image(150,130,ColorMode.RGB);local farPlate=Image(150,130,ColorMode.RGB)
  for y=0,129 do for x=0,149 do local color=wingSource:getPixel(x,y);local edge=16+(x-47)*.57;if y>=edge or x<47 then plate:drawPixel(x,y,color)else farPlate:drawPixel(x,y,color)end end end
  if i==3 then
   local front=transform(plate,81,36,sp.cx+3,sp.cy-9,-64,1,1)
   spr:newCel(near,frame,front,Point(0,0))
   spr:newCel(layers['Rust bat wings'],frame,transform(farPlate,83,40,sp.cx+2,sp.cy-3,-53,1,1),Point(0,0))
  else
   -- Sweep the two full-length wings back independently, parallel to the thrust axis.
   local swept=transform(farPlate,83,40,sp.cx+2,sp.cy-3,-30,1,1)
   spr:newCel(near,frame,transform(plate,81,36,sp.cx-2,sp.cy-4,-4,1,1),Point(0,0))
   spr:newCel(layers['Rust bat wings'],frame,swept,Point(0,0))
  end
 else
  spr:newCel(layers['Rust bat wings'],frame,transform(wingSource,82,39,sp.cx,sp.cy,sp.wingAngle,1,1),Point(0,0))
 end
 -- Remove former attack-specific layers and hidden references on these frames only.
 for _,l in ipairs(spr.layers)do if l.name~='Rust bat wings'and l.name~='Mosquito head and proboscis'and l.name~='Striped abdomen and moth thorax'and l.name~='Far legs - thorax roots'and l.name~='Near legs - thorax roots'and l~=near then local c=l:cel(frame);if c then spr:deleteCel(c)end end end
 spr.frames[frame].duration=sp.duration
 anchors[#anchors+1]={pose=sp.name,thorax={sp.cx,sp.cy},mouth=mouth,needle_tip=tip,needle_angle=14,legs=legRecords}
end
spr:saveAs(base..'stirge-plunge-attack.aseprite')
local sheet=Image(630,960,ColorMode.RGB)
for n=0,17 do local im=Image(n>=15 and W or 150,n>=15 and H or 130,ColorMode.RGB);im:drawSprite(spr,n+1);im:saveAs(base..string.format('frame-%02d.png',n));im:saveAs(out..string.format('frame-%02d.png',n));sheet:drawImage(im,Point(n%3*W,math.floor(n/3)*H))end
sheet:saveAs(base..'stirge-plunge-sheet.png');sheet:saveAs(out..'stirge-plunge-sheet.png')
local gif=Sprite(W,H,ColorMode.RGB);for i,sp in ipairs(specs)do if i>1 then gif:newEmptyFrame()end;local im=Image(W,H,ColorMode.RGB);im:drawSprite(spr,15+i);gif:newCel(gif.layers[1],i,im,Point(0,0));gif.frames[i].duration=sp.duration end;gif:saveCopyAs(out..'attack.gif');gif:close()
local ff=io.open(base..'anchors.json','w');ff:write(json.encode(anchors));ff:close();spr:close();old:close()
