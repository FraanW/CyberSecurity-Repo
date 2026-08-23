package com.finco.lab.samlsp.saml;

import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.AssertingPartyMetadata;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One IdP, as described by a SAML 2.0 metadata document.
 *
 * <p><b>What metadata actually is.</b> A federation has exactly four things each side must agree
 * on: <i>who you are</i> (entity ID), <i>where messages go</i> (endpoint URLs), <i>which bindings
 * carry them</i>, and <i>which public keys prove authorship</i>. Metadata is nothing more than
 * those four facts in a standard XML shape, so that two products written by two vendors can
 * exchange them without a human retyping a certificate into a text box. Every "invalid signature"
 * caused by a fat-fingered PEM is a metadata file that somebody chose not to use.</p>
 *
 * <p>Parsing goes through Spring Security's own {@code RelyingPartyRegistrations}, which wraps
 * OpenSAML. That is deliberate: whatever this preview shows you is <i>exactly</i> what the login
 * filter will trust, because it came out of the same parser. A hand-rolled preview that disagreed
 * with the real config would be worse than no preview at all.</p>
 */
public record IdpMetadata(
        String entityId,
        String singleSignOnServiceLocation,
        String singleSignOnServiceBinding,
        String singleLogoutServiceLocation,
        String singleLogoutServiceResponseLocation,
        String singleLogoutServiceBinding,
        boolean wantAuthnRequestsSigned,
        List<CertificateSummary> signingCertificates,
        List<CertificateSummary> encryptionCertificates,
        Boolean documentSigned,
        String validUntil,
        List<String> warnings) {

    /** Thrown when the bytes are not usable SAML metadata. The message is written to be read. */
    public static class ParseException extends RuntimeException {
        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public ParseException(String message) {
            super(message);
        }
    }

    /**
     * Parses every {@code <IDPSSODescriptor>} in the document.
     *
     * <p>Usually there is one. Aggregated federation metadata (InCommon, eduGAIN, and some
     * PingFederate exports that include several connections) can carry many, which is why this
     * returns a list and the UI makes you pick.</p>
     */
    public static List<IdpMetadata> parseAll(byte[] xml) {
        Collection<RelyingPartyRegistration.Builder> builders = builders(xml);
        Boolean signed = documentIsSigned(xml);
        String validUntil = attributeOnRoot(xml, "validUntil");

        List<IdpMetadata> out = new ArrayList<>();
        for (RelyingPartyRegistration.Builder builder : builders) {
            // The builder that comes out of metadata has the asserting party filled in but none of
            // our own fields. Setting throwaway values lets us build it just to read the IdP half
            // back out — the real registration is assembled later, in IdpTrustStore.
            AssertingPartyMetadata party = builder
                    .registrationId("preview")
                    .entityId("preview")
                    .assertionConsumerServiceLocation("https://preview.invalid/acs")
                    .build()
                    .getAssertingPartyMetadata();
            out.add(describe(party, signed, validUntil));
        }
        if (out.isEmpty()) {
            throw new ParseException("That file parsed as XML, but it contains no <IDPSSODescriptor>. "
                    + "You may have exported the *SP* metadata by mistake — this app needs the "
                    + "IdP's metadata, the one PingFederate publishes about itself.");
        }
        return out;
    }

    /**
     * Re-parses the document and returns the Spring builder for one entity ID, ready to have the
     * SP half attached. Kept separate from {@link #parseAll} so the stored XML stays the single
     * source of truth — nothing is cached in a shape that could drift from the file on disk.
     */
    public static RelyingPartyRegistration.Builder builderFor(byte[] xml, String entityId) {
        for (RelyingPartyRegistration.Builder builder : builders(xml)) {
            String candidate = builder
                    .registrationId("probe")
                    .entityId("probe")
                    .assertionConsumerServiceLocation("https://probe.invalid/acs")
                    .build()
                    .getAssertingPartyMetadata()
                    .getEntityId();
            if (candidate.equals(entityId)) {
                return builder;
            }
        }
        throw new ParseException("No IdP with entity ID '" + entityId + "' in this metadata document.");
    }

    private static Collection<RelyingPartyRegistration.Builder> builders(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new ParseException("The metadata file is empty.");
        }
        try {
            return RelyingPartyRegistrations.collectionFromMetadata(new ByteArrayInputStream(xml));
        } catch (Exception ex) {
            throw new ParseException("Could not read this as SAML 2.0 metadata. Expected an "
                    + "<EntityDescriptor> or <EntitiesDescriptor> document. "
                    + "OpenSAML said: " + rootMessage(ex), ex);
        }
    }

    /**
     * Describes an already-configured asserting party — used when the IdP came from environment
     * variables rather than an uploaded file, so that one shape of summary drives the whole UI.
     */
    public static IdpMetadata of(AssertingPartyMetadata party) {
        return describe(party, null, null);
    }

    private static IdpMetadata describe(AssertingPartyMetadata party, Boolean signed, String validUntil) {
        List<CertificateSummary> signing = summarise(party.getVerificationX509Credentials());
        List<CertificateSummary> encryption = summarise(party.getEncryptionX509Credentials());

        List<String> warnings = new ArrayList<>();
        if (signing.isEmpty()) {
            warnings.add("This metadata has no signing certificate (no KeyDescriptor use=\"signing\"). "
                    + "Without one there is nothing to verify assertions against, and every login "
                    + "will fail with invalid_signature.");
        }
        signing.stream().filter(CertificateSummary::expired).forEach(cert -> warnings.add(
                "Signing certificate " + cert.shortFingerprint() + " expired on " + cert.notAfter()
                        + ". PingFederate may still be signing with it, but it is overdue for rotation."));
        signing.stream().filter(CertificateSummary::notYetValid).forEach(cert -> warnings.add(
                "Signing certificate " + cert.shortFingerprint() + " is not valid until " + cert.notBefore()
                        + " — check the clock on this host and on the IdP."));
        if (signing.size() > 1) {
            warnings.add("This IdP publishes " + signing.size() + " signing certificates. That is normal "
                    + "during a rotation: trust all of them and a key change mid-flight cannot lock you out.");
        }
        if (party.getSingleSignOnServiceLocation() != null
                && party.getSingleSignOnServiceLocation().startsWith("http://")) {
            warnings.add("The SSO URL is plain http://. The assertion itself is signed, but the "
                    + "browser would carry it in clear text. Lab-only.");
        }
        if (Boolean.FALSE.equals(signed)) {
            warnings.add("This metadata document is not XML-signed, so nothing proves it really came "
                    + "from your IdP. Fetch it over HTTPS from a host you trust, and check the "
                    + "fingerprints below against the PingFederate console before you rely on it.");
        }

        return new IdpMetadata(
                party.getEntityId(),
                party.getSingleSignOnServiceLocation(),
                urn(party.getSingleSignOnServiceBinding()),
                party.getSingleLogoutServiceLocation(),
                party.getSingleLogoutServiceResponseLocation(),
                urn(party.getSingleLogoutServiceBinding()),
                party.getWantAuthnRequestsSigned(),
                signing,
                encryption,
                signed,
                validUntil,
                warnings);
    }

    private static List<CertificateSummary> summarise(Collection<Saml2X509Credential> credentials) {
        List<CertificateSummary> out = new ArrayList<>();
        for (Saml2X509Credential credential : credentials) {
            X509Certificate certificate = credential.getCertificate();
            if (certificate != null) {
                out.add(CertificateSummary.of(certificate));
            }
        }
        return out;
    }

    private static String urn(Saml2MessageBinding binding) {
        return binding == null ? null : binding.getUrn();
    }

    /**
     * Was the metadata document itself signed? We do not verify that signature — we have no
     * trust anchor for it — but whether one is present changes how much the file is worth, so
     * the UI says which it is rather than quietly implying the file is trustworthy.
     */
    private static boolean documentIsSigned(byte[] xml) {
        try {
            var document = secureDocumentBuilder().parse(new ByteArrayInputStream(xml));
            return document.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature")
                    .getLength() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String attributeOnRoot(byte[] xml, String attribute) {
        try {
            var root = secureDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
            String value = root.getAttribute(attribute);
            return value.isEmpty() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Metadata arrives from outside, so it is parsed with DTDs and external entities switched
     * off. An XML parser that will fetch a URL named in the document is an SSRF gadget, and a
     * metadata upload form is exactly where somebody would try one.
     */
    static javax.xml.parsers.DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    /**
     * The imported values re-expressed as the environment variables that would produce the same
     * configuration. Import survives a restart but not a redeploy; pasting these into Render is
     * how you make it permanent.
     */
    public Map<String, String> asEnvironmentVariables() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("LAB_SAML_IDP_ENTITY_ID", entityId);
        env.put("LAB_SAML_IDP_SSO_URL", singleSignOnServiceLocation);
        if (singleSignOnServiceBinding != null && singleSignOnServiceBinding.endsWith("HTTP-POST")) {
            env.put("LAB_SAML_IDP_SSO_BINDING", "POST");
        }
        if (singleLogoutServiceLocation != null) {
            env.put("LAB_SAML_IDP_SLO_URL", singleLogoutServiceLocation);
        }
        return env;
    }

    /** True when this document alone is enough to run a login. */
    public boolean isUsable() {
        return entityId != null && !entityId.isBlank()
                && singleSignOnServiceLocation != null && !singleSignOnServiceLocation.isBlank()
                && !signingCertificates.isEmpty();
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
