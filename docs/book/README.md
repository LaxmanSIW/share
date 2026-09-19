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
| Part 4 | 7 · 8 | 7 ⬜ · 8 ⬜ |
| Part 5 | 9 | ⬜ |
| Part 6 | 10 · 11 | ⬜ |
| Part 7 | 12 | ⬜ |
| Part 8 | 13 · 14 | ⬜ |
| Part 9 | 15 | ⬜ |
| Part 10 | 16 · 17 | ⬜ |
| Part 11 | 18 | ⬜ |
| Part 12 | 19 · 20 | ⬜ |
| Part 13 | 21 · 22 | ⬜ |
| Part 14 | A1 · A2 · A3 | ⬜ |
| Part 15 | A4 · A5 | ⬜ |

## Table of contents

| Ch | Title | File |
|---|---|---|
| 0 | How to Use This Book (and a Glossary Primer) | [chapter-00-how-to-use-this-book.md](chapter-00-how-to-use-this-book.md) |
| 1 | Your Computer, Java, and This Project — Setup & First Run | [chapter-01-setup-and-first-run.md](chapter-01-setup-and-first-run.md) |
| 2 | The Skeleton: Entry Point & the App's Data Home | [chapter-02-the-skeleton.md](chapter-02-the-skeleton.md) |
| 3 | The Database Foundation | *(pending)* |
| 4 | Speaking SQLite: The DAO Pattern, Part 1 — Master Data | *(pending)* |
| 5 | The DAO Pattern, Part 2 — Documents & Ledgers | *(pending)* |
| 6 | The Language of the Business: Domain Models, Part 1 | *(pending)* |
| 7 | Domain Models, Part 2: Templates & Design Objects | *(pending)* |
| 8 | The Data Engine: DataManager & Caching | *(pending)* |
| 9 | The Shell: Window, Theme, Sidebar, Navigation | *(pending)* |
| 10 | Signing In: Authentication | *(pending)* |
| 11 | Listing & Editing Data: The Master-Data Views | *(pending)* |
| 12 | Selling: Billing End-to-End | *(pending)* |
| 13 | Buying & Money: Purchases, Transactions, Expenses | *(pending)* |
| 14 | Reports & Dashboards | *(pending)* |
| 15 | The Template Designer | *(pending)* |
| 16 | Rendering & PDF Export | *(pending)* |
| 17 | Label Printing & the TSPL Thermal Pipeline | *(pending)* |
| 18 | The MCP Server: The App as an AI Tool Server | *(pending)* |
| 19 | The AI Assistant | *(pending)* |
| 20 | The Knowledge Hub & In-App Documentation | *(pending)* |
| 21 | Testing the Whole App | *(pending)* |
| 22 | Build, Package, Release | *(pending)* |
| A1 | Full-Project Architecture Recap & Data-Flow Map | *(pending)* |
| A2 | End-to-End Runtime Walkthrough | *(pending)* |
| A3 | Consolidated Performance Roadmap | *(pending)* |
| A4 | Troubleshooting Guide · Glossary · Index | *(pending)* |
| A5 | Final Coverage Audit | [appendix-file-inventory.md](appendix-file-inventory.md) *(living inventory; audited at the end)* |

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
