#!/usr/bin/env python3
"""Exact original-failure / fixed-transition CPU regression, without a game launch."""
import argparse, hashlib, json, pathlib, subprocess

p = argparse.ArgumentParser()
for name in ('jar', 'receipt', 'original-jar', 'cache', 'out'):
    p.add_argument('--' + name, required=True, type=pathlib.Path)
a = p.parse_args()
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
receipt = json.loads(a.receipt.read_text())
assert receipt['buildSucceeded'] and receipt['cleanCheckoutAtBuild'] and receipt['singleProductionCommit']
assert receipt['classesTransplanted'] is False and receipt['jarSha256'] == sha(a.jar)
if a.out.exists(): raise ValueError('Preserve previous run')
a.out.mkdir(parents=True)
fixture = pathlib.Path(__file__).with_name('WholeRamLayerReturnFixture.java').resolve()
commands, cases = [], []
def run(name, cmd):
    commands.append(cmd)
    result = subprocess.run(cmd, text=True, capture_output=True, timeout=90)
    (a.out / (name + '.log')).write_text(result.stdout + result.stderr)
    return result
try:
    for name, jar, enabled, original in (
        ('original-on', a.original_jar, True, True),
        ('fixed-on', a.jar, True, False),
        ('fixed-off', a.jar, False, False),
    ):
        classes = a.out / (name + '-classes'); classes.mkdir()
        result = run(name + '-compile', ['javac', '-source', '8', '-target', '8', '-cp', str(jar.resolve()), '-d', str(classes.resolve()), str(fixture)])
        assert result.returncode == 0, name + ' compilation failed'
        flags = ['-Dspoiledmilk.' + flag + '=true' for flag in ('browserBenchmark', 'browserWholeZone', 'browserWholeRam')] if enabled else []
        result = run(name, ['java', '-Djava.awt.headless=true'] + flags + ['-cp', str(classes.resolve()) + ':' + str(jar.resolve()), 'orsc.WholeRamLayerReturnFixture', str(a.cache.resolve())])
        if original:
            assert result.returncode != 0
            for frame in ('offset=2304,832', 'BrowserWholeOverworldRam.accepts', 'World.transitionSymmetricRenderer3DWorldChunkFrame', 'World.setLayeredTerrainScope'):
                assert frame in result.stderr, 'Original did not reproduce exact failure: ' + frame
        else:
            assert result.returncode == 0 and 'PASS checks=' in result.stdout, name + ' failed'
        cases.append(dict(name=name, passed=True, returncode=result.returncode, jarSha256=sha(jar)))
finally:
    (a.out / 'result.json').write_text(json.dumps(dict(passed=len(cases)==3, cases=cases, commands=commands, receipt=receipt, fixtureSha256=sha(fixture), runnerSha256=sha(pathlib.Path(__file__)), limits='Synthetic void terrain and injected production-provider admission fields isolate the actual World setter and actual provider methods. No cooked-data constructor/readiness/activation proof, browser, GPU, server, FPS or visual claim. Actual background native fallback halo production and stale-result publish guard are exercised.'), indent=2) + '\n')
print(json.dumps(dict(passed=True, cases=len(cases), output=str(a.out))))
