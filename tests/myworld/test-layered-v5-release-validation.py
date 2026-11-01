#!/usr/bin/env python3
"""Offline package-validator/server parity and normal installed-client v5 bootstrap.

Run under the maintainer heavy-build lock. Fixtures own every mutated byte;
this never opens accounts, starts a server or changes the selected package.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'tools/layered-maps'
PAYLOAD = 'placements/deep-l2-entities.json'
ENCODING = 'layered-world-placements-v5'


class LayeredV5ReleaseValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='layered-v5-release-')
        cls.base = Path(cls.temp.name)
        cls.classes = cls.base / 'classes'
        cls.classes.mkdir()
        subprocess.run(['javac', '-source', '8', '-target', '8', '-d', str(cls.classes),
                        *map(str, sorted((TOOL / 'src').rglob('*.java')))], check=True)
        (cls.base / 'ServerPackageCheck.java').write_text('''
import com.openrsc.server.io.NativeLayeredWorldPackage;
import java.nio.file.Paths;
public final class ServerPackageCheck {
 public static void main(String[] args) throws Exception {
  NativeLayeredWorldPackage.load(Paths.get(args[0]));
  System.out.println("PASS native package decode");
 }
}
''')
        (cls.base / 'InstalledClientCheck.java').write_text('''
import orsc.WorldBuilderInstalledClientProfile;
import orsc.WorldBuilderTerrainBootstrap;
public final class InstalledClientCheck {
 public static void main(String[] args) {
  System.setProperty("openrsc.worldBuilderMode", "false");
  System.setProperty(WorldBuilderInstalledClientProfile.PROFILE_PROPERTY,args[0]);
  if(!WorldBuilderInstalledClientProfile.current().isEnabled()
    || !WorldBuilderTerrainBootstrap.isNativeOnly()
    || !args[1].equals(WorldBuilderTerrainBootstrap.mapIdentity())
    || !WorldBuilderTerrainBootstrap.report().contains("installed-client"))
   throw new AssertionError("normal installed native client bootstrap");
  System.out.println("PASS installed-client native map authority");
 }
}
''')
        cls.server_jar = ROOT / 'server/core.jar'
        cls.client_jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
        subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(cls.server_jar),
                        '-d', str(cls.classes), str(cls.base / 'ServerPackageCheck.java')], check=True)
        subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(cls.client_jar),
                        '-d', str(cls.classes), str(cls.base / 'InstalledClientCheck.java')], check=True)
        spec = importlib.util.spec_from_file_location('installed_selection', ROOT / 'scripts/installed-map-selection.py')
        cls.selection = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.selection)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=self.base, prefix='case-')
        self.addCleanup(self.temp.cleanup)
        self.case = Path(self.temp.name)
        self.package = self.case / 'package'
        shutil.copytree(TOOL / 'fixtures/native-package-v1', self.package)
        self.payload = json.loads((self.package / PAYLOAD).read_text())
        self.payload.update(schemaVersion=5, encoding=ENCODING)
        for npc in self.payload['npcs']:
            radius = npc.pop('roamRadius')
            npc['roamBounds'] = {'minimum': {axis: npc['start'][axis] - radius for axis in ('x', 'y')},
                                 'maximum': {axis: npc['start'][axis] + radius for axis in ('x', 'y')}}
            npc['respawnSeconds'] = -1
        self.shortcut = {'schemaVersion': 1, 'template': 'jump-v1', 'action': 'agility-jump',
                         'requiredLevel': 10, 'startChancePercent': 20, 'maxChancePercent': 80,
                         'maxChanceLevel': 40,
                         'successDestination': {'worldSpace': 'global', 'level': -2, 'x': 450, 'y': 600},
                         'failureDestination': {'worldSpace': 'global', 'level': -2, 'x': 451, 'y': 600}}
        self.payload['scenery'][0]['agilityShortcut'] = self.shortcut

    def write_payload(self, payload, encoding=ENCODING):
        data = (json.dumps(payload) + '\n').encode()
        (self.package / PAYLOAD).write_bytes(data)
        path = self.package / 'manifest.json'
        manifest = json.loads(path.read_text())
        entry = next(row for row in manifest['placementSets'] if row['path'] == PAYLOAD)
        entry.update(encoding=encoding, sha256=hashlib.sha256(data).hexdigest())
        path.write_text(json.dumps(manifest) + '\n')

    def check_parsers(self, accepted):
        before = self.selection.inventory(self.package)
        commands = [
            ['java', '-Xmx256m', '-cp', str(self.classes), 'com.openrsc.layeredmaps.LayeredMapsCli',
             'package-check', '--root', str(ROOT), '--workspace', str(self.case / 'report'),
             '--package', str(self.package)],
            ['java', '-Xmx256m', '-cp', str(self.classes) + os.pathsep + str(self.server_jar),
             'ServerPackageCheck', str(self.package)],
        ]
        for command in commands:
            result = subprocess.run(command, text=True, capture_output=True, timeout=30)
            self.assertEqual(accepted, result.returncode == 0,
                             ' '.join(command) + '\n' + result.stdout + result.stderr)
        self.assertEqual(before, self.selection.inventory(self.package), 'validators changed package bytes')

    def test_legacy_v1_through_v4_remain_readable(self):
        original = json.loads((TOOL / 'fixtures/native-package-v1' / PAYLOAD).read_text())
        for version in (1, 2, 3, 4):
            with self.subTest(version=version):
                payload = copy.deepcopy(self.payload if version >= 3 else original)
                if version >= 3:
                    del payload['scenery'][0]['agilityShortcut']
                if version == 3:
                    for npc in payload['npcs']:
                        del npc['respawnSeconds']
                if version == 1:
                    del payload['scenery']
                    del payload['boundaries']
                encoding = ('layered-entity-placements-v1' if version == 1
                            else 'layered-world-placements-v' + str(version))
                payload.update(schemaVersion=version, encoding=encoding)
                self.write_payload(payload, encoding)
                self.check_parsers(True)

    def test_all_five_templates_match_server(self):
        for template in ('jump', 'crawl', 'climb', 'swing', 'balance'):
            with self.subTest(template=template):
                self.shortcut.update(template=template + '-v1', action='agility-' + template)
                self.write_payload(self.payload)
                self.check_parsers(True)

    def test_v5_without_shortcuts_and_empty_sets(self):
        del self.payload['scenery'][0]['agilityShortcut']
        self.write_payload(self.payload)
        self.check_parsers(True)
        for family in ('npcs', 'groundItems', 'scenery', 'boundaries'):
            self.payload[family] = []
        self.write_payload(self.payload)
        self.check_parsers(True)

    def test_supported_chance_and_signed_level_boundaries(self):
        self.shortcut.update(requiredLevel=1, maxChanceLevel=99, startChancePercent=0, maxChancePercent=100)
        self.shortcut['successDestination'].update(x=1, y=32766, level=-(2**31))
        self.shortcut['failureDestination'].update(x=32766, y=1, level=2**31-1)
        self.write_payload(self.payload)
        self.check_parsers(True)
        self.shortcut.update(requiredLevel=99, maxChanceLevel=99, startChancePercent=42, maxChancePercent=42)
        self.payload['npcs'][0]['respawnSeconds'] = 86400
        self.write_payload(self.payload)
        self.check_parsers(True)

    def test_malformed_shortcut_contracts_rejected_by_both(self):
        mutations = [
            ('schemaVersion', 2), ('template', 'crawl-v1'), ('template', 'unknown-v1'),
            ('action', 'agility-fly'), ('requiredLevel', 0), ('requiredLevel', 100),
            ('maxChanceLevel', 9), ('maxChanceLevel', 100), ('maxChanceLevel', 10),
            ('startChancePercent', -1), ('startChancePercent', 81), ('maxChancePercent', 101),
            ('requiredLevel', True), ('requiredLevel', 1.5), ('requiredLevel', 2**31),
        ]
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                payload = copy.deepcopy(self.payload)
                payload['scenery'][0]['agilityShortcut'][field] = value
                self.write_payload(payload)
                self.check_parsers(False)
        for field in ('successDestination', 'failureDestination'):
            for key, value in (('worldSpace', 'instance'), ('x', 0), ('y', 32767), ('level', 2**31),
                               ('level', False), ('extra', 1)):
                with self.subTest(destination=field, key=key, value=value):
                    payload = copy.deepcopy(self.payload)
                    payload['scenery'][0]['agilityShortcut'][field][key] = value
                    self.write_payload(payload)
                    self.check_parsers(False)
        for value in (None, [], dict(self.shortcut, unknown=True)):
            with self.subTest(shortcut=value):
                payload = copy.deepcopy(self.payload)
                payload['scenery'][0]['agilityShortcut'] = value
                self.write_payload(payload)
                self.check_parsers(False)
        payload = copy.deepcopy(self.payload)
        del payload['scenery'][0]['agilityShortcut']['failureDestination']
        self.write_payload(payload)
        self.check_parsers(False)

    def test_encoding_and_existing_placement_guards_retained(self):
        for version, encoding in ((4, 'layered-world-placements-v4'), (6, 'layered-world-placements-v6'),
                                  (4, ENCODING)):
            with self.subTest(version=version, encoding=encoding):
                payload = copy.deepcopy(self.payload)
                payload.update(schemaVersion=version, encoding=encoding)
                self.write_payload(payload, encoding)
                self.check_parsers(False)
        for family, field, value in (('npcs', 'respawnSeconds', 86401), ('groundItems', 'amount', 0),
                                     ('scenery', 'direction', 9), ('scenery', 'unknown', True)):
            with self.subTest(family=family, field=field):
                payload = copy.deepcopy(self.payload)
                payload[family][0][field] = value
                self.write_payload(payload)
                self.check_parsers(False)
        payload = copy.deepcopy(self.payload)
        payload['scenery'][1]['position'] = payload['scenery'][0]['position']
        self.write_payload(payload)
        self.check_parsers(False)

    def test_normal_packaged_client_bootstraps_v5_without_adaptive_binding(self):
        self.write_payload(self.payload)
        manifest = json.loads((self.package / 'manifest.json').read_text())
        _, fingerprint = self.selection.inventory(self.package)
        client = self.case / 'client'
        target = client / 'world-builder/packages' / fingerprint / 'package'
        target.parent.mkdir(parents=True)
        shutil.copytree(self.package, target)
        profile = client / 'world-builder-configs/installed-client.json'
        profile.parent.mkdir()
        digest = hashlib.sha256((target / 'manifest.json').read_bytes()).hexdigest()
        profile.write_text(json.dumps(dict(schemaVersion=1, manifestType='world-builder-installed-client-profile',
                                          active=True, packageId=manifest['packageId'], packageVersion=manifest['packageVersion'],
                                          packageFingerprintSha256=fingerprint, manifestSha256=digest,
                                          packageRelativePath=str(target.relative_to(client)))))
        result = subprocess.run(['java', '-Xmx256m', '-Djava.awt.headless=true', '-cp',
                                 str(self.classes) + os.pathsep + str(self.client_jar), 'InstalledClientCheck',
                                 str(profile), digest], cwd=client, text=True, capture_output=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
