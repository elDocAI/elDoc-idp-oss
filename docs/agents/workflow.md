# Agent Workflow

## 1. Purpose

This document defines how an agent reads requirements, persists task state, handles artifacts, commits and pushes, maintains documentation, and reports results. It complements the repository-level [AGENTS.md](../../AGENTS.md).

## 2. Read first

- Read [AGENTS.md](../../AGENTS.md), this document, and the documentation relevant to the task before changing code.
- Read the existing source, tests, configuration, and history of the area being modified before editing it.
- Explicit later project-owner instructions take precedence over these guidelines and over historical documentation. Record approved requirement or behavior changes in the appropriate document rather than leaving them in conversation history.
- Implement and verify the requested work. Do not stop at a plan, a skeleton, a mockup, or an unsupported claim of completion.
- Resolve routine implementation choices within the authorized scope. Ask for an owner decision only when information is genuinely required, a fixed requirement must change, or a consequential ambiguity cannot be resolved from the repository.

## 3. Task persistence and context management

- Complex tasks may be documented in `.agent-tasks/current-task.md`. When the project owner references that file, treat it as the authoritative specification for the current task.
- Maintain `.agent-tasks/task-progress.md` with completed phases and verification results, relevant commit IDs and push status, task-specific implementation decisions, blockers, and remaining work.
- Update the progress file after each completed phase and before ending or handing off a substantial session.
- After context compaction or session restart, reread the task specification and the progress file before continuing. Do not repeat work that is already completed and verified unless a concrete defect is found or the specification requires rework.
- `.agent-tasks/` contents are local-only and not committed.

Do not rely on conversation history, compaction summaries, or agent-generated notes for exact requirements.

## 4. Files and browser artifacts

- Work inside the project directory and use project-relative paths.
- Save Playwright and browser artifacts under `.playwright-mcp/`:
    - screenshots: `.playwright-mcp/screenshots/`;
    - reports, traces, and temporary browser files: `.playwright-mcp/test-results/` and `.playwright-mcp/tmp/`.
- Save agent logs and other session artifacts in ignored project-local paths, for example `.playwright-mcp/tmp/`.
- The application itself writes embedded MongoDB data, binary caches, and Tesseract language data to the OS temp directory by design. That runtime data is not an agent artifact; see [local-runtime.md](local-runtime.md) for the paths and overrides.
- Do not save project artifacts outside the project directory. Do not change filesystem permissions to bypass tool or MCP security restrictions.
- Do not commit generated runtime data, secrets, credentials, user documents, compiled artifacts, or confidential screenshots. Keep only curated, intentionally tracked evidence.

## 5. Git commits and pushes

- Inspect Git status and the current branch before starting. Preserve unrelated working-tree changes; never reset, discard, or overwrite them.
- Review the diff before committing and stage only the intended files. Keep commits focused and separately reviewable.
- After a completed, validated change set, create a commit with a meaningful message and push it to the current branch when the owner requests or the project workflow expects it.
- The remote is the project's GitLab instance. Do not force-push, rewrite shared history, switch protected branches, or merge and deploy without separate authorization.
- If the directory is not a Git checkout, the remote is missing, or authentication fails, preserve local work and report the exact incomplete action. Never claim a push succeeded when it did not, and never expose credentials.
- Keep `.playwright-mcp/` and other local agent artifacts untracked unless the owner explicitly asks for them to be included.

## 6. Documentation maintenance

- Keep documentation current in the same change set as the behavior it describes. Update `README.md`, `configs/eldoc-idp-oss.conf` comments, and `docs/` material when they describe something that changed.
- Agent-related documentation belongs in `docs/agents/`. Product, architecture, and operational documentation belongs elsewhere in `docs/` or the repository root.
- Consolidate superseded wording instead of appending dated change logs or historical narratives; use Git history for chronology.
- Verify links and referenced paths when moving or renaming documentation. Do not delete documentation that records unique, still-relevant evidence or decisions.
- Do not edit generated documentation artifacts by hand (for example `THIRD_PARTY_NOTICES.md` and the license inventory). Regenerate them with the documented script and only when the change requires it.

## 7. Completion and status reports

Report, at minimum:

- what changed and which components or pages are affected;
- which checks were actually executed (build, tests, browser flows) and their results;
- what could not be verified and why;
- remaining limitations, blockers, or follow-up work;
- commit and push status when a commit was requested.

Never present an unexecuted check as passed, and never hide build failures, tool limitations, or incomplete pushes.

## 8. Writing style

- Prefer commas, colons, semicolons, parentheses, or separate sentences over dashes.
- When a dash is appropriate, prefer en dashes (U+2013) over em dashes (U+2014). Use spaces around en dashes used as sentence punctuation, and no spaces for numeric ranges (for example, `2025–2026`).
- Keep documentation concise, factual, and free of unsupported claims.
