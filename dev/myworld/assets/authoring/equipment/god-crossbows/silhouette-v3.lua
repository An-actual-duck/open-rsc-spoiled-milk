-- Restart from unchanged basic crossbow; only clear material/limb changes.
local root=app.params.root
local pc=app.pixelColor
-- String attachments A/B, bow belly C, and the rear/front stock axis.
local poses={
 {{4,59},{35,59},{20,67},{20,50},{20,71},{0,1}},
 {{24,67},{47,56},{41,69},{21,51},{45,67},{.8,.6}},
 {{41,61},{46,45},{49,54},{20,49},{49,54},{1,0}},
 {{42,40},{60,58},{62,47},{33,50},{61,47},{.85,-.5}},
 {{42,47},{61,52},{51,48},{45,61},{45,45},{0,-1}}
}
local palettes={
 saradomin={stock={174,187,203},limb={215,165,66},string={145,139,121},bolt={220,231,240}},
 zamorak={stock={155,29,38},limb={43,36,47},string={136,37,47},bolt={39,32,43}},
 guthix={stock={115,75,39},limb={123,82,42},string={99,94,63},bolt={169,157,104}}
}
local function bounds(img)
 local a={999,999,0,0}
 for y=0,img.height-1 do for x=0,img.width-1 do if pc.rgbaA(img:getPixel(x,y))>0 then a[1]=math.min(a[1],x);a[2]=math.min(a[2],y);a[3]=math.max(a[3],x);a[4]=math.max(a[4],y)end end end
 return a
end
local function distance(x,y,a,b)
 local dx,dy=b[1]-a[1],b[2]-a[2];local den=dx*dx+dy*dy
 local t=den==0 and 0 or math.max(0,math.min(1,((x-a[1])*dx+(y-a[2])*dy)/den))
 return math.sqrt((x-a[1]-t*dx)^2+(y-a[2]-t*dy)^2)
end
for _,god in ipairs({'saradomin','zamorak','guthix'})do for frame=0,14 do
 local spr=app.open(root..'/inputs/'..string.format('%02d',frame)..'.png');local original=Image(spr.cels[1].image);local im=spr.cels[1].image
 local reference=app.open(root..'/inputs/'..string.format('%02d',math.floor(frame/3)*3+1)..'.png');local a,b=bounds(reference.cels[1].image),bounds(original);reference:close()
 local function project(p)return {b[1]+(p[1]-a[1])*(b[3]-b[1])/(a[3]-a[1]),b[2]+(p[2]-a[2])*(b[4]-b[2])/(a[4]-a[2])}end
 local pose=poses[math.floor(frame/3)+1];local A,B,C,H,N=project(pose[1]),project(pose[2]),project(pose[3]),project(pose[4]),project(pose[5]);local forward=pose[6];local p=palettes[god]
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
   local erase=god~='guthix' and grey and r>=100 and nearLimb and not onAxis
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
  for k=0,32 do outer[#outer+1]=curve(k/32,1.05);inner[#inner+1]=curve(k/32,.64)end
  local outline={};for _,v in ipairs(outer)do outline[#outline+1]=v end;for k=#inner,1,-1 do outline[#outline+1]=inner[k]end
  polygon(outline,0xd1a041)
  for k=2,#outer do line(outer[k-1],outer[k],0x98702f);line(inner[k-1],inner[k],0xf1d28b)end
 elseif god=='zamorak'then
  -- Plain black bow arms. String still attaches at A/B; horns continue forward.
  local points={}
  for k=0,24 do points[#points+1]=curve(k/24,1)end
  for k=24,0,-1 do points[#points+1]=curve(k/24,.67)end
  polygon(points,0x302732)
  for k=1,24 do line(curve((k-1)/24,.68),curve(k/24,.68),0x514451)end
  for side,tip in ipairs({A,B})do
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
 -- Existing stock and bolt sit in front of the transverse limbs, preserving grip.
 for _,v in ipairs(foreground)do im:drawPixel(v[1],v[2],v[3])end
 if god=='guthix'then
  -- Several small, deliberately separated ivy patches across brown wood.
  local function leaf(x,y,flip)
   dot(x,y,0x34512b);dot(x+flip,y-1,0x4d743a);dot(x+flip*2,y-1,0x78954b);dot(x+flip,y,0x5c813e)
  end
  for k,t in ipairs({.2,.47,.73})do
   local x,y=H[1]+(N[1]-H[1])*t,H[2]+(N[2]-H[2])*t
   local flip=k%2==0 and -1 or 1
   line({x-flip,y+1},{x+flip,y-1},0x3c5a2b);leaf(x,y,flip)
  end
  for k,t in ipairs({.13,.32,.68,.87})do local v=curve(t,.85);leaf(v[1],v[2],k%2==0 and -1 or 1)end
 end
 spr:saveAs(root..'/silhouette-v3/'..god..'/'..string.format('%02d',frame)..'.aseprite');spr:saveCopyAs(root..'/silhouette-v3/'..god..'/'..string.format('%02d',frame)..'.png');spr:close()
end end
