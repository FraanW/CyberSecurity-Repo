package com.finco.lab.oauthclient.web;

import com.finco.lab.oauthclient.config.ClientRegistrationConfig;
import com.finco.lab.oauthclient.config.PingFedProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The lab's API. Every endpoint answers one onboarding question — what did PingFederate issue,
 * what is inside it, and what happens when I ask for another one?
 */
@RestController
@RequestMapping("/api")
public class OauthApiController {

    private final PingFedProperties properties;
    private final ClientRegistrationRepository registrations;
    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final PingFedTokenClient tokenClient;
    private final Instant startedAt = Instant.now();

    public OauthApiController(PingFedProperties properties,
                              ClientRegistrationRepository registrations,
                              OAuth2AuthorizedClientRepository authorizedClients,
                              PingFedTokenClient tokenClient) {
        this.properties = properties;
        this.registrations = registrations;
        this.authorizedClients = authorizedClients;
        this.tokenClient = tokenClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "pingfed-oauth-client",
                "role", "OAuth 2.0 / OIDC client (relying party)",
                "startedAt", startedAt.toString());
    }

    @GetMapping("/public/ping")
    public Map<String, Object> ping() {
        return Map.of("pong", true, "authenticated", false);
    }

    /**
     * The onboarding cheat sheet: the redirect URI to whitelist in PingFederate, the endpoints
     * we resolved, and which settings are still missing.
     */
    @GetMapping("/config")
    public Map<String, Object> config(HttpServletRequest request) {
        ClientRegistration client = registrations.findByRegistrationId(properties.getRegistrationId());
        ClientRegistration machine = registrations.findByRegistrationId(ClientRegistrationConfig.MACHINE_REGISTRATION_ID);
        var provider = client.getProviderDetails();

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("clientId", client.getClientId());
        app.put("clientSecretConfigured", PingFedProperties.hasText(client.getClientSecret()));
        app.put("clientAuthenticationMethod", client.getClientAuthenticationMethod().getValue());
        app.put("redirectUri", resolveRedirectUri(request, client));
        app.put("scopes", client.getScopes());
        app.put("pkce", properties.isUsePkce());
        app.put("loginUrl", absolute(request, "/oauth2/authorization/" + properties.getRegistrationId()));

        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("issuer", provider.getIssuerUri());
        endpoints.put("authorizationEndpoint", provider.getAuthorizationUri());
        endpoints.put("tokenEndpoint", provider.getTokenUri());
        endpoints.put("jwksUri", provider.getJwkSetUri());
        endpoints.put("userInfoEndpoint", provider.getUserInfoEndpoint().getUri());
        endpoints.put("introspectionEndpoint", properties.getIntrospectionUri());
        endpoints.put("endSessionEndpoint", provider.getConfigurationMetadata().get("end_session_endpoint"));
        endpoints.put("resolvedVia", PingFedProperties.hasText(properties.getIssuerUri())
                ? "OIDC discovery (issuer URI)" : "explicit settings");

        Map<String, Object> machineClient = new LinkedHashMap<>();
        machineClient.put("clientId", machine.getClientId());
        machineClient.put("scopes", machine.getScopes());
        machineClient.put("tokenEndpoint", machine.getProviderDetails().getTokenUri());
        machineClient.put("sharesTheInteractiveClient",
                !PingFedProperties.hasText(properties.getMachineClientId()));

        return Map.of(
                "configured", properties.isConfigured(),
                "registrationId", properties.getRegistrationId(),
                "revealTokens", properties.isRevealTokens(),
                "client", app,
                "endpoints", endpoints,
                "machineClient", machineClient,
                "missing", missingSettings());
    }

    /**
     * Who you are — whichever way you logged in.
     *
     * <p>{@code loginType: local} means this app checked your password itself. {@code oidc} means
     * it never saw a password: PingFederate authenticated you and handed over a signed ID token.</p>
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            return Map.of(
                    "authenticated", true,
                    "loginType", "local",
                    "subject", authentication.getName(),
                    "authorities", authentication.getAuthorities().stream().map(Object::toString).toList(),
                    "note", "You are logged in with this app's own username and password. "
                            + "No tokens were issued, because no authorization server was involved. "
                            + "Onboard the app into PingFederate and log in again to see them.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("loginType", "oidc");
        body.put("subject", user.getSubject());
        body.put("name", user.getFullName());
        body.put("email", user.getEmail());
        body.put("issuer", String.valueOf(user.getIssuer()));
        body.put("audience", user.getAudience());
        body.put("issuedAt", String.valueOf(user.getIssuedAt()));
        body.put("expiresAt", String.valueOf(user.getExpiresAt()));
        body.put("authorities", user.getAuthorities().stream().map(Object::toString).toList());
        // Sorted so the same claim sits in the same row every time you reload.
        body.put("idTokenClaims", new TreeMap<>(user.getIdToken().getClaims()));
        return body;
    }

    /**
     * Every token in the session, decoded. This is where you find out whether your client's
     * Access Token Manager mints JWTs or reference tokens.
     */
    @GetMapping("/tokens")
    public Map<String, Object> tokens(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            return localLoginNotice();
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                properties.getRegistrationId(), authentication, request);
        if (client == null) {
            return Map.of("error", "no_authorized_client",
                    "error_description", "No token is stored for this session. Log in again.");
        }

        OAuth2AccessToken accessToken = client.getAccessToken();
        OAuth2RefreshToken refreshToken = client.getRefreshToken();

        Map<String, Object> access = new LinkedHashMap<>(TokenInspector.inspect(accessToken.getTokenValue()));
        access.put("type", accessToken.getTokenType().getValue());
        access.put("scopes", accessToken.getScopes());
        access.put("issuedAt", String.valueOf(accessToken.getIssuedAt()));
        access.put("expiresAt", String.valueOf(accessToken.getExpiresAt()));
        if (properties.isRevealTokens()) {
            access.put("value", accessToken.getTokenValue());
        }

        Map<String, Object> id = new LinkedHashMap<>(TokenInspector.inspect(user.getIdToken().getTokenValue()));
        if (properties.isRevealTokens()) {
            id.put("value", user.getIdToken().getTokenValue());
        }

        Map<String, Object> refresh = new LinkedHashMap<>();
        refresh.put("present", refreshToken != null);
        if (refreshToken != null) {
            refresh.put("preview", TokenInspector.preview(refreshToken.getTokenValue()));
            refresh.put("issuedAt", String.valueOf(refreshToken.getIssuedAt()));
            refresh.put("expiresAt", String.valueOf(refreshToken.getExpiresAt()));
            if (properties.isRevealTokens()) {
                refresh.put("value", refreshToken.getTokenValue());
            }
        } else {
            refresh.put("note", "No refresh token. Either 'offline_access' was not in your scopes, "
                    + "or the client's grant settings in PingFederate do not allow Refresh Token.");
        }

        return Map.of("accessToken", access, "idToken", id, "refreshToken", refresh);
    }

    /**
     * Calls PingFederate's UserInfo endpoint live, with the access token we hold.
     * A 401 here almost always means the token lacks the {@code openid} scope.
     */
    @GetMapping("/userinfo")
    public Map<String, Object> userInfo(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof OidcUser)) {
            return localLoginNotice();
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                properties.getRegistrationId(), authentication, request);
        if (client == null) {
            return Map.of("error", "no_authorized_client");
        }
        ClientRegistration registration = registrations.findByRegistrationId(properties.getRegistrationId());
        String uri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        if (!PingFedProperties.hasText(uri)) {
            return Map.of("error", "endpoint_not_configured",
                    "error_description", "No UserInfo endpoint. Set LAB_OAUTH_USERINFO_URI or use discovery.");
        }
        return tokenClient.userInfo(uri, client.getAccessToken().getTokenValue());
    }

    /**
     * Exchanges the refresh token for a fresh access token and stores the result, so
     * {@code /api/tokens} immediately reflects it.
     *
     * <p>Watch the refresh token value across two calls: if it changes, PingFederate has
     * <b>rotation</b> switched on — a reused old token then invalidates the whole chain.</p>
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(Authentication authentication,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        if (!(authentication.getPrincipal() instanceof OidcUser)) {
            return localLoginNotice();
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                properties.getRegistrationId(), authentication, request);
        if (client == null || client.getRefreshToken() == null) {
            return Map.of("error", "no_refresh_token",
                    "error_description", "This session has no refresh token to exchange. "
                            + "Add 'offline_access' to LAB_OAUTH_SCOPES and allow the Refresh Token "
                            + "grant on the client in PingFederate.");
        }

        ClientRegistration registration = client.getClientRegistration();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", client.getRefreshToken().getTokenValue());

        Map<String, Object> tokenResponse = tokenClient.token(
                registration.getProviderDetails().getTokenUri(),
                form,
                registration.getClientId(),
                registration.getClientSecret(),
                registration.getClientAuthenticationMethod());

        if (tokenResponse.get("access_token") instanceof String newAccessToken) {
            storeRefreshedTokens(client, tokenResponse, newAccessToken, authentication, request, response);
        }
        return redact(tokenResponse);
    }

    /**
     * The Client Credentials grant: one service asking for a token in its own name. No user,
     * no browser, no refresh token. Needs no login here, which is exactly the point.
     */
    @PostMapping("/client-credentials")
    public Map<String, Object> clientCredentials() {
        ClientRegistration machine = registrations.findByRegistrationId(
                ClientRegistrationConfig.MACHINE_REGISTRATION_ID);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        // A registration with no scopes returns null here, not an empty set. Asking for no scope
        // at all is valid — PingFederate then applies the client's default scopes.
        Set<String> machineScopes = machine.getScopes();
        if (machineScopes != null && !machineScopes.isEmpty()) {
            form.add("scope", String.join(" ", machineScopes));
        }

        Map<String, Object> tokenResponse = tokenClient.token(
                machine.getProviderDetails().getTokenUri(),
                form,
                machine.getClientId(),
                machine.getClientSecret(),
                machine.getClientAuthenticationMethod());

        Map<String, Object> result = new LinkedHashMap<>(redact(tokenResponse));
        if (tokenResponse.get("access_token") instanceof String token) {
            result.put("decoded", TokenInspector.inspect(token));
        }
        return result;
    }

    /**
     * RFC 7662 introspection — hands the access token back to PingFederate and asks
     * "is this still valid, and whose is it?" This is how a resource server validates a
     * reference token.
     */
    @PostMapping("/introspect")
    public Map<String, Object> introspect(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof OidcUser)) {
            return localLoginNotice();
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                properties.getRegistrationId(), authentication, request);
        if (client == null) {
            return Map.of("error", "no_authorized_client");
        }
        ClientRegistration registration = client.getClientRegistration();
        return tokenClient.introspect(
                properties.getIntrospectionUri(),
                client.getAccessToken().getTokenValue(),
                registration.getClientId(),
                registration.getClientSecret(),
                registration.getClientAuthenticationMethod());
    }

    // ---------------------------------------------------------------- helpers

    private void storeRefreshedTokens(OAuth2AuthorizedClient current,
                                      Map<String, Object> tokenResponse,
                                      String newAccessToken,
                                      Authentication authentication,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        Instant issuedAt = Instant.now();
        long expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.longValue() : 3600L;

        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                newAccessToken,
                issuedAt,
                issuedAt.plusSeconds(expiresIn),
                current.getAccessToken().getScopes());

        // If PingFederate rotated the refresh token, keep the new one — the old one is now dead.
        OAuth2RefreshToken refresh = tokenResponse.get("refresh_token") instanceof String rotated
                ? new OAuth2RefreshToken(rotated, issuedAt)
                : current.getRefreshToken();

        authorizedClients.saveAuthorizedClient(
                new OAuth2AuthorizedClient(current.getClientRegistration(),
                        current.getPrincipalName(), access, refresh),
                authentication, request, response);
    }

    /** Honours the reveal-tokens switch for raw token endpoint responses too. */
    private Map<String, Object> redact(Map<String, Object> tokenResponse) {
        if (properties.isRevealTokens()) {
            return tokenResponse;
        }
        Map<String, Object> copy = new LinkedHashMap<>(tokenResponse);
        for (String key : List.of("access_token", "refresh_token", "id_token")) {
            if (copy.get(key) instanceof String value) {
                copy.put(key, TokenInspector.preview(value));
            }
        }
        return copy;
    }

    /** Returned by the token-facing endpoints when the session came from the local login. */
    private Map<String, Object> localLoginNotice() {
        return Map.of(
                "loginType", "local",
                "note", "This session came from the app's own username/password login, so there "
                        + "are no OAuth tokens behind it. Configure the PingFederate client and "
                        + "log in through it to populate these fields.");
    }

    private List<String> missingSettings() {
        if (properties.isConfigured()) {
            return List.of();
        }
        return List.of(
                "LAB_OAUTH_ISSUER_URI       (easiest — discovery finds every endpoint)",
                "  or LAB_OAUTH_AUTHORIZATION_URI + LAB_OAUTH_TOKEN_URI",
                "LAB_OAUTH_CLIENT_ID        (the client you created in PingFederate)",
                "LAB_OAUTH_CLIENT_SECRET    (unless it is a public client)");
    }

    private String resolveRedirectUri(HttpServletRequest request, ClientRegistration client) {
        String template = client.getRedirectUri();
        if (!template.contains("{")) {
            return template;
        }
        return absolute(request, "/login/oauth2/code/" + properties.getRegistrationId());
    }

    private String absolute(HttpServletRequest request, String path) {
        if (PingFedProperties.hasText(properties.getPublicBaseUrl())) {
            return properties.getPublicBaseUrl() + path;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port)
                + request.getContextPath() + path;
    }
}
