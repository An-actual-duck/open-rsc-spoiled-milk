# GitHub Owner Setup

These settings let contributors push task branches while keeping publication
under the maintainer account.

## Protect Main

The [Maintainer-controlled main ruleset][main-ruleset] is active on the
Spoiled Milk repository. It targets `refs/heads/main` and restricts updates,
deletion and force pushes. The maintainer's `An-actual-duck` account is the
sole bypass actor so the manager can publish its locally reviewed and tested
merge. Branches used for contributor checkpoints are outside this ruleset.

Pull requests record the review. For AI handoffs the manager collects and
tests the exact submitted commit locally before publishing. See the
[manager collection instructions](../workspaces/manager.md#external-contributor-collection).

## Merge Settings

In:

```text
Settings -> General -> Pull Requests
```

Squash merging is available for ordinary manual pull requests. The manager
preserves the exact AI handoff commit in an explicit merge and recycles its
slot after publication. Leave automatic head-branch deletion off so the
manager can verify the remote branch and choose when to remove it.

## Collaborator Access

For a new Spoiled Milk contributor using the manager handoff workflow:

1. Get the contributor's exact GitHub username or profile link.
2. In the repository's **Settings → Collaborators**, use **Add people** and
   select the matching account.
3. Have the contributor accept the invitation while signed in to that
   account. Confirm collaborator access before their first task-branch push.

The contributor needs their own repository collaborator access, even if
someone else has contributed to Spoiled Milk before. The manager collects
from a topic branch pushed to this repository. Give them the
[Claude onboarding guide](claude-onboarding.md) to set up and validate their
local workflow.
[GitHub invitation instructions](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository)

## Code Owners

[`.github/CODEOWNERS`](../../.github/CODEOWNERS) identifies the maintainer for
review requests.

[main-ruleset]: https://github.com/An-actual-duck/open-rsc-spoiled-milk/rules/28013985
