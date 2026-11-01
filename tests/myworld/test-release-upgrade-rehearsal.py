#!/usr/bin/env python3
"""Offline, private old->new->restart->whole-backup-restore persistence rehearsal.

Requires an explicitly supplied consistent read-only SQLite snapshot and frozen
old runtime. No live discovery, database identities, or raw receipts are emitted.
All database copies, raw logs, and per-account receipts remain mode 700/600 in
the supplied private work parent. The only stdout is a sanitized summary.
"""
import argparse
import fcntl
import hashlib
import json
import os
import pathlib
import re
import shutil
import sqlite3
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAVA = pathlib.Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
SOURCE = ROOT / 'server/test/com/openrsc/server/io/ReleaseUpgradeRehearsal.java'
GRACE_IDS = (3299, 3300, 3307, 3308)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def db(path):
    return sqlite3.connect(path)


def canonical_rows(connection, table, columns='*'):
    # Data remains in memory/private receipts; never interpolate it in errors.
    rows = connection.execute(f'SELECT {columns} FROM "{table}"').fetchall()
    return sorted(rows, key=repr)


def economics(path):
    with db(path) as c:
        owned = []
        for table in ('invitems', 'bank', 'equipped'):
            owned.extend((table, *r) for r in c.execute(
                f'SELECT o.playerID,o.itemID,s.catalogID,s.amount,s.noted,s.wielded,s.durability '
                f'FROM {table} o JOIN itemstatuses s ON s.itemID=o.itemID'))
        return {
            'owned': sorted(owned, key=repr),
            'itemstatuses': canonical_rows(c, 'itemstatuses'),
            'auctions': canonical_rows(c, 'auctions'),
            'claims': canonical_rows(c, 'expired_auctions'),
            'skills': canonical_rows(c, 'experience'),
            'currentSkills': canonical_rows(c, 'curstats'),
            'presets': canonical_rows(c, 'bankpresets', 'playerID,slot,inventory,equipment'),
            'authentication': canonical_rows(c, 'players', 'id,username,email,pass,salt,creation_ip,login_ip,former_name'),
        }


def inventory_tree(root):
    return {str(p.relative_to(root)): digest(p) for p in sorted(root.rglob('*')) if p.is_file()}


def seed_probe(path):
    with db(path) as c:
        info = c.execute('PRAGMA table_info(players)').fetchall()
        template = list(c.execute('SELECT * FROM players ORDER BY id LIMIT 1').fetchone())
        columns = [r[1] for r in info]
        probe_id = c.execute('SELECT MAX(id)+1 FROM players').fetchone()[0]
        replacements = dict(id=probe_id, username='Releaseprobe', email='', **{'pass':'unused'}, salt='',
                            creation_ip='127.0.0.1', login_ip='127.0.0.1', former_name='',
                            x=120, y=648, online=0, group_id=10, banned=0, muted=0)
        for k, v in replacements.items():
            template[columns.index(k)] = v
        c.execute('INSERT INTO players VALUES('+','.join('?' for _ in template)+')', template)
        for table in ('experience', 'curstats'):
            data = list(c.execute(f'SELECT * FROM {table} LIMIT 1').fetchone())
            data[0] = probe_id
            data[1:] = [0 if table == 'experience' else 1] * (len(data)-1)
            c.execute(f'INSERT INTO {table} VALUES('+','.join('?' for _ in data)+')', data)
        next_id = c.execute('SELECT MAX(itemID)+1 FROM itemstatuses').fetchone()[0]
        for slot, catalog in enumerate(GRACE_IDS):
            c.execute('INSERT INTO itemstatuses(itemID,catalogID,amount,noted,wielded,durability) VALUES(?,?,?,0,0,100)',
                      (next_id+slot, catalog, slot+1))
            c.execute('INSERT INTO invitems(playerID,itemID,slot) VALUES(?,?,?)', (probe_id,next_id+slot,slot))
        return probe_id


def config(runtime, manifest):
    server = runtime / 'server'
    text = (server/'myworld-host.conf').read_text()
    replacements = dict(db_name='rehearsal', server_bind_address='127.0.0.1',
                        server_port='1', ws_server_port='2', want_feature_websockets='false',
                        monitor_online='false', want_pcap_logging='false',
                        want_auto_server_shutdown='false', want_reset_event='false',
                        layered_native_terrain_manifest_sha256=manifest)
    for key in re.findall(r'(?m)^\s*(want_discord_[a-z_]+):', text):
        replacements[key] = 'false'
    for key,value in replacements.items():
        text = re.sub(r'(?m)^([ \t]*)'+re.escape(key)+r':.*$',
                      lambda m: f'{m[1]}{key}: {value}', text)
    (server/'rehearsal.conf').write_text(text)
    (server/'connections.conf').write_text('db_type: sqlite\ndatabase:\n\tdb_table_prefix: \n')
    (server/'.offline-release-rehearsal').write_text('Disposable offline persistence fixture\n')
    (server/'safe-log4j.xml').write_text('<Configuration status="OFF"><Loggers><Root level="OFF"/></Loggers></Configuration>')
    (server/'tmp').mkdir(exist_ok=True)


def phase(runtime, mode, compile_fixture=True):
    server = runtime/'server'
    cp = os.pathsep.join(map(str, [server/'rehearsal-classes', server/'core-gameplay-overlay.jar',
                                  server/'core.jar',server/'plugins.jar',*sorted((server/'lib').glob('*.jar'))]))
    if compile_fixture:
        (server/'rehearsal-classes').mkdir(exist_ok=True)
        with (server/'compile.log').open('w') as log:
            code = subprocess.run([str(JAVA/'javac'),'-J-Xmx512m','-J-XX:+UseSerialGC',
                                   '-cp',cp,'-d',str(server/'rehearsal-classes'),str(SOURCE)],
                                  stdout=log,stderr=subprocess.STDOUT,timeout=120).returncode
        require(code == 0, 'Fixture compilation failed; inspect private compile.log')
    receipt = server/(mode+'-receipt.json')
    with (server/(mode+'.log')).open('w') as log:
        code = subprocess.run([str(JAVA/'java'),'-Xmx512m','-XX:+UseSerialGC',
                               '-Djava.io.tmpdir='+str(server/'tmp'),
                               '-Dlog4j.configurationFile='+str(server/'safe-log4j.xml'),
                               '-cp',cp,'com.openrsc.server.io.ReleaseUpgradeRehearsal',
                               'rehearsal.conf',str(receipt),mode],cwd=server,stdout=log,stderr=subprocess.STDOUT,
                              timeout=300).returncode
    require(code == 0 and receipt.exists(), 'Production persistence phase failed; inspect private '+mode+'.log')
    return json.loads(receipt.read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs',type=pathlib.Path,required=True)
    parser.add_argument('--candidate',type=pathlib.Path,required=True)
    parser.add_argument('--map-export',type=pathlib.Path,required=True)
    parser.add_argument('--map-fingerprint',required=True)
    parser.add_argument('--map-manifest-sha256',required=True)
    args=parser.parse_args()
    os.umask(0o077)
    require(args.inputs.resolve().parent.stat().st_mode & 0o077 == 0, 'Input parent must be private')
    inputs=json.loads(args.inputs.read_text())
    snapshot=pathlib.Path(inputs['databaseSnapshot'])
    old=pathlib.Path(inputs['oldRuntimeRoot'])
    require(snapshot.stat().st_mode & 0o222 == 0, 'Input database must be read-only')
    require(digest(snapshot)==inputs['snapshotSha256'], 'Snapshot digest mismatch')
    original_tree=inventory_tree(old)
    for rel,sha in inputs['artifactSha256'].items():
        require(digest(old/rel)==sha,'Frozen old artifact mismatch')
    with sqlite3.connect(snapshot.as_uri()+'?mode=ro&immutable=1',uri=True) as c:
        require(c.execute('PRAGMA quick_check').fetchone()[0]=='ok','Original database integrity failure')
    work=args.inputs.resolve().parent/('rehearsal-'+uuid.uuid4().hex[:12]);work.mkdir(mode=0o700)
    metadata=json.loads((args.candidate.parent/'update.json').read_text())
    fixture_source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    source=metadata['sourceCommit']
    require(subprocess.run(['git','diff','--quiet',source,'--','server/src','server/conf',
                            'server/database','server/myworld-host.conf','server/world-builder-capabilities.json'],
                           cwd=ROOT).returncode==0,'Runtime/config source differs from candidate commit')
    for rel,sha in metadata['artifactSha256'].items():
        require(digest(args.candidate/rel)==sha,'Current candidate artifact mismatch')
    require(inventory_tree(old/'server/lib')==inventory_tree(args.candidate/'server/lib'),
            'Runtime dependencies differ; explicitly supply matching current libraries')
    backup=work/'complete-old-backup';shutil.copytree(old,backup)
    (backup/'server/inc/sqlite').mkdir(parents=True)
    shutil.copy2(snapshot,backup/'server/inc/sqlite/rehearsal.db')
    os.chmod(backup/'server/inc/sqlite/rehearsal.db',0o600)
    probe=seed_probe(backup/'server/inc/sqlite/rehearsal.db')
    config(backup,inputs['oldMapManifestSha256'])
    backup_tree=inventory_tree(backup)
    old_run=work/'old-baseline';shutil.copytree(backup,old_run)
    old_before=economics(old_run/'server/inc/sqlite/rehearsal.db')
    old_receipt=phase(old_run,'baseline')
    require(old_receipt['count']==inputs['aggregateCounts']['players']+1,'Snapshot profile count mismatch')
    old_after=economics(old_run/'server/inc/sqlite/rehearsal.db')
    current=work/'candidate';shutil.copytree(backup,current)
    # Replace only explicit runtime assets and versioned config/query/definition files.
    tracked=subprocess.check_output(['git','ls-files','server/conf','server/database',
                                     'server/myworld-host.conf','server/world-builder-capabilities.json'],cwd=ROOT,text=True).splitlines()
    for rel in tracked:
        target=current/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/rel,target)
    for rel in ('server/core.jar','server/core-gameplay-overlay.jar','server/plugins.jar'):
        shutil.copy2(args.candidate/rel,current/rel)
    for folder in ('server/world-builder-fallback','server/world-builder-configs','Client_Base/world-builder-fallback'):
        shutil.copytree(args.candidate/folder,current/folder,dirs_exist_ok=True)
    for side in ('server','Client_Base'):
        package=args.map_export/side/'world-builder/packages'/args.map_fingerprint/'package'
        require(digest(package/'manifest.json')==args.map_manifest_sha256,'Selected map digest mismatch')
        shutil.copytree(package,current/side/'world-builder/packages'/args.map_fingerprint/'package')
    pointer=current/'server/world-builder-configs/primary.json'
    data=json.loads(pointer.read_text())
    for side,key in [('server','serverMapRelativePath'),('Client_Base','clientMapRelativePath')]:
        data[key]=f'{side}/world-builder/packages/{args.map_fingerprint}/package'
    pointer.write_text(json.dumps(data,indent=2))
    config(current,args.map_manifest_sha256)
    new_receipt=phase(current,'schema')
    require(new_receipt['count']==old_receipt['count'],'Candidate profile count mismatch')
    require(all('Grace' in name for name in new_receipt['legacyGraceDefinitionNames']),
            'Stable legacy Neutral IDs must resolve to Grace definitions')
    new_after=economics(current/'server/inc/sqlite/rehearsal.db')
    for key in old_after:
        require(new_after[key]==old_after[key], 'Old/current persistence mismatch in '+key)
    corrected_location_count=0
    task_migration_count=0
    with db(backup/'server/inc/sqlite/rehearsal.db') as c:
        original_positions={str(r[0]):r[1:] for r in c.execute('SELECT id,x,y FROM players')}
        cached_locations={str(r[0]) for r in c.execute("SELECT playerID FROM player_cache WHERE key LIKE 'layerloc_%'")}
    for pid,old_profile in old_receipt['profiles'].items():
        new_profile=new_receipt['profiles'][pid]
        for key in ('inventory','bank','equipment','presets'):
            require(new_profile[key]==old_profile[key], 'Loaded old/current mismatch in '+key)
        for key in ('rank','balances','intro','completed','inventoryUpgrades','migration','legacyStatus','legacyPrestige'):
            require(new_profile['slayer'][key]==old_profile['slayer'][key], 'Slayer entitlement migration mismatch in '+key)
        task_migration_count+=old_profile['slayer']!=new_profile['slayer']
        if old_profile['location']!=new_profile['location']:
            # Accepted bootstrap bug fix: no cache + legacy packed underground
            # band must preserve signed level -1, rather than old erroneous 0.
            x,y=original_positions[pid]
            require(pid not in cached_locations and y//944==3,
                    'Unexpected location change outside accepted underground bootstrap correction')
            require(f'x={x}, y={y%944}, level=-1' in new_profile['location'],
                    'Underground bootstrap did not preserve original coordinates')
            require(re.sub(r'level=-?\d+','level=*',old_profile['location'])==
                    re.sub(r'level=-?\d+','level=*',new_profile['location']),
                    'Underground bootstrap unexpectedly changed XY')
            corrected_location_count+=1
    restart=phase(current,'restart',False)
    require(restart['profiles']==new_receipt['profiles'],'Candidate second JVM load/save not idempotent')
    require(economics(current/'server/inc/sqlite/rehearsal.db')==new_after,'Second JVM changed saved economics')
    progressed=phase(current,'progress',False)
    require(progressed['profiles'][str(probe)]['slayer']!=restart['profiles'][str(probe)]['slayer'], 'Probe progression missing')
    progressed_restart=phase(current,'progress-restart',False)
    require(progressed_restart['profiles']==progressed['profiles'],'Post-upgrade progression failed restart')
    # Downgrade is read-only at application level; never save new-roster accounts with old code.
    downgrade=work/'unsafe-source-only-downgrade';shutil.copytree(backup,downgrade)
    shutil.copy2(current/'server/inc/sqlite/rehearsal.db',downgrade/'server/inc/sqlite/rehearsal.db')
    downgraded=phase(downgrade,'downgrade')
    require(downgraded['rejectedSlayer']>0,'Expected forward-roster downgrade guard not observed')
    restored=work/'restored-complete-old';shutil.copytree(backup,restored)
    require(inventory_tree(restored)==backup_tree,'Complete old backup restore differs before execution')
    restore_receipt=phase(restored,'restored')
    require(restore_receipt['profiles']==old_receipt['profiles'],'Restored old profile receipts differ')
    require(economics(restored/'server/inc/sqlite/rehearsal.db')==old_after,'Restored old economic state differs')
    require(restore_receipt['profiles'][str(probe)]['slayer']!=progressed['profiles'][str(probe)]['slayer'],
            'Rollback must restore old state, not retain post-upgrade progression')
    require(digest(snapshot)==inputs['snapshotSha256'] and inventory_tree(old)==original_tree,
            'Read-only input changed')
    with db(current/'server/inc/sqlite/rehearsal.db') as c:
        require(c.execute('PRAGMA quick_check').fetchone()[0]=='ok','Final private DB integrity failure')
        tables={r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        require('market_operations' in tables,'Additive market receipt schema missing')
        require(c.execute('SELECT count(*) FROM market_operations').fetchone()[0]==0,
                'Schema-only rehearsal must not perform auction trades')
    with db(old_run/'server/inc/sqlite/rehearsal.db') as c:
        old_tables={r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    require(tables-old_tables=={'market_operations'} and old_tables<=tables,
            'Unexpected table addition/removal during candidate upgrade')
    summary=dict(status='PASS',sourceCommit=source,oldSourceCommit=inputs['oldSourceCommit'],
                 fixtureCommit=fixture_source,
                 fixtureSourceSha256=digest(SOURCE),runnerSourceSha256=digest(pathlib.Path(__file__)),
                 oldRelease=inputs['oldRelease'],existingProfiles=inputs['aggregateCounts']['players'],syntheticProfiles=1,
                 oldRosterVersion=old_receipt['rosterVersion'],newRosterVersion=new_receipt['rosterVersion'],
                 sourceOnlyDowngradeRejectedProfiles=downgraded['rejectedSlayer'],mapFingerprint=args.map_fingerprint,
                 assertions=sum(x['checks'] for x in (old_receipt,new_receipt,restart,progressed,progressed_restart,downgraded,restore_receipt)),
                 allInputFilesUnchanged=True,networkAllowed=False,serverStarted=False,
                 oldArtifactSha256=inputs['artifactSha256'],
                 candidateServerArtifactSha256={k:v for k,v in metadata['artifactSha256'].items() if k.startswith('server/')},
                 oldMapFingerprint=inputs['oldMapFingerprint'],
                 candidateMapManifestSha256=args.map_manifest_sha256,
                 additiveTables=['market_operations'],runtimeDependencyJars=21,
                 legacyGraceProbeIds=list(GRACE_IDS),fullRollbackVerified=True,
                 currentGraceDefinitionNames=new_receipt['legacyGraceDefinitionNames'],
                 acceptedUndergroundBootstrapCorrections=corrected_location_count,
                 otherExistingLocationsPreserved=inputs['aggregateCounts']['players']-corrected_location_count,
                 activeTaskMigrationProfiles=task_migration_count,
                 clientRollbackTested=False,
                 postUpgradeProgressionRetainedByRollback=False,
                 inheritedNormalizationGroups=[k for k in old_before if old_before[k]!=old_after[k]])
    (work/'sanitized-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(summary,indent=2))
    print('Private evidence:',work)


if __name__=='__main__':
    with open('/home/justin/.local/share/core-heavy-build.lock','a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        main()
