# AI Workspace — Mantine design handoff

Prepared 23 September 2026. This package is a design reference for the existing
AI Workspace application, with real Mantine components, reusable CSS, and
screenshots rendered from the supplied React prototype.

## Start here

1. Open `gallery.html` to browse the screenshots without running anything.
2. Read `DESIGN-SPEC.md` for layouts, interactions, responsive behavior, and acceptance criteria.
3. Read `API-MAPPING.md` before connecting the design to the application.
4. Give the implementing AI `IMPLEMENTATION-PROMPT.md` and this entire directory.
5. Use `src/theme.js`, `src/base.css`, and `src/workspace.module.css` as the visual source of truth.

The design is now implemented in `apps/web`. This standalone reference still
uses fictional Atlas launch fixtures and makes no application or provider calls.
Its sample answers are manually authored. Forms demonstrate interactions;
they do not authenticate, create accounts, upload content, or generate media.
Use the actual application for functional testing, not this prototype.

## Preview

From this directory:

```powershell
npm ci
npm run dev
```

Open <http://127.0.0.1:5186>. Default view: Library.

| URL fragment | Screen |
| --- | --- |
| `#library` | Library with all six source types |
| `#source/interview` | Audio source / transcript design |
| `#source/brief` | Document source design |
| `#source/market` | Source failure and recovery |
| `#ask` | Answer and selectable supporting evidence |
| `#studio` | Image, video, speech, and media analysis |
| `#activity` | Job status, recovery, and lookup |
| `#login` | Sign-in and registration design |

Library's Add source button opens file, web-page, and YouTube tabs. Search,
type filtering, source menus, confirmation dialogs, evidence selection, Studio
tools/state switches, and mobile navigation are interactive. Small preview-state
controls and sample-data labels are documentation aids, not production UI.

## Package contents

```text
README.md                   Entry point and run instructions
DESIGN-SPEC.md               Visual rules and behavior requirements
API-MAPPING.md               Current API connections and explicit gaps
IMPLEMENTATION-PROMPT.md     Copy/paste prompt for another AI agent
gallery.html                Offline screenshot gallery
package.json / package-lock.json
vite.config.js
src/
  theme.js                  Mantine theme + CSS variable resolver
  base.css                  Tokens, focus, and reduced-motion rules
  workspace.module.css      Responsive product styles
  App.jsx                   Navigation, shell, workspace dialogs
  Library.jsx               Library, source details, import modal
  Ask.jsx                   Answer and supporting-evidence reference
  Studio.jsx                Generation and analysis designs
  Activity.jsx              Jobs and recovery design
  Login.jsx                 Authentication design
  shared.jsx                Brand, source type icons, status badges
  fixtures.js               Fictional sample content — replace in production
  main.jsx                  MantineProvider and CSS import order
scripts/capture.mjs         Screenshot generation and browser smoke checks
public/favicon.svg         Local brand icon
screenshots/                Desktop/mobile PNGs and verification.json
```

Dependencies are pinned: Mantine core/hooks **9.6.2**, React/React DOM **19.3.0**,
Tabler icons **3.48.0**, Vite **8.3.0**, Playwright **1.63.0**. Keep Mantine packages
on the same version. This standalone prototype uses the same React/Vite versions
as the current application. It does not require a Gradle module.

## Reproduce screenshots

Keep the preview server running, then use a second terminal:

```powershell
npm run build
npm run screenshots
```

The screenshot script uses installed Microsoft Edge in headless mode. To use
installed Google Chrome instead:

```powershell
$env:BROWSER_CHANNEL = 'chrome'
npm run screenshots
```

It writes 22 screen PNGs, an overview PNG, and `screenshots/verification.json`, checks interactions,
captures browser errors, and checks six main routes for page overflow at 390,
768, 1024, and 1440 px. Desktop screenshots use a 1440 × 1080 viewport; mobile
uses 390 × 844. Screen images are full-page captures, so their heights vary;
modal/drawer images capture the viewport to preserve the overlay composition.

These checks verify the design prototype, not the application's backend or E2E
behavior. Follow `docs/end-to-end-test-specification.md` when implementing.

The prototype bundles all screens together and Vite reports a bundle-size advisory
(approximately 554 kB before gzip). The production migration should split routes
as appropriate. `vite.config.js` filters only the irrelevant `use client` directive
warning for this browser-only reference; other build warnings remain visible.

## Official references

The component setup follows [Mantine's Vite guide](https://mantine.dev/guides/vite/),
the [MantineProvider contract](https://mantine.dev/theming/mantine-provider/), and
the [AppShell layout API](https://mantine.dev/core/app-shell/).
Plain CSS Modules are used; no PostCSS-only syntax or runtime CSS-in-JS library
is required for this package.

## Artwork and fonts

Typography uses the local system stack (Segoe UI on Windows), with no network
font dependency. Screenshots will have small text-metric differences across
operating systems. The Studio room illustration is original inline SVG in
`Studio.jsx`; it is an explicit placeholder for a returned image, not an actual
provider generation. Source/card illustrations are code-native CSS and SVG.
