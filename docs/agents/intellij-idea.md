# IntelliJ IDEA Development Guidance

These instructions supplement the repository-level [AGENTS.md](../../AGENTS.md) and apply when working in the project's IntelliJ IDEA development environment.

## 1. Run configurations

The project provides several IntelliJ IDEA run configurations for application startup, frontend resource synchronization, and stylesheet compilation.

### Application server — `dev_idea.xml`

**Purpose:** Launches the application using Payara Micro with the `dev-idea` Maven profile, deploying the IntelliJ-built exploded artifact under `out/artifacts/eldoc`.

- Use this configuration to start the local development application.
- Reuse an already-running server instance rather than starting another.
- Monitor the IntelliJ IDEA Run console for startup progress, deployment status, and runtime errors.
- Wait until the application has successfully deployed before attempting browser verification.

**Application access:**

- Development URL: `http://127.0.0.1:8080`
- Login: `admin`
- Password: configured by the shared Payara Micro start configuration used by the `dev-idea` and `dev-mvn` Maven profiles (`-Dai.eldoc.maintenance.password` in `modules/eldoc-idp-web/pom.xml`), which is the standard development password documented in [`local-runtime.md`](local-runtime.md). Pass the same value when launching the JAR outside the IDE.

If the configured password is removed or no password is configured, a random password is generated per JVM start and printed in the application console as `[MAINTENANCE MODE] No password was configured. Generated default maintenance password: ...`; the login page then shows the placeholder `Hint: see console output`. Retrieve it from the active Payara Micro Run console in that case.

Do not change the configured development password as part of unrelated work, and do not commit additional credential copies elsewhere.

**Startup time:**

- The first launch on a machine downloads the embedded MongoDB binary and about 1.4 GB of Tesseract language data, and can take several minutes.
- Until dependency initialization completes, every URL returns HTTP 404 even though the log already reports `ROOT was successfully deployed`. Wait for `Dependency initialization completed` and `Loading application [ROOT] at [/]` before concluding that deployment failed.
- Later launches reuse the cached downloads and start quickly.

### Web resource synchronization — `sync_eldoc_web_resources_to_out_artifact_win.xml`

**Purpose:** Synchronizes web resources from the source tree into the exploded application artifact used by the running server.

**Platform:** Windows.

- Synchronizes frontend resources such as XHTML pages, CSS, JavaScript, and other web assets.
- Copies resources into the corresponding locations under the `out/` build directory.
- Runs in continuous monitoring (watch) mode.
- Launch this configuration once when required, and leave it running throughout the development session.
- Do not restart or launch another instance if the watcher is already active.
- Changes to supported web resources should propagate automatically without a full application rebuild.

If frontend changes do not appear in the browser:

1. Confirm that the synchronization configuration is running.
2. Verify that modified resources have been copied into the exploded artifact.
3. Check browser caching and reload the affected page.
4. Investigate synchronization errors before attempting manual deployment or rebuilding the application.

### SCSS compilation — `compile_styles_watch_mode_win.xml`

**Purpose:** Compiles the application's SCSS stylesheets into the generated CSS resources.

**Platform:** Windows. On other operating systems use the shell scripts described below.

- Watches the source SCSS files and compiles the modified stylesheet into its CSS output.
- Compiles all three entry points in one run.
- Runs continuously in watch mode.
- Launch once when stylesheet development requires it.
- Keep the watcher running during frontend styling work.
- Do not launch duplicate instances if compilation is already active.
- Monitor its console for compilation errors.

Compiled stylesheets:

| SCSS source | Generated CSS |
| --- | --- |
| `scss/eldoc/eldoc.scss` | `src/main/webapp/resources/eldoc/5_0_0/css/eldoc.css` |
| `scss/eldoc/eldoc-print.scss` | `src/main/webapp/resources/eldoc/5_0_0/css/eldoc-print.css` |
| `scss/eldoc/layout.scss` | `src/main/webapp/resources/eldoc/5_0_0/css/layout.css` |

Outside IntelliJ IDEA, or on Linux/macOS, run the scripts in `modules/eldoc-idp-web/scss` directly: `./compile-styles.sh` for a one-off build and `./compile-styles-watch-mode.sh` for watch mode. Both require Dart Sass (`sass`) on `PATH` and compile the same three stylesheets. See [build-and-test.md](build-and-test.md) for the full generation rules, including adding a new stylesheet.

For SCSS changes, the expected workflow is:

1. Edit the relevant SCSS source file.
2. Allow the SCSS watcher to compile the resulting CSS.
3. Allow the web resource synchronization watcher to copy the resulting resources into the deployed artifact, if applicable.
4. Reload the browser and verify the rendered styles.

Avoid manually editing generated CSS when its source is maintained in SCSS. The generated files are committed build output; regenerate them with the scripts above rather than editing them, and regenerate them before packaging.

## 2. Recommended development workflow

When working on the local application:

1. Check whether the Payara Micro application server is already running. Start it using `dev_idea.xml` only if necessary.
2. On Windows, ensure the web resource synchronization watcher is running when modifying web resources.
3. When modifying SCSS, ensure the stylesheet compilation watcher is running.
4. Open the development application at `http://127.0.0.1:8080`.
5. Sign in with the development credentials (`admin` and the development password), or read a generated password from the application console when none is configured.
6. Make the requested source changes.
7. Verify that generated and synchronized resources have reached the deployed application.
8. Test the affected functionality in the browser and inspect relevant console or server errors.

Do not restart the application or watchers unnecessarily.

## 3. Build and deployment

- Follow existing IntelliJ IDEA run configurations and the project's established build process.
- The development environment uses an exploded application artifact under `out/`.
- The CLI counterpart is the `dev-mvn` Maven profile, which deploys `target/elDoc-idp-oss`; see [local-runtime.md](local-runtime.md).
- Verify the actual artifact path from the current project configuration before performing manual operations.
- Prefer automatic synchronization for supported frontend changes.
- Use the project's Maven wrapper when Maven execution is required.
- On Windows, use `.\mvnw.cmd`.
- Avoid full builds for documentation-only changes or frontend changes already handled by active watchers.
- For Java code changes or other changes requiring recompilation or redeployment, follow the established application build and deployment procedure.

## 4. Runtime management and troubleshooting

- Preserve existing running server instances and resource watchers.
- Do not launch duplicate application servers, synchronization watchers, or SCSS compilers.
- Do not stop or restart existing processes unless necessary and authorized.
- Distinguish application defects from compilation failures, stale resources, deployment synchronization problems, and infrastructure errors.
- Inspect relevant IntelliJ IDEA Run consoles and application server logs when diagnosing problems.
- If a deployed resource appears stale, compare the corresponding source and deployed files before making further changes.
- Prefer targeted troubleshooting and synchronization over unnecessary full rebuilds.

## 5. Browser verification

- Verify UI modifications against the application running in the active development environment.
- Confirm that the browser is connected to the correct application instance.
- Use Playwright MCP or equivalent browser automation tools when available.
- When comparing development and reference applications, maintain isolated authentication sessions if necessary.
- Verify rendered appearance, interaction behavior, JavaScript errors, and relevant network requests.
- If changes do not appear, investigate SCSS compilation, web resource synchronization, and caching before concluding that the implementation is incorrect.

## 6. Environment portability

The configurations ending in `_win.xml` are Windows-specific.

When working on another operating system:

- Use an equivalent supported configuration or workflow if available.
- Do not assume Windows-specific commands or paths are portable.
- Follow the current environment's established build, deployment, and resource synchronization procedures.
- For stylesheet compilation, use `scss/compile-styles.sh` and `scss/compile-styles-watch-mode.sh` instead of the `_win` configuration; see [build-and-test.md](build-and-test.md).