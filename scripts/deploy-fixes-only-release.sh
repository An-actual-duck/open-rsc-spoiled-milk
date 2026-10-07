#!/usr/bin/env bash
set -euo pipefail
MANAGER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(pwd -P)" == /home/justin/Core-Framework && "$MANAGER_ROOT" == /home/justin/Core-Framework ]] || exit 1
[[ $# == 4 ]] || { echo 'Usage: deploy-fixes-only-release.sh SOURCE BRANCH COMMIT TAG' >&2; exit 1; }
candidate="$1" branch="$2" expected="$3" tag="$4"
ROOT_DIR="$MANAGER_ROOT"
source "$MANAGER_ROOT/scripts/lib/myworld-common.sh"
fail() { myworld_fail "$@"; }
source "$candidate/scripts/lib/fixes-only-release-source.sh"
fixes_only_require_no_git_operation "$MANAGER_ROOT"
[[ "$(git symbolic-ref --short HEAD)" == main ]] || fail 'Manager must remain on main'
[[ "$(git remote get-url spoiled-milk)" == "$(git -C "$candidate" remote get-url spoiled-milk)" ]] || fail 'Publication remote mismatch'
fixes_only_require_source "$candidate" "$branch" "$expected"
[[ "$(myworld_git_verified_release_commit "$candidate" "$tag")" == "$expected" ]] || fail 'Published release tag mismatch'
myworld_require_port_free "$MYWORLD_PUBLIC_PORT"
[[ -z "$(myworld_server_pids_for_root "$MYWORLD_LIVE_ROOT")" ]] || fail 'Live JVM still running'
[[ -z "$(myworld_git_dirty_status "$MYWORLD_LIVE_ROOT")" ]] || fail 'Live checkout dirty'
[[ "$(git rev-parse --path-format=absolute --git-common-dir)" == "$(git -C "$MYWORLD_LIVE_ROOT" rev-parse --path-format=absolute --git-common-dir)" ]] || fail 'Live repository mismatch'
myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
external_db="$(myworld_external_live_database_path)"
backup_dir="$MYWORLD_LIVE_DB_ROOT/backups"
mkdir -p "$backup_dir"
backup="$backup_dir/pre-${tag}-$(date -u +%Y%m%dT%H%M%SZ).db"
[[ ! -e "$backup" ]] || fail 'Backup destination exists'
python3 - "$external_db" "$backup" <<'PY'
import sqlite3, sys
with sqlite3.connect('file:' + sys.argv[1] + '?mode=ro', uri=True) as source:
    with sqlite3.connect(sys.argv[2]) as backup:
        source.backup(backup)
        assert backup.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
print('Verified live database backup:', sys.argv[2])
PY
package="$(layered_world_install_live_package "$candidate" "$MYWORLD_LIVE_DB_ROOT")"
git fetch spoiled-milk "refs/tags/$tag:refs/tags/$tag"
[[ "$(myworld_git_verified_release_commit "$MANAGER_ROOT" "$tag")" == "$expected" ]] || fail 'Fetched release changed'
fixes_only_require_source "$candidate" "$branch" "$expected"
myworld_require_port_free "$MYWORLD_PUBLIC_PORT"
[[ -z "$(myworld_server_pids_for_root "$MYWORLD_LIVE_ROOT")" ]] || fail 'Live JVM appeared during preparation'
git -C "$MYWORLD_LIVE_ROOT" switch --detach "$expected"
myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
[[ -z "$(myworld_git_dirty_status "$MYWORLD_LIVE_ROOT")" ]] || fail 'Live checkout became dirty'
printf 'Deployed published %s at %s; package %s. Start using manager scripts/run-hosted-release.sh %s\n' "$tag" "$expected" "$package" "$tag"
