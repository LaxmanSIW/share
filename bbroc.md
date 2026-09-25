# DESIGN.md — BlackRock · US Individual Investors

> Source page: https://www.blackrock.com/us/individual
> Snapshot date: 24 Sep 2026 (hero = "Q3 Fixed Income Outlook"; commentary dated 21 Sep)
> Purpose: a design specification an AI agent or developer can use to rebuild the look, structure, and feel of this site.
{
  "page": "https://www.blackrock.com/us/individual",
  "viewport": "1135x925",
  "backgroundColorShare": [
    {
      "value": "#FFFFFF",
      "share": "44.9%"
    },
    {
      "value": "#F4F1EB",
      "share": "33.8%"
    },
    {
      "value": "#000000",
      "share": "8.3%"
    },
    {
      "value": "#FFCE00",
      "share": "6.9%"
    },
    {
      "value": "#161616",
      "share": "5.4%"
    },
    {
      "value": "#EFEFEF",
      "share": "0.7%"
    },
    {
      "value": "#FF4713",
      "share": "0.0%"
    }
  ],
  "textColorShare": [
    {
      "value": "#000000",
      "share": "80.0%"
    },
    {
      "value": "#FFFFFF",
      "share": "19.7%"
    },
    {
      "value": "#616161",
      "share": "0.2%"
    },
    {
      "value": "#FF4713",
      "share": "0.1%"
    }
  ],
  "fontFamilies": [
    {
      "value": "FortBook, Arial, sans-serif  |  weight 400  |  normal",
      "share": "79.8%"
    },
    {
      "value": "FortExtraBold, Arial, sans-serif  |  weight 400  |  normal",
      "share": "11.1%"
    },
    {
      "value": "FortExtraBold, Arial, sans-serif  |  weight 400  |  italic",
      "share": "3.8%"
    },
    {
      "value": "FortBold, Arial, sans-serif  |  weight 400  |  normal",
      "share": "3.6%"
    },
    {
      "value": "FortExtraBold, Arial, sans-serif  |  weight 700  |  normal",
      "share": "1.3%"
    },
    {
      "value": "FortBook, Arial, sans-serif  |  weight 700  |  normal",
      "share": "0.3%"
    },
    {
      "value": "FortBold, Arial, sans-serif  |  weight 700  |  normal",
      "share": "0.1%"
    }
  ],
  "typeStyles": [
    {
      "value": "14px/20px ls normal none",
      "share": "37.5%"
    },
    {
      "value": "16px/24px ls normal none",
      "share": "32.9%"
    },
    {
      "value": "12px/16px ls normal none",
      "share": "10.6%"
    },
    {
      "value": "20px/28px ls normal none",
      "share": "3.9%"
    },
    {
      "value": "16px/22px ls normal none",
      "share": "3.5%"
    },
    {
      "value": "14px/21px ls normal none",
      "share": "3.3%"
    },
    {
      "value": "12px/20px ls normal uppercase",
      "share": "2.3%"
    },
    {
      "value": "20px/30px ls normal none",
      "share": "2.1%"
    },
    {
      "value": "12px/20px ls normal none",
      "share": "1.3%"
    },
    {
      "value": "32px/40px ls normal none",
      "share": "0.9%"
    },
    {
      "value": "40px/48px ls normal none",
      "share": "0.6%"
    },
    {
      "value": "48px/56px ls normal none",
      "share": "0.5%"
    },
    {
      "value": "12px/20px ls 2px uppercase",
      "share": "0.2%"
    },
    {
      "value": "14px/14px ls normal none",
      "share": "0.1%"
    },
    {
      "value": "32px/32px ls normal none",
      "share": "0.1%"
    },
    {
      "value": "24px/32px ls normal none",
      "share": "0.1%"
    },
    {
      "value": "14px/24px ls normal none",
      "share": "0.1%"
    }
  ],
  "borderRadii": [
    {
      "value": "32px  <span>",
      "share": "57.1%"
    },
    {
      "value": "50px  <div>",
      "share": "14.3%"
    },
    {
      "value": "50%  <div>",
      "share": "14.3%"
    },
    {
      "value": "2px  <button>",
      "share": "14.3%"
    }
  ],
  "boxShadows": [
    {
      "value": "rgba(112, 112, 112, 0.5) 0px 0px 12px 0px",
      "share": "100.0%"
    }
  ],
  "keyElements": [
    {
      "label": "body",
      "selector": "body",
      "fontFamily": "Arial, sans-serif",
      "fontSize": "14px",
      "fontWeight": "400",
      "lineHeight": "14px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#000000",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px",
      "border": "0px none rgb(0, 0, 0)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "h1",
      "selector": "h1",
      "fontFamily": "FortExtraBold, Arial, sans-serif",
      "fontSize": "40px",
      "fontWeight": "400",
      "lineHeight": "48px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#000000",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px",
      "border": "0px none rgb(0, 0, 0)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "h2",
      "selector": "main h2, h2",
      "fontFamily": "FortExtraBold, Arial, sans-serif",
      "fontSize": "48px",
      "fontWeight": "400",
      "lineHeight": "56px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#000000",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px",
      "border": "0px none rgb(0, 0, 0)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "h3",
      "selector": "h3",
      "fontFamily": "FortExtraBold, Arial, sans-serif",
      "fontSize": "20px",
      "fontWeight": "700",
      "lineHeight": "28px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#000000",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px",
      "border": "0px none rgb(0, 0, 0)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "paragraph",
      "selector": "main p, p",
      "fontFamily": "AkkuratProBold, Arial, sans-serif",
      "fontSize": "16px",
      "fontWeight": "400",
      "lineHeight": "30px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#414042",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px 0px 0px 16px",
      "border": "0px none rgb(65, 64, 66)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "link in content",
      "selector": "main a",
      "found": false
    },
    {
      "label": "button / CTA",
      "selector": "button, [class*=\"btn\"], [class*=\"button\"], [class*=\"cta\"]",
      "fontFamily": "FortBook, Arial, sans-serif",
      "fontSize": "14px",
      "fontWeight": "400",
      "lineHeight": "14px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#000000",
      "background": null,
      "borderRadius": "0px",
      "padding": "0px",
      "border": "0px none rgb(0, 0, 0)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "header nav link",
      "selector": "header a, nav a",
      "fontFamily": "Arial, sans-serif",
      "fontSize": "14px",
      "fontWeight": "400",
      "lineHeight": "14px",
      "letterSpacing": "normal",
      "textTransform": "none",
      "color": "#005EB8",
      "background": null,
      "borderRadius": "0px",
      "padding": "8px 0px",
      "border": "0px none rgb(0, 94, 184)",
      "boxShadow": "none",
      "textDecoration": "none"
    },
    {
      "label": "footer",
      "selector": "footer",
      "found": false
    },
    {
      "label": "footer link",
      "selector": "footer a",
      "found": false
    },
    {
      "label": "footer heading",
      "selector": "footer h3, footer h2",
      "found": false
    }
  ],
  "rootCssVariables": {
    "--sticky-footer-width": "0",
    "--sticky-footer-left": "0",
    "--cb-Height": "calc(100vh - 100px)",
    "--cb-ContHeight": "calc(100vh - 100px)",
    "--cb-tile-Height": "calc(100vh - 100px)"
  },
  "loadedFonts": [
    "AkkuratProRegular | weight normal | normal | unloaded",
    "AkkuratProItalic | weight normal | normal | unloaded",
    "AkkuratProBold | weight normal | normal | unloaded",
    "AkkuratProBoldItalic | weight normal | normal | unloaded",
    "AkkuratProLight | weight normal | normal | unloaded",
    "AkkuratProLightItalic | weight normal | normal | unloaded",
    "AvenirNextLight | weight normal | normal | unloaded",
    "AvenirNextThin | weight normal | normal | unloaded",
    "AvenirNextRegular | weight normal | normal | unloaded",
    "AvenirNextRegularCondensed | weight normal | normal | unloaded",
    "AvenirNextRegularCondensedItalic | weight normal | normal | unloaded",
    "AvenirNextMedium | weight normal | normal | unloaded",
    "AvenirNextMediumCondensed | weight normal | normal | unloaded",
    "AvenirNextBold | weight normal | normal | unloaded",
    "AvenirNextBoldCondensed | weight normal | normal | unloaded",
    "AvenirNextBoldCondensedItalic | weight normal | normal | unloaded",
    "AvenirNextDemi | weight normal | normal | unloaded",
    "AvenirNextDemiCondensed | weight normal | normal | unloaded",
    "AvenirNextDemiItalic | weight normal | normal | unloaded",
    "AvenirNextItalic | weight normal | normal | unloaded",
    "AvenirNextBook | weight 300 | normal | unloaded",
    "FortBook | weight 400 | normal | loaded",
    "FortBookItalic | weight 400 | normal | unloaded",
    "FortBold | weight 700 | normal | loaded",
    "FortBoldItalic | weight 700 | normal | unloaded",
    "FortExtraBold | weight 800 | normal | loaded",
    "FortExtraBoldItalic | weight 800 | normal | unloaded",
    "FortCondensedBook | weight 400 | normal | unloaded",
    "FortCondensedBookItalic | weight 400 | normal | unloaded",
    "FortCondensedBold | weight 400 | normal | unloaded",
    "BLK Fort Condensed Bold | weight 700 | normal | unloaded",
    "FortCondensedBoldItalic | weight 400 | normal | unloaded",
    "FortCondensedLight | weight 400 | normal | unloaded",
    "FortCondensedLightItalic | weight 400 | normal | unloaded",
    "Font Awesome 5 Brands | weight 400 | normal | loaded",
    "Font Awesome 5 Duotone | weight 900 | normal | unloaded",
    "Font Awesome 5 Pro | weight 300 | normal | unloaded",
    "Font Awesome 5 Pro | weight 400 | normal | unloaded",
    "Font Awesome 5 Pro | weight 900 | normal | loaded",
    "FontAwesome | weight normal | normal | unloaded",
    "flowplayer | weight normal | normal | unloaded"
  ]
}
---

## 0. Read this first — how much of this is verified

I could read the page's **content, structure, asset names, and metadata** directly. I could **not** read its compiled CSS (fonts, radii, exact px values). Rather than invent those and present them as fact, every value in this file carries one of two tags:

| Tag | Meaning |
|---|---|
| **[Sourced]** | Taken from the live page markup/content, or from a fetched brand source (Brandfetch). |
| **[Proposed]** | My recommended value where the live one was not readable. A working default, not a measurement. |

**To convert every [Proposed] value into a measured one (about 60 seconds):** run `blackrock-extract-tokens.js` (delivered alongside this file) in the DevTools console on the live page. It reports real font families, colour shares, radii, shadows, and CSS variables. Paste the JSON output back and every [Proposed] item below gets replaced with the real number.

Palette hex codes come from Brandfetch's aggregated profile for blackrock.com (marked "unclaimed" there), so they are a good brand reference but not a read of the site's stylesheet.

---

## 1. Brand character

| Trait | Expression |
|---|---|
| Tone | Authoritative, editorial, calm. Institutional finance, not fintech-playful. **[Sourced: copy]** |
| Layout personality | Content-led. Large photographic hero, then text-forward modules. Lots of link-driven navigation. **[Sourced: structure]** |
| Colour personality | Mostly neutral (white, black, warm off-white) with one hot accent (Vermilion) and a rare yellow highlight. **[Sourced: palette]** |
| Imagery | Real-world photography, aerial/landscape, no illustration or stock-office clichés. **[Sourced: hero alt text]** |
| Density | Generous whitespace, short paragraphs, one clear CTA per module. **[Proposed]** |
| Trust cues | Persistent legal/risk disclosures, FINRA BrokerCheck, prospectus language. **[Sourced]** |

---

## 2. Colour system

### 2.1 Brand palette — **[Sourced: Brandfetch]**

| Name | Hex | RGB | HSL | CMYK | Role |
|---|---|---|---|---|---|
| **Vermilion** | `#FF4713` | 255, 71, 19 | 13°, 100%, 54% | 0, 72, 93, 0 | **Primary accent** (brand hot colour) |
| **Supernova** | `#FFCE00` | 255, 206, 0 | 48°, 100%, 50% | 0, 19, 100, 0 | Secondary accent / highlight |
| **Spring Wood** | `#F4F1EB` | 244, 241, 235 | 40°, 29%, 94% | 0, 1, 4, 4 | Warm off-white surface |
| **Black** | `#000000` | 0, 0, 0 | 0°, 0%, 0% | 0, 0, 0, 100 | Text, wordmark, dark surfaces |
| **White** | `#FFFFFF` | 255, 255, 255 | 0°, 0%, 100% | 0, 0, 0, 0 | Page background, text on dark |

### 2.2 Which colour is primary, and how much of each

Five brand colours, but they do very different jobs. Neutrals carry the page; Vermilion is the signal.

| Rank | Colour | Approx. share of screen | Where it appears | Basis |
|---|---|---|---|---|
| 1 | **White** `#FFFFFF` | 50–60% | Page canvas, nav bar, content modules | **[Proposed]** from text-on-white editorial structure |
| 2 | **Black** `#000000` | 20–25% | Body text, headings, wordmark, footer field | **[Proposed]**; footer uses the white wordmark **[Sourced]** so its field is dark |
| 3 | **Spring Wood** `#F4F1EB` | 8–15% | Alternating bands, tiles, subtle card fills | **[Proposed]** |
| — | **Photography** | (hero only) | Full-bleed hero image | **[Sourced]** |
| 4 | **Vermilion** `#FF4713` | 3–5% | Primary emphasis: key CTAs, active states, key rules/markers | **[Proposed]** |
| 5 | **Supernova** `#FFCE00` | ≤ 1–2% | Rare highlight or badge only | **[Proposed]** |

**Rule of thumb: about 90% neutral / 10% colour.** If Vermilion is on more than roughly 1 in 20 elements, it stops signalling anything.

### 2.3 Extended neutrals and state colours — **[Proposed]**

These fill gaps the brand palette does not cover (secondary text, borders, hover states). Contrast values are calculated, not estimated.

| Token | Hex | Use | Contrast |
|---|---|---|---|
| `grey-900` | `#1A1A1A` | Soft-black text alternative | 17.40:1 on white |
| `grey-600` | `#595959` | Secondary text, captions, metadata | 7.00:1 on white · 6.21:1 on Spring Wood |
| `grey-200` | `#D9D6CF` | Hairline borders, dividers (warm to match Spring Wood) | decorative only (1.45:1) |
| `vermilion-strong` | `#C93400` | Vermilion when used as **text or thin icon on light bg** | 5.28:1 on white · 4.69:1 on Spring Wood |
| `vermilion-pressed` | `#B82E00` | Pressed/active state of Vermilion controls | 6.12:1 with white text |
| `scrim` | `rgba(0,0,0,0.60)` | Modal backdrop (audience selector, leaving-site dialog) | n/a |

### 2.4 Contrast rules (calculated, WCAG 2.x)

| Pairing | Ratio | Verdict |
|---|---|---|
| Black on White | 21.00:1 | AAA |
| Black on Spring Wood | 18.63:1 | AAA |
| Black on Vermilion | 6.16:1 | AA (safe for any text size) |
| Black on Supernova | 14.08:1 | AAA |
| **White on Vermilion** | **3.41:1** | **Fails AA for normal text.** Only large text (≥24px, or ≥18.66px bold) |
| **Vermilion on White (as text)** | **3.41:1** | **Fails AA for normal text.** Use `vermilion-strong` |
| Vermilion on Spring Wood | 3.02:1 | Non-text/large graphic use only |

**Design consequence:** put **black** text on Vermilion fills, and use `#C93400` (not `#FF4713`) for Vermilion-coloured text on light backgrounds.

### 2.5 Role → token map

| Role | Token |
|---|---|
| Page background | White |
| Alternate section background | Spring Wood |
| Primary text / headings | Black |
| Secondary text | `grey-600` |
| Link (default) | Black, underlined **[Proposed]** |
| Link hover | `vermilion-strong` **[Proposed]** |
| Primary accent fill | Vermilion (black label) |
| Highlight | Supernova (black label) |
| Border / divider | `grey-200` |
| Footer background | Black; text White |
| Focus ring | 2px Black outline + 2px White gap (on dark: White) |

---

## 3. Typography

### 3.1 Font families

| Item | Value | Status |
|---|---|---|
| Wordmark | Delivered as an **SVG image**, never live text. Nav: `blackrock-logo-nav.svg`; footer: `blackrock-logo-white.svg` | **[Sourced]** |
| Wordmark letterform | Third-party sources identify the logo lettering as Rotis Semi Serif Bold. Logo artwork only, not a UI font | Third-party, unverified |
| UI / body / heading font | **Not readable from my tools. Run the extractor to get the real `font-family`.** | **[Measure]** |
| Working fallback stack | `"Helvetica Neue", Helvetica, Arial, "Segoe UI", system-ui, sans-serif` | **[Proposed]** |
| Monospace (tables/tickers) | `ui-monospace, "SF Mono", Menlo, Consolas, monospace` | **[Proposed]** |

### 3.2 Semantic structure — **[Sourced from page markup]**

- Exactly **one `<h1>`** on the page: the hero headline.
- **`<h2>`** for each major section title (funds intro, about, "Learn more", "Explore more").
- **`<h3>`** for footer column headings ("Corporate", "Legal").
- **Eyebrow labels** sit above headings and are written in capitals in the source (e.g. `Q3 FIXED INCOME OUTLOOK`, `FUNDS AT BLACKROCK`, `ABOUT US`). Card categories are inconsistent in source casing (`RETIREMENT`, `Multi-Asset`), so the uppercase look is almost certainly applied by CSS `text-transform`; apply it globally.
- **Mega-menu column headings** are capitals in the source: `FUND TYPE`, `ASSET CLASS`, `FUNDS IN FOCUS`, `ACTIVE STRATEGIES`, `OTHER STRATEGIES`, `INSIGHTS`, `EDUCATION`, `DOCUMENTS & FORMS`, `TOOLS`, `OUR COMPANY`.
- **Legal block:** the first two risk disclaimers are set **bold + italic**; the remaining disclosures are regular weight **[Sourced]**.

### 3.3 Type scale — **[Proposed]** (sizes are working values; hierarchy is sourced)

| Style | Desktop | Mobile | Weight | Case / tracking | Use |
|---|---|---|---|---|---|
| Display / H1 | 56 / 60 | 34 / 40 | 600 | none · −0.01em | Hero headline |
| H2 | 40 / 48 | 28 / 36 | 600 | none · −0.005em | Section titles |
| H3 | 24 / 32 | 20 / 28 | 600 | none | Card titles, footer headings |
| H4 | 20 / 28 | 18 / 26 | 600 | none | Sub-blocks (asset-class subheads) |
| Eyebrow | 13 / 16 | 12 / 16 | 600 | **UPPERCASE · +0.08em** | Section/category label |
| Lede | 20 / 30 | 18 / 28 | 400 | none | Hero deck paragraph |
| Body | 16 / 24 | 16 / 24 | 400 | none | Paragraphs |
| Body small | 14 / 20 | 14 / 20 | 400 | none | Card descriptions, captions |
| Nav (primary) | 16 / 24 | drawer 18 / 24 | 500 | none | Funds, Investment strategies, Insights & education, About us |
| Utility nav | 13 / 16 | 13 / 16 | 400 | none | BlackRock · iShares · Aladdin · Our company · Sign In |
| Mega-menu heading | 12 / 16 | 12 / 16 | 600 | **UPPERCASE · +0.10em** | `FUND TYPE`, `ASSET CLASS`, … |
| CTA link | 16 / 24 | 16 / 24 | 600 | none | "Read the …", "See …", "Learn more …" |
| Legal | 12 / 18 | 12 / 18 | 400 (first 2 paras: 700 italic) | none | Risk disclosures, copyright |

Body measure: keep paragraphs to **60–75 characters** per line **[Proposed]**.

---

## 4. Layout, grid, spacing

| Item | Value | Status |
|---|---|---|
| Responsive | Yes. `viewport: width=device-width, initial-scale=1` | **[Sourced]** |
| Grid | 12 columns desktop, 8 tablet, 4 mobile | **[Proposed]** |
| Max content width | 1280px (hero image bleeds full width) | **[Proposed]** |
| Gutter | 24px (desktop), 16px (mobile) | **[Proposed]** |
| Page side padding | 64px ≥1200 · 40px ≥768 · 20px below | **[Proposed]** |
| Breakpoints | 600 · 900 · 1200 · 1440 | **[Proposed]** |
| Spacing scale (4px base) | 4, 8, 12, 16, 24, 32, 48, 64, 96, 128 | **[Proposed]** |
| Section vertical padding | 96px desktop / 64px mobile | **[Proposed]** |
| Card internal padding | 24–32px | **[Proposed]** |

---

## 5. Shape, borders, elevation

### 5.1 Corner radius — **[Measure]**

I could not read border-radius values. The site's overall look is editorial and structured, so the proposed scale below leans **sharp**, with softness reserved for small controls. **Replace with measured values from the extractor's "Border radii" table.**

| Token | Value | Applied to (proposed) |
|---|---|---|
| `radius-none` | `0` | Hero, cards, image tiles, footer, mega-menu panel |
| `radius-sm` | `2px` | Buttons, inputs, selects |
| `radius-md` | `4px` | Modals/dialogs, dropdown lists |
| `radius-lg` | `8px` | Reserved; only if measurement shows rounded cards |
| `radius-pill` | `999px` | Tags/chips only (if any exist) |

### 5.2 Borders — **[Proposed]**

- Hairline: `1px solid #D9D6CF`
- Emphasis rule (e.g. above a section title): `2px solid #000`, or `3px solid #FF4713` for a single accent rule
- Focus: `2px solid #000` with `2px` offset

### 5.3 Elevation — **[Proposed]**

The page is **flat**. Depth is used only for overlays.

| Level | Shadow | Use |
|---|---|---|
| 0 | none | Cards, tiles, sections |
| 1 | `0 8px 24px rgba(0,0,0,0.12)` | Mega-menu panels, country/region dropdown, Sign In menu |
| 2 | `0 16px 48px rgba(0,0,0,0.24)` + scrim | Audience selector dialog, "leaving BlackRock" dialog |

---

## 6. Imagery and iconography

| Item | Detail | Status |
|---|---|---|
| Hero image | Full-bleed landscape photo, file `3q2026-hero-image.jpg`. Subject: aerial patchwork of striped agricultural fields split by dark waterways, one boat with a wake. Alt text is descriptive and complete | **[Sourced]** |
| Photo style | Aerial / landscape, natural colour, human-scale detail, no overlaid people-in-suits | **[Sourced/inferred]** |
| Hero text placement | Eyebrow, H1, paragraph, then text CTA. Overlay or panel treatment needed for legibility over photo | **[Sourced: order]** / **[Proposed: treatment]** |
| Social icons | LinkedIn, Facebook, X, YouTube; X glyph is an SVG (`x-twitter-black.svg`) | **[Sourced]** |
| Icon style | Simple monoline/solid glyphs, 24px, colour = current text colour | **[Proposed]** |
| Arrow glyph on CTAs | Small right-arrow after link text | **[Proposed]** |
| Logo asset paths | `/blk-one01-c-assets/.../images/media-bin/web/global/wordmark/` : `blackrock-logo-nav.svg`, `blackrock-logo-white.svg` | **[Sourced]** |
| OG image | `/blk-one01-c-assets/include/common/images/blackrock_logo.png` | **[Sourced]** |
| Front-end framework namespace | Assets live under `blk-one01-c-assets` (internal design system, "BLK One") | **[Sourced]** |

Logo clear space: keep at least the height of the "B" free on all sides **[Proposed]**. Never recolour the wordmark; use black on light, white on dark.

---

## 7. Page anatomy (top to bottom) — **[Sourced]**

1. **Skip link** ("Skip to content") → jumps to `#bodyWrapper`
2. **Utility bar:** BlackRock · iShares · Aladdin · Our company | audience label "Individual Investors" | region "United States" | **Sign In** (with dropdown: Manage communications, Mutual Fund & 529 accounts)
3. **Hidden dialogs:** audience selector, location selector
4. **Main nav:** wordmark + Funds · Investment strategies · Insights & education · About us (each opens a mega menu)
5. **Hero:** full-bleed photo + eyebrow + H1 + paragraph + CTA
6. **Three teaser cards** in a row (Retirement · Multi-Asset · Weekly market commentary)
7. **Funds section:** eyebrow + H2 + paragraph + "View all funds" CTA, then **seven asset-class blocks**
8. **About section:** eyebrow + H2 + paragraph + CTA
9. **"Keep exploring":** three link tiles
10. **"Learn more":** iShares, Aladdin
11. **"Explore more":** sitemap mirror of the mega menu (mobile nav / drawer content)
12. **Footer:** white wordmark, mission statement, social row, "Corporate" and "Legal" link columns, copyright, "Manage cookies"
13. **Disclosures:** long-form risk/legal text, then compliance code line
14. **"You are now leaving BlackRock's website" dialog**

---

## 8. Component specifications

Anatomy is **[Sourced]**; visual styling is **[Proposed]** unless noted.

### 8.1 Utility bar
- Height 40px, background White (or Spring Wood), 13px text, links separated by 24px.
- Left: BlackRock, iShares, Aladdin, Our company. Right: audience label, region, Sign In.
- Sign In is a text link with a dropdown of two items. Region opens an alphabetical list of ~40 locations with a "Location not listed" fallback.

### 8.2 Audience selector dialog
- Title text: "Leave the BlackRock site for Individuals to explore other content".
- Three stacked choices, each **link + one-line descriptor**: Advisors ("I invest on behalf of my clients"), Institutions ("I consult or invest on behalf of a financial institution"), General Public ("I want to learn more about BlackRock").
- Radius `4px`, elevation 2, scrim behind. Choice label 16/24 weight 600, descriptor 14/20 `grey-600`.

### 8.3 Main navigation
- Height 72–80px desktop. Wordmark left (height ≈ 24–28px), four items, 16px/500.
- Active/hover: 2px Vermilion underline **[Proposed]**.
- **Mega menu:** full-width white panel, elevation 1, multi-column. Column heading is the uppercase 12px label; items below are 15–16px links with 8–12px vertical rhythm.
- Some panels carry a **featured row** at the top (All funds, 529 College Savings Plan; Planning for retirement, The Bid podcast).
- Mobile: collapses to a full-height drawer using the "Explore more" accordion structure (Funds, Investment strategies, Insights & education, About us).

### 8.4 Hero
- Full-bleed photo, min-height ≈ 560px desktop / 480px mobile.
- Text block max-width ≈ 640px: eyebrow → H1 → lede → CTA.
- Legibility: place text on a solid panel (White or Spring Wood, 32–48px padding) or add a gradient scrim `linear-gradient(90deg, rgba(0,0,0,.55), rgba(0,0,0,0) 70%)` with White text.
- CTA is a text link with arrow.

### 8.5 Teaser card (×3)
- Anatomy: category eyebrow → title (H3) → 2–3 line description → CTA text link. Entire card is one link target.
- Background White or Spring Wood, radius `0`, padding 24–32px, 1px `grey-200` border or none.
- Hover: title underlined, arrow shifts 4px right; no lift/shadow.
- Grid: 3 columns ≥900px, 1 column below.

### 8.6 Asset-class block (×7)
Digital assets · Cash alternatives · Commodities · Stocks · Bonds · Multi-asset · Real estate.
- Anatomy: label (small, bold) → one-line value proposition (H4) → 2–3 sentence explainer (body) → CTA "See … funds".
- Layout: two-column list or alternating band; separated by 1px `grey-200` rules.
- Each block links to the fund screener pre-filtered by asset class.

### 8.7 Buttons and links — **[Proposed]**

| Variant | Fill | Text | Border | Radius | Hover | Pressed |
|---|---|---|---|---|---|---|
| Primary | Black | White | none | 2px | `#1A1A1A` | `#000` + 1px inset |
| Accent | Vermilion `#FF4713` | **Black** | none | 2px | `#E63F0E` | `#B82E00` w/ white text |
| Secondary | transparent | Black | 1px Black | 2px | Black fill, White text | `#1A1A1A` fill |
| Text link (CTA) | none | Black, 600 | 1px underline (offset 3px) | — | text → `#C93400` | — |
| Disabled | `#E5E1D8` | `#8A8A8A` | none | 2px | none | none |

- Padding: 14px 24px (default), 10px 16px (small); min height 44px (touch target).
- Label: 16px/600, no uppercase.

### 8.8 "Keep exploring" tiles (×3)
Account Access · How to plan for retirement · 529 College savings plan.
- Large clickable tiles, Spring Wood fill, title 20/28 weight 600 with arrow, min-height ≈ 120px, radius `0`.

### 8.9 Footer
- Background Black, text White. White wordmark top-left. Mission statement 16/24 on ≤ 60ch.
- Social row of four icons, 24px, White, 16px gap; hover Vermilion.
- Two link columns with H3 headings: **Corporate** (Fraud protection tips, Careers, Newsroom, Investor relations, Contact us, Accessibility) and **Legal** (Terms & Conditions, Privacy Notice, Business Continuity, FINRA BrokerCheck, Rule 606 Disclosure, Cookie Notice, Manage cookies).
- Link 14/20 White, hover underline. Copyright line 12/18.

### 8.10 Disclosure block
- 12/18, `grey-600` on White (or `#BDBDBD` on Black if placed in footer).
- First two paragraphs bold italic **[Sourced]**, then regular paragraphs for commodities, sector concentration, REITs, international, dividends, index-provider notices, fixed-income risk, distributor statement, and legal notice.
- Ends with the compliance code line (format `MKTG####-#######-EXP####`).
- Max width ≈ 900px, paragraph spacing 12px.

### 8.11 "Leaving BlackRock" dialog
- Title, explanatory paragraph, two actions: **Continue to third-party site** (Primary) and **Cancel** (Secondary/text).
- Radius `4px`, max-width 560px, elevation 2 over scrim.

---

## 9. States, motion, accessibility

| Topic | Spec | Status |
|---|---|---|
| Skip link | Present as first focusable element | **[Sourced]** |
| Alt text | Descriptive, full-sentence alt on hero photo | **[Sourced]** |
| Link titles | Sign In / Manage communications carry `title` attributes | **[Sourced]** |
| Focus | Always visible; 2px outline, never removed | **[Proposed]** |
| Hover | Colour/underline change only; no scale or shadow on cards | **[Proposed]** |
| Motion | 150–200ms `cubic-bezier(0.2, 0, 0, 1)`; menus fade + 8px slide; respect `prefers-reduced-motion` | **[Proposed]** |
| Touch targets | ≥ 44×44px | **[Proposed]** |
| Text contrast | ≥ 4.5:1 body, ≥ 3:1 large/UI (see §2.4) | calculated |
| Known page quirk | Footer wordmark link is labelled "Go to BlackRock Advisor homepage" although this is the Individual site. Do not copy that label | **[Sourced]** |

---

## 10. Content and voice — **[Sourced]**

- Section eyebrows are short noun phrases: `Q3 FIXED INCOME OUTLOOK`, `FUNDS AT BLACKROCK`, `ABOUT US`.
- Headlines are plain-language claims or questions ("What's changed in…", "Funds that match up with…").
- CTAs are verb-first and specific: "Read the Fixed Income Outlook", "See bond funds", "Learn more about BlackRock", "View all funds". Avoid "Click here".
- Copy explains the asset class in two or three sentences before linking out.
- Legal language is never trimmed on marketing pages.

---

## 11. Do / Don't

**Do**
- Let white space and photography carry the page; keep colour rare.
- Use Vermilion once per viewport as the single point of emphasis.
- Use black text on Vermilion and Supernova fills.
- Keep every card a single link target with one CTA.
- Keep uppercase eyebrows short (≤ 4 words) with wide tracking.
- Keep the disclosures visible and legible.

**Don't**
- Don't set white text on Vermilion below 24px (fails contrast).
- Don't use `#FF4713` for small text on white.
- Don't add drop shadows to cards, gradients on buttons, or heavy rounding.
- Don't recolour or restyle the wordmark.
- Don't use more than one Supernova element per view.
- Don't reproduce the hero photo; use original or licensed imagery in the same style.

---

## 12. Ready-to-use tokens

```css
:root {
  /* Brand palette — sourced (Brandfetch) */
  --br-vermilion:   #FF4713;
  --br-supernova:   #FFCE00;
  --br-spring-wood: #F4F1EB;
  --br-black:       #000000;
  --br-white:       #FFFFFF;

  /* Extended neutrals & states — proposed */
  --br-grey-900:          #1A1A1A;
  --br-grey-600:          #595959;
  --br-grey-200:          #D9D6CF;
  --br-vermilion-strong:  #C93400; /* text-safe on light */
  --br-vermilion-pressed: #B82E00;
  --br-scrim:             rgba(0, 0, 0, 0.60);

  /* Semantic roles */
  --color-bg:            var(--br-white);
  --color-bg-alt:        var(--br-spring-wood);
  --color-bg-inverse:    var(--br-black);
  --color-text:          var(--br-black);
  --color-text-muted:    var(--br-grey-600);
  --color-text-inverse:  var(--br-white);
  --color-accent:        var(--br-vermilion);
  --color-accent-text:   var(--br-vermilion-strong);
  --color-highlight:     var(--br-supernova);
  --color-border:        var(--br-grey-200);

  /* Typography — proposed until extractor confirms */
  --font-sans: "Helvetica Neue", Helvetica, Arial, "Segoe UI", system-ui, sans-serif;
  --fs-display: clamp(2.125rem, 1.4rem + 2.6vw, 3.5rem);
  --fs-h2:      clamp(1.75rem, 1.3rem + 1.6vw, 2.5rem);
  --fs-h3:      clamp(1.25rem, 1.1rem + 0.6vw, 1.5rem);
  --fs-lede:    clamp(1.125rem, 1.05rem + 0.3vw, 1.25rem);
  --fs-body:    1rem;
  --fs-small:   0.875rem;
  --fs-eyebrow: 0.8125rem;
  --fs-legal:   0.75rem;
  --tracking-eyebrow: 0.08em;

  /* Shape — proposed until extractor confirms */
  --radius-none: 0;
  --radius-sm:   2px;
  --radius-md:   4px;
  --radius-lg:   8px;
  --radius-pill: 999px;

  /* Elevation */
  --shadow-1: 0 8px 24px rgba(0, 0, 0, 0.12);
  --shadow-2: 0 16px 48px rgba(0, 0, 0, 0.24);

  /* Spacing (4px base) */
  --space-1: 4px;  --space-2: 8px;   --space-3: 12px;  --space-4: 16px;
  --space-5: 24px; --space-6: 32px;  --space-7: 48px;  --space-8: 64px;
  --space-9: 96px; --space-10: 128px;

  /* Layout */
  --container-max: 1280px;
  --gutter: 24px;

  /* Motion */
  --ease: cubic-bezier(0.2, 0, 0, 1);
  --dur-fast: 150ms;
  --dur-base: 200ms;
}

.eyebrow {
  font: 600 var(--fs-eyebrow)/1.25 var(--font-sans);
  letter-spacing: var(--tracking-eyebrow);
  text-transform: uppercase;
}

.btn-accent {
  background: var(--br-vermilion);
  color: var(--br-black);           /* white would be 3.41:1 and fail AA */
  border-radius: var(--radius-sm);
  padding: 14px 24px;
  font: 600 1rem/1.5 var(--font-sans);
}
```

---

## 13. Measured-values worksheet (fill from the extractor)

After running `blackrock-extract-tokens.js`, overwrite the **[Proposed]** entries with these:

| Token | Measured value |
|---|---|
| Heading font-family | |
| Body font-family | |
| Font weights loaded | |
| H1 size / line-height / tracking | |
| H2 size / line-height | |
| H3 size / line-height | |
| Body size / line-height | |
| Eyebrow size / tracking / weight | |
| Body text colour (exact hex) | |
| Link colour (default / hover) | |
| Button bg / text / radius / padding | |
| Card radius / border / shadow | |
| Nav item size / weight / colour | |
| Footer bg / text colours | |
| Colour share: top 5 backgrounds | |
| Root CSS variables (`--*`) | |

The extractor's **"Colour share"** tables replace the estimated proportions in §2.2 with real screen-area percentages.
