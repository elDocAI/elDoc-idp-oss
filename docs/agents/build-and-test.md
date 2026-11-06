# Build, Tests, and Dependencies

## 1. Stack and layout

Pin the versions in the root `pom.xml` properties; do not silently substitute frameworks or major versions.

- Java 21 (`maven.compiler.release`), Jakarta EE 11, `jakarta.*` APIs.
- Runtime: Payara Micro (`payara.version`, currently `7.2026.10`), packaged as a single executable Uber JAR.
- Web UI: Jakarta Faces (Mojarra) with PrimeFaces (`17.0.0-SNAPSHOT`, resolved through the Sonatype snapshot repository) and OmniFaces 5.5.5.
- Security: Apache Shiro 3.0.1 (`shiro.version`) with the project's realms and `WEB-INF/shiro.ini`.
- Persistence: MongoDB via `mongodb-driver-sync` 5.13.0. Local development uses the embedded MongoDB fallback (`de.flapdoodle.embed.mongo` 5.0.0) that starts a single-node replica set automatically.
- Document processing: LangChain4j 1.22.0, PDFBox 3.0.8, TwelveMonkeys, Bytedeco JavaCPP/OpenCV/Tesseract, and the other runtime JARs staged into `libs/`.
- One root Maven reactor with `modules/eldoc-base-api`, `modules/eldoc-idp-core`, and `modules/eldoc-idp-web`.

There is no separate frontend build toolchain. Web resources live under `modules/eldoc-idp-web/src/main/webapp`. Stylesheets are authored in SCSS under `modules/eldoc-idp-web/scss` and compiled into `src/main/webapp/resources/eldoc/5_0_0/css/` by the scripts in that folder using Dart Sass; Maven does not compile SCSS.

### Generating the runtime CSS from SCSS

| SCSS source | Generated CSS (runtime resource) |
| --- | --- |
| `scss/eldoc/eldoc.scss` | `resources/eldoc/5_0_0/css/eldoc.css` |
| `scss/eldoc/eldoc-print.scss` | `resources/eldoc/5_0_0/css/eldoc-print.css` |
| `scss/eldoc/layout.scss` | `resources/eldoc/5_0_0/css/layout.css` |

Rules:

- Dart Sass must be installed and `sass` available on `PATH`; verify with `sass --version`. There is no Maven plugin or wrapper for it.
- Run one of the scripts in `modules/eldoc-idp-web/scss`:
  - Linux/macOS: `./compile-styles.sh` (one-off) and `./compile-styles-watch-mode.sh` (continuous). They resolve their own directory and can be started from anywhere.
  - Windows: `compile-styles.cmd` and `compile-styles-watch-mode.cmd`, started from the `scss` folder or through the IntelliJ IDEA run configuration.
- Every invocation compiles all three stylesheets in one Sass run with `--no-source-map --style=compressed`, so the generated CSS is minified and has no `.map` files. Watch mode recompiles on change; stop it when the styling work is finished.
- Generated CSS is committed and is build output: never edit a file under `resources/eldoc/5_0_0/css/` by hand. Hand-written changes are lost on the next compile.
- Do not add a stylesheet to the css folder without a matching SCSS source; Maven and the WAR assembly copy whatever CSS exists there, including stale files.
- Regenerate the CSS after every SCSS change and before packaging or browser verification. Building or redeploying alone does not recompile SCSS.
- To add a stylesheet, create `scss/eldoc/<name>.scss` with the `/*! ... */` license header (kept in compressed output) and add the `eldoc/<name>.scss:../src/main/webapp/resources/eldoc/5_0_0/css/<name>.css` pair to the normal and watch scripts on both platforms.
- Only the listed CSS files are compiled; non-CSS assets are not generated.

## 2. Build commands

From the repository root:

```sh
./mvnw verify                                   # full build and default test lifecycle
./mvnw -pl :eldoc-idp-web -am package           # package the executable Uber JAR
```

On Windows use `.\mvnw.cmd`. The executable artifact is:

```text
modules/eldoc-idp-web/target/elDoc-idp-oss-<revision>-server-<platform>.jar
```

See [local-runtime.md](local-runtime.md) for the launch command, readiness checks, and first-launch downloads.

The application core also stages Bytedeco runtime JARs into the repository-level `libs/` directory and the host-specific native JARs into `libs/linux`, `libs/mac`, or `libs/win`. Build on the same OS and CPU architecture on which the artifact will run; unsupported hosts fail the build before staging.

## 3. Tests

- JUnit 5 is the test framework. Test sources live in `modules/*/src/test/java`.
- `./mvnw verify` runs the tests bound to the default Maven lifecycle. Classes named `*IT` are integration tests and are not executed by the default lifecycle (no failsafe plugin is configured); run them explicitly when the change requires them, for example `./mvnw -pl :eldoc-idp-core test -Dtest=EmbeddedMongoServiceIT`. Some integration tests start an embedded MongoDB and may download it on first run.
- Add or update tests for changed behavior. Do not weaken, skip, or delete existing assertions to make a build pass.
- Verification must not depend on an interactive MCP session or manual browser interaction to succeed.
- Prefer focused module builds while iterating, but run `./mvnw verify` before reporting a significant change as complete.

## 4. Packaged runtime verification

Testing only a development server is insufficient for changes that affect startup, configuration, security, persistence, or packaging:

1. Start the packaged Uber JAR (see [local-runtime.md](local-runtime.md)).
2. Verify the login flow and the affected pages in a browser (see [browser-testing.md](browser-testing.md)).
3. Check the server log for `SEVERE`/`ERROR` entries and the browser console for errors.
4. Stop test processes cleanly and keep test data isolated from any data that must be preserved.

## 5. Dependency research and versioning

- Use the built-in `websearch` tool for current versions, release notes, compatibility notes, and documentation. Prefer authoritative sources: vendor documentation, official release notes, Maven Central, and official artifact repositories.
- Availability of `websearch` is determined by invoking it, not by inspecting MCP server lists.
- Pin exact versions in the root `pom.xml` properties. Do not introduce new floating, range, or snapshot dependencies without a documented reason; the existing PrimeFaces `17.0.0-SNAPSHOT` is a deliberate, existing exception, not a pattern to extend. Do not copy dependency lists from unrelated projects without verification.
- Keep server-provided APIs (`jakarta.*` platform and container APIs) in Maven `provided` scope so they are not packaged twice.
- Validate a stack change by building and running the packaged application before reporting it as working.
- When dependency versions change, regenerate the license inventory as described in [../license-compliance.md](../license-compliance.md) instead of editing generated notices by hand.
- If a required research tool is unavailable, report the exact limitation, document the alternative source used, and continue only with independently verifiable information. Do not silently substitute a different version.
