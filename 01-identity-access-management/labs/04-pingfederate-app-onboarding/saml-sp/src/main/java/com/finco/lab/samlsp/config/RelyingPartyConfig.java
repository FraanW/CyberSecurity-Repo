package com.finco.lab.samlsp.config;

import com.finco.lab.samlsp.saml.IdpTrustStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

import java.security.cert.X509Certificate;

/**
 * Bean wiring for the one thing a SAML SP really is: a <b>relying party registration</b> —
 * "here is who I am, here is who I trust, and here is where they should send the assertion".
 *
 * <p>All the thinking lives in {@link IdpTrustStore}. This class exists to publish it as a
 * {@link RelyingPartyRegistrationRepository}, which is the type Spring Security's SAML filters
 * ask for.</p>
 *
 * <p>Deliberately built in Java rather than in {@code application.yml} so the app can start up
 * <i>unconfigured</i>. On a fresh Render deploy you want a running page that tells you what is
 * missing, not a container that crash-loops before you can read the logs — and, since the IdP can
 * now be imported at runtime, an unconfigured start is a normal state rather than a mistake.</p>
 */
@Configuration
public class RelyingPartyConfig {

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(IdpTrustStore trustStore) {
        return trustStore.createRepository();
    }

    /** Exposed so {@code /api/config} can show the exact values to paste into PingFederate. */
    @Bean
    public LabSpCredentials labSpCredentials(IdpTrustStore trustStore) {
        return new LabSpCredentials(trustStore.spCertificate(), trustStore.spKeysAreEphemeral());
    }

    /** What the frontend needs to show you about our own key material. */
    public record LabSpCredentials(X509Certificate certificate, boolean ephemeral) {
        public String certificatePem() {
            return PemUtils.toPem(certificate);
        }
    }
}
