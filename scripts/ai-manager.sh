#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
# shellcheck source=scripts/lib/ai-workspace-common.sh
source "$SCRIPT_ROOT/scripts/lib/ai-workspace-common.sh"

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/ai-manager.sh status
  ./scripts/ai-manager.sh rescue <ai-N> [-m message] [--local-only] [--allow-sensitive] [--allow-large]
  ./scripts/ai-manager.sh collect-contributor <ai-N> <remote-topic-branch> <exact-commit>
  ./scripts/ai-manager.sh merge <topic-branch>
  ./scripts/ai-manager.sh release-check
  ./scripts/ai-manager.sh release <package-player-release options>

Rescue is an explicit preservation action for an abandoned slot. Merge accepts
only a clean, pushed READY handoff. Contributor collection verifies an exact
remote commit and imports it into an idle slot for review; it does not merge.
None of these commands deletes a worktree.
USAGE
}

manager_rescue() {
  local slot path message="" local_only=false allow_sensitive=false allow_large=false
  local branch timestamp head published_ref

  [[ $# -ge 1 ]] || ai_fail "rescue requires a workspace slot."
  slot="$(ai_normalize_slot "$1")"
  shift
  while (($#)); do
    case "$1" in
      -m|--message)
        [[ $# -ge 2 ]] || ai_fail "$1 requires a commit message."
        message="$2"
        shift 2
        ;;
      --local-only)
        local_only=true
        shift
        ;;
      --allow-sensitive)
        allow_sensitive=true
        shift
        ;;
      --allow-large)
        allow_large=true
        shift
        ;;
      *)
        ai_fail "Unknown rescue option: $1"
        ;;
    esac
  done

  ai_require_manager_identity
  path="$(ai_require_slot "$slot")"
  ai_require_no_git_operation "$path"
  branch="$(ai_current_branch "$path")"
  published_ref="$(ai_published_ref || true)"

  if [[ "$branch" == DETACHED || "$branch" == "$AI_MAIN_BRANCH" ]]; then
    if ai_is_clean "$path" \
        && [[ -n "$published_ref" ]] \
        && git -C "$path" merge-base --is-ancestor HEAD "$published_ref"; then
      ai_fail "Workspace $slot has no uncommitted work to rescue."
    fi
    timestamp="$(date -u '+%Y%m%d-%H%M%S')"
    branch="rescue/$slot/$timestamp"
    while git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$branch"; do
      branch="${branch}-1"
    done
    git -C "$path" switch -c "$branch"
  fi

  message="${message:-WIP($slot): rescue abandoned work $(date -u '+%Y-%m-%d %H:%M UTC')}"
  if [[ "$local_only" == true ]]; then
    ai_checkpoint_path "$path" "$slot" ACTIVE "$message" false "$allow_sensitive" "$allow_large"
    ai_warn "Rescue exists only locally. Push it before pruning or releasing."
  else
    ai_checkpoint_path "$path" "$slot" READY "$message" true "$allow_sensitive" "$allow_large"
  fi
  head="$(ai_head "$path")"
  printf 'Rescued %s on %s at %s. Review this branch before merging.\n' "$slot" "$branch" "$head"
}

manager_collect_contributor() {
  local slot branch expected_head path published_ref remote_ref remote_head merge_base detached_head

  [[ $# -eq 3 ]] \
    || ai_fail "collect-contributor requires an idle slot, remote topic branch, and exact 40-character commit."
  slot="$(ai_normalize_slot "$1")"
  branch="$2"
  expected_head="${3,,}"

  [[ "$branch" != "$AI_MAIN_BRANCH" ]] || ai_fail "Refusing to collect the protected $AI_MAIN_BRANCH branch."
  git -C "$ROOT_DIR" check-ref-format --branch "$branch" >/dev/null 2>&1 \
    || ai_fail "Invalid contributor topic branch: $branch"
  [[ "$expected_head" =~ ^[0-9a-f]{40}$ ]] \
    || ai_fail "Contributor handoff must provide the full 40-character commit hash."

  ai_require_manager
  published_ref="$(ai_published_ref)"
  remote_ref="$AI_REMOTE/$branch"
  git -C "$ROOT_DIR" rev-parse --verify --quiet "refs/remotes/$remote_ref^{commit}" >/dev/null \
    || ai_fail "Remote contributor branch does not exist: $remote_ref"
  remote_head="$(git -C "$ROOT_DIR" rev-parse "refs/remotes/$remote_ref^{commit}")"
  [[ "$remote_head" == "$expected_head" ]] \
    || ai_fail "Contributor branch moved: handoff named $expected_head but $remote_ref is $remote_head. Request a new exact handoff."
  if git -C "$ROOT_DIR" merge-base --is-ancestor "$remote_head" "$published_ref"; then
    ai_fail "Contributor commit $remote_head is already contained in published main."
  fi
  merge_base="$(git -C "$ROOT_DIR" merge-base "$published_ref" "$remote_head" 2>/dev/null || true)"
  [[ -n "$merge_base" ]] || ai_fail "Contributor branch has no shared history with published main."

  path="$(ai_require_slot "$slot")"
  ai_require_no_git_operation "$path"
  ai_require_clean "$path" "Workspace $slot"
  [[ "$(ai_current_branch "$path")" == DETACHED ]] \
    || ai_fail "Workspace $slot is occupied. Choose an idle detached slot."
  detached_head="$(ai_head "$path")"
  git -C "$path" merge-base --is-ancestor "$detached_head" "$published_ref" \
    || ai_fail "Workspace $slot has detached work outside published main. Rescue it before contributor collection."
  ai_require_idle_slot_state "$slot" "$path" "$published_ref"

  git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$branch" \
    && ai_fail "Local branch '$branch' already exists. Inspect it before collecting the remote handoff."
  [[ -z "$(ai_branch_worktree "$branch" || true)" ]] \
    || ai_fail "Branch '$branch' is already checked out in another worktree."

  git -C "$path" switch -c "$branch" "$remote_ref"
  [[ "$(ai_head "$path")" == "$remote_head" ]] \
    || ai_fail "Collected branch does not match the verified contributor commit."
  ai_write_state "$slot" READY "$path" "$branch" "$remote_head"
  ai_generate_workspace_guide "$slot" "$path" READY "$branch" "$remote_head"

  printf 'Collected contributor handoff into %s for read-only review and tests.\n' "$slot"
  printf '  Branch: %s\n' "$branch"
  printf '  Commit: %s\n' "$remote_head"
  printf '  Merge base: %s\n' "$merge_base"
  git -C "$path" log --oneline --decorate "$published_ref..$branch"
  git -C "$path" diff --stat "$published_ref...$branch"
  printf 'No merge was performed. Inspect and test in %s, then deliberately run ai-manager.sh merge %s from the manager checkout.\n' "$path" "$branch"
}

manager_merge() {
  local branch path slot phase state_head branch_head remote_ref

  [[ $# -ge 1 ]] || ai_fail "merge requires a topic branch."
  branch="$1"
  shift
  [[ $# -eq 0 ]] || ai_fail "merge accepts exactly one topic branch."
  [[ "$branch" != "$AI_MAIN_BRANCH" ]] || ai_fail "Refusing to merge $AI_MAIN_BRANCH into itself."

  ai_require_manager
  git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$branch" \
    || ai_fail "Local branch does not exist: $branch"
  path="$(ai_branch_worktree "$branch" || true)"
  [[ -n "$path" ]] || ai_fail "Branch '$branch' is not checked out in a managed workspace."
  ai_require_no_git_operation "$path"
  ai_require_clean "$path" "Handoff branch $branch"
  slot="$(ai_current_slot_for_path "$path" || true)"
  [[ -n "$slot" ]] || ai_fail "Branch '$branch' is not in a neutral ai-N slot."
  ai_load_slot_state "$slot" "$path" \
    || ai_fail "$slot changed after handoff or has ambiguous registration: $AI_SLOT_STATE_ERROR. Ask the worker to hand off the exact current tip."
  phase="$AI_SLOT_STATE_PHASE"
  state_head="$AI_SLOT_STATE_HEAD"
  branch_head="$(ai_head "$path")"
  [[ "$phase" == READY ]] || ai_fail "$slot has not been handed off (phase=${phase:-UNKNOWN})."
  [[ "$AI_SLOT_STATE_BRANCH" == "$branch" ]] \
    || ai_fail "$slot state belongs to $AI_SLOT_STATE_BRANCH, not $branch."
  [[ "$state_head" == "$branch_head" ]] \
    || ai_fail "$slot changed after handoff. Ask the worker to hand off the new tip."
  ai_remote_branch_is_exact "$path" "$branch" \
    || ai_fail "$AI_REMOTE/$branch is missing or does not match the handed-off commit."

  remote_ref="$AI_REMOTE/$branch"
  printf 'Merging READY handoff %s from %s\n' "$branch" "$slot"
  git -C "$ROOT_DIR" log --oneline --decorate "$AI_MAIN_BRANCH..$branch"
  git -C "$ROOT_DIR" diff --stat "$AI_MAIN_BRANCH...$branch"
  if git -C "$ROOT_DIR" merge-base --is-ancestor "$branch" "$AI_MAIN_BRANCH"; then
    printf '%s is already merged into %s.\n' "$branch" "$AI_MAIN_BRANCH"
  else
    git -C "$ROOT_DIR" merge --no-ff "$branch" -m "Merge $branch"
  fi

  printf 'Merged locally. Run relevant tests, then push %s before recycling %s.\n' "$AI_MAIN_BRANCH" "$slot"
  printf 'Durable handoff ref: %s (%s)\n' "$remote_ref" "$branch_head"
}

manager_release_check() {
  local stash_count path path_real slot branch phase remote_state problems=0
  local primary_real live_real branch_path operation published_ref head

  ai_require_manager
  stash_count="$(git -C "$ROOT_DIR" stash list | wc -l | tr -d ' ')"
  if ((stash_count > 0)); then
    ai_warn "$stash_count Git stash(es) remain. Rescue or deliberately resolve them before release."
    problems=1
  fi

  primary_real="$(ai_realpath "$AI_PRIMARY_ROOT")"
  live_real="$(ai_realpath "$AI_LIVE_ROOT" 2>/dev/null || true)"
  published_ref="$(ai_published_ref)"

  while IFS= read -r path; do
    path_real="$(ai_realpath "$path" 2>/dev/null || true)"
    if [[ -z "$path_real" ]]; then
      ai_warn "Git records a missing or prunable worktree at $path. Inspect it before release."
      problems=1
      continue
    fi
    [[ "$path_real" == "$primary_real" ]] && continue

    operation="$(ai_git_operation "$path" || true)"
    if [[ -n "$operation" ]]; then
      ai_warn "Worktree at $path has an unfinished Git operation ($operation)."
      problems=1
      continue
    fi
    if ! ai_is_clean "$path"; then
      ai_warn "Worktree is dirty at $path."
      problems=1
      continue
    fi

    branch="$(ai_current_branch "$path")"
    head="$(ai_head "$path")"
    if [[ -n "$live_real" && "$path_real" == "$live_real" ]]; then
      if [[ "$branch" != DETACHED ]]; then
        ai_warn "Live worktree is attached to branch $branch instead of detached."
        problems=1
      elif ! git -C "$path" merge-base --is-ancestor "$head" "$published_ref"; then
        ai_warn "Live worktree commit $head is not published-main history."
        problems=1
      fi
      continue
    fi

    slot="$(ai_current_slot_for_path "$path" || true)"
    if [[ -z "$slot" ]]; then
      ai_warn "Unmanaged worktree remains at $path."
      problems=1
      continue
    fi

    if ! ai_load_slot_state "$slot" "$path"; then
      ai_warn "$slot has ambiguous registration: $AI_SLOT_STATE_ERROR."
      problems=1
      continue
    fi
    phase="$AI_SLOT_STATE_PHASE"

    if [[ "$branch" == DETACHED ]]; then
      if [[ "$phase" != IDLE ]]; then
        ai_warn "$slot is detached but registered as $phase, not IDLE."
        problems=1
      fi
      if ! git -C "$path" merge-base --is-ancestor "$head" "$published_ref"; then
        ai_warn "$slot has detached commit $head outside published main. Run ai-manager.sh rescue $slot."
        problems=1
      fi
      continue
    fi

    if [[ "$phase" != ACTIVE && "$phase" != READY ]]; then
      ai_warn "$slot is on branch $branch but registered as $phase."
      problems=1
    fi
    remote_state=MISSING
    ai_remote_branch_is_exact "$path" "$branch" && remote_state=BACKED-UP
    if [[ "$remote_state" != BACKED-UP ]]; then
      ai_warn "$slot branch $branch is not backed up at its exact tip."
      problems=1
    fi
  done < <(ai_list_worktree_paths)

  while IFS= read -r branch; do
    [[ -n "$branch" && "$branch" != "$AI_MAIN_BRANCH" ]] || continue
    branch_path="$(ai_branch_worktree "$branch" || true)"
    if [[ -z "$branch_path" ]]; then
      ai_warn "Unmanaged local branch remains without a worker slot: $branch"
      problems=1
    fi
  done < <(git -C "$ROOT_DIR" for-each-ref '--format=%(refname:short)' refs/heads)

  ((problems == 0)) || ai_fail "Release readiness checks failed. No files were changed."
  printf 'PASS: clean published %s; every worker is clean and durably backed up.\n' "$AI_MAIN_BRANCH"
  printf 'LIVE GATE: release preparation and publication do not authorize a public-server shutdown. Keep it running until the user explicitly authorizes this maintenance window, then use the in-game ::update warning and wait for its countdown.\n'
}

manager_release() {
  local argument version="" previous=""

  for argument in "$@"; do
    [[ "$argument" != --skip-build ]] \
      || ai_fail "Manager releases cannot use --skip-build; release provenance requires a fresh client build."
    if [[ "$previous" == --version ]]; then
      version="$argument"
    fi
    previous="$argument"
  done
  [[ -n "$version" ]] \
    || ai_fail "Manager releases require --version so the layered-world artifact can share exact provenance."
  manager_release_check
  "$SCRIPT_ROOT/scripts/package-player-release.sh" "$@"
  "$SCRIPT_ROOT/scripts/package-layered-world-release.sh" --version "$version"
}

# Resolve a path back to its registered neutral slot without changing ROOT_DIR.
ai_current_slot_for_path() {
  local wanted state_path state_path_value
  wanted="$(ai_realpath "$1")"
  [[ -d "$AI_STATE_DIR" ]] || return 1
  for state_path in "$AI_STATE_DIR"/ai-*.state; do
    [[ -f "$state_path" ]] || continue
    state_path_value="$(sed -n 's/^path=//p' "$state_path" | head -n 1)"
    [[ -n "$state_path_value" ]] || continue
    state_path_value="$(ai_realpath "$state_path_value" 2>/dev/null || true)"
    if [[ "$state_path_value" == "$wanted" ]]; then
      sed -n 's/^slot=//p' "$state_path" | head -n 1
      return 0
    fi
  done
  return 1
}

command="${1:-}"
if [[ -z "$command" || "$command" == -h || "$command" == --help ]]; then
  usage
  exit 0
fi
shift

case "$command" in
  status)
    [[ $# -eq 0 ]] || ai_fail "status takes no arguments."
    ai_status
    ;;
  rescue)
    manager_rescue "$@"
    ;;
  collect-contributor)
    manager_collect_contributor "$@"
    ;;
  merge)
    manager_merge "$@"
    ;;
  release-check)
    [[ $# -eq 0 ]] || ai_fail "release-check takes no arguments."
    manager_release_check
    ;;
  release)
    [[ $# -gt 0 ]] || ai_fail "release requires package-player-release options; run that script with --help for details."
    manager_release "$@"
    ;;
  *)
    ai_fail "Unknown command '$command'. Run --help for usage."
    ;;
esac
