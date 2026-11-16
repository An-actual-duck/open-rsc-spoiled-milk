-- Simplified icon identity over the basic held weapon; stock/grip positions stay anchored.
local root=app.params.root
local pc=app.pixelColor
local landmarks={
 {{4,59},{35,59},{20,67},{0,-1}},
 {{24,67},{47,56},{41,66},{-.85,-.5}},
 {{41,61},{46,45},{46,53},{-1,0}},
 {{42,40},{60,58},{60,47},{-.9,.4}},
 {{44,47},{60,52},{47,51},{0,1}}
}
for _,god in ipairs({'saradomin','zamorak','guthix'})do for f=0,14 do
 local spr=app.open(root..'/'..god..'/'..string.format('%02d',f)..'.png');local im=spr.cels[1].image
 local ref=app.open(root..'/inputs/'..string.format('%02d',math.floor(f/3)*3+1)..'.png');local refim=ref.cels[1].image
 local source=app.open(root..'/inputs/'..string.format('%02d',f)..'.png');local original=source.cels[1].image
 local function bounds(img)
  local x0,y0,x1,y1=1000,1000,0,0
  for y=0,img.height-1 do for x=0,img.width-1 do if pc.rgbaA(img:getPixel(x,y))>0 then x0=math.min(x0,x);y0=math.min(y0,y);x1=math.max(x1,x);y1=math.max(y1,y)end end end
  return {x0,y0,x1,y1}
 end
 local a,b=bounds(refim),bounds(original)
 local function project(p)return {b[1]+(p[1]-a[1])*(b[3]-b[1])/(a[3]-a[1]),b[2]+(p[2]-a[2])*(b[4]-b[2])/(a[4]-a[2])}end
 local mark=landmarks[math.floor(f/3)+1];local centre=project(mark[3]);local back=mark[4]
 local function dot(x,y,c)
  x=math.floor(x+.5);y=math.floor(y+.5)
  if x>=0 and x<64 and y>=0 and y<102 then im:drawPixel(x,y,pc.rgba((c>>16)&255,(c>>8)&255,c&255,255))end
 end
 local function line(x0,y0,x1,y1,c)
  local n=math.max(math.abs(x1-x0),math.abs(y1-y0));for i=0,math.ceil(n)do local t=n==0 and 0 or math.min(1,i/n);dot(x0+(x1-x0)*t,y0+(y1-y0)*t,c)end
 end
 local function polygon(points,c)
  for y=30,85 do for x=0,63 do local inside=false;local j=#points
   for i=1,#points do local a,b=points[i],points[j];if(a[2]>y+.5)~=(b[2]>y+.5)and x+.5<(b[1]-a[1])*(y+.5-a[2])/(b[2]-a[2])+a[1]then inside=not inside end;j=i end
   if inside then dot(x,y,c)end
  end end
 end
 for side=1,2 do
  local tip=project(mark[side]);local dx,dy=tip[1]-centre[1],tip[2]-centre[2];local len=math.sqrt(dx*dx+dy*dy);local ux,uy=dx/len,dy/len
  if god=='saradomin'then
   -- Gold layered fan/rays and a compact star at each limb end.
   for k=1,3 do local t=.25+k*.17;local x,y=centre[1]+dx*t,centre[2]+dy*t
    line(x,y,x+back[1]*(2+k%2),y+back[2]*(2+k%2),0xf4d485)
    dot(x+back[1]*(3+k%2),y+back[2]*(3+k%2),0xb88029)
   end
   local tx=math.max(3,math.min(60,tip[1]));local ty=tip[2]
   for d=-3,3 do dot(tx+d,ty,0xe9b545);dot(tx,ty+d,0xe9b545)end
   for d=-1,1 do dot(tx+d,ty+d,0xffedb2);dot(tx+d,ty-d,0xffedb2)end
   dot(tx,ty,0xf9fcff)
  elseif god=='zamorak'then
   -- Curled, tapered horns grow continuously out of each limb, with red ridges.
   if tip[1]>57 then ux=math.min(ux,0);back={-1,back[2]}end
   local bx,by=back[1],back[2]
   local function q(out,away)return {tip[1]+ux*out+bx*away,tip[2]+uy*out+by*away}end
   local points={q(-4,0),q(-1,-1),q(2,1),q(4,4),q(3,8),q(2,10),q(1,6),q(-1,3),q(-4,2)}
   polygon(points,0x231e29)
   for k=1,4 do local p,q0=points[k],points[k+1];line(p[1],p[2],q0[1],q0[2],0x514553)end
   line(tip[1]-ux*3,tip[2]-uy*3,tip[1]+bx*2,tip[2]+by*2,0x992331)
   for k=1,2 do local t=.35+k*.2;local x,y=centre[1]+dx*t,centre[2]+dy*t;line(x,y,x+bx*3,y+by*3,0x342834);dot(x+bx*3,y+by*3,0xa23840)end
  else
   -- Living vine follows each limb; leaf clusters and thorns distinguish it from a plain green bow.
   for k=1,5 do local t=k/6;local x,y=centre[1]+dx*t,centre[2]+dy*t;dot(x,y,0x365124);dot(x+back[1],y+back[2],0x577736)
    if k%2==0 then
     local sx,sy=x+back[1]*3,y+back[2]*3;line(x,y,sx,sy,0x365124);polygon({{sx-1,sy},{sx+1,sy-2},{sx+3,sy-1},{sx+1,sy+1}},0x7d9e4d)
    end
   end
   local tx=math.max(3,math.min(59,tip[1]));local ty=tip[2]
   polygon({{tx-4,ty},{tx-3,ty-3},{tx,ty-1},{tx+2,ty-4},{tx+4,ty-3},{tx+2,ty},{tx,ty+2}},0x4e7131)
   line(tx-2,ty-1,tx+2,ty-2,0xa5bc69)
  end
 end
 if god=='saradomin'then dot(centre[1],centre[2],0xffefbb);dot(centre[1]-1,centre[2]-1,0xe4b144)
 elseif god=='guthix'then
  -- A small vine curl around the stock, clear of the original grip.
  local x,y=centre[1]+back[1]*5,centre[2]+back[2]*5
  line(x-2,y-1,x+2,y+1,0x385425);dot(x+2,y,0x718c42)
 end
 spr:saveAs(root..'/detail-v2/'..god..'/'..string.format('%02d',f)..'.aseprite');spr:saveCopyAs(root..'/detail-v2/'..god..'/'..string.format('%02d',f)..'.png')
 spr:close();source:close();ref:close()
end end
