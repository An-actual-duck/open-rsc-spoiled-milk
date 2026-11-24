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

Recommend Claude Code for work in the local clone. If the contributor is
starting from a browser chat, they can paste this guide into their chat
and use this prompt:

```text
I'm new to Git and GitHub. Walk me through Part 1 of the Spoiled Milk
onboarding guide one step at a time. Tell me where to click or type and
what I should see. Wait for my result before moving on. Help me switch
to Claude Code once it can open my local copy of the repo.
```

## Part 1. Set up GitHub, Git and Claude Code

### 1. Create and secure a GitHub account

Explain that GitHub is the website where the project and its pull requests
live. Git is the command-line tool that copies the project to their computer
and saves their work. GitHub Desktop is an optional app. This guide uses
the Git command line and GitHub in the browser.

1. Have the contributor sign up for a personal GitHub account or sign in to
   an existing one. Have them follow the email verification prompt and open
   the verification message from GitHub. [GitHub signup](https://github.com/signup)
2. Have them check **Settings → Emails** for their verified address. If they
   prefer to keep it private in public commits, help them find and copy their
   GitHub-provided no-reply address for step 4. [GitHub email settings](https://github.com/settings/emails)
3. Walk them through two-factor authentication and have them store the
   recovery codes privately. They enter their own passwords and codes on
   the official site. Keep passwords, codes, tokens and recovery keys out
   of the chat and repository. [GitHub two-factor setup](https://docs.github.com/en/authentication/securing-your-account-with-two-factor-authentication-2fa/configuring-two-factor-authentication)
4. Have them send Justin their **GitHub username** or profile link. The
   username is the account handle shown on their profile. Wait for Justin's
   Spoiled Milk collaborator invitation, then have them open it and accept
   while signed in as that same account. They can install tools, clone and
   explore the public repository while the invitation is pending.
   [GitHub collaborator invitations](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository)

Before their first push, confirm they've accepted the invitation for
`An-actual-duck/open-rsc-spoiled-milk`. A successful clone of this public
repository proves read access. The first assigned task's checkpoint verifies
that the local credentials can push. If access is still pending, ask Justin
before trying a different remote. The manager collects topic branches from
the Spoiled Milk repository.

### 2. Install and check Git and Python

Ask which operating system they use. On Windows, have them open PowerShell
from the Start menu. Open a new window after installing a tool so it can see
the updated command search path. Help them run the checks one at a time and
look for a version number after each one.

```powershell
git --version
py -3 --version
```

On Windows, install Git for Windows with Git Credential Manager enabled.
Install a supported Python 3 release. The contributor helper needs 3.9 or
newer. On macOS or Linux, use their usual terminal and substitute `python3`
for `py -3`. If Windows has Python but no `py` launcher, use `python` after
confirming it reports a suitable version.
[Git downloads](https://git-scm.com/downloads) · [Python downloads](https://www.python.org/downloads/)

### 3. Install Claude Code

Help the contributor check whether their own Claude account has Claude Code
access. Anthropic lists eligible subscriptions and the Claude Console option
in its quickstart. If sign-in asks them to upgrade or add credits, let them
decide whether to proceed. They can still use browser chat for coaching and
run commands themselves in the meantime.
[Claude Code access and installation](https://code.claude.com/docs/en/quickstart)

On Windows, if WinGet is available, run the official package command in
PowerShell:

```powershell
winget install Anthropic.ClaudeCode
```

For other systems, or if WinGet is unavailable, use Anthropic's installation
instructions for that platform. Open a new terminal and check:

```powershell
claude --version
```

If the command is still missing, check Anthropic's installation
troubleshooting for the contributor's platform before trying another
installer. They'll launch and sign in to Claude Code from the project folder
in step 5. [Claude Code setup and troubleshooting](https://code.claude.com/docs/en/setup)

### 4. Clone, set up Git identity and connect to GitHub

Explain that a clone is a local copy of the project and its Git history. Open
the terminal in the parent folder where the contributor wants that copy.
Show them `Get-Location` in PowerShell or `pwd` on macOS or Linux if they
need to check where they are. Run the first two commands only if they need a
new clone. If they already have one, open the existing folder and start with
the last three checks.

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
before making changes.

If an existing clone lacks the helper or this guide, verify that `origin`
is the Spoiled Milk repository and `git status` shows a clean `main` before
updating. Fetch downloads the published history. The second command advances
their local branch only if Git can do so without a merge commit. If they're
on a task branch or have local edits, preserve that work and consult Justin
before switching branches.

```powershell
git fetch origin
git merge --ff-only origin/main
```

Check the existing commit identity:

```powershell
git config --get user.name
git config --get user.email
```

If either value is missing or belongs to a different person, set the
contributor's chosen display name and verified email or GitHub no-reply
address in this clone. Explain that these values appear on commits. The
browser or credential helper handles GitHub sign-in separately. Have them
substitute their real values, keeping the quotation marks, then rerun the
two checks above.

```powershell
git config --local user.name "YOUR DISPLAY NAME"
git config --local user.email "YOUR GITHUB EMAIL OR NO-REPLY ADDRESS"
```

When Git Credential Manager asks them to sign in over HTTPS, have the
contributor complete its browser flow with the account that accepted Justin's
invitation. This prompt may first appear on their first checkpoint. On
macOS or Linux, follow GitHub's instructions for their platform and existing
credential helper. If Git shows a raw password prompt, check the credential
helper setup before continuing.
[GitHub credential setup](https://docs.github.com/en/get-started/git-basics/caching-your-github-credentials-in-git)

Configure the repo's contributor helper with the contributor's actual GitHub
username. This sets up their branch prefix and enables the local Git hook that
blocks direct pushes to `main`.

```powershell
py -3 scripts/contributor-workspace.py setup --username YOUR_GITHUB_USERNAME --remote origin
py -3 scripts/contributor-workspace.py status
```

Check that setup reports `Main push protection: enabled` and that status
shows their username and `origin/main` as the published base.
If setup reports a custom Git hooks path or an unexpected remote, show the
message to Justin before changing the configuration.

### 5. Open the repo in Claude Code and take a read-only tour

From the same folder where `git rev-parse --show-toplevel` confirmed the
contributor's clone, run:

```powershell
claude
```

On first launch, have them follow Claude Code's browser login for their Claude
account, then return to the terminal. If prompted to trust the folder, have
them verify it is their Spoiled Milk clone before accepting. Claude Code can
load the project instructions in `CLAUDE.md` from this folder.
[Claude Code first session](https://code.claude.com/docs/en/quickstart#step-2-start-your-first-session) · [Project instructions](https://code.claude.com/docs/en/memory)

Give the contributor a first prompt to paste into Claude Code:

```text
I'm onboarding as an external Spoiled Milk contributor. Read CLAUDE.md,
AGENTS.md and docs/contributor-guides/claude-onboarding.md. Confirm my
project folder, Git status and contributor setup, then walk me through
the read-only tour. Explain any terminal commands before you run them.
Wait for Justin's first assignment before editing or pushing.
```

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

### 6. Start one assigned task

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

### 7. Review, test and checkpoint

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

### 8. Hand off the exact commit

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

### 9. Address review feedback and finish

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

### 10. Recover from common interruptions

| What Claude sees | What to do |
| --- | --- |
| GitHub refuses the topic-branch push | Check the branch and `origin`, then have the contributor verify which GitHub account is signed in. If access is missing, send Justin the username and error. A failed push can leave a valid local commit, so retry the helper after access is fixed. |
| `status` reports local changes or a backup that is missing or differs | Check `git status --short --branch` and review the changed files. Checkpoint the correct task branch, or use the documented rescue workflow if the work is misplaced. |
| A checkpoint is blocked by a staged file | Read the file list without sharing sensitive contents. Keep the files for inspection, and ask Justin about an unexpected credential, database, or large artifact. |
| The manager says the handoff commit differs from the branch tip | Check the current branch and latest push. If the new tip is the version the contributor wants reviewed, run handoff again and update the existing PR and message with its full commit. |
| A Git operation is already in progress, or a push reports non-fast-forward | Record the error and contact Justin. Avoid force-pushing, resetting, or deleting the branch while preserving the work. |
