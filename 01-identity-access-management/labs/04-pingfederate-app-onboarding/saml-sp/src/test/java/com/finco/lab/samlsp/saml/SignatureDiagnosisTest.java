package com.finco.lab.samlsp.saml;

import com.finco.lab.samlsp.SamlTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The empirical check behind the whole RCA: <b>two different keypairs really do produce a
 * mismatch, and the same keypair really does not.</b>
 *
 * <p>Everything about "invalid signature" in this lab rests on that claim, so it is worth proving
 * rather than asserting. The fixtures build one IdP that signs and, in the failure case, a second
 * IdP whose certificate the SP is holding instead — which is exactly what a key rotation does to
 * you in production.</p>
 */
class SignatureDiagnosisTest {

    private static final String IDP_ENTITY_ID = "https://pf.lab.invalid/idp";
    private static final String SP_ENTITY_ID = "https://sp.lab.invalid/saml2/service-provider-metadata/pingfed";
    private static final String ACS = "https://sp.lab.invalid/login/saml2/sso/pingfed";

    @Test
    void aDifferentKeypairIsReportedAsAKeyMismatch() {
        SamlTestFixtures.Idp signer = SamlTestFixtures.idp("pingfed-signing-key-CURRENT");
        SamlTestFixtures.Idp stale = SamlTestFixtures.idp("pingfed-signing-key-LAST-YEAR");

        SignatureDiagnosis diagnosis = SignatureDiagnosis.of(
                SamlTestFixtures.response(IDP_ENTITY_ID, SP_ENTITY_ID, ACS, signer),
                registrationTrusting(stale.certificate()));

        assertThat(diagnosis.verdict()).isEqualTo(SignatureDiagnosis.Verdict.KEY_MISMATCH);
        assertThat(diagnosis.certificatesInResponse()).hasSize(1);
        assertThat(diagnosis.certificatesWeTrust()).hasSize(1);
        // The two fingerprints differ — which is the entire finding, stated in one line.
        assertThat(diagnosis.certificatesInResponse().get(0).sha256Fingerprint())
                .isNotEqualTo(diagnosis.certificatesWeTrust().get(0).sha256Fingerprint());
        assertThat(diagnosis.summary()).contains("Key mismatch");
        assertThat(diagnosis.nextSteps()).isNotEmpty();
    }

    @Test
    void theSameKeypairIsReportedAsAKeyMatch() {
        SamlTestFixtures.Idp signer = SamlTestFixtures.idp("pingfed-signing-key");

        SignatureDiagnosis diagnosis = SignatureDiagnosis.of(
                SamlTestFixtures.response(IDP_ENTITY_ID, SP_ENTITY_ID, ACS, signer),
                registrationTrusting(signer.certificate()));

        assertThat(diagnosis.verdict()).isEqualTo(SignatureDiagnosis.Verdict.KEY_MATCHES);
        assertThat(diagnosis.certificatesInResponse().get(0).sha256Fingerprint())
                .isEqualTo(diagnosis.certificatesWeTrust().get(0).sha256Fingerprint());
    }

    @Test
    void duringARotationTrustingBothCertificatesResolvesTheMismatch() {
        SamlTestFixtures.Idp oldKey = SamlTestFixtures.idp("pingfed-old");
        SamlTestFixtures.Idp newKey = SamlTestFixtures.idp("pingfed-new");

        SignatureDiagnosis diagnosis = SignatureDiagnosis.of(
                SamlTestFixtures.response(IDP_ENTITY_ID, SP_ENTITY_ID, ACS, newKey),
                registrationTrusting(oldKey.certificate(), newKey.certificate()));

        assertThat(diagnosis.verdict()).isEqualTo(SignatureDiagnosis.Verdict.KEY_MATCHES);
    }

    @Test
    void theSignatureAlgorithmsAreReportedForTheSignedElement() {
        SamlTestFixtures.Idp signer = SamlTestFixtures.idp("pingfed");
        SignatureDiagnosis diagnosis = SignatureDiagnosis.of(
                SamlTestFixtures.response(IDP_ENTITY_ID, SP_ENTITY_ID, ACS, signer),
                registrationTrusting(signer.certificate()));

        assertThat(diagnosis.signedElements()).containsExactly("Assertion");
        assertThat(diagnosis.signatures().get(0).signatureAlgorithm())
                .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        assertThat(diagnosis.signatures().get(0).includesCertificate()).isTrue();
    }

    @Test
    void theOtherRejectionCausesAreCheckedAtTheSameTime() {
        SamlTestFixtures.Idp signer = SamlTestFixtures.idp("pingfed");
        SignatureDiagnosis diagnosis = SignatureDiagnosis.of(
                SamlTestFixtures.response(IDP_ENTITY_ID, "https://someone-elses-sp.invalid", ACS, signer),
                registrationTrusting(signer.certificate()));

        assertThat(diagnosis.otherChecks())
                .anyMatch(check -> check.startsWith("✓ Issuer"))
                .anyMatch(check -> check.startsWith("✗ Audience"))
                .anyMatch(check -> check.startsWith("✓ Destination"));
    }

    @Test
    void anUnsignedResponseSaysSoRatherThanBlamingTheKey() {
        SamlTestFixtures.Idp trusted = SamlTestFixtures.idp("pingfed");
        String unsigned = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="_r">
                  <saml:Issuer>%s</saml:Issuer>
                  <saml:Assertion ID="_a"><saml:Issuer>%s</saml:Issuer></saml:Assertion>
                </samlp:Response>
                """.formatted(IDP_ENTITY_ID, IDP_ENTITY_ID);

        SignatureDiagnosis diagnosis =
                SignatureDiagnosis.of(unsigned, registrationTrusting(trusted.certificate()));

        assertThat(diagnosis.verdict()).isEqualTo(SignatureDiagnosis.Verdict.NOT_SIGNED);
    }

    private static RelyingPartyRegistration registrationTrusting(X509Certificate... certificates) {
        return RelyingPartyRegistration.withRegistrationId("pingfed")
                .entityId(SP_ENTITY_ID)
                .assertionConsumerServiceLocation(ACS)
                .assertingPartyMetadata(party -> party
                        .entityId(IDP_ENTITY_ID)
                        .singleSignOnServiceLocation("https://pf.lab.invalid/idp/SSO.saml2")
                        .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                        .verificationX509Credentials(credentials -> {
                            for (X509Certificate certificate : certificates) {
                                credentials.add(Saml2X509Credential.verification(certificate));
                            }
                        }))
                .build();
    }
}
