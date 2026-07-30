#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

myworld_require_command git
myworld_require_command readlink
myworld_require_command ss
myworld_require_command ps

git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || myworld_fail "Deployment must be run from the Spoiled Milk git repository."

git -C "$ROOT_DIR" fetch --prune spoiled-milk \
  || myworld_fail "Unable to refresh spoiled-milk before deployment."

main_commit="$(myworld_git_main_commit || true)"
[[ -n "$main_commit" ]] \
  || myworld_fail "Unable to resolve spoiled-milk/main. Publish or fetch it before deploying."

manager_root="$(git -C "$ROOT_DIR" worktree list --porcelain | sed -n 's/^worktree //p' | sed -n '1p')"
manager_root="$(myworld_realpath "$manager_root" 2>/dev/null || true)"
current_root="$(myworld_realpath "$ROOT_DIR" 2>/dev/null || true)"
[[ -n "$manager_root" && "$current_root" == "$manager_root" ]] \
  || myworld_fail "Run deployment from the primary manager checkout: ${manager_root:-unknown}"

manager_branch="$(git -C "$ROOT_DIR" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
[[ "$manager_branch" == "main" ]] \
  || myworld_fail "Deployment requires manager branch main; found ${manager_branch:-detached HEAD}."
for operation_marker in MERGE_HEAD CHERRY_PICK_HEAD REVERT_HEAD rebase-apply rebase-merge sequencer BISECT_LOG; do
  operation_path="$(git -C "$ROOT_DIR" rev-parse --git-path "$operation_marker")"
  [[ ! -e "$operation_path" ]] \
    || myworld_fail "Deployment is blocked by unfinished Git state: $operation_marker"
done
manager_status="$(myworld_git_dirty_status "$ROOT_DIR")"
[[ -z "$manager_status" ]] \
  || myworld_fail "Deployment requires a clean manager main checkout."
manager_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
[[ "$manager_commit" == "$main_commit" ]] \
  || myworld_fail "Manager main must exactly match published spoiled-milk/main before deployment."

external_db="$(myworld_external_live_database_path)"
[[ -f "$external_db" ]] \
  || myworld_fail "External live database is missing: $external_db"

# Never replace tracked runtime files beneath a running JVM. Activation is a
# short controlled sequence: backup, stop, deploy, start.
myworld_require_port_free "$MYWORLD_PUBLIC_PORT"
running_live_pids="$(myworld_server_pids_for_root "$MYWORLD_LIVE_ROOT")"
[[ -z "$running_live_pids" ]] \
  || myworld_fail "A server process is still using the live worktree (PID(s): $running_live_pids). Stop it before deployment."

live_layered_package="$(layered_world_install_live_package \
  "$ROOT_DIR" "$MYWORLD_LIVE_DB_ROOT")"

if [[ -e "$MYWORLD_LIVE_ROOT" ]]; then
  [[ -d "$MYWORLD_LIVE_ROOT" ]] \
    || myworld_fail "Fixed live path exists but is not a directory: $MYWORLD_LIVE_ROOT"
  git -C "$MYWORLD_LIVE_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || myworld_fail "Fixed live path is not a git worktree: $MYWORLD_LIVE_ROOT"

  manager_common_dir="$(git -C "$ROOT_DIR" rev-parse --path-format=absolute --git-common-dir)"
  live_common_dir="$(git -C "$MYWORLD_LIVE_ROOT" rev-parse --path-format=absolute --git-common-dir)"
  [[ "$manager_common_dir" == "$live_common_dir" ]] \
    || myworld_fail "Fixed live path belongs to a different repository: $MYWORLD_LIVE_ROOT"

  dirty_status="$(myworld_git_dirty_status "$MYWORLD_LIVE_ROOT")"
  [[ -z "$dirty_status" ]] \
    || myworld_fail "Refusing to deploy over dirty live checkout (including untracked files):
$dirty_status"

  checkout_db="$(myworld_live_database_path "$MYWORLD_LIVE_ROOT")"
  if [[ -L "$checkout_db" ]]; then
    myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
  elif [[ -e "$checkout_db" ]]; then
    myworld_fail "Refusing to replace non-symlink live database path: $checkout_db"
  else
    mkdir -p "$(dirname "$checkout_db")"
    ln -s "$external_db" "$checkout_db"
    myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
  fi

  git -C "$MYWORLD_LIVE_ROOT" switch --detach "$main_commit"
else
  git -C "$ROOT_DIR" worktree add --detach "$MYWORLD_LIVE_ROOT" "$main_commit"
fi

if git -C "$MYWORLD_LIVE_ROOT" symbolic-ref --quiet HEAD >/dev/null 2>&1; then
  myworld_fail "Live deployment unexpectedly left an attached branch at $MYWORLD_LIVE_ROOT"
fi

deployed_commit="$(git -C "$MYWORLD_LIVE_ROOT" rev-parse HEAD)"
[[ "$deployed_commit" == "$main_commit" ]] \
  || myworld_fail "Live checkout is at $deployed_commit; expected spoiled-milk/main $main_commit"

dirty_status="$(myworld_git_dirty_status "$MYWORLD_LIVE_ROOT")"
[[ -z "$dirty_status" ]] \
  || myworld_fail "Live checkout became dirty during deployment:
$dirty_status"

checkout_db="$(myworld_live_database_path "$MYWORLD_LIVE_ROOT")"
if [[ -L "$checkout_db" ]]; then
  myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
elif [[ -e "$checkout_db" ]]; then
  myworld_fail "Refusing to replace non-symlink live database path: $checkout_db"
else
  mkdir -p "$(dirname "$checkout_db")"
  ln -s "$external_db" "$checkout_db"
  myworld_require_live_database_link "$MYWORLD_LIVE_ROOT"
fi

printf 'Deployed detached published main to the fixed live checkout.\n'
printf '  Live root: %s\n' "$MYWORLD_LIVE_ROOT"
printf '  Commit:    %s\n' "$deployed_commit"
printf '  Database:  %s -> %s\n' "$checkout_db" "$external_db"
printf '  Layered world: %s\n' "$live_layered_package"
printf '  Layered manifest: %s\n' "$SPOILED_MILK_LAYERED_MANIFEST_SHA256"

printf 'The public port was verified stopped throughout deployment.\n'
printf 'Start the hosted server from the fixed live checkout when ready.\n'
printf 'Run %s/scripts/live-status.sh to inspect checkout and runtime state.\n' "$MYWORLD_LIVE_ROOT"
