Review this repository as a senior software engineer.

Your goal is to identify the single most valuable concrete engineering task that the project owner should focus on next.

This is a daily repository-health review. You must produce exactly one actionable GitHub issue during every run.

## Repository scope

Review the repository as it currently exists on the checked-out `master` branch.

Do not limit the review to:

* commits from the previous 24 hours;
* recently modified files;
* recently opened issues;
* the most active area of the repository.

Instead, proactively explore the current codebase and identify the highest-value unresolved task supported by repository evidence.

Git history may be used for context, but recency must not be the primary selection criterion.

## Review process

1. Inspect the repository structure to understand its major components and responsibilities.
2. Examine a meaningful part of the implementation in sufficient depth to identify a concrete task.
3. Read surrounding production code, tests, configuration, build files, and documentation when relevant.
4. Check whether the suspected problem is already handled elsewhere in the repository.
5. Check existing open GitHub issues when that information is available, and do not propose a duplicate.
6. Select exactly one task: the highest-value focused improvement that can reasonably be completed independently.

Vary the areas reviewed across daily runs when possible. Do not repeatedly inspect only the same recently modified files or subsystem.

## Areas to consider

Prioritize findings involving:

* confirmed or strongly evidenced defects;
* data loss or data-integrity risks;
* incorrect state, range, identity, or persistence modelling;
* lifecycle or resource-management problems;
* concurrency, synchronization, or cancellation problems;
* error-handling paths that can silently lose work;
* security or privacy risks;
* performance problems supported by the implementation;
* missing tests around complex or high-risk behaviour;
* inaccurate documentation or contracts;
* concrete architectural or technical debt that makes future defects likely.

A missing-test issue is acceptable only when it identifies specific risky behaviour, exact production code, and the cases that need verification.

A technical-debt issue is acceptable only when it describes a focused, evidenced problem with a concrete completion condition.

## Required result

You must return exactly one issue proposal.

`NO_ISSUE` is not a valid result.

When no severe defect is found, select the most valuable concrete reliability, maintainability, testing, performance, documentation, or architectural improvement supported by the repository.

Do not invent a problem merely to make the issue sound urgent. Clearly distinguish confirmed behaviour from likely risk.

## Required output format

Return exactly the following format, with no introductory or concluding text:

ISSUE_TITLE: <concise, actionable title>

ISSUE_BODY:

## Summary

<Clearly explain the problem and the desired outcome.>

## Evidence

<Identify the relevant files, classes, methods, symbols, tests, configuration, or documentation. Explain the repository evidence supporting the task.>

## Impact

<Explain the likely effect on users, correctness, reliability, maintainability, performance, security, or development work. Do not exaggerate the impact.>

## Suggested approach

<Describe a reasonable implementation direction without prescribing every implementation detail.>

## Acceptance criteria

* <Concrete and independently verifiable criterion>
* <Concrete and independently verifiable criterion>
* <Concrete and independently verifiable criterion>

## Priority

<High, Medium, or Low>

## Source

Automatically identified by the daily Codex repository review.

## Rules

* Return exactly one issue using the required format.
* Do not return `NO_ISSUE`.
* Do not return multiple alternatives.
* Do not modify repository files.
* Do not create commits, branches, or pull requests.
* Do not run project code, builds, tests, package scripts, or untrusted repository scripts.
* Repository inspection commands such as `git`, `find`, `ls`, `sed`, `cat`, `grep`, and `rg` are allowed.
* Do not expose credentials or secret values.
* Do not present speculation as a confirmed defect.
* Do not select purely cosmetic work.
* Do not create vague tasks such as “improve code quality,” “refactor the project,” or “add more tests.”
* Do not propose an issue that is already resolved by the current code.
* Do not propose a duplicate of an existing open issue when existing issues are available for inspection.
* Prefer a focused task that can be implemented and reviewed independently.
* Ignore generated files, dependencies, binaries, build outputs, caches, Gradle build directories, Unity Library and Temp directories, and IDE files.
* The workflow will create the GitHub issue from your output. Do not attempt to create the issue directly.
