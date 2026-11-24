package io.trustlens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refreshes the session principal from PostgreSQL on every authenticated request.
 * This makes account disablement and role changes effective without waiting for
 * the browser session to expire.
 */
public class SessionAccountRefreshFilter extends OncePerRequestFilter {
    private final UserDetailsService users;

    public SessionAccountRefreshFilter(UserDetailsService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
            try {
                UserDetails refreshed = users.loadUserByUsername(current.getName());
                if (!refreshed.isEnabled()) {
                    reject(request, response);
                    return;
                }
                UsernamePasswordAuthenticationToken replacement =
                    new UsernamePasswordAuthenticationToken(refreshed, null, refreshed.getAuthorities());
                replacement.setDetails(current.getDetails());
                SecurityContextHolder.getContext().setAuthentication(replacement);
            } catch (UsernameNotFoundException ex) {
                reject(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is disabled or no longer exists");
    }
}
