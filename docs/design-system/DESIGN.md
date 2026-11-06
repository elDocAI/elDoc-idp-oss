# Indigo Focus / Soft Neutral — production intent

## Acceptance priorities

1. **Custom topbar and left menu:** reproduce the supplied HTML at near pixel-level fidelity on desktop. Exact colors, typography, spacing, alignment and selected states are required. Custom application CSS is appropriate for these owned layout areas.
2. **Working area:** retain the existing functional grid, use Soft Neutral `#F5F6F8` behind white surfaces, and use native PrimeFaces 17 Aura controls. Minor native component shade/size/radius differences are acceptable when they avoid unnecessary overrides.

The current [HTML reference](references/indigo-focus-soft-neutral.html) is the measurement authority; the [PNG](references/indigo-focus-soft-neutral.png) is a visual companion. Preserve live menu entries, user/module information and available actions rather than hardcoding prototype content.

## Custom shell: color targets

| Area | Required appearance |
|---|---|
| Topbar | Background/bottom edge `#292B52`; white text and icons |
| Profile circle | `#414369` background; white content; 30px circle |
| Sidebar | `#F0F1F8` background; `#DBDEEC` right edge/dividers |
| Menu labels/icons | `#454965`; section/footer text `#626782` |
| Current page | `#E0E3F7` fill, `#3730A3` text, weight 600, `#4F46E5` vertical marker |
| Count badge | `#DEE1F1` fill and `#454965` text; retain real counts/status semantics |
| Workspace | `#F5F6F8` behind native white content surfaces |

The root inputs and scoped roles in [tokens.css](tokens.css) cover these colors. Reuse native primitives when they are exact matches (white, indigo-600/800). Aura's generated highlight/active-menu colors are not necessarily the custom sidebar's specified colors. Apply exact sidebar roles locally; keep global Aura highlight/navigation tokens unchanged unless a separate real component need is demonstrated.

## Custom shell: desktop geometry and typography

These values were read from the current HTML and checked in its rendered output at 1600×900 and 1366×768, browser zoom 100%, device scale factor 1.

| Element | Target |
|---|---|
| Topbar height | 60px |
| Expanded sidebar width | 260px; synchronize main/footer offsets and header alignment |
| Topbar horizontal inset / major gap | 20px / 22px |
| Wordmark | “elDoc IDP”; 25px, weight 700, -1px letter spacing, 210px desktop brand slot |
| Module/title area | 12px, weight 500; 8px icon/text gap |
| Topbar tool spacing | 16px; icons 16px; profile circle 30px |
| Sidebar insets / item gap | 23px top/bottom, 10px horizontal / 5px |
| Menu row | 42px minimum height; 11px vertical and 12px horizontal padding; 6px radius |
| Menu typography/icons | 12px labels; 16px icons; 10px icon/text gap |
| Section label | 10px, weight 600, uppercase, 1.2px letter spacing; 12px horizontal inset and 10px bottom spacing |
| Selected marker | 3px wide; inset 12px from row top and bottom; does not shift label alignment |
| Badge | 10px, weight 600; 1px/6px padding; 5px radius; right aligned |
| Group divider | 1px; 13px vertical and 12px horizontal margins |
| Sidebar footer | 10px text; 18px top and 12px horizontal inset; anchored near bottom |

Use the supplied font stack and existing self-hosted Inter when available. Match shell text directly instead of changing the global body/rem size. Do not append decorative brand treatments absent from the reference merely because the old SCSS contains them. Retain genuine dynamic module/user labels and all live header actions; align/style their slots consistently with the reference.

The HTML is an inset preview window. Do not copy its 16px outer body padding, fixed preview heights or outer rounded frame into a full-viewport app. Compare shell crops in a normalized coordinate space. Preserve the production sticky topbar, sidebar scrolling, collapse/expand and mobile/off-canvas behavior. The current SCSS uses 900px/560px breakpoints, while the preview uses 850px/620px; retain functional production behavior and document responsive differences instead of breaking navigation to imitate the preview's stacked demo menu.

## Let Aura own working-area components

Keep the native primary palette, white content/form surfaces, text hierarchy, borders, disabled/hover/pressed/loading states, focus rings, radii and typography sizing. Use native buttons, uploads, selects, switches, tags, tables, menus and dialogs. Preserve compact density at the document root; preserve intentional nested normal/comfortable areas.

Use Font Awesome Free as the primary application icon library. PrimeIcons are also allowed on standalone pages such as Login and Error and for existing PrimeFaces component indicators; follow the [icon implementation guidance](implementation-guidelines.md#2b-icon-libraries). Use supplied reference SVGs with their license notices only if needed and consistent with repository conventions. Do not add a UI framework, icon package or new global palette simply to reproduce a small decorative detail.

Outside the custom shell, the prototype's exact violet fills, disabled gray, panel radius, 13px body text and 3px focus ring do not require global overrides. Start with Aura, inspect a real mismatch, and add only a justified semantic/component override. Custom recognition widgets may retain necessary local styling and functional geometry. Success/warning/error and OCR-confidence meanings remain separate from branding.

## Preserve workflow and accessibility

Keep existing upload triggers, validation, file limits, retries, permissions and AJAX behavior. Preserve queue/table interactions and recognition field associations, annotation coordinates, zoom/pan/pages and split-pane resizing. Do not implement the prototype's fake processing message, sample counts or blank additional-fields state as business logic.

Native keyboard behavior, visible focus, labels, readable contrast and zoom/touch usability remain requirements. Normal text targets 4.5:1 contrast and essential indicators/boundaries 3:1. In the dark topbar, use a locally readable hover/focus treatment; preserve light native popovers and dialogs. Near-exact shell styling must not hide controls or remove navigation to obtain a misleading screenshot match.
