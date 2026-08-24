package com.finco.lab.samlsp.config;

import com.finco.lab.samlsp.saml.SamlAuthenticationFailureHandler;
import com.finco.lab.samlsp.saml.SamlFailureLog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The security rules. Read this file top to bottom and you have the whole SP:
 * what is public, what needs a SAML session, and where the SAML endpoints live.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RelyingPartyRegistrationRepository registrations,
                                           SamlFailureLog failureLog,
                                           SamlProperties properties) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: the frontend shell, the config checklist, health, and every SAML endpoint.
                .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico").permitAll()
                .requestMatchers("/api/config", "/api/health", "/api/public/**").permitAll()
                // Reading *what* this app currently trusts is public — it is the same information
                // as the metadata endpoint, and a dashboard that cannot show it is useless.
                // Reading the last failure's *code* is public too; the assertion behind it is not.
                .requestMatchers(HttpMethod.GET, "/api/idp", "/api/saml/last-failure").permitAll()
                // The SP metadata has to be readable without a login — a PingFederate admin
                // fetches it from the console, and nothing in it is secret: it is our entity ID,
                // our ACS URL, and our *public* certificate.
                .requestMatchers("/api/sp-metadata.xml").permitAll()
                .requestMatchers("/saml2/**", "/login/**", "/logout/saml2/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                // Everything else needs a session — including /api/whoami, /api/assertion, and
                // every write to /api/idp/**. Changing which IdP this app trusts is changing who
                // is allowed to mint logins for it; an anonymous caller who could POST metadata
                // could point the SP at an IdP they control and walk in as anyone. The Step 0
                // username/password login is what gets you in when SSO itself is broken.
                .anyRequest().authenticated())

            // Publishes our SP metadata XML. Uploading this file is the fastest way to onboard:
            // PingFederate reads the entity ID, ACS URL and signing certificate straight out of it.
            .saml2Metadata(Customizer.withDefaults())

            // Local login. The dashboard at "/" doubles as the login page, so an unauthenticated
            // visitor lands somewhere useful instead of on a bare form.
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/?error=bad-credentials")
                .permitAll())

            .saml2Login(saml2 -> saml2
                // After the assertion is accepted, land back on the dashboard — and forget the
                // last failure, so a stale banner cannot outlive the problem it described.
                .successHandler(samlSuccessHandler(failureLog))
                // And when it is rejected, land somewhere that can tell you why. The default
                // sends the browser to /login?error — a page this app does not have — and drops
                // the evidence on the floor. This records the failure first.
                .failureHandler(new SamlAuthenticationFailureHandler(
                        failureLog, properties.getRegistrationId())))

            // Single Logout: kills our session AND tells PingFederate to kill its own.
            .saml2Logout(saml2 -> saml2.logoutRequest(request -> request.logoutUrl("/logout/saml2/slo")))

            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))

            .exceptionHandling(handling -> handling
                // A browser hitting a protected page should be bounced to PingFederate.
                // An XHR from our own frontend should get a clean 401 it can render instead.
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")))

            .csrf(csrf -> csrf
                // The IdP POSTs the assertion and logout messages to us cross-site by design —
                // those two endpoints are protected by XML signatures, not by a CSRF token.
                .ignoringRequestMatchers("/login/saml2/sso/**", "/logout/saml2/slo")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    private static AuthenticationSuccessHandler samlSuccessHandler(SamlFailureLog failureLog) {
        SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler("/");
        delegate.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            failureLog.clear();
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    /**
     * Spring Security defers creating the CSRF token until something asks for it. Our frontend
     * reads it from the {@code XSRF-TOKEN} cookie, so we force it into existence on every request.
     */
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
