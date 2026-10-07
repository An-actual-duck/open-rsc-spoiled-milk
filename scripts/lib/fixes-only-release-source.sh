#!/usr/bin/env bash
# Explicit exception for a reviewed, pushed fixes-only source. Never changes refs.
fixes_only_require_no_git_operation() {
  local git_dir entry
  git_dir="$(git -C "$1" rev-parse --absolute-git-dir)"
  for entry in MERGE_HEAD CHERRY_PICK_HEAD REVERT_HEAD REBASE_HEAD rebase-apply rebase-merge sequencer BISECT_LOG; do
    [[ ! -e "$git_dir/$entry" ]] || fail "Unfinished Git operation in $1"
  done
}
fixes_only_require_source() {
  local root="$1" branch="$2" expected="$3" head actual remote_head git_dir entry
  [[ "$branch" =~ ^fix/fixes-only-[a-zA-Z0-9._-]+$ ]] || fail "Invalid fixes-only branch"
  [[ "$expected" =~ ^[0-9a-f]{40}$ ]] || fail "Fixes-only source requires an exact 40-character commit"
  actual="$(git -C "$root" symbolic-ref --quiet --short HEAD || true)"
  [[ "$actual" == "$branch" ]] || fail "Fixes-only source branch mismatch"
  fixes_only_require_no_git_operation "$root"
  [[ -z "$(git -C "$root" status --porcelain --untracked-files=all)" ]] || fail "Fixes-only source is dirty"
  head="$(git -C "$root" rev-parse HEAD)"
  [[ "$head" == "$expected" ]] || fail "Fixes-only source commit changed"
  remote_head="$(git -C "$root" ls-remote --exit-code spoiled-milk "refs/heads/$branch" | awk '{print $1}')" \
    || fail "Cannot verify the pushed fixes-only source"
  [[ "$remote_head" == "$expected" ]] || fail "Fixes-only source must match the actual pushed remote branch"
  git -C "$root" merge-base --is-ancestor fec94c8731b5521410963575ef0f2fa5c05ef0b3 "$head" \
    || fail "Fixes-only source is not based on the approved deployed baseline"
}
