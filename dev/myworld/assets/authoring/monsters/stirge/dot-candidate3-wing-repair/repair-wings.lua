-- Focused native Aseprite repair of Dot's candidate 3. No anatomy regeneration.
local root=app.params['root']
local base=root..'/dev/myworld/assets/authoring/monsters/stirge/dot-candidate3-wing-repair/'
local out=root..'/output/art/stirge-dot-wing-repair/'
local s=app.open(base..'inputs/stirge-candidate-3.aseprite')
local pc=app.pixelColor
local function C(r,g,b)return pc.rgba(r,g,b,255)end
local rust0,rust1,rust2,rib=C(153,61,28),C(193,85,32),C(218,113,42),C(91,45,27)
local wingLayer
for _,layer in ipairs(s.layers)do if layer.name=='Rust bat wings'then wingLayer=layer end end
assert(wingLayer,'Missing source wing layer')
local specs={
 {frame=3,root={75,36},corner={93,25},outline={{75,36},{86,28},{94,25},{98,31},{97,39},{86,43}}},
 {frame=4,root={74,37},corner={93,23},outline={{74,37},{88,25},{94,24},{99,31},{105,41},{89,43}}},
 {frame=5,root={74,38},corner={91,24},outline={{74,38},{86,25},{92,24},{101,32},{91,40}}}
}
local function inside(x,y,poly)
 local result=false;local j=#poly
 for i=1,#poly do
  local a,b=poly[i],poly[j]
  if(a[2]>y)~=(b[2]>y)and x<(b[1]-a[1])*(y-a[2])/(b[2]-a[2])+a[1]then result=not result end
  j=i
 end
 return result
end
local function line(im,a,b,c)
 local x,y=a[1],a[2];local dx=math.abs(b[1]-x);local sx=x<b[1]and 1 or -1
 local dy=-math.abs(b[2]-y);local sy=y<b[2]and 1 or -1;local err=dx+dy
 while true do
  if x>=0 and x<150 and y>=0 and y<130 then im:drawPixel(x,y,c)end
  if x==b[1]and y==b[2]then break end
  local e=2*err;if e>=dy then err=err+dy;x=x+sx end;if e<=dx then err=err+dx;y=y+sy end
 end
end
for _,spec in ipairs(specs)do
 local oldCel=wingLayer:cel(spec.frame+1)
 local original=Image(150,130,ColorMode.RGB);original:drawImage(oldCel.image,oldCel.position)
 local repaired=Image(original)
 for y=0,129 do for x=0,149 do
  if pc.rgbaA(original:getPixel(x,y))==0 and inside(x+.5,y+.5,spec.outline)then
   local bestColor=rust0;local bestDist=1e9
   for yy=math.max(0,y-13),math.min(129,y+13)do for xx=math.max(0,x-13),math.min(149,x+13)do
    local c=original:getPixel(xx,yy)
    if c==rust0 or c==rust1 or c==rust2 then
     local d=(xx-x)^2+(yy-y)^2;if d<bestDist then bestDist=d;bestColor=c end
    end
   end end
   -- Continue the existing membrane grain from the same frame's near wing.
   -- This copies only its three rust membrane shades, never a rib or anatomy.
   if x>=29 and y+1<130 then
    local donor=original:getPixel(x-29,y+1)
    if donor==rust0 or donor==rust1 or donor==rust2 then bestColor=donor end
   end
   if y>=spec.root[2]+4 then bestColor=rust0 end
   repaired:drawPixel(x,y,bestColor)
  end
 end end
 -- A narrow leading root rib joins the new membrane naturally to the far wing.
 line(repaired,spec.root,spec.corner,rib)
 -- Raise the intact flap pose toward the upper thorax; move slightly headward.
 local raised=Image(150,130,ColorMode.RGB);raised:drawImage(repaired,Point(3,-5))
 -- Close the residual body-stencil gaps bounded by membrane on both sides.
 -- The small shoulder ROI excludes outer tips and the wing's scalloped edge.
 local shoulder=Image(raised)
 for y=16,37 do
  local first,last=nil,nil
  for x=70,105 do if pc.rgbaA(shoulder:getPixel(x,y))>0 then first=first or x;last=x end end
  if first and last then for x=first,last do
   if pc.rgbaA(shoulder:getPixel(x,y))==0 then
    local color=rust1;local donor=shoulder:getPixel(x-29,y+1)
    if donor==rust0 or donor==rust1 or donor==rust2 then color=donor end
    raised:drawPixel(x,y,color)
   end
  end end
 end
 s:newCel(wingLayer,spec.frame+1,raised,Point(0,0))
end
s:saveAs(base..'stirge-wing-repaired.aseprite')
local imgs={}
for frame=0,17 do
 local width=frame>=15 and 97 or 150
 local image=Image(width,130,ColorMode.RGB);image:drawSprite(s,frame+1);imgs[frame+1]=Image(image)
 image:saveAs(base..string.format('frame-%02d.png',frame));image:saveAs(out..string.format('frame-%02d.png',frame))
end
local sheet=Image(450,780,ColorMode.RGB)
for n,im in ipairs(imgs)do sheet:drawImage(im,Point(((n-1)%3)*150,math.floor((n-1)/3)*130))end
sheet:saveAs(base..'stirge-wing-repaired-sheet.png');sheet:saveAs(out..'stirge-wing-repaired-sheet.png')
local function append(sprite,image)
 if image.width~=150 then local padded=Image(150,130,ColorMode.RGB);padded:drawImage(image,Point(math.floor((150-image.width)/2),0));image=padded end
 local n=#sprite.frames;if sprite.cels[1]then sprite:newEmptyFrame();n=n+1 end
 sprite:newCel(sprite.layers[1],n,Image(image),Point(0,0));sprite.frames[n].duration=.15
end
for group,name in ipairs({'front','front-right','right-profile','rear-right','rear','attack-right'})do
 local gif=Sprite(150,130,ColorMode.RGB);gif:deleteCel(gif.cels[1])
 for _,phase in ipairs({0,1,2,1})do append(gif,imgs[(group-1)*3+phase+1])end
 gif:saveCopyAs(out..name..'.gif');gif:close()
end
s:close()
