/**
 * Liefert die aktuelle Stunde als [from, to)-Intervall (Backend erwartet
 * hour >= timeFrom && hour < timeTo). Die Minute wird bewusst nicht
 * berücksichtigt, damit "Jetzt" robust gegenüber Minutenwechsel bleibt.
 *
 * Ausgelagert aus FilterBar.svelte, damit die Filterlogik ohne Svelte-Compiler
 * mit einem simplen Node-Test (node --test) regressionsgeprüft werden kann.
 */
export function currentHourRange(now: Date = new Date()): { from: number; to: number } {
    const h = now.getHours();
    return { from: h, to: h + 1 };
}
