package com.finco.lab.samlsp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the lab needs to talk to a PingFederate IdP, all driven by environment variables
 * so no server URL, entity ID, or certificate is ever committed to git.
 *
 * <p>Two ways to point at PingFederate:</p>
 * <ol>
 *   <li><b>Metadata URL</b> — set {@code idpMetadataUrl}. Spring pulls the IdP entity ID,
 *       SSO URL and signing certificate out of the metadata for you. Easiest.</li>
 *   <li><b>Manual</b> — set {@code idpEntityId} + {@code idpSsoUrl} + {@code idpCertificate}
 *       by hand. Use this when the metadata endpoint is not reachable from the internet.</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "lab.saml")
public class SamlProperties {

    /** The registration ID. It appears in the ACS URL, so keep it stable once onboarded. */
    private String registrationId = "pingfed";

    /**
     * Public HTTPS base URL of this app (e.g. {@code https://pingfed-saml-sp.onrender.com}).
     * Leave blank locally — Spring then derives it per-request.
     */
    private String publicBaseUrl = "";

    /** SP entity ID. Blank = use this app's metadata URL as the entity ID (a common default). */
    private String spEntityId = "";

    /** PingFederate SAML metadata URL, e.g. https://pf.example.com/pf/federation_metadata.ping?PartnerSpId=... */
    private String idpMetadataUrl = "";

    /** PingFederate's SAML entity ID (the "Issuer" value in its assertions). */
    private String idpEntityId = "";

    /** PingFederate's SSO endpoint, usually https://pf.example.com/idp/SSO.saml2 */
    private String idpSsoUrl = "";

    /** REDIRECT or POST — the binding used to send the AuthnRequest. */
    private String idpSsoBinding = "REDIRECT";

    /** PingFederate's Single Logout endpoint, usually https://pf.example.com/idp/SLO.saml2 */
    private String idpSloUrl = "";

    /** REDIRECT or POST — the binding used for logout messages. */
    private String idpSloBinding = "REDIRECT";

    /** PingFederate's assertion-signing certificate, PEM encoded. */
    private String idpCertificate = "";

    /** Sign our AuthnRequests? PingFederate SP connections often require this. */
    private boolean signAuthnRequests = true;

    /** SP private key (PEM, PKCS#8 or PKCS#1). Blank = mint a throwaway one at startup. */
    private String spPrivateKey = "";

    /** SP certificate (PEM) matching {@link #spPrivateKey}. Blank = mint a throwaway one. */
    private String spCertificate = "";

    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = normaliseBaseUrl(publicBaseUrl); }

    /**
     * Accepts either a full URL or a bare hostname, because Render's {@code fromService} block
     * supplies just {@code my-app.onrender.com}. Anything reachable over the public internet is
     * HTTPS, so a bare host gets that scheme.
     */
    static String normaliseBaseUrl(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("/+$", "");
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    public String getSpEntityId() { return spEntityId; }
    public void setSpEntityId(String spEntityId) { this.spEntityId = spEntityId; }

    public String getIdpMetadataUrl() { return idpMetadataUrl; }
    public void setIdpMetadataUrl(String idpMetadataUrl) { this.idpMetadataUrl = idpMetadataUrl; }

    public String getIdpEntityId() { return idpEntityId; }
    public void setIdpEntityId(String idpEntityId) { this.idpEntityId = idpEntityId; }

    public String getIdpSsoUrl() { return idpSsoUrl; }
    public void setIdpSsoUrl(String idpSsoUrl) { this.idpSsoUrl = idpSsoUrl; }

    public String getIdpSsoBinding() { return idpSsoBinding; }
    public void setIdpSsoBinding(String idpSsoBinding) { this.idpSsoBinding = idpSsoBinding; }

    public String getIdpSloUrl() { return idpSloUrl; }
    public void setIdpSloUrl(String idpSloUrl) { this.idpSloUrl = idpSloUrl; }

    public String getIdpSloBinding() { return idpSloBinding; }
    public void setIdpSloBinding(String idpSloBinding) { this.idpSloBinding = idpSloBinding; }

    public String getIdpCertificate() { return idpCertificate; }
    public void setIdpCertificate(String idpCertificate) { this.idpCertificate = idpCertificate; }

    public boolean isSignAuthnRequests() { return signAuthnRequests; }
    public void setSignAuthnRequests(boolean signAuthnRequests) { this.signAuthnRequests = signAuthnRequests; }

    public String getSpPrivateKey() { return spPrivateKey; }
    public void setSpPrivateKey(String spPrivateKey) { this.spPrivateKey = spPrivateKey; }

    public String getSpCertificate() { return spCertificate; }
    public void setSpCertificate(String spCertificate) { this.spCertificate = spCertificate; }

    /** True when we have enough config to actually reach a PingFederate IdP. */
    public boolean isConfigured() {
        boolean viaMetadata = hasText(idpMetadataUrl);
        boolean viaManual = hasText(idpEntityId) && hasText(idpSsoUrl) && hasText(idpCertificate);
        return viaMetadata || viaManual;
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
