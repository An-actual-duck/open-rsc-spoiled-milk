#!/usr/bin/env python3
"""Compile the changed server classes and exercise the actual bounded admission/protocol."""
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / 'server/src/com/openrsc/server'
FILES = ['GameStateUpdater.java', 'Server.java', 'login/LoginRequest.java',
         'net/ConnectionAttachment.java', 'net/RSCConnectionHandler.java',
         'net/rsc/ActionSender.java', 'net/rsc/LoginPacketHandler.java',
         'login/LoginAdmissionReservations.java', 'login/StagedLoginProtocol.java',
         'login/StagedLoginSession.java','model/world/region/RegionManager.java']

def main():
    base = Path(subprocess.check_output(['python3', 'scripts/workspace-layout.py',
        'path', 'tests', 'browser-staged-login-server', '--mkdir'], cwd=ROOT, text=True).strip())
    output = base / ('run-' + str(time.time_ns()))
    output.mkdir()
    # Use an existing normal build solely as dependency input; never rebuild or start services.
    core = Path(os.environ.get('STAGED_LOGIN_CORE_JAR', '/home/justin/Core-Framework/server/core.jar'))
    if not core.is_file():
        raise SystemExit('Set STAGED_LOGIN_CORE_JAR to a current server/core.jar dependency')
    javac = os.environ.get('JAVAC', '/usr/lib/jvm/java-21-openjdk-amd64/bin/javac')
    java = str(Path(javac).with_name('java'))
    classpath = os.pathsep.join([str(ROOT / 'server/lib/log4j-api-2.17.0.jar'),
        str(ROOT / 'server/lib/log4j-core-2.17.0.jar'), str(core), str(ROOT / 'server/lib/*')])
    commands = [[javac, '-proc:none', '-source', '8', '-target', '8', '-sourcepath', '',
        '-cp', classpath, '-d', str(output)] + [str(SERVER / name) for name in FILES]
        + [str(ROOT / 'tests/myworld/StagedLoginServerFixture.java')],
        [java, '-cp', str(output) + os.pathsep + classpath,
         'com.openrsc.server.login.StagedLoginServerFixture']]
    (output / 'commands.json').write_text(json.dumps(commands, indent=2))
    for index, command in enumerate(commands):
        result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (output / ('step-' + str(index) + '.log')).write_text(result.stdout)
        print(result.stdout, end='')
        if result.returncode:
            raise SystemExit(result.returncode)
        if index == 1 and 'PASS ' not in result.stdout:
            raise SystemExit('Fixture did not report its terminal assertion success')
    print('Evidence:', output)

if __name__ == '__main__':
    main()
