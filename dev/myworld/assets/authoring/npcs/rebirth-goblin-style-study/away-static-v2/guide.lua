local out=assert(app.params.out);local s=Sprite(128,128,ColorMode.RGB);local im=s.cels[1].image;local pc=app.pixelColor
local function poly(p,c)
 for y=0,127 do for x=0,127 do local inside=false;local j=#p
 for i=1,#p do local a,b=p[i],p[j];if((a[2]>y)~=(b[2]>y))and x<(b[1]-a[1])*(y-a[2])/(b[2]-a[2])+a[1]then inside=not inside end;j=i end
 if inside then im:drawPixel(x,y,c)end end end
end
local function line(a,b,r,c)
 for y=0,127 do for x=0,127 do local vx,vy=b[1]-a[1],b[2]-a[2];local t=math.max(0,math.min(1,((x-a[1])*vx+(y-a[2])*vy)/(vx*vx+vy*vy)));if(x-a[1]-t*vx)^2+(y-a[2]-t*vy)^2<=r*r then im:drawPixel(x,y,c)end end end
end
local far=pc.rgba(89,106,122,255);local back=pc.rgba(167,178,186,255);local near=pc.rgba(216,222,224,255);local hip=pc.rgba(139,119,91,255);local ear=pc.rgba(225,175,95,255)
-- Geometry guide only: far limb blue-grey; near limb light grey.
line({54,49},{47,66},3,far);line({47,66},{45,85},3,far)
line({57,76},{53,95},4,far);line({53,95},{53,108},3,far);poly({{49,108},{54,104},{64,103},{62,109},{51,112}},far)
poly({{54,46},{72,54},{69,78},{57,73}},back);poly({{72,54},{78,50},{73,74},{69,78}},near)
poly({{57,72},{69,78},{73,74},{73,85},{61,87},{54,79}},hip)
line({68,82},{68,98},4,near);line({68,98},{70,113},3,near);poly({{66,113},{72,109},{83,108},{81,114},{69,118}},near)
line({73,55},{81,72},3,near);line({81,72},{82,91},3,near)
line({64,48},{65,39},4,back)
-- Separated skull side planes and ear roots, not two ears on back.
poly({{51,20},{63,14},{76,20},{78,35},{67,44},{54,37}},back)
poly({{67,25},{76,20},{78,35},{67,44}},near)
poly({{53,26},{42,17},{44,26},{54,32}},ear)
poly({{68,32},{88,29},{80,39},{68,38}},ear)
poly({{77,24},{84,25},{78,30}},near)
s.layers[1].name='Orientation proxy - not final art';s:saveAs(out..'/construction.aseprite');s:saveCopyAs(out..'/construction.png');app.command.SpriteSize{width=768,height=768,method='nearest-neighbor'};s:saveCopyAs(out..'/construction-6x.png')
