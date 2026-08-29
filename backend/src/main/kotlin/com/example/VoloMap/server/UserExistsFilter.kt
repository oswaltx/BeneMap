package com.example.VoloMap.server

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.filter.OncePerRequestFilter

// Verhindert, dass eine zweite, noch aktive Session eines inzwischen
// gelöschten Kontos (z. B. auf einem anderen Gerät) weiterhin als
// authentifiziert behandelt wird. Muss nach SecurityContextHolderFilter
// in der Filterkette laufen, damit der SecurityContext bereits geladen ist
// — siehe die Verdrahtung über http.addFilterAfter(...) in SecurityConfig.
class UserExistsFilter(
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is UsernamePasswordAuthenticationToken && authentication.isAuthenticated) {
            if (!userRepository.existsByEmail(authentication.name)) {
                SecurityContextLogoutHandler().logout(request, response, authentication)
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}
