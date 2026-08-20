package com.finco.lab.samlsp.config;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Small helpers for turning PEM text (as pasted into a Render environment variable) into the
 * Java key objects Spring Security wants — plus a throwaway self-signed keypair generator so
 * the app still boots before you have real key material.
 */
public final class PemUtils {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private PemUtils() {
    }

    /**
     * Environment variables cannot hold real newlines everywhere, so we accept three shapes:
     * a proper multi-line PEM, a PEM with literal {@code \n} sequences, or bare base64 with no
     * BEGIN/END armour at all.
     */
    public static String normalise(String pem, String label) {
        String text = pem.replace("\\n", "\n").trim();
        if (text.contains("-----BEGIN")) {
            return text;
        }
        String body = text.replaceAll("\\s", "");
        StringBuilder wrapped = new StringBuilder("-----BEGIN " + label + "-----\n");
        for (int i = 0; i < body.length(); i += 64) {
            wrapped.append(body, i, Math.min(i + 64, body.length())).append('\n');
        }
        return wrapped.append("-----END ").append(label).append("-----\n").toString();
    }

    public static X509Certificate readCertificate(String pem) {
        try {
            String normalised = normalise(pem, "CERTIFICATE");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read the X.509 certificate. "
                    + "Paste the whole PEM block, BEGIN/END lines included.", ex);
        }
    }

    /** Reads PKCS#8 ("BEGIN PRIVATE KEY") or PKCS#1 ("BEGIN RSA PRIVATE KEY") private keys. */
    public static RSAPrivateKey readPrivateKey(String pem) {
        String normalised = normalise(pem, "PRIVATE KEY");
        try (PEMParser parser = new PEMParser(new StringReader(normalised))) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            PrivateKey key;
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                key = converter.getKeyPair(pemKeyPair).getPrivate();
            } else if (parsed instanceof PrivateKeyInfo info) {
                key = converter.getPrivateKey(info);
            } else if (parsed instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new IllegalArgumentException("The private key is passphrase-encrypted. "
                        + "Decrypt it first: openssl pkcs8 -topk8 -nocrypt -in enc.key -out plain.key");
            } else {
                throw new IllegalArgumentException("Unrecognised private key format: "
                        + (parsed == null ? "empty input" : parsed.getClass().getSimpleName()));
            }
            if (!(key instanceof RSAPrivateKey rsa)) {
                throw new IllegalArgumentException("Only RSA private keys are supported by this lab.");
            }
            return rsa;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read the private key PEM.", ex);
        }
    }

    /** Mints a throwaway self-signed RSA keypair — good enough to boot, not good enough to trust. */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate an RSA keypair", ex);
        }
    }

    public static X509Certificate selfSign(KeyPair keyPair, String commonName) {
        try {
            X500Name subject = new X500Name("CN=" + commonName + ", OU=IAM Lab, O=FinCo Lab");
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minus(1, ChronoUnit.DAYS)),
                    Date.from(now.plus(3650, ChronoUnit.DAYS)),
                    subject,
                    keyPair.getPublic());
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(
                            new JcaContentSignerBuilder("SHA256WithRSA")
                                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                    .build(keyPair.getPrivate())));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not self-sign the lab certificate", ex);
        }
    }

    /** Renders a certificate back out as PEM so the frontend can show it for copy-paste into Ping. */
    public static String toPem(X509Certificate certificate) {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----";
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encode the certificate", ex);
        }
    }
}
