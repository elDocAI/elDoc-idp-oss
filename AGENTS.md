# Agent Guidance

These instructions apply to the entire repository and to any coding agent working on this project. Start with [`docs/agents/README.md`](docs/agents/README.md) for the list of agent documents and when to read each one.

## 1. General principles

- Follow the existing project architecture, conventions, and established implementation patterns.
- Preserve existing functionality, business workflows, navigation, permissions, validation, and application lifecycle behavior unless explicitly instructed otherwise.
- Keep changes focused on the requested scope. Avoid unrelated refactoring, architectural changes, dependency updates, or functional modifications.
- Preserve existing working-tree changes. Do not revert, overwrite, or restore unrelated files without explicit authorization.
- Prefer maintainable, reusable solutions over task-specific workarounds.
- Inspect relevant existing implementations before introducing new patterns or components.
- Do not assume historical documentation accurately represents the current codebase or runtime. Verify when necessary.

## 2. Architecture and implementation

- Respect the separation between frontend presentation, application logic, backend services, security, and persistence.
- For UI-focused work, do not modify business logic, backend APIs, authentication, authorization, data models, or persistence unless explicitly required.
- Preserve Jakarta Faces and PrimeFaces component lifecycle behavior, including validation, AJAX processing, partial updates, and managed component state.
- Prefer supported framework APIs and established project abstractions over direct manipulation of framework-managed DOM structures.
- Keep shared functionality centralized. Avoid duplicating implementation logic, styling, or configuration across pages.
- Do not weaken, bypass, or disable authentication, authorization, validation, or other security checks to make a change work.
- Never commit secrets, credentials, tokens, certificates, or user documents, and never write them to logs.
- Keep runtime data, generated caches, and local configuration out of source control.

## 3. Frontend and UI/UX

For all UI development and modernization involving Jakarta Faces and PrimeFaces, follow the detailed requirements in the [design-system documentation](docs/design-system/README.md), especially [`implementation-guidelines.md`](docs/design-system/implementation-guidelines.md).

The approved Stitch export in [`docs/design-system/`](docs/design-system/README.md) is the visual authority; the [PrimeFaces Aura Showcase](https://showcase.primefaces.org/theming.xhtml?theme=aura) is the implementation baseline.

General principles:

- Maintain a modern, clean, lightweight, consistent, accessible, and responsive enterprise UI.
- Follow established elDoc branding, shared design tokens, reusable components, and PrimeFaces Aura design conventions.
- Preserve existing functionality, navigation, workflows, component behavior, validation, and framework lifecycle compatibility.
- Prefer improvements to shared layouts, templates, and reusable styles over page-specific implementations.
- Stylesheets are generated: the CSS files under `modules/eldoc-idp-web/src/main/webapp/resources/eldoc/5_0_0/css/` are compiled from the SCSS sources in `modules/eldoc-idp-web/scss` by the scripts in that folder. Change the SCSS source and regenerate the CSS instead of editing generated CSS. See [`build-and-test.md`](docs/agents/build-and-test.md) for the generation rules.
- Avoid unnecessary styling overrides, duplicated CSS, excessive specificity, and unrelated UI refactoring.
- Verify both visual appearance and interactive behavior in the running application using browser automation when available.
- Apply the detailed design, implementation, accessibility, and verification requirements from the referenced document.

These PrimeFaces-specific requirements apply only to the relevant Jakarta Faces / PrimeFaces modules, not automatically to other frontend technologies.

## 4. JavaScript

- Prefer modern native JavaScript for new application-level functionality where practical.
- When modifying existing jQuery-dependent application code, consider replacing the affected functionality with native JavaScript if doing so is straightforward and safe.
- Do not perform repository-wide migrations unless explicitly requested.
- Do not remove or interfere with dependencies required internally by PrimeFaces or other frameworks.
- Preserve event handling, initialization, AJAX compatibility, validation, and partial-update behavior.
- Use framework-supported APIs when interacting with managed UI components.

## 5. Browser testing and UI verification

Full requirements: [`docs/agents/browser-testing.md`](docs/agents/browser-testing.md).

- Use browser automation tools, such as Playwright MCP when available, to verify actual application behavior rather than relying exclusively on source inspection.
- When a reference application is provided, treat it as read-only unless explicitly authorized otherwise.
- Use isolated browser contexts when authentication sessions or cookies could interfere with one another.
- Compare equivalent application states and viewport dimensions when evaluating UI differences.
- Verify both appearance and functionality, including navigation, forms, dialogs, overlays, keyboard interactions, filtering, sorting, pagination, and responsive layouts where relevant.
- Avoid executing destructive actions, modifying production/reference data, or triggering irreversible workflows without authorization.
- After changes, verify the deployed application, inspect browser console errors and relevant network failures, and address regressions.
- Account for asynchronous updates, animations, and framework-managed rendering when evaluating UI behavior.
- Save browser artifacts under `.playwright-mcp/` (screenshots in `screenshots/`, reports and temporary files in `test-results/` or `tmp/`) and keep them untracked.
- Clearly distinguish verified functionality from functionality that could not be tested. Never mark an unexecuted browser check as passed.

## 6. Build, deployment, and runtime

Build, test, and dependency rules: [`docs/agents/build-and-test.md`](docs/agents/build-and-test.md). Launching and stopping: [`docs/agents/local-runtime.md`](docs/agents/local-runtime.md).

- Identify the project's existing build and deployment workflow before executing commands.
- Follow the conventions of the current operating system, development environment, and build tooling.
- Reuse existing application instances and development processes whenever appropriate. Avoid starting duplicate servers or conflicting background processes.
- Do not interrupt or restart running services, watchers, or development processes unless required and authorized.
- Verify that changed resources have reached the active deployment before evaluating browser results.
- If deployed content appears stale, investigate resource synchronization, caching, and deployment state before changing application code.
- Distinguish infrastructure or server failures from application defects.
- Run validation appropriate to the scope of changes. Avoid unnecessary full builds for documentation-only or similarly nonfunctional modifications.
- Build on the same OS and CPU architecture as the target runtime; the packaged native libraries are host-specific.

## 7. Verification and reporting

Full requirements: [`docs/agents/workflow.md`](docs/agents/workflow.md) (completion and status reports).

- Review the diff to ensure changes remain within the requested scope.
- Run appropriate syntax, build, and functional checks based on the files and behavior modified.
- Report significant implementation changes, affected components, executed checks, and any remaining limitations.
- Do not claim successful verification of functionality that was not actually tested.
- Record detailed test evidence in the project's designated verification documentation when required.
- Report commit and push status when a commit was requested; never claim a push that did not succeed.

## 8. Environment-specific instructions

Environment-specific configuration, IDE run configurations, local server management, artifact synchronization, and machine-dependent commands are maintained separately from these general guidelines.

### Local runtime

When launching, accessing, or stopping the application from a command line, follow [`docs/agents/local-runtime.md`](docs/agents/local-runtime.md). It covers the build command, the direct JAR launch, the standard development credentials, the long first start (embedded MongoDB and Tesseract language-data downloads), readiness checks, and local state reset.

Key points:

- Run the application from the repository root so `configs/` resolves; the HTTP listener binds port `8080`.
- Maintenance-mode login is `admin`, with the password configured by the `dev-idea` and `dev-mvn` Maven profiles (see the runtime guide); pass the same value when launching the JAR directly.
- The first launch downloads several hundred megabytes of MongoDB binary and about 1.4 GB of Tesseract language data. Startup can take minutes, and every URL returns HTTP 404 until dependency initialization completes. This is expected, not a deployment failure.

### IntelliJ IDEA

When working within the IntelliJ IDEA development environment, follow the additional instructions in [`docs/agents/intellij-idea.md`](docs/agents/intellij-idea.md).

Apply these instructions only when relevant to the active development environment. Do not assume IntelliJ IDEA-specific configurations or workflows apply to other IDEs, command-line environments, or CI/CD pipelines.

## 9. Historical documentation

Consult existing project documentation for previous design decisions, implementation details, and verification results when relevant.

Historical records provide context but must not override the current implementation, explicit task requirements, or verified runtime behavior.

## 10. Semantic code navigation and editing

Full requirements: [`docs/agents/serena.md`](docs/agents/serena.md).

- Serena MCP is the primary tool for semantic navigation, symbol and reference analysis, diagnostics, and reference-aware refactoring, mainly for the Java sources.
- Prefer Serena when investigating declarations, implementations, callers, references, or inheritance, and before modifying non-trivial existing code.
- Use text search for literal strings, configuration values, labels, log messages, and filenames. Do not present text-search results as complete semantic analysis.
- Do not use `sed`, `perl`, or uncontrolled global replacement for structural Java refactoring when a reference-aware operation is available.
- Serena complements but does not replace the Maven build, tests, and browser verification.

## 11. Serena memory

Full requirements: [`docs/agents/serena.md`](docs/agents/serena.md) (section 5).

- At the start of substantial work, inspect the available memories and read the top-level index memory first, when present.
- Store only stable, non-obvious project knowledge that avoids expensive rediscovery. Do not store working-tree state, task progress, temporary paths, volatile details, or generic framework knowledge.
- Memories never override `AGENTS.md`, documentation, source code, tests, or explicit owner instructions. Update or remove stale memories.
- Never store secrets, credentials, or user data in memories.

## 12. Task persistence and context management

Full requirements: [`docs/agents/workflow.md`](docs/agents/workflow.md).

- Complex tasks may be specified in `.agent-tasks/current-task.md`; when the owner references it, treat it as the authoritative task specification.
- Maintain `.agent-tasks/task-progress.md` with completed phases, verification results, commit IDs, decisions, blockers, and remaining work. Update it after each completed phase and before handing off.
- Reread the specification and progress file after context compaction or session restart. Do not repeat completed and verified work unless a defect is found.
- Do not rely on conversation history or compaction summaries for exact requirements.

## 13. Files and artifacts

- Work inside the project directory and use project-relative paths.
- Keep Playwright and browser artifacts under `.playwright-mcp/` and agent logs in ignored project-local paths.
- The application's own embedded MongoDB data, binary cache, and Tesseract language data live in the OS temp directory by design; see [`docs/agents/local-runtime.md`](docs/agents/local-runtime.md).
- Do not change filesystem permissions to bypass tool or MCP restrictions, and do not commit generated runtime data, secrets, credentials, or user documents.

## 14. Dependency research and compatibility

- Use the built-in `websearch` tool for versions, release notes, compatibility notes, and documentation, and prefer authoritative sources such as vendor documentation, official release notes, Maven Central, and official artifact repositories.
- Pin exact versions in the root `pom.xml` properties; do not introduce new floating or snapshot dependencies without a documented reason.
- Keep server-provided Jakarta APIs in Maven `provided` scope, and do not package duplicate server implementations.
- Validate a dependency or version change by building and running the packaged application before reporting it as working.
- Report unavailable research tools and the alternative sources used. Do not silently substitute a different version.

## 15. Git commits and pushes

Full requirements: [`docs/agents/workflow.md`](docs/agents/workflow.md) (section 5).

- Inspect Git status and the current branch first; preserve unrelated working-tree changes.
- Review the diff and stage only intended files. Keep commits focused and separately reviewable.
- After a completed, validated change set, commit with a meaningful message and push to the current branch when requested.
- Do not force-push, rewrite shared history, switch protected branches, or merge and deploy without authorization. Never claim a push that did not succeed.

## 16. Documentation maintenance

- Keep documentation current in the same change set as the behavior it describes.
- Agent documentation belongs in `docs/agents/`; product and architecture documentation belongs elsewhere in `docs/` or the repository root.
- The design-system documentation is [`docs/design-system/README.md`](docs/design-system/README.md).
- Consolidate superseded wording instead of appending dated change logs. Use Git history for chronology.
- Regenerate generated artifacts such as the license inventory with the documented script instead of editing them by hand.
