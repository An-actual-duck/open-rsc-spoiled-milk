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

Start conservatively:

- New contributors: pull requests from forks or limited write access.
- Trusted contributors: write access after they understand the workflow.
- `main`: protected for everyone except controlled owner merges.

## Code Owners

[`.github/CODEOWNERS`](../../.github/CODEOWNERS) identifies the maintainer for
review requests.

[main-ruleset]: https://github.com/An-actual-duck/open-rsc-spoiled-milk/rules/28013985
