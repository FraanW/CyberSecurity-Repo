package com.finco.lab.oauthclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All PingFederate coordinates, supplied as environment variables so no hostname, client ID or
 * secret is ever committed.
 *
 * <p>Two ways to point at the server:</p>
 * <ol>
 *   <li><b>Discovery</b> — set {@code issuerUri}. The app fetches
 *       {@code {issuer}/.well-known/openid-configuration} and learns every endpoint. Easiest.</li>
 *   <li><b>Manual</b> — set {@code authorizationUri}, {@code tokenUri} and friends by hand. Use
 *       this when discovery is blocked, or when PingFederate's issuer value does not match the
 *       host you reach it on (a very common cause of "issuer mismatch" errors).</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "lab.oauth")
public class PingFedProperties {

    /** Registration ID — it appears in the redirect URI, so keep it stable once onboarded. */
    private String registrationId = "pingfed";

    /** Public HTTPS base URL of this app. Leave blank locally. */
    private String publicBaseUrl = "";

    /** PingFederate's OIDC issuer, e.g. https://pf.example.com. Blank = configure manually. */
    private String issuerUri = "";

    private String authorizationUri = "";
    private String tokenUri = "";
    private String jwkSetUri = "";
    private String userInfoUri = "";

    /** RFC 7662 token introspection endpoint — the only way to read PingFederate reference tokens. */
    private String introspectionUri = "";

    /** OIDC RP-initiated logout endpoint, usually https://pf.example.com/idp/startSLO.ping */
    private String endSessionUri = "";

    /** The OAuth client you created in PingFederate for the Authorization Code flow. */
    private String clientId = "";
    private String clientSecret = "";
    private List<String> scopes = List.of("openid", "profile", "email");

    /** Client authentication method: client_secret_basic, client_secret_post, or none (public client). */
    private String clientAuthenticationMethod = "client_secret_basic";

    /**
     * Show full token values in the API responses. True is what makes this a teaching lab — you
     * can copy the access token and call a real API with it. Set it to false the moment you point
     * this app at anything that is not a throwaway test client.
     */
    private boolean revealTokens = true;

    /** Send PKCE on the Authorization Code flow. Leave on — PingFederate supports it and it is safer. */
    private boolean usePkce = true;

    /**
     * A second PingFederate client, restricted to the Client Credentials grant (machine-to-machine).
     * Blank = reuse the Authorization Code client above.
     */
    private String machineClientId = "";
    private String machineClientSecret = "";
    private List<String> machineScopes = List.of();

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

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getAuthorizationUri() { return authorizationUri; }
    public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }

    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }

    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }

    public String getUserInfoUri() { return userInfoUri; }
    public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }

    public String getIntrospectionUri() { return introspectionUri; }
    public void setIntrospectionUri(String introspectionUri) { this.introspectionUri = introspectionUri; }

    public String getEndSessionUri() { return endSessionUri; }
    public void setEndSessionUri(String endSessionUri) { this.endSessionUri = endSessionUri; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public String getClientAuthenticationMethod() { return clientAuthenticationMethod; }
    public void setClientAuthenticationMethod(String m) { this.clientAuthenticationMethod = m; }

    public boolean isRevealTokens() { return revealTokens; }
    public void setRevealTokens(boolean revealTokens) { this.revealTokens = revealTokens; }

    public boolean isUsePkce() { return usePkce; }
    public void setUsePkce(boolean usePkce) { this.usePkce = usePkce; }

    public String getMachineClientId() { return machineClientId; }
    public void setMachineClientId(String machineClientId) { this.machineClientId = machineClientId; }

    public String getMachineClientSecret() { return machineClientSecret; }
    public void setMachineClientSecret(String s) { this.machineClientSecret = s; }

    public List<String> getMachineScopes() { return machineScopes; }
    public void setMachineScopes(List<String> machineScopes) { this.machineScopes = machineScopes; }

    /** The machine-to-machine client falls back to the interactive one when not set separately. */
    public String effectiveMachineClientId() {
        return hasText(machineClientId) ? machineClientId : clientId;
    }

    public String effectiveMachineClientSecret() {
        return hasText(machineClientId) ? machineClientSecret : clientSecret;
    }

    /** True when we have enough to at least attempt an Authorization Code flow. */
    public boolean isConfigured() {
        boolean endpoints = hasText(issuerUri) || (hasText(authorizationUri) && hasText(tokenUri));
        return endpoints && hasText(clientId);
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
