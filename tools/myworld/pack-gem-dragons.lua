-- Aseprite batch packer. Approved native PNGs are input, never resampled.
local input=app.params['input'];local output=app.params['output']
assert(input and output,'Set --script-param input=<approved base>/frames --script-param output=<asset folder>')
local colors={{'sapphire',0,76,255},{'emerald',51,204,51},{'ruby',255,51,0},{'diamond',255,255,255},{'dragonstone',187,0,255}}
local widths={226,226,226,226,226,335};local pc=app.pixelColor
for _,gem in ipairs(colors)do
 local sheet=Image(1465,489,ColorMode.RGB);local x0=0
 for direction=0,5 do
  for pose=0,2 do
   local frame=Image{fromFile=input..'/'..string.format('%02d.png',direction*3+pose)}
   assert(frame.width==widths[direction+1]and frame.height==163,'Wrong native canvas')
   for pixel in frame:pixels()do
    local c=pixel();local r,g,b,a=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c),pc.rgbaA(c)
    -- Body is already fixed green/yellow. Only neutral mineral shades recolor.
    if a>0 and math.max(r,g,b)-math.min(r,g,b)<=16 and r>20 then
     local value=(r+g+b)/3/255;pixel(pc.rgba(math.floor(gem[2]*value+.5),math.floor(gem[3]*value+.5),math.floor(gem[4]*value+.5),a))
    end
   end
   sheet:drawImage(frame,Point(x0,pose*163))
  end
  x0=x0+widths[direction+1]
 end
 sheet:saveAs(output..'/'..gem[1]..'.png')
end
print('Packed five native 18-frame gem dragon sheets')
