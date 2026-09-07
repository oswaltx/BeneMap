// In dev, frontend (5173) and backend (8080) run as separate servers.
// In production, Spring Boot serves both from the same origin, so requests
// stay relative.
export const API_BASE = import.meta.env.DEV ? "http://localhost:8080" : "";

// Uploaded photos are stored as paths relative to the backend (e.g.
// "/uploads/photos/xxx.jpg"), unlike hand-typed photo URLs which are always
// absolute (http/https). In dev the frontend and backend run on different
// origins, so a relative path needs the backend origin prefixed to actually
// load; in prod they share an origin and API_BASE is empty, so this is a no-op.
export function resolvePhotoUrl(url: string): string {
    return url.startsWith("/") ? `${API_BASE}${url}` : url;
}
