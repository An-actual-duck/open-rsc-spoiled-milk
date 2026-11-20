#!/usr/bin/env python3
"""Create an empty-account private world from maintained data, never a live DB."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('run', type=Path)
    parser.add_argument('--tcp-port', type=int, default=43629)
    parser.add_argument('--ws-port', type=int, default=43529)
    parser.add_argument('--covered-walking-preview', action='store_true')
    parser.add_argument('--predicted-scenery', action='store_true', help='Enable the private scenery prediction')
    parser.add_argument('--prebuild-ownership', action='store_true', help='Stage scenery in the ownership transition strips')
    parser.add_argument('--travel-trace', action='store_true', help='Record the disposable character position on each server tick')
    parser.add_argument('--outer-coverage', action='store_true', help='Spawn at the private outer coverage test route')
    args = parser.parse_args(); run = args.run.resolve()
    if not all(1024 <= port <= 65535 for port in (args.tcp_port, args.ws_port)) or args.tcp_port == args.ws_port:
        parser.error('Use two different nonprivileged loopback ports')
    if args.outer_coverage and not args.covered_walking_preview:
        parser.error('The outer coverage route requires the covered walking preview')
    if args.predicted_scenery and not args.covered_walking_preview:
        parser.error('Predicted scenery requires the covered walking preview')
    if args.prebuild_ownership and not args.predicted_scenery:
        parser.error('Scenery ownership prediction requires the scenery preview')
    if not (run / 'stage.json').is_file() or (run / 'server').exists(): parser.error('Use a staged run without a server; existing data is preserved')
    selection = json.loads(subprocess.check_output(['python3', str(ROOT / 'scripts/installed-map-selection.py'), str(ROOT)]))
    server = run / 'server'; server.mkdir()
    for name in ['conf', 'database']: shutil.copytree(ROOT / 'server' / name, server / name)
    for name in ['badwords.txt', 'goodwords.txt', 'globalrules.txt', 'alertwords.txt']: shutil.copy2(ROOT / 'server' / name, server / name)
    package = server / 'package'; shutil.copytree(selection['serverPackage'], package)
    config = (ROOT / 'server/myworld.conf').read_text()
    values = dict(db_name='browser_spike_dev', server_name='Private Browser Proof', server_name_welcome='Private Browser Proof',
                  server_bind_address='127.0.0.1', server_port=str(args.tcp_port), ws_server_port=str(args.ws_port), want_feature_websockets='true',
                  want_discord_bot='false', want_discord_general_logging='false', want_discord_auction_updates='false',
                  want_discord_item_showcase='false', want_discord_monitoring_updates='false', want_auto_server_shutdown='false',
                  layered_native_terrain_package_path=str(package), layered_native_terrain_manifest_sha256=selection['manifestSha256'],
                  layered_native_world_runtime_profile='world-builder-installed')
    if args.outer_coverage:
        values.update(respawn_location_x='239', respawn_location_y='609')
    for key, value in values.items():
        config, count = re.subn(r'(?m)^([ \t]*)' + key + r':.*$', lambda m: m[1] + key + ': ' + value, config)
        if not count: config += '\n' + key + ': ' + value + '\n'
    (server / 'myworld.conf').write_text(config)
    # The existing OptionalSslHandler requires an SSL context even on ws://.
    # This per-run self-signed certificate is only a local handler prerequisite;
    # the browser still uses plaintext loopback WS and trusts no new certificate.
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', str(server / 'private-key.pem'),
                    '-out', str(server / 'private-cert.pem'), '-days', '7', '-subj', '/CN=localhost'],
                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, check=True)
    (server / 'private-key.pem').chmod(0o600)
    (server / 'connections.conf').write_text('db_type: sqlite\nssl:\n\tssl_server_cert_path: ' + str(server / 'private-cert.pem') + '\n\tssl_server_key_path: ' + str(server / 'private-key.pem') + '\n')
    dbdir = server / 'inc/sqlite'; dbdir.mkdir(parents=True)
    seed = ROOT / 'server/inc/sqlite/myworld_seed.db'
    with sqlite3.connect('file:' + str(seed) + '?mode=ro', uri=True) as database:
        assert database.execute('SELECT COUNT(*) FROM players').fetchone()[0] == 0, 'Seed must contain zero accounts'
        assert database.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    shutil.copy2(seed, dbdir / 'browser_spike_dev.db')
    (dbdir / 'browser_spike_dev.db').chmod(0o600)
    (server / 'BROWSER-PRIVATE.json').write_text(json.dumps(dict(source=str(ROOT), seedAccounts=0, seedSha256=hashlib.sha256(seed.read_bytes()).hexdigest(), mapManifest=selection['manifestSha256']), indent=2) + '\n')
    # A future worker rebuild/recycle cannot change classes under this JVM.
    artifacts = []
    for name in ('core.jar', 'plugins.jar', 'core-gameplay-overlay.jar'):
        source = ROOT / 'server' / name
        shutil.copy2(source, server / name)
        artifacts.append(dict(path=name, sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    lib = server / 'lib'; lib.mkdir()
    for source in sorted((ROOT / 'server/lib').glob('*.jar')):
        shutil.copy2(source, lib / source.name)
        artifacts.append(dict(path='lib/' + source.name, sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    marker = json.loads((server / 'BROWSER-PRIVATE.json').read_text())
    marker['sourceCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    marker['artifacts'] = artifacts
    (server / 'BROWSER-PRIVATE.json').write_text(json.dumps(marker, indent=2) + '\n')
    ordered = ['log4j-api-2.17.0.jar', 'log4j-core-2.17.0.jar', 'log4j-iostreams-2.17.0.jar', 'xpp3-1.1.4c.jar', 'mysql-connector-j-9.4.0.jar', 'commons-lang-2.6.jar']
    classes = run / 'server-classes'; classes.mkdir()
    cp = ':'.join([str(lib / name) for name in ordered] + [str(server / 'core-gameplay-overlay.jar'), str(server / 'core.jar'), str(server / 'plugins.jar'), str(lib / '*'), str(classes)])
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', cp, '-d', str(classes), str(ROOT / 'tools/browser-gameplay/src/BrowserPrivateServer.java')], check=True)
    command = ['java', '-Xmx2g', '-XX:+UseSerialGC']
    if args.covered_walking_preview:
        command.append('-Dspoiledmilk.coveredWalkingPreview=true')
    if args.predicted_scenery:
        command.append('-Dspoiledmilk.predictedSceneryPreview=true')
    if args.prebuild_ownership:
        command.append('-Dspoiledmilk.predictedSceneryOwnershipPreview=true')
    if args.travel_trace:
        command.append('-Dspoiledmilk.browserTravelTrace=true')
    command += ['-cp', cp, 'BrowserPrivateServer', str(args.tcp_port), str(args.ws_port)]
    (run / 'server-command.json').write_text(json.dumps(dict(cwd=str(server), command=command), indent=2) + '\n')
    print('Prepared private server. Disposable credentials: browserdemo / BrowserProof13')


if __name__ == '__main__': main()
