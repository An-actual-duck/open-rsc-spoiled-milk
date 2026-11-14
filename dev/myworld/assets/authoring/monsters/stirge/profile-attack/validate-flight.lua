local base=app.params['base']
local a=app.open(base..'/inputs/approved-flight.aseprite')
local b=app.open(base..'/stirge-profile-attack.aseprite')
local checked=0
local function im(layer,f)local x=Image(150,130,ColorMode.RGB);local c=layer:cel(f);if c then x:drawImage(c.image,c.position)end;return x end
assert(#a.layers==#b.layers and #b.frames==18 and #b.tags==6)
for n,l in ipairs(a.layers)do local r=b.layers[n];assert(l.name==r.name and l.isVisible==r.isVisible)
 for f=1,15 do local x,y=im(l,f),im(r,f);for row=0,129 do for col=0,149 do assert(x:getPixel(col,row)==y:getPixel(col,row),'Flight layer changed')end end;checked=checked+1 end
end
print('PASS: '..checked..' approved flight layer/frame combinations unchanged; eighteen frames, nine layers, six tags retained.')
a:close();b:close()
