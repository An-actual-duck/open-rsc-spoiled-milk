#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

print_pid_status() {
  local pid="$1"
  local port="$2"
  local label="$3"
  local root conf branch commit db_name server_name args marker_path marker_state
  local dirty_status published_commit checkout_publication checkout_state
  local launch_publication marker_identity marker_root_real checkout_safe=false
  local checkout_db checkout_db_real external_db external_db_real db_link_state db_link_valid=false
  local runtime_db_targets runtime_db_state runtime_db_valid=false
  local original_root="$ROOT_DIR"

  root="$(myworld_server_root_for_pid "$pid")"
  conf="$(myworld_server_conf_for_pid "$pid")"
  args="$(myworld_process_args "$pid")"
  marker_state="missing"

  if [[ -n "$root" && -n "$conf" && -f "$root/server/$conf" ]]; then
    branch="$(git -C "$root" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
    commit="$(git -C "$root" rev-parse HEAD 2>/dev/null || true)"
    published_commit="$(myworld_git_published_main_commit "$root" 2>/dev/null || true)"
    checkout_publication="$(myworld_git_commit_publication_state "$root" "$commit")"
    if ! dirty_status="$(myworld_git_dirty_status "$root" 2>/dev/null)"; then
      dirty_status="unknown"
    fi

    if [[ "$branch" != "HEAD" ]]; then
      checkout_state="UNSAFE attached branch $branch"
    elif [[ -n "$dirty_status" ]]; then
      checkout_state="UNSAFE dirty detached checkout"
    else
      case "$checkout_publication" in
        current)
          checkout_state="current detached published main"
          checkout_safe=true
          ;;
        previous)
          checkout_state="detached previous published main; deployment pending"
          checkout_safe=true
          ;;
        unpublished)
          checkout_state="UNSAFE detached unpublished commit"
          ;;
        *)
          checkout_state="UNKNOWN detached commit"
          ;;
      esac
    fi

    ROOT_DIR="$root"
    db_name="$(myworld_conf_value "$conf" db_name)"
    server_name="$(myworld_conf_value "$conf" server_name)"
    ROOT_DIR="$original_root"
    marker_path="$root/server/run/server-${port}.env"

    checkout_db="$(myworld_live_database_path "$root")"
    external_db="$(myworld_external_live_database_path)"
    checkout_db_real="$(readlink -f "$checkout_db" 2>/dev/null || true)"
    external_db_real="$(readlink -f "$external_db" 2>/dev/null || true)"
    if [[ -L "$checkout_db" && -n "$external_db_real" && "$checkout_db_real" == "$external_db_real" ]]; then
      db_link_state="verified external database"
      db_link_valid=true
    elif [[ ! -L "$checkout_db" ]]; then
      db_link_state="UNSAFE missing/non-symlink live database path"
    else
      db_link_state="UNSAFE points to ${checkout_db_real:-unknown}; expected ${external_db_real:-unknown}"
    fi

    runtime_db_targets="$(myworld_process_database_targets "$pid")"
    runtime_db_state="${runtime_db_targets:-missing database file descriptor}"
    if myworld_process_uses_only_external_live_database "$pid"; then
      runtime_db_valid=true
    fi
  else
    branch="unknown"
    commit="unknown"
    published_commit="unknown"
    checkout_state="unknown"
    db_name="unknown"
    server_name="unknown"
    marker_path=""
    db_link_state="unknown"
    runtime_db_state="unknown"
  fi

  printf '%s\n' "$label"
  printf '  PID:      %s\n' "$pid"
  printf '  Port:     %s\n' "$port"
  printf '  Worktree: %s\n' "${root:-unknown}"
  printf '  Branch:   %s\n' "${branch:-unknown}"
  printf '  Worktree commit: %s\n' "${commit:-unknown}"
  printf '  Published main:  %s\n' "${published_commit:-unknown}"
  printf '  Checkout: %s\n' "$checkout_state"
  printf '  Config:   %s\n' "${conf:-unknown}"
  printf '  DB:       %s\n' "${db_name:-unknown}"
  printf '  DB link:  %s\n' "$db_link_state"
  printf '  Runtime DB: %s\n' "$runtime_db_state"
  printf '  Server:   %s\n' "${server_name:-unknown}"

  if [[ -n "$marker_path" && -f "$marker_path" ]]; then
    marker_state="present"
    marker_label=""
    marker_root=""
    marker_branch=""
    marker_commit=""
    marker_config=""
    marker_db=""
    marker_server=""
    marker_port=""
    marker_started_at=""
    marker_safety=""
    marker_db_path=""
    marker_layered_package_path=""
    marker_layered_manifest_sha256=""
    marker_layered_runtime_mode=""
    # shellcheck disable=SC1090
    source "$marker_path"
    printf '  Launch:   %s\n' "${marker_label:-unknown}"
    printf '  Started:  %s\n' "${marker_started_at:-unknown}"
    printf '  Launch commit: %s\n' "${marker_commit:-unknown}"
    printf '  Marker:   %s %s %s\n' "${marker_branch:-unknown}" "${marker_config:-unknown}" "${marker_db:-unknown}"
    printf '  Attestation: %s\n' "${marker_safety:-missing}"
    printf '  Layered runtime: %s\n' "${marker_layered_runtime_mode:-legacy/unrecorded}"
    if [[ -n "${marker_layered_package_path:-}" ]]; then
      printf '  Layered package: %s\n' "$marker_layered_package_path"
      printf '  Layered manifest: %s\n' "${marker_layered_manifest_sha256:-missing}"
    fi
    launch_publication="$(myworld_git_commit_publication_state "$root" "${marker_commit:-}")"
    case "$launch_publication" in
      current)
        printf '  Runtime:  current published main\n'
        ;;
      previous)
        printf '  Runtime:  previous published main\n'
        ;;
      unpublished)
        printf '  Runtime:  UNPUBLISHED launch commit\n'
        ;;
      *)
        printf '  Runtime:  unknown launch commit\n'
        ;;
    esac
  else
    printf '  Launch:   missing marker; server was likely started before launch markers existed\n'
    printf '  Launch commit: unknown without a marker\n'
    printf '  Runtime:  unknown without a marker\n'
    launch_publication="unknown"
  fi

  if [[ -z "$root" || -z "$conf" ]]; then
    printf '  Verdict:  UNKNOWN PROCESS\n'
    printf '  Args:     %s\n' "$args"
  elif [[ "$port" == "$MYWORLD_PUBLIC_PORT" ]]; then
    local live_root_real root_real hosted_layered_required=false
    local layered_marker_valid=false expected_layered_path expected_layered_real marker_layered_real
    local installed_layered_manifest_sha256=""
    live_root_real="$(myworld_realpath "$MYWORLD_LIVE_ROOT" 2>/dev/null || true)"
    root_real="$(myworld_realpath "$root" 2>/dev/null || true)"
    if [[ "$root_real" == "$live_root_real" && "$conf" == "myworld-host.conf" && "$db_name" == "spoiled_milk_alpha" ]]; then
      if grep -Eq '^[[:space:]]*want_layered_native_terrain_package:[[:space:]]*true([[:space:]]|$)' \
          "$root/server/$conf"; then
        hosted_layered_required=true
      fi
      if [[ "$hosted_layered_required" == false ]]; then
        layered_marker_valid=true
      elif [[ "${marker_layered_runtime_mode:-}" == legacy-rollback ]]; then
        layered_marker_valid=true
      elif [[ "${marker_layered_runtime_mode:-}" == production ]]; then
        expected_layered_path="$(layered_world_live_package_path "$MYWORLD_LIVE_DB_ROOT")"
        expected_layered_real="$(readlink -f "$expected_layered_path" 2>/dev/null || true)"
        marker_layered_real="$(readlink -f "${marker_layered_package_path:-}" 2>/dev/null || true)"
        if [[ -n "$expected_layered_real" && -f "$expected_layered_real/manifest.json" ]]; then
          installed_layered_manifest_sha256="$(
            layered_world_manifest_sha256 "$expected_layered_real" 2>/dev/null || true
          )"
        fi
        if [[ -n "$expected_layered_real" \
            && "$marker_layered_real" == "$expected_layered_real" \
            && "$installed_layered_manifest_sha256" == "$SPOILED_MILK_LAYERED_MANIFEST_SHA256" \
            && "${marker_layered_manifest_sha256:-}" == "$SPOILED_MILK_LAYERED_MANIFEST_SHA256" ]]; then
          layered_marker_valid=true
        fi
      fi
      marker_identity="invalid"
      if [[ "$marker_state" == "present" ]]; then
        marker_root_real="$(myworld_realpath "${marker_root:-}" 2>/dev/null || true)"
        if [[ "$marker_root_real" == "$live_root_real" \
            && "${marker_branch:-}" == HEAD \
            && "${marker_config:-}" == "myworld-host.conf" \
            && "${marker_db:-}" == "spoiled_milk_alpha" \
            && "${marker_db_path:-}" == "$external_db_real" \
            && "${marker_safety:-}" == verified-live \
            && "$layered_marker_valid" == true \
            && "${marker_port:-}" == "$MYWORLD_PUBLIC_PORT" ]]; then
          marker_identity="valid"
        fi
      fi

      if [[ "$marker_state" != "present" ]]; then
        printf '  Verdict:  DANGER: running public server has no verified launch marker\n'
      elif [[ "$checkout_safe" != true ]]; then
        printf '  Verdict:  DANGER: live checkout is not clean detached published-main history\n'
      elif [[ "$db_link_valid" != true ]]; then
        printf '  Verdict:  DANGER: live checkout database link is not the external database\n'
      elif [[ "$marker_identity" != "valid" ]]; then
        printf '  Verdict:  DANGER: hosted launch marker lacks verified live identity, database, or layered-runtime attestation\n'
      elif [[ "${marker_commit:-}" != "$commit" ]]; then
        printf '  Verdict:  DANGER: runtime launch commit differs from files currently in the live checkout\n'
      elif [[ "$runtime_db_valid" != true ]]; then
        printf '  Verdict:  DANGER: runtime database file descriptor is missing, wrong, or deleted\n'
      elif [[ "$launch_publication" == "current" ]]; then
        printf '  Verdict:  OK LIVE HOSTED SERVER - running current published main\n'
      elif [[ "$launch_publication" == "previous" ]]; then
        printf '  Verdict:  OK LIVE HOSTED SERVER - running previous published main; restart pending\n'
      else
        printf '  Verdict:  DANGER: hosted launch commit is not verified published-main history\n'
      fi
    else
      printf '  Verdict:  DANGER: public port is not the approved live server\n'
    fi
  else
    printf '  Verdict:  private/non-public port\n'
  fi
  printf '\n'
}

print_port_status() {
  local port="$1"
  local label="$2"
  local pids pid found=0

  local summary

  summary="$(myworld_listener_summary_for_port "$port")"
  pids="$(myworld_listener_pids_for_port "$port")"
  if [[ -z "$pids" ]]; then
    printf '%s\n' "$label"
    printf '  Port:    %s\n' "$port"
    if [[ -n "$summary" ]]; then
      printf '  Verdict: UNKNOWN LISTENER; process identity is unavailable\n'
      printf '  Socket:  %s\n\n' "$summary"
    else
      printf '  Verdict: no listener\n\n'
    fi
    return
  fi

  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    found=1
    print_pid_status "$pid" "$port" "$label"
  done <<<"$pids"

  [[ "$found" == 1 ]] || printf '%s\n  Port: %s\n  Verdict: no listener\n\n' "$label" "$port"
}

main() {
  printf 'Spoiled Milk server status\n'
  printf 'Live root: %s\n\n' "$MYWORLD_LIVE_ROOT"
  print_port_status "$MYWORLD_PUBLIC_PORT" "PUBLIC PLAYER PORT"
  print_port_status "$MYWORLD_DEV_PORT" "PRIVATE DEV PORT"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
