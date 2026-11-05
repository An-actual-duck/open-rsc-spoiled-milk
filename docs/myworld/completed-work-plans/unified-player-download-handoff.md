# Unified player download handoff

Branch: feat/unified-player-download. Ready for manager review.

The website can use one stable release-asset URL ending in
releases/latest/download/spoiled-milk-client.zip. The ZIP has one shared game
payload, the existing Windows Java runtime, Windows and Linux launch files,
a macOS command wrapper, and instructions for each operating system.

Packaging changes:

- Normal releases generate the combined download and checksum alongside the
  versioned platform archives. The latter remain available for existing
  updater compatibility.
- Manager release accepts --consolidate-existing to combine an already tagged
  release without rebuilding its client or map. It still requires clean,
  published main and the matching original source commit in both archives.
- Consolidation rejects payload differences, unsafe ZIP entries, missing
  Windows Java, incorrect source provenance and overwriting an existing ZIP.
- Both Windows launch templates now keep their failure exit inside the
  error conditional. Consolidation also repairs that line in an older
  archived launcher while retaining its original JVM options.

Validation completed:

- Player release regression fixtures passed.
- Ten consolidation tests passed, including exact-payload reuse, rejected
  provenance and launch invocation from extracted paths containing spaces.
- Twenty-seven workspace workflow tests passed, including the consolidation
  branch that skips the server-map packaging operation.
- Eight POSIX updater scenarios passed. Eight PowerShell scenarios were
  skipped because native PowerShell is unavailable.
- Ruff, warning-level ShellCheck, shell syntax and diff whitespace checks passed.
- Original v0.3.5 archives matched all published checksums.
- The real candidate passed ZIP CRC validation, its Windows Java executable
  was verified as x86-64 PE, and its shell launchers reached a recording JVM
  after checking the actual published version. No game login occurred.

Candidate evidence is under output/reviews/unified-player-download/candidate
in this worker. Candidate size is 62,355,276 bytes and SHA-256 is
489c63998836767baf3f9e567290ffdf887f3a8be55ecbc452375d6eda429e45.
Its unchanged game client belongs to release commit
b31466614322e6e0b6b29f6c6dca45c11fda0da0.

Native Windows and macOS execution remains unverified here. The manager still
needs to integrate, run final checks, produce the matching archive through
the manager release command, and publish the additional asset and checksums.
No game source, public process, database, map or existing release archive was
changed by the worker.
