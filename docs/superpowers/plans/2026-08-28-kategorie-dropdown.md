# Kategorie-Dropdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the free-text category field on activity creation/editing with a fixed dropdown, so app-native activities use the same consistent category names as scraped city offers.

**Architecture:** A single canonical list of 16 category names (the 15 existing scraper categories plus "Sonstiges") is derived once in the backend from the scraper's existing category map, and mirrored as an identical constant in the frontend. Both activity forms (`AddActivity.svelte`, `EditActivityModal.svelte`) render a `<select>` populated from the frontend constant instead of a text input. No backend schema or validation changes — `category` stays an optional `String?`.

**Tech Stack:** Kotlin/Spring Boot backend, Svelte 5 + TypeScript frontend.

## Global Constraints

- The category list is exactly these 16 values, in this order: Bildung, Familie & Nachbarschaft, Flüchtlingshilfe, Hausaufgabenbetreuung, Kultur, Leben im Alter, LGBTQ, Obdachlosigkeit, Patenschaften, Soziales, Sport und Bewegung, Tierhilfe, Übersetzen / Dolmetschen, Umwelt, Natur und Tierschutz, Vereinsarbeit, Verkauf, Sonstiges.
- "Sonstiges" is only ever chosen by a provider through the dropdown — the scraper never assigns it.
- The category field stays optional. No provider-facing requirement to pick a value, and no backend validation restricting `category` to the fixed list.
- No shared API endpoint for the category list — the frontend constant is a manually maintained mirror of the backend constant, not fetched over HTTP.
- No database migration or backfill for existing rows — out of scope of this plan (handled separately as manual cleanup of test data, not part of implementation).

---

### Task 1: Backend — shared category constant

**Files:**
- Modify: `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt:9`
- Create: `backend/src/main/kotlin/com/example/VoloMap/server/ActivityCategories.kt`
- Test: `backend/src/test/kotlin/com/example/VoloMap/server/ActivityCategoriesTest.kt`

**Interfaces:**
- Produces: `val ACTIVITY_CATEGORIES: List<String>` in package `com.example.VoloMap.server`, containing the 16 values from Global Constraints in that exact order. Later tasks do not depend on this (the frontend list is a separately maintained mirror per Global Constraints), but it is the backend's source of truth and must match the frontend list exactly.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/example/VoloMap/server/ActivityCategoriesTest.kt`:

```kotlin
package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActivityCategoriesTest {

    @Test
    fun `activity categories match the fifteen scraper categories plus Sonstiges in order`() {
        val expected = listOf(
            "Bildung",
            "Familie & Nachbarschaft",
            "Flüchtlingshilfe",
            "Hausaufgabenbetreuung",
            "Kultur",
            "Leben im Alter",
            "LGBTQ",
            "Obdachlosigkeit",
            "Patenschaften",
            "Soziales",
            "Sport und Bewegung",
            "Tierhilfe",
            "Übersetzen / Dolmetschen",
            "Umwelt, Natur und Tierschutz",
            "Vereinsarbeit",
            "Verkauf",
            "Sonstiges",
        )
        assertEquals(expected, ACTIVITY_CATEGORIES)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `.\gradlew.bat test --tests "com.example.VoloMap.server.ActivityCategoriesTest"`
Expected: compile failure — `unresolved reference: ACTIVITY_CATEGORIES` (the constant doesn't exist yet).

- [ ] **Step 3: Make `ENGAGEMENT_CATEGORIES` visible outside `Scraper.kt`**

In `backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt`, line 9 currently reads:

```kotlin
private val ENGAGEMENT_CATEGORIES = mapOf(
```

Change it to drop the `private` modifier (this is the only change to this file — the map's contents stay exactly as they are):

```kotlin
val ENGAGEMENT_CATEGORIES = mapOf(
```

- [ ] **Step 4: Create the shared category list**

Create `backend/src/main/kotlin/com/example/VoloMap/server/ActivityCategories.kt`:

```kotlin
package com.example.VoloMap.server

val ACTIVITY_CATEGORIES: List<String> = ENGAGEMENT_CATEGORIES.values.toList() + "Sonstiges"
```

- [ ] **Step 5: Run test to verify it passes**

Run (from `backend/`): `.\gradlew.bat test --tests "com.example.VoloMap.server.ActivityCategoriesTest"`
Expected: PASS.

- [ ] **Step 6: Run the full backend test suite**

Run (from `backend/`): `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL, no regressions (the only production change is removing `private` from one declaration and adding one new file — nothing that touches scraper behavior).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/VoloMap/server/Scraper.kt backend/src/main/kotlin/com/example/VoloMap/server/ActivityCategories.kt backend/src/test/kotlin/com/example/VoloMap/server/ActivityCategoriesTest.kt
git commit -m "feat: add shared activity category list"
```

---

### Task 2: Frontend — category dropdown in both activity forms

**Files:**
- Create: `frontend/src/lib/categories.ts`
- Modify: `frontend/src/lib/AddActivity.svelte:1-3` (imports), `frontend/src/lib/AddActivity.svelte:144-147` (Kategorie field)
- Modify: `frontend/src/lib/EditActivityModal.svelte:1-3` (imports), `frontend/src/lib/EditActivityModal.svelte:106-109` (Kategorie field)

**Interfaces:**
- Consumes: nothing from Task 1 (this list is a manually maintained mirror, not an import from the backend — see Global Constraints).
- Produces: `export const ACTIVITY_CATEGORIES: string[]` from `frontend/src/lib/categories.ts`, imported by `AddActivity.svelte` and `EditActivityModal.svelte`.

This project has no automated frontend test suite — `npm run check` (svelte-check + tsc) is the only automated gate for frontend changes, per existing project convention. Steps below use that instead of a test-first cycle, since there is no test framework for Svelte markup here.

- [ ] **Step 1: Create the frontend category constant**

Create `frontend/src/lib/categories.ts`:

```typescript
export const ACTIVITY_CATEGORIES = [
    "Bildung",
    "Familie & Nachbarschaft",
    "Flüchtlingshilfe",
    "Hausaufgabenbetreuung",
    "Kultur",
    "Leben im Alter",
    "LGBTQ",
    "Obdachlosigkeit",
    "Patenschaften",
    "Soziales",
    "Sport und Bewegung",
    "Tierhilfe",
    "Übersetzen / Dolmetschen",
    "Umwelt, Natur und Tierschutz",
    "Vereinsarbeit",
    "Verkauf",
    "Sonstiges",
];
```

This must contain exactly the same 16 strings in the same order as `ACTIVITY_CATEGORIES` in `backend/src/main/kotlin/com/example/VoloMap/server/ActivityCategories.kt` from Task 1.

- [ ] **Step 2: Replace the Kategorie field in AddActivity.svelte**

In `frontend/src/lib/AddActivity.svelte`, the script block currently starts with:

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, authChecked, fetchWithSessionCheck } from "../auth";
```

Add the new import below the existing ones:

```svelte
<script lang="ts">
    import Link from "./Link.svelte";
    import { currentUser, authChecked, fetchWithSessionCheck } from "../auth";
    import { ACTIVITY_CATEGORIES } from "./categories";
```

Further down, the Kategorie field currently reads:

```svelte
            <label>
                Kategorie
                <input type="text" bind:value={category} />
            </label>
```

Replace it with:

```svelte
            <label>
                Kategorie
                <select bind:value={category}>
                    <option value="">– bitte wählen –</option>
                    {#each ACTIVITY_CATEGORIES as cat}
                        <option value={cat}>{cat}</option>
                    {/each}
                </select>
            </label>
```

The `category` variable itself (`let category = "";`) and its use in `handleSubmit`/form-reset stay unchanged — only the input element changes from a text field to a select.

- [ ] **Step 3: Replace the Kategorie field in EditActivityModal.svelte**

In `frontend/src/lib/EditActivityModal.svelte`, the script block currently starts with:

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { fetchWithSessionCheck } from "../auth";
```

Add the new import below the existing ones:

```svelte
<script lang="ts">
    import { createEventDispatcher } from "svelte";
    import { fetchWithSessionCheck } from "../auth";
    import { ACTIVITY_CATEGORIES } from "./categories";
```

Further down, the Kategorie field currently reads:

```svelte
        <label>
            Kategorie
            <input type="text" bind:value={category} />
        </label>
```

Replace it with:

```svelte
        <label>
            Kategorie
            <select bind:value={category}>
                <option value="">– bitte wählen –</option>
                {#each ACTIVITY_CATEGORIES as cat}
                    <option value={cat}>{cat}</option>
                {/each}
            </select>
        </label>
```

The `category` variable itself (`let category = marker.category;`) and its use in `handleSubmit` stay unchanged — only the input element changes from a text field to a select.

- [ ] **Step 4: Run the frontend type-check**

Run (from `frontend/`): `npm run check`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/categories.ts frontend/src/lib/AddActivity.svelte frontend/src/lib/EditActivityModal.svelte
git commit -m "feat: replace category text input with fixed dropdown"
```

---

## Post-Implementation (not part of either task, done by the controller after the branch is finished)

- Delete all non-Köln (app-native, i.e. `source_url IS NULL`) activities from the local H2 database — they are test/dummy data, including the activity with category "Ehre" that motivated this feature. This is direct DB cleanup, not a code change, and is handled separately (as with earlier test-data cleanup this session), not through the SDD task loop.
