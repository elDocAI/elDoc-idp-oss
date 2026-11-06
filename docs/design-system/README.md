# elDoc — PrimeFaces 17 Aura integration

Selected direction: **Indigo Focus / Soft Neutral**, 5 November 2026.

**Match the custom topbar and left menu almost exactly to the supplied HTML. Use native Aura styling with minimal overrides for the working-area components.** These are separate acceptance targets: native differences are acceptable for standard components, not a reason to leave the custom shell visually different.

## Apply it

Keep the installed Aura theme and integrate through the existing sources:

- `modules/eldoc-idp-web/scss/eldoc/layout.scss` — global theme inputs and application shell/layout.
- `modules/eldoc-idp-web/scss/eldoc/eldoc.scss` — application-specific components and functional visualization.

Inspect actual imports, selector ownership, templates and compiled stylesheet order before editing. Use [tokens.css](tokens.css) as a declaration reference; merge required declarations into those existing sources and emit them once through the established build. Do not add another theme stylesheet or edit generated CSS manually.

The primary input is native indigo-600 (`#4F46E5`), light mode remains fixed and the existing UI font stack is retained. The three main application surfaces are header `#292B52`, sidebar `#F0F1F8` and workspace `#F5F6F8`. A few additional **shell-scoped** text, selection, divider and avatar roles are provided to reproduce the custom layout accurately. They do not change the global Aura component palette.

Preserve **compact density on the actual `<html>` root**, including intentional nested overrides. Use native density instead of manually shrinking global controls. Audit existing tokens and overrides before adding new ones; see [SCSS-REVIEW.md](SCSS-REVIEW.md).

## Icons

**Font Awesome Free is the primary icon library** for application navigation, actions and content. PrimeIcons are also allowed on standalone pages such as Login and Error, and for existing PrimeFaces component indicators. See [icon implementation guidance](implementation-guidelines.md#2b-icon-libraries) for supported styles and usage.

## Files

| File | Use |
|---|---|
| [tokens.css](tokens.css) | Native inputs and scoped shell-color declarations to merge into existing SCSS |
| [DESIGN.md](DESIGN.md) | Exact custom-shell targets and native-component boundaries |
| [implementation-guidelines.md](implementation-guidelines.md) | Integration, cleanup, scope/density and verification |
| [SCSS-REVIEW.md](SCSS-REVIEW.md) | Findings from the supplied SCSS, with cleanup candidates and usage caveats |
| [HTML reference](references/indigo-focus-soft-neutral.html) / [PNG reference](references/indigo-focus-soft-neutral.png) | Current selected design; HTML governs measurements when artifacts differ |

## Scope and verification

The supplied HTML has a **260px desktop sidebar, 60px topbar and “elDoc IDP” wordmark**. The implementing agent must inspect and measure this current reference, not an earlier variant. Its outer preview margin, rounded window frame, maximum width and artificial minimum heights are presentation scaffolding, not production layout requirements.

The refreshed SCSS copies were reviewed and the reference rendered at 1600×900 and 1366×768. This review identified residual primary, sizing, padding and shadow overrides; it does not establish whole-repository dead-code status. The supplied SCSS and reference files remain unchanged. No application repository, shared template or running JSF app was supplied here, so application integration and server workflows remain for the implementation agent to verify.

The HTML's private prototype variables support the standalone reference. Use its custom-shell appearance, while retaining Aura's own standard component states, typography sizing, radii and spacing wherever practical. Preserve workflows, permissions and OCR geometry throughout.
