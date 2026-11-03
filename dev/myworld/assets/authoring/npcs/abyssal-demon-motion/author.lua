-- Bounded Aseprite edit of existing approved artwork. No generated replacement art.
-- Direction numbers are the owner's one-based browser labels.
local root = app.params.root or '.'
local out = assert(app.params.out, 'Pass an output directory')
local source = root .. '/dev/myworld/assets/authoring/npcs/abyssal-demon-motion/reference/approved-before.png'
local sprite = app.open(source)
assert(sprite.width == 756 and sprite.height == 336, 'Unexpected approved sprite layout')
app.command.ChangePixelFormat { format = 'rgb' }
local original = Image(sprite.width, sprite.height, ColorMode.RGB)
original:drawImage(sprite.cels[1].image, sprite.cels[1].position)
local result = Image(original)
local cellW, cellH = 100, 112
local angle = math.rad(2.3)
local function weight(y)
  if y <= 46 then return 1 end
  if y >= 72 then return 0 end
  local t = (y - 46) / 26
  return 1 - t * t * (3 - 2 * t)
end
local function round(x) return math.floor(x + 0.5) end
for _, column in ipairs({2, 3}) do
  for row = 1, 2 do
    local direction = row == 1 and 1 or -1
    local ox, oy = column * cellW, row * cellH
    for y = 0, cellH - 1 do
      local theta = angle * direction * weight(y)
      local co, si = math.cos(theta), math.sin(theta)
      for x = 0, cellW - 1 do
        -- Inverse nearest-neighbour rotation around the lower torso. The
        -- smooth weight fixes the lower body and feet at their original pixels.
        local sx = round(48 + co * (x - 48) + si * (y - 66))
        local sy = round(66 - si * (x - 48) + co * (y - 66))
        local color = 0
        if sx >= 0 and sx < cellW and sy >= 0 and sy < cellH then
          color = original:getPixel(ox + sx, oy + sy)
        end
        result:putPixel(ox + x, oy + y, color)
      end
    end
  end
end
sprite.layers[1].name = 'Original approved production sheet — preserved'
sprite.layers[1].isVisible = false
local revised = sprite:newLayer()
revised.name = 'Side + diagonal rear upper-body rocking — v1'
sprite:newCel(revised, 1, result, Point(0, 0))
sprite:saveAs(out .. '/abyssal-demon-motion-v1.aseprite')
sprite:saveCopyAs(out .. '/abyssal-demon-motion-v1.png')
-- Export the compact source expected by the established lossless packer.
-- Padding and the separate spike strip are restored by that packer.
local base = Sprite(612, 300, ColorMode.RGB)
local baseImage = base.cels[1].image
for row = 0, 2 do
  for y = 0, 99 do
    for x = 0, 611 do
      baseImage:putPixel(x, row * 100 + y, result:getPixel(x, row * 112 + 6 + y))
    end
  end
end
base:saveCopyAs(out .. '/approved-base.png')
base:close()
-- Before/after comparison, with each original frame's common ground anchor.
local board = Sprite(400, 336, ColorMode.RGB)
local image = board.cels[1].image
for row = 0, 2 do
  for column = 0, 3 do
    local sourceColumn = column < 2 and 2 or 3
    local input = column % 2 == 0 and original or result
    for y = 0, cellH - 1 do
      for x = 0, cellW - 1 do
        image:putPixel(column * cellW + x, row * cellH + y,
          input:getPixel(sourceColumn * cellW + x, row * cellH + y))
      end
    end
  end
end
board:saveCopyAs(out .. '/comparison-native.png')
app.command.SpriteSize { width = 1200, height = 1008, method = 'nearest-neighbor' }
board:saveCopyAs(out .. '/comparison-3x.png')
board:close()
sprite:close()
