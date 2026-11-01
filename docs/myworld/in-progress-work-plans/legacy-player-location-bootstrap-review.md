# Legacy Player location bootstrap — verification in progress

The reported defect was reproduced before production changes with the actual
LoginRequest and complete PlayerService.loadPlayer flow, fresh synthetic SQLite
rows and a fresh synthetic native terrain package. An original stored location
`(109,3509)` with no `layerloc_*` cache keys incorrectly became signed level 0
at `(109,677)` instead of level -1. Reproduction evidence:
`output/player-location-bootstrap-2ca2533470/login.log`.

Cause: native initialization correctly interprets the packed database location
and then normalizes the runtime Point. Later location restore used that mutable
runtime projection as though it was the original database receipt, discarding
the packed level band for bootstrap and stale-record comparisons.

The focused fix returns the existing immutable
LegacyPlayerLocationPersistenceSnapshot from loadPlayerData and retains it as a
local variable for that one load transaction. Restore receives its original
packed Point. No service-global state, new cache format, migration-rule changes,
map changes or death/logout changes are introduced.

Initial corrected full-load fixture passes 57 assertions: missing cache for
levels 0/1/2/-1, complete classic records, complete native deep record, stale
record rebase, partial/malformed/divergent-record refusal, and two account loads
through the same service. Non-economic profile/authentication fields reuse the
existing synthetic F5 fixture scaffolding; economic and location-cache queries
use real production JDBC queries. No real accounts/databases/maps, private/public
server operations or listeners are used. Final regressions and evidence follow.
