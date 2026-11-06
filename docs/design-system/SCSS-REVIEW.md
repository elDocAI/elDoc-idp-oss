# Review of the supplied SCSS

Reviewed 5 November 2026 against the refreshed design package and both readable SCSS copies. Line numbers identify the **supplied snapshots** and will move during implementation. Search the current application repository before modifying or deleting rules. This is a source review, not a running-app audit or proof that a selector is unused everywhere.

## Findings to carry into implementation

| Supplied location | Finding | Required agent action |
|---|---|---|
| `layout.scss:29` and `:51` | `--p-primary-base: #793162` appears in both `:root` and `:root, [data-p-scope]`. This differs from indigo-600 and can keep overriding a newly added declaration or reset a scoped primary. | Consolidate to the supported indigo root input and remove the redundant broad reset; verify intentional scopes still work. Do not just append a third primary declaration. |
| `layout.scss:31–33` | `--p-typography-font-size: 0.8rem` and manual form padding impose global density adjustments. | With compact density on the document root, restore native values unless an actual requirement justifies these overrides. Match shell text locally. |
| `layout.scss:34–39` | Select/popover/navigation/modal/card/raised-button shadow tokens are all forced to `none`. | Restore native Aura elevation unless a real, documented exception remains; verify popover boundaries and stacking. |
| `layout.scss:41–42` | Sidebar width 260px and topbar height 60px match the current HTML and have real cross-file consumers. | Keep coordinated shell dimensions, offsets and collapse behavior. Do not classify these as obsolete theme tokens. |
| `layout.scss:43–44` | Menu labels use 14px and icons 18px; reference uses 12px and 16px on desktop. | Adjust only the custom menu roles and their consumers to match the reference. |
| `layout.scss:45`; `eldoc.scss:1032` | `--eldoc-control-height: 32px` is used by `.reco-field-input`. | Retain until that custom field's sizing is safely converted or locally justified. It is not unused and is not evidence that every native control needs a fixed height. |
| `layout.scss:46,105` | `--eldoc-control-font-size: 14px` is consumed by `.topbar`. | Make the shell typography intentional; rename/localize if appropriate after a repository-wide usage check. Do not resize all components to fix header text. |
| `layout.scss:47` | `--eldoc-control-radius: 4px` has no `var()` consumer in these two supplied files. | Candidate for removal only after checking templates, other styles and JavaScript property assignments. |
| `layout.scss:94–106,336–350,528–549` | Header has no explicit selected-design background; sidebar uses native white content; main/footer have no explicit Soft Neutral canvas. | Consume the three approved shell surfaces in the actual owned rules and cover the page/footer consistently. Verify in the running app. |
| Sidebar adapters around `layout.scss:358–520` | Native-looking `.p-menu`/`.p-panelmenu` classes are styled within the app's owned sidebar. | Inspect real markup. Reuse supported local component tokens/adapters where needed; near-exact shell styling is allowed here. Preserve nested menu behavior, permissions and live badges. |
| `layout.scss:803–815`; `eldoc.scss:1070–1128` | Workbench panel/grid sizing and paginator changes overlap the component/density area. | Check whether native compact density can replace cosmetic adjustments. Retain functional alignment and resizing; do not delete as a block. |
| `eldoc.scss:324–338,489` | Semantic log backgrounds and a form-builder scroll-height adjustment use `!important`. | Review the real cascade and behavior. Prefer a supported token/scoped rule; keep a justified exception until its replacement is proven. |
| Preview/OCR/overlay areas throughout `eldoc.scss` | These contain positioning, image sizing, z-index, semantic colors and custom field states. | Preserve functional visualization and OCR geometry; being custom CSS does not make a rule redundant. |

## Why the UI can still look mostly white

The supplied layout is largely structural, but still includes native theme overrides and component adapters. Header, sidebar and main working backgrounds are not yet connected to the selected shell roles. More component overrides would not solve the missing owned-shell styling.

## Suggested ownership

- Consolidated native inputs, shell-only palette/metrics and shell/response rules in `layout.scss`, following the actual project's organization.
- Application components, custom recognition widgets and semantic/functional visualization in `eldoc.scss`.
- Existing Facelets template/fragments for markup adjustments, compact root density and asset includes. Inspect their actual locations and preserve IDs, event bindings and UI state hooks.

Do not modify generated CSS or load an additional copy of the token definitions. Preserve the existing license headers. Supplied SCSS copies are review inputs and have not been patched here.
