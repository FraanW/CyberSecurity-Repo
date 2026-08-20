package com.finco.lab.samlsp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;

/**
 * Builds the one thing a SAML SP really is: a <b>relying party registration</b> — "here is who I
 * am, here is who I trust, and here is where they should send the assertion".
 *
 * <p>Deliberately built in Java rather than in {@code application.yml} so the app can start up
 * <i>unconfigured</i>. On a fresh Render deploy you want a running page that tells you what is
 * missing, not a container that crash-loops before you can read the logs.</p>
 */
@Configuration
public class RelyingPartyConfig {

    private static final Logger log = LoggerFactory.getLogger(RelyingPartyConfig.class);

    /** Stand-in values used only when nothing is configured yet, so the app still boots. */
    private static final String PLACEHOLDER_IDP_ENTITY_ID = "urn:lab:pingfederate:not-configured";
    private static final String PLACEHOLDER_SSO_URL = "https://pingfederate.invalid/idp/SSO.saml2";

    private final SamlProperties properties;
    private final X509Certificate spCertificate;
    private final RSAPrivateKey spPrivateKey;
    private final boolean ephemeralKeys;

    public RelyingPartyConfig(SamlProperties properties) {
        this.properties = properties;
        boolean supplied = SamlProperties.hasText(properties.getSpPrivateKey())
                && SamlProperties.hasText(properties.getSpCertificate());
        if (supplied) {
            this.spPrivateKey = PemUtils.readPrivateKey(properties.getSpPrivateKey());
            this.spCertificate = PemUtils.readCertificate(properties.getSpCertificate());
            this.ephemeralKeys = false;
            log.info("SAML SP: using the signing keypair supplied via LAB_SAML_SP_PRIVATE_KEY / LAB_SAML_SP_CERTIFICATE.");
        } else {
            KeyPair keyPair = PemUtils.generateKeyPair();
            this.spPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.spCertificate = PemUtils.selfSign(keyPair, "pingfed-saml-sp-lab");
            this.ephemeralKeys = true;
            log.warn("SAML SP: no keypair supplied — generated a throwaway self-signed one. "
                    + "It changes on every restart, so upload a fixed keypair before you rely on "
                    + "signed AuthnRequests or encrypted assertions. See /api/config for the current cert.");
        }
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        return new InMemoryRelyingPartyRegistrationRepository(buildRegistration());
    }

    /** Exposed so {@code /api/config} can show the exact values to paste into PingFederate. */
    @Bean
    public LabSpCredentials labSpCredentials() {
        return new LabSpCredentials(spCertificate, ephemeralKeys);
    }

    private RelyingPartyRegistration buildRegistration() {
        RelyingPartyRegistration.Builder builder;

        if (SamlProperties.hasText(properties.getIdpMetadataUrl())) {
            builder = fromMetadata();
        } else {
            builder = fromExplicitSettings();
        }

        Saml2X509Credential signing = Saml2X509Credential.signing(spPrivateKey, spCertificate);
        Saml2X509Credential decryption = Saml2X509Credential.decryption(spPrivateKey, spCertificate);

        builder.registrationId(properties.getRegistrationId())
                .signingX509Credentials(credentials -> credentials.add(signing))
                .decryptionX509Credentials(credentials -> credentials.add(decryption))
                .assertionConsumerServiceLocation(template("/login/saml2/sso/{registrationId}"))
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .singleLogoutServiceLocation(template("/logout/saml2/slo"))
                .singleLogoutServiceResponseLocation(template("/logout/saml2/slo"))
                .singleLogoutServiceBinding(binding(properties.getIdpSloBinding()));

        if (SamlProperties.hasText(properties.getSpEntityId())) {
            builder.entityId(properties.getSpEntityId());
        } else {
            builder.entityId(template("/saml2/service-provider-metadata/{registrationId}"));
        }

        return builder.build();
    }

    /**
     * The happy path: PingFederate publishes its metadata, we read the entity ID, SSO URL and
     * signing certificate straight out of it. One URL instead of three env vars.
     */
    private RelyingPartyRegistration.Builder fromMetadata() {
        try {
            RelyingPartyRegistration.Builder builder =
                    RelyingPartyRegistrations.fromMetadataLocation(properties.getIdpMetadataUrl());
            log.info("SAML SP: loaded PingFederate metadata from {}", properties.getIdpMetadataUrl());
            return applyAuthnRequestSigning(builder);
        } catch (Exception ex) {
            log.error("SAML SP: could not load IdP metadata from {} ({}). "
                            + "Falling back to a placeholder so the app still starts — fix LAB_SAML_IDP_METADATA_URL "
                            + "or switch to the manual entity-id / sso-url / certificate settings.",
                    properties.getIdpMetadataUrl(), ex.getMessage());
            return placeholder();
        }
    }

    /** The manual path: you paste PingFederate's entity ID, SSO URL and signing certificate. */
    private RelyingPartyRegistration.Builder fromExplicitSettings() {
        if (!properties.isConfigured()) {
            log.warn("SAML SP: no IdP configured yet. Set LAB_SAML_IDP_METADATA_URL, or all of "
                    + "LAB_SAML_IDP_ENTITY_ID / LAB_SAML_IDP_SSO_URL / LAB_SAML_IDP_CERTIFICATE. "
                    + "The app is up but login will not work. Open / to see what is missing.");
            return placeholder();
        }
        X509Certificate idpCertificate = PemUtils.readCertificate(properties.getIdpCertificate());
        RelyingPartyRegistration.Builder builder = RelyingPartyRegistration
                .withRegistrationId(properties.getRegistrationId())
                .assertingPartyMetadata(party -> party
                        .entityId(properties.getIdpEntityId())
                        .singleSignOnServiceLocation(properties.getIdpSsoUrl())
                        .singleSignOnServiceBinding(binding(properties.getIdpSsoBinding()))
                        .wantAuthnRequestsSigned(properties.isSignAuthnRequests())
                        .verificationX509Credentials(credentials ->
                                credentials.add(Saml2X509Credential.verification(idpCertificate))));
        if (SamlProperties.hasText(properties.getIdpSloUrl())) {
            builder.assertingPartyMetadata(party -> party
                    .singleLogoutServiceLocation(properties.getIdpSloUrl())
                    .singleLogoutServiceResponseLocation(properties.getIdpSloUrl())
                    .singleLogoutServiceBinding(binding(properties.getIdpSloBinding())));
        }
        log.info("SAML SP: configured manually against IdP entity ID {}", properties.getIdpEntityId());
        return builder;
    }

    private RelyingPartyRegistration.Builder applyAuthnRequestSigning(RelyingPartyRegistration.Builder builder) {
        return builder.assertingPartyMetadata(party ->
                party.wantAuthnRequestsSigned(properties.isSignAuthnRequests()));
    }

    /**
     * A registration that is structurally valid but points nowhere. It exists so an unconfigured
     * deploy still serves the frontend and the {@code /api/config} checklist.
     */
    private RelyingPartyRegistration.Builder placeholder() {
        X509Certificate throwaway = PemUtils.selfSign(PemUtils.generateKeyPair(), "unconfigured-idp");
        return RelyingPartyRegistration
                .withRegistrationId(properties.getRegistrationId())
                .assertingPartyMetadata(party -> party
                        .entityId(PLACEHOLDER_IDP_ENTITY_ID)
                        .singleSignOnServiceLocation(PLACEHOLDER_SSO_URL)
                        .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                        .wantAuthnRequestsSigned(false)
                        .verificationX509Credentials(credentials ->
                                credentials.add(Saml2X509Credential.verification(throwaway))));
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

    /** What the frontend needs to show you about our own key material. */
    public record LabSpCredentials(X509Certificate certificate, boolean ephemeral) {
        public String certificatePem() {
            return PemUtils.toPem(certificate);
        }
    }
}
