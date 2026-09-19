# InvoiceStudio: Zero to Finished Product

**A complete, beginner-friendly build guide for the InvoiceStudio desktop application**
(Java 21 · JavaFX 21 · SQLite · PDFBox · ZXing — the "Obsidian & Gold" billing studio)

> Written as the companion book to the real source code in this repository. Every chapter
> shows the *actual* files of this project, in full, and explains them line by block.
> Nothing is invented: when the source is unclear or looks wrong, the book says so
> with a **GAP:** or **ISSUE:** marker instead of glossing over it.

---

## How the book is delivered

The book is written **in parts**. Each part contains two or three chapters and ends at a
chapter boundary with a `Next:` marker. When you reply **"continue"**, the next part is
written. Chapters are never compressed to fit a message — length is bounded only by
completeness.

| Part | Chapters | Status |
|---|---|---|
| Part 1 | 0 · 1 · 2 | ✅ written |
| Part 2 | 3 · 4 | 3 ✅ · 4 ✅ |
| Part 3 | 5 · 6 | 5 ✅ · 6 ✅ |
| Part 4 | 7 · 8 | 7 ✅ · 8 ✅ |
| Part 5 | 9 | ✅ |
| Part 6 | 10 · 11 | ✅ · ✅ |
| Part 7 | 12 | ✅ |
| Part 8 | 13 · 14 | ✅ · ✅ |
| Part 9 | 15 | ✅ |
| Part 10 | 16 · 17 | ✅ · ✅ |
| Part 11 | 18 | ✅ |
| Part 12 | 19 · 20 | ✅ · ✅ |
| Part 13 | 21 · 22 | ✅ · ✅ |
| Part 14 | A1 · A2 · A3 | ✅ · ✅ · ✅ |
| Part 15 | A4 · A5 | ✅ · ✅ |

> **The book is complete:** 23 chapters (Ch 0–22) + 5 appendices, 172 application files
> and 77 test files covered, 100 GAP / 121 ISSUE / 144 NOTE markers raised honestly.
> Start with [Chapter 0](chapter-00-how-to-use-this-book.md); the final coverage audit
> lives in [Appendix A5](appendix-file-inventory.md).

## Table of contents

| Ch | Title | File |
|---|---|---|
| 0 | How to Use This Book (and a Glossary Primer) | [chapter-00-how-to-use-this-book.md](chapter-00-how-to-use-this-book.md) |
| 1 | Your Computer, Java, and This Project — Setup & First Run | [chapter-01-setup-and-first-run.md](chapter-01-setup-and-first-run.md) |
| 2 | The Skeleton: Entry Point & the App's Data Home | [chapter-02-the-skeleton.md](chapter-02-the-skeleton.md) |
| 3 | The Database Foundation | [chapter-03-the-database-foundation.md](chapter-03-the-database-foundation.md) |
| 4 | Speaking SQLite: The DAO Pattern, Part 1 — Master Data | [chapter-04-dao-part-1-master-data.md](chapter-04-dao-part-1-master-data.md) |
| 5 | The DAO Pattern, Part 2 — Documents & Ledgers | [chapter-05-dao-part-2-documents-ledgers.md](chapter-05-dao-part-2-documents-ledgers.md) |
| 6 | The Language of the Business: Domain Models, Part 1 | [chapter-06-domain-models-part-1.md](chapter-06-domain-models-part-1.md) |
| 7 | Domain Models, Part 2: Templates & Design Objects | [chapter-07-domain-models-part-2-templates.md](chapter-07-domain-models-part-2-templates.md) |
| 8 | The Data Engine: DataManager & Caching | [chapter-08-data-engine.md](chapter-08-data-engine.md) |
| 9 | The Shell: Window, Theme, Sidebar, Navigation | [chapter-09-the-shell.md](chapter-09-the-shell.md) |
| 10 | Signing In: Authentication | [chapter-10-authentication.md](chapter-10-authentication.md) |
| 11 | Listing & Editing Data: The Master-Data Views | [chapter-11-master-data-views.md](chapter-11-master-data-views.md) |
| 12 | Selling: Billing End-to-End | [chapter-12-billing-end-to-end.md](chapter-12-billing-end-to-end.md) |
| 13 | Buying & Money: Purchases, Transactions, Expenses | [chapter-13-purchases-transactions-expenses.md](chapter-13-purchases-transactions-expenses.md) |
| 14 | Reports & Dashboards | [chapter-14-reports-and-dashboards.md](chapter-14-reports-and-dashboards.md) |
| 15 | The Template Designer | [chapter-15-the-template-designer.md](chapter-15-the-template-designer.md) |
| 16 | Rendering & PDF Export | [chapter-16-rendering-and-pdf-export.md](chapter-16-rendering-and-pdf-export.md) |
| 17 | Label Printing & the TSPL Thermal Pipeline | [chapter-17-label-printing-and-tspl.md](chapter-17-label-printing-and-tspl.md) |
| 18 | The MCP Server: The App as an AI Tool Server | [chapter-18-the-mcp-server.md](chapter-18-the-mcp-server.md) |
| 19 | The AI Assistant | [chapter-19-the-ai-assistant.md](chapter-19-the-ai-assistant.md) |
| 20 | The Knowledge Hub & In-App Documentation | [chapter-20-knowledge-hub.md](chapter-20-knowledge-hub.md) |
| 21 | Testing the Whole App | [chapter-21-testing-the-whole-app.md](chapter-21-testing-the-whole-app.md) |
| 22 | Build, Package, Release | [chapter-22-build-package-release.md](chapter-22-build-package-release.md) |
| A1 | Full-Project Architecture Recap & Data-Flow Map | [appendix-a1-architecture-recap.md](appendix-a1-architecture-recap.md) |
| A2 | End-to-End Runtime Walkthrough | [appendix-a2-runtime-walkthrough.md](appendix-a2-runtime-walkthrough.md) |
| A3 | Consolidated Performance Roadmap | [appendix-a3-performance-roadmap.md](appendix-a3-performance-roadmap.md) |
| A4 | Troubleshooting Guide · Glossary · Index | [appendix-a4-troubleshooting-glossary-index.md](appendix-a4-troubleshooting-glossary-index.md) |
| A5 | Final Coverage Audit | [appendix-file-inventory.md](appendix-file-inventory.md) *(now the completed audit)* |

## The rules this book obeys

1. **Complete, never compressed.** Every file, class, method, resource and config used by
   the app is shown in full and explained. No "etc.", no "similar to above".
2. **Beginner-first.** Every technical term is explained the first time it appears.
   "Obviously" and "simply" are banned — nothing is obvious on first contact.
3. **Why, not just what.** Every piece of code exists to solve a problem; each chapter
   tells the story of that problem first.
4. **Faithful build, honest improvements.** The code shown is the code in the repo.
   Better alternatives appear in clearly labelled **OPTIONAL IMPROVEMENT** blocks, with
   trade-offs, so the original decisions stay visible.
5. **Gaps are marked, never guessed.** Anything unclear in the source is flagged
   `GAP:`/`ISSUE:` in place, with an explanation of what is uncertain and why.
6. **Every chapter ends with a coverage self-check** listing exactly what was covered.
