package com.example.VoloMap.server

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userRepository: UserRepository
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService { email ->
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("Unbekannte E-Mail: $email")
        org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash)
            .authorities(SimpleGrantedAuthority("ROLE_${user.role}"))
            .build()
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    // Verfolgt alle aktiven Sessions pro Nutzer, damit Passwort-Reset gezielt
    // andere Sessions desselben Kontos invalidieren kann. maximumSessions(-1)
    // in securityFilterChain bedeutet ausdrücklich "unbegrenzt" — es wird
    // keine Obergrenze für gleichzeitige Sessions eingeführt.
    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

    @Bean
    fun httpSessionEventPublisher(): HttpSessionEventPublisher = HttpSessionEventPublisher()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
        sessionRegistry: SessionRegistry
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .addFilterAfter(UserExistsFilter(userRepository), SecurityContextHolderFilter::class.java)
            .sessionManagement {
                // Der Default-Handler von Spring Security für abgelaufene Sessions schreibt
                // nur eine Textmeldung in den Response-Body, ohne den Status zu ändern (200).
                // Damit ein Passwort-Reset invalidierte Sessions konsistent mit dem Rest der
                // API als 401 ausweist, wird hier explizit ein 401-Status gesetzt.
                it.maximumSessions(-1)
                    .sessionRegistry(sessionRegistry)
                    .expiredSessionStrategy { event -> event.response.sendError(HttpStatus.UNAUTHORIZED.value()) }
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/forgot-password", "/auth/reset-password").permitAll()
                it.requestMatchers(HttpMethod.POST, "/add", "/add-recurring").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.PUT, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*").hasRole("ANBIETER")
                it.requestMatchers(HttpMethod.POST, "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signup").hasRole("USER")
                it.requestMatchers(HttpMethod.DELETE, "/activities/*/signup").hasRole("USER")
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
        return http.build()
    }
}
