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
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
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
        securityContextRepository: SecurityContextRepository
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/", "/markers", "/categories", "/activities/*/ratings", "/providers/*/ratings", "/activities/*/signups").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
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
