package io.trustlens.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        return http
            .csrf(c -> c.csrfTokenRepository(csrf)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/api/v1/auth/csrf", "/api/v1/auth/login",
                                 "/api/v1/auth/signup", "/api/v1/auth/mfa/setup", "/api/v1/auth/mfa/verify",
                                 "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                 .requestMatchers("/api/v1/admin/roles/**").hasAuthority("PERM_ROLE_MANAGE")
                .requestMatchers("/api/v1/admin/**").hasAuthority("PERM_USER_MANAGE")
                .requestMatchers("/api/v1/policies/**").hasAuthority("PERM_POLICY_MANAGE")
                .requestMatchers("/api/v1/audit-events").hasAuthority("PERM_AUDIT_READ")
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new SessionAccountRefreshFilter(users), AnonymousAuthenticationFilter.class)
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .build();
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (origins.isEmpty()) throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain at least one explicit origin");
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN", "Accept"));
        config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
