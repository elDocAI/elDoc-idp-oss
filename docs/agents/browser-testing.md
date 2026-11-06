# Browser Testing with Playwright MCP

## 1. Purpose

Playwright MCP is the primary tool for verifying actual application behavior in a real browser. Source inspection, unit tests, and screenshots alone are not substitutes for interacting with the running application.

## 2. Before you start

- Confirm the application is running and ready; see [local-runtime.md](local-runtime.md).
- Confirm which instance and URL you are testing (`http://127.0.0.1:8080` for local development).
- Set the viewport explicitly when layout matters, and compare equivalent states at equivalent sizes.
- Use isolated browser contexts when sessions or cookies could interfere with one another.

## 3. Required coverage

For changes that affect the UI or a user flow, verify the affected pages end to end:

- navigation, menu state, tabs, and breadcrumbs;
- forms, validation messages, required fields, and submit behavior;
- PrimeFaces lifecycle behavior: AJAX updates, partial rendering, dialogs, overlays, and component state after updates;
- data tables: filtering, sorting, pagination, selection, and empty-state rendering;
- keyboard interaction and accessible labels where relevant;
- responsive layouts at representative desktop and narrower widths;
- browser console errors and failed network requests.

Account for asynchronous updates, animations, and framework-managed rendering instead of asserting on transient DOM state.

## 4. Authentication and data

- Sign in with the maintenance-mode credentials described in [local-runtime.md](local-runtime.md).
- A freshly initialized database has no documents; empty inbox, queue, and workflow tables are expected and are not defects.
- Avoid destructive or irreversible actions (document uploads, deletions, configuration changes, workflow transitions) unless the task requires them, and never against production or reference data.
- When a reference application is provided, treat it as read-only unless explicitly authorized.

## 5. Artifacts

- Screenshots: `.playwright-mcp/screenshots/`.
- Reports, traces, and temporary browser files: `.playwright-mcp/test-results/` and `.playwright-mcp/tmp/`.
- Playwright MCP can only write inside the workspace. Do not attempt to write into system temp directories or change filesystem permissions to bypass that restriction.
- Keep `.playwright-mcp/` untracked, and do not commit confidential screenshots or captured user data.

## 6. Reporting

- State which pages and flows were actually exercised, at which viewport, and with which result.
- Report browser console errors, failed requests, and server log entries that are relevant to the change.
- If a required browser check is blocked (tool failure, browser startup problem, unavailable environment), report exactly what could not be verified and why. Never mark an unexecuted check as passed.
