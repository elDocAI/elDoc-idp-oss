# Agent Documentation

This folder contains the instructions and reference material for coding agents working on elDoc IDP OSS. The repository-level [AGENTS.md](../../AGENTS.md) is the primary contract; the documents here add detail and environment-specific procedures.

Read in this order unless the task clearly needs otherwise:

| Document | Read when |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | Always, before any work |
| [workflow.md](workflow.md) | Always: task contract, task persistence, artifacts, Git, reporting |
| [serena.md](serena.md) | Before non-trivial Java or web-resource investigation and refactoring |
| [build-and-test.md](build-and-test.md) | Before building, testing, or changing dependencies or versions |
| [browser-testing.md](browser-testing.md) | Before verifying UI behavior in a browser |
| [local-runtime.md](local-runtime.md) | Before launching, accessing, or stopping the application |
| [design-system/README.md](../design-system/README.md) | Before any UI, styling, or Jakarta Faces / PrimeFaces change |
| [intellij-idea.md](intellij-idea.md) | When working inside IntelliJ IDEA |

Precedence: explicit project-owner instructions, then `AGENTS.md`, then these documents, then historical notes. Documented procedures never override verified runtime behavior; when they disagree, verify and update the documentation.

Keep agent documentation in this folder. Product and architecture documentation belongs outside `docs/agents/`.
