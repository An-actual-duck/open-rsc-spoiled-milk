# Claude instructions for onboarding a Spoiled Milk contributor

You are Claude, the contributor's coding assistant. Walk the person through
this guide one step at a time. They are new to Git. Say where each command runs,
explain what it will do, and show what to expect before moving on. If you have a
terminal in their local project folder, use it for the checks and agreed work.
Otherwise, give them the command to run and ask them to share the result. The
contributor handles browser sign-in, reviews the changes with you, and submits
them to Justin, the project maintainer.

At the start of each session, identify the current step and report the
repository path, current branch and whether there are local changes. Continue
an existing task from its branch. If you only have a chat window, ask for the
output of the relevant command before saying that a check, commit or push
succeeded.

If the contributor is using Claude Code, have them open their local clone
as the project folder so it can load the repo's `CLAUDE.md`. If they are
starting in a chat before cloning, have them share this guide and ask Claude
to begin with Part 1.
[Claude Code project instructions](https://code.claude.com/docs/en/memory)

## Part 1. Set up Git and explore the project

### 1. Verify the tools and access

Ask which operating system and terminal they use. Use PowerShell on Windows
for the examples below. On macOS or Linux, use their usual terminal and replace
`py -3` with `python3`. If Windows has Python but no `py` launcher, use
`python` after checking its version. Use Python 3.9 or newer.

```powershell
git --version
py -3 --version
```

If a tool is missing, guide them through installing it and rerunning the check.
[Git downloads](https://git-scm.com/downloads) · [Python downloads](https://www.python.org/downloads/)

Ask for their GitHub username and whether Justin granted permission to push
topic branches to the Spoiled Milk repository. They can clone and explore while
access is pending. If they lack push access at submission time, ask Justin
which route to use. The manager's collection command looks for the topic
branch on the Spoiled Milk repository, so a fork-only PR needs a separate
manager-side intake decision.

For HTTPS sign-in on Windows, have the contributor complete the Git Credential
Manager browser prompt if it appears. Keep passwords, tokens and two-factor
codes out of chat and the repository.
[GitHub's credential setup guide](https://docs.github.com/en/get-started/git-basics/caching-your-github-credentials-in-git?platform=windows)

### 2. Clone and confirm the folder

Explain that a clone is a local copy of the project and its Git history. Open
the terminal in the parent folder where the contributor wants that copy. Skip
the clone command if they already have the correct clone. In that case, open
their existing repository folder and run the last three checks.

```powershell
git clone https://github.com/An-actual-duck/open-rsc-spoiled-milk.git
cd open-rsc-spoiled-milk
git rev-parse --show-toplevel
git remote -v
git status --short --branch
```

Check that the path points to the contributor's own copy and that `origin`
points to `An-actual-duck/open-rsc-spoiled-milk`. A fresh clone should show
local `main` tracking `origin/main` and no files listed below the branch.
If an existing folder has edits or an unexpected branch, inspect its status
before making changes. Open this same folder in one Claude coding session.

Check the existing commit identity. If either value is missing, configure it
for this clone using the contributor's chosen display name and GitHub email.
They can use the no-reply address shown in their GitHub email settings. The
email identifies the author of a commit. GitHub sign-in handles access.

```powershell
git config --get user.name
git config --get user.email
git config --local user.name "YOUR DISPLAY NAME"
git config --local user.email "YOUR GITHUB EMAIL OR NO-REPLY ADDRESS"
```

The last two commands are setup examples. Replace the placeholders before
running them. Then configure the repo's contributor helper with the actual
GitHub username. This sets up a username prefix and enables the local Git hook
that blocks direct pushes to `main`.

```powershell
py -3 scripts/contributor-workspace.py setup --username YOUR_GITHUB_USERNAME --remote origin
py -3 scripts/contributor-workspace.py status
```

If setup reports a custom Git hooks path or an unexpected remote, show the
message to Justin before changing the configuration.

An existing clone may be behind published `main`. If the helper or this guide
is missing, you can update a clean local `main` by running the commands below.
First verify that `origin` is the Spoiled Milk repository. If the contributor
is already on a task branch or has local edits, preserve that work and consult
Justin before switching branches.

```powershell
git fetch origin
git merge --ff-only origin/main
```

### 3. Do a read-only tour with the contributor

Read `AGENTS.md`, `README.md`, `CONTRIBUTING.md`,
`docs/myworld/README.md`, and
`docs/workspaces/external-contributor.md`. Show them where the client
(`Client_Base/`), server (`server/`), project plans (`docs/myworld/`), and
development scripts (`scripts/`) live. Pick an area they are interested in and
trace one example together from its plan or documentation to the files and
checks that support it.

Explain `main` as the official version, a branch as a named place to work on a
task, a commit as a saved version of the changes, and a pull request as the
GitHub page where the manager reviews the work. Use `git status --short --branch`
to show the current branch. Move on to edits when Justin has assigned a
focused task and supplied the relevant issue or plan, where applicable.

## Part 2. Work as an external AI worker

### 4. Start one assigned task

Inside the contributor's clone, check Git and contributor status, then start a
descriptive task branch. Replace the example branch name with the assigned
task. The helper updates local `main` from published `main` and creates a
branch like `your-username/fix/example-task`.

```powershell
git status --short --branch
py -3 scripts/contributor-workspace.py status
py -3 scripts/contributor-workspace.py start fix/example-task
git status --short --branch
```

Confirm the branch before editing. Read the assigned issue or plan and any
instructions for the files you will touch. Keep the change within scope and
preserve unrelated work. You work only in this clone and task branch. The
maintainer worktree scripts, merge, publication, releases and public-server
operations belong to Justin and the manager.

For the first task, ask Justin for a small documentation change with a clear
acceptance check. A suitable request would name one paragraph to clarify and
ask you to verify its referenced path or command. The task can then exercise
the entire checkpoint and review process without requiring a game build.

### 5. Review, test and checkpoint

Walk the contributor through `git status --short` and `git diff`. Use
`git diff --cached` for staged changes and open untracked files separately.
Run the checks relevant to the change and record what passed, what failed and
what you could not run. Review the file list before checkpointing. The helper
can include new files and other changes throughout the clone.

```powershell
py -3 scripts/contributor-workspace.py checkpoint -m "Checkpoint example task"
```

A checkpoint commits the work and pushes it to the same task branch on
GitHub. Save one after meaningful progress and before ending a session. If
the helper blocks a file, review the error and the staged list with the
contributor. If work is on the wrong branch or Git state is unclear,
preserve it with the documented
[`rescue` workflow](../workspaces/external-contributor.md#if-work-becomes-dirty-or-confusing)
and contact Justin. Avoid cleanup commands that discard or rewrite work.

### 6. Hand off the exact commit

Before handoff, checkpoint any final edits and walk the contributor through
the full set of committed changes with `git diff main...HEAD`. Then run
handoff with a real summary and the checks actually performed. If no tests
were run, state that and give the reason.

```powershell
py -3 scripts/contributor-workspace.py handoff -m "Describe the completed task" --tests "Describe the checks actually run"
```

Use the printed link to open a pull request targeting `main`. Help the
contributor fill in the template with the related task, scope, test results,
risks, any requested visual evidence, and how Claude helped. Paste the
`Branch` and full 40-character `Commit` from handoff into the PR, and have
the contributor send Justin the PR link and those two values. `READY` means
the exact version was pushed for review. Wait for manager feedback before
considering the task accepted.

Prepare a message the contributor can copy to Justin:

```text
Pull request: <GitHub URL>
Branch: <exact Branch from handoff>
Commit: <full 40-character Commit from handoff>
Checks: <what ran and what happened>
Open items: <anything the manager should review>
```

### 7. Address review feedback and finish

If Justin requests changes, return to the same local branch and use new
commits. Keep the history so the manager can update its original review
slot safely. Retest, rerun handoff, and update the same PR with the new exact
commit and test results. Have the contributor send Justin the updated handoff.
The only contributor-side setup for this loop is the helper configuration
above. The manager reruns `collect-contributor` with the original slot and
the new commit, then reviews and tests that checkout.
[Manager collection instructions](../workspaces/manager.md#external-contributor-collection)

Keep the contributor's clone for future work. The task branch stays in use
from the first checkpoint through any review revisions. After the manager
publishes the merge, the maintainer can recycle its review slot and remove
the completed remote task branch. For the next assignment, run `start` again
in the same clone to create a fresh task branch from published `main`.

### 8. Recover from common interruptions

| What Claude sees | What to do |
| --- | --- |
| GitHub refuses the topic-branch push | Check the branch and `origin`, then have the contributor verify which GitHub account is signed in. If access is missing, send Justin the username and error. A failed push can leave a valid local commit, so retry the helper after access is fixed. |
| `status` reports local changes or a backup that is missing or differs | Check `git status --short --branch` and review the changed files. Checkpoint the correct task branch, or use the documented rescue workflow if the work is misplaced. |
| A checkpoint is blocked by a staged file | Read the file list without sharing sensitive contents. Keep the files for inspection, and ask Justin about an unexpected credential, database, or large artifact. |
| The manager says the handoff commit differs from the branch tip | Check the current branch and latest push. If the new tip is the version the contributor wants reviewed, run handoff again and update the existing PR and message with its full commit. |
| A Git operation is already in progress, or a push reports non-fast-forward | Record the error and contact Justin. Avoid force-pushing, resetting, or deleting the branch while preserving the work. |
