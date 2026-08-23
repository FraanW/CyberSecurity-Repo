package com.finco.lab.samlsp;

import com.finco.lab.samlsp.config.PemUtils;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Builders for the two XML documents these tests need: an IdP metadata file, and a SAML Response
 * that carries a certificate in its {@code <ds:KeyInfo>}.
 *
 * <p>Both are written out by hand rather than produced with OpenSAML. That is deliberate — the
 * point of the tests is to check that <i>our</i> parsing and diagnosis behave, and generating the
 * fixtures with the same library that parses them would let a shared bug pass unnoticed.</p>
 */
public final class SamlTestFixtures {

    private SamlTestFixtures() {
    }

    public record Idp(KeyPair keyPair, X509Certificate certificate) {
        public String base64Certificate() {
            try {
                return Base64.getEncoder().encodeToString(certificate.getEncoded());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public static Idp idp(String commonName) {
        KeyPair keyPair = PemUtils.generateKeyPair();
        return new Idp(keyPair, PemUtils.selfSign(keyPair, commonName));
    }

    /** A minimal but schema-ordered IdP metadata document — the shape PingFederate exports. */
    public static String metadata(String entityId, String ssoUrl, Idp... signingCertificates) {
        StringBuilder keys = new StringBuilder();
        for (Idp idp : signingCertificates) {
            keys.append("""
                        <md:KeyDescriptor use="signing">
                          <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                            <ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data>
                          </ds:KeyInfo>
                        </md:KeyDescriptor>
                    """.formatted(idp.base64Certificate()));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
                  <md:IDPSSODescriptor WantAuthnRequestsSigned="true"
                      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                %s    <md:SingleLogoutService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="%s/idp/SLO.saml2"/>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:unspecified</md:NameIDFormat>
                    <md:SingleSignOnService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="%s"/>
                  </md:IDPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(entityId, keys, ssoUrl.replaceAll("/idp/SSO.saml2$", ""), ssoUrl);
    }

    /**
     * A SAML Response with a signature block that names a certificate. The signature value is not
     * real maths — {@code SignatureDiagnosis} never verifies it, it only compares which key was
     * advertised against which keys we trust, which is exactly the distinction under test.
     */
    public static String response(String issuer, String audience, String destination, Idp signer) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                                ID="_resp1" Version="2.0" IssueInstant="2026-01-01T00:00:00Z"
                                Destination="%s" InResponseTo="_req1">
                  <saml:Issuer>%s</saml:Issuer>
                  <samlp:Status>
                    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                  </samlp:Status>
                  <saml:Assertion ID="_a1" Version="2.0" IssueInstant="2026-01-01T00:00:00Z">
                    <saml:Issuer>%s</saml:Issuer>
                    <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                      <ds:SignedInfo>
                        <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                        <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                        <ds:Reference URI="#_a1">
                          <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                          <ds:DigestValue>bm90LXJlYWw=</ds:DigestValue>
                        </ds:Reference>
                      </ds:SignedInfo>
                      <ds:SignatureValue>bm90LXJlYWw=</ds:SignatureValue>
                      <ds:KeyInfo>
                        <ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data>
                      </ds:KeyInfo>
                    </ds:Signature>
                    <saml:Subject>
                      <saml:NameID Format="urn:oasis:names:tc:SAML:2.0:nameid-format:unspecified">labuser</saml:NameID>
                    </saml:Subject>
                    <saml:Conditions NotBefore="2026-01-01T00:00:00Z" NotOnOrAfter="2036-01-01T00:00:00Z">
                      <saml:AudienceRestriction><saml:Audience>%s</saml:Audience></saml:AudienceRestriction>
                    </saml:Conditions>
                  </saml:Assertion>
                </samlp:Response>
                """.formatted(destination, issuer, issuer, signer.base64Certificate(), audience);
    }
}
