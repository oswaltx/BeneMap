import { fetchWithSessionCheck } from "../auth";
import { API_BASE } from "./apiBase";

export async function deleteActivity(id: number): Promise<boolean> {
    if (!confirm("Aktivität wirklich löschen? Das entfernt auch alle Bewertungen dazu.")) {
        return false;
    }
    try {
        const res = await fetchWithSessionCheck(`${API_BASE}/activities/${id}`, {
            method: "DELETE",
            credentials: "include",
        });
        if (res.status === 404) {
            // Already gone (e.g. deleted from another tab) — same end state as a
            // successful delete, so the caller should refresh without an alert.
            return true;
        }
        if (!res.ok) {
            alert("Löschen fehlgeschlagen. Bitte versuche es erneut.");
            return false;
        }
        return true;
    } catch (e) {
        alert("Server nicht erreichbar. Bitte versuche es später erneut.");
        return false;
    }
}
