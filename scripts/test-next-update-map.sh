#!/usr/bin/env bash
# Explicit external candidate only. Never starts Server.init(), listeners or accounts.
set -euo pipefail
if [[ $# != 3 ]]; then
  echo 'Usage: scripts/test-next-update-map.sh PACKAGE MANIFEST_SHA256 NEW_EVIDENCE_DIR' >&2
  exit 2
fi
repo="$(cd "$(dirname "$0")/.." && pwd)"
package="$(realpath "$1")"
manifest="$2"
evidence="$3"
[[ "$manifest" =~ ^[0-9a-f]{64}$ && -f "$package/manifest.json" ]] || exit 2
[[ ! -e "$evidence" ]] || { echo 'Evidence directory must be new' >&2; exit 2; }
mkdir -p "$evidence"
evidence="$(realpath "$evidence")"
export JAVA_HOME="${JAVA_HOME:-/home/justin/jdk8u482-b08}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANT_OPTS='-Xmx512m -XX:+UseSerialGC'
exec 9>"${CORE_HEAVY_BUILD_LOCK:-/home/justin/.local/share/core-heavy-build.lock}"
flock 9
python3 - "$repo" "$evidence" "$package" <<'PY'
import importlib.util, json, pathlib, subprocess, sys
repo, output, package = map(pathlib.Path, sys.argv[1:])
fixture = output / 'fixture'
fixture.mkdir()
# Only versioned definitions, sample config and SQL query XML; no account DB,
# local.conf, credentials, owner save, installed map or symlinked source root.
names = subprocess.check_output(['git', '-C', str(repo), 'ls-files', '-z',
    'server/conf', 'server/database/mysql/queries', 'server/database/sqlite/queries',
    'server/database/sql_queries.xsd', 'server/myworld.conf',
    'server/connections.conf'], text=True).split('\0')
for name in filter(None, names):
    destination = fixture / pathlib.Path(name).relative_to('server')
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(subprocess.check_output(['git', '-C', str(repo), 'show', 'HEAD:' + name]))
(output / 'source-commit.txt').write_text(subprocess.check_output(
    ['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True))
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('selection', repo / 'scripts/installed-map-selection.py')
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)
entries, fingerprint = selection.inventory(package)
(output / 'package-identity.json').write_text(json.dumps(dict(
    package=str(package), fingerprint=fingerprint, entries=entries), sort_keys=True, indent=2)+'\n')
PY
(
  cd "$repo/server"
  sh ../tools/vendor/apache-ant-1.10.5/bin/ant compile_core compile_plugins
) >"$evidence/build.log" 2>&1
mkdir "$evidence/classes"
"$JAVA_HOME/bin/javac" -J-Xmx512m -J-XX:+UseSerialGC -source 8 -target 8 -cp "$repo/server/core.jar:$repo/server/plugins.jar" \
  -d "$evidence/classes" "$repo/server/test/com/openrsc/server/io/NextUpdateMapAcceptanceTest.java"
set +e
(
  cd "$evidence/fixture"
  "$JAVA_HOME/bin/java" -Xmx512m -XX:+UseSerialGC \
    -cp "$evidence/classes:$repo/server/core.jar:$repo/server/plugins.jar" \
    com.openrsc.server.io.NextUpdateMapAcceptanceTest "$package" "$manifest"
) 2>&1 | tee "$evidence/runtime.log"
result=$?
set -e
python3 - "$repo" "$evidence" "$package" <<'PY'
import importlib.util, json, pathlib, sys
repo, output, package = map(pathlib.Path, sys.argv[1:])
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('selection', repo / 'scripts/installed-map-selection.py')
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)
before = json.loads((output / 'package-identity.json').read_text())
_, after = selection.inventory(package)
if after != before['fingerprint']:
    raise SystemExit('FAIL: selected external package changed during acceptance')
print('PASS external package bytes unchanged: ' + after)
PY
exit "$result"
