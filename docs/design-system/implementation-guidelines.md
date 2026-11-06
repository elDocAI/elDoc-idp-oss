# Apply the design with native Aura tokens

## 1. Verify the installed theme before editing

Inspect the dependency/build, `primefaces.THEME`, loaded `primefaces-aura:theme.css`, `primefaces:components.css`, shared template, existing app overrides and build/deploy pipeline. The installed stylesheet and running DOM decide which tokens and
component classes exist.

The current official PrimeFaces v17 documentation exposes **`--p-primary-base`**. It derives the primary scale, normal/hover/active colors, contrast foreground, selection and focus colors. Use this single input; setting only `--p-primary-color`
leaves other state tokens potentially inconsistent. Do not remap all `--p-primary-50`…`950`, modify `--p-indigo-600`, or paste a PrimeNG/PrimeVue `definePreset` example into this Jakarta Faces app.

If the installed snapshot lacks this input or uses different tokens, inspect its actual declarations and adapt the smallest equivalent mapping. Do not leave a silent no-op, guess names, upgrade PrimeFaces or replace Aura just to fit this file.

## 2. Integrate in the existing SCSS and shared template

Keep `primefaces.THEME=aura` and inspect the real repository sources:

- `modules/eldoc-idp-web/scss/eldoc/layout.scss` — consolidate native inputs, shell variables, topbar/sidebar/main styling and responsive behavior here when consistent with existing ownership.
- `modules/eldoc-idp-web/scss/eldoc/eldoc.scss` — preserve application components, semantic/status styling and functional preview/OCR rules; remove redundant component restyling only after verification.
- `modules/eldoc-idp-web/src/main/webapp/WEB-INF/templates/template.xhtml` — verify the shared stylesheet includes and the actual topbar/menu fragments and behavior hooks.

The supplied design-system documentation specifies these existing template includes:

```xhtml
<h:outputStylesheet library="eldoc" name="css/layout.css" />
<h:outputStylesheet library="eldoc" name="css/eldoc.css" />
```

Verify these includes in the repository. Treat `tokens.css` as a declaration reference and merge it into the existing SCSS pipeline; do not load an additional duplicate token stylesheet. Consolidate conflicting legacy declarations, regenerate the existing CSS through the project build and verify the served assets. Keep theme overrides unlayered; avoid `!important`. The existing SCSS build remains the app's asset pipeline, not a fork/build of the Aura theme.

**The custom topbar/sidebar must match the current reference almost exactly.** Adjust their existing owned selectors and, where necessary, shared Facelets markup while preserving routing, permissions, event IDs/classes and navigation behavior. Local shell-specific palette/geometry is intentional; the rest of the app retains native Aura component styling. See `DESIGN.md` for exact shell colors and measurements.

`tokens.css` retains minimal native inputs and the three main surface colors, plus a small shell-scoped palette for exact menu text/selection/dividers/badges and the profile circle. These declarations must be consumed by actual shell rules; declaring variables alone will not change the UI. Do not put shell colors into the global Aura surface/highlight scale. Do not allow the earlier “three colors only” simplification to prevent an accurate custom shell.

Retain the user's UI font stack: `Inter, Roboto, Arial, ui-sans-serif, system-ui, sans-serif`. Use existing self-hosted assets. Change local shell typography to match the reference, not the global rem size. Body-appended native popovers still use light Aura surfaces/text. If a native menu widget is rendered inside the custom sidebar, use its supported scoped tokens first and the smallest owned-container adapter needed for visual fidelity; do not alter every menu in the application.

## 2a. Audit the supplied overrides before adding new ones

Start with [SCSS-REVIEW.md](SCSS-REVIEW.md). It identifies the duplicate `#793162` primary input, manual density settings, shadow suppression, potentially unused control radius and actual cross-file consumers. Confirm each finding in the current repository, including imported CSS, templates and JavaScript assignments. Replace/consolidate both conflicting primary declarations; remove the broad input reset on all scopes unless it is truly required. Preserve deliberate local primary overrides.

Restore native compact component sizing and elevation where the extra overrides have no demonstrated purpose. Keep necessary shell geometry and recognition/preview semantics. Remove a variable only after its consumers are removed, migrated or proven absent. Avoid blanket deletion of `.p-*` adapters, `.wfForm` rules, semantic log states, or fixed positioning: some express real application behavior rather than obsolete cosmetics.

## 2b. Icon libraries

Use **Font Awesome Free** as the primary library for application icons, including the topbar, sidebar, buttons and content. The bundled version is currently **7.3.1**, under `modules/eldoc-idp-web/src/main/webapp/resources/fa/7_3_1/`; the shared template loads it with `<h:outputStylesheet library="fa" name="all.min.css"/>`.

- Use current icon names with an explicit style, for example `fa-solid fa-arrow-up-right-from-square` or `fa-regular fa-file-pdf`. Apply the same convention to icon classes returned by Java or generated by JavaScript.
- Choose an icon and style available in the bundled Free distribution. Availability of an icon name in `all.min.css` does not mean every style is included. Pro-only styles and glyphs must not be used; for example, the external-link icon uses `fa-solid`, since its regular style requires Pro.
- Keep shared actions visually consistent across pages. Preserve accessible labels or tooltips on controls; mark purely decorative HTML icons with `aria-hidden="true"`.
- PrimeIcons (`pi pi-*`) are allowed on standalone pages such as Login and Error. Existing PrimeFaces component indicators, including the shared AJAX spinner, may also retain PrimeIcons. Preserve framework-managed component icons and behavior.
- Use the existing self-hosted resources and shared template includes. Do not restore the Font Awesome v4 shim or add another icon package for an individual icon.

When changing icons, verify the rendered glyph and selected font style against the bundled Free assets, including icons produced dynamically and after AJAX updates where relevant.

## 3. Scope and density correctly

PrimeFaces re-declares derived tokens on `[data-p-scope]` so they recalculate when a local primary input changes. For a deliberate local primary override, add `data-p-scope=""` to the actual container. Plain XHTML elements can carry this attribute; JSF components may require the repository’s supported pass-through attribute mechanism. Do not invent an attribute that the installed component does not accept.

Root primary inputs normally inherit. If a global **derived-token** override must also affect scoped containers, target `:root, [data-p-scope]`. Density-dependent derived tokens may additionally require `[data-p-density]`. Apply this only to the specific override; do not duplicate the theme’s token definitions or reset intentional local primary colors. Check overlay append targets, since content appended under `body` may sit outside a local scope.

Keep **compact density** as the existing application baseline:

```html
<html xmlns="http://www.w3.org/1999/xhtml" data-p-density="compact">
```

Apply this attribute in the shared Facelets template that renders the actual `<html>` root. Pages using that template inherit the setting and need no separate declaration. Add it to other page entry points only when they render their own document root and do not already specify a density.

Preserve deliberate nested `data-p-density="normal"` or `data-p-density="comfortable"` overrides. Dialogs and other content appended under `body` inherit the root density unless explicitly overridden.

Do not force a global 34px control height or alter the root font-size to emulate compact density.

## 4. Implement and verify in the app

Capture before/after screenshots using the same route, viewport, data, zoom, scroll and expanded menu state. Render the reference HTML locally at 1600×900 and 1366×768 with the same browser/device scale. Compare dedicated **topbar and expanded-sidebar crops**, aligned to their own edges; exclude the reference's preview margin/frame. Inspect colors, geometry, typography, icons, brand/module alignment, row spacing, count badges, selected fill/marker, dividers and profile circle. Iterate until no material visible shell mismatch remains; do not invent a percentage score or infer fidelity from the primary color alone. Real labels, counts, permissions and extra actions may differ; do not delete them or replace them with sample content.

Check normal/hover/focus/current-page states; all menu branches; sidebar collapse, scrolling, state persistence and mobile open/close; module/profile/notification actions; light popovers outside or inside the topbar. Keep visible keyboard focus against the dark header. At smaller supported widths, preserve functional production off-canvas behavior and document intentional differences from the preview.

For working-area components, inspect computed native button/field styles and open overlays, not only root variable strings. Check primary normal/hover/active/focus/disabled states, valid/invalid/read-only forms, selects, switches, menus, dialogs appended to body, uploads, AJAX replacements and theme persistence. Run a queue and OCR review flow, preserving field associations, overlay coordinates, confidence semantics, zoom/pan/page navigation and splitter behavior. Use authorized test fixtures.

Run repository-required build/checks, inspect browser/console and relevant request errors, verify served rebuilt assets and check keyboard/contrast/zoom. Visually review the real screenshots; saving screenshots alone is not verification. Finish by removing redundant declarations and dead variables, and record each remaining non-shell Aura override with its purpose. Report actual tested routes/states/viewports and any gaps. Do not claim application/server tests from the standalone reference.

## Sources and version boundary

Verified against official PrimeFaces source on 5 November 2026, commit `daf9a9d89166a917c2c2890280ee4f6410a7bb41`. This is a **source snapshot**, not proof of the installed application's build.

- [Live theming showcase](https://showcase.primefaces.org/theming)
- [Live token catalog](https://showcase.primefaces.org/theming/tokens)
- [PrimeFaces v17 theming documentation](https://github.com/primefaces/primefaces/blob/master/docs/17_0_0/core/theming.md)
- [Aura token definitions](https://github.com/primefaces/primefaces/blob/master/primefaces/src/main/frontend/themes/presets/aura.css)
- [Token scope build logic](https://github.com/primefaces/primefaces/blob/master/primefaces/src/main/frontend/build/scripts/bundle.mjs)
