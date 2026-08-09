# A11 Combat Observability Profiles

## Delivered combat boundary

A11 adds an optional, bounded in-memory combat observer without changing a
combat formula, event order, damage transaction, packet, plugin dispatch, or
server startup path.

`CombatTraceObserver` implements the existing read-only
`CombatDamageObserver` seam. Production remains inert because `Server` still
installs `CombatDamageObserver.NONE`; this branch intentionally does not alter
`Server`, `ServerConfiguration`, `PluginHandler`, the shared plugin registry,
or the Server R2 documentation.

The recorder has a fixed ring capacity of 1 through 1,024 records. Its public
snapshot is immutable and contains factual result values only:

- reason code (`DAMAGE_SETTLED` or `TARGET_HITS_DEPLETED`);
- game tick, source category, optional combat style, and a bounded stable
  effect key;
- resolved/actual/overkill damage and immediate Hits before/after values.

It deliberately retains no `Mob`, participant snapshot, player/account name,
network identity, location, session generation, coordinates, or raw request
identity. Effect keys containing whitespace, punctuation outside `-` and `_`,
or more than 64 characters are stored and logged as `redacted`. The existing
damage-observation failure log now uses that same rule.

## Profiles and lifecycle meaning

The only accepted profiles are closed enum values, parsed case-insensitively
from the documented values `off`, `damage`, `lifecycle`, and `full`:

| Profile | Records |
| --- | --- |
| `off` | Nothing; observer is disabled. |
| `damage` | One `DAMAGE_SETTLED` record for each settled observed damage result. |
| `lifecycle` | Only `TARGET_HITS_DEPLETED` when the immediate damage mutation reaches zero Hits. |
| `full` | Both records for a terminal result; ordinary results have `DAMAGE_SETTLED`. |

`TARGET_HITS_DEPLETED` is intentionally not a death-complete, rewards-paid,
or respawn-complete claim. Death adapters remain owned by their existing event
families, can be deferred, and may follow different compatibility paths. This
is the narrow lifecycle boundary that can be reported honestly from the shared
resolved-damage result.

Bad profile names and capacities outside 1–1,024 fail validation before an
observer can be installed. The observer is read-only; the existing
`CombatDamageObservation` wrapper catches observer setup/publication runtime
failures so diagnostics cannot roll back, repeat, or suppress gameplay. Its
existing executable failure-isolation scenario remains the authority for a
throwing observer.

## R2-2 integration contract (remaining work)

The Server R2 owner may wire this capability only after its configuration and
extension work has its own acceptance gate. The integration must:

1. Treat absent combat-trace configuration as `off`, preserving the current
   production `CombatDamageObserver.NONE` default.
2. Parse a supplied profile exclusively with
   `CombatTraceProfile.fromExternalValue`; reject unknown names clearly rather
   than falling back to `full` or another enabled profile.
3. Validate an explicit bounded capacity before constructing
   `CombatTraceObserver`; use a documented conservative default only when the
   profile was intentionally enabled and capacity omitted.
4. Construct/inject the observer through the existing `Server` constructor
   `CombatDamageObserver` parameter. Do not add combat decisions, mutable
   callbacks, or a second observer registry.
5. Keep any operator export/display path read-only, bounded, and redacted. It
   must not print player names, account hashes, IPs, coordinates, entity
   objects, or unvalidated effect-key strings.
6. On reload or extension failure, retain the previous valid observer or
   safely use `NONE`; never prevent startup, disconnect players, or affect a
   damage transaction. Profile replacement must be atomic from the observer
   consumer's perspective.
7. Add R2-owned lifecycle tests for startup, valid enablement, invalid
   configuration, disabled default, transactional reload failure, and shutdown
   cleanup. Those tests belong with R2 configuration/extension ownership, not
   this combat-only branch.

No custom content, compatibility mode, plugin adapter, or external sink is
enabled by this combat foundation.

## Executable coverage

`CurrentCombatObservabilityCharacterization` verifies profile validation,
capacity rejection, bounded oldest-record eviction, effect-key redaction,
immutable snapshots, terminal-Hits lifecycle recording, and an observer
installed through the existing server constructor while authoritative Hits and
hitsplat settlement remain unchanged. The existing combat observer failure
scenario verifies both enablement and publication failures cannot change
damage or presentation.
