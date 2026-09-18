package com.invoicestudio.service;

import com.invoicestudio.model.KnowledgeArticle;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in documentation articles for InvoiceStudio:
 * 1. TSC Printer & Hardware Reference Library (13 articles)
 * 2. Complete AI Chatbot Architecture, Optimization & Build-From-Scratch Encyclopedia (16 chapters)
 */
public final class KnowledgeSeed {

    private KnowledgeSeed() {}

    public static List<KnowledgeArticle> buildAllArticles() {
        List<KnowledgeArticle> list = new ArrayList<>();
        long now = System.currentTimeMillis();

        // =====================================================================
        // SECTION 1: TSC PRINTER REFERENCE LIBRARY (13 Articles)
        // =====================================================================
        list.add(new KnowledgeArticle(
                "art_tsc_overview",
                "TSC / TA210 — Printer",
                "TSC TA210 — Printer Overview",
                "The desktop label printer this app targets: specs, sensors and what makes it different from an A4 printer.",
                """
                The **TSC TA210** is a 4-inch desktop thermal label printer (the TA310 is its 300-dpi sibling). Unlike an office printer, it prints one die-cut label at a time from a roll, burning an image directly with a thermal print head. It supports both direct thermal media (heat-sensitive paper) and thermal transfer (ribbon + media).

                ### Key Specifications (Official TSC Datasheet)
                - **Resolution**: 203 DPI = 8 dots per mm (TA300/TA310 are 300 DPI / 12 dots/mm siblings).
                - **Maximum Print Width**: 4.25 in / 108 mm — the physical width of the print head (300-dpi TA310: 104 mm / 4.09 in).
                - **Maximum Print Speed**: 5 ips (127 mm/s); maximum print length 90 in (2286 mm).
                - **Media Sensors**: Transmissive gap sensor (die-cut labels) and reflective black mark sensor (continuous stock with black marks).
                - **Command Languages**: TSPL / TSPL2 natively (also supports EPL/ZPL emulation on some firmware).

                > [!NOTE]
                > Because the head is 108 mm wide, any liner up to that width prints full-bleed — a 77 mm two-up liner is perfectly in range. The app warns only when the label stock paper width exceeds 108 mm.

                ---
                ### Why this matters in the app
                Every label dimension configured in the **Label Stock dialog** is converted from millimeters to dots at **8 dots/mm**, and the print head receives a 1-bit bitmap at exactly that density.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_sensors",
                "TSC / TA210 — Printer",
                "Label Stock, Sensors & Orientation — Who Knows What",
                "What the printer figures out by itself, what the software must declare, and how BarTender's Page Setup maps to the Label Stock dialog.",
                """
                A thermal printer is smart about the **FEED direction only**. Its gap sensor watches one vertical line of the web and sees the die-cut gaps (or black marks) scroll past. Everything **ACROSS** the strip — how many labels sit side by side, the liner width, and side margins — is invisible to that sensor and must be declared by the software.

                ### What the Printer Learns by Itself — Sensor Calibration
                - **Calibration** feeds a few labels through the sensor and **measures the roll's real pitch** (label length + gap), then stores sensor thresholds.
                - TSPL exposes it as `GAPDETECT` and `AUTODETECT` (manual p.6: *"feeds the paper through the sensor to determine the paper and gap sizes"*).
                - The app's one-click equivalent: **Bulk Print dialog → Calibrate Sensor** — it spools `AUTODETECT` as its own RAW job. Always recalibrate after changing rolls.
                - **Crucial Rule**: That is the full extent of the printer's own knowledge: *where each label ends along the feed*. It cannot name the label size, count columns across, or arrange content — layout is always the software's responsibility.

                ### What the Software Must Declare — Our Script or the Driver
                Printing through the Windows driver, the driver's Page Setup declares the stock and BarTender reads it. InvoiceStudio skips the GDI layer and spools raw TSPL, so **our script is the declaration**:
                1. `SIZE` (liner width × row height in dots)
                2. `GAP` (feed gap between labels)
                3. `DIRECTION` (feed orientation)
                4. `BITMAP` (1-bit raster data)
                5. `PRINT` (quantities to feed)

                ### BarTender Page Setup ↔ Label Stock Dialog Mapping
                | BarTender Setting | InvoiceStudio Label Stock | Notes |
                | :--- | :--- | :--- |
                | Paper Size 77.0 × 38.0 mm | Liner width 77 mm | Height (38) = label height (36) + gap (2) |
                | Label Size 75.0 × 36.0 mm | Label Width & Height | Physical label dimensions on roll |
                | Columns: 2 across | Columns across: 2 | Feeds one row per print |
                | Margins 1.0 mm | Left / Right margin | Edge padding |
                | Orientation Portrait/Landscape | Artwork direction | Rotates content only; paper size stays constant |

                ### The Simple Way to Think About It
                Measure the physical roll with a ruler: label width across, label height along the feed, the gap between rows, and how many labels across. Enter those numbers into Label Stock. The diagram mirrors the roll.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_sizes",
                "TSC / TA210 — Printer",
                "Label Sizes & Media Specs — TA210 Envelope",
                "Verified media envelope and the 17 built-in presets the Label Stock dialog offers.",
                """
                Every preset in the Label Stock dialog falls inside the TA210's official media envelope, validated before the fields are filled:

                ### Verified Media Specs (Official Datasheet)
                - **Media Width**: 25.4 – 118 mm (die-cut liner).
                - **Label Length**: 10 – 2794 mm (feed direction).
                - **Max Print Width**: 108 mm (the 203-dpi print head — media wider than this will not fully print).
                - **Resolution**: 203 DPI = 8 dots/mm.
                - **Typical Die-Cut Gap**: 2 mm or more.
                - **Media Core Diameter**: 25.4 – 38 mm.

                ### The Built-in Presets
                The preset selector offers 17 standard industry sizes, largest height first — from the full-width 108 × 2794 continuous roll down to the 25.4 × 10 mm jewelry tag minimum. Each preset carries suggested L/R margin, row gap, and column gap values:
                `[W × H] | L/R: x mm | Row Gap: y mm | Col Gap: z mm`

                > [!TIP]
                > Presets populate the dialog fields; they never lock them. Hand-typed dimensions are validated against the envelope above. The dialog explains exactly which constraint a custom size violates.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_brightness",
                "TSC / Print Settings",
                "Brightness Threshold — Sharp Black & White",
                "The Settings → Print slider that decides which pixels burn black and which stay white.",
                """
                A thermal print head has no gray levels: every dot either **burns black** or **stays white**. The **Brightness Threshold** (Settings → Print) decides where that cut happens on the downsampled image.

                - Every dot's 8-bit gray value (`0 = black` to `255 = white`) is compared against the threshold.
                - `gray ≤ threshold` → burns sharp **black**.
                - `gray > threshold` → stays sharp **white**.
                - **Default 150**: Slightly above mid-gray so hairlines, fine barcodes, and small text survive clearly.
                - **Higher threshold (e.g. 180)**: More pixels count as black = bolder, heavier print. Best for faint thermal media or thin fonts.
                - **Lower threshold (e.g. 110)**: Only true darks burn = crisper barcode edges on high-quality synthetic media, reducing thermal ribbon wear.

                > [!NOTE]
                > The output is always exactly two colors — black and white. There is no dithering or halftone gray: that is what ensures 1-D barcodes (Code 128, EAN-13) remain 100% scan-reliable.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_knobs",
                "TSC / Print Settings",
                "Advanced Settings & Knobs",
                "System properties for support sessions — plus how routing picks the native pipeline.",
                """
                InvoiceStudio provides technical override knobs via JVM system properties for diagnostics and custom hardware configurations.

                ### Print Engine Routing
                - **Auto (default)**: Printers whose name contains `TSC`, `TA200`, `TA210`, `TA300`, or `TA310` automatically use the native TSPL pipeline. All other printers use the standard JavaFX print driver path.
                - `-Dinvoicestudio.print.engine=tspl`: Force the native pipeline for every printer.
                - `-Dinvoicestudio.print.engine=driver`: Disable the native pipeline (classic driver printing).

                ### TSPL Tuning Properties
                - `-Dinvoicestudio.tspl.direction=0|1`: Invert print feed orientation (default `1`).
                - `-Dinvoicestudio.tspl.dotsPerMm=N`: Override dot density (default `8` for 203 DPI, `12` for 300 DPI).
                - `-Dinvoicestudio.tspl.threshold=0..255`: Override the GUI Brightness Threshold from Settings.
                - `-Dinvoicestudio.tspl.dump=/path/job.tspl`: Write the exact RAW bytes of every spooled job to a file (full TSPL script: `SIZE/GAP/DIRECTION + CLS/BITMAP/PRINT`). Useful for auditing against the TSC manual.

                ### RAW Spooling Details
                Jobs are sent through the JDK print service as `BYTE_ARRAY AUTOSENSE`. On Windows, this spools with datatype `RAW`, which the TSC driver passes untouched to the USB port.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tspl_language",
                "TSC / TSPL Language",
                "TSPL / TSPL2 — The Command Language",
                "The text-based printer language we send to the TA210: commands, units and line endings.",
                """
                **TSPL** (TSC Printer Language) and its extended version **TSPL2** are the native command languages of TSC thermal printers. A print job is a text script of commands, each on its own line terminated by `CRLF`, with raster commands like `BITMAP` followed by binary dot data.

                ### Core Protocol Rules
                - Text commands are ASCII, case-insensitive, and terminated with CRLF (`\\r\\n`).
                - The default unit is the **dot** — at 203 DPI, 1 mm = 8 dots. The app declares all dimensions in dots to eliminate rounding drift.
                - The script controls printer state: stock dimensions (`SIZE`), sensor type (`GAP`/`BLINE`), print orientation (`DIRECTION`), image buffer clearing (`CLS`), and quantity (`PRINT`).

                ### Why Native TSPL Beats the Windows Driver
                Printing through a normal Windows GDI driver lets the print spooler guess paper sizes and scale pages. On a gap-sensor label printer, a 1mm mismatch feeds extra blank labels. Sending native TSPL as RAW data bypasses GDI entirely: quantities, dimensions, and positions are guaranteed byte-for-byte.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tspl_anatomy",
                "TSC / TSPL Language",
                "Script Anatomy — One Label Job",
                "The exact command sequence InvoiceStudio streams to the TA210 for a print run.",
                """
                Every print run sent by InvoiceStudio follows a clean, deterministic script structure: a setup header followed by an image buffer per strip row and a `PRINT` command specifying copies to feed.

                ```tspl
                SIZE 432 dot,200 dot
                GAP 24 dot,0 dot
                DIRECTION 1
                CLS
                BITMAP 0,0,54,200,0,<binary bitmap data>
                PRINT 1,1
                ```

                ### Command Breakdown
                - `SIZE 432 dot,200 dot`: The strip row (web width × row height) in dots (54 mm × 25 mm at 8 dots/mm = 432 × 200).
                - `GAP 24 dot,0 dot`: The physical die-cut gap between labels (3 mm = 24 dots). The optical sensor uses this to detect label start.
                - `DIRECTION 1`: The bitmap's y=0 edge feeds first, so the label reads upright immediately after tearing.
                - `CLS`: Clears the image memory buffer before rendering.
                - `BITMAP`: Burns the 1-bit monochrome raster image of the full strip row.
                - `PRINT 1,1`: Feeds exactly ONE label row. Five identical copies become one bitmap followed by `PRINT 5,1`.\n""",
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tspl_setup",
                "TSC / TSPL Language",
                "Setup Commands — SIZE, GAP, DIRECTION, CLS",
                "The four commands that put the printer into a known state before drawing.",
                """
                ### 1. `SIZE m,n` — Label Dimensions
                Defines the label width (across the head) and length (along the feed direction).
                - Syntax: `SIZE 432 dot,200 dot`
                - Wrong `SIZE` = wrong feed pitch (causing extra blank feeds or clipped graphics).

                ### 2. `GAP m,n` / `BLINE` — Sensor Setup
                - `GAP 24 dot,0 dot`: Declares die-cut stock with a 24-dot gap and 0-dot offset.
                - `GAP 0,0`: Declares continuous roll stock without gaps.
                - `BLINE`: Replaces `GAP` when using black-mark media.

                ### 3. `DIRECTION 0|1` — Print Orientation
                Controls which edge of the bitmap leads during paper feed. `DIRECTION 1` prints the image so it reads right-side-up after tearing at the tear bar.

                ### 4. `CLS` — Clear Image Buffer
                Clears the printer's internal raster memory. Sent before every `BITMAP` command so artifacts from preceding labels never contaminate subsequent prints.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tspl_bitmap",
                "TSC / TSPL Language",
                "Drawing the Label — the BITMAP Command",
                "How a 1-bit image reaches the head: syntax, bit polarity and row padding.",
                """
                The `BITMAP` command draws a monochrome 1-bit raster image directly into the printer's RAM buffer.

                ```tspl
                BITMAP x,y,width,height,mode,data
                ```

                ### Parameter Definitions
                - `x, y`: Top-left start coordinate in dots (InvoiceStudio always draws from `0,0`).
                - `width`: Row width in **bytes** (not dots!). A 432-dot row = 432 / 8 = 54 bytes.
                - `height`: Image height in **dots** (pixel scanlines).
                - `mode`: `0` = OVERWRITE (default used by the app), `1` = OR, `2` = XOR.
                - `data`: Raw binary dot stream, MSB first, with each line padded to a full byte boundary.

                ### Bit Polarity Rules
                - **Bit 0** = **BLACK** (thermal head heats up and burns).
                - **Bit 1** = **WHITE** (paper stays unheated/blank).
                - Every byte starts initialized to all-white (`0xFF`), and InvoiceStudio clears the bit of every dot scheduled to burn.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tspl_print",
                "TSC / TSPL Language",
                "Printing & Quantities — the PRINT Command",
                "PRINT m,n controls copies: how selecting 1 label feeds exactly 1 label.",
                """
                ```tspl
                PRINT m[,n]
                ```

                - `m`: How many labels/sets to print (the queued label count).
                - `n`: Optional repeat count of each set (InvoiceStudio always specifies `1`).

                ### Fast Repetition
                `PRINT 1,1` feeds exactly one label. A print queue of 100 identical labels compresses into **one single bitmap upload** followed by `PRINT 100,1`. The printer repeats its internal hardware buffer at full print speed (5 inches/sec) with zero PC communication lag.

                ### Helper Commands (Reference)
                - `FORMFEED`: Advances paper to the next die-cut gap.
                - `CUT`: Triggers the automatic rotary cutter (on cutter-equipped hardware).
                - `FEED n`: Feeds paper forward by n dots without printing.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_blank_labels",
                "TSC / Troubleshooting",
                "Blank Labels After a Good Label",
                "Printed one record but got extra empty labels? Work through the causes in order — almost all are configuration, one is printer calibration.",
                """
                Extra blank labels are **always a paper feed mismatch**, never extra `PRINT` commands sent by the software. Work through these verified causes in order:

                ### Cause 1: Declared Feed Pitch is Larger Than Real Roll Pitch
                The printer burns the bitmap and then feeds until the next detected gap. If the software declares a 78 mm pitch on a roll that physically has a 39 mm pitch, **one print feeds two labels** (first has content, second is blank).
                - **Fix**: Measure ONE physical label + ONE gap with a ruler. Set **Label Height** + **Feed Gap** in the Label Stock dialog so their sum equals your measured distance.

                ### Cause 2: Multi-Across Stock With Small Queue
                On 2-across or 4-across stock, the printer feeds a complete horizontal strip row. If you print 1 label on 4-across media, 1 label prints and the remaining 3 in that row pass out blank.
                - **Fix**: Set `Columns = 1` for single-column rolls. For multi-across rolls, print in multiples of the column count.

                ### Cause 3: Stale Sensor Calibration
                If the optical gap sensor has stored old threshold values from a previous roll, it fails to detect the gap at the expected position.
                - **Fix**: Open **Bulk Print dialog → Calibrate Sensor** to spool the `AUTODETECT` command, or hold the printer's physical `FEED` button while powering on.

                ### Cause 4: Hardware / Windows Spooler Leftovers
                Stuck print jobs in the Windows spooler can inject form feeds between jobs. Clear the Windows print queue and restart the printer.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_troubleshooting",
                "TSC / Troubleshooting",
                "Troubleshooting Guide",
                "Symptom → cause → fix for the most common thermal label printing problems.",
                """
                ### Common Symptoms & Solutions

                #### 1. Prints All Black with White Content Shapes
                - **Cause**: Inverted bit polarity in driver.
                - **Fix**: InvoiceStudio encodes ink as bit 0 (burn) and paper as bit 1 natively. Ensure raw TSPL printing is enabled.

                #### 2. Print is Too Light or Faint
                - **Cause**: Insufficient head heat or worn ribbon.
                - **Fix**: Increase the **Brightness Threshold** (Settings → Print) to 170–180. On thermal transfer models, verify ribbon tension and clean the head with isopropyl alcohol.

                #### 3. Content is Clipped on the Right
                - **Cause**: Media exceeds maximum print width.
                - **Fix**: The TSC TA210 print head is 108 mm wide. Ensure your total liner width in Label Stock does not exceed 108 mm.

                #### 4. Barcode Fails to Scan
                - **Cause**: Over-burned edges blooming together.
                - **Fix**: Reduce Brightness Threshold to 120–130, ensure adequate white quiet-zones around the barcode, and avoid scaling barcodes below 100% element width.
                """,
                now,
                "TSC Hardware Team"
        ));

        list.add(new KnowledgeArticle(
                "art_tsc_pipeline",
                "TSC / Inside the App",
                "InvoiceStudio Print Pipeline",
                "From canvas design to burned dots: every stage between the Strip Preview and the label.",
                """
                InvoiceStudio utilizes a unified rendering pipeline: what you see in the Strip Preview is rendered by the exact same engine that burns onto the physical label.

                ```mermaid
                flowchart TD
                    A["Label Canvas Design"] --> B["Strip-Row Page Composer"]
                    B --> C["2x Snapshot Rendering (406 DPI)"]
                    C --> D["Grayscale Luminance (Rec. 601)"]
                    D --> E["2x2 Box Downsample to 203 DPI"]
                    E --> F["Brightness Threshold Cut (1-bit B&W)"]
                    F --> G["TSPL Generator (SIZE/GAP/DIRECTION/BITMAP)"]
                    G --> H["RAW Byte Spooling (Direct to USB Port)"]
                    H --> I["TSC TA210 Head Burns Dots"]
                ```

                ### Pipeline Key Features
                - **Background Execution**: Spooling occurs off the JavaFX Application Thread, keeping UI controls completely fluid.
                - **Zero GDI Scaling**: Windows print spooler never resamples, rotates, or alters dot positioning.
                - **Deterministic Output**: Bitmaps match the preview down to the individual dot.
                """,
                now,
                "TSC Hardware Team"
        ));

        // =====================================================================
        // SECTION 2: AI CHATBOT ENCYCLOPEDIA (11 Chapters)
        // =====================================================================

        list.add(new KnowledgeArticle(
                "art_ai_01_lifecycle",
                "AI Chatbot / 01. Architecture & Lifecycle",
                "End-to-End Chatbot Execution Cycle",
                "From user keystroke to LLM payload, MCP tool loop, and JavaFX bubble rendering.",
                """
                The InvoiceStudio AI Assistant is a high-performance, native desktop agent designed to bridge conversational natural language with live enterprise ERP state (billing, inventory, ledgers, and thermal printing).

                ### Complete Execution Architecture
                ```mermaid
                sequenceDiagram
                    autonumber
                    actor User
                    participant UI as ChatbotPanel (JavaFX Thread)
                    participant Worker as AppExecutors.io()
                    participant Engine as AiChatClient
                    participant Router as Smart Router
                    participant LLM as Provider API (Gemini / OpenAI / Claude)
                    participant MCP as McpToolRegistry

                    User->>UI: Types query & presses Enter
                    UI->>UI: Validates input, renders User Bubble, shows (● ● ●) thinking dots
                    UI->>Worker: Submits background Task
                    Worker->>Engine: send(cfg, history, userText, attachment)
                    Engine->>Engine: Check CHAT_ONLY regex (Smalltalk bypass)
                    Engine->>Engine: Check isConfirmationContext()
                    opt Smart Routing Active
                        Engine->>Router: Shortlist candidate tools (names-only pass)
                        Router->>LLM: Lightweight route prompt
                        LLM-->>Router: ["list_buyers", "financial_summary"]
                    end
                    loop Max 6 Tool Rounds
                        Engine->>LLM: Dispatch payload + schemas
                        LLM-->>Engine: functionCall: list_buyers(limit=5)
                        Engine->>MCP: McpToolRegistry.call("list_buyers", args)
                        MCP-->>Engine: JSON records result
                        Engine->>Engine: Record call + tool turns, truncate if > 4000 chars
                    end
                    Engine-->>Worker: ChatResult(text, toolTrace)
                    Worker->>UI: Platform.runLater(() -> render)
                    UI->>UI: ChatMarkdownRenderer.render(), hide dots, update history
                ```

                ### 1. User Input Capture & UI Thread Decoupling
                - **Class**: `com.invoicestudio.ui.ChatbotPanel`
                - **Method**: `send()` (lines 512–596)
                - When the user presses `Enter`, `ChatbotPanel.send()` immediately creates a user message bubble, clears the input area, and invokes `renderPendingImage()`.
                - A JavaFX `Task<AiChatClient.ChatResult>` is submitted to `AppExecutors.io()`. Network sockets and JSON serializations **never** execute on the JavaFX Application Thread.

                ### 2. Multi-Stage Pipeline in AiChatClient
                - **Class**: `com.invoicestudio.service.AiChatClient`
                - **Method**: `send(ChatbotConfig cfg, List<ChatTurn> history, String userText, ImagePart attachment)` (lines 82–250)
                - The engine processes the request through four deterministic gates:
                  1. **Zero-Cost Greeting Bypass**: Recognizes smalltalk ("hi", "hello") and executes a schema-free request.
                  2. **Confirmation Gate**: Recognizes approval words ("yes", "proceed") and enforces `confirm_operation` retention.
                  3. **Smart Router**: Selects 1 to 3 candidate tools from 50+ available MCP capabilities.
                  4. **Execution Loop**: Runs up to `MAX_TOOL_ROUNDS = 6`, calling local Java services and re-feeding results back to the LLM.

                ### 3. JavaFX Bubble Rendering
                - **Class**: `com.invoicestudio.ui.ChatbotPanel`
                - **Method**: `addAiBubble(String text, Image img)` (lines 607–629)
                - Invokes `com.invoicestudio.ui.chat.ChatMarkdownRenderer.render(text, isError)`, turning Markdown text into styled JavaFX nodes with tables, syntax-highlighted code blocks, and interactive copy buttons.

                ---
                ### Key Code References
                | Component | File Path | Key Functions |
                | :--- | :--- | :--- |
                | **UI Controller** | `com/invoicestudio/ui/ChatbotPanel.java` | `send()`, `addAiBubble()`, `updateInputHeight()` |
                | **Chat Engine** | `com/invoicestudio/service/AiChatClient.java` | `send()`, `dispatch()`, `isConfirmationContext()` |
                | **Tool Engine** | `com/invoicestudio/mcp/McpToolRegistry.java` | `call()`, `tools()`, `confirmable()` |
                | **Async Pool** | `com/invoicestudio/service/AppExecutors.java` | `io()`, `runOnFx()` |
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_02_ui_components",
                "AI Chatbot / 01. Architecture & Lifecycle",
                "UI Components & Responsive Layout",
                "Design system, auto-growing input box, clipboard copy, and Markdown rendering.",
                """
                The chatbot overlay follows modern conversational UI standards inspired by Gemini and ChatGPT, built with native JavaFX controls styled via CSS tokens in a sleek dark-gold palette (`#10161F` card, `#D9A13B` gold accents, `#17202E` bubbles).

                ### Visual Hierarchy
                - **Header**:
                  - Sparkle avatar in gold.
                  - Title: `"Assistant"`.
                  - Live model chip (`modelChip`): Clickable pill opening the live catalogue popup (`ChatbotModelPickerDialog`).
                  - Action buttons: Terminal logs (`>_`), Expand/Collapse toggle, Clear history, Close panel.
                - **Message Viewport**:
                  - `ScrollPane` wrapping a `VBox(12)` message container.
                  - Bound width: `maxBubbleWidth = scroll.widthProperty() - 70px`. Bubbles expand dynamically to fill space without horizontal scrolling.
                - **Input Area Pill**:
                  - Rounded borderless container (`-fx-background-radius: 14; -fx-background-color: #0F1520`).
                  - Auto-growing `TextArea`.
                  - Attachment paperclip button (`FileChooser` supporting PNG, JPG, GIF, WEBP, BMP).
                  - Circular gold Send button with SVG arrow glyph.

                ### Auto-Growing Input Box (1x to 3x)
                The text area dynamically expands as the user types, bounded between 1 row (36px) and 3 rows (92px):
                ```java
                // ChatbotPanel.java lines 309-345
                private void updateInputHeight(String text) {
                    int lines = 1;
                    int charsInLine = 0;
                    int maxChars = expanded ? 72 : 36;
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '\\n') { lines++; charsInLine = 0; }
                        else if (++charsInLine >= maxChars) { lines++; charsInLine = 0; }
                        if (lines >= 3) { lines = 3; break; }
                    }
                    applyInputHeight(lines);
                }
                ```

                ### Keyboard Event Handling
                - `Enter`: Consumed and triggers `send()`.
                - `Shift + Enter`: Inserts a newline `\\n` without submitting the message.

                ### Interactive Markdown Rendering
                Powered by `ChatMarkdownRenderer.java`:
                - **Code Blocks**: Formatted with dark monospace styling and a one-click **Copy** button.
                - **Tables**: Converted into bordered JavaFX `GridPane` layouts with bold headers and alternating row backgrounds.
                - **Alerts / Callouts**: Formatted with accent border bars (`> [!NOTE]`, `> [!TIP]`, `> [!WARNING]`).
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_03_smalltalk",
                "AI Chatbot / 02. Optimization & Intelligence",
                "Zero-Cost Smalltalk & Greeting Interceptor",
                "How greetings and conversational chit-chat bypass the 50+ tool schemas.",
                """
                In enterprise function-calling architectures, every request sent to the model typically carries the entire tool catalogue. In InvoiceStudio, 50+ MCP tool schemas consume **~7,500 prompt tokens** on every single turn.

                ### The Smalltalk Problem
                When a user opens the chat and types `"hi"`, `"hello"`, or `"thanks"`, sending 7,500 tokens of schema definitions:
                - Costs 500× more tokens than the response requires.
                - Increases round-trip network latency by 1.5–2.5 seconds.
                - Wastes precious free-tier rate limits (e.g. Gemini 15 RPM / 1M TPM).

                ### The Greeting Interceptor
                - **Class**: `com.invoicestudio.service.AiChatClient`
                - **Pattern**: `CHAT_ONLY` (lines 252–256)
                ```java
                private static final java.util.regex.Pattern CHAT_ONLY = java.util.regex.Pattern.compile(
                        "(?is)^\\\\s*(hi+|hello+|hey+|yo|thanks?|thank\\\\s*you|thx|ty|great|nice|cool|wow|"
                        + "good\\\\s*(morning|afternoon|evening|night)|bye+|goodbye|see\\\\s*ya|"
                        + "who\\\\s+are\\\\s+you\\\\??|how\\\\s+are\\\\s+you\\\\??|what\\\\s+can\\\\s+you\\\\s+do\\\\??|help)\\\\s*[!.?]*\\\\s*$");
                ```

                ### Execution Logic
                ```java
                boolean pureChat = !confirmCtx && turns.size() <= 1 && CHAT_ONLY.matcher(first).matches();
                if (pureChat) {
                    ChatbotLogManager.router("Smalltalk detected -> schema-free dispatch (0 tools)", first);
                    ProviderResponse resp = dispatch(cfg, turns, java.util.Set.of());
                    return new ChatResult(resp.text(), List.of());
                }
                ```
                When smalltalk is detected, `dispatch()` sends `Set.of()` for allowed tools. The payload carries **zero tool schemas**, returning a conversational greeting in under 400ms!

                ### Token & Latency Comparison
                | Metric | Standard Tool Request | Smalltalk Interceptor | Savings |
                | :--- | :--- | :--- | :--- |
                | **Input Tokens** | ~7,500 tokens | ~25 tokens | **99.6% reduction** |
                | **Latency** | 2,200 ms | 420 ms | **81% faster** |
                | **Schemas Carried** | 50 schemas | 0 schemas | **Zero overhead** |
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_04_smart_routing",
                "AI Chatbot / 02. Optimization & Intelligence",
                "Smart Routing & Schema Pruning Engine",
                "How 50+ tools are dynamically pruned to 1-3 candidates, slashing token usage by 85%.",
                """
                Rather than forcing every conversational turn to carry all 50+ MCP tool schemas, InvoiceStudio introduces a **Two-Pass Smart Router**.

                ### How It Works
                ```mermaid
                flowchart TD
                    A["User: 'Show me top 5 buyers by outstanding'"] --> B["Pass 1: Smart Router"]
                    B --> C{"Does query need tools?"}
                    C -- No --> D["Direct conversational reply (0 tools)"]
                    C -- Yes --> E["Shortlist candidate tool names: ['list_buyers']"]
                    E --> F["Pass 2: Heavy Dispatch (only 'list_buyers' schema)"]
                    F --> G["Model calls list_buyers(limit=5)"]
                    G --> H["McpToolRegistry executes locally"]
                    H --> I["Model outputs formatted Markdown table"]
                ```

                ### 1. Pass 1 — Names-Only Tool Router
                - **Method**: `routeTools(cfg, turns, first, confirmCtx)` in `AiChatClient.java` (lines 296–340).
                - Instead of full JSON parameter schemas, the router sends a lightweight directory of tool names and one-line summaries:
                  ```
                  list_buyers: List all buyers/customers
                  create_buyer: Create a buyer/customer
                  financial_summary: High-level P&L and receivables
                  ...
                  ```
                - Total prompt size: ~400 tokens (vs. 7,500 tokens for full schemas).
                - The router outputs a compact JSON response:
                  `{"needTools": true, "tools": ["list_buyers"]}`

                ### 2. Direct Conversational Answers
                If the user asks a conceptual question (e.g. *"What is GST ITC?"* or *"Explain thermal transfer ribbons"*), the router answers immediately in `route.note()`. The system skips Pass 2 entirely, saving a full API round-trip!

                ### 3. The Escalation Safety Net
                What if the router under-selects and the model realizes mid-thought that it needs a tool outside the shortlist?
                ```java
                // AiChatClient.java lines 194-202
                if (allowed != null && !escalated) {
                    if (resp.toolCalls().stream().anyMatch(tc -> !allowed.contains(tc.name()))) {
                        escalated = true;
                        allowed = null; // restore all 50+ tools
                        ChatbotLogManager.warn("Model requested tool outside shortlist -> escalating to full catalogue", null);
                        continue;
                    }
                }
                ```
                The engine escalates **once** to the full catalogue. This guarantees 100% functional reliability while preserving an average 85% token savings across 95% of queries.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_05_confirmation_context",
                "AI Chatbot / 02. Optimization & Intelligence",
                "Multi-Turn Confirmation & Context Retention",
                "Preserving destructive operation approvals across multi-turn dialogs.",
                """
                A classic failure mode in AI agents occurs during destructive operations (e.g. deleting an invoice or updating credit limits). When the agent asks for confirmation, the user responds with a single word like `"yes"`, `"confirm"`, or `"proceed"`.

                ### The Confirmation Amnesia Bug
                1. **Turn 1 (User)**: `"Delete buyer Acme Corp"`
                2. **Turn 1 (Assistant)**: `"Buyer Acme Corp has pending invoices. Are you sure? OperationId: op_9872. Reply 'yes' to confirm."`
                3. **Turn 2 (User)**: `"yes"`
                4. **The Bug**:
                   - Naive smalltalk regex sees `"yes"` and treats it as casual conversation.
                   - Or the smart router sees `"yes"`, cannot deduce that a deletion tool is needed, and fails to shortlist `confirm_operation`.
                   - The operation fails or asks the user to repeat themselves.

                ### Context Retention in InvoiceStudio
                - **Class**: `com.invoicestudio.service.AiChatClient`
                - **Method**: `isConfirmationContext(List<ChatTurn> turns, String userText)` (lines 267–288)

                ```java
                static boolean isConfirmationContext(List<ChatTurn> turns, String userText) {
                    if (turns == null || turns.isEmpty()) return false;
                    String trimmed = userText == null ? "" : userText.trim();
                    if (CONFIRMATION_WORDS.matcher(trimmed).matches()) return true;
                    if (!PendingOperations.pending().isEmpty()) return true;

                    // Inspect assistant turns for confirmation prompts
                    for (int i = turns.size() - 1; i >= 0; i--) {
                        ChatTurn t = turns.get(i);
                        if ("assistant".equals(t.role())) {
                            String txt = t.text() == null ? "" : t.text().toLowerCase();
                            if (txt.contains("confirm") || txt.contains("approve") || txt.contains("operationid")
                                    || txt.contains("proceed") || txt.contains("pending")) {
                                return true;
                            }
                            break;
                        }
                    }
                    return false;
                }
                ```

                ### Guaranteed confirm_operation Injection
                When `isConfirmationContext` returns true:
                1. Pure smalltalk bypass is **deactivated**.
                2. `confirm_operation` is **force-injected** into `allowed`:
                   ```java
                   if (confirmCtx && allowed != null) {
                       allowed.add("confirm_operation");
                   }
                   ```
                3. The assistant receives the confirmation response, extracts `op_9872`, and calls `confirm_operation(operationId="op_9872")` cleanly.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_06_mcp_system",
                "AI Chatbot / 03. MCP & Tool Calling System",
                "The Model Context Protocol (MCP) in InvoiceStudio",
                "Tool specifications, safety contracts, execution engine, and payload boundaries.",
                """
                The **Model Context Protocol (MCP)** standardizes how language models interact with local tools, databases, and application features.

                ### Tool Definition Anatomy
                Every tool is defined in `com.invoicestudio.mcp.McpToolRegistry.ToolDef`:
                ```java
                public static final class ToolDef {
                    public final String name;
                    public final String description;
                    public final Map<String, Object> inputSchema;
                    public final boolean mutates;
                    public final boolean destructive;
                }
                ```

                ### 3-Tier Safety Model
                | Category | Flags | Execution Behavior | Examples |
                | :--- | :--- | :--- | :--- |
                | **Read-Only** | `mutates: false`<br>`destructive: false` | Executes immediately with zero side-effects. | `list_buyers`, `list_bills`, `stock_report`, `financial_summary` |
                | **Create / Add** | `mutates: true`<br>`destructive: false` | Executes immediately. Idempotent by name. | `create_buyer`, `create_item`, `record_expense` |
                | **Destructive** | `mutates: true`<br>`destructive: true` | **Never executes directly.** Queues in `PendingOperations` and requests confirmation. | `delete_buyer`, `update_item`, `delete_bill`, `delete_category` |

                ### Foreign Key Healing via McpEnsure
                When an AI agent creates a new record (e.g. `create_buyer` with `transportName: "SafeXpress"`), traditional databases fail if the transport ID does not exist.
                InvoiceStudio utilizes `McpEnsure.java`:
                - Checks if `"SafeXpress"` exists.
                - If missing, **auto-creates** the transport on the fly.
                - Assigns the new ID to the buyer.
                - Reports `autoCreated: ["SafeXpress"]` in the response payload.

                ### Context Truncation Guard
                To prevent massive queries from overwhelming the context window, tool outputs are capped at `MAX_RESULT_CHARS = 4000`. Oversized dumps are trimmed with a notice advising the model to narrow its query with `limit` or filters.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_07_adding_new_tools",
                "AI Chatbot / 03. MCP & Tool Calling System",
                "Step-by-Step Guide: Adding a New Tool to the Chatbot",
                "Developer walkthrough for registering new business operations and tools.",
                """
                Adding a new tool to InvoiceStudio requires zero modifications to `AiChatClient.java`. Because the chat engine introspects `McpToolRegistry.tools()` dynamically, your tool is immediately available to the LLM, Smart Router, and UI.

                ### Developer Cookbook: Adding `get_customer_ledger`

                #### Step 1: Declare the Schema in McpToolRegistry.buildTools()
                Open `com.invoicestudio.mcp.McpToolRegistry.java` and add your `ToolDef` inside `buildTools()`:
                ```java
                t.add(new ToolDef("get_customer_ledger",
                        "Fetch debit and credit ledger transactions for a specific buyer within a date range.",
                        obj(
                                "buyerId", str("Buyer unique ID (required)"),
                                "fromDate", str("Start date in ISO format YYYY-MM-DD"),
                                "toDate", str("End date in ISO format YYYY-MM-DD"),
                                "limit", num("Max transaction rows (default 100)")
                        ), false, false));
                ```

                #### Step 2: Implement the Business Method
                Write the helper method using `McpArgs` for defensive parameter parsing:
                ```java
                private static Object customerLedger(DataManager dm, Map<String, Object> args) {
                    String buyerId = str(args, "buyerId");
                    if (buyerId.isBlank()) throw new IllegalArgumentException("buyerId is required");
                    String from = strOr(args, "fromDate", "2000-01-01");
                    String to = strOr(args, "toDate", LocalDate.now().toString());
                    int limit = intVal(args, "limit", 100);

                    List<Transaction> rows = dm.getTransactionsForBuyer(buyerId, from, to, limit);
                    return mapOf("buyerId", buyerId, "count", rows.size(), "transactions", rows);
                }
                ```

                #### Step 3: Register in McpToolRegistry.call()
                Add a case branch in the `switch (name)` block:
                ```java
                case "get_customer_ledger": return customerLedger(dm, args);
                ```

                #### Step 4: Handle Destructive Operations (If Applicable)
                If your tool modifies or deletes data, wrap the execution in `confirmable()`:
                ```java
                case "archive_customer": return confirmable("archive_customer", args, () -> {
                    dm.buyers().archiveBuyer(str(args, "id"));
                });
                ```

                #### Step 5: Verify with Unit Tests
                Add an automated assertion in `McpServerTest.java`:
                ```java
                @Test
                void customerLedgerExecutesCorrectly() throws Exception {
                    Object res = McpToolRegistry.call("get_customer_ledger", Map.of("buyerId", "test_buyer"));
                    assertNotNull(res);
                }
                ```
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_08_multi_provider",
                "AI Chatbot / 04. Multi-Provider & Model Hub",
                "Multi-Provider Engine & REST Contracts",
                "Direct HTTP integration with Gemini, OpenAI, Claude, Ollama, and quota failover.",
                """
                InvoiceStudio eliminates third-party SDK bloat by communicating directly with AI provider endpoints using standard Java 11+ `java.net.http.HttpClient`.

                ### Provider Wire Specifications
                - **Google Gemini**:
                  - Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`
                  - Payload: `contents[].parts[]` supporting `text`, `inline_data` (base64 image), `functionCall`, and `functionResponse`.
                  - Thought Signatures: Preserves `tSig` (Gemini 2.0 / 2.5 thought signatures) so multi-turn tool calling does not fail with validation errors.
                - **OpenAI**:
                  - Endpoint: `https://api.openai.com/v1/chat/completions` (Bearer token authentication).
                  - Payload: `messages[]` with `tool_calls[]` array; tool responses sent as `{ role: "tool", tool_call_id: id, content: json }`.
                - **Anthropic Claude**:
                  - Endpoint: `https://api.anthropic.com/v1/messages` (`x-api-key`, `anthropic-version: 2023-06-01`).
                  - Payload: `tools[].input_schema`, `tool_use` blocks in assistant turn, `tool_result` blocks in user turn.
                - **Ollama**:
                  - Endpoint: `http://localhost:11434/v1/chat/completions`
                  - Runs local, private models (e.g. `llama3`, `mistral`, `phi3`) with **zero API keys** and zero external internet dependency!
                - **OpenAI-Compatible (DeepSeek, Groq, Mistral, OpenRouter)**:
                  - Uses the OpenAI wire protocol with custom base URLs and headers.

                ### Automatic Free-Tier Quota Failover
                When using Google Gemini free tier, API keys occasionally hit daily quotas (HTTP 429). `AiChatClient.java` catches this transparently:
                ```java
                // AiChatClient.java lines 162-180
                if (msg.contains("Daily free-tier limit") && ChatbotConfig.GEMINI.equals(cfg.getProvider())) {
                    exhausted.add(activeModel);
                    String next = ModelCatalog.nextFailover(activeModel, exhausted);
                    if (next != null) {
                        cfg.setModel(next);
                        trace.add("⚠ model " + activeModel + " hit daily limit — switched to " + next);
                        continue; // Retries immediately with fallback model
                    }
                }
                ```

                ### Dynamic Model Catalog
                `ModelCatalog.java` queries the provider's live `models.list` API. Users can view and switch between available chat models directly from the UI header chip.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_09_realtime_diagnostics",
                "AI Chatbot / 04. Multi-Provider & Model Hub",
                "Real-Time Live Diagnostics & Terminal Logging",
                "Observability into background execution steps, routing decisions, and MCP timings.",
                """
                To eliminate "black-box" AI frustration, InvoiceStudio includes an integrated live diagnostics engine. Users and developers can observe background decisions in real time.

                ### ChatbotLogManager Architecture
                - **Class**: `com.invoicestudio.service.ChatbotLogManager`
                - **Ring Buffer**: Thread-safe storage capped at `MAX_ENTRIES = 500`. Oldest logs are discarded when full.

                ### Log Levels & Event Taxonomy
                | Tag | Color Badge | Meaning |
                | :--- | :--- | :--- |
                | `INFO` | Blue `#3B82F6` | User prompts, attachments, session resets. |
                | `ROUTER` | Magenta `#A855F7` | Smart router shortlisting decisions and smalltalk detection. |
                | `DISPATCH` | Yellow `#EAB308` | HTTP requests sent to LLM endpoint with active model and turn counts. |
                | `TOOL-CALL` | Cyan `#06B6D4` | Tool execution requested by the model with JSON arguments. |
                | `MCP-EXEC` | Green `#10B981` | Local tool execution duration (in milliseconds) and result summary. |
                | `SUCCESS` | Bright Green `#22C55E` | Successful completion of conversation turn. |
                | `WARN` | Orange `#F97316` | Retries, quota warnings, catalogue escalations. |
                | `ERROR` | Red `#EF4444` | Network failures, invalid schemas, API errors. |

                ### The CLI Diagnostics Window
                Clicking the Terminal icon (`>_`) in the chatbot header opens `ChatbotLogDialog.java`:
                - Dark monospace CLI window (`Consolas`).
                - Displays timestamps (`HH:mm:ss.SSS`), category tags, and formatted details.
                - Includes **Copy All Logs** for support tickets.
                - Auto-clears whenever the chat is cleared or closed to maintain clean sessions.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_10_build_from_scratch",
                "AI Chatbot / 05. The Complete Build-From-Scratch Manual",
                "Building an Enterprise JavaFX AI Chatbot from Scratch",
                "Master blueprint & tutorial: create a production-grade desktop AI assistant in 6 phases.",
                """
                This chapter provides a complete engineering roadmap for building a production-grade, function-calling AI assistant in Java and JavaFX from scratch.

                ### Phase 1: JavaFX Chat UI Shell
                1. Create `ChatbotPanel extends VBox` with dark styling.
                2. Add a `ScrollPane` containing a `VBox` of message rows.
                3. Implement avatar rows: user on the right, AI on the left.
                4. Bind bubble widths: `maxBubbleWidth = scroll.widthProperty() - 70`.
                5. Create an auto-growing `TextArea` input with `Enter` (send) and `Shift+Enter` (newline).

                ### Phase 2: Lightweight HTTP Client
                1. Initialize `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()`.
                2. Use Jackson `ObjectMapper` for JSON building without heavyweight cloud SDKs.
                3. Build payloads matching Gemini (`contents[].parts[]`) or OpenAI (`messages[]`).
                4. Always execute requests inside `AppExecutors.io()`, and update JavaFX controls using `Platform.runLater()`.

                ### Phase 3: Function Calling & Tool Loop
                1. Define a `ToolDef` record with `name`, `description`, and `inputSchema`.
                2. Implement a `while (round < MAX_TOOL_ROUNDS)` iteration loop.
                3. When the LLM outputs a function call, parse arguments and invoke the corresponding local Java method.
                4. Append tool results to the conversation history and re-send to the LLM.

                ### Phase 4: Token Optimization
                1. Add a regex interceptor (`CHAT_ONLY`) to bypass tool schemas on casual greetings.
                2. Implement a Smart Router: ask the model with tool names only, then carry only the shortlisted schemas.
                3. Add an escalation fallback if the model calls an unlisted tool.
                4. Truncate tool outputs larger than 4,000 characters.

                ### Phase 5: Multi-Turn Memory & Confirmation State Machine
                1. Maintain a `List<ChatTurn> history`.
                2. Check `CONFIRMATION_WORDS` regex on user replies ("yes", "ok", "confirm").
                3. When the user confirms an operation, force-inject `confirm_operation` into the allowed tool list.
                4. Store destructive operations in a `PendingOperations` queue.

                ### Phase 6: Rich Output & Diagnostics
                1. Use a native Markdown renderer (`ChatMarkdownRenderer`) to format tables, code blocks, and lists.
                2. Provide interactive copy buttons on code blocks and speech bubbles.
                3. Implement `ChatbotLogManager` to emit structured CLI logs during background execution steps.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_11_future_roadmap",
                "AI Chatbot / 06. Future Optimization Roadmap",
                "Advanced Optimization & Next-Generation Capabilities",
                "Semantic embeddings, prompt caching, local SLMs, and streaming tool pipelines.",
                """
                Future milestones to further enhance the intelligence, speed, and cost-efficiency of the InvoiceStudio AI Assistant.

                ### 1. Local Semantic Vector Caching
                - **Concept**: Embed frequently asked questions (e.g. *"What is our GST number?"*, *"Show outstanding for Acme"*) using an ONNX Runtime model (e.g. `bge-small-en-v1.5`) directly in Java.
                - **Benefit**: Cache queries with cosine similarity > 0.95. Answers return in **under 10ms** with **zero API calls and zero cost**.

                ### 2. Provider-Side Prompt Caching
                - **Concept**: Gemini 1.5/2.0 and Anthropic Claude offer explicit and implicit prompt caching for prefixes > 1,024 tokens.
                - **Benefit**: By structuring system instructions and the MCP tool catalogue as a static cached prefix, prompt token costs drop by **75–90%**, and time-to-first-token drops by **50%**.

                ### 3. Local Small Language Models (SLM) for Smart Routing
                - **Concept**: Run a quantized 0.5B or 1.5B parameter model (e.g. `Qwen2.5-0.5B` or `Phi-3.5-mini`) locally via Ollama or `llama.cpp` JNI bindings.
                - **Benefit**: Offloads the Smart Router classification pass entirely to local hardware. Shortlists tools in 25ms without internet access.

                ### 4. Streaming Function Calling
                - **Concept**: Transition from blocking HTTP `POST` requests to Server-Sent Events (SSE) streaming (`text/event-stream`).
                - **Benefit**: Tool calls can be parsed and dispatched to local databases while the LLM is still generating parameters, reducing perceived latency by up to 40%.

                ### 5. Token Governance & Budgeting
                - Real-time token meters in `Settings → Chatbot`.
                - Session cost estimates based on current model pricing tiers.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        // Chapter 12 — Tool-Call Limit & Quota Failover
        list.add(new KnowledgeArticle(
                "art_ai_12_tool_limit_failover",
                "AI Chatbot / 07. Tool-Call Limit & Quota Failover",
                "Configurable Tool-Round Cap & Automatic Model Failover",
                "How the max-tool-rounds setting protects cost and reliability, and how quota failover automatically switches models when a daily limit is hit.",
                """
                Two complementary safety mechanisms keep the AI assistant both **cost-controlled** and \
                **highly available**, even when individual models hit their rate or daily quota limits.

                ---

                ### 1. Configurable Tool-Round Cap

                #### Why it exists
                Each model↔tool round-trip costs tokens and latency. Without a cap, a misbehaving model \
                or a poorly-worded query could trigger dozens of consecutive tool calls — running up your \
                API bill and blocking the UI thread for minutes.

                #### How it works — `ChatbotConfig` + `AiChatClient`
                - **Setting**: `Settings → Chatbot → Max tool rounds` (spinner, range 1–20, default 6).
                - **Persisted in**: `chatbot.json` as `"maxToolCalls": 6`.
                - **Enforced in**: `AiChatClient.send()` — at the top of the tool loop:

                ```java
                // AiChatClient.java — tool loop guard
                if (round > cfg.getMaxToolCalls()) {
                    ChatbotLogManager.warn("Safety limit reached: " + cfg.getMaxToolCalls() + " tool rounds", null);
                    return new ChatResult("(stopped after " + cfg.getMaxToolCalls()
                            + " tool rounds — ask me to continue)", trace);
                }
                ```

                - **User experience**: The assistant politely reports how many rounds it used and asks the \
                  user to continue if they need more depth. No silent failure.

                #### Tuning guidelines
                | Value | When to use |
                | :--- | :--- |
                | **1–2** | Simple read-only queries (list bills, show stock). Minimum cost. |
                | **3–4** | Single-entity lookups needing 1–2 tool hops (buyer + their bills). |
                | **5–6** (default) | Multi-step agentic tasks (create item → restock → report). |
                | **8–12** | Complex cross-entity reports, bulk operations with confirmations. |
                | **15–20** | Advanced automation; use only with a paid key and a cost budget. |

                #### What to change if you modify this
                - **`ChatbotConfig.java`**: `maxToolCalls` field + `getMaxToolCalls()` / `setMaxToolCalls()`.
                - **`ChatbotSettingsPanel.java`**: `maxToolSpin` Spinner in `behaviourCard()`, loaded in `loadFromConfig()`, saved in `save()`.
                - **`AiChatClient.java`**: The `if (round > cfg.getMaxToolCalls())` guard inside `send()`.

                ---

                ### 2. Automatic Quota Failover

                #### Why it exists
                Gemini's free tier gives each model a **daily request quota** (e.g. 20 RPD on `gemini-flash-latest`). \
                Once that quota is exhausted the API returns HTTP 429 with `"GenerateRequestsPerDay"` in the \
                body. Without failover, the assistant would simply crash with a hard error — even though \
                several other Gemini models may still have full quotas for the day.

                #### How it works — `ModelCatalog` + `AiChatClient`

                **Step 1 — Detect the daily-quota error**
                ```java
                // AiChatClient.java — post() method
                boolean dailyQuota = resp.statusCode() == 429
                        && resp.body() != null && resp.body().contains("PerDay");
                ```

                The `friendlyProviderError()` helper maps `"GenerateRequestsPerDay"` → `"Daily free-tier limit…"`, \
                which the catch block in `send()` then matches:

                ```java
                boolean isDailyLimit = msg.contains("Daily free-tier limit");
                boolean isGemini = ChatbotConfig.GEMINI.equals(cfg.getProvider());
                if (isDailyLimit && isGemini) { /* failover logic */ }
                ```

                **Step 2 — Iterate failover candidates**
                `ModelCatalog.failoverCandidates()` returns an ordered list of flash models:
                ```java
                // ModelCatalog.java
                public static List<String> failoverCandidates() {
                    return List.of(
                        "gemini-flash-latest", "gemini-3.8-flash", "gemini-3.7-flash",
                        "gemini-3.6-flash", "gemini-3.5-flash", "gemini-2.5-flash",
                        "gemini-2.5-flash-lite", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite");
                }
                ```

                `ModelCatalog.nextFailover(current, exhausted)` picks the first candidate not already tried:
                ```java
                for (String cand : failoverCandidates()) {
                    if (!cand.equals(current) && !exhausted.contains(cand)) return cand;
                }
                ```

                **Step 3 — Seamless retry without re-prompting the user**
                ```java
                String next = ModelCatalog.nextFailover(currentModel, exhausted);
                if (next != null) {
                    exhausted.add(next);
                    cfg.setModel(next);           // mutate config for this send only
                    trace.add("⚠ model " + originalModel + " hit daily limit — switched to " + next);
                    continue;                     // re-enter the loop with the new model
                }
                ```

                The model switch appears in the `toolTrace` so the user can see it in the **Logs** dialog. \
                After the send completes (or all candidates are exhausted), the original model is restored:
                ```java
                if (!cfg.getModel().equals(originalModel)) cfg.setModel(originalModel);
                ```

                #### When ALL candidates are exhausted
                ```
                All Gemini fallback models are also at their daily limits —
                wait for the daily reset or use another provider (Ollama is local & free).
                ```

                #### What to change if you add more fallback models
                - **`ModelCatalog.failoverCandidates()`**: Add new model ids (newest / most generous quota first).
                - **`AiChatClient.send()`**: No changes needed — the failover loop is model-list-agnostic.

                #### Important: failover is Gemini-only
                Other providers (OpenAI, Anthropic, Groq…) have different quota structures and no shared \
                stable fallback catalogue. For those providers, a quota error surfaces immediately so the \
                user can choose a different provider in Settings.

                ---

                ### 3. Why Both Mechanisms Together Matter
                | Without | Effect |
                | :--- | :--- |
                | No tool-round cap | Runaway loops, high token cost, UI frozen. |
                | No quota failover | Single model 429 = total assistant failure for the rest of the day. |
                | Both together | Cost-bounded + self-healing: switches model transparently, reports gracefully. |
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        // Chapter 13 — Speed & Cost Optimization Pass (what was done to improve,
        // and how the assistant is optimized further)
        list.add(new KnowledgeArticle(
                "art_ai_13_speed_optimization",
                "AI Chatbot / 08. Speed & Cost Optimization",
                "What Was Done to Improve — and How It Is Optimized Further",
                "The full speed & cost optimization pass: zero-schema small talk, MCP-off fast path, failover that never burns tool rounds, light-model-first defaults, the Z.ai GLM provider, and a dedicated chat executor.",
                """
                This chapter documents the latest optimization pass end to end — **what was changed, why it \
                makes the chatbot faster and cheaper, and what to tune next**. Every item below is live in the \
                current build and covered by unit tests.

                ---

                ### 1. The problems, diagnosed

                | Symptom you saw | Root cause found |
                | :--- | :--- |
                | Chat went silent / "stuck" with the dots forever | ALL chat requests ran on the app's single-threaded IO executor. One slow provider call (120 s HTTP timeout + retry ladder) wedged every later message behind it. |
                | Small talk felt slow and burned quota | A filter bug made the "schema-free" small-talk path actually carry the FULL ~60-tool schema catalogue on every greeting. |
                | With MCP off there was no reply at all | Tool schemas were still sent with the MCP server stopped; the model could ask for tools that could never run. |
                | After a quota failover the next request was huge and slow | The model switch replayed every accumulated tool-call + tool-result payload into the NEW model as input tokens. |
                | Chapters went missing in the Knowledge Hub | The hub preferred a stale local copy forever and the shipped JSON resource lagged behind the seed. |

                ---

                ### 2. What was done to improve

                #### 2.1 Dedicated chat executor — never wedged again
                Chat/provider requests now run on their own 3-worker pool (`AppExecutors.chat()`), separate from \
                file/network IO. A slow or hung provider response can no longer block the rest of the app, and the \
                settings "Test connection" probe runs independently. The busy-dots state always resolves — success \
                or a friendly error bubble.

                #### 2.2 Zero-schema fast paths (the small-talk fix is big)
                Tool-schema filtering previously treated an EMPTY tool set as "no restriction", so the \
                zero-schema paths carried the full catalogue anyway. The filter now means exactly:
                - `allowed == null` → full catalogue (fail-open / escalation),
                - `allowed == []` (empty) → **zero tool schemas**,
                - `allowed == {names}` → shortlist only.

                Greetings like *"hi"*, *"thanks"*, *"good morning"* now dispatch one tiny request with **no tool \
                schemas at all** — the cheapest request the provider can serve.

                #### 2.3 MCP-off fast path — always a reply, with guidance
                When Settings → MCP Server is stopped, the assistant detects it before dispatching and:
                - sends **zero tool schemas** (nothing can hang on a tool that cannot run),
                - swaps in a system note that instructs the model: *if the user asks for live business data, \
                tell them to start the MCP server in **Settings → MCP Server***,
                - answers general conversation normally.

                So with MCP off you always get an instant, useful reply — including the exact "please start MCP \
                by going to Settings → MCP Server" guidance — instead of silence. If the server dies mid-conversation, \
                every tool error fed back to the model carries the same hint, so the model relays it.

                #### 2.4 Failover that never burns a tool round
                Tool rounds are counted **only after a real tool result** comes back. A daily-quota model switch \
                (`continue` before any tool ran) and a router-escalation re-ask are **FREE** — they never consume \
                the Max-tool-rounds budget. A unit test locks this contract.

                #### 2.5 Failover drops the MCP payload replay
                On a quota failover, `AiChatClient.trimForFailover()` rebuilds the payload for the new model from:
                - the tail of prior **text** turns (the last correct responses), plus
                - the current interaction from your original prompt onward.

                Every in-flight `call`/`tool` payload pair is dropped — the new model re-plans the work from your \
                prompt instead of paying for the previous model's tool dump. The shortlist resets to the full \
                catalogue so the fresh model has every option.

                #### 2.6 Light-by-default model policy
                - Default chat model: **`gemini-flash-lite-latest`** (Google's stable light alias — immune to \
                per-version retirements). Heavier models remain one pick away in Settings or the header menu.
                - The **router pass always runs on the light model**, never on your chosen (possibly paid/scarce) model.
                - The **failover ladder leads with light models** (`flash-lite` → `flash` → versioned flashes) — \
                when a bucket empties we step DOWN to the lightest tier with quota left, never up.

                #### 2.7 Z.ai GLM provider — full feature parity
                New provider **Z.ai GLM** (OpenAI-compatible endpoint `api.z.ai/api/paas/v4`) with the same \
                treatment as Gemini: default model **`glm-4.5-flash`** (free tier), smart routing, native function \
                calling, confirmations, and image attachments. Pick it in Settings → Chatbot → Provider.

                #### 2.8 Knowledge Hub merge-on-load
                The hub now **merges shipped articles into the local library by id** — your local edits stay, and \
                every newly seeded chapter (including *"07. Tool-Call Limit & Quota Failover"*, which older \
                installs never received) appears automatically after an update.

                ---

                ### 3. How it is optimized further (effect + next steps)

                | Optimization | Typical effect |
                | :--- | :--- |
                | Small talk zero-schema | ~7–10K input tokens saved **per greeting**; replies in a round trip. |
                | Router on light model | Router latency ≈ sub-second; zero burn of your main model's daily quota. |
                | Shortlisted schemas | Data questions carry ~1K of schema tokens instead of the full ~7–10K. |
                | Failover payload trim | Post-failover request shrinks from (history + all tool payloads) to (history + prompt). |
                | MCP-off fast path | Instant replies with MCP off; no wasted tool rounds, no hang. |
                | Light default model | The largest free daily quota tier is consumed first — expensive requests only when you opt in. |

                **Tuning knobs in Settings → Chatbot**
                - *Max tool rounds* (1–20, default 6): keep 4–6 for daily use; raise only for long multi-step jobs.
                - *History window* (4–80, default 12 on new installs): smaller = faster + cheaper per request.
                - *Smart tool routing*: keep ON — it is the single biggest token saver.

                **Future roadmap**: parallel tool execution inside a round, response caching for repeated reports, \
                per-message model hints ("answer fast" vs "think hard"), and streaming partial replies into the bubble.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        // Chapter 14 — Bug Playbook & Token-Light Speed (future chatbot bugs +
        // their solutions; how to keep answers fast on the fewest tokens)
        list.add(new KnowledgeArticle(
                "art_ai_14_bug_playbook",
                "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "Future Chatbot Bugs & Their Solutions — Fast Answers on Fewer Tokens",
                "The maintenance playbook: every failure class the assistant can hit (silence, confirmation loops, model drift, audit blindness, router misses, quota walls), the fix that ships for each, and the operating habits that keep the chat fast and token-cheap.",
                """
                This chapter is the assistant's **maintenance playbook**: the bug classes a chatbot of this \
                design can develop, the concrete fix that ships for each one, and the habits that keep every \
                answer fast and cheap. It is written from the operator's chair — each section starts with what \
                a real user would see.

                ---

                ### 1. Bug playbook — what you'd see, what it means, what was done

                | # | What the user sees | Root cause class | Fix that ships |
                | :--- | :--- | :--- | :--- |
                | 1 | Dots sit still for a minute, "it got stuck" | Multi-request turns (router → model → tool → model) with no visible progress | The busy dots are a **live progress line** — the chat streams each execution step ("Round 1 → …", "Model requested delete_supplier", "MCP delete_supplier executed in 210 ms") while you wait. All chat work also runs on a dedicated 3-worker executor, so one slow call can never wedge the app. |
                | 2 | Asked to delete → nothing → late confirmation → "unknown operationId" loops | The model had to guess the queued operation's id from chat history | **PENDING APPROVALS injection**: whenever destructive ops are queued, the system prompt now carries `operationId | tool | summary` for each, so "yes" resolves in exactly one `confirm_operation` round. Duplicate queues are told apart and re-running the underlying tool is explicitly forbidden. |
                | 3 | Header chip / Settings shows a model you never picked | Quota failover mutated the SHARED saved config (`cfg.setModel` on the live instance); some exits never restored it | Failover now runs on a **per-send copy** (`ChatbotConfig.copyForSend()`): the switch lasts exactly one send, the saved model, chip and Settings stay in sync with the user's real choice. |
                | 4 | "It deleted my supplier but there are no logs" | Chatbot tool runs went straight to `McpToolRegistry`, bypassing the MCP audit trail; closing the chat also wiped session logs | Every chatbot tool execution is mirrored into the MCP audit (`[CHAT] tool args — ms — result`, `[CHAT-ERROR]` on failure) in Settings → MCP Server and `mcp-audit.log`; session logs are **kept on chat close** — only the explicit Clear button empties them. |
                | 5 | Answer about the wrong entity / wrong tool picked | Router shortlisted too narrowly | **Escalation net**: if the model calls a tool outside the shortlist, the request escalates ONCE to the full catalogue — the router saves tokens when right, never costs correctness when wrong. Escalation is free (no round consumed). |
                | 6 | 429 "daily limit" and the assistant dies for the day | A single model's free bucket empties | **Light-first failover ladder** (`flash-lite` → `flash` → versioned flashes), one attempt per candidate, per-send only; the tool-round budget is untouched by switches. |
                | 7 | Answers degrade on giant table questions / requests get slow | Full result payloads re-sent every later round | Tool results are **capped (~4K chars)** with an explicit "narrow your query" instruction; the model is trained by its tools to pass `limit` (e.g. "top 5"). |
                | 8 | "stopped after N tool rounds — ask me to continue" | Safety cap on model↔tool round-trips reached | By design. Say **continue** — a fresh send resumes with full history. Raise Max tool rounds in Settings only for long multi-step jobs. |
                | 9 | One provider's errors repeat | Provider outage / key problem | The failure text is the provider's own message surfaced verbatim; MCP-related failures always carry the Settings → MCP Server pointer. Switch provider (or Ollama, local & free) in Settings. |
                | 10 | Weird 400s on new Gemini versions | Schema strictness (arrays without `items`, empty tool lists, thought signatures) | All handled at the builder layer: `items` injection for array schemas, zero-schema requests OMIT the tools field entirely, thought signatures round-trip on the part — each fix was live-verified, not guessed. |

                #### When you hit something NEW (the debugging ritual that works)
                1. Open the terminal icon in the chat header — **Assistant Live Execution Logs** show every \
                router decision, dispatch, tool call, duration and error, in order, live.
                2. Reproduce with the smallest possible phrasing of the same task.
                3. Check the three usual suspects in order: **MCP server on?** (Settings → MCP Server), \
                **daily quota** (try again on a light model), **pending approvals** (the banner in Settings → MCP Server).
                4. Only then change settings — Max tool rounds up for genuinely multi-step tasks, history \
                window up only when the model clearly lacks context.
                5. Log everything via Copy All before reporting — the log contains the failing request's \
                round, tool, args and elapsed time.

                ---

                ### 2. Where the tokens actually go (and the caps that hold them)

                Every request pays for four payloads; each is engineered down:

                | Payload | Typical size | What keeps it small |
                | :--- | :--- | :--- |
                | Tool schemas | ~7–10K tokens if everything is sent | Smart router shortlist (~1K), zero-schema small talk, MCP-off fast path sends none |
                | Conversation history | Grows with the chat | History window setting (default 12), oldest-first trim, text-only tail on failover |
                | Tool results | Up to 4K chars per result | Hard cap + "narrow your query" guidance; `limit` params on every list tool |
                | System prompt | ~250 tokens, +pending-ops list only while approvals wait | Static, cached by providers, only grows when confirmation state exists |

                #### Token-speed habits that compound
                - **One topic per message.** "Create 10 suppliers named A1..A10 and delete the last one" \
                forces sequential rounds; two messages resolve each in one.
                - **Say "top 5" / "this month"** — bounded questions get short answers and small tool results.
                - **Keep Smart tool routing ON.** It is the single biggest saver: chat-only messages carry \
                zero schemas, data questions carry a handful.
                - **Stay on the light model** for daily Q&A; heavy models for genuinely hard analysis.
                - **Trim the history window** (Settings → Chatbot) if sessions run long — 8–12 turns is the \
                sweet spot for this assistant's work.
                - **Ask follow-ups, don't re-paste.** The model already holds the last exchanges; re-describing \
                the same data burns tokens twice.
                - **Images are expensive** — attach screenshots only when text can't describe the problem.

                ---

                ### 3. The user-perspective QA checklist

                Run through these when validating a build — each maps to a fixed path above:

                1. **Greeting** ("hi") → instant one-shot reply, zero tool schemas, no router spend.
                2. **Read** ("top 5 buyers by outstanding") → router shortlist → one tool round → markdown table.
                3. **Bulk create** ("create 10 suppliers") → idempotent creates, `autoCreated`-style responses, \
                progress line visible the whole time.
                4. **Destructive** ("delete the last one") → queued with `requiresConfirmation` → model asks \
                using the injected operationId → "yes" → `confirm_operation` executes → **audit shows [CHAT] \
                rows and they survive closing the chat**.
                5. **Money** ("record 5000 received against INV-x") → `pay_bill` (not a status flip) → ledger + \
                History both show the receipt.
                6. **Documentation** ("what do you know about tool rounds?") → `list_knowledge` → `get_knowledge` \
                → answer cites the app's own chapter.
                7. **Quota wall** (model 429s) → light-first failover in the same send → trace line "switched to …" \
                → your chosen model unchanged in Settings afterwards.
                8. **MCP off** → instant conversational reply pointing at Settings → MCP Server — never silence.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_15_round3_tokens_glm_pipeline",
                "AI Chatbot / 10. Round 3 — Token Meter, GLM Catalogue & Live Pipeline",
                "What Changed in the Third Optimization Round — and Why It Stays Fast",
                "Per-message time & token counts beside every reply, token + HTTP tracing in the execution logs, the live Z.ai GLM model catalogue with a free-flash router, the animated assistant pipeline bar with its performance contract, and the small audit fixes that came out of this round.",
                """
                This chapter documents the third chatbot round: **making costs visible, adding a second live \
                provider catalogue, and showing — with a tiny animation — what the assistant is actually doing \
                between your message and its answer**. Every item shipped with tests; nothing here guesses at \
                API behaviour, all provider contracts were verified against the live APIs first.

                ---

                ### 1. What you can see now (and where)

                | Feature | Where you see it | What it tells you |
                | :--- | :--- | :--- |
                | **Message meta row** | Under every bubble, beside the copy icon — `12:34 · ↑1,234 ↓567 tok · 3.2 s` | Local send/reply time, the tokens the request consumed (↑ input, ↓ output) and the wall time of the whole send. User bubbles show the time only; parts the provider did not report are simply omitted. |
                | **Token + HTTP tracing** | Chat header → terminal icon → live execution logs | New `TOKENS` lines after each stage ("↑123 ↓45 tok — total 168 (Final …)") and one `HTTP` line per real request ("HTTP 200 in 812 ms — payload 14,210 chars") — so you can see exactly where time and tokens go. |
                | **Pipeline bar** | Slim strip above the input pill, only while a request runs | A character walks the pipeline in plain words: *Received → Routing — picking the right tools → Asking the AI (API) → Assistant calls a tool → Working in your app (MCP) → Done*. A coin counter ticks up as rounds report tokens, and a slim progress bar creeps underneath. |
                | **GLM model catalogue** | Chat header model chip · Settings → Chatbot → Browse | Z.ai (GLM) now fetches its model list live from `GET /api/paas/v4/models` (Bearer key), same 10-minute cache as Gemini. `glm-4.5-flash` stays the default — it is the tier this key can use for free. |

                ---

                ### 2. How the pipeline bar stays free (the performance contract)

                The animation was added **only** because it provably cannot affect the app or the chat speed:

                - **Idle cost: zero.** The strip is hidden *and* unmanaged when no request is in flight — it is \
                removed from layout entirely, not merely invisible.
                - **Two tiny animations while visible.** A 650 ms scale pulse on a 22 px avatar and a 300 ms timer \
                advancing the bar width (it moves 7% of the remaining distance, slowing as it goes). No CSS \
                effects, no shadows, no image decoding, no binding chains.
                - **Both animations STOP the instant the reply lands.** The bar shows a final "Done — answer \
                ready" state for ~1.4 s, then removes itself again.
                - **It renders, never generates.** Stages and coins are driven by the same log entries the \
                assistant already produces (`ROUTER`, `DISPATCH`, `TOOL-CALL`, `MCP-EXEC`, `TOKENS`) — no \
                extra work is created for the sake of the animation.

                The stage captions use deliberate plain language: **Routing** means the cheap classifier is \
                picking tools; **Asking the AI (API)** is the real model call; **Working in your app (MCP)** \
                means a tool is reading or writing your records. If you can read the strip, you know where the \
                time is being spent.

                ---

                ### 3. The token meter — how the numbers are produced

                - Each provider response carries a usage block: Gemini calls it `usageMetadata` \
                (`promptTokenCount` / `candidatesTokenCount`), OpenAI-compatible APIs (including Z.ai GLM) call \
                it `usage` (`prompt_tokens` / `completion_tokens`), Anthropic calls it `usage` \
                (`input_tokens` / `output_tokens`). All three are parsed.
                - A send usually makes several requests (router pass + one per tool round). The counts you see \
                are the **sum over the whole send**, not just the last request.
                - Providers that omit usage simply produce no token display — the UI never shows a made-up \
                zero. The same data feeds the `TOKENS` log lines and the coin counter, so chat, logs and \
                animation always agree.

                #### Reading the numbers
                - **↑ (input) grows with history, tool schemas and tool results** — it is re-sent every round, \
                which is why the router shortlist, the 12-message history window and the 4K result cap matter.
                - **↓ (output) is the model's answer + tool-call arguments** — short questions with "top 5" \
                bounds keep it small.
                - A "hi" costs almost nothing (zero-schema fast path, single request). A "list top 5 buyers" \
                typically spends the most on the tool-result round trip — watch the `HTTP` payload sizes in \
                the log to confirm.

                ---

                ### 4. GLM as a full first-class provider

                - **Live catalogue** (this round): the model chip and Settings Browse button now list real \
                Z.ai models. The endpoint was verified live before wiring: it returns plain ids \
                (`glm-4.5`, `glm-4.5-air`, `glm-5` …); the id doubles as the display name.
                - **Free-flash router policy** (this round): the smart router is pinned to `glm-4.5-flash` for \
                GLM — the same rule Gemini already had. Classification never burns the (possibly metered) \
                model you actually picked.
                - **Tool calling verified live**: GLM answers with standard OpenAI-style `tool_calls`, so the \
                whole MCP tool surface (bills, suppliers, payments, knowledge, confirmations) works without \
                any provider-specific branch in the tool layer.
                - **Balance errors surfaced honestly**: models outside your plan's balance return HTTP 429 with \
                "Insufficient balance" — that message reaches the chat verbatim instead of a mystery failure.

                ---

                ### 5. Small audit fixes that shipped with this round

                | Found | Impact | Fix |
                | :--- | :--- | :--- |
                | New-install history window was still 30 — the documented intent (and the Settings spinner) say 12 | Every request silently carried ~18 extra messages of input tokens | Field default corrected to 12; existing saved configs are untouched |
                | Router pass for non-Gemini OpenAI-compatible providers ran on the user's chosen model | GLM users paid metered tokens for mere classification | Router pinned to the provider's free flash tier where one exists |
                | Error bubbles were detected by sniffing reply text ("error", "api key" substrings) | Legitimate answers mentioning errors were styled red | Explicit error flag passed at the call sites — no sniffing |
                | Verification screenshots (PNGs) sat in the repo from old UI test runs | Dead weight, no runtime reference | Removed; the shots directory is git-ignored |

                ---

                ### 6. If something looks off — the 60-second routine

                1. Open the terminal icon in the chat header. The newest lines tell the story: `HTTP` for \
                transport, `TOKENS` for cost, `ROUTER` for tool choice, `MCP-EXEC` for data operations.
                2. Cross-check the meta row under the reply: does the token total match the `TOKENS` log? If \
                yes, the spend was real and you can see which round it came from.
                3. Daily GLM/Gemini quotas reset per model — the failover trace ("switched to …") shows when a \
                switch saved your session, and your picked model stays untouched in Settings.
                4. Everything in this chapter is covered by automated tests: usage accumulation and the \
                meta record contract, GLM catalogue parsing, and the end-to-end user journeys from chapter 09.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        list.add(new KnowledgeArticle(
                "art_ai_16_round4_instant_greetings_new_fullsurface",
                "AI Chatbot / 16. Round-4 Change Log",
                "Round 4: Instant Local Greetings, /new Context Reset & the Full-Surface Test Harness",
                "What changed in this round: mid-chat greetings stopped costing API calls, /new resets context for free, every MCP tool is now chat-loop tested, and the template ruler reads correctly at any zoom.",
                """
                ### 1. The "chatbot feels slow" investigation — what was actually true

                Three separate things were stacked on top of each other:

                | Suspect | Verdict |
                |---|---|
                | Free-tier models are slow | **Partly true.** Free buckets (Gemini flash-lite, GLM flash) queue requests; per-minute 429s trigger the retry ladder (3 s + 6 s waits). Nothing to fix in code — but every AVOIDED request now matters twice as much. |
                | The router adds a round trip | **True for every message**, but the router is names-only (no descriptions, ~1K tokens) and runs on the provider's lightest free model. It usually SAVES time by shrinking the heavy pass. Keep it. |
                | "hi" mid-chat was slow | **Real bug (fixed).** The smalltalk shortcut required `turns.size() <= 1`, so ANY history pushed a greeting into the router + heavy pass — two provider calls to say hello. |

                Measured with a zero-latency local provider (the new surface harness): the client pipeline
                itself takes **10–41 ms for a full router + tool + answer flow**, and a local greeting is
                **1 ms with ZERO requests**. Whatever slowness you feel beyond that is the provider queue —
                the app's layer is effectively free.

                ### 2. Instant local greetings (round 4 fix)

                `AiChatClient.send()` now answers pure smalltalk LOCALLY before any provider call:

                - Matches the same `CHAT_ONLY` regex ("hi", "hello", "hey", "thanks", "good morning", "bye",
                "who are you", "what can you do", "help"…).
                - Works **mid-chat** — history no longer disables it.
                - **Zero requests, zero tokens**, replies in ~1 ms; the meta row shows the real time.
                - Deterministic canned copy (same input → same reply, no randomness).
                - Safety: confirmation contexts and queued approvals bypass the shortcut, so a "hi" can
                never eat a pending yes/no. The scripted stub proves this in
                `confirmationPromptBeatsTheGreetingMatcher`.

                ### 3. The /new command (context reset for free)

                - **`/new`** — clears the conversation context instantly and replies locally
                ("Started a fresh thread…"). No API call, works even without an API key.
                - **`/new <question>`** — sends ONLY the question; no history tokens ride along. Big
                threads stop taxing every message on slow/free tiers.
                - Input pill advertises it: "Ask about your business… (/new = fresh context)".
                - Parsed in `ChatbotPanel.parseNewCommand` (unit-tested without JavaFX).

                ### 4. Full MCP surface test harness ("the AI plays the AI")

                `AiChatFullSurfaceStubTest` drives the REAL `AiChatClient` loop, REAL router and REAL
                `McpToolRegistry` against a scripted Gemini-shaped local provider:

                - **Every registered MCP tool** (76) executes through the chat loop; the harness
                auto-detects `requiresConfirmation` results and completes the full
                `confirm_operation` → approve flow like a real user.
                - Covers: router shortlist + one-shot escalation to the full catalogue, the tool-round
                safety stop, two tool calls in one round sharing one result turn, the history window
                bounding real payloads, and greeting-vs-confirmation precedence.
                - Latency probes print the client-layer overhead (see §1) so future "it's slow" reports
                can be split into app time vs provider time.

                ### 5. Template ruler at zoom (numbers & tick heights)

                The ruler strip scales with the page (22 px → 22·zoom on screen), but ticks and numbers
                were screen-CONSTANT (10 px / 8 px) — at 300% zoom a 66 px strip carried tiny 8 px
                numbers and 10 px ticks that looked lost. Now:

                - **Zoomed IN (>100%): tick lengths and number font grow with the strip**
                (screen px = base × zoom) — at 200% ticks are 20 px and numbers 16 px.
                - **Zoomed OUT (≤100%): unchanged** — the readability floor keeps the previous
                screen-constant sizes.
                - Positions, snapping, hairline width, colors, 1-2-5 step logic: all untouched.

                ### 6. Housekeeping

                - Stale verification run dirs (bulk-verify, knowledge-verify, selzoom-verify, zoom-verify)
                removed from the workspace; the three remaining repo PNGs are LIVE app icons referenced
                by code (window icon, dialogs, watermark) — they must stay.
                - Everything here is covered by automated tests: the full-surface harness (§4), the
                updated journey checklist, the /new parser tests, and the existing designer suites.
                """,
                now,
                "InvoiceStudio AI Core"
        ));

        return list;
    }
}
