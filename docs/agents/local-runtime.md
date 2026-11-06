# Local Runtime Guide

## 1. Purpose and scope

This document describes how to build, launch, access, and stop the elDoc IDP OSS application locally from a command line, and records the runtime specifics that cause most confusion on a fresh environment: the long first start, the maintenance-mode credentials, and the embedded dependency downloads.

Follow the repository-level [AGENTS.md](../../AGENTS.md) in addition to this guide. IDE-specific workflows are documented separately in [`intellij-idea.md`](intellij-idea.md).

The details below were verified by launching the application from the repository root on Linux with JDK 21 (revision `7.2026.4`). Re-verify commands for other operating systems, shells, or revisions instead of assuming portability.

## 2. Prerequisites

- JDK 21 (`java -version` must report 21).
- The Maven Wrapper (`./mvnw`, `./mvnw.cmd` on Windows); no separate Maven installation is required.
- Network access on the first launch only, for the embedded MongoDB binary and the Tesseract language data (see section 5).
- Write access to the directories used for the embedded database, binary cache, and language data (defaults live under the OS temp directory).

## 3. Build

From the repository root:

```sh
./mvnw -pl :eldoc-idp-web -am package
```

The executable artifact is written to:

```text
modules/eldoc-idp-web/target/elDoc-idp-oss-<revision>-server-<platform>.jar
```

`<revision>` is the root `pom.xml` `revision` property (for example `7.2026.4`) and `<platform>` is `linux`, `mac`, or `win` according to the build host. Build on the same OS and CPU architecture on which the application will run, because the host-specific native libraries are packaged into the JAR.

Before rebuilding, check whether a usable JAR already exists in `target/` and is not older than the current source revision:

```sh
ls -l modules/eldoc-idp-web/target/*-server-*.jar
git log -1 --format=%cd
```

Reuse an up-to-date artifact instead of triggering a full multi-minute build. Rebuild whenever Java sources, resources, or POMs changed.

### Faces Production mode over local HTTP

`web.xml` uses Faces `Production` mode and enables
`org.apache.shiro.ee.secure-session-cookie.disabled` for local HTTP on port 8080.
`SessionCookiePolicyListener` observes Shiro environment publication during startup
and honors this flag despite Shiro 3.0.1's operator-precedence bug. Shiro retains
its automatic listener registration; do not also register its loader in `web.xml`. It only changes the
session cookie's Secure flag when the override is true; other cookie attributes
and Shiro session tracking remain unchanged. Remove the parameter or set it to
`false` for HTTPS deployments so Shiro's secure default applies. Restart/redeploy
after changing it, and clear any previously stored secure session cookie before
retesting HTTP. This override does not configure remember-me or form-replay cookies.

## 4. Launch

Run from the repository root so the relative configuration directory resolves:

```sh
mkdir -p .playwright-mcp/tmp
nohup java \
  -Dai.eldoc.idp-oss.configDir=configs \
  -Dai.eldoc.maintenance.password='dev@CC12#4' \
  -Xmx2048m \
  -jar modules/eldoc-idp-web/target/elDoc-idp-oss-<revision>-server-linux.jar \
  > .playwright-mcp/tmp/eldoc.log 2>&1 &
```

Notes:

- Quote the password value: it contains `#`, which is otherwise treated as a shell comment.
- Run the process detached and redirect output to a project-local, ignored log file so startup progress and the maintenance password can be read without blocking the terminal. Agent artifacts stay inside the project; see [workflow.md](workflow.md).
- `-Djava.net.preferIPv4Stack=true` is also passed by the Maven development profiles and is harmless to include.
- Prefer this direct JAR launch for agent work. It is faster and more predictable than the Maven development profiles, which additionally perform a build and deploy an exploded directory.

### Maven development profiles

`modules/eldoc-idp-web/pom.xml` provides two development profiles with one shared Payara Micro start configuration. They differ only in the exploded directory they deploy:

| Profile | Deploys | Intended use |
| --- | --- | --- |
| `dev-idea` | `out/artifacts/eldoc` (IntelliJ artifact, classes in `WEB-INF/classes`) | The IntelliJ run configuration `dev_idea.xml` |
| `dev-mvn` | `modules/eldoc-idp-web/target/elDoc-idp-oss` (Maven build, classes archived under `WEB-INF/lib`) | CLI/agent runs from a Maven build |

`dev-mvn` from the repository root:

```sh
./mvnw -Pdev-mvn -pl :eldoc-idp-web -am package payara-micro:start -Dmaven.test.skip=true
```

The `package` step creates `target/elDoc-idp-oss`; the start goal then deploys that directory at `/`. The build also produces the executable Uber JAR, so this loop is heavier than the direct JAR launch. Restart the server after rebuilding; do not assume that every rebuilt artifact is reloaded in place.

For `dev-idea`, use the IntelliJ run configuration, which builds the `eldoc-exploded` artifact before starting.

### Configuration

- The default configuration is `configs/eldoc-idp-oss.conf`.
- The directory is resolved from `-Dai.eldoc.idp-oss.configDir=<dir>` first, then `ELDOC_IDP_OSS_CONFIG_DIR`, then discovery of `configs/eldoc-idp-oss.conf` in the working directory or its parents.
- A non-blank explicit directory is used as provided; it is not replaced when the file is missing. Confirm the log line `elDoc Configuration directory resolved to: configs` after startup.

### Port

The HTTP listener binds `0.0.0.0:8080` by default. Confirm with:

```sh
ss -ltn | grep ':8080'
```

## 5. First launch

The first launch performs one-time initialization that can take several minutes:

1. The embedded single-node MongoDB replica set starts on a loopback port (`127.0.0.1:<random>`, replica set `eldoc-rs`) because `ELDOC_MONGODB_URI` is unset in `configs/eldoc-idp-oss.conf`.
2. A platform-specific MongoDB binary is downloaded into `${java.io.tmpdir}/eldoc-idp-oss-db-bin`; the time depends on the connection and the binary is reused afterwards.
3. Because `idp.tessdata.path` is empty, Tesseract language data (about 1400 MB of downloads, roughly 1.7 GB on disk) is downloaded into `${java.io.tmpdir}/eldoc-idp-oss-tessdata-best`. This is the longest step.
4. The tenant database (`eldoc_idposs` for `tenantId=idposs`) is created and initialized on first use.

On Linux the defaults resolve to:

| Purpose | Path |
| --- | --- |
| Database files | `/tmp/eldoc-idp-oss-db-data` |
| MongoDB binary cache | `/tmp/eldoc-idp-oss-db-bin` |
| Tesseract language data | `/tmp/eldoc-idp-oss-tessdata-best` |

Subsequent launches reuse these directories and start in well under a minute.

### Readiness signals

Do not interpret the first `404` responses as a deployment failure. During startup Payara Micro reports the module as deployed before the application finishes dependency initialization, so every path (including `/`, `/login.xhtml`, and static resources) returns HTTP 404 while the downloads run. In the observed first launch this lasted roughly 110 seconds.

Startup is complete when the log contains, in order:

```text
Dependency initialization completed
...
elDoc: Servlet Context ready
...
Loading application [ROOT] at [/]
```

The reliable external check is the login page:

```sh
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/login.xhtml   # 200 when ready
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' http://127.0.0.1:8080/
```

`/` answers `302` to `/login.xhtml` for an anonymous session. Poll the login page rather than the root URL, and wait for the readiness log lines instead of assuming a hang.

## 6. Access and credentials

- URL: `http://127.0.0.1:8080` (welcome page `idp/start.xhtml`, unauthenticated users are redirected to `/login.xhtml`).
- Login page title: `Login | elDoc™ GenAI Document Excellence`.

The default administrator account comes from maintenance mode (`LocalMaintenanceModeRealm` in `eldoc-idp-core`):

| Setting | JVM option | Environment variable | Default |
| --- | --- | --- | --- |
| Login | `ai.eldoc.maintenance.login` | `ELDOC_AI_MAINTENANCE_LOGIN` | `admin` |
| Password | `ai.eldoc.maintenance.password` | `ELDOC_AI_MAINTENANCE_PASSWORD` | generated at startup |

### Standard development password

The `dev-idea` and `dev-mvn` Maven profiles in `modules/eldoc-idp-web/pom.xml` share a Payara Micro start configuration that sets the development password:

```text
-Dai.eldoc.maintenance.password=dev@CC12#4
```

Use the same value when launching the JAR directly, so the local, IDE, and agent environments share one credential:

- Login: `admin`
- Password: `dev@CC12#4`

The password must satisfy the maintenance-mode policy: 10-50 characters with at least one uppercase letter, one lowercase letter, one digit, and one special character from `!@#&():?*~$^+=<>`. The login must be at least 5 characters. A value that violates the policy is not accepted; the realm reports `Maintenance mode is disabled or not valid` and the login fails.

When no password is configured, a random 10-character password is generated once per JVM start and printed to the console as:

```text
[MAINTENANCE MODE] No password was configured. Generated default maintenance password: ...
```

The login page then shows the placeholders `Hint: admin` and `Hint: see console output`, and the password must be read from the server log for that run.

Never change the committed development password or the maintenance-mode defaults as part of unrelated work.

## 7. Stopping and restarting

Find and stop the process, then verify the port is free:

```sh
pkill -f 'elDoc-idp-oss-.*-server-.*\.jar'
sleep 5
ss -ltn | grep ':8080' || echo 'port 8080 free'
```

Restart only when required: Java changes, resource changes not picked up by a running watcher, or a wedged process. A restart repeats the full startup sequence, but reuses cached downloads.

Do not start a second instance while one is running; the HTTP port bind fails and the log becomes misleading.

## 8. Resetting local state

- Reset the database: stop the application and delete `${java.io.tmpdir}/eldoc-idp-oss-db-data`.
- Force a fresh MongoDB download: also delete `${java.io.tmpdir}/eldoc-idp-oss-db-bin`.
- Force a fresh language-data download: delete `${java.io.tmpdir}/eldoc-idp-oss-tessdata-best`.

Overriding `ELDOC_EMBEDDED_MONGODB_DATA_DIR` and `ELDOC_EMBEDDED_MONGODB_BINARY_CACHE_DIR` keeps state outside the temp directory, which OS cleaners may wipe. Point `idp.tessdata.path` at a prepared TessData directory to skip the language-data download entirely.

Deleting local data is destructive: confirm with the user before removing non-default or shared directories.

## 9. Troubleshooting

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Every path returns 404 on first launch | Dependency initialization still running | Wait for `Dependency initialization completed` and poll `/login.xhtml` |
| Port 8080 already in use | Another instance is running | Inspect `ss -ltnp` and the process list; reuse the existing instance |
| Login rejected with a valid-looking password | Password fails the complexity policy, or a different value is configured | Check the configured JVM option/env variable or read the generated password from the log |
| `elDoc Configuration directory resolved to:` shows an unexpected path | Wrong working directory or override | Launch from the repository root or pass an absolute `configDir` |
| Startup log shows only MongoDB output | Normal: `mongod` stdout is mirrored into the application log | Look for `elDoc:`/`ai.eldoc` lines to follow application progress |
| Requests fail after a rebuild | Old process still serving the previous artifact | Stop the process, confirm the port is free, relaunch the new JAR |

## 10. Browser verification

See [browser-testing.md](browser-testing.md) for the full browser-testing requirements and artifact locations.

- Verify the running application, not only the sources: load `/`, sign in with the credentials from section 6, and exercise the affected pages.
- A clean run shows no browser console errors and no `SEVERE`/`ERROR` entries in the server log (filter out the mirrored `mongod output` lines).
- Useful authenticated pages: `/idp/start.xhtml` (AI document processing), `/workflow/inbox.xhtml`, `/workflow/status.xhtml` (AI processing queue), `/admin/config.xhtml` (Configuration), `/admin/logs.xhtml` (System logs).
- The Playwright MCP browser profile configured in `.ai/mcp/mcp.json` uses a 1600x900 viewport and the Edge channel on Windows; size the browser explicitly when the viewport matters.
- Playwright MCP writes screenshots only inside the workspace (repository root or `.playwright-mcp/`). Use that directory rather than system temp paths.
- A freshly initialized database has no documents: empty inbox, queue, and workflow lists are expected and are not a defect.
- Avoid document uploads, configuration edits, and other data-changing actions unless the task requires them.
