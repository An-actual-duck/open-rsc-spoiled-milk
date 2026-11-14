local root=app.params['root'];local path=root..'/dev/myworld/assets/authoring/monsters/stirge/plunge-attack/'
local atlas=Image(1260,480,ColorMode.RGB)
for n=0,17 do local im=Image{fromFile=path..string.format('frame-%02d.png',n)};local direction=math.floor(n/3);local phase=n%3;atlas:drawImage(im,Point(direction*210+(n<15 and 30 or 0),phase*160+(n<15 and 15 or 0)))end
atlas:saveAs(root..'/dev/myworld/assets/runtime/sprites/npcs/slayer-monsters/stirge.png')
