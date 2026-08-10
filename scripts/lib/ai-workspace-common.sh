#!/usr/bin/env bash

# Shared helpers for the neutral AI worktree workflow. Callers are expected to
# enable `set -euo pipefail` before sourcing this file.

AI_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_INFERRED_ROOT="$(cd "$AI_COMMON_DIR/../.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$AI_INFERRED_ROOT}"

ai_fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

ai_warn() {
  printf 'WARN: %s\n' "$*" >&2
}

ai_require_command() {
  command -v "$1" >/dev/null 2>&1 || ai_fail "Missing required command: $1"
}

ai_require_repository() {
  git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || ai_fail "Not a Git worktree: $ROOT_DIR"
}

ai_realpath() {
  local path="$1"
  (cd "$path" 2>/dev/null && pwd -P)
}

ai_primary_worktree() {
  git -C "$ROOT_DIR" worktree list --porcelain \
    | sed -n 's/^worktree //p' \
    | sed -n '1p'
}

ai_initialize_context() {
  local configured_remote

  ai_require_command git
  ai_require_repository

  AI_PRIMARY_ROOT="$(ai_primary_worktree)"
  [[ -n "$AI_PRIMARY_ROOT" ]] || ai_fail "Unable to determine the primary Git worktree."
  AI_PRIMARY_ROOT="$(ai_realpath "$AI_PRIMARY_ROOT")"
  AI_PROJECT_NAME="${AI_PROJECT_NAME:-$(basename "$AI_PRIMARY_ROOT")}"
  AI_WORKSPACE_PARENT="${AI_WORKSPACE_PARENT:-${WORKSPACE_PARENT:-$(dirname "$AI_PRIMARY_ROOT")}}"
  AI_MAIN_BRANCH="${AI_MAIN_BRANCH:-main}"

  configured_remote="$(git -C "$ROOT_DIR" config --get "branch.${AI_MAIN_BRANCH}.remote" 2>/dev/null || true)"
  if [[ -z "$configured_remote" || "$configured_remote" == "." ]]; then
    configured_remote="spoiled-milk"
  fi
  AI_REMOTE="${AI_REMOTE:-$configured_remote}"
  AI_LIVE_ROOT="/tmp/spoiled-milk-live-main"

  AI_COMMON_GIT_DIR="$(git -C "$ROOT_DIR" rev-parse --git-common-dir)"
  if [[ "$AI_COMMON_GIT_DIR" != /* ]]; then
    AI_COMMON_GIT_DIR="$(cd "$ROOT_DIR/$AI_COMMON_GIT_DIR" && pwd -P)"
  fi
  AI_STATE_DIR="$AI_COMMON_GIT_DIR/ai-workspaces"

  ai_require_invocation_from_managed_worktree
}

ai_require_invocation_from_managed_worktree() {
  local caller candidate candidate_real

  caller="$(pwd -P)"
  while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    candidate_real="$(ai_realpath "$candidate" 2>/dev/null || true)"
    [[ -n "$candidate_real" ]] || continue
    if [[ "$caller" == "$candidate_real" || "$caller" == "$candidate_real"/* ]]; then
      return 0
    fi
  done < <(git -C "$ROOT_DIR" worktree list --porcelain | sed -n 's/^worktree //p')

  ai_fail "Refusing cross-project invocation from $caller. Run this workflow only inside a registered $AI_PROJECT_NAME worktree."
}

ai_normalize_slot() {
  local slot="${1:-}"

  [[ -n "$slot" ]] || ai_fail "A workspace slot is required (for example, ai-1)."
  if [[ "$slot" =~ ^[1-9][0-9]*$ ]]; then
    slot="ai-$slot"
  fi
  [[ "$slot" =~ ^ai-[1-9][0-9]*$ ]] \
    || ai_fail "Invalid workspace slot '$slot'; use ai-1, ai-2, and so on."
  printf '%s\n' "$slot"
}

ai_slot_path() {
  local slot
  slot="$(ai_normalize_slot "$1")"
  printf '%s/%s-%s\n' "$AI_WORKSPACE_PARENT" "$AI_PROJECT_NAME" "$slot"
}

ai_state_path() {
  local slot
  slot="$(ai_normalize_slot "$1")"
  printf '%s/%s.state\n' "$AI_STATE_DIR" "$slot"
}

ai_state_get() {
  local slot="$1"
  local key="$2"
  local state_path

  state_path="$(ai_state_path "$slot")"
  [[ -f "$state_path" ]] || return 0
  sed -n "s/^${key}=//p" "$state_path" | head -n 1
}

ai_write_state() {
  local slot="$1"
  local phase="$2"
  local path="$3"
  local branch="$4"
  local head="$5"
  local remote_policy="${6:--}"
  local state_path temp_path

  mkdir -p "$AI_STATE_DIR"
  state_path="$(ai_state_path "$slot")"
  temp_path="${state_path}.tmp.$$"
  {
    printf 'slot=%s\n' "$slot"
    printf 'phase=%s\n' "$phase"
    printf 'path=%s\n' "$path"
    printf 'branch=%s\n' "$branch"
    printf 'head=%s\n' "$head"
    printf 'remote_policy=%s\n' "$remote_policy"
    printf 'updated=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  } > "$temp_path"
  mv "$temp_path" "$state_path"
}

ai_current_branch() {
  local path="$1"
  git -C "$path" symbolic-ref --quiet --short HEAD 2>/dev/null || printf 'DETACHED\n'
}

ai_head() {
  git -C "$1" rev-parse HEAD
}

ai_short_head() {
  git -C "$1" rev-parse --short HEAD
}

ai_dirty_status() {
  local path="$1"
  local output

  if ! output="$(git -C "$path" status --porcelain=v1 --untracked-files=all 2>&1)"; then
    printf 'FAIL: Unable to inspect Git status at %s:\n%s\n' "$path" "$output" >&2
    return 1
  fi
  printf '%s' "$output"
}

ai_is_clean() {
  local status

  if ! status="$(ai_dirty_status "$1")"; then
    ai_fail "Git status inspection failed; refusing to classify $1 as clean."
  fi
  [[ -z "$status" ]]
}

ai_require_clean() {
  local path="$1"
  local label="${2:-worktree}"
  local status

  if ! status="$(ai_dirty_status "$path")"; then
    ai_fail "Git status inspection failed; refusing to continue with $path."
  fi
  [[ -z "$status" ]] || ai_fail "$label is dirty at $path. Checkpoint or rescue it before continuing:
$status"
}

ai_git_operation() {
  local path="$1"
  local git_path

  for git_path in MERGE_HEAD CHERRY_PICK_HEAD REVERT_HEAD rebase-merge rebase-apply; do
    git_path="$(git -C "$path" rev-parse --git-path "$git_path")"
    if [[ -e "$git_path" ]]; then
      printf '%s\n' "$(basename "$git_path")"
      return 0
    fi
  done
  return 1
}

ai_require_no_git_operation() {
  local path="$1"
  local operation

  operation="$(ai_git_operation "$path" || true)"
  [[ -z "$operation" ]] || ai_fail "An unfinished Git operation ($operation) exists in $path. Resolve it first."
}

ai_published_ref() {
  local candidate="$AI_REMOTE/$AI_MAIN_BRANCH"

  git -C "$ROOT_DIR" rev-parse --verify --quiet "refs/remotes/$candidate^{commit}" >/dev/null \
    || return 1
  printf '%s\n' "$candidate"
}

ai_fetch_remote() {
  git -C "$ROOT_DIR" fetch --prune "$AI_REMOTE" \
    || ai_fail "Unable to refresh $AI_REMOTE. Publication-sensitive manager actions require a successful fetch."
}

ai_require_manager_identity() {
  local root_real primary_real

  root_real="$(ai_realpath "$ROOT_DIR")"
  primary_real="$(ai_realpath "$AI_PRIMARY_ROOT")"
  [[ "$root_real" == "$primary_real" ]] \
    || ai_fail "Run this manager operation from the primary checkout: $AI_PRIMARY_ROOT"
}

ai_require_manager() {
  local branch published_ref head published_head

  ai_require_manager_identity
  branch="$(ai_current_branch "$ROOT_DIR")"
  [[ "$branch" == "$AI_MAIN_BRANCH" ]] \
    || ai_fail "Manager operations require branch '$AI_MAIN_BRANCH'; found '$branch'."
  ai_require_no_git_operation "$ROOT_DIR"
  ai_require_clean "$ROOT_DIR" "Manager checkout"

  ai_fetch_remote

  published_ref="$(ai_published_ref || true)"
  [[ -n "$published_ref" ]] \
    || ai_fail "Unable to resolve a published $AI_MAIN_BRANCH remote ref. Fetch $AI_REMOTE first."
  head="$(ai_head "$ROOT_DIR")"
  published_head="$(git -C "$ROOT_DIR" rev-parse "$published_ref^{commit}")"
  [[ "$head" == "$published_head" ]] \
    || ai_fail "Manager $AI_MAIN_BRANCH is not at published $published_ref ($published_head); found $head. Pull, push, or reconcile it first."
}

ai_load_slot_state() {
  local slot="$1"
  local path="$2"
  local state_path stored_slot stored_phase stored_path stored_branch stored_head stored_remote_policy
  local stored_path_real actual_path_real actual_branch actual_head

  AI_SLOT_STATE_ERROR=""
  AI_SLOT_STATE_PHASE=""
  AI_SLOT_STATE_BRANCH=""
  AI_SLOT_STATE_HEAD=""
  AI_SLOT_STATE_REMOTE_POLICY=""

  state_path="$(ai_state_path "$slot")"
  if [[ ! -f "$state_path" ]]; then
    AI_SLOT_STATE_ERROR="missing state file $state_path"
    return 1
  fi

  stored_slot="$(ai_state_get "$slot" slot)"
  stored_phase="$(ai_state_get "$slot" phase)"
  stored_path="$(ai_state_get "$slot" path)"
  stored_branch="$(ai_state_get "$slot" branch)"
  stored_head="$(ai_state_get "$slot" head)"
  stored_remote_policy="$(ai_state_get "$slot" remote_policy)"

  [[ "$stored_slot" == "$slot" ]] || {
    AI_SLOT_STATE_ERROR="state records slot '${stored_slot:-missing}', expected '$slot'"
    return 1
  }
  [[ "$stored_phase" =~ ^(IDLE|ACTIVE|READY|QUARANTINED|RECYCLING)$ ]] || {
    AI_SLOT_STATE_ERROR="state has unknown phase '${stored_phase:-missing}'"
    return 1
  }
  [[ -n "$stored_path" ]] || {
    AI_SLOT_STATE_ERROR="state is missing its path"
    return 1
  }
  stored_path_real="$(ai_realpath "$stored_path" 2>/dev/null || true)"
  actual_path_real="$(ai_realpath "$path" 2>/dev/null || true)"
  [[ -n "$stored_path_real" && "$stored_path_real" == "$actual_path_real" ]] || {
    AI_SLOT_STATE_ERROR="state path '${stored_path:-missing}' does not match '$path'"
    return 1
  }

  actual_branch="$(ai_current_branch "$path")"
  actual_head="$(ai_head "$path" 2>/dev/null)" || {
    AI_SLOT_STATE_ERROR="unable to resolve the current Git commit"
    return 1
  }
  [[ "$stored_branch" == "$actual_branch" ]] || {
    AI_SLOT_STATE_ERROR="state branch '${stored_branch:-missing}' does not match '$actual_branch'"
    return 1
  }
  [[ "$stored_head" == "$actual_head" ]] || {
    AI_SLOT_STATE_ERROR="state head '${stored_head:-missing}' does not match '$actual_head'"
    return 1
  }
  if [[ "$stored_phase" == RECYCLING && ! "$stored_remote_policy" =~ ^(DELETE|KEEP)$ ]]; then
    AI_SLOT_STATE_ERROR="RECYCLING state has invalid remote policy '${stored_remote_policy:-missing}'"
    return 1
  fi

  AI_SLOT_STATE_PHASE="$stored_phase"
  AI_SLOT_STATE_BRANCH="$stored_branch"
  AI_SLOT_STATE_HEAD="$stored_head"
  AI_SLOT_STATE_REMOTE_POLICY="$stored_remote_policy"
  return 0
}

ai_require_slot_state() {
  local slot="$1"
  local path="$2"

  ai_load_slot_state "$slot" "$path" \
    || ai_fail "Workspace $slot has ambiguous registration: $AI_SLOT_STATE_ERROR. Rescue or deliberately repair it before continuing."
}

ai_require_idle_slot_state() {
  local slot="$1"
  local path="$2"
  local published_ref="$3"
  local state_path actual_head

  state_path="$(ai_state_path "$slot")"
  if ai_load_slot_state "$slot" "$path"; then
    [[ "$AI_SLOT_STATE_PHASE" == IDLE && "$AI_SLOT_STATE_BRANCH" == DETACHED ]] \
      || ai_fail "Workspace $slot is registered as $AI_SLOT_STATE_PHASE on $AI_SLOT_STATE_BRANCH, not IDLE. Handoff, rescue, or recycle its current task first."
    return 0
  fi

  # Losing an IDLE metadata file should not strand a safe neutral folder. Only
  # reconstruct state when there is no old record to overwrite and Git proves
  # the detached commit already belongs to published main.
  if [[ ! -f "$state_path" ]] \
      && [[ "$(ai_current_branch "$path")" == DETACHED ]] \
      && ai_is_clean "$path" \
      && git -C "$path" merge-base --is-ancestor HEAD "$published_ref"; then
    actual_head="$(ai_head "$path")"
    ai_warn "Reconstructing missing IDLE metadata for safe detached workspace $slot."
    ai_write_state "$slot" IDLE "$path" DETACHED "$actual_head"
    ai_generate_workspace_guide "$slot" "$path" IDLE DETACHED "$actual_head"
    ai_load_slot_state "$slot" "$path" || ai_fail "Unable to reconstruct state for $slot."
    return 0
  fi

  if [[ -f "$state_path" ]]; then
    local recorded_phase recorded_branch
    recorded_phase="$(ai_state_get "$slot" phase)"
    recorded_branch="$(ai_state_get "$slot" branch)"
    if [[ -n "$recorded_branch" && "$recorded_branch" != DETACHED ]]; then
      ai_fail "Workspace $slot was manually detached from registered $recorded_phase branch '$recorded_branch'. Reattach that branch in $path, then handoff or recycle it; do not overwrite its state."
    fi
  fi

  ai_fail "Workspace $slot has ambiguous registration: $AI_SLOT_STATE_ERROR. Run ai-manager.sh rescue $slot before reuse."
}

ai_require_slot() {
  local slot="$1"
  local path slot_git_dir

  path="$(ai_slot_path "$slot")"
  [[ -e "$path" ]] || ai_fail "Workspace $slot does not exist at $path. Run 'ai-workspace.sh create $slot'."
  git -C "$path" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || ai_fail "Workspace path is not a registered Git worktree: $path"
  slot_git_dir="$(git -C "$path" rev-parse --git-common-dir)"
  if [[ "$slot_git_dir" != /* ]]; then
    slot_git_dir="$(cd "$path/$slot_git_dir" && pwd -P)"
  fi
  [[ "$slot_git_dir" == "$AI_COMMON_GIT_DIR" ]] \
    || ai_fail "Workspace path belongs to a different Git repository: $path"
  printf '%s\n' "$path"
}

ai_current_slot() {
  local root_real state_path state_real slot base suffix

  root_real="$(ai_realpath "$ROOT_DIR")"
  if [[ -d "$AI_STATE_DIR" ]]; then
    for state_path in "$AI_STATE_DIR"/ai-*.state; do
      [[ -f "$state_path" ]] || continue
      state_real="$(sed -n 's/^path=//p' "$state_path" | head -n 1)"
      [[ -n "$state_real" ]] || continue
      state_real="$(ai_realpath "$state_real" 2>/dev/null || true)"
      if [[ "$state_real" == "$root_real" ]]; then
        sed -n 's/^slot=//p' "$state_path" | head -n 1
        return 0
      fi
    done
  fi

  base="$(basename "$root_real")"
  suffix="${base#"$AI_PROJECT_NAME-"}"
  if [[ "$suffix" =~ ^ai-[1-9][0-9]*$ ]]; then
    printf '%s\n' "$suffix"
    return 0
  fi
  return 1
}

ai_branch_worktree() {
  local wanted="$1"
  local path branch

  while IFS= read -r path; do
    branch="$(ai_current_branch "$path")"
    if [[ "$branch" == "$wanted" ]]; then
      printf '%s\n' "$path"
      return 0
    fi
  done < <(git -C "$ROOT_DIR" worktree list --porcelain | sed -n 's/^worktree //p')
  return 1
}

ai_remote_branch_ref() {
  printf 'refs/remotes/%s/%s\n' "$AI_REMOTE" "$1"
}

ai_remote_branch_is_exact() {
  local path="$1"
  local branch="$2"
  local ref local_head remote_head

  ref="$(ai_remote_branch_ref "$branch")"
  git -C "$path" rev-parse --verify --quiet "$ref^{commit}" >/dev/null || return 1
  local_head="$(ai_head "$path")"
  remote_head="$(git -C "$path" rev-parse "$ref^{commit}")"
  [[ "$local_head" == "$remote_head" ]]
}

ai_push_branch() {
  local path="$1"
  local branch="$2"

  git -C "$path" push --set-upstream "$AI_REMOTE" "HEAD:refs/heads/$branch"
  ai_remote_branch_is_exact "$path" "$branch" \
    || ai_fail "Push completed but $AI_REMOTE/$branch does not match the local tip."
}

ai_generate_workspace_guide() {
  local slot="$1"
  local path="$2"
  local phase="$3"
  local branch="$4"
  local head="$5"

  {
    printf '# AI Workspace %s\n\n' "$slot"
    printf 'This folder is a neutral AI worktree slot. Its current state is:\n\n'
    printf -- '- Phase: `%s`\n' "$phase"
    printf -- '- Branch: `%s`\n' "$branch"
    printf -- '- Checkpoint: `%s`\n\n' "$head"
    printf 'Project boundary: this slot belongs only to Spoiled Milk/Core-Framework. Never accept World Editor, World Builder, runtime-provider, or provider-lock work here. Those tasks belong to `/home/justin/rsc-world-editor` or `/home/justin/rsc-world-editor-runtime` and their independent workers.\n\n'
    if [[ "$phase" == IDLE ]]; then
      printf 'This slot is idle. Do not edit files while HEAD is detached. From the manager checkout, assign a focused topic branch first:\n\n'
      printf '```bash\n./scripts/ai-workspace.sh start %s TYPE/short-task-name\n```\n' "$slot"
    elif [[ "$phase" == QUARANTINED ]]; then
      printf 'Checkpointing stopped because staged files looked sensitive or too large for an ordinary remote push. The files remain staged locally. Review them, remove anything unsafe, then checkpoint again. Use the printed override only after deliberate review.\n'
    elif [[ "$phase" == RECYCLING ]]; then
      printf 'Recycling was interrupted. Do not edit this slot. From the manager checkout, rerun ai-workspace.sh recycle %s to finish the verified cleanup.\n' "$slot"
    else
      printf 'Work only on the assigned branch and task. Do not switch branches, use Git stashes, run the public hosted server, or alter another worktree.\n\n'
      printf 'After meaningful progress, preserve it with:\n\n'
      printf '```bash\n./scripts/ai-workspace.sh checkpoint -m "Describe the checkpoint"\n```\n\n'
      printf 'Before ending the session, make the exact pushed commit available to the manager with:\n\n'
      printf '```bash\n./scripts/ai-workspace.sh handoff -m "Describe the handoff"\n```\n'
    fi
  } > "$path/AI_WORKSPACE.md"
}

ai_path_looks_sensitive() {
  local path="${1,,}"
  local base="${path##*/}"

  case "$base" in
    .env.example|.env.sample|.env.template)
      return 1
      ;;
    .env|.env.*|id_rsa|id_ed25519|credentials.txt|credentials.json|secrets.txt|secrets.json|*.pem|*.p12|*.pfx|*.jks)
      return 0
      ;;
  esac
  return 1
}

ai_index_blob_looks_sensitive() {
  local path="$1"
  local file="$2"

  git -C "$path" show ":$file" 2>/dev/null \
    | LC_ALL=C grep -aE -- \
      '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}' \
      >/dev/null
  return ${PIPESTATUS[1]}
}

ai_review_checkpoint_index() {
  local path="$1"
  local allow_sensitive="$2"
  local allow_large="$3"
  local max_bytes="${AI_MAX_CHECKPOINT_BLOB_BYTES:-99614720}"
  local file size problems=0

  [[ "$max_bytes" =~ ^[1-9][0-9]*$ ]] \
    || ai_fail "AI_MAX_CHECKPOINT_BLOB_BYTES must be a positive integer."

  printf 'Staged checkpoint files:\n'
  git -C "$path" diff --cached --name-status | sed 's/^/  /'
  while IFS= read -r -d '' file; do
    size="$(git -C "$path" cat-file -s ":$file" 2>/dev/null || true)"
    [[ "$size" =~ ^[0-9]+$ ]] || continue
    printf '  %s bytes  %q\n' "$size" "$file"
    if [[ "$allow_sensitive" != true ]] && ai_path_looks_sensitive "$file"; then
      printf 'BLOCKED: likely sensitive path %q. Remove it or rerun with --allow-sensitive after deliberate review.\n' "$file" >&2
      problems=1
    fi
    if [[ "$allow_sensitive" != true ]] \
        && ((size <= 1048576)) \
        && ai_index_blob_looks_sensitive "$path" "$file"; then
      printf 'BLOCKED: %q contains a likely credential signature. Remove it or rerun with --allow-sensitive after deliberate review.\n' "$file" >&2
      problems=1
    fi
    if [[ "$allow_large" != true ]] && ((size > max_bytes)); then
      printf 'BLOCKED: %q is %s bytes (limit %s). Use Git LFS, remove it, or rerun with --allow-large after deliberate review.\n' "$file" "$size" "$max_bytes" >&2
      problems=1
    fi
  done < <(git -C "$path" diff --cached --name-only --diff-filter=ACMR -z)

  ((problems == 0))
}

ai_create_slot() {
  local requested="$1"
  local slot path base_ref head

  slot="$(ai_normalize_slot "$requested")"
  path="$(ai_slot_path "$slot")"
  [[ ! -e "$path" ]] || ai_fail "Workspace path already exists: $path"

  base_ref="$AI_MAIN_BRANCH"
  git -C "$ROOT_DIR" rev-parse --verify --quiet "$base_ref^{commit}" >/dev/null \
    || ai_fail "Missing local base branch: $base_ref"

  printf 'Creating neutral AI workspace %s\n' "$slot"
  printf '  Path: %s\n' "$path"
  printf '  Base: %s\n' "$base_ref"
  git -C "$ROOT_DIR" worktree add --detach "$path" "$base_ref"
  head="$(ai_head "$path")"
  ai_write_state "$slot" IDLE "$path" DETACHED "$head"
  ai_generate_workspace_guide "$slot" "$path" IDLE DETACHED "$head"
  printf 'Workspace %s is ready and idle.\n' "$slot"
}

ai_checkpoint_path() {
  local path="$1"
  local slot="$2"
  local phase="$3"
  local message="$4"
  local push_branch="$5"
  local allow_sensitive="${6:-false}"
  local allow_large="${7:-false}"
  local branch head

  branch="$(ai_current_branch "$path")"
  [[ "$branch" != DETACHED ]] || ai_fail "Workspace $slot is detached. Start a task before checkpointing."
  [[ "$branch" != "$AI_MAIN_BRANCH" ]] || ai_fail "Worker checkpoints are forbidden on $AI_MAIN_BRANCH."
  ai_require_no_git_operation "$path"

  git -C "$path" add -A
  if ! git -C "$path" diff --cached --quiet; then
    git -C "$path" diff --cached --check
    if ! ai_review_checkpoint_index "$path" "$allow_sensitive" "$allow_large"; then
      head="$(ai_head "$path")"
      ai_write_state "$slot" QUARANTINED "$path" "$branch" "$head"
      ai_generate_workspace_guide "$slot" "$path" QUARANTINED "$branch" "$head"
      ai_fail "Checkpoint quarantined in $slot. Nothing was committed or pushed; reviewed files remain staged locally."
    fi
    git -C "$path" commit -m "$message"
  else
    printf 'No file changes to commit in %s.\n' "$slot"
  fi

  ai_require_clean "$path" "Workspace $slot"
  head="$(ai_head "$path")"
  if [[ "$push_branch" == true ]]; then
    ai_push_branch "$path" "$branch"
  fi

  ai_write_state "$slot" "$phase" "$path" "$branch" "$head"
  ai_generate_workspace_guide "$slot" "$path" "$phase" "$branch" "$head"
  printf '%s %s at %s (%s).\n' "$slot" "$phase" "$head" "$branch"
}

ai_list_worktree_paths() {
  git -C "$ROOT_DIR" worktree list --porcelain | sed -n 's/^worktree //p'
}

ai_status() {
  local published_ref published_head stash_count path path_real role branch head short_head
  local dirty_count behind ahead merged remote_state slot phase state_flag live_real primary_real
  local orphan_count=0 candidate candidate_path candidate_head candidate_remote_head
  local remote_count=0 remote_candidate remote_branch local_state

  published_ref="$(ai_published_ref || true)"
  if [[ -n "$published_ref" ]]; then
    published_head="$(git -C "$ROOT_DIR" rev-parse "$published_ref^{commit}")"
  else
    published_head="unknown"
  fi
  primary_real="$(ai_realpath "$AI_PRIMARY_ROOT")"
  live_real="$(ai_realpath "$AI_LIVE_ROOT" 2>/dev/null || true)"

  printf 'AI workspace status\n'
  printf '  Primary:   %s\n' "$AI_PRIMARY_ROOT"
  printf '  Published: %s %s\n' "${published_ref:-missing}" "$published_head"
  printf '  Remote:    %s\n' "$AI_REMOTE"

  stash_count="$(git -C "$ROOT_DIR" stash list | wc -l | tr -d ' ')"
  printf '  Stashes:   %s\n\n' "$stash_count"
  if ((stash_count > 0)); then
    git -C "$ROOT_DIR" stash list | sed 's/^/    WARN: /'
    printf '\n'
  fi

  while IFS= read -r path; do
    path_real="$(ai_realpath "$path" 2>/dev/null || true)"
    if [[ -z "$path_real" ]]; then
      printf '[STALE/PRUNABLE] %s\n' "$path"
      printf '  Git still records this worktree, but its directory cannot be inspected. Run git worktree prune only after confirming no recoverable files remain.\n'
      continue
    fi
    branch="$(ai_current_branch "$path")"
    head="$(ai_head "$path")"
    short_head="$(ai_short_head "$path")"
    dirty_count="$(ai_dirty_status "$path" | wc -l | tr -d ' ')"
    role=OTHER
    slot=""
    phase="-"
    state_flag=""

    if [[ "$path_real" == "$primary_real" ]]; then
      role=MANAGER
    elif [[ -n "$live_real" && "$path_real" == "$live_real" ]]; then
      role=LIVE
    else
      slot="$(basename "$path_real")"
      slot="${slot#"$AI_PROJECT_NAME-"}"
      if [[ "$slot" =~ ^ai-[1-9][0-9]*$ ]]; then
        role=AI-SLOT
        if ai_load_slot_state "$slot" "$path"; then
          phase="$AI_SLOT_STATE_PHASE"
        else
          phase="UNKNOWN"
          state_flag=" STATE-STALE($AI_SLOT_STATE_ERROR)"
        fi
      fi
    fi

    behind="?"
    ahead="?"
    merged="?"
    if [[ -n "$published_ref" ]]; then
      read -r behind ahead < <(git -C "$path" rev-list --left-right --count "$published_ref...HEAD")
      if git -C "$path" merge-base --is-ancestor HEAD "$published_ref"; then
        merged=yes
      else
        merged=no
      fi
    fi

    remote_state="-"
    if [[ "$branch" != DETACHED && "$branch" != "$AI_MAIN_BRANCH" ]]; then
      if ai_remote_branch_is_exact "$path" "$branch"; then
        remote_state=BACKED-UP
      elif git -C "$path" rev-parse --verify --quiet "$(ai_remote_branch_ref "$branch")^{commit}" >/dev/null; then
        remote_state=OUT-OF-DATE
      else
        remote_state=MISSING
      fi
    fi

    printf '[%s] %s\n' "$role" "$path"
    printf '  branch=%s head=%s dirty=%s phase=%s%s\n' "$branch" "$short_head" "$dirty_count" "$phase" "$state_flag"
    printf '  vs-published: ahead=%s behind=%s merged=%s remote=%s\n' "$ahead" "$behind" "$merged" "$remote_state"
  done < <(ai_list_worktree_paths)

  printf '\nLocal topic branches without a worktree:\n'
  while IFS= read -r candidate; do
    [[ -n "$candidate" && "$candidate" != "$AI_MAIN_BRANCH" ]] || continue
    candidate_path="$(ai_branch_worktree "$candidate" || true)"
    [[ -z "$candidate_path" ]] || continue
    orphan_count=$((orphan_count + 1))
    remote_state=MISSING
    if git -C "$ROOT_DIR" rev-parse --verify --quiet "$(ai_remote_branch_ref "$candidate")^{commit}" >/dev/null; then
      candidate_head="$(git -C "$ROOT_DIR" rev-parse "$candidate^{commit}")"
      candidate_remote_head="$(git -C "$ROOT_DIR" rev-parse "$(ai_remote_branch_ref "$candidate")^{commit}")"
      if [[ "$candidate_head" == "$candidate_remote_head" ]]; then
        remote_state=BACKED-UP
      else
        remote_state=OUT-OF-DATE
      fi
    fi
    if [[ -n "$published_ref" ]] && git -C "$ROOT_DIR" merge-base --is-ancestor "$candidate" "$published_ref"; then
      merged=yes
    else
      merged=no
    fi
    printf '  %s merged=%s remote=%s\n' "$candidate" "$merged" "$remote_state"
  done < <(git -C "$ROOT_DIR" for-each-ref '--format=%(refname:short)' refs/heads)
  ((orphan_count > 0)) || printf '  (none)\n'

  printf '\nRemote task branches:\n'
  while IFS= read -r remote_candidate; do
    [[ -n "$remote_candidate" ]] || continue
    remote_branch="${remote_candidate#"$AI_REMOTE/"}"
    [[ "$remote_branch" != "$AI_MAIN_BRANCH" && "$remote_branch" != HEAD ]] || continue
    remote_count=$((remote_count + 1))
    if git -C "$ROOT_DIR" show-ref --verify --quiet "refs/heads/$remote_branch"; then
      local_state=LOCAL
    else
      local_state=REMOTE-ONLY
    fi
    if [[ -n "$published_ref" ]] && git -C "$ROOT_DIR" merge-base --is-ancestor "$remote_candidate" "$published_ref"; then
      merged=yes
    else
      merged=no
    fi
    printf '  %s merged=%s state=%s\n' "$remote_candidate" "$merged" "$local_state"
  done < <(git -C "$ROOT_DIR" for-each-ref '--format=%(refname:short)' "refs/remotes/$AI_REMOTE")
  ((remote_count > 0)) || printf '  (none)\n'
}

ai_initialize_context
