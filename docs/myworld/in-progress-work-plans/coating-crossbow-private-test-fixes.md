# Private coating and god-crossbow test corrections

November 15, 2026. No public deployment or account changes.

- The packaged god crossbow icons existed but their nested inventory directory was absent from ClientExternalAssetLoader's search list. Added the directory and actual packaged-only lookup checks for all three icons in both sprite modes.
- The basic crossbow already had the reported side-view hand misalignment. The player compositor now offsets all crossbow families by eight native pixels toward the facing direction and eight downward in side views only, retaining the existing mirrored walk-phase handling. All approved weapon artwork, other directions, draw order and unrelated equipment remain unchanged. Actual player-compositor captures of both facings and all three walk frames were inspected; isolated weapon thumbnails did not expose this issue.
- Naga scales now use a smaller green native icon, with an editable Aseprite master and reproducible palette recipe. The original blue dragon scale reference is preserved. Packaged-only rendering checks assert green pixels and bounded size.

## Private inventory incident

The private test inventory was edited in SQLite while its server process remained running. Its in-memory maxItemId counter was therefore lower than the manually inserted IDs. New crafted items reused those instance IDs, causing wrong-item removals and duplicate-key save failures. This is a scenario setup mistake; production crafting/grinding code was not changed in response.

The private database and in-memory inventory were captured before repair. The disposable account's remaining inventory, equipment and bank received fresh unique instance IDs, the counter was advanced, and the session saved successfully before stopping its private process. Evidence is under the manager's output/tests/next-update-private/20261115-2d8f793ba/scenario (bug-report-before-fixes.sqlite, live-inventory.txt, rekeyed-inventory.txt, after-rekey-recovered.sqlite). The pre-test database is also preserved.

**Future scenario setup:** log out, stop the exact private server process, verify it has exited, back up SQLite, then edit inventory. Restart afterward so the server reloads the maximum instance ID. Logout alone is insufficient. Never edit a running server database to insert test items.

A new full-inventory batch regression uses the real Herblaw plugin: three raw scales produce exactly three powders while preserving the tin scimitar, mortar and all unrelated herbs.

## Verification

- Client build passed.
- God crossbow visual suite: 427 custom and 430 classic checks, plus 36 projectile geometry checks and unchanged approved held/archive payload checks.
- Naga scale suite: 12,843 gameplay/drop/grinding checks, including the full-inventory batch; both client modes passed.
- Actual player compositor side captures inspected for basic and all three god crossbows, all three walk frames.
- git diff --check passed. Workspace layout check continues to report pre-existing legacy output-root entries; no retained evidence was deleted.

Private owner re-test is needed for final visual acceptance and coating/crossbow gameplay. No public release is authorized by this task.
