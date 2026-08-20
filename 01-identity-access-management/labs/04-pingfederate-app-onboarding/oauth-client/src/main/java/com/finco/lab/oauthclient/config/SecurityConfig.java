package com.finco.lab.oauthclient.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The security rules: what is public, what needs an OIDC login, and how logout propagates
 * back to PingFederate.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final PingFedProperties properties;

    public SecurityConfig(PingFedProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository registrations) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico").permitAll()
                .requestMatchers("/api/config", "/api/health", "/api/public/**").permitAll()
                // The machine-to-machine demo deliberately needs NO user session — that is the
                // whole point of the Client Credentials grant.
                .requestMatchers("/api/client-credentials").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated())

            // Local login. The dashboard at "/" doubles as the login page, so an unauthenticated
            // visitor lands somewhere useful instead of on a bare form.
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/?error=bad-credentials")
                .permitAll())

            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true))

            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler(registrations))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))

            .exceptionHandling(handling -> handling
                // Browsers get redirected to PingFederate; our own XHRs get a 401 to render.
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")))

            .csrf(csrf -> csrf
                // The Client Credentials demo touches no session and no user state, so a CSRF
                // token would protect nothing — and leaving it off means you can call it with a
                // one-line curl, which is how machine-to-machine clients really behave.
                .ignoringRequestMatchers("/api/client-credentials")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * RP-initiated logout: when PingFederate publishes an {@code end_session_endpoint}, logging
     * out here also ends the session <i>there</i>. Without it you "log out", click login, and are
     * silently logged straight back in — the single most confusing SSO behaviour for newcomers.
     */
    private LogoutSuccessHandler logoutSuccessHandler(ClientRegistrationRepository registrations) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        String base = PingFedProperties.hasText(properties.getPublicBaseUrl())
                ? properties.getPublicBaseUrl()
                : "{baseUrl}";
        handler.setPostLogoutRedirectUri(base);
        return handler;
    }

    /** Forces the deferred CSRF token into existence so the frontend can read its cookie. */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
