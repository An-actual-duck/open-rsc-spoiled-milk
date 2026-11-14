local root=app.params['root'];local base=root..'/dev/myworld/assets/authoring/monsters/death-wing/';local out=root..'/output/art/bat-npc-wing-colors/';local pc=app.pixelColor
local function C(r,g,b)return pc.rgba(r,g,b,255)end
local dark={[C(97,97,97)]=C(22,23,27),[C(124,124,124)]=C(43,45,51),[C(182,182,182)]=C(76,79,87),[C(108,68,18)]=C(42,34,27),[C(146,103,33)]=C(65,53,38),[C(175,132,58)]=C(91,76,53),[C(76,76,76)]=C(18,19,22),[C(102,102,102)]=C(22,23,27),[C(127,127,127)]=C(43,45,51),[C(183,183,183)]=C(76,79,87)}
local membrane={[C(97,97,97)]=true,[C(124,124,124)]=true,[C(182,182,182)]=true,[C(76,76,76)]=true,[C(102,102,102)]=true,[C(127,127,127)]=true,[C(183,183,183)]=true}
-- Original authentic sprite-frame crop offsets, restored without recentering.
local shifts={{2,16},{0,13},{13,0},{8,20},{2,13},{9,6},{37,21},{24,8},{31,0},{22,10},{29,5},{38,0},{1,14},{1,10},{13,0},{0,4},{28,8},{30,3}}
-- Conservative central body/leg-root guards; match the approved front guard exactly.
local guards={{62,88},{62,88},{62,88},{59,98},{59,98},{59,98},{64,112},{64,112},{64,112},{48,91},{48,91},{48,91},{61,90},{61,90},{61,90},{27,89},{43,97},{45,97}}
local s=Sprite(150,130,ColorMode.RGB);s.layers[1].name='Body - authentic unchanged';local bodyLayer=s.layers[1];local wings=s:newLayer();wings.name='Wings - near-black palette'
-- Keep wings behind the complete original body, as on the authentic flattened source.
wings.stackIndex=1
local atlas=Image(847,390,ColorMode.RGB);local sourceAtlas=Image(847,390,ColorMode.RGB);local audit={}
for n=0,17 do
 if n>0 then s:newEmptyFrame()end;s.frames[n+1].duration=.15
 local raw=Image{fromFile=base..string.format('inputs/bat/frame-%02d.png',n)};local shift=shifts[n+1];local guard=guards[n+1]
 local width=n<15 and 150 or 97;local original=Image(width,130,ColorMode.RGB);original:drawImage(raw,Point(shift[1],shift[2]))
 local body,plate,mask=Image(width,130,ColorMode.RGB),Image(width,130,ColorMode.RGB),Image(width,130,ColorMode.RGB);local count=0
 for y=0,raw.height-1 do for x=0,raw.width-1 do
  local color=raw:getPixel(x,y);local xx,yy=x+shift[1],y+shift[2];local isWing=membrane[color]
  if not isWing and dark[color]and(xx<guard[1]or xx>guard[2])then
   for dy=-2,2 do for dx=-2,2 do local nx,ny=x+dx,y+dy
    if nx>=0 and nx<raw.width and ny>=0 and ny<raw.height and membrane[raw:getPixel(nx,ny)]then isWing=true end
   end end
  end
  if isWing then plate:drawPixel(xx,yy,dark[color]);mask:drawPixel(xx,yy,C(255,255,255));count=count+1
  else body:drawPixel(xx,yy,color)end
  if xx>=guard[1]and xx<=guard[2]and not membrane[color]then assert(not isWing,'Body was recolored')end
 end end
 local im=Image(width,130,ColorMode.RGB);im:drawImage(plate,Point(0,0));im:drawImage(body,Point(0,0))
 for y=0,129 do for x=0,width-1 do
  assert(pc.rgbaA(original:getPixel(x,y))==pc.rgbaA(im:getPixel(x,y)),'Death Wing alpha changed')
  if pc.rgbaA(mask:getPixel(x,y))==0 then assert(original:getPixel(x,y)==im:getPixel(x,y),'Nonwing pixels changed')end
 end end
 s:newCel(bodyLayer,n+1,body,Point(0,0));s:newCel(wings,n+1,plate,Point(0,0))
 im:saveAs(base..string.format('frame-%02d.png',n));im:saveAs(out..string.format('death-wing-%02d.png',n));original:saveAs(out..string.format('death-wing-original-%02d.png',n));mask:saveAs(out..string.format('death-wing-mask-%02d.png',n))
 local col=math.floor(n/3);local row=n%3;atlas:drawImage(im,Point(col*150,row*130));sourceAtlas:drawImage(original,Point(col*150,row*130))
 audit[#audit+1]={frame=n,shift=shift,canvas={width,130},bodyGuardX=guard,recoloredWingPixels=count}
end
for group,name in ipairs({'front','front-right','right','rear-right','rear','attack'})do local tag=s:newTag((group-1)*3+1,group*3);tag.name=name end
s:saveAs(base..'death-wing.aseprite');atlas:saveAs(root..'/dev/myworld/assets/runtime/sprites/npcs/death-wing/death-wing.png');atlas:saveAs(out..'death-wing-atlas.png');sourceAtlas:saveAs(out..'death-wing-original-atlas.png')
local f=io.open(base..'frame-layout.json','w');f:write(json.encode(audit));f:close();s:close();print('PASS eighteen Death Wing frames: authentic offsets, alpha and all nonwing pixels preserved.')
