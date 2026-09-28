# Blade Runner Solo Mode — Table Roller (Android App)

**Date:** 2026-09-28
**Status:** Approved for planning
**Source material:** `Blade Runner - Solo Mode.pdf` (26 pages, single-player case file rules for the Blade Runner RPG)

## Purpose

A personal-use Android app that lets the user roll on any of the rollable
tables from the Blade Runner RPG "Solo Mode" booklet without needing to flip
through the PDF or use physical dice. Single source of truth for table data
is a hand-transcribed JSON file bundled with the app; no PDF parsing happens
on-device, no network access, no accounts.

## Scope

- Personal use only. Sideloaded APK (installed via `adb install` or copied
  to the phone), not published to Google Play. No app-store polish
  requirements (icon/branding can be minimal).
- Covers every rollable table in `Blade Runner - Solo Mode.pdf`. Narrative
  rules text, examples, and the Case Log page (a blank form, not a table)
  are out of scope — only structured random-roll tables are included.
- No table content from other Blade Runner RPG books (Core Rulebook, other
  Case Files) is in scope, since only this PDF exists in the project
  directory today. If the user adds another PDF later, that's a new pass of
  data extraction, not a re-architecture.

### Table catalog (22 tables, 9 sections)

| # | Section | Table | Dice | Columns |
|---|---|---|---|---|
| 1 | Character Creation | Blade Runner Origin | 1d12 | Result |
| 2 | Investigation Basics | Scene Categories | 1d12 | Result, Detail, Skill Roll Examples |
| 3 | Investigation Basics | Scene Check | 1d8 | Result, Detail, Examples |
| 4 | Investigation Basics | Question Check | 1d10 | Result |
| 5 | Investigation Basics | Critical Success | 1d8 | Result, Bonus Effect |
| 6 | Investigation Basics | NPC Skill Level | 1d8 | NPC Proficiency, Skill Roll |
| 7 | Combat & Chases | NPC Tactics | 1d8 | Behavior |
| 8 | Combat & Chases | NPC Chase Maneuvers | 1d8 | Pursuer Maneuver, Prey Maneuver |
| 9 | Case File Tools | Cipher Table | 1d6 + 1d12 | Method, Focus |
| 10 | Case Briefing | Table 1: Assignment | 1d6 + 1d10 | Result |
| 11 | Case Briefing | Table 2: Relevance | 1d12 | Result |
| 12 | Case Briefing | Table 3: Initial Complication | 1d12 | Result |
| 13 | Case Briefing | Table 4: Personal Hook | 1d12 | Result |
| 14 | Clue Tables | Table 1: Meaning | 1d8 | Result |
| 15 | Clue Tables | Table 2: Evidence Descriptor | 1d6 + 1d10 | Result, Detail |
| 16 | Clue Tables | Table 3: Evidence Type | 1d6 + 1d12 | Result |
| 17 | Character Tables | Table 1: Sphere | 1d6 + 1d8 | Result |
| 18 | Character Tables | Table 2: Trait | 1d6 + 1d12 | Result |
| 19 | Character Tables | Human or Replicant | 1d10 | Result |
| 20 | Locations | Location Table | 1d6 + 1d12 | Environment, Place |
| 21 | Event Tables | Downtime Event Table | 1d12 | Home, Street |
| 22 | Event Tables | Countdown Event Table | 1d12 | Result, Examples |

This catalog is the definitive scope for data extraction. If, during
transcription, a table turns out to be miscounted or merged/split
differently, the JSON is the source of truth and this table should be
updated to match — the app doesn't need to hit exactly "22."

## Data model

Single bundled asset: `app/src/main/assets/tables.json`, an array of table
objects sharing one schema for every table shape found above (single-die,
two-die-combined, multi-column):

```json
{
  "sections": [
    {
      "name": "Character Creation",
      "tables": [
        {
          "id": "blade_runner_origin",
          "title": "Blade Runner Origin",
          "dice": ["d12"],
          "columns": ["Result"],
          "rows": [
            { "ranges": [[1,1]], "values": ["In the wake of a traumatic incident, your dreams invade your waking life. You're not sure what's real anymore."] },
            { "ranges": [[2,2]], "values": ["..."] }
          ]
        }
      ]
    }
  ]
}
```

- `dice`: ordered list of die types to roll, e.g. `["d6","d12"]` for the
  Cipher table (roll both, look up the matching row).
- `rows[].ranges`: parallel to `dice` — one `[min,max]` pair per die,
  indicating which rolled values select this row. For single-die tables
  this is a single range against the one die rolled.
- `columns` / `values`: supports 1-N result columns per row (e.g. Method +
  Focus, or Pursuer Maneuver + Prey Maneuver, or Home + Street).

Transcription approach: since `pdftotext` loses some die-face glyphs used
as row numbers in a couple of tables (e.g. Question Check's 1 and 10 rows
render as blank), those specific tables will be transcribed by visually
inspecting the rendered PDF page rather than relying on extracted text, to
avoid silently dropping rows.

## App architecture

- **Kotlin + Jetpack Compose**, single-activity app.
- `minSdk 26`, `targetSdk` latest stable at build time.
- No network permissions, no persistence beyond the running process —
  table data loads from the bundled asset at startup; roll history lives
  in memory for the session and is lost on app restart (acceptable for a
  personal tool; can be revisited later if wanted).
- Structure:
  ```
  app/src/main/java/.../
    data/   RollTable.kt (data classes), TableRepository.kt (loads + parses tables.json)
    model/  RollEntry.kt (one logged roll: table title, dice rolled, result text, timestamp)
    logic/  Roller.kt (random die rolls, range lookup against a table's rows)
    ui/     SectionListScreen.kt, TableRollScreen.kt, theme/ (colors, type)
  app/src/main/assets/tables.json
  ```
- `RollHistoryViewModel` holds the session's `List<RollEntry>`, newest
  first, with a clear action.

## UI / UX

- **Home screen**: sections rendered as expandable cards (matching the
  catalog's 8 sections above), each listing its tables. Tapping a table
  navigates to its roll screen.
- **Table roll screen**:
  - Table title and a prominent **Roll** button.
  - Rolling shows the raw die value(s) (e.g. "D6: 4 · D12: 9") and the
    matched row's full text, with all of that table's columns displayed
    together (e.g. both Method and Focus for the Cipher table).
  - Each roll prepends an entry to a scrollable session history list below
    (table name, dice rolled, result, short timestamp).
  - Toolbar action to clear history.
- **Theme**: dark noir palette — near-black background, single neon accent
  color (amber or cyan) — fitting the source material, low-cost in Compose.
- **No search** in v1; 22 tables across 8 sections is small enough to
  browse. Can be added later without restructuring if it proves annoying.

## Build & tooling plan

The dev machine currently has no JDK, Android SDK, or Gradle installed
(only `adb`/platform-tools via `android-tools`). Setup path:

1. Install OpenJDK (17) via pacman.
2. Download the Android SDK command-line tools directly from Google (not
   the full Android Studio IDE) and use `sdkmanager` to install
   `platform-tools`, one `platforms;android-XX`, and one `build-tools`
   version.
3. Hand-write the Gradle project (`settings.gradle.kts`, module
   `build.gradle.kts`, Gradle wrapper) rather than generating it from
   Android Studio.
4. Build a debug APK via `./gradlew assembleDebug`; install with
   `adb install` over USB, or copy the APK to the phone for manual
   install.

## Testing plan

- Unit tests (JVM, no emulator needed) for `Roller.kt`: given a table
  definition and fixed die rolls, verify the correct row/columns are
  selected, including boundary values (e.g. roll of exactly 1 and exactly
  the max on multi-row-range tables).
- A lightweight test/lint pass over `tables.json` at build or test time:
  every table's row ranges fully and non-overlappingly cover the declared
  dice's possible values (e.g. a d12 table's ranges union to exactly
  1–12), catching transcription mistakes early.
- Manual smoke test on-device/emulator: launch app, expand each section,
  roll each table at least once, confirm history log updates and clears.

## Out of scope (explicitly deferred, not forgotten)

- Persisting roll history across app restarts.
- In-app search/filter across tables.
- Supporting additional PDFs/table sources beyond this one booklet.
- Any Play Store packaging (signing for release, store listing, icons).
