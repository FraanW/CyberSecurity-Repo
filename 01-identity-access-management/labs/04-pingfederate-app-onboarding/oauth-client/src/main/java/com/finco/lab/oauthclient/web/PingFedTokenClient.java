package com.finco.lab.oauthclient.web;

import com.finco.lab.oauthclient.config.PingFedProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deliberately calls PingFederate's token, introspection and userinfo endpoints by hand.
 *
 * <p>Spring Security can do all of this for you invisibly. For onboarding practice that is the
 * wrong trade: the point is to <i>see</i> the exact HTTP request PingFederate receives, so that
 * when it answers {@code invalid_client} you know which half of the request to fix.</p>
 */
@Component
public class PingFedTokenClient {

    private final RestClient http = RestClient.builder().build();

    /** POST /as/token.oauth2 with any grant type and form parameters. */
    public Map<String, Object> token(String tokenUri,
                                     MultiValueMap<String, String> form,
                                     String clientId,
                                     String clientSecret,
                                     ClientAuthenticationMethod authMethod) {
        return post(tokenUri, form, clientId, clientSecret, authMethod);
    }

    /** POST /as/introspect.oauth2 — RFC 7662. The only way to read a reference token. */
    public Map<String, Object> introspect(String introspectionUri,
                                          String token,
                                          String clientId,
                                          String clientSecret,
                                          ClientAuthenticationMethod authMethod) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        return post(introspectionUri, form, clientId, clientSecret, authMethod);
    }

    /** GET /idp/userinfo.openid with the access token as a Bearer credential. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> userInfo(String userInfoUri, String accessToken) {
        try {
            Map<String, Object> body = http.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return body == null ? Map.of() : body;
        } catch (Exception ex) {
            return errorMap("The UserInfo call failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String uri,
                                     MultiValueMap<String, String> form,
                                     String clientId,
                                     String clientSecret,
                                     ClientAuthenticationMethod authMethod) {
        if (!PingFedProperties.hasText(uri)) {
            return Map.of("error", "endpoint_not_configured",
                    "error_description", "This PingFederate endpoint is not set. Check /api/config.");
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>(form);

        RestClient.RequestBodySpec request = http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON);

        if (ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(authMethod)) {
            // Credentials travel in the form body.
            body.add("client_id", clientId);
            if (PingFedProperties.hasText(clientSecret)) {
                body.add("client_secret", clientSecret);
            }
        } else if (ClientAuthenticationMethod.NONE.equals(authMethod)) {
            // Public client: an ID, no secret.
            body.add("client_id", clientId);
        } else {
            // client_secret_basic — credentials travel in the Authorization header. PingFederate's
            // default, and the one the spec recommends.
            String basic = Base64.getEncoder().encodeToString(
                    (clientId + ":" + (clientSecret == null ? "" : clientSecret)).getBytes(StandardCharsets.UTF_8));
            request = request.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        }

        try {
            Map<String, Object> response = request.body(body).retrieve().body(Map.class);
            return response == null ? Map.of() : response;
        } catch (Exception ex) {
            return errorMap("The call to " + uri + " failed", ex);
        }
    }

    /** Errors are returned as JSON, not thrown — the frontend renders them next to the button. */
    private static Map<String, Object> errorMap(String what, Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "request_failed");
        error.put("error_description", what + ": " + ex.getMessage());
        return error;
    }
}
