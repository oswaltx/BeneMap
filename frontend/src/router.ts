import { writable } from "svelte/store";
import type { Component } from "svelte";

import Home from "./pages/Home.svelte";
import About from "./pages/About.svelte";
import AddActivity from "./lib/AddActivity.svelte";
import Profile from "./lib/Profile.svelte";
import Login from "./pages/Login.svelte";
import Register from "./pages/Register.svelte";
import Impressum from "./pages/Impressum.svelte";
import Datenschutz from "./pages/Datenschutz.svelte";

export const route = writable<string>(window.location.pathname);

export const routes: Record<string, Component> = {
    "/": Home,
    "/about": About,
    "/add": AddActivity,
    "/profile": Profile,
    "/login": Login,
    "/register": Register,
    "/impressum": Impressum,
    "/datenschutz": Datenschutz,
};

export function navigate(path: string) {
    history.pushState({}, "", path);
    route.set(path);
}

window.addEventListener("popstate", () => {
    route.set(window.location.pathname);
});