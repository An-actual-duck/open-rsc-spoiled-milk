#!/usr/bin/env bash
set -euo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Manager-owned release operation; the candidate remains an isolated source.
[[ "$(pwd -P)" == /home/justin/Core-Framework ]] || { echo 'FAIL: invoke this release operation from the Core manager checkout' >&2; exit 1; }
[[ "$(git symbolic-ref --short HEAD)" == main ]] || { echo 'FAIL: manager must remain on main' >&2; exit 1; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
branch="" commit="" version="" previous=""
for argument in "$@"; do
  [[ "$argument" != --skip-build ]] || fail "Fixes-only releases require a fresh build"
  case "$previous" in
    --fixes-only-source) branch="$argument" ;;
    --source-commit) commit="$argument" ;;
    --version) version="$argument" ;;
  esac
  previous="$argument"
done
[[ -n "$version" ]] || fail "Specify the release version"
source "$SOURCE_ROOT/scripts/lib/fixes-only-release-source.sh"
ROOT_DIR="$(pwd -P)"
source "$SOURCE_ROOT/scripts/lib/ai-workspace-common.sh"
ai_initialize_context
fixes_only_require_no_git_operation "$(pwd -P)"
fixes_only_require_source "$SOURCE_ROOT" "$branch" "$commit"
manager_remote="$(git remote get-url spoiled-milk)"
candidate_remote="$(git -C "$SOURCE_ROOT" remote get-url spoiled-milk)"
[[ "$manager_remote" == "$candidate_remote" ]] || fail "Candidate uses a different publication remote"
# Existing worker work is not integrated. Require each registered slot to be
# clean and backed up before proceeding, without packaging dirty manager inputs.
for slot in /home/justin/Core-Framework-ai-{1,2,3}; do
  fixes_only_require_no_git_operation "$slot"
  [[ -z "$(git -C "$slot" status --porcelain --untracked-files=all)" ]] || fail "Worker slot is dirty: $slot"
  worker_branch="$(git -C "$slot" symbolic-ref --short HEAD 2>/dev/null || true)"
  worker_head="$(git -C "$slot" rev-parse HEAD)"
  ai_load_slot_state "${slot##*-Framework-}" "$slot" || fail "Invalid registered worker state: $AI_SLOT_STATE_ERROR"
  if [[ -z "$worker_branch" ]]; then
    [[ "$AI_SLOT_STATE_PHASE" == IDLE && "$AI_SLOT_STATE_BRANCH" == DETACHED ]] || fail "Detached worker is not registered IDLE: $slot"
    [[ "$worker_head" == "$(git -C "$slot" rev-parse 'spoiled-milk/main^{commit}')" ]] || fail "IDLE worker does not match published main: $slot"
    continue
  fi
  [[ "$worker_branch" != main && "$AI_SLOT_STATE_PHASE" =~ ^(ACTIVE|READY)$ ]] || fail "Inspect worker phase/branch before fixes-only release: $slot"
  worker_remote="$(git -C "$slot" ls-remote --exit-code spoiled-milk "refs/heads/$worker_branch" | awk '{print $1}')"
  [[ "$worker_head" == "$worker_remote" ]] || fail "Worker is not backed up at its exact tip: $slot"
done
ROOT_DIR="$SOURCE_ROOT" bash "$SOURCE_ROOT/scripts/package-player-release.sh" "$@"
ROOT_DIR="$SOURCE_ROOT" bash "$SOURCE_ROOT/scripts/package-layered-world-release.sh" \
  --version "$version" --fixes-only-source "$branch" --source-commit "$commit"
printf 'Fixes-only release prepared from %s. Public activation remains unauthorized.\n' "$commit"
