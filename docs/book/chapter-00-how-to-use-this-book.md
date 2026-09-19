# Chapter 0 — How to Use This Book (and a Glossary Primer)

> **Part 1 of InvoiceStudio: Zero to Finished Product**
> Files covered in this chapter: none — this is your map and your toolkit.
> Estimated reading time: 15 minutes. Worth every one of them.

---

## 1. Chapter goal

By the end of this short chapter you will know:

- what this book builds (the finished product),
- how to read it (the chapter anatomy, the markers, the promises it makes),
- the ten words of vocabulary you need before Chapter 1 begins.

No code yet. No installation yet. Just orientation — like the safety card in an
airplane seat pocket, but friendlier.

---

## 2. Story intro: the app you are about to build

Meet **Kumar**. He runs a wholesale trouser business in Mumbai — denim, cotton,
formal, lycra — three hundred buyers, a thermal label printer, GST returns every
month, and a notebook that is running out of pages.

Kumar tried spreadsheets. Spreadsheets do not stop him selling the same stock
twice. He tried cloud billing tools. They want a monthly fee and a permanent
internet connection, and his data lives on somebody else's computer.

**InvoiceStudio** is the answer this book builds: a **desktop application** — a
program that installs on Kumar's own Windows PC — that manages his entire
billing life:

- **Buyers, suppliers, items, stock** — the master data of his business.
- **Invoices** with GST tax columns, partial payments, PDF export with a
  scannable UPI **QR code** so customers can pay by phone.
- **Purchases, expenses, transactions** — where the money went.
- **Reports and dashboards** — who owes him what, this month vs last month.
- An invoice **Template Designer** — drag-and-drop layout editing, because a
  1990s-fixed invoice format was a business requirement from day one.
- **Thermal label printing** — real `TSPL` commands to a real label printer.
- An embedded **MCP server** (Chapter 18) that exposes the whole business as
  tools to AI assistants, and a built-in **AI chat assistant** (Chapter 19)
  that can answer "top 5 buyers by outstanding" by querying the live database.

It is a real product: ~57,500 lines of Java, 305 files, an installer, a CI
pipeline, 372 automated tests, and a simulated three-month stress test
(`MERCHANT_SIM_REPORT.md`) where a fake business was run through the real
app to prove it survives contact with reality.

You are going to rebuild it, file by file, understanding every line.

---

## 3. Concepts first: how to read this book

### The chapter anatomy

Every chapter (except this one) follows the same eleven-section skeleton:

1. **Chapter goal** — what exists and runs by the end of the chapter.
2. **Story intro** — the problem this part solves, with a real-world analogy.
3. **Concepts first** — every technical term explained before it is used.
4. **Files in this chapter** — a table of exactly which files you create.
5. **Step-by-step build** — full code, block-by-block explanation.
6. **How it works at runtime** — what the computer actually does, with a diagram.
7. **How to change it** — safe modification recipes (move a button, change a color).
8. **Performance & UX analysis** — the cost of each decision, better alternatives,
   and clearly labelled `OPTIONAL IMPROVEMENT` blocks.
9. **Common mistakes and fixes** — the errors beginners actually hit.
10. **Checkpoint** — how to prove it works, plus exercises.
11. **Summary and coverage self-check** — the chapter's promise, audited.

### The markers

| Marker | Meaning |
|---|---|
| `Tip` | A shortcut or habit that saves you time. |
| `Warning` | A place where a small mistake causes a confusing error later. |
| `Note` | Background information you can skip on first read. |
| `GAP:` | The source is ambiguous or incomplete here — the book says what is uncertain rather than inventing an answer. |
| `ISSUE:` | The source contains a real quirk/bug worth knowing about; the faithful build keeps it, the explanation tells you why and how you'd fix it. |
| `OPTIONAL IMPROVEMENT` | Better code than the original, kept out of the main build so the book stays faithful to the repo. |

### The three promises

1. **Nothing is skipped.** Every file in the repository is shown in full and
   explained in the chapter that owns it (see `appendix-file-inventory.md` —
   the ledger the final audit checks against).
2. **Faithful means faithful.** The code you type matches the repository —
   including its warts. When a wart is worth knowing about, you get an
   `ISSUE:` marker, not silence.
3. **You can always run it.** Every chapter ends with a checkpoint that proves
   the app still works with only the chapters so far completed.

### How to physically work through a chapter

- Type the code yourself. Muscle memory is a real teacher. Copy-paste teaches
  nothing about the parts your fingers skipped.
- After each numbered step, run the checkpoint command if one is given.
  Small verifications catch small mistakes while they are still small.
- When something breaks: Chapter 1's troubleshooting table, then the
  "Common mistakes" section of the current chapter, then Appendix A4.

---

## 4. The ten words you need first

These appear constantly. Each gets a fuller explanation at first *use* too —
this is just so Chapter 1 does not feel like a foreign film without subtitles.

1. **Java** — a programming language. You write human-readable text (`.java`
   files); a program called the **compiler** (`javac`) translates it into
   **bytecode** (`.class` files) that any computer with a **Java Virtual
   Machine (JVM)** can execute. "Write once, run anywhere" is the whole point.
2. **JDK** — *Java Development Kit*. The toolbox: compiler + JVM + tools.
   Version 21 is what this project uses. A **JRE** (runtime only) can run but
   not build programs — we need the full JDK.
3. **JavaFX** — a UI toolkit for Java: buttons, tables, windows, styling.
   The app's entire interface is JavaFX.
4. **Maven** — a **build tool**. It reads `pom.xml` (a recipe file), downloads
   the libraries the project needs (**dependencies**), compiles the code, runs
   tests, and packages the result into a **JAR** (a `.jar` file — a zip of
   compiled code with a table of contents).
5. **SQLite** — a complete SQL database stored in **one file** on disk
   (`invoicestudio.db`). No server process, no installation — ideal for
   desktop apps.
6. **DAO** — *Data Access Object*. A Java class whose only job is reading and
   writing one kind of database row (Buyers, Bills…). The app's database
   vocabulary, in other words.
7. **UI thread** — GUIs allow exactly one thread (a line of execution) to
   touch on-screen widgets. In JavaFX it is called the **JavaFX Application
   Thread**. Doing slow work there freezes the window — a crime this book's
   chapters repeatedly work to avoid.
8. **JSON** — a text format for structured data (`{"name": "Acme"}`), used
   here for settings files and for talking to AI providers.
9. **PDF** — *Portable Document Format*. The app generates invoices as PDFs
   with the **PDFBox** library so they print identically everywhere.
10. **Git / GitHub** — **Git** records the history of a project folder; **GitHub**
   hosts that history online and runs **CI** (continuous integration — robots
   that build and test the app on every change).

---

## 5. What you will need (so you can gather it now)

| Tool | Version | Why | Chapter |
|---|---|---|---|
| A computer | Windows 10/11, macOS 12+, or a Linux desktop | JavaFX needs a display | 1 |
| JDK | **21** (Temurin recommended) | compiles & runs everything | 1 |
| Maven | 3.8+ | builds & tests | 1 |
| Git | any modern | gets the source | 1 |
| An IDE | IntelliJ IDEA Community / VS Code | comfortable editing (optional but recommended) | 1 |
| Disk space | ~2 GB free | JDK + Maven + repo + build outputs | 1 |

No database server, no internet at *runtime* (only Maven downloads and the
optional AI features need it), no paid tools.

---

## 6. Checkpoint

There is nothing to run yet. Your checkpoint: you can say out loud —

- what the finished app does (Kumar's business in one paragraph),
- what `GAP:` and `ISSUE:` markers mean,
- the difference between a JDK and a JRE,
- why the book will keep mentioning a "UI thread".

**Exercises**

1. Skim the repository's `README.md` for ten minutes. Do not worry about
   understanding it — just notice how much of it this book will re-explain.
2. Open `appendix-file-inventory.md`. Find the row for `Launcher.java` and
   note which chapter owns it (Chapter 2). This is how you can always find
   where a file is explained.
3. Write down one feature *you* would want in a billing app. Keep it; near the
   end of the book, Chapter 22's modification guides will show you where it
   would live.

---

## 7. Summary and coverage self-check

You now know the product, the reading system, the markers, and the ten seed
words of vocabulary. Nothing from the codebase was covered — by design.

**Files covered this chapter:** none (orientation only).
**Files remaining:** 305 — Chapter 1 starts the real journey with `pom.xml`,
`README.md`, `.gitignore`, `.vscode/settings.json` and `fx.env`, and ends with
the app running on your screen for the first time.

*Next: Part 1 continues with Chapter 1 — "Your Computer, Java, and This
Project — Setup & First Run."*
