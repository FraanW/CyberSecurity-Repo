package com.finco.lab.oauthclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the two client registrations this lab uses:
 *
 * <ul>
 *   <li><b>pingfed</b> — Authorization Code + PKCE, the flow a human logs in with.</li>
 *   <li><b>pingfed-machine</b> — Client Credentials, the flow one service uses to call another.</li>
 * </ul>
 *
 * <p>Built in Java rather than {@code application.yml} for one reason: if PingFederate is
 * unreachable when the container starts, discovery fails — and a crash-looping container on
 * Render tells you nothing. Here we catch it, log it, and serve a page that says what broke.</p>
 */
@Configuration
public class ClientRegistrationConfig {

    private static final Logger log = LoggerFactory.getLogger(ClientRegistrationConfig.class);

    public static final String MACHINE_REGISTRATION_ID = "pingfed-machine";

    private static final String PLACEHOLDER = "https://pingfederate.invalid";

    private final PingFedProperties properties;

    public ClientRegistrationConfig(PingFedProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration interactive = authorizationCodeClient();
        // Reuse whatever token endpoint the interactive client ended up with — discovered or
        // hand-typed. Both clients always talk to the same PingFederate.
        ClientRegistration machine = machineClient(interactive.getProviderDetails().getTokenUri());
        return new InMemoryClientRegistrationRepository(List.of(interactive, machine));
    }

    private ClientRegistration authorizationCodeClient() {
        ClientRegistration.Builder builder = baseBuilder(properties.getRegistrationId());

        builder.clientId(orPlaceholder(properties.getClientId(), "not-configured"))
                .clientName("PingFederate (Authorization Code)")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // {baseUrl} is filled in per-request, so the same image works locally and on Render.
                .redirectUri(redirectUri())
                .scope(properties.getScopes())
                .userNameAttributeName(IdTokenClaimNames.SUB);

        if (PingFedProperties.hasText(properties.getClientSecret())) {
            builder.clientSecret(properties.getClientSecret());
        }
        builder.clientAuthenticationMethod(clientAuthenticationMethod());

        if (properties.isUsePkce()) {
            // PKCE proves the app that redeems the code is the app that started the flow.
            // Spring turns it on automatically for public clients; confidential clients must ask.
            builder.clientSettings(ClientRegistration.ClientSettings.builder()
                    .requireProofKey(true)
                    .build());
        }

        if (PingFedProperties.hasText(properties.getEndSessionUri())) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("end_session_endpoint", properties.getEndSessionUri());
            builder.providerConfigurationMetadata(metadata);
        }

        return builder.build();
    }

    /** The machine-to-machine client: no user, no redirect, no refresh token. */
    private ClientRegistration machineClient(String tokenUri) {
        ClientRegistration.Builder builder = ClientRegistration
                .withRegistrationId(MACHINE_REGISTRATION_ID)
                .clientName("PingFederate (Client Credentials)")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientId(orPlaceholder(properties.effectiveMachineClientId(), "not-configured"))
                .clientSecret(properties.effectiveMachineClientSecret())
                .clientAuthenticationMethod(clientAuthenticationMethod())
                .tokenUri(orPlaceholder(tokenUri, PLACEHOLDER + "/as/token.oauth2"));
        if (!properties.getMachineScopes().isEmpty()) {
            builder.scope(properties.getMachineScopes());
        }
        return builder.build();
    }

    /**
     * Discovery first, hand-typed endpoints second, placeholder last — so the app always starts.
     */
    private ClientRegistration.Builder baseBuilder(String registrationId) {
        if (PingFedProperties.hasText(properties.getIssuerUri())) {
            try {
                ClientRegistration.Builder builder = ClientRegistrations
                        .fromIssuerLocation(properties.getIssuerUri())
                        .registrationId(registrationId);
                log.info("OAuth client: discovered PingFederate endpoints from issuer {}", properties.getIssuerUri());
                return builder;
            } catch (RuntimeException ex) {
                log.error("OAuth client: OIDC discovery against {} failed ({}). "
                                + "Falling back to the manually configured endpoints. If PingFederate is up, this is "
                                + "usually an issuer-value mismatch or a firewall between Render and your server.",
                        properties.getIssuerUri(), ex.getMessage());
            }
        }
        return manualBuilder(registrationId);
    }

    private ClientRegistration.Builder manualBuilder(String registrationId) {
        if (!properties.isConfigured()) {
            log.warn("OAuth client: not configured yet. Set LAB_OAUTH_ISSUER_URI (or the individual "
                    + "endpoint URLs) plus LAB_OAUTH_CLIENT_ID / LAB_OAUTH_CLIENT_SECRET. "
                    + "The app is up but login will not work — open / to see what is missing.");
        }
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .authorizationUri(orPlaceholder(properties.getAuthorizationUri(), PLACEHOLDER + "/as/authorization.oauth2"))
                .tokenUri(orPlaceholder(properties.getTokenUri(), PLACEHOLDER + "/as/token.oauth2"));
        if (PingFedProperties.hasText(properties.getJwkSetUri())) {
            builder.jwkSetUri(properties.getJwkSetUri());
        }
        if (PingFedProperties.hasText(properties.getUserInfoUri())) {
            builder.userInfoUri(properties.getUserInfoUri());
        }
        if (PingFedProperties.hasText(properties.getIssuerUri())) {
            builder.issuerUri(properties.getIssuerUri());
        }
        return builder;
    }

    private ClientAuthenticationMethod clientAuthenticationMethod() {
        return switch (properties.getClientAuthenticationMethod().toLowerCase()) {
            case "client_secret_post" -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
            case "none" -> ClientAuthenticationMethod.NONE;
            default -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    }

    private String redirectUri() {
        if (PingFedProperties.hasText(properties.getPublicBaseUrl())) {
            return properties.getPublicBaseUrl()
                    + "/login/oauth2/code/" + properties.getRegistrationId();
        }
        return "{baseUrl}/login/oauth2/code/{registrationId}";
    }

    private static String orPlaceholder(String value, String fallback) {
        return PingFedProperties.hasText(value) ? value : fallback;
    }
}
