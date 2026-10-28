-- Aseprite batch packer. Approved native PNGs are input, never resampled.
local input=app.params['input'];local output=app.params['output']
assert(input and output,'Set --script-param input=<approved base>/frames --script-param output=<asset folder>')
local widths={226,226,226,226,226,335};local pc=app.pixelColor
local sheet=Image(1465,489,ColorMode.RGB);local x0=0
for direction=0,5 do
 for pose=0,2 do
  local frame=Image{fromFile=input..'/'..string.format('%02d.png',direction*3+pose)}
  assert(frame.width==widths[direction+1]and frame.height==163,'Wrong native canvas')
  for pixel in frame:pixels()do
   local c=pixel();local r,g,b,a=pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c),pc.rgbaA(c)
   -- The native mask recognises exact gray. Normalize only the already-approved
   -- near-neutral mineral shades, leaving fixed green/yellow body and alpha alone.
   if a>0 and math.max(r,g,b)-math.min(r,g,b)<=16 and r>20 then
    local v=math.floor((r+g+b)/3+.5);pixel(pc.rgba(v,v,v,a))
   end
  end
  sheet:drawImage(frame,Point(x0,pose*163))
 end
 x0=x0+widths[direction+1]
end
sheet:saveAs(output..'/gemdragon.png')
print('Packed one shared neutral 18-frame gem dragon sheet')
