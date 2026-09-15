import { test } from "node:test";
import assert from "node:assert/strict";
import { currentHourRange } from "../src/lib/timeRange.ts";

/**
 * Regressionstest für den „Jetzt"-Filter (FilterBar.svelte):
 * currentHourRange muss die aktuelle Stunde als [from, to)-Intervall liefern,
 * damit das Backend (hour >= timeFrom && hour < timeTo) den aktuellen
 * Zeitpunkt korrekt filtert. Die Minute wird bewusst ignoriert.
 */
test("currentHourRange liefert [currentHour, currentHour+1) für repräsentative Stunden", () => {
    const cases = [
        { hour: 0, expected: { from: 0, to: 1 } }, // Mitternacht
        { hour: 1, expected: { from: 1, to: 2 } },
        { hour: 8, expected: { from: 8, to: 9 } },
        { hour: 12, expected: { from: 12, to: 13 } },
        { hour: 14, expected: { from: 14, to: 15 } },
        { hour: 18, expected: { from: 18, to: 19 } },
        { hour: 23, expected: { from: 23, to: 24 } }, // 23:xx
    ];
    for (const { hour, expected } of cases) {
        const fixed = new Date(2026, 8, 10, hour, 30, 0); // 10.09.2026 HH:30
        assert.deepEqual(
            currentHourRange(fixed),
            expected,
            `hour=${hour}: erwartet ${JSON.stringify(expected)}`,
        );
    }
});

test("currentHourRange ignoriert die Minute (robust gegen Minutenwechsel)", () => {
    const base = new Date(2026, 8, 10, 14, 0, 0);
    const later = new Date(2026, 8, 10, 14, 59, 59);
    assert.deepEqual(currentHourRange(base), currentHourRange(later));
});
