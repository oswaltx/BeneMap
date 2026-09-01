package com.example.VoloMap.server

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Direct navigation/refresh on a client-side route (e.g. /about) has no matching
 * @RestController — forward it to the SPA shell so Svelte's router can take over.
 */
@Configuration
class SpaWebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        listOf(
            "/about", "/add", "/profile", "/login", "/register",
            "/impressum", "/datenschutz", "/nutzungsbedingungen", "/forgot-password", "/reset-password", "/verify-email",
        ).forEach { registry.addViewController(it).setViewName("forward:/index.html") }
    }
}
