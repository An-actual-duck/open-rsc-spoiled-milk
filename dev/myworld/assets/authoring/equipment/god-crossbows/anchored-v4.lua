-- Restart from unchanged basic crossbow; only clear material/limb changes.
local root=app.params.root
local pc=app.pixelColor
-- String attachments A/B, bow belly C, and the rear/front stock axis.
local poses={
 {{5,58},{34,58},{20,64},{20,51},{20,68},{0,1}},
 {{5,58},{34,58},{20,67},{20,50},{20,71},{0,1}},
 {{5,60},{34,60},{20,70},{20,49},{20,73},{0,1}},
 {{21,66},{44,60},{37,72},{20,52},{40,70},{0.8,0.6}},
 {{25,66},{47,56},{38,69},{23,52},{45,65},{0.8,0.6}},
 {{28,64},{48,50},{40,65},{23,50},{49,61},{0.8,0.6}},
 {{35,60},{44,47},{46,56},{19,47},{47,55},{1,0}},
 {{39,59},{46,46},{49,54},{21,48},{49,53},{1,0}},
 {{43,59},{45,43},{52,50},{22,48},{52,50},{1,0}},
 {{43,41},{60,58},{62,48},{34,47},{62,48},{0.85,-0.5}},
 {{42,42},{61,55},{62,47},{34,49},{62,47},{0.85,-0.5}},
 {{40,40},{61,51},{62,43},{34,49},{62,43},{0.85,-0.5}},
 {{30,55},{60,55},{45,49},{45,61},{45,47},{0,-1}},
 {{30,54},{60,54},{45,46},{45,61},{45,44},{0,-1}},
 {{30,53},{59,53},{45,43},{45,60},{45,41},{0,-1}},
}
local palettes={
 saradomin={stock={174,187,203},limb={215,165,66},string={145,139,121},bolt={220,231,240}},
 zamorak={stock={155,29,38},limb={43,36,47},string={136,37,47},bolt={39,32,43}},
 guthix={stock={115,75,39},limb={123,82,42},string={99,94,63},bolt={169,157,104}}
}
local function distance(x,y,a,b)
 local dx,dy=b[1]-a[1],b[2]-a[2];local den=dx*dx+dy*dy
 local t=den==0 and 0 or math.max(0,math.min(1,((x-a[1])*dx+(y-a[2])*dy)/den))
 return math.sqrt((x-a[1]-t*dx)^2+(y-a[2]-t*dy)^2)
end
for _,god in ipairs({'saradomin','zamorak','guthix'})do for frame=0,14 do
 local spr=app.open(root..'/inputs/'..string.format('%02d',frame)..'.png');local original=Image(spr.cels[1].image);local im=spr.cels[1].image
 local pose=poses[frame+1];local A,B,C,H,N=pose[1],pose[2],pose[3],pose[4],pose[5];local forward=pose[6];local p=palettes[god]
 local bowStart=frame>=12 and .5 or 0

 local foreground={}
 local function rgb(c)return pc.rgba((c>>16)&255,(c>>8)&255,c&255,255)end
 local function dot(x,y,c)
  x=math.floor(x+.5);y=math.floor(y+.5)
  if x>=0 and x<64 and y>=0 and y<102 then im:drawPixel(x,y,rgb(c))end
 end
 local function line(a,b,c)
  local n=math.ceil(math.max(math.abs(a[1]-b[1]),math.abs(a[2]-b[2])))
  for i=0,n do local t=n==0 and 0 or i/n;dot(a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t,c)end
 end
 local function polygon(points,c)
  for y=30,85 do for x=0,63 do local inside=false;local j=#points
   for i=1,#points do local a,b=points[i],points[j];if(a[2]>y+.5)~=(b[2]>y+.5)and x+.5<(b[1]-a[1])*(y+.5-a[2])/(b[2]-a[2])+a[1]then inside=not inside end;j=i end
   if inside then dot(x,y,c)end
  end end
 end
 -- Recolour stock, string and mechanism directly from the baseline.
 for y=0,101 do for x=0,63 do
  local v=original:getPixel(x,y);local alpha=pc.rgbaA(v)
  if alpha>0 then
   local r,g,b=pc.rgbaR(v),pc.rgbaG(v),pc.rgbaB(v);local grey=r==g and g==b
   local pal,shade=p.stock,math.max(.35,math.min(1.15,r/140))
   if grey then pal=r<100 and p.string or p.limb;shade=r/155
   elseif r>220 and g<180 then pal=p.bolt;shade=.8+g/700 end
   local function ch(c)return math.max(1,math.min(255,math.floor(c*shade+.5)))end
   local color=pc.rgba(ch(pal[1]),ch(pal[2]),ch(pal[3]),255)
   local onAxis=distance(x,y,H,N)<=1.3
   local nearLimb=math.min(distance(x,y,A,C),distance(x,y,C,B))<=5
   local nearString=(frame<12 and distance(x,y,H,A)<=2) or distance(x,y,H,B)<=2
   local erase=god~='guthix' and grey and ((nearLimb and not onAxis)or(r<100 and nearString))
   if erase then im:drawPixel(x,y,0)else im:drawPixel(x,y,color)end
   if not grey or (grey and r>=100 and onAxis) then foreground[#foreground+1]={x,y,color}end
  end
 end end
 local mid={(A[1]+B[1])/2,(A[2]+B[2])/2};local depth={C[1]-mid[1],C[2]-mid[2]}
 local function curve(t,scale)
  local arch=math.sin(math.pi*t)*scale
  return {A[1]+(B[1]-A[1])*t+depth[1]*arch,A[2]+(B[2]-A[2])*t+depth[2]*arch}
 end
 if god=='saradomin'then
  -- One continuous tapered crescent, no stars, rays or extra ornaments.
  local outer,inner={},{}
  for k=0,32 do local t=bowStart+(1-bowStart)*k/32;outer[#outer+1]=curve(t,1.08);inner[#inner+1]=curve(t,.43)end
  local outline={};for _,v in ipairs(outer)do outline[#outline+1]=v end;for k=#inner,1,-1 do outline[#outline+1]=inner[k]end
  polygon(outline,0xd1a041)
  for k=2,#outer do line(outer[k-1],outer[k],0x98702f);line(inner[k-1],inner[k],0xf1d28b)end
 elseif god=='zamorak'then
  -- Plain black bow arms. String still attaches at A/B; horns continue forward.
  local points={}
  for k=0,24 do points[#points+1]=curve(bowStart+(1-bowStart)*k/24,1)end
  for k=24,0,-1 do points[#points+1]=curve(bowStart+(1-bowStart)*k/24,.67)end
  polygon(points,0x302732)
  for k=1,24 do line(curve(bowStart+(1-bowStart)*(k-1)/24,.68),curve(bowStart+(1-bowStart)*k/24,.68),0x514451)end
  for side,tip in ipairs({A,B})do
   if frame<12 or side==2 then
   local dx,dy=tip[1]-mid[1],tip[2]-mid[2];local len=math.sqrt(dx*dx+dy*dy);local ux,uy=dx/len,dy/len
   -- Keep the forward-facing hook inside the fixed native canvas in rear views.
   if tip[1]>58 then ux=-math.abs(ux)*.3 end
   local fscale=1
   if forward[1]>0 then fscale=math.max(.25,math.min(1,(62-tip[1])/(8*forward[1])))end
   local function q(out,fwd)return {tip[1]+ux*out+forward[1]*fwd*fscale,tip[2]+uy*out+forward[2]*fwd*fscale}end
   local horn={q(-2,0),q(1,0),q(2,3),q(1,6),q(-1,8),q(0,4),q(-2,2)}
   polygon(horn,0x28212d)
   for k=2,4 do line(horn[k],horn[k+1],0x574956)end
   end
  end
 end
 -- String, limbs and stock use the same explicit source-frame anchors.
 if god~='guthix' then
  local stringColor=god=='saradomin' and 0x888274 or 0x88252f
  if frame<12 then line(H,A,stringColor)end
  line(H,B,stringColor)
 end
 -- Existing stock and bolt sit in front of the transverse limbs, preserving grip.
 for _,v in ipairs(foreground)do im:drawPixel(v[1],v[2],v[3])end
 if god=='guthix'then
  -- Continuous small vines plus denser, attached ivy across stock and both arms.
  local function ivyDot(x,y,c)
   local xx,yy=math.floor(x+.5),math.floor(y+.5)
   if xx>=0 and xx<64 and yy>=0 and yy<102 and pc.rgbaA(original:getPixel(xx,yy))>0 then dot(xx,yy,c)end
  end
  local function leaf(x,y,flip)
   ivyDot(x,y,0x34512b);ivyDot(x+flip,y-1,0x4d743a);ivyDot(x+flip*2,y-1,0x78954b)
   ivyDot(x+flip,y,0x5c813e);ivyDot(x,y+1,0x365b2c);ivyDot(x+flip,y+1,0x6f9148)
  end
  local dx,dy=N[1]-H[1],N[2]-H[2];local len=math.sqrt(dx*dx+dy*dy);local ux,uy=-dy/len,dx/len
  for k=0,20 do
   local t=.1+.8*k/20;local side=2+math.sin(t*math.pi*4)
   ivyDot(H[1]+dx*t+ux*side,H[2]+dy*t+uy*side,0x35552b)
  end
  for k,t in ipairs({.13,.25,.38,.52,.64,.77,.87})do
   local side=k%2==0 and -2 or 2
   leaf(H[1]+dx*t+ux*side,H[2]+dy*t+uy*side,k%2==0 and -1 or 1)
  end
  for k,t0 in ipairs({.1,.23,.36,.64,.77,.9})do
   local t=bowStart+(1-bowStart)*t0;local v=curve(t,.85)
   leaf(v[1],v[2],k%2==0 and -1 or 1)
  end
 end
 -- Clearing old limbs can uncover tiny disconnected remnants of their shading.
 -- Keep the connected weapon; do not leave floating pixels or old limb strips.
 if god~='guthix' then
  local seen,groups={},{}
  for yy=0,101 do for xx=0,63 do
   local key=yy*64+xx
   if not seen[key] and pc.rgbaA(im:getPixel(xx,yy))>0 then
    local group={{xx,yy}};local n=1;seen[key]=true
    while n<=#group do
     local v=group[n];n=n+1
     for dy=-1,1 do for dx=-1,1 do
      local x,y=v[1]+dx,v[2]+dy;local k=y*64+x
      if x>=0 and x<64 and y>=0 and y<102 and not seen[k] and pc.rgbaA(im:getPixel(x,y))>0 then seen[k]=true;group[#group+1]={x,y}end
     end end
    end
    groups[#groups+1]=group
   end
  end end
  table.sort(groups,function(a,b)return #a>#b end)
  for k=2,#groups do for _,v in ipairs(groups[k])do im:drawPixel(v[1],v[2],0)end end
 end
 spr:saveAs(root..'/anchored-v4/'..god..'/'..string.format('%02d',frame)..'.aseprite');spr:saveCopyAs(root..'/anchored-v4/'..god..'/'..string.format('%02d',frame)..'.png');spr:close()
end end
