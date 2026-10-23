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
        self.assertEqual(len(entries), 502)
        # All 501 historical stored entry headers and payloads are byte-exact.
        self.assertEqual(hashlib.sha256(b"".join(h + p for h, p in entries[:-1])).hexdigest(),
                         "463ef68aa0496473ebbad6e8fac0bf9297ae6f4eee9840c84b6f9c8d24028ae9")
        header, data = entries[-1]
        self.assertEqual(int.from_bytes(header[:4], "big"), native.name_hash("sm_collapsed_wall.ob3"))
        self.assertEqual(struct.unpack(">HH", data[:4]), (130, 91))
        self.assertEqual(hashlib.sha256(data).hexdigest(), "8aeeef3577b9367a7bbcbbdac78045b4364d1b9a995081fb3a4da54c49bc9f03")
        definitions = list(ET.parse(ROOT / "server/conf/server/defs/GameObjectDef.xml").getroot())
        self.assertEqual(len(definitions), 1340)
        definition = definitions[1339]
        self.assertEqual(definition.findtext("objectModel"), "sm_collapsed_wall")
        self.assertEqual([definition.findtext(k) for k in ("type", "width", "height")], ["1", "2", "1"])


if __name__ == "__main__":
    unittest.main()
