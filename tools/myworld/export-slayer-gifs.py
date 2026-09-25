#!/usr/bin/env python3
"""Read-only production sprite export. Requires Pillow; never rewrites source assets."""
import argparse
import hashlib
import html
import json
import re
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "dev/myworld/assets/sprites/npcs/slayer-movement-preview"
ENUM = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/SlayerMovementPreview.java"
MELEE = [0, 1, 2, 1, 0, 0, 0, 0]
NAMES = {
    "giant-frog": [("spit", 5, [0, 1, 2], [200]*3)],
    "cockatrice": [("melee", 5, MELEE, [120]*8)],
    "banshee": [("melee", 2, MELEE, [120]*8), ("magic-shared-pose", 2, [0, 1, 2], [200]*3)],
    "naga": [("cleave", 5, [0, 1, 2], [200]*3), ("sword-throw", 6, [0, 1, 2], [200]*3)],
    "terror-dog": [("bite", 5, MELEE, [120]*8)],
    "bloodveld": [("chomp", 5, [0, 1, 2], [200]*3), ("tongue-pull", 6, [0, 1, 2], [200]*3)],
    "dark-beast": [("melee", 5, MELEE, [120]*8), ("lightning-charge", 6, [0, 1]*5+[2], [640]*11)],
    "abyssal-demon": [("stab", 5, [0, 1, 2], [80, 160, 400]), ("sink-spikes-rise", 6, [0, 1, 2], [80, 1200, 1280])],
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "output/slayer-npc-gifs")
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    (out / "native").mkdir(exist_ok=True)
    (out / "3x").mkdir(exist_ok=True)
    definitions = re.findall(r'\w+\((\d+), "([^"]+)", "([^"]+)", ([\d, ]+)\)', ENUM.read_text())
    assert len(definitions) == 8
    manifest, cards, contact = [], [], []
    for npc_id, title, slug, raw_widths in definitions:
        widths = [int(n) for n in raw_widths.split(",")]
        source_path = SOURCE / (slug + ".png")
        original = source_path.read_bytes()
        sheet = Image.open(source_path).convert("RGBA")
        assert sheet.width == sum(widths) and sheet.height % 3 == 0
        height = sheet.height // 3
        canvas_size = (max(widths) + 16, height + 16)
        sequences = [("front-walk", 0, [0, 1, 2] if slug == "giant-frog" else [0, 1, 0, 2], [200]*(3 if slug == "giant-frog" else 4))] + NAMES[slug]
        cards.append(f"<section><h2>{html.escape(title)}</h2><div class='row'>")
        for label, column, sequence, durations in sequences:
            x = sum(widths[:column])
            native_frames, occupied = [], []
            for row in range(3):
                frame = sheet.crop((x, row*height, x+widths[column], (row+1)*height))
                # Match ClientExternalAssetLoader's alpha>=64 normalization.
                frame.putalpha(frame.getchannel("A").point(lambda a: 255 if a >= 64 else 0))
                bbox = frame.getbbox()
                assert bbox is not None, (slug, label, row, "empty production frame")
                occupied.append(bbox)
                canvas = Image.new("RGB", canvas_size, (18, 20, 23))
                offset = ((canvas.width-frame.width)//2, 8)
                canvas.paste(frame, offset, frame)
                # All existing source pixels fit; do not trim/recenter each frame.
                assert bbox[0]+offset[0] >= 8 and bbox[2]+offset[0] <= canvas.width-8
                assert bbox[1]+8 >= 8 and bbox[3]+8 <= canvas.height-8
                native_frames.append(canvas)
            atlas = Image.new("RGB", (canvas_size[0]*3, canvas_size[1]))
            for row, frame in enumerate(native_frames): atlas.paste(frame, (row*canvas_size[0], 0))
            palette = atlas.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
            indexed = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in native_frames]
            filename = slug + "--" + label + ".gif"
            for folder, scale in [("native", 1), ("3x", 3)]:
                frames = [indexed[row].resize((canvas_size[0]*scale, canvas_size[1]*scale), Image.Resampling.NEAREST) for row in sequence]
                path = out / folder / filename
                frames[0].save(path, save_all=True, append_images=frames[1:], duration=durations,
                               loop=0, disposal=2, optimize=False)
                gif = Image.open(path)
                actual_duration = 0
                for frame_index in range(gif.n_frames):
                    gif.seek(frame_index)
                    logical_time = 0
                    logical_index = 0
                    for candidate_index, duration in enumerate(durations):
                        if logical_time <= actual_duration < logical_time + duration:
                            logical_index = candidate_index
                            break
                        logical_time += duration
                    assert gif.convert("RGB").tobytes() == frames[logical_index].convert("RGB").tobytes(), (filename, frame_index, "GIF disposal/palette mismatch")
                    actual_duration += gif.info["duration"]
                assert actual_duration == sum(durations), (filename, actual_duration)
                assert gif.size == frames[0].size
            record = dict(npc=title, npc_id=int(npc_id), animation=label, source=str(source_path.relative_to(ROOT)),
                          source_sha256=hashlib.sha256(original).hexdigest(), column=column, rows=sequence,
                          durations_ms=durations, total_ms=sum(durations), canvas=list(canvas_size),
                          encoded_gif_frames=gif.n_frames,
                          source_bounds=occupied, gif_native="native/"+filename, gif_3x="3x/"+filename)
            manifest.append(record)
            cards.append(f"<figure><img src='3x/{filename}' alt='{html.escape(title+' '+label)}'><figcaption>{html.escape(label.replace('-', ' '))}<br><small>{sum(durations)/1000:g}s · <a href='native/{filename}'>native GIF</a> · <a href='3x/{filename}'>3× GIF</a></small></figcaption></figure>")
            strip = atlas.resize((atlas.width*2, atlas.height*2), Image.Resampling.NEAREST)
            contact.append((title+" — "+label, strip))
        cards.append("</div></section>")
        assert source_path.read_bytes() == original, "source changed during export"
    intro = """<!doctype html><meta charset='utf-8'><title>Approved Slayer NPC animations</title>
<style>body{background:#121417;color:#ddd;font:16px system-ui;margin:24px}a{color:#8bd}h2{border-bottom:1px solid #454545}.row{display:flex;flex-wrap:wrap;align-items:end;gap:18px}figure{margin:8px;text-align:center}img{image-rendering:pixelated;max-width:100%;height:auto}small{color:#aaa}p{max-width:1000px;line-height:1.5}</style>
<h1>Approved Slayer NPC animations</h1><p>8 monsters · 8 front walks · 13 attack previews. Exported from the current production sheets, without new art. Banshee melee and magic share side-view art. All images loop; right-facing attack art is shown.</p>
<p>Walk review cadence: 200 ms per pose (walking speed in-game depends on movement). Bloodveld chomp and Naga cleave use clean 0,1,2 review loops at 200 ms each; other ordinary melee uses client Combat A's 0,1,2,1,0,0,0,0 pattern at the nominal 20 ms update rate. Projectile poses are 200 ms each. Dark Beast pulses for ten 640 ms ticks, then discharges for one tick. Abyssal stab: 80/160/400 ms; spikes: 80/1200/1280 ms. These are sprite previews, not recordings of damage or complete encounter cooldowns. The chomp/cleave preview timing does not change gameplay.</p>
<p>Original cell padding and common foot height are retained. Wider attacks use their original canvas widths, centered like the client; no per-frame auto-fit. GIF requires a 256-color palette per animation, without dithering. Source PNGs are untouched. <a href='slayer-npc-gifs.zip'>Download all GIFs and gallery</a> · <a href='manifest.json'>Source/timing manifest</a></p>"""
    (out/"index.html").write_text(intro+"\n".join(cards))
    (out/"manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    width = max(strip.width for _, strip in contact)
    height = sum(strip.height+28 for _, strip in contact)
    review = Image.new("RGB", (width, height), (18,20,23))
    draw = ImageDraw.Draw(review)
    y = 0
    for label, strip in contact:
        draw.text((8,y+5),label,fill="white")
        review.paste(strip,(0,y+28)); y += strip.height+28
    review.save(out/"contact.png")
    with zipfile.ZipFile(out/"slayer-npc-gifs.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(out.rglob("*")):
            if path.is_file() and path.suffix != ".zip": archive.write(path, path.relative_to(out))
    print(f"Exported {len(manifest)} animations (native and 3x): {out/'index.html'}")

if __name__ == "__main__": main()
