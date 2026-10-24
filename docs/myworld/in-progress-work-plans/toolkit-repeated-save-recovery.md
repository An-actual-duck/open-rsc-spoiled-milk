# Toolkit repeated-save protection repair

On October 23, 2026 the owner reported `Protected root budget` while saving a
layered tower project after refreshing the window content. The pre-existing
checkpoint reader added each newly reopened checkpoint's baseline to the
persisted protection set. Repeated reopen/save cycles reached its16-root limit.
The current failed attempt retained17roots and remained in an unpublished
`.modern-project-pending-*` directory.

Independent Toolkit handoff `acd1c4bd2176633253a0a9b4816aebd29611ef33`, integrated
in Toolkit main8479a6b, restores the exact serialized protection set on reopen.
The active checkpoint baseline is protected separately in destination checks.
No historical/original protections are guessed, removed or renamed. A bounded
4096-root reader/writer limit permits recovery of existing17-root attempts;
the actual repair stops future accumulation rather than merely increasing a cap.
Source containment, absolute normalized paths, symlink guards and JSON bounds
remain enforced. No map schema, game loader, renderer or content roster changes.

Verification passed five new regression cases, including40save/reopen cycles
with22genuine/history roots, offline retained content, both history branches,
old17-root recovery, admission bounds and output protections. Full package
verification:1240tests, zero failures/errors and two existing conditional UI skips.
The manager independently passed19focused tests after integration.

## Owner work preservation and recovery

The owner confirmed no further edits after the failure, then closed both
editor and launcher after backup. Both located failed attempts were copied
and SHA256-verified with originals unchanged. Chronological latest was used;
an older attempt had a higher generation, which is not proof that it was the
current editing state. No automatic merge of the two histories was attempted.

Recovery evidence is preserved outside Git at
`/home/justin/.local/share/protected-root-save-recovery-qx0o7gw1/`.
It contains both original copies, full file-hash inventories, verification logs,
diagnostic limitations and the final semantic comparison receipt. The current
recovery saved and reopened at the normal packaged1536MiB limit. It preserves
all10910tile overrides,358placement overrides,19undo and4redo states, refreshed
content revision, generation, baseline identity and all17protected roots.
Level/placement record ordering is nonsemantic; record values and history order
were compared without treating harmless record reordering as lost work.

A new selectable checkpoint, `project-recovered-protected-roots-20261023`, was
created in the existing tower session's saves directory. Its view association
comes from the last successful checkpoint; no original save was overwritten.
The earlier failed attempt remains separately preserved for optional review.

## Installed tool

The verified private Linux package is installed at
`/home/justin/Core-Framework/RSC Development Toolkit`. Package SHA256:
`0f1d0ee7b1fb285d02b90cf3ee4ec1ab522605b961ea706fa04f98c6fb7f0fb1`.
All101packaged file hashes passed after installation. The package, previous
installation and install receipt remain under
`/home/justin/.local/share/repeated-save-protection-package/`.
The manager reopened the recovered checkpoint using the installed application.
The owner can continue there and test Save. No server map import, deployment,
public restart or account changes were performed.
