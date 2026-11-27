# External AI Contributor Workflow

For instructions you can give directly to Claude, see the
[Claude contributor onboarding guide](../contributor-guides/claude-onboarding.md).

This is the safe contribution path for someone who uses AI tools but is not a
Spoiled Milk maintainer. It keeps `main`, releases, and the public server under
Justin's manager AI while still producing the same kind of exact, durable
handoff as an internal worker.

## Previous onboarding example

The commands below use `Goutan` as an example from an earlier onboarding.
Use your own GitHub username, email and branch name for a new setup. Start with
the Claude onboarding guide for account creation, repository access and
sign-in.

- GitHub contributor: `Goutan`
- Operating system: Windows
- Expected AI sessions: one
- Workspace model: one ordinary clone and one topic branch at a time
- Repository access: collaborator topic-branch pushes and pull requests
- Existing onboarding test: pull request #3 from `Goutan-patch-1`

`Goutan-patch-1` is a valid existing review branch. The onboarding tooling does
not rename, delete, rewrite, merge, or otherwise modify it. New work should use
the namespaced branch format described below.

## The Mental Model

There are three separate responsibilities:

1. Goutan works on `goutan/TYPE/short-description`, checks the work, pushes
   checkpoints, and opens a pull request.
2. Justin's manager AI collects the exact pushed commit into an idle internal
   worker slot, reviews it, and runs the final tests.
3. Justin's manager AI alone decides whether to merge, publish, release, or
   deploy it.

A pull request is a request for review, not permission to merge or deploy. A
contributor never needs the live database, credentials, release keys, hosted
server folder, or deployment commands.

## Why The Maintainer Scripts Are Not Reused

Do not run `scripts/ai-workspace.sh` or `scripts/ai-manager.sh` on Goutan's
computer. They are intentionally maintainer-specific:

- They model one primary manager checkout plus sibling `Core-Framework-ai-N`
  worktrees.
- They normally use the same maintainer remote for published `main` and worker
  branch pushes, with `spoiled-milk` as a fallback remote name.
- Their handoff state lives in the manager clone's Git metadata, which an
  external clone cannot share.
- Manager operations can merge, recycle, delete remote task branches, package
  releases, and reference the maintainer's live-server layout.
- The shell interface assumes Bash. The shared library also has a fixed
  `/tmp/spoiled-milk-live-main` classification path.

The contributor helper is `scripts/contributor-workspace.py`. It discovers the
current clone, uses a remote selected during setup, works from PowerShell or Git
Bash through Python, and exposes no merge, release, deployment, or remote-delete
command.

## Windows Prerequisites

Install:

- Git for Windows
- Python 3
- the AI development tool Goutan intends to use

The examples below use PowerShell's Python launcher, `py -3`. If that command
is unavailable but `python` works, replace `py -3` with `python`.

Before the first contribution, Git also needs a name and a GitHub-compatible
email. The following commands may be run from any directory. GitHub's private
no-reply email is appropriate if desired; replace the placeholder with the
actual address shown in the contributor's GitHub email settings:

```powershell
git config --global user.name "Goutan"
git config --global user.email "GITHUB-NOREPLY-EMAIL"
```

These two identity values are commit attribution, not repository credentials.

## First-Time Setup

If the repository is not cloned yet, run this from the parent folder where the
project should live:

```powershell
git clone https://github.com/An-actual-duck/open-rsc-spoiled-milk.git
cd open-rsc-spoiled-milk
```

If it is already cloned, open PowerShell in the repository folder. Every
remaining contributor command in this guide runs from that repository root—the
folder containing `AGENTS.md`, `Client_Base`, `server`, and `scripts`.

Inspect the clone without changing it:

```powershell
git status --short --branch
git remote -v
```

Configure the contributor workflow:

```powershell
py -3 scripts/contributor-workspace.py setup --username Goutan --remote origin
py -3 scripts/contributor-workspace.py status
```

Setup stores only local Git configuration. It also enables the repository's
contributor `pre-push` guard. That guard rejects pushes to `main` made through
Git clients that honor repository hooks, including the helper and ordinary Git
commands. It does not grant or alter GitHub access. GitHub branch protection is
still the repository-level backstop.

If setup reports an existing custom hooks path, stop and ask Justin. The helper
will not overwrite another Git hook configuration.

## Start A Task

Justin should provide one focused task and any relevant plan document. From the
repository root, start it with a descriptive type and name:

```powershell
py -3 scripts/contributor-workspace.py start fix/short-description
```

The helper safely fetches published `origin/main`, fast-forwards a clean local
`main`, and creates:

```text
goutan/fix/short-description
```

Other valid examples are:

```powershell
py -3 scripts/contributor-workspace.py start feat/interface-improvement
py -3 scripts/contributor-workspace.py start docs/setup-clarification
py -3 scripts/contributor-workspace.py start test/collision-regression
```

It refuses to start if the current folder is dirty, a previous branch is not
backed up at its exact commit, or local `main` contains unique work. A refusal
means stop and preserve the work; it is not an invitation to reset or clean it.

Use only one AI session in this clone. Give the AI this context:

```text
Read AGENTS.md, CONTRIBUTING.md, docs/workspaces/external-contributor.md,
and the task's plan or issue before editing.

Work only on the current topic branch and requested task. Do not switch
branches, touch main, use maintainer workspace/release scripts, access any
public server, or revert unrelated work. Inspect first, make focused changes,
run relevant tests, and report changed files and risks.
```

## Save A Checkpoint

A checkpoint is a normal commit plus a remote backup of the same topic branch.
Use it after meaningful progress and before the AI session or computer may
close:

```powershell
py -3 scripts/contributor-workspace.py checkpoint -m "Checkpoint collision definition fix"
```

The helper:

- refuses `main`, detached HEAD, generic branches, and unfinished Git operations
- stages tracked and untracked project files
- checks whitespace errors
- lists every staged path and size
- blocks likely credentials and files over 25 MB
- commits the checkpoint
- performs an ordinary, non-force push of only the current topic branch
- verifies the remote branch is the exact local commit

If the safety scan blocks a file, nothing is committed or pushed. The files
remain staged so they are not lost. Ask Justin to review the situation rather
than bypassing the guard.

## Test And Hand Off

Run tests appropriate to the change. Record the exact commands or manual checks
because they belong in the pull request.

When the work is ready, run:

```powershell
py -3 scripts/contributor-workspace.py handoff `
  -m "Fix client collision definitions" `
  --tests "Client build; manually checked Support and Well pathing"
```

PowerShell's backtick continues a command onto the next line. The same command
may be written on one line.

Handoff commits any final changes, verifies a clean folder and exact remote
branch, fetches current published `main`, and prints a block containing:

- contributor
- topic branch
- exact 40-character commit
- published-main commit and merge base used for comparison
- tests reported by the contributor
- a GitHub pull-request link when the remote is hosted on GitHub

Open or update the pull request into `main`, complete the repository template,
and paste the exact Branch and Commit values into its Contributor Handoff
section. Send those same two values to Justin. A later edit or commit requires a
new handoff and an updated pull-request comment.

## If Work Becomes Dirty Or Confusing

Do not use a stash, `git clean`, `git reset --hard`, forced checkout, branch
deletion, or force-push. Preserve first:

```powershell
py -3 scripts/contributor-workspace.py rescue -m "Rescue unfinished collision investigation"
```

If necessary, this creates a timestamped branch such as:

```text
goutan/rescue/20260720-183000
```

It commits and pushes the recoverable files but deliberately does not label
them READY. Send the rescue branch to Justin for inspection before cleanup.

If Git is in the middle of a merge, rebase, cherry-pick, or revert, the helper
stops without changing it. Ask Justin for help.

## After Submission

Keep the branch and clone intact while review is in progress. If Justin asks
for changes, make them on the same topic branch, checkpoint, retest, and hand
off a new exact commit.

After the pull request is merged, start the next task with the `start` command.
It safely updates local `main`; there is no need to delete or rewrite the old
branch as part of the beginner workflow.

## Maintainer Collection Checklist

The following commands are for Justin's manager checkout, not the contributor.

1. Confirm the pull request author, scope, branch, and exact full commit.
2. Confirm no credentials, databases, release artifacts, or unrelated files
   are present.
3. Choose an idle neutral slot and collect the exact remote handoff:

   ```bash
   ./scripts/ai-manager.sh collect-contributor ai-N goutan/TYPE/task EXACT_COMMIT
   ```

4. Inspect the diff and run relevant tests in the printed slot path. Collection
   marks the verified commit READY but does not merge it.
5. If review fails, leave the branch unchanged and request a new handoff on
   the same branch. When it arrives, run `collect-contributor` with the original
   slot and the new exact commit. Review and test the updated checkout. See the
   [manager guide](manager.md#external-contributor-collection) for the refresh
   requirements.
6. If review passes, return to the manager checkout and run:

   ```bash
   ./scripts/ai-manager.sh merge goutan/TYPE/task
   ```

7. Run final integration tests, push reviewed `main`, and recycle the slot only
   after published main contains the exact handoff.
8. Release and live deployment remain separate owner operations. Contributor
   approval or a merged pull request never authorizes a public-server restart.

## GitHub Main Protection

The [maintainer-controlled main ruleset][main-ruleset] applies to `main`.
Only the maintainer account can bypass the update restriction. Contributors
can push their task branches and open pull requests for manager review.
Contributor setup is local and does not alter GitHub settings.

[main-ruleset]: https://github.com/An-actual-duck/open-rsc-spoiled-milk/rules/28013985
