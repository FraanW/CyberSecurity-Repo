package com.finco.lab.samlsp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property this lab depends on most: <b>the app must start with nothing configured.</b>
 *
 * <p>A container that crash-loops on a fresh Render deploy tells you nothing. One that boots and
 * serves a checklist of what is missing tells you everything. These tests keep it that way.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnconfiguredAppSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    RestClient.Builder restClientBuilder;

    private RestClient client() {
        return restClientBuilder.baseUrl("http://localhost:" + port).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String path) {
        return client().get().uri(path).retrieve().body(Map.class);
    }

    @Test
    void healthIsPublicAndReportsUp() {
        Map<String, Object> body = getJson("/api/health");
        assertThat(body).containsEntry("status", "UP");
    }

    @Test
    void configIsPublicAndListsWhatIsMissing() {
        Map<String, Object> body = getJson("/api/config");
        assertThat(body).containsEntry("configured", false);
        assertThat((List<?>) body.get("missing")).isNotEmpty();

        // The onboarding values must be present even with no IdP — they are what you paste
        // into PingFederate *before* the connection exists.
        Map<String, Object> sp = asMap(body.get("serviceProvider"));
        assertThat((String) sp.get("assertionConsumerServiceUrl")).endsWith("/login/saml2/sso/pingfed");
        assertThat((String) sp.get("signingCertificate")).startsWith("-----BEGIN CERTIFICATE-----");
    }

    @Test
    void spMetadataIsServedWithoutLogin() {
        String xml = client().get().uri("/api/sp-metadata.xml").retrieve().body(String.class);
        assertThat(xml).contains("EntityDescriptor").contains("AssertionConsumerService");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void protectedApiReturnsCleanUnauthorizedRatherThanRedirecting() {
        ResponseEntity<Void> response = client().get().uri("/api/whoami")
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })   // do not throw; we are asserting on the status
                .toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
