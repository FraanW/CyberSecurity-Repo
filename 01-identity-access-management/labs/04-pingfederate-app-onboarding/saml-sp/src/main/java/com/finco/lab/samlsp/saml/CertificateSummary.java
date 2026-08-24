package com.finco.lab.samlsp.saml;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HexFormat;
import java.security.MessageDigest;

/**
 * A certificate, described the way a human debugging SSO needs to see it.
 *
 * <p><b>Why the fingerprint matters more than anything else here.</b> "Invalid signature" almost
 * always means <i>the IdP signed with one private key and we checked with a different public key</i>.
 * Two certificates can have identical subjects, identical issuers and overlapping validity windows
 * and still be different keys — a rotation leaves you holding a twin that looks right in every
 * field you would normally read. The SHA-256 fingerprint is a hash of the whole encoded
 * certificate, so it is the one field that cannot lie: same fingerprint means the same bytes,
 * therefore the same public key.</p>
 *
 * <p>Compare fingerprints, not subject names. That single habit resolves most signature tickets.</p>
 */
public record CertificateSummary(
        String subject,
        String issuer,
        String serialNumber,
        String notBefore,
        String notAfter,
        String sha256Fingerprint,
        String sha1Fingerprint,
        String keyAlgorithm,
        Integer keySizeBits,
        boolean selfSigned,
        boolean expired,
        boolean notYetValid) {

    public static CertificateSummary of(X509Certificate certificate) {
        Instant now = Instant.now();
        Integer bits = (certificate.getPublicKey() instanceof RSAPublicKey rsa)
                ? rsa.getModulus().bitLength()
                : null;
        return new CertificateSummary(
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber().toString(16),
                certificate.getNotBefore().toInstant().toString(),
                certificate.getNotAfter().toInstant().toString(),
                fingerprint(certificate, "SHA-256"),
                fingerprint(certificate, "SHA-1"),
                certificate.getPublicKey().getAlgorithm(),
                bits,
                certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()),
                certificate.getNotAfter().toInstant().isBefore(now),
                certificate.getNotBefore().toInstant().isAfter(now));
    }

    /**
     * The same value {@code openssl x509 -fingerprint -sha256 -noout} prints, and the same value
     * the PingFederate console shows next to a signing certificate. Colon-separated uppercase hex
     * so you can eyeball it against either without reformatting.
     */
    static String fingerprint(X509Certificate certificate, String algorithm) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(certificate.getEncoded());
            String hex = HexFormat.of().withUpperCase().formatHex(digest);
            StringBuilder out = new StringBuilder(hex.length() + hex.length() / 2);
            for (int i = 0; i < hex.length(); i += 2) {
                if (i > 0) {
                    out.append(':');
                }
                out.append(hex, i, i + 2);
            }
            return out.toString();
        } catch (Exception ex) {
            return "unavailable (" + ex.getMessage() + ")";
        }
    }

    /** Short form for log lines and table cells, e.g. {@code AB:CD:EF:01…}. */
    public String shortFingerprint() {
        return sha256Fingerprint.length() > 11 ? sha256Fingerprint.substring(0, 11) + "…" : sha256Fingerprint;
    }
}
