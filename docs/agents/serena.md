# Serena: Semantic Navigation and Memory

## 1. Purpose

Serena MCP provides semantic code navigation, symbol and reference analysis, diagnostics, and reference-aware editing. Its main use in this project is the Java sources; support for web resources (XHTML, JavaScript, CSS/SCSS) depends on the language servers available in the active Serena backend. Use it to understand and change code safely, not only to find text.

## 2. When to use Serena

Use Serena before modifying non-trivial existing code, and whenever semantic relationships may influence the change:

- locating classes, methods, fields, constants, and other symbols;
- finding declarations, implementations, references, and callers;
- understanding cross-file dependencies and change impact;
- inspecting available semantic diagnostics;
- renaming symbols and performing reference-aware refactoring;
- inserting, replacing, or restructuring complete methods or classes.

Prefer Serena symbol-level editing for complete-symbol changes. For small edits inside an existing symbol, precise native edits are usually simpler and safer.

Text search (`grep`, `ripgrep`, `find`, glob) remains appropriate for literal strings, filenames, labels in properties files, configuration values, log messages, and other non-semantic lookups. Do not present text-search results as complete reference analysis when Serena can provide reliable results.

Do not use `sed`, `perl`, or uncontrolled global replacement for structural Java refactoring when a reference-aware operation is available.

## 3. Working procedure

For non-trivial changes:

1. Inspect the relevant symbols and surrounding context.
2. Identify affected references and callers where applicable.
3. Apply Serena refactoring or precise native edits as appropriate.
4. Inspect `git diff` for unintended changes.
5. Run the applicable diagnostics, build, and tests (see [build-and-test.md](build-and-test.md)).

## 4. Tool availability

- Serena is normally initialized and the project activated by the MCP server configuration. Do not call `activate_project` unless Serena reports that no project is active or the wrong project is active.
- When Serena is reached through a tool-discovery or Code Mode wrapper, discover the tool and follow its actual argument schema. Do not invent tool names, parameters, or wrapper arguments.
- If Serena is unavailable or cannot support an operation reliably, say so when relevant, use the appropriate native tools, and compensate with explicit reference checks and verification.
- Serena analysis and diagnostics complement, but never replace, the Maven build, JUnit tests, and Playwright browser verification.

## 5. Serena memory

Serena memories are durable, concise cross-session notes that avoid expensive rediscovery. They are supplementary working knowledge and never override `AGENTS.md`, documentation, source code, tests, Git history, or explicit owner instructions.

### Discovery model

- Memories are discovered progressively through references, forming a graph.
- At the start of substantial work, list the available memories and read the top-level index memory first, when present. Follow only the references relevant to the current task.
- Reference memories with the `mem:` prefix inside backticks, for example `mem:core`. The surrounding text should explain which aspect the referenced memory covers, not only its name.
- Use topics or folders to mirror project structure (for example modules such as `modules/eldoc-idp-core` or `modules/eldoc-idp-web`) when the number of memories grows.

### Style and content

- Write dense agent notes, not prose documentation. Prefer invariants and terse bullets over rationale and examples, unless an example prevents a likely mistake.
- Store only stable, non-obvious project knowledge that is likely to remain useful across sessions, such as important symbol locations, non-obvious conventions, stable tooling quirks, and durable implementation constraints that are not already documented well elsewhere.
- Do not store: current working-tree state, milestone progress, temporary task notes, one-off log or fixture paths, volatile line-level details, generic Java or PrimeFaces knowledge, or requirements already maintained in the repository documentation.
- Never store secrets, credentials, tokens, customer or user data, or document contents in memories.

### Maintenance

- Update or remove a memory when repository changes make it stale.
- Rename memories with Serena's memory tools so references stay consistent, and run `serena memories check` when stale references are suspected.
- Prefer references between memories over duplicated content.
