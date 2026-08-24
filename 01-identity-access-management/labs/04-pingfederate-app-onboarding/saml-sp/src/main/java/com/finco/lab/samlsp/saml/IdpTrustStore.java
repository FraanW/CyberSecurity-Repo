package com.finco.lab.samlsp.saml;

import com.finco.lab.samlsp.config.PemUtils;
import com.finco.lab.samlsp.config.ReloadableRelyingPartyRegistrationRepository;
import com.finco.lab.samlsp.config.SamlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single answer to "who does this app trust, and how do we know?".
 *
 * <p><b>The design in one sentence:</b> the SP half of the registration (our entity ID, our ACS
 * URL, our keypair) never changes, and the IdP half is rebuilt from whichever source currently
 * wins — so re-pointing the app at a different IdP is one object swap, not a redeploy.</p>
 *
 * <h2>Precedence, highest first</h2>
 * <ol>
 *   <li><b>Imported metadata</b> — a file somebody deliberately uploaded through the UI. A
 *       deliberate act at runtime beats a value someone set weeks ago, and it is the thing you
 *       want to be able to change at 11pm when a rotation broke SSO.</li>
 *   <li><b>{@code LAB_SAML_IDP_METADATA_URL}</b> — fetched at startup.</li>
 *   <li><b>{@code LAB_SAML_IDP_ENTITY_ID} / {@code _SSO_URL} / {@code _CERTIFICATE}</b> — typed by hand.</li>
 *   <li><b>Nothing</b> — a placeholder that lets the app boot and tell you what is missing.</li>
 * </ol>
 *
 * <p><b>The placeholder is the dangerous one</b>, and it is worth understanding why. It has to
 * contain <i>some</i> certificate, because a registration without one will not build. Earlier this
 * app minted a random self-signed certificate for that slot — which meant an unreachable metadata
 * URL degraded silently into "trusts a key nobody holds", and every login failed with
 * {@code invalid_signature} for a reason that had nothing to do with the IdP. It still mints one
 * (there is no alternative), but the state is now flagged everywhere it is visible:
 * {@link TrustState#trustAnchorIsPlaceholder()} is true, {@code /api/config} says so, and the
 * dashboard refuses to pretend login could work.</p>
 */
@Component
public class IdpTrustStore {

    private static final Logger log = LoggerFactory.getLogger(IdpTrustStore.class);

    private static final String PLACEHOLDER_IDP_ENTITY_ID = "urn:lab:pingfederate:not-configured";
    private static final String PLACEHOLDER_SSO_URL = "https://pingfederate.invalid/idp/SSO.saml2";
    private static final String IMPORTED_METADATA_FILE = "imported-idp-metadata.xml";
    private static final String IMPORTED_SELECTION_FILE = "imported-idp-entity-id.txt";
    private static final String IMPORTED_ORIGIN_FILE = "imported-idp-origin.txt";
    /** A metadata document is a few KB. Anything far larger is not metadata. */
    public static final int MAX_METADATA_BYTES = 1_048_576;

    /** Where the current IdP configuration came from. */
    public enum Source {
        IMPORTED_METADATA("imported metadata file"),
        ENV_METADATA_URL("LAB_SAML_IDP_METADATA_URL"),
        ENV_EXPLICIT("environment variables"),
        NONE("nothing — not configured");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A snapshot of the live trust configuration, safe to serialise straight to JSON. */
    public record TrustState(
            Source source,
            String origin,
            Instant appliedAt,
            IdpMetadata idp,
            boolean trustAnchorIsPlaceholder,
            List<String> warnings) {
    }

    private final SamlProperties properties;
    private final X509Certificate spCertificate;
    private final java.security.interfaces.RSAPrivateKey spPrivateKey;
    private final boolean ephemeralSpKeys;
    private final Path stateDir;
    private final AtomicReference<TrustState> state = new AtomicReference<>();
    private ReloadableRelyingPartyRegistrationRepository repository;

    public IdpTrustStore(SamlProperties properties) {
        this.properties = properties;
        boolean supplied = SamlProperties.hasText(properties.getSpPrivateKey())
                && SamlProperties.hasText(properties.getSpCertificate());
        if (supplied) {
            this.spPrivateKey = PemUtils.readPrivateKey(properties.getSpPrivateKey());
            this.spCertificate = PemUtils.readCertificate(properties.getSpCertificate());
            this.ephemeralSpKeys = false;
            log.info("SAML SP: using the signing keypair supplied via LAB_SAML_SP_PRIVATE_KEY / LAB_SAML_SP_CERTIFICATE.");
        } else {
            var keyPair = PemUtils.generateKeyPair();
            this.spPrivateKey = (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate();
            this.spCertificate = PemUtils.selfSign(keyPair, "pingfed-saml-sp-lab");
            this.ephemeralSpKeys = true;
            log.warn("SAML SP: no keypair supplied — generated a throwaway self-signed one. "
                    + "It changes on every restart, so PingFederate will stop trusting our signed "
                    + "AuthnRequests after a redeploy. See /api/config for the current certificate.");
        }
        this.stateDir = Path.of(properties.getStateDir());
    }

    /** Called once by the configuration class that owns the bean wiring. */
    public ReloadableRelyingPartyRegistrationRepository createRepository() {
        this.repository = new ReloadableRelyingPartyRegistrationRepository(bootstrap());
        return this.repository;
    }

    public TrustState state() {
        return state.get();
    }

    public X509Certificate spCertificate() {
        return spCertificate;
    }

    public boolean spKeysAreEphemeral() {
        return ephemeralSpKeys;
    }

    // ------------------------------------------------------------------ startup

    private RelyingPartyRegistration bootstrap() {
        byte[] stored = readStoredMetadata();
        if (stored != null) {
            String entityId = readStoredLine(IMPORTED_SELECTION_FILE);
            String origin = readStoredLine(IMPORTED_ORIGIN_FILE);
            try {
                return applyMetadataInternal(stored, entityId,
                        origin == null ? "a previously imported file" : origin, false);
            } catch (RuntimeException ex) {
                log.error("SAML SP: the metadata imported earlier no longer parses ({}). "
                        + "Falling back to the environment configuration.", ex.getMessage());
            }
        }
        return fromEnvironment();
    }

    private RelyingPartyRegistration fromEnvironment() {
        if (SamlProperties.hasText(properties.getIdpMetadataUrl())) {
            try {
                RelyingPartyRegistration.Builder builder =
                        RelyingPartyRegistrations.fromMetadataLocation(properties.getIdpMetadataUrl());
                RelyingPartyRegistration registration = finish(builder);
                publish(Source.ENV_METADATA_URL, properties.getIdpMetadataUrl(), registration, false);
                log.info("SAML SP: loaded IdP metadata from {}", properties.getIdpMetadataUrl());
                return registration;
            } catch (Exception ex) {
                // Do NOT go straight to the placeholder. Falling all the way back to a random
                // trust anchor is what turns a network problem into an "invalid signature"
                // mystery. Try the manual settings first, and say loudly what happened.
                log.error("SAML SP: could not load IdP metadata from {} ({}).",
                        properties.getIdpMetadataUrl(), ex.getMessage());
                if (hasExplicitSettings()) {
                    log.warn("SAML SP: falling back to the LAB_SAML_IDP_ENTITY_ID / _SSO_URL / "
                            + "_CERTIFICATE settings instead.");
                    return fromExplicitSettings("environment variables (metadata URL was unreachable)");
                }
                return placeholder("metadata URL " + properties.getIdpMetadataUrl() + " is unreachable");
            }
        }
        if (hasExplicitSettings()) {
            return fromExplicitSettings("environment variables");
        }
        return placeholder("no IdP configured yet");
    }

    private boolean hasExplicitSettings() {
        return SamlProperties.hasText(properties.getIdpEntityId())
                && SamlProperties.hasText(properties.getIdpSsoUrl())
                && SamlProperties.hasText(properties.getIdpCertificate());
    }

    private RelyingPartyRegistration fromExplicitSettings(String origin) {
        List<X509Certificate> certificates = PemUtils.readCertificates(properties.getIdpCertificate());
        RelyingPartyRegistration.Builder builder = RelyingPartyRegistration
                .withRegistrationId(properties.getRegistrationId())
                .assertingPartyMetadata(party -> {
                    party.entityId(properties.getIdpEntityId())
                            .singleSignOnServiceLocation(properties.getIdpSsoUrl())
                            .singleSignOnServiceBinding(binding(properties.getIdpSsoBinding()))
                            .wantAuthnRequestsSigned(properties.isSignAuthnRequests())
                            .verificationX509Credentials(credentials -> {
                                credentials.clear();
                                certificates.forEach(certificate ->
                                        credentials.add(Saml2X509Credential.verification(certificate)));
                            });
                    if (SamlProperties.hasText(properties.getIdpSloUrl())) {
                        party.singleLogoutServiceLocation(properties.getIdpSloUrl())
                                .singleLogoutServiceResponseLocation(properties.getIdpSloUrl())
                                .singleLogoutServiceBinding(binding(properties.getIdpSloBinding()));
                    }
                });
        RelyingPartyRegistration registration = finish(builder);
        publish(Source.ENV_EXPLICIT, origin, registration, false);
        log.info("SAML SP: configured manually against IdP entity ID {} with {} trusted signing certificate(s).",
                properties.getIdpEntityId(), certificates.size());
        return registration;
    }

    private RelyingPartyRegistration placeholder(String why) {
        X509Certificate throwaway = PemUtils.selfSign(PemUtils.generateKeyPair(), "unconfigured-idp");
        RelyingPartyRegistration registration = finish(RelyingPartyRegistration
                .withRegistrationId(properties.getRegistrationId())
                .assertingPartyMetadata(party -> party
                        .entityId(PLACEHOLDER_IDP_ENTITY_ID)
                        .singleSignOnServiceLocation(PLACEHOLDER_SSO_URL)
                        .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                        .wantAuthnRequestsSigned(false)
                        .verificationX509Credentials(credentials ->
                                credentials.add(Saml2X509Credential.verification(throwaway)))));
        publish(Source.NONE, why, registration, true);
        log.warn("SAML SP: {}. SAML login cannot work. Import the IdP's metadata file at / "
                + "(Step 2), or set LAB_SAML_IDP_METADATA_URL, or all of LAB_SAML_IDP_ENTITY_ID / "
                + "_SSO_URL / _CERTIFICATE.", why);
        return registration;
    }

    // ------------------------------------------------------------------ runtime import

    /**
     * Preview: parse an uploaded document and describe every IdP in it, changing nothing.
     *
     * <p>Separating preview from apply is the whole point of the feature. You get to <i>read</i>
     * the entity ID, the SSO URL and — the field that matters — the certificate fingerprints
     * before you commit to trusting them. Import wizards that skip this step are why nobody knows
     * which key their SP is actually holding.</p>
     */
    public List<IdpMetadata> preview(byte[] xml) {
        return IdpMetadata.parseAll(guardSize(xml));
    }

    /**
     * Apply an uploaded metadata document. Atomically swaps the live registration, then writes
     * the document to the state directory so a container restart does not undo the change.
     */
    public synchronized TrustState importMetadata(byte[] xml, String entityId, String origin) {
        RelyingPartyRegistration registration = applyMetadataInternal(guardSize(xml), entityId, origin, true);
        repository.replace(registration);
        log.info("SAML SP: now trusting IdP '{}' from {} ({} signing certificate(s)).",
                registration.getAssertingPartyMetadata().getEntityId(), origin,
                registration.getAssertingPartyMetadata().getVerificationX509Credentials().size());
        return state.get();
    }

    /** Throw away an imported document and go back to whatever the environment says. */
    public synchronized TrustState reset() {
        deleteStored();
        RelyingPartyRegistration registration = fromEnvironment();
        repository.replace(registration);
        log.info("SAML SP: imported metadata cleared; back to {}.", state.get().source().label());
        return state.get();
    }

    private RelyingPartyRegistration applyMetadataInternal(byte[] xml, String entityId, String origin,
                                                           boolean persist) {
        List<IdpMetadata> parsed = IdpMetadata.parseAll(xml);
        IdpMetadata chosen = select(parsed, entityId);
        if (!chosen.isUsable()) {
            throw new IdpMetadata.ParseException("That metadata describes IdP '" + chosen.entityId()
                    + "' but is missing an SSO URL or a signing certificate, so it cannot drive a login.");
        }

        RelyingPartyRegistration.Builder builder = IdpMetadata.builderFor(xml, chosen.entityId());
        builder.assertingPartyMetadata(party ->
                party.wantAuthnRequestsSigned(properties.isSignAuthnRequests()));
        RelyingPartyRegistration registration = finish(builder);

        if (persist) {
            store(xml, chosen.entityId(), origin);
        }
        publish(Source.IMPORTED_METADATA, origin, registration, false, chosen);
        return registration;
    }

    private static IdpMetadata select(List<IdpMetadata> parsed, String entityId) {
        if (entityId == null || entityId.isBlank()) {
            if (parsed.size() > 1) {
                throw new IdpMetadata.ParseException("This document describes " + parsed.size()
                        + " IdPs. Pick one by entity ID: "
                        + parsed.stream().map(IdpMetadata::entityId).toList());
            }
            return parsed.get(0);
        }
        return parsed.stream()
                .filter(candidate -> entityId.equals(candidate.entityId()))
                .findFirst()
                .orElseThrow(() -> new IdpMetadata.ParseException(
                        "No IdP with entity ID '" + entityId + "' in this document. It contains: "
                                + parsed.stream().map(IdpMetadata::entityId).toList()));
    }

    // ------------------------------------------------------------------ shared assembly

    /**
     * Attaches the SP half — the part that is the same no matter which IdP we point at.
     *
     * <p>Our signing key proves we sent the AuthnRequest; our decryption key opens an encrypted
     * assertion. Both are the <i>same</i> keypair here, which is fine for a lab and common in
     * practice, though separating them is tidier when a key has to be rotated for one purpose
     * and not the other.</p>
     */
    private RelyingPartyRegistration finish(RelyingPartyRegistration.Builder builder) {
        return builder
                .registrationId(properties.getRegistrationId())
                .entityId(SamlProperties.hasText(properties.getSpEntityId())
                        ? properties.getSpEntityId()
                        : template("/saml2/service-provider-metadata/{registrationId}"))
                .signingX509Credentials(credentials -> {
                    credentials.clear();
                    credentials.add(Saml2X509Credential.signing(spPrivateKey, spCertificate));
                })
                .decryptionX509Credentials(credentials -> {
                    credentials.clear();
                    credentials.add(Saml2X509Credential.decryption(spPrivateKey, spCertificate));
                })
                .assertionConsumerServiceLocation(template("/login/saml2/sso/{registrationId}"))
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .singleLogoutServiceLocation(template("/logout/saml2/slo"))
                .singleLogoutServiceResponseLocation(template("/logout/saml2/slo"))
                .singleLogoutServiceBinding(binding(properties.getIdpSloBinding()))
                .build();
    }

    private void publish(Source source, String origin, RelyingPartyRegistration registration,
                         boolean placeholder) {
        publish(source, origin, registration, placeholder,
                IdpMetadata.of(registration.getAssertingPartyMetadata()));
    }

    private void publish(Source source, String origin, RelyingPartyRegistration registration,
                         boolean placeholder, IdpMetadata idp) {
        List<String> warnings = new ArrayList<>(idp.warnings());
        if (placeholder) {
            warnings.add(0, "No real IdP is configured, so this app is currently trusting a "
                    + "randomly generated certificate that nobody holds the private key for. "
                    + "Every login will fail with invalid_signature until you fix that — the "
                    + "failure would be ours, not PingFederate's.");
        }
        if (ephemeralSpKeys && registration.getAssertingPartyMetadata().getWantAuthnRequestsSigned()) {
            warnings.add("Our own signing keypair is a throwaway one that changes on every restart. "
                    + "PingFederate will reject our AuthnRequest after a redeploy until you "
                    + "re-import our SP metadata. Set LAB_SAML_SP_PRIVATE_KEY / _CERTIFICATE to fix it.");
        }
        state.set(new TrustState(source, origin, Instant.now(), idp, placeholder, List.copyOf(warnings)));
    }

    /**
     * Render terminates TLS at its edge, so a URL built from the raw request would say
     * {@code http://}. When PUBLIC_BASE_URL is set we pin the public HTTPS origin instead of
     * letting Spring guess with {@code {baseUrl}}.
     */
    private String template(String path) {
        if (SamlProperties.hasText(properties.getPublicBaseUrl())) {
            return properties.getPublicBaseUrl()
                    + path.replace("{registrationId}", properties.getRegistrationId());
        }
        return "{baseUrl}" + path;
    }

    private static Saml2MessageBinding binding(String value) {
        return "POST".equalsIgnoreCase(value) ? Saml2MessageBinding.POST : Saml2MessageBinding.REDIRECT;
    }

    private static byte[] guardSize(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new IdpMetadata.ParseException("No metadata content was sent.");
        }
        if (xml.length > MAX_METADATA_BYTES) {
            throw new IdpMetadata.ParseException("That file is " + (xml.length / 1024)
                    + " KB. SAML metadata is a few KB — this is almost certainly the wrong file.");
        }
        return xml;
    }

    // ------------------------------------------------------------------ persistence

    /**
     * Best-effort persistence. An import survives a container <i>restart</i>; it does not survive
     * a redeploy, because the filesystem is rebuilt from the image. That is why the API hands
     * back the equivalent environment variables — copying those into Render is what makes a
     * configuration permanent.
     */
    private void store(byte[] xml, String entityId, String origin) {
        try {
            Files.createDirectories(stateDir);
            Files.write(stateDir.resolve(IMPORTED_METADATA_FILE), xml);
            Files.writeString(stateDir.resolve(IMPORTED_SELECTION_FILE), entityId);
            Files.writeString(stateDir.resolve(IMPORTED_ORIGIN_FILE), origin == null ? "" : origin);
        } catch (IOException ex) {
            log.warn("SAML SP: imported metadata applied but could not be saved to {} ({}). "
                    + "It will be lost on restart.", stateDir, ex.getMessage());
        }
    }

    private byte[] readStoredMetadata() {
        Path file = stateDir.resolve(IMPORTED_METADATA_FILE);
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException ex) {
            log.warn("SAML SP: could not read {} ({}).", file, ex.getMessage());
            return null;
        }
    }

    private String readStoredLine(String name) {
        Path file = stateDir.resolve(name);
        try {
            if (!Files.exists(file)) {
                return null;
            }
            String value = Files.readString(file).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException ex) {
            return null;
        }
    }

    private void deleteStored() {
        for (String name : List.of(IMPORTED_METADATA_FILE, IMPORTED_SELECTION_FILE, IMPORTED_ORIGIN_FILE)) {
            try {
                Files.deleteIfExists(stateDir.resolve(name));
            } catch (IOException ex) {
                log.warn("SAML SP: could not delete {} ({}).", name, ex.getMessage());
            }
        }
    }
}
