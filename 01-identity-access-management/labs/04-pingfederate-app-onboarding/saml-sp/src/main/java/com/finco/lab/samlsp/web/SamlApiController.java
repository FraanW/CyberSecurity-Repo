package com.finco.lab.samlsp.web;

import com.finco.lab.samlsp.config.RelyingPartyConfig.LabSpCredentials;
import com.finco.lab.samlsp.config.SamlProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.metadata.Saml2MetadataResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lab's read-only API. Every endpoint here exists to answer one onboarding question:
 * "what did PingFederate actually send me, and what do I paste into its console?"
 */
@RestController
@RequestMapping("/api")
public class SamlApiController {

    private final SamlProperties properties;
    private final LabSpCredentials credentials;
    private final RelyingPartyRegistrationResolver resolver;
    private final Saml2MetadataResolver metadataResolver = new OpenSaml5MetadataResolver();
    private final Instant startedAt = Instant.now();

    public SamlApiController(SamlProperties properties,
                             LabSpCredentials credentials,
                             RelyingPartyRegistrationRepository registrations) {
        this.properties = properties;
        this.credentials = credentials;
        this.resolver = new DefaultRelyingPartyRegistrationResolver(registrations);
    }

    /** Liveness. Render pings this; so can you. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "pingfed-saml-sp",
                "role", "SAML 2.0 Service Provider",
                "startedAt", startedAt.toString());
    }

    /** Unauthenticated smoke test, so you can prove routing works before SSO does. */
    @GetMapping("/public/ping")
    public Map<String, Object> ping() {
        return Map.of("pong", true, "authenticated", false);
    }

    /**
     * The onboarding cheat sheet: every value PingFederate asks for when you create the
     * SP connection, already filled in with this deployment's real URLs.
     */
    @GetMapping("/config")
    public Map<String, Object> config(HttpServletRequest request) {
        RelyingPartyRegistration registration =
                resolver.resolve(request, properties.getRegistrationId());

        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("entityId", registration.getEntityId());
        sp.put("assertionConsumerServiceUrl", registration.getAssertionConsumerServiceLocation());
        sp.put("assertionConsumerServiceBinding", registration.getAssertionConsumerServiceBinding().getUrn());
        sp.put("singleLogoutServiceUrl", registration.getSingleLogoutServiceLocation());
        sp.put("metadataUrl", absolute(request,
                "/saml2/service-provider-metadata/" + properties.getRegistrationId()));
        sp.put("loginUrl", absolute(request, "/saml2/authenticate/" + properties.getRegistrationId()));
        sp.put("signingCertificate", credentials.certificatePem());
        sp.put("signingCertificateIsEphemeral", credentials.ephemeral());

        var party = registration.getAssertingPartyMetadata();
        Map<String, Object> idp = new LinkedHashMap<>();
        idp.put("entityId", party.getEntityId());
        idp.put("singleSignOnServiceUrl", party.getSingleSignOnServiceLocation());
        idp.put("singleSignOnServiceBinding", party.getSingleSignOnServiceBinding().getUrn());
        idp.put("singleLogoutServiceUrl", party.getSingleLogoutServiceLocation());
        idp.put("wantAuthnRequestsSigned", party.getWantAuthnRequestsSigned());
        idp.put("configuredFrom", SamlProperties.hasText(properties.getIdpMetadataUrl())
                ? "metadata URL" : "explicit settings");

        return Map.of(
                "configured", properties.isConfigured(),
                "registrationId", properties.getRegistrationId(),
                "serviceProvider", sp,
                "identityProvider", idp,
                "missing", missingSettings());
    }

    /**
     * Who you are — whichever way you logged in.
     *
     * <p>The {@code loginType} field is the interesting one: {@code local} means this app checked
     * your password itself; {@code saml} means it never saw a password and trusted PingFederate's
     * signed assertion instead. Same session cookie, same app, entirely different trust story.</p>
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(Authentication authentication, HttpSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("authorities", authentication.getAuthorities().stream()
                .map(Object::toString).toList());
        body.put("localSessionId", session.getId());
        body.put("localSessionCreatedAt", Instant.ofEpochMilli(session.getCreationTime()).toString());

        if (authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
            body.put("loginType", "saml");
            body.put("nameId", principal.getName());
            body.put("relyingPartyRegistrationId", principal.getRelyingPartyRegistrationId());
            body.put("sessionIndexes", principal.getSessionIndexes());
            body.put("attributes", flattenAttributes(principal));
        } else {
            body.put("loginType", "local");
            body.put("nameId", authentication.getName());
            body.put("attributes", Map.of());
            body.put("note", "You are logged in with this app's own username and password. "
                    + "No IdP was involved, so there is no assertion to inspect. "
                    + "Onboard the app into PingFederate and log in again to see one.");
        }
        return body;
    }

    /** Just the attribute contract — the exact list to compare against the Ping console. */
    @GetMapping("/attributes")
    public ResponseEntity<Map<String, Object>> attributes(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal)) {
            return ResponseEntity.ok(localLoginNotice());
        }
        Map<String, List<Object>> raw = principal.getAttributes();
        return ResponseEntity.ok(Map.of(
                "count", raw.size(),
                "names", raw.keySet(),
                "attributes", flattenAttributes(principal)));
    }

    /**
     * The raw, pretty-printed SAML Response exactly as it arrived — the thing you would
     * otherwise need SAML-tracer to see.
     */
    @GetMapping(value = "/assertion", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> assertion(Authentication authentication) {
        if (!(authentication instanceof Saml2Authentication saml2)) {
            return ResponseEntity.ok("<!-- No SAML assertion: this session was created by the "
                    + "app's own username/password login, not by PingFederate. -->");
        }
        return ResponseEntity.ok(SamlResponseFormatter.prettyPrint(saml2.getSaml2Response()));
    }

    /** The handful of assertion fields that break onboarding most often, parsed out for you. */
    @GetMapping("/assertion/highlights")
    public Map<String, Object> assertionHighlights(Authentication authentication) {
        if (!(authentication instanceof Saml2Authentication saml2)) {
            return localLoginNotice();
        }
        return SamlResponseFormatter.highlights(saml2.getSaml2Response());
    }

    /**
     * The SP metadata as a downloadable file. This is the one artifact PingFederate wants:
     * import it and the entity ID, ACS URL, bindings and signing certificate are all filled in
     * for you — no retyping, no typos.
     */
    @GetMapping(value = "/sp-metadata.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> spMetadata(HttpServletRequest request) {
        RelyingPartyRegistration registration =
                resolver.resolve(request, properties.getRegistrationId());
        String xml = metadataResolver.resolve(registration);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + properties.getRegistrationId() + "-sp-metadata.xml\"")
                .body(xml);
    }

    private Map<String, Object> localLoginNotice() {
        return Map.of(
                "loginType", "local",
                "note", "This session came from the app's own username/password login, so there is "
                        + "no SAML assertion behind it. Configure the IdP and log in via "
                        + "PingFederate to populate these fields.");
    }

    private Map<String, Object> flattenAttributes(Saml2AuthenticatedPrincipal principal) {
        Map<String, Object> flat = new LinkedHashMap<>();
        principal.getAttributes().forEach((name, values) ->
                flat.put(name, values.size() == 1 ? values.get(0) : values));
        return flat;
    }

    private List<String> missingSettings() {
        if (properties.isConfigured()) {
            return List.of();
        }
        return List.of(
                "LAB_SAML_IDP_METADATA_URL  (easiest — one URL and you are done)",
                "or all three of: LAB_SAML_IDP_ENTITY_ID, LAB_SAML_IDP_SSO_URL, LAB_SAML_IDP_CERTIFICATE");
    }

    private String absolute(HttpServletRequest request, String path) {
        if (SamlProperties.hasText(properties.getPublicBaseUrl())) {
            return properties.getPublicBaseUrl() + path;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port)
                + request.getContextPath() + path;
    }
}
