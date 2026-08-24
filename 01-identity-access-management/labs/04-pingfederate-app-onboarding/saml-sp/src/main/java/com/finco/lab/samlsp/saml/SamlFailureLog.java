package com.finco.lab.samlsp.saml;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Remembers the last SAML login failure, and works out what it meant.
 *
 * <p><b>Why this exists.</b> Out of the box, a rejected assertion is a redirect to
 * {@code /login?error} and one line in the server log. The user sees a blank page; the operator
 * sees nothing unless they had DEBUG logging on <i>before</i> the failure. That is backwards: the
 * moment of failure is precisely when you have the evidence in your hands, and it is thrown away.
 * This captures it — the error code, the raw response, and a signature diagnosis — so the next
 * click can show you the cause rather than the symptom.</p>
 *
 * <p><b>Only the most recent failure is kept</b>, in memory, and it is cleared on a successful
 * login. A SAML response contains a real person's identity attributes, so it is not something to
 * accumulate: the detail endpoint requires a session, and the app forgets the whole thing on
 * restart. In production you would not keep it at all.</p>
 */
@Component
public class SamlFailureLog {

    private static final Logger log = LoggerFactory.getLogger(SamlFailureLog.class);

    /** What the dashboard may show without a session: enough to name the problem, no assertion content. */
    public record Summary(Instant at, String errorCode, String description, String verdict, String headline) {
    }

    /** The full picture, including the raw response. Requires a session to read. */
    public record Failure(Instant at, String errorCode, String description, String rawResponseXml,
                          SignatureDiagnosis diagnosis) {
        Summary summary() {
            return new Summary(at, errorCode, description,
                    diagnosis == null ? null : diagnosis.verdict().name(),
                    diagnosis == null ? description : diagnosis.summary());
        }
    }

    private final RelyingPartyRegistrationResolver resolver;
    private final AtomicReference<Failure> last = new AtomicReference<>();

    public SamlFailureLog(RelyingPartyRegistrationRepository registrations) {
        // The *resolver*, not the repository. A registration straight out of the repository still
        // has "{baseUrl}" in its entity ID and ACS URL when PUBLIC_BASE_URL is unset; comparing an
        // assertion's Audience against a literal "{baseUrl}/..." would report a mismatch that is
        // not real. The resolver fills those in from the request that just arrived.
        this.resolver = new DefaultRelyingPartyRegistrationResolver(registrations);
    }

    public Failure last() {
        return last.get();
    }

    public Summary lastSummary() {
        Failure failure = last.get();
        return failure == null ? null : failure.summary();
    }

    public void clear() {
        last.set(null);
    }

    /**
     * Called by the failure handler. Pulls {@code SAMLResponse} straight off the request — the
     * form field PingFederate POSTed — and runs the diagnosis against the registration that was
     * live at the time.
     */
    public void record(HttpServletRequest request, AuthenticationException exception, String registrationId) {
        Saml2Error error = (exception instanceof Saml2AuthenticationException saml2)
                ? saml2.getSaml2Error()
                : new Saml2Error("authentication_failure", exception.getMessage());

        String xml = decodeResponse(request.getParameter("SAMLResponse"));
        SignatureDiagnosis diagnosis = null;
        RelyingPartyRegistration registration = resolver.resolve(request, registrationId);
        if (xml != null && registration != null) {
            try {
                diagnosis = SignatureDiagnosis.of(xml, registration);
            } catch (RuntimeException ex) {
                log.warn("SAML SP: could not diagnose the failed response: {}", ex.toString());
            }
        }

        last.set(new Failure(Instant.now(), error.getErrorCode(), error.getDescription(), xml, diagnosis));
        log.error("SAML SP: login rejected [{}] {}{}", error.getErrorCode(), error.getDescription(),
                diagnosis == null ? "" : " — " + diagnosis.summary());
    }

    public static String decodeResponse(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
