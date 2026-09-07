import { writable, get } from "svelte/store";
import { API_BASE } from "./apiBase";
import { fetchWithSessionCheck, currentUser } from "../auth";

export const favoriteIds = writable<Set<number>>(new Set());

export async function loadFavorites(): Promise<void> {
    try {
        const res = await fetchWithSessionCheck(`${API_BASE}/favorites/ids`, { credentials: "include" });
        favoriteIds.set(res.ok ? new Set(await res.json()) : new Set());
    } catch {
        favoriteIds.set(new Set());
    }
}

export async function toggleFavorite(activityId: number): Promise<void> {
    const isFavorite = get(favoriteIds).has(activityId);
    try {
        const res = await fetchWithSessionCheck(`${API_BASE}/activities/${activityId}/favorite`, {
            method: isFavorite ? "DELETE" : "POST",
            credentials: "include",
        });
        if (!res.ok) return;
        favoriteIds.update((current) => {
            const next = new Set(current);
            if (isFavorite) next.delete(activityId);
            else next.add(activityId);
            return next;
        });
    } catch {
        // Netzwerkfehler: State bleibt unverändert, Nutzer kann den Klick einfach wiederholen.
    }
}

// Lädt die Favoriten neu, sobald sich ein Nutzer einloggt (oder die Session beim
// Start bereits eingeloggt ist), und leert sie beim Logout — analog zu currentUser.
let lastUserId: number | null = null;
currentUser.subscribe((user) => {
    if (user && user.id !== lastUserId) {
        lastUserId = user.id;
        loadFavorites();
    } else if (!user) {
        lastUserId = null;
        favoriteIds.set(new Set());
    }
});
