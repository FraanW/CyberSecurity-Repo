package com.finco.lab.samlsp.web;

import com.finco.lab.samlsp.saml.SamlFailureLog;
import com.finco.lab.samlsp.saml.SignatureDiagnosis;
import com.finco.lab.samlsp.config.SamlProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "why did that login fail?" endpoints.
 *
 * <p><b>Split by sensitivity, deliberately.</b> {@code /last-failure} is public but says only what
 * broke — an error code and a one-line verdict — because a dashboard that cannot mention a failure
 * is a dashboard nobody trusts. {@code /last-failure/detail} contains the actual SAML response,
 * which carries somebody's name, email and group memberships, so it needs a session. Log in with
 * the app's own username and password (Step 0) — that path does not depend on the IdP, which is
 * exactly why it is still there when SSO is broken.</p>
 */
@RestController
@RequestMapping("/api/saml")
public class SamlDiagnosticsController {

    private final SamlFailureLog failureLog;
    private final RelyingPartyRegistrationResolver resolver;
    private final SamlProperties properties;

    public SamlDiagnosticsController(SamlFailureLog failureLog,
                                     RelyingPartyRegistrationRepository registrations,
                                     SamlProperties properties) {
        this.failureLog = failureLog;
        // Resolve rather than look up, so the URLs we compare against are the real ones and not
        // the "{baseUrl}" placeholders Spring stores when PUBLIC_BASE_URL is unset.
        this.resolver = new DefaultRelyingPartyRegistrationResolver(registrations);
        this.properties = properties;
    }

    /** Public: was there a failure, and what was it called? No assertion content. */
    @GetMapping("/last-failure")
    public Map<String, Object> lastFailure() {
        SamlFailureLog.Summary summary = failureLog.lastSummary();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hasFailure", summary != null);
        body.put("failure", summary);
        if (summary != null) {
            body.put("detailRequiresLogin", "GET /api/saml/last-failure/detail — needs a session, "
                    + "because it contains the assertion. Use the Step 0 login.");
        }
        return body;
    }

    /** Authenticated: the full diagnosis and the raw response that produced it. */
    @GetMapping("/last-failure/detail")
    public ResponseEntity<Map<String, Object>> lastFailureDetail() {
        SamlFailureLog.Failure failure = failureLog.last();
        if (failure == null) {
            return ResponseEntity.ok(Map.of("hasFailure", false,
                    "note", "No SAML login has failed since this app started."));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hasFailure", true);
        body.put("at", failure.at());
        body.put("errorCode", failure.errorCode());
        body.put("description", failure.description());
        body.put("diagnosis", failure.diagnosis());
        body.put("rawResponseXml", failure.rawResponseXml());
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/last-failure")
    public Map<String, Object> clear() {
        failureLog.clear();
        return Map.of("cleared", true);
    }

    /**
     * Diagnose a response you captured yourself — paste the base64 {@code SAMLResponse} from
     * SAML-tracer, or the decoded XML.
     *
     * <p>Useful when the failure happened on someone else's machine, or when you want to check a
     * response against this app's trust anchors <i>before</i> changing anything. Authenticated,
     * because it will echo the assertion's contents back at you.</p>
     */
    @PostMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnose(@RequestParam("samlResponse") String samlResponse,
                                                       HttpServletRequest request) {
        String xml = samlResponse.stripLeading().startsWith("<")
                ? samlResponse
                : SamlFailureLog.decodeResponse(samlResponse);
        if (xml == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "invalid_input",
                    "message", "That is neither XML nor base64. Paste the SAMLResponse form value "
                            + "exactly as SAML-tracer shows it, or the decoded XML."));
        }
        RelyingPartyRegistration registration =
                resolver.resolve(request, properties.getRegistrationId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("diagnosis", SignatureDiagnosis.of(xml, registration));
        body.put("note", "This is a comparison, not a verification. It tells you whether the key in "
                + "the response is one this app trusts; only the login filter actually checks the "
                + "signature maths.");
        return ResponseEntity.ok(body);
    }
}
