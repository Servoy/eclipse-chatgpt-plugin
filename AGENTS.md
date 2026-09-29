# Agent operating guide — AssistAI (Eclipse plugin)

Project reference (structure, servers, testing, build) is in `CLAUDE.md`. This file
is about **how** to operate the tools in this workspace. Read it before editing code
or calling any Eclipse capability.

## Everything runs through Code Mode

This harness exposes the Eclipse MCP servers (and `memory`, `time`, the search/graph
tools, etc.) **only through Code Mode**. There is no "normal tool" path for them:

- Call the `execute` tool and write JavaScript that calls the tool by the exact `path`
  from the catalog, e.g. `await tools["eclipse-ide"].getCompilationErrors({ ... })` or
  `await tools["eclipse-coder"].replaceString({ ... })`.
- The catalog is partial. Find a tool with `search(...)` **inside** an `execute` script
  first (it is synchronous — call it without `await`), then call it by the returned
  `path`. Do not guess tool names.
- **Never** call an Eclipse/MCP tool as if it were a top-level tool (e.g.
  `eclipse-ide_getCompilationErrors`). That name does not exist in Code Mode and the
  call fails with *"No tool named ... is currently available."* Preserve the bracket
  form exactly: `tools["eclipse-ide"].getCompilationErrors`, not `tools.eclipse_ide...`.
- To call a single method, still write a one-line `execute` script that awaits it and
  returns the result. There is no shortcut that bypasses `execute`.
- `search` and `execute` themselves are the only entry points; `await` every tool call
  whose result you use.

### Only these tools are direct (not Code Mode)

The built-in filesystem/inspection tools are called normally: `read`, `grep`, `glob`,
and `shell`. Use them for reading files, searching, and running the shell (e.g. a full
Maven build). Everything else goes through `execute`.

## Editing files

**Every file inside an Eclipse workspace project MUST be edited through the
`tools["eclipse-coder"]` tools** — `replaceString`, `applyPatch`, `insertIntoFile`,
`deleteLinesInFile`, `createFile`, `renameFile`, `deleteFile`, refactorings, etc. This
keeps open editors in sync, triggers incremental compilation, and records local-history
undo. Do **not** use the built-in `edit`/`write` tools on workspace files: they write
straight to disk behind Eclipse's back, so the editor, the JDT model and undo history
drift out of sync.

- Multi-hunk changes → `tools["eclipse-coder"].applyPatch`.
- Single targeted replacement → `tools["eclipse-coder"].replaceString`.
- New file → `tools["eclipse-coder"].createFile`.

The built-in `edit`/`write` tools are acceptable **only** for files that are not inside
any Eclipse project — repo-root docs (`CLAUDE.md`, `AGENTS.md`), CI YAML, shell scripts
under `tools/`, and the like. When in doubt, if the file is under a `plugins/…` or
`tests/…` project, use `eclipse-coder`.

## After any code change

Run `tools["eclipse-ide"].getCompilationErrors({ projectName, severity: "ERROR" })` for
the affected project(s) and fix what it reports before moving on. For behavior changes,
run the relevant tests through `tools["eclipse-pde"].runJUnitPluginTests` (PDE harness)
or the plain-JUnit runners — see `CLAUDE.md` for which tests use which harness.

## `memory` / `think` and other reasoning tools

`tools.memory.think`, `tools.memory.remember`, `tools.memory.recall`, etc. are Code Mode
tools like everything else — reachable only via `execute`. `think` obtains no new
information, so there is no benefit to routinely wrapping reasoning in it; call the
`memory` tools only when you actually want to persist or retrieve a note across the
session. Plain reasoning belongs in your normal response, not in an `execute` script.

## Full builds

A full `mvn clean verify` runs in the **`shell`** (a direct tool), not through the
Eclipse MCP tools — see `CLAUDE.md` for the Maven/toolchain prerequisites. Use the
Eclipse `runMavenBuild`/PDE tools for scoped, in-IDE runs; use the shell for the whole
reactor.
