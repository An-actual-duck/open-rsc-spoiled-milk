-- Native Aseprite articulation of the approved true-profile anatomy.
local root=app.params['root'];local base=root..'/dev/myworld/assets/authoring/monsters/stirge/profile-attack/'
local out=root..'/output/art/stirge-profile-attack/'
local s=app.open(base..'inputs/approved-flight.aseprite');local pc=app.pixelColor
local function full(l,f)local im=Image(150,130,ColorMode.RGB);local c=l:cel(f);if c then im:drawImage(c.image,c.position)end;return im end
local function rot(x,y,a)local c,t=math.cos(a),math.sin(a);return x*c-y*t,x*t+y*c end
local function line(im,x,y,xx,yy,color)
 x=math.floor(x+.5);y=math.floor(y+.5);xx=math.floor(xx+.5);yy=math.floor(yy+.5)
 local dx,dy=math.abs(xx-x),-math.abs(yy-y);local sx=x<xx and 1 or -1;local sy=y<yy and 1 or -1;local err=dx+dy
 while true do if x>=0 and x<150 and y>=0 and y<130 then im:drawPixel(x,y,color)end;if x==xx and y==yy then break end;local e=2*err;if e>=dy then err=err+dy;x=x+sx end;if e<=dx then err=err+dx;y=y+sy end end
end
local specs={
 {name='Wind up',cx=33,cy=45,body=-15,head=30,wingFrame=9,wingX=.58,wingY=.78,wingAngle=-10,duration=.22},
 {name='Plunge',cx=42,cy=44,body=12,head=-45,wingFrame=7,wingX=.62,wingY=.78,wingAngle=12,duration=.12},
 {name='Stabilize',cx=37,cy=43,body=-4,head=-12,wingFrame=9,wingX=.65,wingY=.88,wingAngle=-4,duration=.20}
}
local sources={};for _,l in ipairs(s.layers)do sources[l.name]={};for f=7,9 do sources[l.name][f]=full(l,f)end end
local function transform(src,px,py,tx,ty,angle,sx,sy)
 local im=Image(150,130,ColorMode.RGB);angle=math.rad(angle)
 for y=0,129 do for x=0,149 do
  local xx,yy=rot(x-tx,y-ty,-angle);xx=math.floor(px+xx/sx+.5);yy=math.floor(py+yy/sy+.5)
  if xx>=0 and xx<150 and yy>=0 and yy<130 then im:drawPixel(x,y,src:getPixel(xx,yy))end
 end end;return im
end
for i,sp in ipairs(specs)do
 local f=15+i;local angle=math.rad(sp.body)
 local nx,ny=rot(6,-2,angle);nx=sp.cx+nx;ny=sp.cy+ny
 for _,l in ipairs(s.layers)do
  local im=Image(150,130,ColorMode.RGB)
  if l.name=='Rust bat wings'then
   local pivot=sp.wingFrame==9 and {82,39}or{82,40}
   -- Preserve the profile wing silhouettes, folding their span for the narrow attack canvas.
   im=transform(sources[l.name][sp.wingFrame],pivot[1],pivot[2],sp.cx,sp.cy,sp.wingAngle,sp.wingX,sp.wingY)
  elseif l.name=='Mosquito head and proboscis'then
   -- Keep the skull rigid, articulate its needle separately for the stabbing projection.
   local skull=Image(sources[l.name][8])
   local mx,my=105.64,37.004
   local midx,midy=mx+37*.38,my+28*.32
   line(skull,mx,my,midx,midy,0);line(skull,midx,midy,mx+37,my+28,0)
   im=transform(skull,89,38,nx,ny,sp.body+sp.head,1,1)
   local aa=math.rad(sp.body+sp.head);local ox,oy=rot(mx-89,my-38,aa)
   local mouthx,mouthy=nx+ox,ny+oy
   local needleAngle=math.atan(28,37)+aa
   local len=33 -- constant projected needle length throughout the attack
   local tipx,tipy=mouthx+math.cos(needleAngle)*len,mouthy+math.sin(needleAngle)*len
   line(im,mouthx,mouthy,tipx,tipy,pc.rgba(149,121,74,255))
   line(im,mouthx,mouthy,mouthx+(tipx-mouthx)*.35,mouthy+(tipy-mouthy)*.35,pc.rgba(175,139,77,255))
   im:drawPixel(math.floor(tipx+.5),math.floor(tipy+.5),pc.rgba(42,27,24,255))
  elseif l.name=='Striped abdomen and moth thorax'or l.name=='Near legs - thorax roots'or l.name=='Far legs - thorax roots'then
   im=transform(sources[l.name][8],83,40,sp.cx,sp.cy,sp.body,1,1)
  end
  for y=0,129 do for x=97,149 do assert(pc.rgbaA(im:getPixel(x,y))==0,'Attack exceeds width: '..sp.name..' '..l.name..' '..x..','..y)end end
  s:newCel(l,f,im,Point(0,0))
 end
 s.frames[f].duration=sp.duration
end
s:saveAs(base..'stirge-profile-attack.aseprite')
local sheet=Image(450,780,ColorMode.RGB)
for i=0,17 do local im=Image(i>=15 and 97 or 150,130,ColorMode.RGB);im:drawSprite(s,i+1);im:saveAs(base..string.format('frame-%02d.png',i));im:saveAs(out..string.format('frame-%02d.png',i));sheet:drawImage(im,Point(i%3*150,math.floor(i/3)*130))end
sheet:saveAs(base..'stirge-profile-attack-sheet.png');sheet:saveAs(out..'stirge-profile-attack-sheet.png')
local gif=Sprite(97,130,ColorMode.RGB)
for i,sp in ipairs(specs)do if i>1 then gif:newEmptyFrame()end;local im=Image(97,130,ColorMode.RGB);im:drawSprite(s,15+i);gif:newCel(gif.layers[1],i,im,Point(0,0));gif.frames[i].duration=sp.duration end
gif:saveCopyAs(out..'attack.gif');gif:saveAs(base..'attack-only.aseprite');gif:close()
local file=io.open(base..'articulation.json','w');file:write(json.encode(specs));file:close();s:close()
