#!/usr/bin/env python3
"""Validate a committed, release-specific owner exception; never alters Git refs."""
import argparse,json,re,subprocess
from pathlib import Path

def validate(root,relative,version):
 def git(*args):return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.PIPE)
 path=Path(relative)
 if path.is_absolute() or '..' in path.parts or path.as_posix()!=f'config/release-exceptions/{version}.json':raise ValueError('Exception must be the version-specific repository manifest')
 file=root/path
 if file.is_symlink() or file.read_bytes()!=git('show','HEAD:'+path.as_posix()):raise ValueError('Exception must match committed source exactly')
 d=json.loads(file.read_bytes())
 if d.get('schemaVersion')!=1 or d.get('releaseVersion')!=version or not d.get('authorization'):raise ValueError('Missing exact-version owner authorization')
 rows=d.get('excludedBranches');seen=set();by_remote={}
 if not isinstance(rows,list) or not rows:raise ValueError('Nonempty explicit branch inventory required')
 for row in rows:
  b=row['branch'];c=row['commit'];remote=row['remote']
  if b=='main' or b in seen or not row.get('reason') or not re.fullmatch('[a-f0-9]{40}',c) or remote!='spoiled-milk':raise ValueError('Invalid/duplicate protected branch exception')
  git('check-ref-format','refs/heads/'+b)
  for ref in ['refs/heads/'+b,'refs/remotes/'+remote+'/'+b]:
   if git('rev-parse',ref+'^{commit}').decode().strip()!=c:raise ValueError('Excluded branch moved or lacks exact backup: '+b)
  seen.add(b);by_remote.setdefault(remote,[]).append(row)
 for remote,group in by_remote.items():
  published={ref:commit for commit,ref in (line.split() for line in git('ls-remote','--heads',remote,*['refs/heads/'+x['branch'] for x in group]).decode().splitlines())}
  for row in group:
   if published.get('refs/heads/'+row['branch'])!=row['commit']:raise ValueError('Remote backup changed/missing: '+row['branch'])
 return sorted(seen)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--manifest',required=True);p.add_argument('--version',required=True);a=p.parse_args()
 try:print('\n'.join(validate(a.root.resolve(),a.manifest,a.version)))
 except (OSError,ValueError,KeyError,subprocess.CalledProcessError) as e:raise SystemExit('FAIL release exception: '+str(e))
