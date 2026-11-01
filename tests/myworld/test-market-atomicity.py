#!/usr/bin/env python3
"""Run real engine market failure/restart checks in newly-created synthetic runtime.

Invoke under the shared heavy-build lock after compile_combat_tests. SQLite is
network-denied. --mysql-manifest accepts only Core's named disposable endpoint.
"""
import argparse, json, os, pathlib, subprocess, sys, time, uuid
root = pathlib.Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument('--mysql-manifest', type=pathlib.Path)
p.add_argument('--output', type=pathlib.Path)
a = p.parse_args()
output = a.output or root/'output'/('market-atomicity-'+uuid.uuid4().hex[:10])
output = output.resolve()
output.mkdir(parents=True, exist_ok=False)
runtime = output/'runtime'
subprocess.run([sys.executable, str(root/'tests/myworld/prepare-cleric-well-fixture.py'), str(runtime)], check=True, stdout=subprocess.DEVNULL)
env = os.environ.copy()
env.update(MARKET_TEST_JDBC='jdbc:sqlite:'+str(output/'market-test.sqlite'), MARKET_TEST_USER='', MARKET_TEST_PASSWORD='')
backend = 'sqlite'
if a.mysql_manifest:
    m = json.loads(a.mysql_manifest.read_text())
    assert m['name'] == 'core-auction-test-8f1ffb86f4' and m['host']=='127.0.0.1' and m['port']==32768 and m['database']=='auction_test'
    env.update(MARKET_TEST_JDBC=m['jdbcUrl'], MARKET_TEST_USER=m['user'], MARKET_TEST_PASSWORD=m['password'])
    backend = 'mysql-mariadb'
classpath = os.pathsep.join(str(x) for x in [root/'output/combat-test/test-classes', root/'output/combat-test/core-classes', *sorted((root/'server/lib').glob('*.jar'))])
java = pathlib.Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin/java'
results=[]
for mode, expected in [('test',0),('crash',77),('recover',0)]:
    start=time.monotonic(); log=output/(mode+'.log')
    with log.open('w') as stream:
        result=subprocess.run([str(java),'-Xmx512m','-XX:+UseSerialGC','-cp',classpath,'com.openrsc.server.combat.MarketAtomicityFixture',mode],cwd=runtime,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=180)
    results.append(dict(mode=mode, exit=result.returncode, expected=expected, seconds=round(time.monotonic()-start,2), log=str(log)))
    if result.returncode != expected:
        print(log.read_text()[-8000:]); raise SystemExit('Market fixture failed: '+mode+'; evidence '+str(output))
(output/'results.json').write_text(json.dumps(dict(backend=backend, results=results),indent=2)+'\n')
for mode in ['test','recover']:
    for line in (output/(mode+'.log')).read_text().splitlines():
        if line.startswith('PASS market'): print(line)
print('Evidence:',output)
