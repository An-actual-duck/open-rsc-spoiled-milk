local root=assert(app.params.root);local out=assert(app.params.out);local pc=app.pixelColor
local ref=app.open(root..'/v1/goblin-a.aseprite');local palette,seen={},{}
for it in ref.cels[1].image:pixels()do local c=it();if pc.rgbaA(c)>0 and not seen[c]then palette[#palette+1]=c;seen[c]=true end end
table.sort(palette);ref:close()
local original=app.open(root..'/away-static-v2/source-generated.png');local im=original.cels[1].image
local x0,y0,x1,y1=im.width,im.height,-1,-1
for it in im:pixels()do if pc.rgbaA(it())>=128 then x0=math.min(x0,it.x);y0=math.min(y0,it.y);x1=math.max(x1,it.x);y1=math.max(y1,it.y)end end
local h=104;local w=math.floor((x1-x0+1)*h/(y1-y0+1)+.5);assert(w<110)
local ox,oy=math.floor((128-w)/2),14;local art=Image(128,128,ColorMode.RGB)
local function closest(c)
 local rr,gg,bb=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c);local best,score=palette[1],math.huge
 for _,p in ipairs(palette)do local dr,dg,db=rr-pc.rgbaR(p),gg-pc.rgbaG(p),bb-pc.rgbaB(p);local d=dr*dr+dg*dg+db*db;if d<score then best=p;score=d end end
 return best
end
for y=0,h-1 do for x=0,w-1 do
 local sx=x0+math.min(x1-x0,math.floor((x+.5)*(x1-x0+1)/w));local sy=y0+math.min(y1-y0,math.floor((y+.5)*(y1-y0+1)/h));local c=im:getPixel(sx,sy)
 if pc.rgbaA(c)>=128 then art:drawPixel(ox+x,oy+y,closest(c))end
end end
original:close();local s=Sprite(128,128,ColorMode.RGB);s.cels[1].image=art;s.layers[1].name='A - rear three-quarter construction view';s:saveAs(root..'/away-static-v2/goblin-a-away-static.aseprite');s:saveCopyAs(out..'/goblin-a-away.png')
app.command.SpriteSize{width=768,height=768,method='nearest-neighbor'};s:saveCopyAs(out..'/goblin-a-away-6x.png')
for y=42,117 do if y%3==0 then local runs={};local start=nil
 for x=0,127 do if pc.rgbaA(art:getPixel(x,y))>0 then if not start then start=x end elseif start then runs[#runs+1]=start..'-'..(x-1);start=nil end end
 print(y..': '..table.concat(runs,', '))
end end
