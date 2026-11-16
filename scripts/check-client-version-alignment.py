#!/usr/bin/env python3
"""Check enforced server profiles against client source and/or a compiled client."""
from pathlib import Path
import argparse,re,subprocess,sys

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--client-jar',type=Path)
    args=parser.parse_args();root=args.root.resolve();versions={}
    source=root/'Client_Base/src/orsc/Config.java'
    if source.is_file():
        match=re.search(r'\bCLIENT_VERSION\s*=\s*(\d+)\s*;',source.read_text())
        if not match:raise ValueError('Cannot read client source protocol version')
        versions['client source']=int(match.group(1))
    if args.client_jar:
        result=subprocess.run(['javap','-constants','-classpath',str(args.client_jar.resolve()),'orsc.Config'],capture_output=True,text=True,check=True)
        match=re.search(r'\bCLIENT_VERSION\s*=\s*(\d+)\s*;',result.stdout)
        if not match:raise ValueError('Cannot read compiled client protocol version')
        versions['compiled client']=int(match.group(1))
    if not versions:raise ValueError('Client source or --client-jar is required')
    profiles=[root/'server/myworld.conf',root/'server/myworld-host.conf']
    if not profiles[0].is_file():raise ValueError('Missing maintained/private server profile')
    for profile in profiles:
        if not profile.is_file():continue
        match=re.search(r'^\s*client_version:\s*(\d+)\s*(?:#.*)?$',profile.read_text(),re.M)
        if not match:raise ValueError('Cannot read client_version in '+str(profile))
        versions[profile.name]=int(match.group(1))
    if len(set(versions.values()))!=1:
        raise ValueError('Client/server protocol mismatch: '+', '.join(f'{key}={value}'for key,value in versions.items()))
    print('PASS client/server protocol alignment: '+str(next(iter(versions.values()))))
if __name__=='__main__':
    try:main()
    except (OSError,ValueError,subprocess.CalledProcessError)as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
