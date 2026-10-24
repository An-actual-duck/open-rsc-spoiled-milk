#!/usr/bin/env python3
"""Bounded converter/archive tests; no Blender, networking, game login or DB."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("native", ROOT / "scripts/import-native-scenery.py")
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativeImportTest(unittest.TestCase):
    def test_colour_and_axes(self):
        self.assertEqual(native.colour((0, 0, 0)), -1)
        self.assertEqual(native.colour((1, 1, 1)), -32768)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "m.mtl").write_text("newmtl grey\nKd 0.1 0.1 0.1\n")
            obj = root / "m.obj"
            obj.write_text("mtllib m.mtl\nv 1 0 0\nv 0 1 0\nv 0 0 1\nusemtl grey\nf 1 2 3\n")
            data = native.convert(obj)
            self.assertEqual(struct.unpack(">HH9h", data[:22]), (3, 1, 128, 0, 0, 0, 0, -128, 0, 128, 0))
            self.assertEqual(data[-3:], bytes((0, 1, 2)))
            obj.write_text("v nan 0 0\n")
            with self.assertRaises(ValueError):
                native.convert(obj)

    def test_append_preserves_entries_and_refuses_collision(self):
        entry = struct.pack(">I", native.name_hash("old.ob3")) + native.u24(3) * 2
        body = b"\0\1" + entry + b"old"
        before = native.u24(len(body)) * 2 + body
        after = native.append_model(before, "new.ob3", b"new")
        self.assertEqual(native.archive_entries(after)[0], native.archive_entries(before)[0])
        with self.assertRaises(ValueError):
            native.append_model(after, "NEW.OB3", b"replacement")

    def test_installed_asset_is_additive_and_native(self):
        import hashlib
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 502)
        # All 501 historical stored entry headers and payloads are byte-exact.
        self.assertEqual(hashlib.sha256(b"".join(h + p for h, p in entries[:501])).hexdigest(),
                         "463ef68aa0496473ebbad6e8fac0bf9297ae6f4eee9840c84b6f9c8d24028ae9")
        header, data = entries[501]
        self.assertEqual(int.from_bytes(header[:4], "big"), native.name_hash("sm_collapsed_wall.ob3"))
        self.assertEqual(struct.unpack(">HH", data[:4]), (130, 91))
        self.assertEqual(hashlib.sha256(data).hexdigest(), "8aeeef3577b9367a7bbcbbdac78045b4364d1b9a995081fb3a4da54c49bc9f03")
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        self.assertGreaterEqual(len(definitions), 1340)
        definition = definitions[1339]
        self.assertEqual(definition.findtext("objectModel"), "sm_collapsed_wall")
        self.assertEqual([definition.findtext(k) for k in ("type", "width", "height")], ["1", "2", "1"])

    def test_ruined_furnishings_additive_native_contract(self):
        import hashlib
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 507)
        # Includes the previously approved collapsed wall, not just vanilla.
        self.assertEqual(hashlib.sha256(b"".join(h + p for h, p in entries[:502])).hexdigest(),
                         "fb29bcd37aaf2b61d8f5cdc70bc564b921ff533e7c2f911f61a1d2112f7d890e")
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        expected = (
            ("broken_table", 140, 100, 90, 1, 2, "fe75c68fca1fccb61aa0a1d88e5f1ef77570d0037082b79959c45add8b4abaf2"),
            ("destroyed_barrel", 190, 135, 89, 1, 1, "99614bd48f28e1743ef348022715cdaf7c91cf5fa8cb32f30dafdd9418573f29"),
            ("fallen_pillar", 62, 41, 69, 1, 2, "6ab8f1eba991a0c32fe90c38e503f6bb640dbc17ddfa9a61a745915465b83595"),
            ("dilapidated_bookshelf", 202, 149, 183, 1, 2, "6a928e193e5a2ce09be3cef974cc44c5a0216507c212e2d51000337153f0d42a"),
            ("ruined_books", 170, 127, 43, 0, 1, "2e46927d3f72883816a279cb696d7965842d42c0fb7c516e32131015dd5400a1"),
        )
        for index, (key, vertices, faces, height, kind, width, digest) in enumerate(expected):
            with self.subTest(asset=key):
                header, data = entries[502 + index]
                self.assertEqual(int.from_bytes(header[:4], "big"), native.name_hash("sm_" + key + ".ob3"))
                self.assertEqual(hashlib.sha256(data).hexdigest(), digest)
                self.assertEqual(struct.unpack(">HH", data[:4]), (vertices, faces))
                y = struct.unpack(">" + "h" * vertices, data[4 + vertices*2:4 + vertices*4])
                self.assertEqual((min(y), max(y)), (-height, 0))
                offset = 4 + vertices*6
                sizes = data[offset:offset+faces]
                materials = struct.unpack(">" + "h" * faces*2, data[offset+faces:offset+faces*5])
                self.assertTrue(all(c < 0 for c in materials), "No new texture dependencies")
                self.assertEqual(materials[:faces], materials[faces:])
                self.assertEqual(data[offset+faces*5:offset+faces*6], bytes(faces), "Authored flat-light flags")
                indices = data[offset+faces*6:]
                self.assertEqual(len(indices), sum(sizes))
                self.assertTrue(all(i < vertices for i in indices))
                definition = definitions[1340 + index]
                self.assertEqual(definition.findtext("objectModel"), "sm_" + key)
                self.assertEqual([definition.findtext(k) for k in ("type", "width", "height")],
                                 list(map(str, (kind, width, 1))))

    def test_barrel_loose_wood_is_separate_from_base_after_native_rounding(self):
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        data = entries[503][1]
        vertices = struct.unpack(">H", data[:2])[0]
        xs = struct.unpack(">" + "h" * vertices, data[4:4 + vertices*2])
        zs = struct.unpack(">" + "h" * vertices, data[4 + vertices*4:4 + vertices*6])

        def hull(start, end):
            points = sorted(set(zip(xs[start:end], zs[start:end])))
            def cross(a, b, c):
                return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
            lower, upper = [], []
            for p in points:
                while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0: lower.pop()
                lower.append(p)
            for p in reversed(points):
                while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0: upper.pop()
                upper.append(p)
            return lower[:-1] + upper[:-1]

        def strictly_separate(a, b):
            for polygon in (a, b):
                for p, q in zip(polygon, polygon[1:]+polygon[:1]):
                    axis = (q[1]-p[1], p[0]-q[0])
                    pa = [x*axis[0]+z*axis[1] for x, z in a]
                    pb = [x*axis[0]+z*axis[1] for x, z in b]
                    if max(pa) < min(pb) or max(pb) < min(pa): return True
            return False

        # Component ranges are pinned to the reviewed OBJ/native hash above.
        parts = [hull(70, 90), hull(170, 180), hull(180, 190)]
        for i, a in enumerate(parts):
            for b in parts[i+1:]:
                self.assertTrue(strictly_separate(a, b), "Base/loose wood must not overlap even after integer native rounding")

    def test_wall_props_preserve_hanging_height_wide_indices_and_prior_content(self):
        import hashlib
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 510)
        self.assertEqual(hashlib.sha256(b"".join(h+p for h,p in entries[:507])).hexdigest(),
                         "b8a1b3239a644407fad39ee5980899539fa396195a4de60db7e88f535ddddf4c")
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        expected = (
            ("rusted_shackles", 692, 518, -179, -60, "4b80f3f998cd9ca572c5507e4a0c2109af677137535492b85e1f517a1a46b787"),
            ("tattered_banner", 82, 63, -187, -45, "9996ad35ea7d10c754d707e9c342b8262f04ba9f700a09f303a692367668735f"),
            ("smashed_mirror", 88, 64, -194, -57, "09425d6db7dd2408967718a8b291777b6c94b98d17cbb7c3d860b785b5d58880"),
        )
        for i, (key, count, faces, low, high, digest) in enumerate(expected):
            with self.subTest(asset=key):
                header, data = entries[507+i]
                self.assertEqual(int.from_bytes(header[:4], "big"), native.name_hash("sm_"+key+".ob3"))
                self.assertEqual(hashlib.sha256(data).hexdigest(), digest)
                self.assertEqual(struct.unpack(">HH", data[:4]), (count, faces))
                axes = [struct.unpack(">"+"h"*count, data[4+axis*count*2:4+(axis+1)*count*2]) for axis in range(3)]
                self.assertEqual((min(axes[1]), max(axes[1])), (low, high), "Keep authored hanging height, never ground-normalize")
                self.assertGreaterEqual(min(axes[2])-(-64), 2, "Back stays in front of the wall")
                offset = 4+count*6
                sizes = data[offset:offset+faces]
                materials = struct.unpack(">"+"h"*faces*2, data[offset+faces:offset+faces*5])
                self.assertTrue(all(c<0 for c in materials))
                self.assertEqual(materials[:faces], materials[faces:])
                self.assertEqual(data[offset+faces*5:offset+faces*6], bytes(faces))
                payload = data[offset+faces*6:]
                width = 1 if count<256 else 2
                self.assertEqual(len(payload), sum(sizes)*width)
                indices = list(payload) if width==1 else list(struct.unpack(">"+"H"*sum(sizes), payload))
                self.assertTrue(all(j<count for j in indices))
                if i==0: self.assertEqual(max(indices), 691, "Shackles require full 16-bit native indices")
                cursor = 0
                for size in sizes:
                    a,b,c = indices[cursor:cursor+3]
                    u = [axes[d][b]-axes[d][a] for d in range(3)]
                    v = [axes[d][c]-axes[d][a] for d in range(3)]
                    normal = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
                    self.assertTrue(any(normal), "No collapsed face after native rounding")
                    cursor += size
                definition = definitions[1345+i]
                self.assertEqual(definition.findtext("objectModel"), "sm_"+key)
                self.assertEqual([definition.findtext(k) for k in ("type","width","height")], ["0","1","1"])


    def test_hanging_cage_is_additive_suspended_and_flat(self):
        import hashlib
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 511)
        self.assertEqual(hashlib.sha256(b"".join(h+p for h,p in entries[:510])).hexdigest(),
                         "e487ab178f3eb96740f402212852482af31fa2fde9fe816f302b1627cf24263e")
        header, data = entries[510]
        self.assertEqual(int.from_bytes(header[:4], "big"), native.name_hash("sm_hanging_cage.ob3"))
        self.assertEqual(hashlib.sha256(data).hexdigest(), "4424069c4295b4f3cd4b10932674741581a18185e9cb95e6ec1040dc71aa675a")
        count, faces = struct.unpack(">HH", data[:4])
        self.assertEqual((count, faces), (264, 198))
        axes = [struct.unpack(">"+"h"*count, data[4+i*count*2:4+(i+1)*count*2]) for i in range(3)]
        self.assertEqual([(min(a), max(a)) for a in axes], [(-61, 61), (-289, -70), (-56, 56)])
        offset = 4+count*6
        sizes = data[offset:offset+faces]
        mats = struct.unpack(">"+"h"*faces*2, data[offset+faces:offset+faces*5])
        self.assertTrue(all(c<0 for c in mats))
        self.assertEqual(mats[:faces], mats[faces:])
        self.assertEqual(data[offset+faces*5:offset+faces*6], bytes(faces))
        payload = data[offset+faces*6:]
        self.assertEqual(len(payload), sum(sizes)*2)
        indices = struct.unpack(">"+"H"*sum(sizes), payload)
        self.assertEqual(max(indices), 263)
        cursor = 0
        for size in sizes:
            face = indices[cursor:cursor+size]
            self.assertTrue(all(i<count for i in face))
            a,b,c = face[:3]
            u = [axes[d][b]-axes[d][a] for d in range(3)]
            v = [axes[d][c]-axes[d][a] for d in range(3)]
            n = (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
            self.assertTrue(any(n))
            cursor += size
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        definition = definitions[1348]
        self.assertEqual(definition.findtext("objectModel"), "sm_hanging_cage")
        self.assertEqual([definition.findtext(k) for k in ("name","type","width","height","groundItemVar","command1","command2")],
                         ["Hanging cage","0","1","1","0","WalkTo","Examine"])

    def test_pointed_window_preserves_prior_content_and_passive_binding(self):
        import hashlib
        import json
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 512)
        self.assertEqual(hashlib.sha256(b"".join(h+p for h,p in entries[:511])).hexdigest(),
                         "e22b5941ebd1ee4ab781851c5965d5a08f1391582fdc46ae23a20175fbc1f387")
        self.assertEqual(int.from_bytes(entries[511][0][:4], "big"),
                         native.name_hash("sm_pointed_window.ob3"))
        data = entries[511][1]
        self.assertEqual(hashlib.sha256(data).hexdigest(),
                         "bc393caddb63366f019ef341ccea7bfdc8e03d92b4299fe3c61561108d985006")
        count, faces = struct.unpack(">HH", data[:4])
        self.assertEqual((count, faces), (216, 189), "Coarse closed relief, not hundreds of tiny pixel quads")
        axes = [struct.unpack(">"+"h"*count, data[4+i*count*2:4+(i+1)*count*2]) for i in range(3)]
        self.assertEqual([(min(a), max(a)) for a in axes], [(-34,34),(-189,-34),(-60,-51)])
        offset = 4+count*6
        sizes = data[offset:offset+faces]
        mats = struct.unpack(">"+"h"*faces*2, data[offset+faces:offset+faces*5])
        self.assertTrue(all(c<0 for c in mats), "Opaque RGB555, no texture allocation")
        self.assertEqual(mats[:faces], mats[faces:])
        self.assertEqual(data[offset+faces*5:offset+faces*6], bytes(faces))
        indices = list(data[offset+faces*6:])
        self.assertEqual(len(indices), sum(sizes))
        self.assertEqual(max(indices), count-1)
        cursor = 0
        for size in sizes:
            face = indices[cursor:cursor+size]
            self.assertTrue(all(i<count for i in face))
            a,b,c = face[:3]
            u = [axes[d][b]-axes[d][a] for d in range(3)]
            v = [axes[d][c]-axes[d][a] for d in range(3)]
            self.assertTrue(any((u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])),
                            "No collapsed native polygon")
            cursor += size
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        self.assertGreaterEqual(len(definitions), 1350)
        historical = [[(e.tag, e.text) for e in d] for d in definitions[:1349]]
        self.assertEqual(hashlib.sha256(json.dumps(historical, separators=(",", ":")).encode()).hexdigest(),
                         "9085576e195b169a6a1c1cda5f74ab3fb03958827d4ebedd00b8ff131e8b6767")
        definition = definitions[1349]
        self.assertEqual([definition.findtext(k) for k in
                          ("name", "objectModel", "type", "width", "height", "groundItemVar", "command1", "command2")],
                         ["Pointed window", "sm_pointed_window", "0", "1", "1", "0", "WalkTo", "Examine"])

    def test_window_family_preserves_approved_pointed_and_passive_bindings(self):
        import hashlib
        import json
        entries = native.archive_entries((ROOT / "Client_Base/Cache/video/models.orsc").read_bytes())
        self.assertGreaterEqual(len(entries), 515)
        # Stained-glass revisions may replace only entry513; all accepted models stay exact.
        self.assertEqual(hashlib.sha256(b"".join(h+p for i,(h,p) in enumerate(entries[:515]) if i!=513)).hexdigest(),
                         "dcd18b437b850ddfe936fd8ec34d281abe849c606f9a052eb0a8a410b1cc95b1")
        self.assertEqual(hashlib.sha256(b"".join(h+p for h,p in entries[:512])).hexdigest(),
                         "2d1534597440605ae9c5ab9fdefd19b9b373ef50c7813291ae78f2fe55f89a62")
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        historical = [[(e.tag, e.text) for e in d] for d in definitions[:1350]]
        self.assertEqual(hashlib.sha256(json.dumps(historical, separators=(",", ":")).encode()).hexdigest(),
                         "90f69d8c56bf53dfd1b630926bd9e2e38914f28e5419676c7e93e649f0ce8ce6")
        expected = (
            ("Stone window", "sm_stone_window", 236, 190, ((-40,40),(-177,-40),(-56,-45)),
             "f045b0e8c7ef6c2d1279a6623adfa882b470ea68dbb536cae765859ea8056779"),
            ("Stained glass window", "sm_stained_glass_window", 356, 272, ((-34,34),(-190,-33),(-60,-51)),
             "3b5ae950db76393c2a3288c9f51205051f80c1cf0cf75c54682088d1a54350b6"),
            ("Stone arrowslit", "sm_stone_arrowslit", 162, 133, ((-24,24),(-190,-33),(-60,-51)),
             "550dff4184ed6d770dc58db9b78819663cf7ad3e4ead62fa00792838d6870281"),
        )
        for i,(name,model,count,faces,bounds,digest) in enumerate(expected):
            with self.subTest(model=model):
                self.assertEqual(int.from_bytes(entries[512+i][0][:4], "big"), native.name_hash(model+".ob3"))
                data = entries[512+i][1]
                self.assertEqual(hashlib.sha256(data).hexdigest(), digest)
                self.assertEqual(struct.unpack(">HH",data[:4]), (count,faces))
                axes = [struct.unpack(">"+"h"*count,data[4+d*count*2:4+(d+1)*count*2]) for d in range(3)]
                self.assertEqual(tuple((min(a),max(a)) for a in axes), bounds)
                if model == "sm_stained_glass_window":
                    # Nine unchanged 12-vertex stones, then recessed backing;
                    # all coloured cells, including the sword, share one glass plane.
                    self.assertEqual(set(axes[2][108:126]), {-59,-58})
                    self.assertEqual(set(axes[2][126:]), {-57,-55})
                offset = 4+count*6
                sizes = data[offset:offset+faces]
                materials = struct.unpack(">"+"h"*faces*2,data[offset+faces:offset+faces*5])
                self.assertTrue(all(c<0 for c in materials), "No texture slot allocation")
                self.assertEqual(materials[:faces], materials[faces:])
                self.assertEqual(data[offset+faces*5:offset+faces*6], bytes(faces))
                payload = data[offset+faces*6:]
                width = 1 if count<256 else 2
                self.assertEqual(len(payload),sum(sizes)*width)
                indices = list(payload) if width==1 else list(struct.unpack(">"+"H"*sum(sizes),payload))
                self.assertEqual(max(indices),count-1)
                cursor=0
                for size in sizes:
                    polygon=indices[cursor:cursor+size]
                    self.assertTrue(all(0<=j<count for j in polygon))
                    a,b,c=polygon[:3]
                    u=[axes[d][b]-axes[d][a] for d in range(3)]
                    v=[axes[d][c]-axes[d][a] for d in range(3)]
                    self.assertTrue(any((u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])),
                                    "No degenerate native face")
                    cursor+=size
                definition = definitions[1350+i]
                self.assertEqual([definition.findtext(k) for k in
                                  ("name","objectModel","type","width","height","groundItemVar","command1","command2")],
                                 [name,model,"0","1","1","0","WalkTo","Examine"])

if __name__ == "__main__":
    unittest.main()
