import { writable } from "svelte/store";

export type Role = "ANBIETER" | "USER";

export interface AuthUser {
    id: number;
    email: string;
    name: string;
    role: Role;
    photoUrl: string | null;
    websiteUrl: string | null;
}

export const currentUser = writable<AuthUser | null>(null);
export const authChecked = writable<boolean>(false);

const API_BASE = "http://localhost:8080";

async function extractError(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json();
        return body?.error ?? fallback;
    } catch {
        return fallback;
    }
}

export async function fetchCurrentUser(): Promise<void> {
    try {
        const res = await fetch(`${API_BASE}/auth/me`, { credentials: "include" });
        currentUser.set(res.ok ? await res.json() : null);
    } catch {
        currentUser.set(null);
    } finally {
        authChecked.set(true);
    }
}

export async function login(email: string, password: string): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
        return extractError(res, "Login fehlgeschlagen.");
    }
    currentUser.set(await res.json());
    return null;
}

export async function register(
    email: string,
    password: string,
    name: string,
    role: Role
): Promise<string | null> {
    const res = await fetch(`${API_BASE}/auth/register`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, name, role }),
    });
    if (!res.ok) {
        return extractError(res, "Registrierung fehlgeschlagen.");
    }
    currentUser.set(await res.json());
    return null;
}

export async function logout(): Promise<void> {
    await fetch(`${API_BASE}/auth/logout`, {
        method: "POST",
        credentials: "include",
    });
    currentUser.set(null);
}

/**
 * Wraps fetch for session-bearing calls that expect the user to still be
 * logged in. If the server responds 401 (e.g. the session expired mid-use),
 * clears the auth store so the UI falls back to the logged-out view on the
 * next reactive update. The response is still returned to the caller so it
 * can show its own error message.
 */
export async function fetchWithSessionCheck(
    input: RequestInfo | URL,
    init?: RequestInit
): Promise<Response> {
    const res = await fetch(input, init);
    if (res.status === 401) {
        currentUser.set(null);
    }
    return res;
}
