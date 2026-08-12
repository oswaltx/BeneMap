import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/login": Login,
    "/register": Register,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
    (window as any)._paq?.push(['setCustomUrl', path]);
    (window as any)._paq?.push(['trackPageView']);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
    (window as any)._paq?.push(['setCustomUrl', window.location.pathname]);
    (window as any)._paq?.push(['trackPageView']);
});