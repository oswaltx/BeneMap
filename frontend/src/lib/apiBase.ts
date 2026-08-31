// In dev, frontend (5173) and backend (8080) run as separate servers.
// In production, Spring Boot serves both from the same origin, so requests
// stay relative.
export const API_BASE = import.meta.env.DEV ? "http://localhost:8080" : "";
