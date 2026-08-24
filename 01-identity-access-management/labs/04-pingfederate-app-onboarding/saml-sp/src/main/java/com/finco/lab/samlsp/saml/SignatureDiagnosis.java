package com.finco.lab.samlsp.saml;

import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers the only question that matters after an {@code invalid_signature}:
 * <b>which key signed this, and is it a key we trust?</b>
 *
 * <h2>How signature verification actually works</h2>
 *
 * <p>An RSA keypair is two numbers with one property: what you transform with the private key,
 * only the matching public key can untransform, and vice versa. Nothing else about them is
 * special. Federation uses that property in one direction only — the IdP keeps the private key
 * secret and hands out the public one, so a value that verifies against the public key must have
 * been produced by whoever holds the private key. That is the entire trust model: <i>a signature
 * is a claim of authorship that only one party could have made</i>.</p>
 *
 * <p>Signing an XML document adds two steps around that:</p>
 * <ol>
 *   <li><b>Canonicalise, then digest.</b> The signed element is rewritten into a byte-exact
 *       normal form (Exclusive C14N: attribute order fixed, redundant namespace declarations
 *       dropped, whitespace rules pinned) and hashed with SHA-256. Canonicalisation exists
 *       because two XML documents can be semantically identical and byte-wise different, and a
 *       hash cares only about bytes.</li>
 *   <li><b>Sign the digest.</b> The hash — not the document — is what RSA operates on. The
 *       result goes into {@code <ds:SignatureValue>}, and the digest goes into
 *       {@code <ds:DigestValue>}.</li>
 * </ol>
 *
 * <p>The SP replays that: canonicalise the same element, hash it, compare against
 * {@code DigestValue}, then verify {@code SignatureValue} with the IdP's <i>public</i> key. Two
 * distinct ways to fail, and they mean opposite things:</p>
 *
 * <table><caption>What each failure tells you</caption>
 *   <tr><th>Digest mismatch</th><td>the bytes changed after signing — something rewrote the XML</td></tr>
 *   <tr><th>Signature mismatch</th><td>the bytes are intact, but the public key is the wrong one</td></tr>
 * </table>
 *
 * <p><b>In practice the second is nearly always it</b>, and nearly always for the same reason: the
 * SP holds a certificate that is <i>not</i> the one the IdP is currently signing with. This class
 * proves or disproves that in one shot — it pulls the certificate out of the response's own
 * {@code <ds:KeyInfo>} (IdPs helpfully include the certificate they signed with) and compares its
 * SHA-256 fingerprint against every certificate the SP trusts.</p>
 *
 * <p><b>Note the fingerprint is not itself a trust decision.</b> Anyone can put any certificate in
 * KeyInfo; a matching fingerprint tells you the key <i>lines up</i> with what you configured, and
 * a non-matching one tells you the key you configured is not in play. The actual verification is
 * still done by Spring Security against the configured trust anchors, never against KeyInfo. This
 * is a diagnostic, not a second verifier.</p>
 */
public record SignatureDiagnosis(
        Verdict verdict,
        String summary,
        List<String> nextSteps,
        List<String> signedElements,
        List<SignatureDetail> signatures,
        List<CertificateSummary> certificatesInResponse,
        List<CertificateSummary> certificatesWeTrust,
        List<String> otherChecks) {

    public enum Verdict {
        /** The signing key in the response is not one we trust. The classic cause. */
        KEY_MISMATCH,
        /** The key lines up, so the failure is about the bytes, not the key. */
        KEY_MATCHES,
        /** Nothing in the message is signed at all. */
        NOT_SIGNED,
        /** The IdP did not include its certificate, so no automatic comparison is possible. */
        NO_KEY_IN_RESPONSE,
        /** We hold no trusted certificate, so nothing could ever verify. */
        NOTHING_TRUSTED,
        /** The response would not parse. */
        UNREADABLE
    }

    /** One {@code <ds:Signature>}, described by the algorithms it declares. */
    public record SignatureDetail(
            String overElement,
            String signatureAlgorithm,
            String digestAlgorithm,
            String canonicalizationMethod,
            boolean includesCertificate) {
    }

    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String SAML = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";

    /**
     * @param responseXml the decoded SAML Response XML
     * @param registration the live registration, for the certificates we trust and the URLs we expect
     */
    public static SignatureDiagnosis of(String responseXml, RelyingPartyRegistration registration) {
        List<CertificateSummary> trusted = new ArrayList<>();
        for (Saml2X509Credential credential : registration.getAssertingPartyMetadata()
                .getVerificationX509Credentials()) {
            if (credential.getCertificate() != null) {
                trusted.add(CertificateSummary.of(credential.getCertificate()));
            }
        }

        Document document;
        try {
            document = IdpMetadata.secureDocumentBuilder()
                    .parse(new ByteArrayInputStream(responseXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return new SignatureDiagnosis(Verdict.UNREADABLE,
                    "The response did not parse as XML: " + ex.getMessage(),
                    List.of("Check that the SAMLResponse form field was base64 of XML, not already decoded, "
                            + "and that nothing truncated it."),
                    List.of(), List.of(), List.of(), trusted, List.of());
        }

        List<SignatureDetail> signatures = signatures(document);
        List<String> signedElements = signatures.stream().map(SignatureDetail::overElement).toList();
        List<CertificateSummary> inResponse = certificatesIn(document);
        List<String> checks = crossChecks(document, registration);

        if (trusted.isEmpty()) {
            return new SignatureDiagnosis(Verdict.NOTHING_TRUSTED,
                    "This app holds no IdP signing certificate at all, so no signature could ever verify.",
                    List.of("Import the IdP metadata file at Step 2, or set LAB_SAML_IDP_CERTIFICATE."),
                    signedElements, signatures, inResponse, trusted, checks);
        }
        if (signatures.isEmpty()) {
            return new SignatureDiagnosis(Verdict.NOT_SIGNED,
                    "Nothing in this response is signed — there is no <ds:Signature> element anywhere.",
                    List.of("In PingFederate: SP Connection → Credentials → Digital Signature Settings — "
                                    + "pick a signing certificate and RSA SHA-256.",
                            "Also check Browser SSO → Protocol Settings → Signature Policy; "
                                    + "'Always sign the assertion' is the setting you want."),
                    signedElements, signatures, inResponse, trusted, checks);
        }

        Set<String> trustedFingerprints = new LinkedHashSet<>();
        trusted.forEach(certificate -> trustedFingerprints.add(certificate.sha256Fingerprint()));

        if (inResponse.isEmpty()) {
            return new SignatureDiagnosis(Verdict.NO_KEY_IN_RESPONSE,
                    "The response is signed but carries no certificate in <ds:KeyInfo>, so this app "
                            + "cannot tell you which key was used — only that the one we hold did not verify it.",
                    List.of("In the PingFederate console open the SP connection → Credentials → "
                                    + "Digital Signature Settings and note the certificate's SHA-256 fingerprint.",
                            "Compare it by eye with the fingerprint under 'certificates we trust' below.",
                            "Ping can be told to include its certificate: tick 'Include the certificate in "
                                    + "the <KeyInfo> element' in the same screen. That makes this diagnosis automatic."),
                    signedElements, signatures, inResponse, trusted, checks);
        }

        boolean match = inResponse.stream()
                .anyMatch(certificate -> trustedFingerprints.contains(certificate.sha256Fingerprint()));

        if (!match) {
            CertificateSummary used = inResponse.get(0);
            return new SignatureDiagnosis(Verdict.KEY_MISMATCH,
                    "Key mismatch — this is your failure. The IdP signed with the certificate "
                            + "fingerprinted " + used.sha256Fingerprint() + " (subject " + used.subject()
                            + "). This app trusts " + trusted.size() + " certificate(s), and that one is "
                            + "not among them. The signature is almost certainly valid; we are simply "
                            + "holding the wrong public key.",
                    List.of("Fix it in one step: Step 2 → import the IdP's metadata file. It carries the "
                                    + "certificate the IdP is actually using, so the fingerprints line up by construction.",
                            "If you configured by hand, LAB_SAML_IDP_CERTIFICATE is stale — most often "
                                    + "because the key was rotated, or because the certificate pasted in was "
                                    + "PingFederate's SSL/server certificate rather than its SAML signing certificate. "
                                    + "They are different certificates on the same host.",
                            "During a rotation, trust both: this app accepts several concatenated PEM blocks "
                                    + "in LAB_SAML_IDP_CERTIFICATE, and metadata import trusts every signing "
                                    + "certificate the document publishes."),
                    signedElements, signatures, inResponse, trusted, checks);
        }

        return new SignatureDiagnosis(Verdict.KEY_MATCHES,
                "The certificate in the response matches one we trust, so the key is not the problem. "
                        + "A signature failure with a matching key means the signed bytes changed between "
                        + "signing and verification — a digest mismatch rather than a signature mismatch.",
                List.of("Look for anything that rewrites the XML in transit: a proxy or WAF that "
                                + "re-encodes the POST body, or a copy-paste through an editor that "
                                + "re-indented it. Canonicalisation is byte-exact; a single added newline "
                                + "inside the signed element breaks the digest.",
                        "Check the signature algorithm below. If PingFederate is signing with RSA-SHA1 "
                                + "and the platform has SHA-1 disabled, the failure looks like a bad "
                                + "signature. Move the connection to RSA SHA-256.",
                        "If the assertion is encrypted, confirm we decrypted it with the right key — a "
                                + "mis-decrypted assertion produces garbage that fails the digest."),
                signedElements, signatures, inResponse, trusted, checks);
    }

    private static List<SignatureDetail> signatures(Document document) {
        List<SignatureDetail> details = new ArrayList<>();
        NodeList nodes = document.getElementsByTagNameNS(DS, "Signature");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element signature = (Element) nodes.item(i);
            Node parent = signature.getParentNode();
            String over = parent instanceof Element element ? element.getLocalName() : "unknown";
            details.add(new SignatureDetail(
                    over,
                    algorithmOf(signature, "SignatureMethod"),
                    algorithmOf(signature, "DigestMethod"),
                    algorithmOf(signature, "CanonicalizationMethod"),
                    signature.getElementsByTagNameNS(DS, "X509Certificate").getLength() > 0));
        }
        return details;
    }

    private static String algorithmOf(Element signature, String localName) {
        NodeList nodes = signature.getElementsByTagNameNS(DS, localName);
        return nodes.getLength() == 0 ? null : ((Element) nodes.item(0)).getAttribute("Algorithm");
    }

    private static List<CertificateSummary> certificatesIn(Document document) {
        List<CertificateSummary> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        NodeList nodes = document.getElementsByTagNameNS(DS, "X509Certificate");
        for (int i = 0; i < nodes.getLength(); i++) {
            String base64 = nodes.item(i).getTextContent().replaceAll("\\s", "");
            if (base64.isEmpty() || !seen.add(base64)) {
                continue;
            }
            try {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
                out.add(CertificateSummary.of(certificate));
            } catch (Exception ignored) {
                // A KeyInfo we cannot decode is itself worth nothing; skip it rather than fail.
            }
        }
        return out;
    }

    /**
     * The other four fields that reject an otherwise-perfect assertion. Reported alongside the
     * signature verdict because they are cheap to check and, once you are already debugging, you
     * want to know about the next failure before you go round again.
     */
    private static List<String> crossChecks(Document document, RelyingPartyRegistration registration) {
        List<String> checks = new ArrayList<>();

        String issuer = textOf(document, SAML, "Issuer");
        String expectedIssuer = registration.getAssertingPartyMetadata().getEntityId();
        checks.add(compare("Issuer vs the IdP entity ID we trust", issuer, expectedIssuer));

        String audience = textOf(document, SAML, "Audience");
        checks.add(compare("Audience vs our SP entity ID", audience, registration.getEntityId()));

        String destination = attributeOf(document, PROTOCOL, "Response", "Destination");
        checks.add(compare("Destination vs our ACS URL", destination,
                registration.getAssertionConsumerServiceLocation()));

        String notOnOrAfter = attributeOf(document, SAML, "Conditions", "NotOnOrAfter");
        if (notOnOrAfter != null) {
            try {
                Instant expiry = Instant.parse(notOnOrAfter);
                checks.add(expiry.isBefore(Instant.now())
                        ? "✗ Conditions expired at " + notOnOrAfter + " and it is now " + Instant.now()
                          + " — either the assertion sat in the browser too long, or the clocks differ. "
                          + "Check NTP on both hosts before you blame anything else."
                        : "✓ Conditions still valid until " + notOnOrAfter);
            } catch (Exception ex) {
                checks.add("? Conditions NotOnOrAfter is '" + notOnOrAfter + "', which is not a timestamp.");
            }
        }

        String status = attributeOf(document, PROTOCOL, "StatusCode", "Value");
        if (status != null && !status.endsWith(":Success")) {
            checks.add("✗ StatusCode is " + status + " — the IdP refused before signing anything "
                    + "meaningful. Read PingFederate's audit.log for the reason.");
        }
        return checks;
    }

    private static String compare(String label, String actual, String expected) {
        if (actual == null) {
            return "? " + label + ": not present in the response.";
        }
        return actual.equals(expected)
                ? "✓ " + label + ": " + actual
                : "✗ " + label + ": response says '" + actual + "', we expect '" + expected + "'.";
    }

    private static String textOf(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String attributeOf(Document document, String namespace, String localName, String attribute) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = ((Element) nodes.item(0)).getAttribute(attribute);
        return value.isEmpty() ? null : value;
    }
}
