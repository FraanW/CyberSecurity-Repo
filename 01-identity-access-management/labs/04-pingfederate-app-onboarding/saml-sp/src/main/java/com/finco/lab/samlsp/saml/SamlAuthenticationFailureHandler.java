package com.finco.lab.samlsp.saml;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Turns a rejected assertion into something you can act on.
 *
 * <p>Records the failure (see {@link SamlFailureLog}) and sends the browser back to the dashboard
 * with {@code ?samlError=<code>} rather than to Spring Security's default {@code /login?error},
 * which this app does not even have a page for — a rejected login currently lands the user on
 * nothing at all.</p>
 */
public class SamlAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final SamlFailureLog failureLog;
    private final String registrationId;

    public SamlAuthenticationFailureHandler(SamlFailureLog failureLog, String registrationId) {
        this.failureLog = failureLog;
        this.registrationId = registrationId;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        failureLog.record(request, exception, registrationId);
        String code = failureLog.lastSummary() == null ? "unknown" : failureLog.lastSummary().errorCode();
        response.sendRedirect(request.getContextPath() + "/?samlError="
                + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }
}
