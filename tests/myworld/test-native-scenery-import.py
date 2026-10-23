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
            ("destroyed_barrel", 190, 135, 89, 1, 1, "1e62129e68cc034cb5492da66dbbe274c6432f536792493d8a715f8c31f1e9fb"),
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


if __name__ == "__main__":
    unittest.main()
