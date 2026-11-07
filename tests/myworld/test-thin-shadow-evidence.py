#!/usr/bin/env python3
"""Checks the offline native fence evidence validator with small synthetic data."""
import csv
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('fence_evidence',ROOT/'tools/renderer-benchmark/analyze-fence-export.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)


class EvidenceTests(unittest.TestCase):
    def fixture(self,path,pixels=(255,0,255,0)):
        (path/'fence-triangles.tsv').write_text('effectiveTexture\ty0\ty1\ty2\n12\t0\t-192\t-192\n')
        with (path/'fence-textures.tsv').open('w') as out:
            writer=csv.writer(out,delimiter='\t',lineterminator='\n');writer.writerow(('texture','width','height','signature','x','y','rgba'))
            for i,rgba in enumerate(pixels):writer.writerow((12,2,2,7,i%2,i//2,rgba))

    def test_source_alpha_runs_and_triangle_span(self):
        with tempfile.TemporaryDirectory() as name:
            path=Path(name);self.fixture(path)
            texture=evidence.analyze(path)['textures']['12']
            self.assertEqual(texture['opaqueFraction'],.5)
            self.assertEqual(texture['horizontalOpaqueRunHistogram'],{1:2})
            self.assertEqual(texture['verticalOpaqueRunHistogram'],{2:1})
            self.assertEqual(texture['triangleYSpans'],[192])

    def test_incomplete_pixel_grid_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path=Path(name);self.fixture(path,(255,0,255))
            with self.assertRaisesRegex(ValueError,'Incomplete'):evidence.analyze(path)

    def test_duplicate_pixel_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path=Path(name);self.fixture(path)
            with (path/'fence-textures.tsv').open('a') as out:out.write('12\t2\t2\t7\t0\t0\t255\n')
            with self.assertRaisesRegex(ValueError,'Duplicate'):evidence.analyze(path)

    def test_missing_referenced_texture_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path=Path(name);self.fixture(path)
            (path/'fence-triangles.tsv').write_text('effectiveTexture\ty0\ty1\ty2\n10\t0\t-192\t-192\n')
            with self.assertRaisesRegex(ValueError,'missing exported'):evidence.analyze(path)


if __name__=='__main__':unittest.main()
