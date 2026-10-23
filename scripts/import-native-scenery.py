#!/usr/bin/env python3
"""Convert a reviewed, flat-colour Z-up OBJ to native OB3 and append to models.orsc.

Explicit offline asset operation, not a runtime model generator. Only vertices,
convex polygon faces, objects, flat shading and linear MTL Kd are accepted.
Existing archive entries (including their stored compression) remain byte exact.
"""
import argparse
import bz2
import hashlib
import math
from pathlib import Path
import struct


def name_hash(name):
    result = 0
    for char in name.upper():
        result = (result * 61 + ord(char) - 32) & 0xffffffff
    return result


def u24(value):
    return value.to_bytes(3, "big")


def archive_entries(data):
    unpacked, packed = int.from_bytes(data[:3], "big"), int.from_bytes(data[3:6], "big")
    if len(data) != packed + 6:
        raise ValueError("Malformed archive envelope")
    body = data[6:]
    if unpacked != packed:
        body = bz2.decompress(b"BZh1" + body)
    if len(body) != unpacked:
        raise ValueError("Archive size mismatch")
    count = int.from_bytes(body[:2], "big")
    pos = 2 + count * 10
    entries = []
    for i in range(count):
        header = body[2 + i * 10:12 + i * 10]
        length = int.from_bytes(header[7:10], "big")
        payload = body[pos:pos + length]
        if len(payload) != length:
            raise ValueError("Truncated archive entry")
        entries.append((header, payload))
        pos += length
    if pos != len(body):
        raise ValueError("Trailing archive bytes")
    return entries


def append_model(data, name, model):
    entries = archive_entries(data)
    key = name_hash(name)
    if any(int.from_bytes(h[:4], "big") == key for h, _ in entries):
        raise ValueError("Model name/hash already exists; refusing replacement")
    header = struct.pack(">I", key) + u24(len(model)) * 2
    entries.append((header, model))
    body = struct.pack(">H", len(entries)) + b"".join(h for h, _ in entries) + b"".join(p for _, p in entries)
    return u24(len(body)) * 2 + body


def linear_to_srgb(value):
    if not math.isfinite(value) or not 0 <= value <= 1:
        raise ValueError("Invalid linear material colour")
    value = value * 12.92 if value <= .0031308 else 1.055 * value ** (1 / 2.4) - .055
    return min(255, max(0, round(value * 255)))


def colour(kd):
    r, g, b = (linear_to_srgb(v) >> 3 for v in kd)
    return -1 - ((r << 10) | (g << 5) | b)


def convert(obj):
    materials = {}
    current = None
    vertices, faces, colours = [], [], []
    for raw in obj.read_text().splitlines():
        tokens = raw.split()
        if not tokens or tokens[0].startswith("#"):
            continue
        tag, values = tokens[0], tokens[1:]
        if tag == "mtllib":
            if len(values) != 1 or Path(values[0]).name != values[0]:
                raise ValueError("MTL must be beside the OBJ")
            material = None
            for line in obj.with_name(values[0]).read_text().splitlines():
                words = line.split()
                if words and words[0] == "newmtl":
                    material = words[1]
                elif words and words[0] == "Kd":
                    materials[material] = colour(tuple(map(float, words[1:])))
        elif tag == "v":
            if len(values) != 3:
                raise ValueError("Expected three coordinates")
            x, y, z = map(float, values)
            if not all(math.isfinite(v) for v in (x, y, z)):
                raise ValueError("Non-finite vertex")
            # 128 native units/tile; native up is negative Y. This transform
            # has positive determinant, so outward OBJ winding is retained.
            vertices.append(tuple(round(v * 128) for v in (x, -z, y)))
        elif tag == "usemtl":
            current = values[0]
        elif tag == "f":
            indices = [int(v) - 1 for v in values]
            if not 3 <= len(indices) <= 255 or any(v < 0 or v >= len(vertices) for v in indices):
                raise ValueError("Invalid polygon")
            if current not in materials:
                raise ValueError("Face has no flat colour")
            faces.append(indices)
            colours.append(materials[current])
        elif tag == "s" and values == ["off"]:
            pass
        elif tag != "o":
            raise ValueError("Unsupported OBJ instruction: " + tag)
    if not vertices or not faces or len(vertices) > 65535 or len(faces) > 65535:
        raise ValueError("Empty or oversized mesh")
    if any(abs(v) > 32767 for vertex in vertices for v in vertex):
        raise ValueError("Mesh exceeds signed-short native bounds")
    data = struct.pack(">HH", len(vertices), len(faces))
    for axis in range(3):
        data += struct.pack(">" + "h" * len(vertices), *(v[axis] for v in vertices))
    data += bytes(map(len, faces))
    # Both sides have identical materials: no mirrored missing-face surprises.
    data += struct.pack(">" + "h" * len(faces), *colours) * 2
    data += bytes(len(faces))  # flat rather than Gouraud light
    for face in faces:
        data += bytes(face) if len(vertices) < 256 else struct.pack(">" + "H" * len(face), *face)
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("obj", type=Path)
    parser.add_argument("archive", type=Path, help="input models.orsc, never modified")
    parser.add_argument("output", type=Path, help="fresh output archive")
    parser.add_argument("--name", required=True, help="unique native model basename, no .ob3")
    args = parser.parse_args()
    if not args.name.replace("_", "").isalnum():
        parser.error("Use an alphanumeric model name")
    model = convert(args.obj)
    updated = append_model(args.archive.read_bytes(), args.name + ".ob3", model)
    with args.output.open("xb") as stream:
        stream.write(updated)
    print("Native model SHA256:", hashlib.sha256(model).hexdigest())
    print("Output archive SHA256:", hashlib.sha256(updated).hexdigest())


if __name__ == "__main__":
    main()
